import { expect, it } from "vitest";
import { safeNext } from "./next";

it("같은 사이트 경로는 쿼리까지 그대로", () => expect(safeNext("/join?code=4F7K2Q")).toBe("/join?code=4F7K2Q"));
it("다른 사이트로 가는 주소는 홈으로", () => {
  expect(safeNext("//evil.com")).toBe("/");
  expect(safeNext("https://evil.com")).toBe("/");
  expect(safeNext("/\\evil.com")).toBe("/");
  expect(safeNext(null)).toBe("/");
});
