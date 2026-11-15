import { describe, expect, it } from "vitest";
import { statusOf, viewFor } from "./settlement";
import type { Settlement } from "./types";

const s: Settlement = {
  requestedAt: "2026-11-15T18:20:00+09:00", requestedBy: "A", perPersonWon: 11750, transfers: [
    { from: "B", to: "A", amountWon: 11750, sentAt: null, receivedAt: null },
    { from: "C", to: "A", amountWon: 11750, sentAt: "2026-11-15T18:30:00+09:00", receivedAt: null },
    { from: "D", to: "E", amountWon: 11750, sentAt: "x", receivedAt: "y" },
  ],
};

describe("viewFor", () => {
  it("동승자는 자기가 보낼 송금만 본다", () => {
    expect(viewFor(s, "B").toSend.map((x) => x.index)).toEqual([0]);
  });
  it("받는 사람은 받을 송금을 본다", () => {
    expect(viewFor(s, "A").toReceive.map((x) => x.index)).toEqual([0, 1]);
  });
  it("송금이 없는 사람도 오류 없이 빈 목록", () => {
    expect(viewFor(s, "Z")).toEqual({ toSend: [], toReceive: [], perPersonWon: 11750, allDone: false });
  });
  it("모두 받았으면 allDone", () => {
    expect(viewFor({ ...s, transfers: [s.transfers[2]] }, "Z").allDone).toBe(true);
    expect(viewFor({ ...s, transfers: [] }, "Z").allDone).toBe(false);
  });
});

describe("statusOf", () => {
  it("받음 > 보냄 > 확인 전", () => {
    expect(statusOf(s.transfers[0])).toBe("pending");
    expect(statusOf(s.transfers[1])).toBe("sent");
    expect(statusOf(s.transfers[2])).toBe("received");
  });
});
