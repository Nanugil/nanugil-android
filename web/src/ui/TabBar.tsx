"use client";
import styled from "@emotion/styled";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "./Icon";
import { t } from "./theme";

const TABS = [
  { key: "home", href: "/", icon: "home", label: "홈" },
  { key: "join", href: "/join", icon: "qr_code_scanner", label: "코드 참여" },
  { key: "me", href: "/me", icon: "person", label: "내 정보" },
] as const;

/** 지금 주소가 어느 탭에 속하는지. 그룹·정산 화면은 홈 아래로 본다. 미리보기(/preview/…)도 같은 규칙. */
export function activeTab(pathname: string): (typeof TABS)[number]["key"] {
  const path = pathname.replace(/^\/preview(?=\/|$)/, "") || "/";
  if (path.startsWith("/join")) return "join";
  if (path.startsWith("/me")) return "me";
  return "home";
}

const Bar = styled.nav`
  position: fixed; left: 0; right: 0; bottom: 0; z-index: var(--z-sticky);
  padding: ${t.space.xs} ${t.gutter} calc(${t.space.xs} + env(safe-area-inset-bottom));
  background: ${t.color.paper};
  border-top: 1px solid ${t.color.rule};
`;
const List = styled.ul`
  list-style: none; margin: 0 auto; padding: ${t.space.x2s}; max-width: ${t.container};
  display: flex; gap: ${t.space.x2s};
  border-radius: ${t.radius.pill}; background: ${t.color.card}; border: 1px solid ${t.color.rule};
`;
const custom = { shouldForwardProp: (prop: string) => prop !== "active" };
const Item = styled.li`flex: 1 1 0; min-width: 0;`;
const Tab = styled(Link, custom)<{ active: boolean }>`
  display: flex; align-items: center; justify-content: center; gap: ${t.space.xs};
  min-height: 48px; border-radius: ${t.radius.pill}; text-decoration: none; white-space: nowrap;
  color: ${(p) => (p.active ? t.color.ink : t.color.muted)};
  background: ${(p) => (p.active ? t.color.cardStrong : "transparent")};
  border: 1px solid ${(p) => (p.active ? t.color.rule : "transparent")};
  transition: background-color ${t.dur.short} ${t.ease.out}, color ${t.dur.short} ${t.ease.out};
  .material-symbols-rounded { font-size: 26px; color: ${(p) => (p.active ? t.color.accent : "inherit")};
    font-variation-settings: "FILL" ${(p) => (p.active ? 1 : 0)}; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }
  &:active { opacity: 0.7; }
`;

/** 아래 탭바. 아이콘만 보이고(선택된 탭은 채운 파란 아이콘 + 알약 배경), 이름은 aria-label로 읽힌다. */
export function TabBar() {
  const active = activeTab(usePathname() ?? "/");
  const prefix = (usePathname() ?? "").startsWith("/preview") ? "/preview" : "";
  return (
    <Bar aria-label="주요 메뉴">
      <List>
        {TABS.map((tab) => {
          const on = tab.key === active;
          const href = prefix ? `${prefix}${tab.href === "/" ? "/home" : tab.href}` : tab.href;
          return (
            <Item key={tab.key}>
              <Tab href={href} active={on} aria-label={tab.label} aria-current={on ? "page" : undefined}>
                <Icon name={tab.icon} />
              </Tab>
            </Item>
          );
        })}
      </List>
    </Bar>
  );
}
