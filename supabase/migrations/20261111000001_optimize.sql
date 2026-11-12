-- Supabase Advisor 지적 반영. 테이블·컬럼은 바꾸지 않는다.

-- 성능: 외래 키 색인 (Unindexed foreign keys). 그룹·멤버·사용자를 지울 때와 운행을 그룹으로 찾을 때 쓴다.
-- trips_one_active_per_group은 진행 중 운행만 덮어서 끝난 운행까지 찾는 색인이 따로 필요하다.
create index groups_owner        on public.groups (owner_id);
create index trips_group         on public.trips (group_id);
create index trips_started_by    on public.trips (started_by);
create index trip_members_member on public.trip_members (member_id);

-- 보안: 내부 도우미는 RPC 안에서만 쓴다. RPC는 SECURITY DEFINER(소유자 권한)로 돌아서
-- 로그인 사용자에게 실행 권한이 없어도 부를 수 있다. 앱에서 직접 부르는 길만 막는다.
-- (is_group_member·is_trip_group_member·is_active_trip_driver는 RLS 정책이 로그인 사용자 권한으로 부르므로 그대로 둔다.)
revoke execute on function public._my_member_id(uuid)          from authenticated;
revoke execute on function public._require_owner(uuid)         from authenticated;
revoke execute on function public._active_trip_group(uuid)     from authenticated;
revoke execute on function public._my_driver_member_id(uuid)   from authenticated;
revoke execute on function public._new_invite_code()           from authenticated;
revoke execute on function public._find_group_by_code(text)    from authenticated;
