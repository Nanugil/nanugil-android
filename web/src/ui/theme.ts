// Emotion에서 쓰는 토큰. 값은 모두 globals.css의 :root CSS 변수라 다크·라이트가 CSS만으로 바뀐다.
const v = (name: string) => `var(--${name})`;

export const t = {
  color: {
    paper: v("color-paper"), card: v("color-card"), cardStrong: v("color-card-strong"),
    ink: v("color-ink"), ink2: v("color-ink-2"), muted: v("color-muted"), rule: v("color-rule"),
    accent: v("color-accent"), accentInk: v("color-accent-ink"), focus: v("color-focus"),
    success: v("color-success"), cta: v("color-cta"), ctaInk: v("color-cta-ink"), track: v("color-track"), error: v("color-error"), warn: v("color-warn"), input: v("color-input"),
  },
  text: { xs: v("text-xs"), sm: v("text-sm"), md: v("text-md"), lg: v("text-lg"), xl: v("text-xl"), x2: v("text-2xl"), figure: v("text-figure") },
  space: { x3s: v("space-3xs"), x2s: v("space-2xs"), xs: v("space-xs"), sm: v("space-sm"), md: v("space-md"), lg: v("space-lg"), xl: v("space-xl"), x2l: v("space-2xl") },
  radius: { card: v("radius-card"), control: v("radius-control"), pill: v("radius-pill") },
  dur: { micro: v("dur-micro"), short: v("dur-short"), long: v("dur-long") },
  ease: { out: v("ease-out") },
  controlH: v("control-h"),
  container: v("container"),
  containerWide: v("container-wide"),
  gutter: v("gutter"),
} as const;
