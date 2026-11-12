/** 서버 초대 코드 규칙과 같다: 6자리, 헷갈리는 0/O, 1/I 제외 */
const CODE = /^[A-HJ-NP-Z2-9]{6}$/;

export function normalizeInviteCode(input: string): string | null {
  const code = input.replace(/\s/g, "").toUpperCase();
  return CODE.test(code) ? code : null;
}
