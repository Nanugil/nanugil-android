/** 서버 RPC가 돌려준 오류. code는 docs/group-trip-api.md "오류 코드" 표의 값이다. */
export class ServerError extends Error {
  constructor(public readonly code: string) {
    super(code);
    this.name = "ServerError";
  }
}

const MESSAGES: Record<string, string> = {
  NOT_MEMBER: "이 그룹의 멤버가 아니에요",
  NOT_FOUND: "찾을 수 없어요",
  INVALID_CODE: "초대 코드를 다시 확인해 주세요",
  TOO_MANY_ATTEMPTS: "잠시 후 다시 시도해 주세요",
  ALREADY_MEMBER: "이미 참여한 그룹이에요",
  MEMBER_TAKEN: "이미 다른 사람이 고른 이름이에요",
  NOT_YOUR_TRANSFER: "내 송금만 표시할 수 있어요",
  INVALID_TRANSFER: "정산 내용을 다시 확인해 주세요",
  INVALID_PAY_LINK: "카카오페이 송금 링크를 넣어 주세요",
};

export function messageFor(code: string): string {
  return MESSAGES[code] ?? "잠시 후 다시 시도해 주세요";
}
