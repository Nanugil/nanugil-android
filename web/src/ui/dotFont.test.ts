import { expect, it } from "vitest";
import { dotLayout, GLYPHS } from "./dotFont";

it("숫자 0~9는 모두 5칸 x 7줄", () => {
  for (const d of "0123456789") {
    expect(GLYPHS[d]).toHaveLength(7);
    for (const row of GLYPHS[d]) expect(row).toHaveLength(5);
  }
});

it("쉼표는 좁은 칸으로, 글자 사이는 한 칸씩 띄운다", () => {
  const { width, dots } = dotLayout("1,0");
  // 1(5칸) + 간격 1 + 쉼표(2칸) + 간격 1 + 0(5칸)
  expect(width).toBe(14);
  expect(dots.every((d) => d.x >= 0 && d.x < 14 && d.y >= 0 && d.y < 7)).toBe(true);
});

it("켜진 점과 꺼진 점을 모두 돌려준다 (꺼진 점은 흐리게 그린다)", () => {
  const { dots } = dotLayout("8");
  expect(dots).toHaveLength(35);
  expect(dots.filter((d) => d.on).length).toBeGreaterThan(10);
});

it("모르는 글자는 건너뛴다", () => {
  expect(dotLayout("원").width).toBe(0);
});
