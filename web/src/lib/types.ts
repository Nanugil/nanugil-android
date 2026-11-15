/** 운전자가 정산을 요청한 순간의 송금 한 건. 시각은 ISO 문자열, 아직이면 null. */
export type Transfer = { from: string; to: string; amountWon: number; sentAt: string | null; receivedAt: string | null };
/** trips.settlement (docs/group-trip-api.md P2) */
export type Settlement = { requestedAt: string; requestedBy: string; perPersonWon: number; transfers: Transfer[] };
export type Member = { id: string; userId: string | null; displayName: string; payLink: string | null };
export type Trip = {
  id: string; groupId: string; destinationName: string; startedAt: string; endedAt: string | null; settlement: Settlement | null;
};
export type Group = { id: string; name: string; destinationName: string };
export type InviteResult = { groupId: string; groupName: string; memberCount: number };
