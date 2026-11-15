-- 보안 검토 반영 시험. passenger_web_test.sql 뒤에 돈다 (t 스키마·사용자 재사용).
set role authenticated;

-- High 1: 내 그룹의 맞는 코드로 실패 횟수를 0으로 되돌릴 수 없다
insert into t.kv values ('dummy', '') on conflict do nothing;
reset role;
insert into auth.users (id) values ('dddddddd-0000-4000-8000-000000000004');
set role authenticated;
set request.jwt.claim.sub = 'dddddddd-0000-4000-8000-000000000004';
do $$ declare g public.groups; begin
  g := public.create_group('공격자 그룹', '서울', 37.5, 127.0, 'D');
  perform t.put('dcode', g.invite_code);
end $$;
do $$ declare i int; begin
  for i in 1..4 loop perform public.preview_invite('ZZZZZZ'); end loop;
  perform public.preview_invite(t.v('dcode'));   -- 맞는 코드
  perform public.preview_invite('ZZZZZZ');        -- 다섯 번째 실패
  perform t.ok((select error from public.preview_invite(t.v('dcode'))) = 'TOO_MANY_ATTEMPTS', '성공해도 실패 횟수가 지워지지 않음');
end $$;

-- Medium: 송금 링크 길이 제한, 줄바꿈 거절
set request.jwt.claim.sub = 'aaaaaaaa-0000-4000-8000-000000000001';
select t.expect_error(format('select public.set_pay_link(%L, %L)', t.v('group'), 'https://qr.kakaopay.com/' || repeat('a', 400)), 'INVALID_PAY_LINK');
select t.expect_error(format('select public.set_pay_link(%L, %L)', t.v('group'), 'https://qr.kakaopay.com/a' || chr(10) || 'b'), 'INVALID_PAY_LINK');

-- Medium: 금액 상한, 받는 사람은 운전자, 송금 목록 50건 제한
select t.expect_error(format('select public.request_settlement(%L, 1, %L)', t.v('trip'),
  jsonb_build_array(jsonb_build_object('from', t.v('pB'), 'to', t.v('mA'), 'amountWon', 10000001))), 'INVALID_TRANSFER');
select t.expect_error(format('select public.request_settlement(%L, 1, %L)', t.v('trip'),
  jsonb_build_array(jsonb_build_object('from', t.v('mA'), 'to', t.v('pB'), 'amountWon', 1000))), 'INVALID_TRANSFER');
select t.expect_error(format('select public.request_settlement(%L, 1, %L)', t.v('trip'),
  (select jsonb_agg(jsonb_build_object('from', t.v('pB'), 'to', t.v('mA'), 'amountWon', 1)) from generate_series(1, 51))), 'INVALID_TRANSFER');
-- 운행 기록이 있는 멤버는 나가거나 강퇴할 수 없다 (과거 운행·정산 보존)
select t.expect_error(format('select public.leave_group(%L, %L)', t.v('group'), t.v('pD')), 'MEMBER_HAS_HISTORY');
reset role;
select 'security_test OK';
