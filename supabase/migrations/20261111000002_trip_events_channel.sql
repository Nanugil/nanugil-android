-- T5: 운행 시작·종료, 차량 도착을 Postgres Changes로 받는 채널 'group:{그룹 ID}'.
-- Realtime 공개 채널을 막아 두었으므로 private 채널로 열고, 그룹 멤버만 들어올 수 있게 한다.
-- 읽기만 허용한다(보내기 정책 없음). 받는 행은 trips·trip_members의 RLS로 한 번 더 걸러진다. 테이블·컬럼은 바꾸지 않는다.

create function public.is_group_topic_member(p_topic text)
returns boolean language plpgsql stable security definer set search_path = '' as $$
begin
  if p_topic !~ '^group:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    return false;
  end if;
  return exists (
    select 1 from public.group_members
    where group_id = substr(p_topic, 7)::uuid and user_id = auth.uid()
  );
end;
$$;

revoke all on function public.is_group_topic_member(text) from public, anon;
grant execute on function public.is_group_topic_member(text) to authenticated;

create policy "그룹 멤버만 운행 알림 받기" on realtime.messages for select to authenticated
  using (public.is_group_topic_member(realtime.topic()));
