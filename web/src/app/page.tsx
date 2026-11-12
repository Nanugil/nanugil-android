import { groupTrips, myGroups, myMemberId } from "@/lib/api";
import { statusOf, viewFor } from "@/lib/settlement";
import { createServerSupabase } from "@/lib/supabase/server";
import { HomeView, type HomeGroup } from "./HomeView";

/** 01 홈: 보낼 돈이 가장 위, 그다음 운행 상태와 요약, 그룹 목록 */
export default async function Home() {
  const sb = await createServerSupabase();
  const { data } = await sb.auth.getUser();
  const name = String(data.user?.user_metadata?.display_name ?? "");
  let groups: HomeGroup[];
  try {
    groups = await Promise.all((await myGroups(sb)).map(async (g) => {
      const [trips, me] = await Promise.all([groupTrips(sb, g.id), myMemberId(sb, g.id)]);
      const active = trips.find((tr) => tr.endedAt === null) ?? null;
      const settled = trips.find((tr) => tr.settlement !== null) ?? null;
      const s = settled?.settlement ?? null;
      const toSend = s ? viewFor(s, me).toSend.filter((x) => statusOf(x.transfer) === "pending") : [];
      return {
        id: g.id, name: g.name, destination: active?.destinationName ?? g.destinationName, driving: active !== null,
        owed: toSend.length > 0 && settled ? { tripId: settled.id, amountWon: toSend.reduce((sum, x) => sum + x.transfer.amountWon, 0) } : null,
        progress: s ? { done: s.transfers.filter((x) => statusOf(x) === "received").length, total: s.transfers.length } : null,
      };
    }));
  } catch {
    return <HomeView name={name} groups={null} />;
  }
  return <HomeView name={name} groups={groups} />;
}
