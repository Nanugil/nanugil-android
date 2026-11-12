import Link from "next/link";
import { notFound } from "next/navigation";
import { devOnly } from "./mock";

const PAGES = [
  ["/preview/home", "홈 (그룹 2개, 보낼 돈 있음)"],
  ["/preview/empty", "홈 (그룹 없음)"],
  ["/preview/join", "코드로 참여"],
  ["/preview/group", "그룹 (운행 중)"],
  ["/preview/settle", "정산 (동승자 김민수)"],
  ["/preview/driver", "정산 (운전자 강운전, 받을 돈)"],
  ["/login", "로그인"],
  ["/signup", "가입"],
];

export default function Preview() {
  if (!devOnly()) notFound();
  return (
    <main style={{ padding: 24, display: "grid", gap: 12 }}>
      <h1>화면 미리보기 (개발 전용, 가짜 데이터)</h1>
      {PAGES.map(([href, label]) => <Link key={href} href={href} style={{ minHeight: 44, display: "block" }}>{label}</Link>)}
    </main>
  );
}
