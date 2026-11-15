import { expect, it } from "vitest";
import { isKakaoPayLink } from "./payLink";

it("https 카카오페이 주소만 받는다", () => {
  expect(isKakaoPayLink("https://qr.kakaopay.com/abc")).toBe(true);
  expect(isKakaoPayLink("https://kakaopay.com")).toBe(true);
  expect(isKakaoPayLink("http://qr.kakaopay.com/abc")).toBe(false);
  expect(isKakaoPayLink("https://kakaopay.com.evil.io/x")).toBe(false);
  expect(isKakaoPayLink("javascript:alert(1)")).toBe(false);
  expect(isKakaoPayLink(null)).toBe(false);
});
