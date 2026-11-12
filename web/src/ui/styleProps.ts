/** 스타일에만 쓰는 속성을 DOM으로 넘기지 않는다 (styled(tag, styleOnly("a", "b"))). */
export const styleOnly = (...names: string[]) => ({ shouldForwardProp: (prop: string) => !names.includes(prop) });
