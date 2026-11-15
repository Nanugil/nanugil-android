import type { Settlement, Transfer } from "./types";

export type TransferStatus = "pending" | "sent" | "received";

/** 받았다고 표시됐으면 received, 보냈다고만 표시됐으면 sent, 아니면 pending */
export function statusOf(t: Transfer): TransferStatus {
  if (t.receivedAt) return "received";
  if (t.sentAt) return "sent";
  return "pending";
}

type Indexed = { index: number; transfer: Transfer };
export type SettlementView = { toSend: Indexed[]; toReceive: Indexed[]; perPersonWon: number; allDone: boolean };

/** 내 입장에서 본 정산. index는 서버 송금 목록의 순번이라 markSent·markReceived에 그대로 넘긴다. */
export function viewFor(s: Settlement, myMemberId: string): SettlementView {
  const all = s.transfers.map((transfer, index) => ({ index, transfer }));
  return {
    toSend: all.filter((x) => x.transfer.from === myMemberId),
    toReceive: all.filter((x) => x.transfer.to === myMemberId),
    perPersonWon: s.perPersonWon,
    allDone: s.transfers.length > 0 && s.transfers.every((t) => statusOf(t) === "received"),
  };
}
