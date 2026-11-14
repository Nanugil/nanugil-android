"use client";
// 밀어서 실행: 손잡이를 오른쪽 끝까지 밀면 onDone. 덜 밀면 제자리로 돌아간다.
// 손잡이는 버튼이라 탭·Enter·Space로도 실행되고, 움직임 줄이기 설정이면 바로 실행한다.
import styled from "@emotion/styled";
import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { Draggable } from "gsap/Draggable";
import { useEffect, useRef } from "react";
import { Icon } from "./Icon";
import { t } from "./theme";

gsap.registerPlugin(Draggable, useGSAP);

const HANDLE = 48;
const PAD = 4;
const Track = styled.div`
  position: relative; height: ${HANDLE + PAD * 2}px; border-radius: 16px; background: ${t.color.fill}; overflow: hidden;
  touch-action: pan-y; user-select: none; -webkit-user-select: none;
`;
const Fill = styled.span`position: absolute; inset: 0 auto 0 0; width: 100%; transform-origin: left center; transform: scaleX(0);
  background: color-mix(in oklab, var(--color-accent) 12%, transparent); pointer-events: none;`;
const Text = styled.span`position: absolute; inset: 0; display: grid; place-items: center; padding-left: ${HANDLE}px;
  font-size: 15px; font-weight: 500; color: ${t.color.muted}; pointer-events: none;`;
const Handle = styled.button`
  position: absolute; top: ${PAD}px; left: ${PAD}px; width: ${HANDLE}px; height: ${HANDLE}px; border-radius: 12px; border: 0;
  display: grid; place-items: center; background: ${t.color.cta}; color: ${t.color.ctaInk}; cursor: grab;
  &:active { cursor: grabbing; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }
  .material-symbols-rounded { font-size: 22px; }
`;

/** label은 화면에 보이는 안내(“밀어서 …”), actionLabel은 화면 읽기 프로그램이 읽는 동작 이름 */
export function SlideToAction({ label, actionLabel, onDone }: { label: string; actionLabel: string; onDone: () => void }) {
  const track = useRef<HTMLDivElement>(null);
  const handle = useRef<HTMLButtonElement>(null);
  const fill = useRef<HTMLSpanElement>(null);
  const text = useRef<HTMLSpanElement>(null);
  const done = useRef(false);
  const dragged = useRef(false);
  const draggable = useRef<Draggable | null>(null);
  const onDoneRef = useRef(onDone);
  onDoneRef.current = onDone; // 드래그 콜백이 늘 최신 onDone을 부르게

  const max = () => (track.current?.clientWidth ?? 0) - HANDLE - PAD * 2;
  const sync = (x: number) => {
    const r = Math.max(0, Math.min(1, x / Math.max(1, max())));
    gsap.set(fill.current, { scaleX: r });
    gsap.set(text.current, { opacity: 1 - r * 1.4 });
  };
  const reset = () => {
    done.current = false;
    draggable.current?.enable();
    gsap.set(handle.current, { x: 0 }); sync(0);
  };
  const finish = (from = 0) => {
    if (done.current) return;
    done.current = true;
    draggable.current?.disable();
    gsap.killTweensOf([handle.current, fill.current, text.current]);
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) { onDoneRef.current(); return; }
    gsap.timeline({ onComplete: () => onDoneRef.current() })
      .fromTo(handle.current, { x: from }, { x: max(), duration: 0.28, ease: "power2.out" })
      .to(fill.current, { scaleX: 1, duration: 0.28, ease: "power2.out" }, 0)
      .to(text.current, { opacity: 0, duration: 0.2 }, 0);
  };

  useGSAP(() => {
    const [d] = Draggable.create(handle.current, {
      type: "x", bounds: { minX: 0, maxX: max() }, minimumMovement: 4,
      onPress() { dragged.current = false; this.applyBounds({ minX: 0, maxX: max() }); },
      onDrag() { dragged.current = true; sync(this.x); },
      onRelease() {
        if (!dragged.current) return; // 탭은 click이 처리한다
        setTimeout(() => { dragged.current = false; }, 0);
        if (this.x >= max() * 0.85) finish(this.x);
        else gsap.to(handle.current, { x: 0, duration: 0.35, ease: "back.out(1.6)", onUpdate: () => sync(Number(gsap.getProperty(handle.current, "x"))) });
      },
    });
    draggable.current = d;
    return () => { d.kill(); draggable.current = null; };
  }, { scope: track });

  // 뒤로 가기로 다시 보이면(브라우저 캐시) 처음 상태로
  useEffect(() => {
    const onShow = (e: PageTransitionEvent) => { if (e.persisted) reset(); };
    window.addEventListener("pageshow", onShow);
    return () => window.removeEventListener("pageshow", onShow);
  });

  return (
    <Track ref={track}>
      <Fill ref={fill} />
      <Text ref={text} aria-hidden="true">{label}</Text>
      <Handle ref={handle} type="button" aria-label={actionLabel}
        onClick={() => { if (dragged.current) return; finish(); }}>
        <Icon name="arrow_forward" />
      </Handle>
    </Track>
  );
}
