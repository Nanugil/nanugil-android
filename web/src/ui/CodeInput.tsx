"use client";
import styled from "@emotion/styled";
import { forwardRef, useId } from "react";
import { styleOnly } from "./styleProps";
import { t } from "./theme";

const Wrap = styled.div`position: relative; display: grid; gap: ${t.space.xs};`;
const Boxes = styled.div`
  display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: ${t.space.xs};
  pointer-events: none;
`;
const Box = styled("span", styleOnly("filled", "current"))<{ filled: boolean; current: boolean }>`
  display: grid; place-items: center; height: 56px;
  border-radius: 12px;
  background: ${t.color.fill};
  border: 1.5px solid ${(p) => (p.current ? t.color.accent : "transparent")};
  font-size: 22px; font-weight: 600; font-variant-numeric: tabular-nums;
  transition: border-color ${t.dur.micro} ${t.ease.out};
`;
// 진짜 입력칸은 하나다. 칸 6개 위에 투명하게 겹쳐 붙여 넣기·지우기·자동 대문자를 그대로 쓴다.
const Real = styled.input`
  position: absolute; inset: 0; width: 100%; height: 100%;
  opacity: 0; font-size: 16px; cursor: text;
  &:disabled { cursor: not-allowed; }
  &:focus-visible + div span[data-current="true"] { outline: 2px solid ${t.color.focus}; outline-offset: 1px; }
`;
const Label = styled.label`font-size: ${t.text.sm}; color: ${t.color.ink2}; font-weight: 500;`;

type Props = {
  value: string;
  onChange: (value: string) => void;
  onComplete?: (code: string) => void;
  disabled?: boolean;
  invalid?: boolean;
  readOnly?: boolean;
  errorId?: string;
};

/** v5 화면 04의 6칸 초대 코드 입력 */
export const CodeInput = forwardRef<HTMLDivElement, Props>(function CodeInput({ value, onChange, onComplete, disabled, invalid, readOnly, errorId }, ref) {
  const id = useId();
  return (
    <Wrap ref={ref}>
      <Label htmlFor={id} className="visually-hidden">초대 코드 6자리</Label>
      <div style={{ position: "relative" }}>
        <Real
          id={id} value={value} disabled={disabled} readOnly={readOnly} maxLength={6} aria-describedby={errorId}
          autoComplete="one-time-code" autoCapitalize="characters" autoCorrect="off" spellCheck={false}
          inputMode="text" aria-invalid={invalid || undefined}
          onChange={(e) => {
            if (readOnly) return;
            const next = e.target.value.replace(/[^0-9a-z]/gi, "").toUpperCase().slice(0, 6);
            onChange(next);
            if (next.length === 6) onComplete?.(next);
          }}
        />
        <Boxes aria-hidden="true">
          {Array.from({ length: 6 }, (_, i) => (
            <Box key={i} filled={i < value.length} current={i === Math.min(value.length, 5)} data-current={i === Math.min(value.length, 5)}>
              {value[i] ?? ""}
            </Box>
          ))}
        </Boxes>
      </div>
    </Wrap>
  );
});
