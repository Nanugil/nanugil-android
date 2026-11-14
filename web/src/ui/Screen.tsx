"use client";
import styled from "@emotion/styled";
import { useEffect, useRef, type ReactNode } from "react";
import { markReady, usePageEnter } from "./motion";
import { styleOnly } from "./styleProps";
import { TabBar } from "./TabBar";
import { t } from "./theme";

const Main = styled("main", styleOnly("wide", "nav"))<{ wide: boolean; nav: boolean }>`
  width: 100%; max-width: ${(p) => (p.wide ? t.containerWide : t.container)}; margin-inline: auto;
  padding: calc(8px + env(safe-area-inset-top)) ${t.gutter}
    ${(p) => (p.nav ? `calc(${t.space.lg} + 76px + env(safe-area-inset-bottom))` : t.space.x2l)};
  display: grid; gap: 0; align-content: start;
  > button, > a { margin-top: 8px; }
`;

/** 모든 화면의 틀: 한 줄 본문과 아래 탭바(로그인 전 화면은 nav={false}). 머리는 각 화면이 그린다. */
export function Screen({ children, nav = true, wide = false }: { children: ReactNode; nav?: boolean; wide?: boolean }) {
  const main = useRef<HTMLElement>(null);
  usePageEnter(main);
  useEffect(() => { markReady(); }, []); // 자식 효과가 다 돈 뒤에 실행된다
  return (
    <>
      <Main ref={main} wide={wide} nav={nav}>{children}</Main>
      {nav && <TabBar />}
    </>
  );
}
