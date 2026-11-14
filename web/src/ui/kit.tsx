"use client";
// 모든 화면이 함께 쓰는 촘촘한 가로 정렬 부품 (홈 레이아웃 기준). 카드 안에 카드를 넣지 않고 줄로 나눈다.
import styled from "@emotion/styled";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { Icon } from "./Icon";
import { styleOnly } from "./styleProps";
import { t } from "./theme";

export const Body = styled.div`display: grid; gap: 10px;`;
export const Tile = styled("section", styleOnly("span", "wideSpan", "pad"))<{ span?: number; wideSpan?: number; pad?: "sm" | "md" }>`
  grid-column: span ${(p) => p.span ?? 1};
  @media (min-width: 48rem) { grid-column: span ${(p) => p.wideSpan ?? p.span ?? 1}; }
  display: grid; gap: 12px; align-content: start; min-width: 0;
  padding: ${(p) => (p.pad === "md" ? "20px" : "14px 16px")}; border-radius: 18px;
  background: ${t.color.card}; border: 1px solid ${t.color.rule};
`;
export const Line = styled.div`display: flex; align-items: center; justify-content: space-between; gap: ${t.space.sm}; min-width: 0;`;
export const Left = styled.div`display: flex; align-items: center; gap: 10px; min-width: 0;
  > span { white-space: nowrap; }`;
export const Stack = styled.div`display: grid; min-width: 0; line-height: 1.35;
  b { font-size: ${t.text.sm}; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`;
export const Label = styled.span`font-size: ${t.text.xs}; color: ${t.color.muted};`;
export const Text = styled.p`font-size: ${t.text.sm}; color: ${t.color.ink2}; line-height: 1.5;`;
export const IconBox = styled("span", styleOnly("tone"))<{ tone?: "plain" | "accent" | "success" | "dark" }>`
  display: grid; place-items: center; flex: none; width: 36px; height: 36px; border-radius: 11px;
  border: 1px solid ${(p) => (p.tone === "dark" ? "transparent" : t.color.rule)};
  background: ${(p) => p.tone === "accent" ? "color-mix(in oklab, var(--color-accent) 10%, var(--color-card))"
    : p.tone === "success" ? "color-mix(in oklab, var(--color-success) 12%, var(--color-card))"
    : p.tone === "dark" ? t.color.cta : t.color.cardStrong};
  color: ${(p) => p.tone === "accent" ? t.color.accent : p.tone === "success" ? t.color.success : p.tone === "dark" ? t.color.ctaInk : t.color.ink};
  .material-symbols-rounded { font-size: 20px; }
`;
export const Big = styled.span`font-size: ${t.text.xl}; font-weight: 700; line-height: 1; font-variant-numeric: tabular-nums; white-space: nowrap;
  small { font-size: ${t.text.xs}; font-weight: 500; color: ${t.color.muted}; margin-left: 2px; }`;
export const Chip = styled("span", styleOnly("tone"))<{ tone: "accent" | "plain" | "success" }>`
  display: inline-flex; align-items: center; gap: 2px; flex: none; min-height: 28px; padding: 0 10px;
  border-radius: ${t.radius.pill}; white-space: nowrap; font-size: ${t.text.xs}; font-weight: 600;
  .material-symbols-rounded { font-size: 16px; }
  border: 1px solid ${(p) => (p.tone === "accent" ? t.color.accent : p.tone === "success" ? t.color.success : t.color.rule)};
  color: ${(p) => (p.tone === "accent" ? t.color.accent : p.tone === "success" ? t.color.success : t.color.ink2)};
  background: ${(p) => p.tone === "accent" ? "color-mix(in oklab, var(--color-accent) 8%, var(--color-card))"
    : p.tone === "success" ? "color-mix(in oklab, var(--color-success) 8%, var(--color-card))" : t.color.card};
`;
export const Where = styled.span`display: inline-flex; align-items: center; gap: 2px; font-size: ${t.text.xs}; color: ${t.color.ink2};
  .material-symbols-rounded { color: ${t.color.warn}; font-size: 14px; }`;
export const SectionHead = styled.h2`font-size: ${t.text.md}; display: flex; justify-content: space-between; align-items: baseline;
  span { font-size: ${t.text.xs}; color: ${t.color.muted}; font-weight: 500; }`;
export const Rows = styled.ul`list-style: none; margin: 0; padding: 0; display: grid;`;
export const Row = styled.li`position: relative; display: flex; align-items: center; gap: ${t.space.sm}; min-height: 52px;
  border-bottom: 1px solid ${t.color.rule}; &:last-child { border-bottom: 0; }`;
export const RowLink = styled(Link)`display: flex; align-items: center; gap: ${t.space.sm}; min-height: 52px; flex: 1; min-width: 0;
  text-decoration: none; > .material-symbols-rounded:last-child { color: ${t.color.muted}; font-size: 20px; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }`;
// 값이 바뀔 때 잠깐 밝아지는 덮개 (useFlashOnChange가 opacity만 움직인다)
export const Flash = styled.span`position: absolute; inset: 0; pointer-events: none; opacity: 0; border-radius: 10px;
  background: color-mix(in oklab, var(--color-accent) 18%, transparent);`;
export const Initial = styled.span`display: grid; place-items: center; flex: none; width: 32px; height: 32px; border-radius: 10px;
  background: ${t.color.cardStrong}; font-size: ${t.text.xs}; font-weight: 700;`;

const Head = styled.header`display: grid; grid-template-columns: 44px minmax(0, 1fr) 44px; align-items: center; gap: ${t.space.xs};
  h1 { font-size: ${t.text.md}; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`;
export const RoundButton = styled.button`display: grid; place-items: center; width: 44px; height: 44px; border-radius: 14px;
  border: 1px solid ${t.color.rule}; background: ${t.color.card}; color: ${t.color.ink}; cursor: pointer;
  &:active { transform: scale(0.96); } &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }
  transition: transform ${t.dur.micro} ${t.ease.out};`;

/** 안쪽 화면 머리: 왼쪽 뒤로, 가운데 제목, 오른쪽 동작 하나 (레퍼런스 세 번째 화면의 둥근 사각 버튼). */
export function PageHeader({ title, back, action }: { title: string; back?: string; action?: ReactNode }) {
  const router = useRouter();
  return (
    <Head>
      {back ? <RoundButton type="button" aria-label="뒤로" onClick={() => (history.length > 1 ? router.back() : router.push(back))}>
        <Icon name="arrow_back" /></RoundButton> : <span />}
      <h1>{title}</h1>
      {action ?? <span />}
    </Head>
  );
}
