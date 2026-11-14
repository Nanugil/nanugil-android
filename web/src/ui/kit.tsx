"use client";
// 카드 없는 화면 부품. 흰 바탕 위에 구역(Section)과 줄(Item)만 쓰고, 간격은 20px 하나로 맞춘다.
import styled from "@emotion/styled";
import Link from "next/link";
import type { ReactNode } from "react";
import { Icon } from "./Icon";
import { styleOnly } from "./styleProps";
import { t } from "./theme";

/** 화면의 한 구역. 구분선 없이 여백(구역 사이 32px)으로만 나눈다. */
export const Section = styled.section`display: grid; gap: 16px; padding-block: 16px;`;
export const SectionHead = styled.div`display: flex; align-items: baseline; justify-content: space-between; gap: 12px;
  h2 { font-size: 15px; font-weight: 600; color: ${t.color.ink}; }
  span { font-size: 13px; color: ${t.color.muted}; font-variant-numeric: tabular-nums; }`;
export const Hint = styled.p`font-size: 13px; line-height: 1.5; color: ${t.color.muted};`;
export const Lead = styled.p`font-size: 15px; line-height: 1.55; color: ${t.color.ink2};`;
export const Title = styled.h1`font-size: 26px; font-weight: 700; line-height: 1.3; letter-spacing: -0.02em;`;

/** 줄 목록. 줄 사이는 선 없이 64px 높이로만 나눈다. */
export const List = styled.ul`list-style: none; margin: 0; padding: 0; display: grid;`;
export const Item = styled.li`position: relative; display: flex; align-items: center; gap: 12px; min-height: 64px;`;
export const ItemLink = styled(Link)`display: flex; align-items: center; gap: 12px; flex: 1; min-width: 0; align-self: stretch;
  text-decoration: none; color: inherit;
  > .material-symbols-rounded:last-child { color: ${t.color.faint}; font-size: 20px; margin-right: -6px; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; border-radius: 8px; }`;
export const ItemText = styled.div`display: grid; gap: 2px; flex: 1; min-width: 0;
  strong { font-size: 15px; font-weight: 500; color: ${t.color.ink}; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  span { font-size: 13px; color: ${t.color.muted}; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`;
export const Value = styled.span`flex: none; font-size: 15px; font-weight: 600; color: ${t.color.ink}; font-variant-numeric: tabular-nums; white-space: nowrap;`;
export const Avatar = styled.span`display: grid; place-items: center; flex: none; width: 40px; height: 40px; border-radius: 50%;
  background: ${t.color.fill}; color: ${t.color.ink2}; font-size: 15px; font-weight: 600;
  .material-symbols-rounded { font-size: 20px; }`;

/** 상태 표시: 배경·테두리 없는 색 글자. 색만으로 구분하지 않도록 늘 글자로 쓴다. */
export const Tag = styled("span", styleOnly("tone"))<{ tone?: "neutral" | "accent" | "success" }>`
  display: inline-flex; align-items: center; gap: 2px; flex: none; font-size: 13px; font-weight: 500; white-space: nowrap;
  color: ${(p) => (p.tone === "accent" ? t.color.accent : p.tone === "success" ? t.color.success : t.color.muted)};
  .material-symbols-rounded { font-size: 16px; }
`;

// 값이 바뀔 때 잠깐 밝아지는 덮개 (useFlashOnChange가 opacity만 움직인다)
export const Flash = styled.span`position: absolute; inset: 0 -8px; pointer-events: none; opacity: 0; border-radius: 10px;
  background: color-mix(in oklab, var(--color-accent) 12%, transparent);`;

export const IconButton = styled.button`display: grid; place-items: center; width: 44px; height: 44px; border-radius: 12px;
  border: 0; background: transparent; color: ${t.color.ink}; cursor: pointer;
  transition: background-color ${t.dur.micro} ${t.ease.out};
  @media (hover: hover) { &:hover { background: ${t.color.fill}; } }
  &:active { background: ${t.color.fill}; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }`;

const Head = styled.header`display: grid; grid-template-columns: 44px minmax(0, 1fr) 44px; align-items: center; gap: 8px;
  min-height: 56px; margin-inline: -14px;
  h1 { font-size: 17px; font-weight: 600; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`;

const BackLink = styled(Link)`display: grid; place-items: center; width: 44px; height: 44px; border-radius: 12px; color: ${t.color.ink}; text-decoration: none;
  .material-symbols-rounded { font-size: 22px; }
  @media (hover: hover) { &:hover { background: ${t.color.fill}; } }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }`;

/** 안쪽 화면 머리: 왼쪽 뒤로, 가운데 제목, 오른쪽 동작 하나. */
export function PageHeader({ title, back, action }: { title: string; back?: string; action?: ReactNode }) {
  return (
    <Head>
      {back ? <BackLink href={back} aria-label="뒤로"><Icon name="arrow_back_ios_new" /></BackLink> : <span />}
      <h1>{title}</h1>
      {action ?? <span />}
    </Head>
  );
}
