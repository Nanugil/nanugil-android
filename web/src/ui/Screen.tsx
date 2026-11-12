"use client";
import styled from "@emotion/styled";
import Link from "next/link";
import type { ReactNode } from "react";
import { Icon } from "./Icon";
import { t } from "./theme";

const Bar = styled.header`
  position: sticky; top: 0; z-index: var(--z-sticky);
  display: flex; align-items: center; justify-content: space-between; gap: ${t.space.sm};
  min-height: 56px; padding: 0 ${t.gutter};
  background: color-mix(in oklab, var(--color-paper) 72%, transparent);
  backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid ${t.color.rule};
`;
const Brand = styled(Link)`
  display: inline-flex; align-items: center; gap: ${t.space.xs};
  min-height: 44px; font-weight: 700; font-size: ${t.text.lg}; text-decoration: none;
`;
const Mark = styled.img`width: 28px; height: 28px; border-radius: 8px;`;
const Main = styled.main`
  width: 100%; max-width: ${t.container}; margin-inline: auto;
  padding: ${t.space.lg} ${t.gutter} ${t.space.x2l};
  display: grid; gap: ${t.space.lg}; align-content: start;
`;

/** 모든 화면의 틀: 위쪽 바(워드마크 + 동작 하나)와 가운데 한 줄 본문. */
export function Screen({ action, children }: { action?: ReactNode; children: ReactNode }) {
  return (
    <>
      <Bar>
        <Brand href="/" aria-label="나누길 홈"><Mark src="/icon.svg" alt="" width={28} height={28} />나누길</Brand>
        {action}
      </Bar>
      <Main>{children}</Main>
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
