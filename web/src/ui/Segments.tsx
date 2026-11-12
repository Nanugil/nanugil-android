"use client";
import styled from "@emotion/styled";
import { styleOnly } from "./styleProps";
import { t } from "./theme";

const Row = styled("div", styleOnly("count"))<{ count: number }>`display: grid; grid-template-columns: repeat(${(p) => p.count}, minmax(0, 1fr)); gap: 4px;`;
const Bar = styled("span", styleOnly("on"))<{ on: boolean }>`height: 28px; border-radius: 6px;
  background: ${(p) => (p.on ? t.color.accent : t.color.track)};`;

/** 칸으로 나뉜 게이지 (레퍼런스의 배터리 막대). done/total만큼 칸을 채운다. 칸 수는 최대 10. */
export function Segments({ done, total, label }: { done: number; total: number; label: string }) {
  const count = Math.max(1, Math.min(total, 10));
  const lit = total === 0 ? 0 : Math.round((done / total) * count);
  return (
    <Row count={count} role="img" aria-label={label}>
      {Array.from({ length: count }, (_, i) => <Bar key={i} on={i < lit} />)}
    </Row>
  );
}
