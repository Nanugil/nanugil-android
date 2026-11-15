import { expect, it } from "vitest";
import { applyTripChange } from "./tripChange";
import type { Settlement, Trip } from "./types";

const settlement = (requestedAt: string): Settlement => ({
  requestedAt, requestedBy: "A", perPersonWon: 11750,
  transfers: [{ from: "B", to: "A", amountWon: 11750, sentAt: null, receivedAt: null }],
});
const trip = (s: Settlement | null, id = "t1"): Trip =>
  ({ id, groupId: "g", destinationName: "가평", startedAt: "", endedAt: null, settlement: s });

it("새 정산 요청에 내 송금이 있으면 보낼 금액을 알려 준다", () => {
  const r = applyTripChange([trip(null)], trip(settlement("r1")), "B");
  expect(r.owedWon).toBe(11750);
  expect(r.trips).toHaveLength(1);
});
it("같은 요청의 표시만 바뀌면 다시 알리지 않는다", () => {
  expect(applyTripChange([trip(settlement("r1"))], trip(settlement("r1")), "B").owedWon).toBeNull();
});
it("다시 요청하면 다시 알린다", () => {
  expect(applyTripChange([trip(settlement("r1"))], trip(settlement("r2")), "B").owedWon).toBe(11750);
});
it("내 송금이 없으면 알리지 않는다", () => {
  expect(applyTripChange([], trip(settlement("r1")), "Z").owedWon).toBeNull();
});
it("처음 보는 운행은 맨 앞에 넣는다", () => {
  expect(applyTripChange([trip(null, "old")], trip(null, "new"), "B").trips.map((x) => x.id)).toEqual(["new", "old"]);
});
