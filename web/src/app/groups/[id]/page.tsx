import { groupMembers, groupTrips, myMemberId } from "@/lib/api";
import { createServerSupabase } from "@/lib/supabase/server";
import { GroupLive } from "./GroupLive";

/** 03 그룹: 멤버와 운행 상태. 정산 요청이 오면 GroupLive가 정산 화면으로 넘긴다. */
export default async function GroupPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const sb = await createServerSupabase();
  try {
    const [{ data: group }, members, trips, me] = await Promise.all([
      sb.from("groups").select("id, name, destination_name").eq("id", id).maybeSingle(),
      groupMembers(sb, id), groupTrips(sb, id), myMemberId(sb, id),
    ]);
    if (!group) return <GroupLive state={{ kind: "error" }} />;
    return <GroupLive state={{ kind: "ok", group: { id: group.id, name: group.name, destinationName: group.destination_name }, members, trips, me }} />;
  } catch {
    return <GroupLive state={{ kind: "error" }} />;
  }
}
