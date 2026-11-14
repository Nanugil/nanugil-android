"use client";
// GSAP 움직임은 설계 문서 5장의 네 군데에서만 쓴다. 모두 움직임 줄이기 설정이면 최종 상태를 바로 보여 준다.
// 서버가 그린 HTML은 이미 최종 상태라, 스크립트가 꺼져 있어도 내용은 다 보인다.
import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { useEffect, useRef, useState, type RefObject } from "react";
import { formatWon } from "@/lib/format";

gsap.registerPlugin(useGSAP);

function reduced(): boolean {
  return typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function usePrefersReducedMotion(): boolean {
  const [value, setValue] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    setValue(mq.matches);
    const on = () => setValue(mq.matches);
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, []);
  return value;
}

/** 틀린 코드: trigger가 바뀔 때마다 좌우로 짧게 흔든다. */
export function useShake(ref: RefObject<HTMLElement | null>, trigger: number) {
  useGSAP(() => {
    if (trigger === 0 || !ref.current || reduced()) return;
    gsap.fromTo(ref.current, { x: 0 }, { keyframes: { x: [-8, 8, -6, 6, -3, 0] }, duration: 0.42, ease: "power2.out" });
  }, { dependencies: [trigger] });
}

/** 정산 카드가 아래에서 올라온다. */
export function useRiseIn(ref: RefObject<HTMLElement | null>) {
  useGSAP(() => {
    if (!ref.current || reduced()) return;
    gsap.from(ref.current, { y: 24, opacity: 0, duration: 0.6, ease: "power3.out" });
  });
}

/** 금액이 0원부터 to까지 올라간다. 글자는 처음부터 최종 금액으로 그려져 있다. */
export function useCountUp(ref: RefObject<HTMLElement | null>, to: number) {
  useGSAP(() => {
    const el = ref.current;
    if (!el || reduced()) return;
    const state = { n: 0 };
    gsap.to(state, {
      n: to, duration: 1.1, ease: "power2.out",
      onUpdate: () => { el.textContent = formatWon(Math.round(state.n)); },
      onComplete: () => { el.textContent = formatWon(to); },
    });
  }, { dependencies: [to] });
}

/** 0부터 to까지 세어 올린 값을 돌려준다. 처음 그림(서버)은 최종 값이다. */
export function useCountUpValue(to: number): number {
  const [value, setValue] = useState(to);
  const shown = useRef<number | null>(null); // 처음엔 0부터, 이후엔 지금 보이는 값부터 이어서
  useGSAP(() => {
    if (reduced()) { shown.current = to; setValue(to); return; }
    const state = { n: shown.current ?? 0 };
    const tween = gsap.to(state, { n: to, duration: 0.9, ease: "power2.out",
      onUpdate: () => { shown.current = Math.round(state.n); setValue(shown.current); },
      onComplete: () => { shown.current = to; setValue(to); } });
    return () => { tween.kill(); };
  }, { dependencies: [to] });
  return value;
}

/** 체크 표시(svg path)를 그린다. 체크가 나타날 때(마운트)와 active가 true로 바뀔 때. */
export function useDrawCheck(ref: RefObject<SVGPathElement | null>, active: boolean) {
  useGSAP(() => {
    const path = ref.current;
    if (!path || !active || reduced()) return;
    const len = path.getTotalLength();
    gsap.fromTo(path, { strokeDasharray: len, strokeDashoffset: len }, { strokeDashoffset: 0, duration: 0.45, ease: "power2.out" });
  }, { dependencies: [active] });
}

/** key가 바뀌면 덮개(overlay)를 잠깐 밝혔다가 끈다. 처음 그릴 때는 하지 않는다. */
export function useFlashOnChange(ref: RefObject<HTMLElement | null>, key: unknown) {
  const first = useRef(true);
  useGSAP(() => {
    if (first.current) { first.current = false; return; }
    if (!ref.current || reduced()) return;
    gsap.fromTo(ref.current, { opacity: 0.6 }, { opacity: 0, duration: 0.9, ease: "power2.out" });
  }, { dependencies: [key] });
}

/** 화면이 열릴 때 바로 아래 자식들(머리·구역·버튼)이 차례로 살짝 올라온다. 서버가 그린 내용은 이미 보이는 상태다. */
export function usePageEnter(ref: RefObject<HTMLElement | null>) {
  useGSAP(() => {
    const el = ref.current;
    if (!el || reduced()) return;
    gsap.from(el.children, { y: 8, opacity: 0, duration: 0.25, ease: "power2.out", stagger: 0.04, clearProps: "transform,opacity" });
  }, { scope: ref });
}
