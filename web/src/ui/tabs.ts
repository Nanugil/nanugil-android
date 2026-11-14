// 탭바 선택 규칙 (화면 코드와 분리한 순수 함수)

/** 지금 주소가 어느 탭에 속하는지. 그룹·정산 화면은 홈 아래로 본다. 미리보기(/preview/…)도 같은 규칙. */
export function activeTab(pathname: string): "home" | "join" | "me" {
  const path = pathname.replace(/^\/preview(?=\/|$)/, "") || "/";
  if (path.startsWith("/join")) return "join";
  if (path.startsWith("/me")) return "me";
  return "home";
}
