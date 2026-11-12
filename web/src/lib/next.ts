/** 로그인 뒤 돌아갈 경로. 같은 사이트 경로만 받고, 다른 사이트로 빠지는 주소(//, /\, https:)는 홈으로 바꾼다. */
export function safeNext(next: string | null): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return "/";
  return next;
}
