import { viewFor } from "./settlement";
import type { Trip } from "./types";

/**
 * 실시간으로 받은 운행 변경을 목록에 반영한다. 정산 요청이 새로 오거나 다시 왔고(requestedAt이 바뀜)
 * 나에게 보낼 송금이 있으면 owedWon에 보낼 금액을 담는다. 화면은 이 값으로 알림·이동을 한다.
 */
export function applyTripChange(prev: Trip[], trip: Trip, me: string): { trips: Trip[]; owedWon: number | null } {
  const old = prev.find((x) => x.id === trip.id);
  const trips = old ? prev.map((x) => (x.id === trip.id ? trip : x)) : [trip, ...prev];
  const s = trip.settlement;
  if (!s || s.requestedAt === old?.settlement?.requestedAt) return { trips, owedWon: null };
  const toSend = viewFor(s, me).toSend;
  return { trips, owedWon: toSend.length > 0 ? toSend.reduce((sum, x) => sum + x.transfer.amountWon, 0) : null };
}
