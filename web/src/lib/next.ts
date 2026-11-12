/**
 * 로그인 뒤 돌아갈 경로. 브라우저와 같은 규칙으로 주소를 풀어 본 뒤 같은 사이트일 때만 경로를 돌려준다.
 * (URL 파서는 탭·줄바꿈을 지우므로 "/\t/evil.com"도 다른 사이트가 된다. 문자열 검사만으로는 막을 수 없다)
 */
export function safeNext(next: string | null): string {
  if (!next || !next.startsWith("/")) return "/";
  const base = "http://same.invalid";
  const url = new URL(next, base);
  return url.origin === base ? url.pathname + url.search + url.hash : "/";
}
