"use client";
import styled from "@emotion/styled";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { t } from "./theme";

// 탭 아이콘은 선 굵기를 맞춘 SVG로 직접 그린다 (24 격자, 둥근 끝)
const PATHS = {
  home: <><path d="M4 10.2 12 4l8 6.2V19a1.5 1.5 0 0 1-1.5 1.5H15v-5.2a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v5.2H5.5A1.5 1.5 0 0 1 4 19z" /></>,
  join: <><circle cx="9.5" cy="8.5" r="3.5" /><path d="M3.5 20c.7-3.3 3.1-5.3 6-5.3s5.3 2 6 5.3" /><path d="M18.5 7.5v6M15.5 10.5h6" /></>,
  me: <><circle cx="12" cy="8.5" r="4" /><path d="M4.5 20.5c1-3.8 3.9-6 7.5-6s6.5 2.2 7.5 6" /></>,
} as const;

const TABS = [
  { key: "home", href: "/", label: "홈" },
  { key: "join", href: "/join", label: "그룹 참여" },
  { key: "me", href: "/me", label: "내 정보" },
] as const;

function TabIcon({ name, active }: { name: keyof typeof PATHS; active: boolean }) {
  return (
    <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={active ? 2 : 1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {PATHS[name]}
    </svg>
  );
}

/** 지금 주소가 어느 탭에 속하는지. 그룹·정산 화면은 홈 아래로 본다. 미리보기(/preview/…)도 같은 규칙. */
export function activeTab(pathname: string): (typeof TABS)[number]["key"] {
  const path = pathname.replace(/^\/preview(?=\/|$)/, "") || "/";
  if (path.startsWith("/join")) return "join";
  if (path.startsWith("/me")) return "me";
  return "home";
}

const Bar = styled.nav`
  position: fixed; left: 0; right: 0; bottom: 0; z-index: var(--z-sticky);
  padding-bottom: env(safe-area-inset-bottom);
  background: color-mix(in oklab, #FFFFFF 94%, transparent);
  border-top: 1px solid ${t.color.rule};
`;
const List = styled.ul`list-style: none; margin: 0 auto; padding: 0; max-width: ${t.container}; display: flex;`;
const Item = styled.li`flex: 1 1 0; min-width: 0;`;
const custom = { shouldForwardProp: (prop: string) => prop !== "active" };
const Tab = styled(Link, custom)<{ active: boolean }>`
  display: grid; place-items: center; height: 56px; text-decoration: none;
  color: ${(p) => (p.active ? t.color.ink : t.color.muted)};
  svg { transition: transform ${t.dur.short} ${t.ease.out}; }
  &[aria-current="page"] svg { transform: translateY(-1px); }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: -6px; border-radius: 12px; }
  &:active { opacity: 0.6; }
`;

/** 아래 탭바. 아이콘만 보이고(선택된 탭은 채운 진한 아이콘), 이름은 aria-label로 읽힌다. */
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
                <TabIcon name={tab.key} active={on} />
              </Tab>
            </Item>
          );
        })}
      </List>
    </Bar>
  );
}
