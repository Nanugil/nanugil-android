/** 서버 RPC가 돌려준 오류. code는 docs/group-trip-api.md "오류 코드" 표의 값이다. */
export class ServerError extends Error {
  constructor(public readonly code: string) {
    super(code);
    this.name = "ServerError";
  }
}

const MESSAGES: Record<string, string> = {
  NOT_MEMBER: "이 그룹의 멤버가 아닙니다.",
  NOT_FOUND: "요청한 정보를 찾을 수 없습니다.",
  INVALID_CODE: "초대 코드가 올바르지 않습니다. 다시 확인해 주세요.",
  TOO_MANY_ATTEMPTS: "시도 횟수를 초과했습니다. 10분 후 다시 시도해 주세요.",
  ALREADY_MEMBER: "이미 참여한 그룹입니다.",
  MEMBER_TAKEN: "다른 사람이 이미 선택한 이름입니다. 다른 이름을 선택해 주세요.",
  NOT_YOUR_TRANSFER: "본인 송금만 완료로 표시할 수 있습니다.",
  INVALID_TRANSFER: "정산 정보가 맞지 않습니다. 새로고침한 뒤 다시 시도해 주세요.",
  INVALID_PAY_LINK: "카카오페이 송금 링크를 입력해 주세요.",
  SETTLEMENT_CHANGED: "운전자가 정산을 다시 요청했습니다. 바뀐 금액을 확인해 주세요.",
  SETTLEMENT_LOCKED: "이미 송금이 표시된 정산은 바꿀 수 없습니다.",
  MEMBER_HAS_HISTORY: "운행 기록이 있는 멤버는 그룹에서 뺄 수 없습니다.",
  ALREADY_REQUESTED: "다른 운전자가 이미 정산을 요청했습니다.",
};

export function messageFor(code: string): string {
  return MESSAGES[code] ?? "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
}
