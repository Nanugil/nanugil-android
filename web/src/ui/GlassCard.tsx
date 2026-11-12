"use client";
import styled from "@emotion/styled";
import { t } from "./theme";

/** v5 차량 화면의 반투명 카드. 카드 안에 카드를 넣지 않는다 (안쪽은 줄로 나눈다). */
export const GlassCard = styled.section`
  display: grid;
  gap: ${t.space.md};
  padding: ${t.space.lg};
  border-radius: ${t.radius.card};
  background: ${t.color.card};
  border: 1px solid ${t.color.rule};
  backdrop-filter: blur(18px) saturate(140%);
  -webkit-backdrop-filter: blur(18px) saturate(140%);
`;
