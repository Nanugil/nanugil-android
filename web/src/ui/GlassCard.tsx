"use client";
import styled from "@emotion/styled";
import { t } from "./theme";

/** 평면 카드: 단색 면 + 1px 테두리, 그림자 없음. 카드 안에 카드를 넣지 않는다 (안쪽은 줄로 나눈다). */
export const GlassCard = styled.section`
  display: grid;
  gap: ${t.space.md};
  padding: ${t.space.lg};
  border-radius: ${t.radius.card};
  background: ${t.color.card};
  border: 1px solid ${t.color.rule};
`;
