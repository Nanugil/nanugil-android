import { expect, it } from "vitest";
import { formatWon } from "./format";

it("천 단위 쉼표 + 원", () => {
  expect(formatWon(11750)).toBe("11,750원");
  expect(formatWon(0)).toBe("0원");
  expect(formatWon(1234567)).toBe("1,234,567원");
});
