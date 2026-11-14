"use client";
// 카드 없는 화면 부품. 흰 바탕 위에 구역(Section)과 줄(Item)만 쓰고, 간격은 20px 하나로 맞춘다.
import styled from "@emotion/styled";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { Icon } from "./Icon";
import { styleOnly } from "./styleProps";
import { t } from "./theme";

/** 화면의 한 구역. 구역 사이는 얇은 선으로 나눈다 (첫 구역은 선 없음). */
export const Section = styled.section`
  display: grid; gap: 12px; padding-block: 20px;
  & + & { border-top: 1px solid ${t.color.rule}; }
`;
export const SectionHead = styled.div`display: flex; align-items: baseline; justify-content: space-between; gap: 12px;
  h2 { font-size: 15px; font-weight: 600; color: ${t.color.ink}; }
  span { font-size: 13px; color: ${t.color.muted}; font-variant-numeric: tabular-nums; }`;
export const Hint = styled.p`font-size: 13px; line-height: 1.5; color: ${t.color.muted};`;
export const Lead = styled.p`font-size: 15px; line-height: 1.55; color: ${t.color.ink2};`;
export const Title = styled.h1`font-size: 22px; font-weight: 600; line-height: 1.35; letter-spacing: -0.01em;`;

/** 줄 목록. 줄 사이 구분선은 글자 시작점부터 끝까지만. */
export const List = styled.ul`list-style: none; margin: 0; padding: 0; display: grid;`;
export const Item = styled.li`position: relative; display: flex; align-items: center; gap: 12px; min-height: 60px; padding-block: 10px;
  & + & { border-top: 1px solid ${t.color.rule}; }`;
export const ItemLink = styled(Link)`display: flex; align-items: center; gap: 12px; flex: 1; min-width: 0; min-height: 60px;
  margin-block: -10px; padding-block: 10px; text-decoration: none; color: inherit;
  > .material-symbols-rounded:last-child { color: ${t.color.muted}; font-size: 20px; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; border-radius: 8px; }`;
export const ItemText = styled.div`display: grid; gap: 2px; flex: 1; min-width: 0;
  strong { font-size: 15px; font-weight: 500; color: ${t.color.ink}; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  span { font-size: 13px; color: ${t.color.muted}; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`;
export const Value = styled.span`flex: none; font-size: 15px; font-weight: 600; color: ${t.color.ink}; font-variant-numeric: tabular-nums; white-space: nowrap;`;
export const Avatar = styled.span`display: grid; place-items: center; flex: none; width: 36px; height: 36px; border-radius: 50%;
  background: ${t.color.fill}; color: ${t.color.ink2}; font-size: 13px; font-weight: 600;`;

/** 상태 표시: 테두리 없는 작은 연한 태그. 색만으로 구분하지 않도록 늘 글자를 쓴다. */
export const Tag = styled("span", styleOnly("tone"))<{ tone?: "neutral" | "accent" | "success" }>`
  display: inline-flex; align-items: center; flex: none; height: 22px; padding: 0 8px; border-radius: 6px;
  font-size: 12px; font-weight: 600; white-space: nowrap;
  color: ${(p) => (p.tone === "accent" ? t.color.accent : p.tone === "success" ? t.color.success : t.color.ink2)};
  background: ${(p) => p.tone === "accent" ? "color-mix(in oklab, var(--color-accent) 10%, #FFFFFF)"
    : p.tone === "success" ? "color-mix(in oklab, var(--color-success) 10%, #FFFFFF)" : t.color.fill};
`;

/** 숫자 요약 두 칸 (카드 없이 세로선으로 나눔) */
export const Stats = styled.dl`display: grid; grid-template-columns: 1fr 1fr; margin: 0;
  > div { display: grid; gap: 4px; padding-block: 4px; }
  > div + div { border-left: 1px solid ${t.color.rule}; padding-left: 20px; }
  dt { font-size: 13px; color: ${t.color.muted}; }
  dd { margin: 0; font-size: 20px; font-weight: 600; font-variant-numeric: tabular-nums; }
  dd small { font-size: 13px; font-weight: 500; color: ${t.color.muted}; margin-left: 2px; }`;

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
  min-height: 56px; margin-inline: -12px;
  h1 { font-size: 17px; font-weight: 600; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`;

/** 안쪽 화면 머리: 왼쪽 뒤로, 가운데 제목, 오른쪽 동작 하나. */
export function PageHeader({ title, back, action }: { title: string; back?: string; action?: ReactNode }) {
  const router = useRouter();
  return (
    <Head>
      {back ? <IconButton type="button" aria-label="뒤로" onClick={() => (history.length > 1 ? router.back() : router.push(back))}>
        <Icon name="arrow_back_ios_new" /></IconButton> : <span />}
      <h1>{title}</h1>
      {action ?? <span />}
    </Head>
  );
}
