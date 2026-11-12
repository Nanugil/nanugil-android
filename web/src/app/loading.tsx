"use client";
import styled from "@emotion/styled";
import { Screen } from "@/ui/Screen";
import { t } from "@/ui/theme";

const Bone = styled.div<{ h: number }>`
  height: ${(p) => p.h}px; border-radius: ${t.radius.card}; background: ${t.color.card}; border: 1px solid ${t.color.rule};
`;

/** 불러오는 동안: 카드 모양 자리 */
export default function Loading() {
  return (
    <Screen>
      <span className="visually-hidden" role="status">불러오는 중</span>
      <Bone h={36} style={{ width: "40%" }} />
      <Bone h={148} />
      <Bone h={148} />
    </Screen>
  );
}
