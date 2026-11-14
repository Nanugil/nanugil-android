import { expect, it } from "vitest";
import { activeTab } from "./tabs";

it("주소로 선택된 탭을 정한다", () => {
  expect(activeTab("/")).toBe("home");
  expect(activeTab("/groups/abc")).toBe("home");
  expect(activeTab("/trips/t/settle")).toBe("home");
  expect(activeTab("/join")).toBe("join");
  expect(activeTab("/me")).toBe("me");
  expect(activeTab("/preview/join")).toBe("join");
  expect(activeTab("/preview/driver")).toBe("home");
  expect(activeTab("/preview/me")).toBe("me");
});
