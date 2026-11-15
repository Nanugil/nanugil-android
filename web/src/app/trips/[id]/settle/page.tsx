import { getTrip, groupMembers, myMemberId } from "@/lib/api";
import { createServerSupabase } from "@/lib/supabase/server";
import { SettleView } from "./SettleView";

/** 08·09 정산: 내 부담액, 보낼 곳, 그룹 전체 송금 현황 */
export default async function SettlePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const sb = await createServerSupabase();
  try {
    const trip = await getTrip(sb, id);
    const [members, me] = await Promise.all([groupMembers(sb, trip.groupId), myMemberId(sb, trip.groupId)]);
    return <SettleView state={{ kind: "ok", trip, members, me }} />;
  } catch {
    return <SettleView state={{ kind: "error" }} />;
  }
}
