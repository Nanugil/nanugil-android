-- 차량 앱 사용자 멤버 (#37 리뷰 2번)
-- 차량 앱은 익명 로그인, 동승자 웹은 이메일 가입이다. 동승자 웹에서 이름을 연결하면(claim_member) user_id가 생겨서
-- 차량 앱이 운행 전에 그 동승자를 운전자로 보고 [운행 시작] 때 방장 차에 태우지 않았다.
-- auth.users는 앱에서 못 읽으니, 그룹 멤버만 이 그룹의 차량 앱 사용자(익명 계정) 멤버 ID를 받게 한다.

create function public.app_member_ids(p_group_id uuid)
returns setof uuid language plpgsql stable security definer set search_path = '' as $$
begin
  perform public._my_member_id(p_group_id); -- 그룹 멤버가 아니면 NOT_MEMBER
  return query
    select m.id from public.group_members m join auth.users u on u.id = m.user_id
    where m.group_id = p_group_id and u.is_anonymous;
end;
$$;

revoke all on function public.app_member_ids(uuid) from public, anon;
grant execute on function public.app_member_ids(uuid) to authenticated;
