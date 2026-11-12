-- 동승자 웹 (docs/superpowers/specs/2026-11-13-passenger-web-design.md)
-- 테이블은 4개 그대로 두고 열 2개와 RPC 6개를 더한다.
--   group_members.pay_link : 운전자의 카카오페이 송금 링크 (지금은 차량 기기에만 있음)
--   trips.settlement       : 정산 요청 시점의 송금 목록. 요청 뒤에 값을 고쳐도 바뀌지 않는다.
-- 새 오류 코드: INVALID_TRANSFER, NOT_YOUR_TRANSFER, MEMBER_TAKEN, INVALID_PAY_LINK

alter table public.group_members
  add column pay_link text check (pay_link ~ '^https://([a-z0-9-]+\.)*kakaopay\.com(/.*)?$');

-- { requestedAt, requestedBy, perPersonWon, transfers: [{ from, to, amountWon, sentAt, receivedAt }] }
alter table public.trips add column settlement jsonb;

-- ─────────────────────────────────────────────
-- 송금 링크
-- ─────────────────────────────────────────────

-- 내 멤버 행의 송금 링크를 바꾼다. null이면 지운다.
create function public.set_pay_link(p_group_id uuid, p_link text)
returns void language plpgsql security definer set search_path = '' as $$
declare v_me uuid := public._my_member_id(p_group_id); v_link text := nullif(btrim(p_link), '');
begin
  if v_link is not null and v_link !~ '^https://([a-z0-9-]+\.)*kakaopay\.com(/.*)?$' then
    raise exception 'INVALID_PAY_LINK';
  end if;
  update public.group_members set pay_link = v_link where id = v_me;
end;
$$;

-- ─────────────────────────────────────────────
-- 정산 요청과 송금 표시
-- ─────────────────────────────────────────────

-- 이 운행의 운전자가 도착한 뒤 송금 목록을 올린다. 다시 부르면 덮어쓰되,
-- (보낸 사람, 받는 사람, 금액)이 같은 송금은 이미 표시된 sentAt·receivedAt을 이어받는다.
create function public.request_settlement(p_trip_id uuid, p_per_person_won bigint, p_transfers jsonb)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_me     uuid := public._my_driver_member_id(p_trip_id);
  v_old    jsonb;
  v_list   jsonb := '[]'::jsonb;
  v_t      jsonb;
  v_from   uuid;
  v_to     uuid;
  v_amount bigint;
  v_prev   jsonb;
  v_result jsonb;
begin
  if not exists (
    select 1 from public.trip_members tm join public.trips t on t.id = tm.trip_id
    where tm.trip_id = p_trip_id and tm.member_id = v_me and (tm.arrived_at is not null or t.ended_at is not null)
  ) then
    raise exception 'NOT_ARRIVED';
  end if;

  if p_per_person_won is null or p_per_person_won < 0
     or p_transfers is null or jsonb_typeof(p_transfers) <> 'array' then
    raise exception 'INVALID_TRANSFER';
  end if;

  select t.settlement into v_old from public.trips t where t.id = p_trip_id for update;

  for v_t in select value from jsonb_array_elements(p_transfers) loop
    begin
      v_from   := (v_t ->> 'from')::uuid;
      v_to     := (v_t ->> 'to')::uuid;
      v_amount := (v_t ->> 'amountWon')::bigint;
    exception when others then
      raise exception 'INVALID_TRANSFER';
    end;
    if v_from is null or v_to is null or v_amount is null or v_amount <= 0 or v_from = v_to
       or not exists (select 1 from public.trip_members tm where tm.trip_id = p_trip_id and tm.member_id = v_from)
       or not exists (select 1 from public.trip_members tm where tm.trip_id = p_trip_id and tm.member_id = v_to) then
      raise exception 'INVALID_TRANSFER';
    end if;

    v_prev := null;
    if v_old is not null then
      select o into v_prev from jsonb_array_elements(v_old -> 'transfers') o
      where o ->> 'from' = v_from::text and o ->> 'to' = v_to::text and (o ->> 'amountWon')::bigint = v_amount
      limit 1;
    end if;

    v_list := v_list || jsonb_build_array(jsonb_build_object(
      'from', v_from, 'to', v_to, 'amountWon', v_amount,
      'sentAt', coalesce(v_prev -> 'sentAt', 'null'::jsonb),
      'receivedAt', coalesce(v_prev -> 'receivedAt', 'null'::jsonb)
    ));
  end loop;

  v_result := jsonb_build_object(
    'requestedAt', now(), 'requestedBy', v_me, 'perPersonWon', p_per_person_won, 'transfers', v_list
  );
  update public.trips set settlement = v_result where id = p_trip_id;
  return v_result;
end;
$$;

-- 송금 하나에 시각을 표시한다. p_side는 'from'(보냈어요) 또는 'to'(받았어요). 이미 표시돼 있으면 그대로 둔다.
create function public._mark_transfer(p_trip_id uuid, p_index int, p_side text, p_field text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_group uuid;
  v_me    uuid;
  v_s     jsonb;
  v_t     jsonb;
begin
  select t.group_id, t.settlement into v_group, v_s from public.trips t where t.id = p_trip_id for update;
  if v_group is null then raise exception 'NOT_MEMBER'; end if;
  v_me := public._my_member_id(v_group); -- 그룹 멤버가 아니면 NOT_MEMBER
  if v_s is null or p_index is null or p_index < 0 or p_index >= jsonb_array_length(v_s -> 'transfers') then
    raise exception 'INVALID_TRANSFER';
  end if;
  v_t := v_s -> 'transfers' -> p_index;
  if v_t ->> p_side <> v_me::text then
    raise exception 'NOT_YOUR_TRANSFER';
  end if;
  if jsonb_typeof(v_t -> p_field) is distinct from 'string' then
    v_s := jsonb_set(v_s, array['transfers', p_index::text, p_field], to_jsonb(now()));
    update public.trips set settlement = v_s where id = p_trip_id;
  end if;
  return v_s;
end;
$$;

create function public.mark_transfer_sent(p_trip_id uuid, p_index int)
returns jsonb language sql security definer set search_path = '' as $$
  select public._mark_transfer(p_trip_id, p_index, 'from', 'sentAt');
$$;

create function public.mark_transfer_received(p_trip_id uuid, p_index int)
returns jsonb language sql security definer set search_path = '' as $$
  select public._mark_transfer(p_trip_id, p_index, 'to', 'receivedAt');
$$;

-- ─────────────────────────────────────────────
-- 이름만 있는 멤버를 내 계정에 연결 (초대 코드로 확인한 뒤에만)
-- join_group·preview_invite처럼 실패 횟수를 남겨야 해서 오류를 결과의 error 열로 돌려준다.
-- ─────────────────────────────────────────────

create function public.invite_unclaimed_members(p_code text)
returns table (error text, member_id uuid, display_name text)
language plpgsql security definer set search_path = '' as $$
declare v_group uuid; v_error text;
begin
  select o_group_id, o_error into v_group, v_error from public._find_group_by_code(p_code);
  if v_error is not null then
    return query select v_error, null::uuid, null::text;
    return;
  end if;
  return query
    select null::text, m.id, m.display_name from public.group_members m
    where m.group_id = v_group and m.user_id is null
    order by m.joined_at, m.id;
end;
$$;

create function public.claim_member(p_code text, p_member_id uuid)
returns table (error text, group_id uuid, group_name text, member_count int)
language plpgsql security definer set search_path = '' as $$
declare v_group uuid; v_error text; v_rows int;
begin
  select o_group_id, o_error into v_group, v_error from public._find_group_by_code(p_code);
  if v_error is null and exists (select 1 from public.group_members m where m.group_id = v_group and m.user_id = auth.uid()) then
    v_error := 'ALREADY_MEMBER';
  end if;
  if v_error is null then
    update public.group_members m set user_id = auth.uid()
    where m.id = p_member_id and m.group_id = v_group and m.user_id is null;
    get diagnostics v_rows = row_count;
    if v_rows = 0 then v_error := 'MEMBER_TAKEN'; end if;
  end if;
  if v_error is not null then
    return query select v_error, null::uuid, null::text, null::int;
    return;
  end if;
  return query
    select null::text, g.id, g.name, (select count(*)::int from public.group_members m where m.group_id = g.id)
    from public.groups g where g.id = v_group;
end;
$$;

-- ─────────────────────────────────────────────
-- 실행 권한: 로그인한 사용자만 (내부 도우미 _mark_transfer는 아무도 직접 못 부른다)
-- ─────────────────────────────────────────────

revoke all on function public.set_pay_link(uuid, text) from public, anon;
revoke all on function public.request_settlement(uuid, bigint, jsonb) from public, anon;
revoke all on function public._mark_transfer(uuid, int, text, text) from public, anon, authenticated;
revoke all on function public.mark_transfer_sent(uuid, int) from public, anon;
revoke all on function public.mark_transfer_received(uuid, int) from public, anon;
revoke all on function public.invite_unclaimed_members(text) from public, anon;
revoke all on function public.claim_member(text, uuid) from public, anon;

grant execute on function public.set_pay_link(uuid, text) to authenticated;
grant execute on function public.request_settlement(uuid, bigint, jsonb) to authenticated;
grant execute on function public.mark_transfer_sent(uuid, int) to authenticated;
grant execute on function public.mark_transfer_received(uuid, int) to authenticated;
grant execute on function public.invite_unclaimed_members(text) to authenticated;
grant execute on function public.claim_member(text, uuid) to authenticated;
