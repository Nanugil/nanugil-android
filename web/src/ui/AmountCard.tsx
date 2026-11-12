"use client";
import styled from "@emotion/styled";
import { useRef } from "react";
import { formatWon } from "@/lib/format";
import { DotNumber } from "./DotNumber";
import { GlassCard } from "./GlassCard";
import { useCountUpValue, useRiseIn } from "./motion";
import { t } from "./theme";

const Card = styled(GlassCard)`
  justify-items: center; text-align: center; padding-block: ${t.space.xl};
`;
const Label = styled.p`font-size: ${t.text.sm}; color: ${t.color.ink2};`;
const Figure = styled.div`display: flex; align-items: flex-end; gap: ${t.space.xs}; max-width: 100%;`;
const Won = styled.span`font-size: ${t.text.x2}; font-weight: 700; line-height: 1;`;
const Sub = styled.p`display: inline-flex; align-items: center; min-height: 32px; padding: 0 ${t.space.sm};
  border-radius: ${t.radius.pill}; border: 1px solid ${t.color.rule}; font-size: ${t.text.xs}; color: ${t.color.ink2};`;

/** 08 화면의 1인 금액. 차량 계기판 같은 도트 숫자로 가장 크게, 0원부터 세어 올라간다. */
export function AmountCard({ label, amountWon, sub }: { label: string; amountWon: number; sub?: string }) {
  const card = useRef<HTMLElement>(null);
  useRiseIn(card);
  const shown = useCountUpValue(amountWon);
  return (
    <Card ref={card} aria-label={`${label} ${formatWon(amountWon)}`}>
      <Label>{label}</Label>
      <Figure><DotNumber text={new Intl.NumberFormat("ko-KR").format(shown)} height={64} /><Won aria-hidden="true">원</Won></Figure>
      {sub && <Sub>{sub}</Sub>}
    </Card>
  );
}
