"use client";
import styled from "@emotion/styled";
import { forwardRef, type ReactNode } from "react";
import { t } from "./theme";

const Row = styled.li`
  position: relative; display: flex; align-items: center; justify-content: space-between; gap: ${t.space.sm};
  min-height: 56px; padding: ${t.space.xs} 0; border-bottom: 1px solid ${t.color.rule};
  &:last-child { border-bottom: 0; }
`;
const Avatar = styled.span`
  display: grid; place-items: center; flex: none; width: 36px; height: 36px; border-radius: ${t.radius.pill};
  background: ${t.color.cardStrong}; font-weight: 700; font-size: ${t.text.sm};
`;
const Name = styled.span`display: flex; align-items: center; gap: ${t.space.sm}; min-width: 0;
  span:last-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`;
const Sub = styled.span`display: block; font-size: ${t.text.xs}; color: ${t.color.muted};`;
const Trailing = styled.span`display: flex; align-items: center; gap: ${t.space.xs}; flex: none;`;
// 값이 바뀔 때 잠깐 밝아지는 덮개 (useFlashOnChange가 opacity만 움직인다)
export const Flash = styled.span`
  position: absolute; inset: 0; pointer-events: none; opacity: 0; border-radius: 8px;
  background: color-mix(in oklab, var(--color-accent) 25%, transparent);
`;

type Props = { name: string; sub?: ReactNode; trailing?: ReactNode; children?: ReactNode };

/** 멤버 한 줄: 이니셜, 이름(+보조 줄), 오른쪽 끝 표시. 카드가 아니라 줄로 나눈다. */
export const MemberRow = forwardRef<HTMLLIElement, Props>(function MemberRow({ name, sub, trailing, children }, ref) {
  return (
    <Row ref={ref}>
      {children}
      <Name>
        <Avatar aria-hidden="true">{name.slice(0, 1)}</Avatar>
        <span>{name}{sub && <Sub>{sub}</Sub>}</span>
      </Name>
      {trailing && <Trailing>{trailing}</Trailing>}
    </Row>
  );
});

export const Rows = styled.ul`list-style: none; margin: 0; padding: 0; display: grid;`;
