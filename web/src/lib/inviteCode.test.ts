import { expect, it } from "vitest";
import { normalizeInviteCode } from "./inviteCode";

it("공백을 걷고 대문자로 바꾼다", () => expect(normalizeInviteCode(" 4f7k2q ")).toBe("4F7K2Q"));
it("헷갈리는 글자(O, 0, I, 1)가 있으면 null", () => {
  expect(normalizeInviteCode("4F7K2O")).toBeNull();
  expect(normalizeInviteCode("4F7K21")).toBeNull();
});
it("6자리가 아니면 null", () => expect(normalizeInviteCode("4F7K2")).toBeNull());
