"use client";
import styled from "@emotion/styled";
import { useRef } from "react";
import { formatWon } from "@/lib/format";
import { GlassCard } from "./GlassCard";
import { useCountUp, useRiseIn } from "./motion";
import { t } from "./theme";

const Label = styled.p`font-size: ${t.text.sm}; color: ${t.color.ink2};`;
const Figure = styled.p`font-size: ${t.text.figure}; font-weight: 800; line-height: 1.1; letter-spacing: -0.02em; font-variant-numeric: tabular-nums;`;
const Sub = styled.p`font-size: ${t.text.sm}; color: ${t.color.muted};`;

/** 08 화면의 1인 금액. 정산 화면에서 가장 큰 글자다. 올라오면서 0원부터 세어 올라간다. */
export function AmountCard({ label, amountWon, sub }: { label: string; amountWon: number; sub?: string }) {
  const card = useRef<HTMLElement>(null);
  const figure = useRef<HTMLParagraphElement>(null);
  useRiseIn(card);
  useCountUp(figure, amountWon);
  return (
    <GlassCard ref={card} aria-label={`${label} ${formatWon(amountWon)}`}>
      <Label>{label}</Label>
      <Figure ref={figure} aria-hidden="true">{formatWon(amountWon)}</Figure>
      {sub && <Sub>{sub}</Sub>}
    </GlassCard>
  );
}
