"use client";
import styled from "@emotion/styled";
import { t } from "./theme";

const Bar = styled.div`display: flex; align-items: center; justify-content: space-between; gap: 12px;
  min-height: 48px; padding: 0 4px 0 14px; border-radius: 12px; background: ${t.color.fill};
  font-size: 13px; color: ${t.color.ink2};
  button { min-height: 44px; padding: 0 12px; border: 0; background: none; font-weight: 600; color: ${t.color.accent}; cursor: pointer; }
  button:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: -2px; border-radius: 10px; }`;

/** 실시간 연결이 끊겼을 때만 보인다. 화면 값이 최신이 아닐 수 있다고 알리고 다시 불러오게 한다. */
export function LiveNotice({ lost, onRetry }: { lost: boolean; onRetry: () => void }) {
  if (!lost) return null;
  return (
    <Bar role="status">
      <span>실시간 연결이 끊겼습니다. 금액이 최신이 아닐 수 있습니다.</span>
      <button type="button" onClick={onRetry}>다시 불러오기</button>
    </Bar>
  );
}
