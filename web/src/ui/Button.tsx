"use client";
import { css } from "@emotion/react";
import styled from "@emotion/styled";
import Link from "next/link";
import { t } from "./theme";

type Variant = { variant?: "primary" | "ghost" };

const base = (p: Variant) => css`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: ${t.space.xs};
  width: 100%;
  min-height: ${t.controlH};
  padding: 0 ${t.space.lg};
  border-radius: ${t.radius.control};
  border: 0;
  background: ${p.variant === "ghost" ? t.color.fill : t.color.cta};
  color: ${p.variant === "ghost" ? t.color.ink : t.color.ctaInk};
  font-weight: 600;
  font-size: 16px;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
  transition: transform ${t.dur.micro} ${t.ease.out}, opacity ${t.dur.micro} ${t.ease.out};
  -webkit-tap-highlight-color: transparent;

  @media (hover: hover) {
    &:hover:not(:disabled) { opacity: 0.88; }
  }
  &:active:not(:disabled) { transform: scale(0.98); opacity: 0.9; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }
  &:disabled, &[aria-disabled="true"] { background: ${t.color.fill}; color: ${t.color.muted}; cursor: not-allowed; }
  &[aria-busy="true"] { cursor: progress; opacity: 0.75; }
`;

/** 화면마다 primary는 하나. ghost는 테두리만 있는 보조 버튼. aria-busy면 진행 중. */
// variant는 스타일 전용이라 DOM(특히 Link가 그리는 <a>)으로 넘기지 않는다
const noVariant = { shouldForwardProp: (prop: string) => prop !== "variant" };
export const Button = styled("button", noVariant)<Variant>`${base}`;
/** 버튼 모양의 페이지 이동 */
export const ButtonLink = styled(Link, noVariant)<Variant>`${base}`;
/** 버튼 모양의 바깥 링크 (카카오페이 송금 등) */
export const ButtonA = styled("a", noVariant)<Variant>`${base}`;
