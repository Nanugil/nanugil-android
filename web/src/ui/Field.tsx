"use client";
import styled from "@emotion/styled";
import { useId, useState, type InputHTMLAttributes } from "react";
import { Icon } from "./Icon";
import { styleOnly } from "./styleProps";
import { t } from "./theme";

const Wrap = styled.div`display: grid; gap: 8px;`;
const Label = styled.label`font-size: 13px; color: ${t.color.ink2}; font-weight: 500;`;
const Box = styled.div`position: relative; display: grid;
  > .material-symbols-rounded { position: absolute; left: ${t.space.md}; top: 50%; transform: translateY(-50%); color: ${t.color.muted}; pointer-events: none; }`;
const Eye = styled.button`position: absolute; right: 4px; top: 50%; transform: translateY(-50%);
  display: grid; place-items: center; width: 44px; height: 44px; border: 0; border-radius: ${t.radius.control};
  background: transparent; color: ${t.color.muted}; cursor: pointer;
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: -2px; }
  &:active { opacity: 0.7; }`;
const Input = styled("input", styleOnly("withIcon", "withEye"))<{ withIcon: boolean; withEye: boolean }>`
  min-height: ${t.controlH};
  padding: 0 ${(p) => (p.withEye ? "52px" : t.space.md)} 0 ${(p) => (p.withIcon ? "48px" : t.space.md)};
  border-radius: 12px;
  border: 0;
  background: ${t.color.input};
  transition: background-color ${t.dur.micro} ${t.ease.out};
  font-size: ${t.text.md};
  &::placeholder { color: ${t.color.muted}; }
  &:focus { outline: none; background: ${t.color.card}; box-shadow: inset 0 0 0 1.5px ${t.color.accent}; }
  &[aria-invalid="true"] { background: ${t.color.card}; box-shadow: inset 0 0 0 1.5px ${t.color.error}; }
  &:disabled { opacity: 0.55; cursor: not-allowed; }
`;
const Help = styled("p", styleOnly("error"))<{ error?: boolean }>`
  font-size: ${t.text.xs};
  color: ${(p) => (p.error ? t.color.error : t.color.muted)};
`;

type Props = InputHTMLAttributes<HTMLInputElement> & { label: string; help?: string; error?: string | null; icon?: string };

/** 라벨·입력(앞 아이콘, 비밀번호 보기)·도움말(오류) 한 묶음. 도움말 자리는 비어 있어도 한 줄을 차지해 화면이 흔들리지 않는다. */
export function Field({ label, help, error, icon, type, ...input }: Props) {
  const id = useId();
  const [shown, setShown] = useState(false);
  const password = type === "password";
  const helpId = `${id}-help`;
  return (
    <Wrap>
      <Label htmlFor={id}>{label}</Label>
      <Box>
        <Input {...input} id={id} type={password && shown ? "text" : type} withIcon={!!icon} withEye={password}
          aria-invalid={error ? true : undefined} aria-describedby={error || help ? helpId : undefined} />
        {icon && <Icon name={icon} />}
        {password && (
          <Eye type="button" onClick={() => setShown((v) => !v)} aria-label={shown ? "비밀번호 숨기기" : "비밀번호 보기"}>
            <Icon name={shown ? "visibility_off" : "visibility"} />
          </Eye>
        )}
      </Box>
      {(error || help) && <Help id={helpId} error={!!error} role={error ? "alert" : undefined}>{error ?? help}</Help>}
    </Wrap>
  );
}
