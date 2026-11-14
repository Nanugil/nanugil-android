"use client";
import { Icon } from "@/ui/Icon";
import { Hint, Item, ItemLink, ItemText, List, PageHeader, Section } from "@/ui/kit";
import { Screen } from "@/ui/Screen";

const PAGES = [
  { href: "/preview/home", name: "홈", sub: "그룹 2개, 보낼 금액 있음" },
  { href: "/preview/empty", name: "홈", sub: "참여한 그룹이 없을 때" },
  { href: "/preview/join", name: "초대 코드 입력", sub: "6자리 코드" },
  { href: "/preview/group", name: "그룹", sub: "운행 중, 멤버 8명" },
  { href: "/preview/settle", name: "정산", sub: "동승자 김민수 화면" },
  { href: "/preview/driver", name: "정산", sub: "운전자 강운전 화면, 받을 금액" },
  { href: "/preview/me", name: "내 정보", sub: "이름, 이메일, 로그아웃" },
  { href: "/login", name: "로그인", sub: "이메일, 소셜 로그인" },
  { href: "/signup", name: "회원가입", sub: "이메일, 비밀번호, 이름" },
];

export function PreviewIndex() {
  return (
    <Screen nav={false}>
      <PageHeader title="화면 미리보기" />
      <Hint>개발 서버 전용 가짜 데이터 화면입니다. 버튼은 서버와 연결되지 않습니다.</Hint>
      <Section>
        <List>
          {PAGES.map((p) => (
            <Item key={p.href}>
              <ItemLink href={p.href}>
                <ItemText><strong>{p.name}</strong><span>{p.sub}</span></ItemText>
                <Icon name="chevron_right" />
              </ItemLink>
            </Item>
          ))}
        </List>
      </Section>
    </Screen>
  );
}
