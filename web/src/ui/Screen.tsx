"use client";
import styled from "@emotion/styled";
import Link from "next/link";
import type { ReactNode } from "react";
import { Icon } from "./Icon";
import { TabBar } from "./TabBar";
import { styleOnly } from "./styleProps";
import { t } from "./theme";

const Bar = styled.header`
  position: sticky; top: 0; z-index: var(--z-sticky);
  display: flex; align-items: center; justify-content: space-between; gap: ${t.space.sm};
  min-height: 56px; padding: 0 ${t.gutter};
  background: ${t.color.paper};
  border-bottom: 1px solid ${t.color.rule};
`;
const Brand = styled(Link)`
  display: inline-flex; align-items: center; gap: ${t.space.xs};
  min-height: 44px; font-weight: 700; font-size: ${t.text.lg}; text-decoration: none;
`;
const Mark = styled.img`width: 28px; height: 28px; border-radius: 8px;`;
const Main = styled("main", styleOnly("wide"))<{ wide: boolean }>`
  width: 100%; max-width: ${(p) => (p.wide ? t.containerWide : t.container)}; margin-inline: auto;
  padding: ${t.space.lg} ${t.gutter} calc(${t.space.x2l} + 72px + env(safe-area-inset-bottom));
  display: grid; gap: ${t.space.lg}; align-content: start;
`;

/** 모든 화면의 틀: 위쪽 바(워드마크 + 동작 하나), 가운데 한 줄 본문, 아래 탭바(로그인 전 화면은 nav={false}). */
export function Screen({ action, children, nav = true, bar = true, wide = false }:
  { action?: ReactNode; children: ReactNode; nav?: boolean; bar?: boolean; wide?: boolean }) {
  return (
    <>
      {bar && <Bar>
        <Brand href="/" aria-label="나누길 홈"><Mark src="/icon.svg" alt="" width={28} height={28} />나누길</Brand>
        {action}
      </Bar>}
      <Main wide={wide}>{children}</Main>
      {nav && <TabBar />}
    </>
  );
}

export const BackLink = styled(Link)`
  display: inline-flex; align-items: center; gap: ${t.space.x2s}; min-height: 44px;
  color: ${t.color.ink2}; text-decoration: none; font-size: ${t.text.sm};
`;

export function Back({ href, label }: { href: string; label: string }) {
  return <BackLink href={href}><Icon name="arrow_back" />{label}</BackLink>;
}
