// 개발 전용 미리보기 데이터 (npm run dev에서만 /preview가 열린다). README 정산 예시와 같은 숫자.
import type { Member, Settlement, Trip } from "@/lib/types";

const m = (id: string, displayName: string, payLink: string | null = null): Member => ({ id, userId: null, displayName, payLink });
export const members: Member[] = [
  m("a", "강운전", "https://qr.kakaopay.com/demo-a"), m("b", "윤운전", "https://qr.kakaopay.com/demo-b"),
  m("p1", "김민수"), m("p2", "이서준"), m("p3", "박지우"), m("p4", "최하은"), m("p5", "정도윤"), m("p6", "한예린"),
];
const tr = (from: string, to: string, amountWon: number, sentAt: string | null = null, receivedAt: string | null = null) =>
  ({ from, to, amountWon, sentAt, receivedAt });
export const settlement: Settlement = {
  requestedAt: "2026-11-15T18:20:00+09:00", requestedBy: "a", perPersonWon: 11750,
  transfers: [
    tr("p1", "a", 11750), tr("p2", "a", 11750, "x"), tr("p3", "a", 11750, "x", "y"),
    tr("p4", "b", 11750, "x", "y"), tr("p5", "b", 11750), tr("p6", "b", 11750, "x"), tr("a", "b", 16000),
  ],
};
export const group = { id: "g", name: "2026 신소재공학과 MT", destinationName: "청평호 펜션" };
export const trip = (s: Settlement | null, ended = false): Trip => ({
  id: "t", groupId: "g", destinationName: "청평호 펜션", startedAt: "2026-11-15T09:00:00+09:00",
  endedAt: ended ? "2026-11-15T18:00:00+09:00" : null, settlement: s,
});
export const devOnly = () => process.env.NODE_ENV !== "production";
