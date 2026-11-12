"use client";
import styled from "@emotion/styled";
import { useId, type InputHTMLAttributes } from "react";
import { t } from "./theme";

const Wrap = styled.div`display: grid; gap: ${t.space.x2s};`;
const Label = styled.label`font-size: ${t.text.sm}; color: ${t.color.ink2}; font-weight: 500;`;
const Input = styled.input`
  min-height: ${t.controlH};
  padding: 0 ${t.space.md};
  border-radius: ${t.radius.control};
  border: 1px solid ${t.color.rule};
  background: ${t.color.input};
  font-size: ${t.text.md};
  &::placeholder { color: ${t.color.muted}; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: -1px; }
  &[aria-invalid="true"] { outline: 2px solid ${t.color.error}; outline-offset: -1px; }
  &:disabled { opacity: 0.55; cursor: not-allowed; }
`;
const Help = styled.p<{ error?: boolean }>`
  min-height: 1lh;
  font-size: ${t.text.xs};
  color: ${(p) => (p.error ? t.color.error : t.color.muted)};
`;

type Props = InputHTMLAttributes<HTMLInputElement> & { label: string; help?: string; error?: string | null };

/** 라벨·입력·도움말(오류) 한 묶음. 도움말 자리는 비어 있어도 한 줄을 차지해 화면이 흔들리지 않는다. */
export function Field({ label, help, error, ...input }: Props) {
  const id = useId();
  const helpId = `${id}-help`;
  return (
    <Wrap>
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} aria-invalid={error ? true : undefined} aria-describedby={helpId} {...input} />
      <Help id={helpId} error={!!error} role={error ? "alert" : undefined}>{error ?? help ?? ""}</Help>
    </Wrap>
  );
}
