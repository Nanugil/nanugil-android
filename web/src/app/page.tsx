import { groupTrips, myGroups, myMemberId } from "@/lib/api";
import { statusOf, viewFor } from "@/lib/settlement";
import { createServerSupabase } from "@/lib/supabase/server";
import { HomeView, type HomeGroup } from "./HomeView";

/** 01 홈: 내 그룹, 진행 중 운행, 나에게 온 정산 요청 */
export default async function Home() {
  const sb = await createServerSupabase();
  let groups: HomeGroup[];
  try {
    groups = await Promise.all((await myGroups(sb)).map(async (g) => {
      const [trips, me] = await Promise.all([groupTrips(sb, g.id), myMemberId(sb, g.id)]);
      const active = trips.find((tr) => tr.endedAt === null) ?? null;
      const settled = trips.find((tr) => tr.settlement !== null) ?? null;
      const toSend = settled?.settlement ? viewFor(settled.settlement, me).toSend.filter((x) => statusOf(x.transfer) === "pending") : [];
      return {
        id: g.id, name: g.name, destination: active?.destinationName ?? g.destinationName, driving: active !== null,
        owed: toSend.length > 0 && settled ? { tripId: settled.id, amountWon: toSend.reduce((s, x) => s + x.transfer.amountWon, 0) } : null,
      };
    }));
  } catch {
    return <HomeView groups={null} />;
  }
  return <HomeView groups={groups} />;
}
