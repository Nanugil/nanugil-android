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
  border: 1px solid ${p.variant === "ghost" ? t.color.rule : "transparent"};
  background: ${p.variant === "ghost"
    ? "linear-gradient(180deg, var(--glow-top), transparent), var(--color-card)"
    : "linear-gradient(180deg, color-mix(in oklab, var(--color-accent) 70%, #FFFFFF), var(--color-accent) 55%)"};
  box-shadow: ${p.variant === "ghost"
    ? "inset 0 1px 0 var(--glow-top)"
    : "inset 0 1px 0 rgb(255 255 255 / 0.35), inset 0 -8px 16px -8px rgb(0 0 0 / 0.25), 0 10px 28px -8px color-mix(in oklab, var(--color-accent) 70%, transparent)"};
  color: ${p.variant === "ghost" ? t.color.ink : t.color.accentInk};
  font-weight: 600;
  font-size: ${t.text.md};
  text-decoration: none;
  cursor: pointer;
  transition: transform ${t.dur.micro} ${t.ease.out}, opacity ${t.dur.micro} ${t.ease.out};
  -webkit-tap-highlight-color: transparent;

  @media (hover: hover) {
    &:hover:not(:disabled) { opacity: 0.88; }
  }
  &:active:not(:disabled) { transform: scale(0.98); }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }
  &:disabled, &[aria-disabled="true"] { opacity: 0.55; cursor: not-allowed; }
  &[aria-busy="true"] { cursor: progress; opacity: 0.75; }
`;

/** 화면마다 primary는 하나. ghost는 테두리만 있는 보조 버튼. aria-busy면 진행 중. */
export const Button = styled.button<Variant>`${base}`;
/** 버튼 모양의 페이지 이동 */
export const ButtonLink = styled(Link)<Variant>`${base}`;
/** 버튼 모양의 바깥 링크 (카카오페이 송금 등) */
export const ButtonA = styled.a<Variant>`${base}`;
