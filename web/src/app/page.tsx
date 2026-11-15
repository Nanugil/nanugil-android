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
      // 운행이 여러 번이면 미납금을 모두 더한다. 이동은 가장 오래된 미납 운행으로 (최근 것만 보면 이전 미납이 사라진다)
      const unpaid = trips
        .filter((tr) => tr.settlement)
        .map((tr) => ({ tr, owed: viewFor(tr.settlement!, me).toSend.filter((x) => statusOf(x.transfer) === "pending") }))
        .filter((x) => x.owed.length > 0);
      const owedWon = unpaid.reduce((sum, x) => sum + x.owed.reduce((s2, y) => s2 + y.transfer.amountWon, 0), 0);
      const focus = unpaid.at(-1)?.tr ?? trips.find((tr) => tr.settlement !== null) ?? null; // trips는 최신순
      const s = focus?.settlement ?? null;
      return {
        id: g.id, name: g.name, destination: active?.destinationName ?? g.destinationName, driving: active !== null,
        owed: unpaid.length > 0 && focus ? { tripId: focus.id, amountWon: owedWon } : null,
        progress: s ? { done: s.transfers.filter((x) => statusOf(x) === "received").length, total: s.transfers.length } : null,
      };
    }));
  } catch {
    return <HomeView name={name} groups={null} />;
  }
  return <HomeView name={name} groups={groups} />;
}
