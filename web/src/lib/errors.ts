/** 서버 RPC가 돌려준 오류. code는 docs/group-trip-api.md "오류 코드" 표의 값이다. */
export class ServerError extends Error {
  constructor(public readonly code: string) {
    super(code);
    this.name = "ServerError";
  }
}

const MESSAGES: Record<string, string> = {
  NOT_MEMBER: "이 그룹의 멤버가 아닙니다",
  NOT_FOUND: "정보를 찾을 수 없습니다",
  INVALID_CODE: "초대 코드를 다시 확인해 주세요",
  TOO_MANY_ATTEMPTS: "시도 횟수를 넘었습니다. 10분 뒤에 다시 시도해 주세요",
  ALREADY_MEMBER: "이미 참여한 그룹입니다",
  MEMBER_TAKEN: "다른 사람이 이미 선택한 이름입니다",
  NOT_YOUR_TRANSFER: "본인의 송금만 표시할 수 있습니다",
  INVALID_TRANSFER: "정산 내용을 다시 확인해 주세요",
  INVALID_PAY_LINK: "카카오페이 송금 링크를 입력해 주세요",
  SETTLEMENT_CHANGED: "운전자가 정산을 다시 요청했습니다. 바뀐 금액을 확인해 주세요",
  ALREADY_REQUESTED: "다른 운전자가 이미 정산을 요청했습니다",
};

export function messageFor(code: string): string {
  return MESSAGES[code] ?? "일시적인 오류입니다. 잠시 후 다시 시도해 주세요";
}
