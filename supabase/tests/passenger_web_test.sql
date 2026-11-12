-- 20261113000000_passenger_web.sql 시험. supabase/tests/run.sh 로 돌린다.
-- A 운전자(방장), B 동승자(운전자가 이름만 넣어 둔 멤버를 연결), C 그룹 밖 사람.
create schema t;
create table t.kv (k text primary key, v text);
grant usage on schema t to authenticated, anon;
grant all on t.kv to authenticated, anon;
create function t.v(p text) returns text language sql stable as $$ select v from t.kv where k = p $$;
create function t.put(p text, val text) returns void language sql as $$
  insert into t.kv values (p, val) on conflict (k) do update set v = excluded.v
$$;
-- p_sql이 p_code 오류(raise exception '<코드>')로 실패해야 통과
create function t.expect_error(p_sql text, p_code text) returns void language plpgsql as $$
begin
  begin
    execute p_sql;
  exception when others then
    if sqlerrm = p_code then return; end if;
    raise exception 'expected % but got "%" : %', p_code, sqlerrm, p_sql;
  end;
  raise exception 'expected % but succeeded : %', p_code, p_sql;
end $$;
create function t.ok(p_cond boolean, p_what text) returns void language plpgsql as $$
begin if p_cond is not true then raise exception 'FAILED: %', p_what; end if; end $$;
grant execute on all functions in schema t to authenticated, anon;

insert into auth.users (id) values
  ('aaaaaaaa-0000-4000-8000-000000000001'), ('bbbbbbbb-0000-4000-8000-000000000002'), ('cccccccc-0000-4000-8000-000000000003');

set role authenticated;

-- ── A: 그룹·운행 준비 ──
set request.jwt.claim.sub = 'aaaaaaaa-0000-4000-8000-000000000001';
do $$ declare g public.groups; tr public.trips; begin
  g := public.create_group('시험 MT', '가평', 37.83, 127.51, 'A');
  perform t.put('group', g.id::text); perform t.put('code', g.invite_code);
  perform t.put('pB', (public.add_passenger(g.id, 'B이름')).id::text);
  perform t.put('pD', (public.add_passenger(g.id, 'D이름')).id::text);
  perform t.put('mA', (select id::text from public.group_members where group_id = g.id and user_id = auth.uid()));
  tr := public.start_trip(g.id);
  perform t.put('trip', tr.id::text);
  perform public.join_trip(tr.id, 'A차', 'EV');
  perform public.set_trip_passengers(tr.id, array[t.v('pB')::uuid, t.v('pD')::uuid]);
end $$;

-- 송금 링크
select t.expect_error(format('select public.set_pay_link(%L, %L)', t.v('group'), 'https://evil.com/x'), 'INVALID_PAY_LINK');
select t.expect_error(format('select public.set_pay_link(%L, %L)', t.v('group'), 'https://kakaopay.com.evil.io/x'), 'INVALID_PAY_LINK');
select public.set_pay_link(t.v('group')::uuid, ' https://qr.kakaopay.com/abc ');
select t.ok((select pay_link from public.group_members where id = t.v('mA')::uuid) = 'https://qr.kakaopay.com/abc', 'pay_link 저장');

-- 도착 전 정산 요청
select t.expect_error(format('select public.request_settlement(%L, 11750, %L)', t.v('trip'),
  jsonb_build_array(jsonb_build_object('from', t.v('pB'), 'to', t.v('mA'), 'amountWon', 11750))), 'NOT_ARRIVED');

-- ── B: 이름 연결 ──
set request.jwt.claim.sub = 'bbbbbbbb-0000-4000-8000-000000000002';
select t.ok((select count(*) from public.invite_unclaimed_members(t.v('code')) where error is null) = 2, '연결 전 이름 2개');
select t.ok((select error from public.claim_member(t.v('code'), t.v('pB')::uuid)) is null, 'B 이름 연결');
select t.ok((select array_agg(display_name) from public.invite_unclaimed_members(t.v('code'))) = array['D이름'], '연결 뒤 D만 남음');
select t.ok((select error from public.claim_member(t.v('code'), t.v('pD')::uuid)) = 'ALREADY_MEMBER', '이미 멤버');
select t.expect_error(format('select public.request_settlement(%L, 0, %L)', t.v('trip'), '[]'), 'NOT_DRIVER');

-- ── C: 남 ──
set request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000003';
select t.ok((select error from public.claim_member(t.v('code'), t.v('pB')::uuid)) = 'MEMBER_TAKEN', '이미 연결된 이름');
select t.ok((select error from public.invite_unclaimed_members('ZZZZZZ')) = 'INVALID_CODE', '틀린 코드');

-- ── A: 도착 후 정산 요청 ──
set request.jwt.claim.sub = 'aaaaaaaa-0000-4000-8000-000000000001';
select public.mark_arrived(t.v('trip')::uuid);
select t.expect_error(format('select public.request_settlement(%L, 11750, %L)', t.v('trip'),
  jsonb_build_array(jsonb_build_object('from', t.v('mA'), 'to', t.v('mA'), 'amountWon', 1))), 'INVALID_TRANSFER');
select t.expect_error(format('select public.request_settlement(%L, 11750, %L)', t.v('trip'),
  jsonb_build_array(jsonb_build_object('from', gen_random_uuid(), 'to', t.v('mA'), 'amountWon', 1))), 'INVALID_TRANSFER');
select t.expect_error(format('select public.request_settlement(%L, 11750, %L)', t.v('trip'),
  jsonb_build_array(jsonb_build_object('from', t.v('pB'), 'to', t.v('mA'), 'amountWon', 0))), 'INVALID_TRANSFER');
select t.expect_error(format('select public.request_settlement(%L, 11750, %L)', t.v('trip'),
  jsonb_build_array(jsonb_build_object('from', 'not-a-uuid', 'to', t.v('mA'), 'amountWon', 1))), 'INVALID_TRANSFER');
select t.expect_error(format('select public.request_settlement(%L, 11750, %L)', t.v('trip'), '{}'), 'INVALID_TRANSFER');

do $$ declare s jsonb; begin
  s := public.request_settlement(t.v('trip')::uuid, 11750, jsonb_build_array(
    jsonb_build_object('from', t.v('pB'), 'to', t.v('mA'), 'amountWon', 11750),
    jsonb_build_object('from', t.v('pD'), 'to', t.v('mA'), 'amountWon', 11750)));
  perform t.ok((s ->> 'perPersonWon')::int = 11750, 'perPersonWon');
  perform t.ok(s ->> 'requestedBy' = t.v('mA'), 'requestedBy');
  perform t.ok(jsonb_array_length(s -> 'transfers') = 2, '송금 2건');
  perform t.ok(jsonb_typeof(s -> 'transfers' -> 0 -> 'sentAt') = 'null', 'sentAt null');
  perform t.ok((select settlement from public.trips where id = t.v('trip')::uuid) = s, '저장됨');
end $$;

-- ── B: 보냈어요 ──
set request.jwt.claim.sub = 'bbbbbbbb-0000-4000-8000-000000000002';
do $$ declare s1 jsonb; s2 jsonb; begin
  s1 := public.mark_transfer_sent(t.v('trip')::uuid, 0);
  perform t.ok(jsonb_typeof(s1 -> 'transfers' -> 0 -> 'sentAt') = 'string', 'sentAt 표시');
  perform pg_sleep(0.01);
  s2 := public.mark_transfer_sent(t.v('trip')::uuid, 0);
  perform t.ok(s2 -> 'transfers' -> 0 -> 'sentAt' = s1 -> 'transfers' -> 0 -> 'sentAt', '두 번 눌러도 처음 시각');
  perform t.put('sentAt0', s1 -> 'transfers' -> 0 ->> 'sentAt');
end $$;
select t.expect_error(format('select public.mark_transfer_sent(%L, 1)', t.v('trip')), 'NOT_YOUR_TRANSFER');
select t.expect_error(format('select public.mark_transfer_sent(%L, 5)', t.v('trip')), 'INVALID_TRANSFER');
select t.expect_error(format('select public.mark_transfer_sent(%L, -1)', t.v('trip')), 'INVALID_TRANSFER');
select t.expect_error(format('select public.mark_transfer_received(%L, 0)', t.v('trip')), 'NOT_YOUR_TRANSFER');

-- ── C: 그룹 밖 ──
set request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000003';
select t.expect_error(format('select public.mark_transfer_sent(%L, 0)', t.v('trip')), 'NOT_MEMBER');
select t.ok((select count(*) from public.trips where id = t.v('trip')::uuid) = 0, 'RLS: 남은 운행을 못 봄');

-- ── A: 받았어요, 다시 요청 ──
set request.jwt.claim.sub = 'aaaaaaaa-0000-4000-8000-000000000001';
select t.expect_error(format('select public.mark_transfer_sent(%L, 0)', t.v('trip')), 'NOT_YOUR_TRANSFER');
select t.ok(jsonb_typeof(public.mark_transfer_received(t.v('trip')::uuid, 0) -> 'transfers' -> 0 -> 'receivedAt') = 'string', 'receivedAt 표시');
do $$ declare s jsonb; begin
  s := public.request_settlement(t.v('trip')::uuid, 11750, jsonb_build_array(
    jsonb_build_object('from', t.v('pB'), 'to', t.v('mA'), 'amountWon', 11750),
    jsonb_build_object('from', t.v('pD'), 'to', t.v('mA'), 'amountWon', 11750)));
  perform t.ok(s -> 'transfers' -> 0 ->> 'sentAt' = t.v('sentAt0'), '같은 송금은 sentAt 유지');
  perform t.ok(jsonb_typeof(s -> 'transfers' -> 0 -> 'receivedAt') = 'string', '같은 송금은 receivedAt 유지');
  s := public.request_settlement(t.v('trip')::uuid, 12000, jsonb_build_array(
    jsonb_build_object('from', t.v('pB'), 'to', t.v('mA'), 'amountWon', 12000)));
  perform t.ok(jsonb_typeof(s -> 'transfers' -> 0 -> 'sentAt') = 'null', '금액이 바뀌면 sentAt 초기화');
  perform t.ok(jsonb_typeof(s -> 'transfers' -> 0 -> 'receivedAt') = 'null', '금액이 바뀌면 receivedAt 초기화');
end $$;

-- 권한: 내부 도우미는 직접 못 부르고, anon은 RPC를 못 부른다
select t.expect_error(format('select public._mark_transfer(%L, 0, %L, %L)', t.v('trip'), 'to', 'receivedAt'),
  'permission denied for function _mark_transfer');
set role anon;
select t.expect_error(format('select public.mark_transfer_sent(%L, 0)', t.v('trip')), 'permission denied for function mark_transfer_sent');
reset role;
select 'passenger_web_test OK';
