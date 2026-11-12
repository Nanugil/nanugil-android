"use client";
import styled from "@emotion/styled";
import { t } from "./theme";

/** 화면마다 primary는 하나. ghost는 테두리만 있는 보조 버튼. aria-busy면 진행 중. */
export const Button = styled.button<{ variant?: "primary" | "ghost" }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: ${t.space.xs};
  width: 100%;
  min-height: ${t.controlH};
  padding: 0 ${t.space.lg};
  border-radius: ${t.radius.control};
  border: 1px solid ${(p) => (p.variant === "ghost" ? t.color.rule : "transparent")};
  background: ${(p) => (p.variant === "ghost" ? "transparent" : t.color.accent)};
  color: ${(p) => (p.variant === "ghost" ? t.color.ink : t.color.accentInk)};
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
  &:disabled { opacity: 0.55; cursor: not-allowed; }
  &[aria-busy="true"] { cursor: progress; opacity: 0.75; }
`;
