"use client";
import styled from "@emotion/styled";
import Link from "next/link";
import { GlassCard } from "@/ui/GlassCard";
import { Icon } from "@/ui/Icon";
import { Screen } from "@/ui/Screen";
import { t } from "@/ui/theme";

const Title = styled.h1`font-size: ${t.text.x2};`;
const Lead = styled.p`color: ${t.color.ink2};`;
const List = styled.ul`list-style: none; margin: 0; padding: 0; display: grid;`;
const Row = styled(Link)`
  display: flex; align-items: center; gap: ${t.space.sm}; min-height: 60px; padding: ${t.space.xs} 0;
  border-bottom: 1px solid ${t.color.rule}; text-decoration: none;
  li:last-child & { border-bottom: 0; }
  > .material-symbols-rounded:first-of-type { display: grid; place-items: center; width: 40px; height: 40px; flex: none;
    border-radius: ${t.radius.control}; background: ${t.color.cardStrong}; color: ${t.color.accent}; }
  > .material-symbols-rounded:last-of-type { margin-left: auto; color: ${t.color.muted}; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }
  @media (hover: hover) { &:hover span:nth-of-type(2) { color: ${t.color.accent}; } }
`;
const Text = styled.span`display: grid; transition: opacity ${t.dur.micro} ${t.ease.out};`;
const Sub = styled.span`font-size: ${t.text.xs}; color: ${t.color.muted};`;

const PAGES = [
  { href: "/preview/home", icon: "home", name: "홈", sub: "그룹 2개, 보낼 돈 있음" },
  { href: "/preview/empty", icon: "inbox", name: "홈", sub: "아직 참여한 그룹이 없을 때" },
  { href: "/preview/join", icon: "qr_code_2", name: "코드로 참여", sub: "6칸 초대 코드 입력" },
  { href: "/preview/group", icon: "groups", name: "그룹", sub: "운행 중, 멤버 8명" },
  { href: "/preview/settle", icon: "payments", name: "정산", sub: "동승자 김민수가 볼 때" },
  { href: "/preview/driver", icon: "account_balance_wallet", name: "정산", sub: "운전자 강운전이 볼 때, 받을 돈" },
  { href: "/preview/me", icon: "person", name: "내 정보", sub: "이름, 이메일, 로그아웃" },
  { href: "/login", icon: "login", name: "로그인", sub: "이메일, 소셜 로그인 자리" },
  { href: "/signup", icon: "person_add", name: "가입", sub: "이메일, 비밀번호, 이름" },
];

export function PreviewIndex() {
  return (
    <Screen>
      <Title>화면 미리보기</Title>
      <Lead>개발 서버에서만 열리는 가짜 데이터 화면이에요. 버튼은 서버에 연결되지 않아요.</Lead>
      <GlassCard>
        <List>
          {PAGES.map((p) => (
            <li key={p.href}>
              <Row href={p.href}>
                <Icon name={p.icon} />
                <Text><span>{p.name}</span><Sub>{p.sub}</Sub></Text>
                <Icon name="chevron_right" />
              </Row>
            </li>
          ))}
        </List>
      </GlassCard>
    </Screen>
  );
}
