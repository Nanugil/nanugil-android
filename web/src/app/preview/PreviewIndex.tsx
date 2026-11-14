"use client";
import { Icon } from "@/ui/Icon";
import { IconBox, Label, PageHeader, Row, RowLink, Rows, Stack, Tile } from "@/ui/kit";
import { Screen } from "@/ui/Screen";

const PAGES = [
  { href: "/preview/home", icon: "home", name: "홈", sub: "그룹 2개, 보낼 돈 있음" },
  { href: "/preview/empty", icon: "inbox", name: "홈", sub: "아직 참여한 그룹이 없을 때" },
  { href: "/preview/join", icon: "qr_code_scanner", name: "코드로 참여", sub: "6칸 초대 코드 입력" },
  { href: "/preview/group", icon: "groups", name: "그룹", sub: "운행 중, 멤버 8명" },
  { href: "/preview/settle", icon: "payments", name: "정산", sub: "동승자 김민수가 볼 때" },
  { href: "/preview/driver", icon: "account_balance_wallet", name: "정산", sub: "운전자 강운전이 볼 때, 받을 돈" },
  { href: "/preview/me", icon: "person", name: "내 정보", sub: "이름, 이메일, 로그아웃" },
  { href: "/login", icon: "login", name: "로그인", sub: "이메일, 소셜 로그인 자리" },
  { href: "/signup", icon: "person_add", name: "가입", sub: "이메일, 비밀번호, 이름" },
];

export function PreviewIndex() {
  return (
    <Screen nav={false}>
      <PageHeader title="화면 미리보기" />
      <Label>개발 서버에서만 열리는 가짜 데이터 화면이에요. 버튼은 서버에 연결되지 않아요.</Label>
      <Tile>
        <Rows>
          {PAGES.map((p) => (
            <Row key={p.href}>
              <RowLink href={p.href}>
                <IconBox><Icon name={p.icon} /></IconBox>
                <Stack style={{ flex: 1 }}><b>{p.name}</b><Label>{p.sub}</Label></Stack>
                <Icon name="chevron_right" />
              </RowLink>
            </Row>
          ))}
        </Rows>
      </Tile>
    </Screen>
  );
}
