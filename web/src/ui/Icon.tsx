// 앱에서 쓰는 아이콘 11종을 SVG 선으로 직접 그린다 (예전 Material Symbols 웹폰트 약 5MB를 대신).
// 크기는 글자 크기(1em)를 따라가서 기존 `.material-symbols-rounded { font-size }` 규칙이 그대로 통한다.
import type { ReactNode } from "react";

const P: Record<string, ReactNode> = {
  chevron_right: <path d="M9.5 6l6 6-6 6" />,
  arrow_back_ios_new: <path d="M15 5l-7 7 7 7" />,
  arrow_forward: <><path d="M5 12h14" /><path d="M13 6l6 6-6 6" /></>,
  check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
  add: <><path d="M12 5v14" /><path d="M5 12h14" /></>,
  ios_share: <><path d="M12 3.5v11" /><path d="M8 7.5l4-4 4 4" /><path d="M7 11H6a1.5 1.5 0 0 0-1.5 1.5v6A1.5 1.5 0 0 0 6 20h12a1.5 1.5 0 0 0 1.5-1.5v-6A1.5 1.5 0 0 0 18 11h-1" /></>,
  visibility: <><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" /><circle cx="12" cy="12" r="3" /></>,
  visibility_off: <><path d="M10.6 5.6A9.6 9.6 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-2.4 3.2M6.5 7.2A16.6 16.6 0 0 0 2.5 12S6 18.5 12 18.5a9.3 9.3 0 0 0 4.4-1.1" /><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" /><path d="M3.5 3.5l17 17" /></>,
  radio_button_unchecked: <circle cx="12" cy="12" r="8.5" />,
  radio_button_checked: <><circle cx="12" cy="12" r="8.5" /><circle cx="12" cy="12" r="4" fill="currentColor" stroke="none" /></>,
  error: <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5v5" /><path d="M12 16h.01" /></>,
};

/** 글자 옆 꾸밈 아이콘. 스크린 리더에서는 숨긴다. 없는 이름이면 아무것도 그리지 않는다. */
export function Icon({ name, className }: { name: string; className?: string }) {
  const shape = P[name];
  if (!shape) return null;
  return (
    <svg className={`material-symbols-rounded${className ? ` ${className}` : ""}`} viewBox="0 0 24 24" width="1em" height="1em"
      fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      {shape}
    </svg>
  );
}
