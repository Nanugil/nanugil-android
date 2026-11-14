"use client";
import { dotLayout } from "./dotFont";

/** 도트 숫자. 켜진 점은 글자색, 꺼진 점은 흐리게 남는다 (계기판 느낌). 읽는 글자는 부모의 aria-label이 맡는다. */
export function DotNumber({ text, height = 64 }: { text: string; height?: number }) {
  const { width, dots } = dotLayout(text);
  const pitch = 10;
  return (
    <svg viewBox={`0 0 ${width * pitch} ${7 * pitch}`} height={height} style={{ maxWidth: "100%", width: "auto", overflow: "visible" }}
      aria-hidden="true" focusable="false">
      <g fill="var(--color-ink)" opacity="0.07">
        {dots.filter((d) => !d.on).map((d) => <rect key={`${d.x}-${d.y}`} x={d.x * pitch + 1} y={d.y * pitch + 1} width={8} height={8} rx={4} />)}
      </g>
      <g fill="var(--color-ink)">
        {dots.filter((d) => d.on).map((d) => <rect key={`${d.x}-${d.y}`} x={d.x * pitch + 1} y={d.y * pitch + 1} width={8} height={8} rx={4} />)}
      </g>
    </svg>
  );
}
