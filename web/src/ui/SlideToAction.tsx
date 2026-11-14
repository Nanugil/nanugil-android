"use client";
// 밀어서 실행: 손잡이를 오른쪽 끝까지 밀면 onDone. 덜 밀면 제자리로 돌아간다.
// 손잡이는 버튼이라 탭·Enter·Space로도 실행되고, 움직임 줄이기 설정이면 바로 실행한다.
import styled from "@emotion/styled";
import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { Draggable } from "gsap/Draggable";
import { useRef } from "react";
import { Icon } from "./Icon";
import { t } from "./theme";

gsap.registerPlugin(Draggable, useGSAP);

const HANDLE = 56;
const Track = styled.div`
  position: relative; height: 64px; border-radius: 18px; background: ${t.color.fill}; overflow: hidden;
  touch-action: pan-y; user-select: none; -webkit-user-select: none;
`;
const Fill = styled.span`position: absolute; inset: 0 auto 0 0; width: 100%; transform-origin: left center; transform: scaleX(0);
  background: color-mix(in oklab, var(--color-accent) 14%, transparent); pointer-events: none;`;
const Text = styled.span`position: absolute; inset: 0; display: grid; place-items: center; padding-left: ${HANDLE}px;
  font-size: 15px; font-weight: 600; color: ${t.color.ink2}; pointer-events: none;`;
const Handle = styled.button`
  position: absolute; top: 4px; left: 4px; width: ${HANDLE}px; height: 56px; border-radius: 14px; border: 0;
  display: grid; place-items: center; background: ${t.color.cta}; color: ${t.color.ctaInk}; cursor: grab;
  &:active { cursor: grabbing; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }
  .material-symbols-rounded { font-size: 24px; }
`;

export function SlideToAction({ label, onDone }: { label: string; onDone: () => void }) {
  const track = useRef<HTMLDivElement>(null);
  const handle = useRef<HTMLButtonElement>(null);
  const fill = useRef<HTMLSpanElement>(null);
  const text = useRef<HTMLSpanElement>(null);
  const done = useRef(false);
  const dragged = useRef(false);

  const finish = (from = 0) => {
    if (done.current) return;
    done.current = true;
    const max = (track.current?.clientWidth ?? 0) - HANDLE - 8;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) { onDone(); return; }
    gsap.timeline({ onComplete: onDone })
      .fromTo(handle.current, { x: from }, { x: max, duration: 0.28, ease: "power2.out" })
      .to(fill.current, { scaleX: 1, duration: 0.28, ease: "power2.out" }, 0)
      .to(text.current, { opacity: 0, duration: 0.2 }, 0);
  };

  useGSAP(() => {
    const max = () => (track.current?.clientWidth ?? 0) - HANDLE - 8;
    const sync = (x: number) => {
      const r = Math.max(0, Math.min(1, x / max()));
      gsap.set(fill.current, { scaleX: r });
      gsap.set(text.current, { opacity: 1 - r * 1.4 });
    };
    const [d] = Draggable.create(handle.current, {
      type: "x", bounds: { minX: 0, maxX: max() }, minimumMovement: 4,
      onPress() { this.applyBounds({ minX: 0, maxX: max() }); },
      onDrag() { dragged.current = true; sync(this.x); },
      onRelease() {
        if (this.x >= max() * 0.85) finish(this.x);
        else gsap.to(handle.current, { x: 0, duration: 0.35, ease: "back.out(1.6)", onUpdate: () => sync(Number(gsap.getProperty(handle.current, "x"))) });
      },
    });
    return () => d.kill();
  }, { scope: track });

  return (
    <Track ref={track}>
      <Fill ref={fill} />
      <Text ref={text} aria-hidden="true">{label}</Text>
      <Handle ref={handle} type="button" aria-label={label}
        onClick={() => { if (dragged.current) { dragged.current = false; return; } finish(); }}>
        <Icon name="arrow_forward" />
      </Handle>
    </Track>
  );
}
