// 실제 Supabase에 붙어 동승자 웹 RPC와 실시간 알림을 끝까지 돌려 본다.
// .env.local에 키가 있어야 하고, 마이그레이션 20261113000000_passenger_web.sql이 적용돼 있어야 한다.
// 실행할 때마다 시험용 사용자·그룹이 서버에 남는다 (앱의 ServerLiveTest와 같음).
import { beforeAll, describe, expect, it } from "vitest";
import {
  claimMember, getTrip, markReceived, markSent, previewInvite, subscribeGroup, unclaimedMembers,
} from "@/lib/api";
import { ServerError } from "@/lib/errors";
import type { Trip } from "@/lib/types";
import { hasServer, newUser, prepareTrip, requestSettlement, type PreparedTrip, type TestUser } from "./helpers";

async function code(p: Promise<unknown>): Promise<string> {
  try { await p; } catch (e) { if (e instanceof ServerError) return e.code; throw e; }
  return "NO_ERROR";
}

describe.skipIf(!hasServer)("동승자 웹 서버", () => {
  let A: TestUser, B: TestUser, C: TestUser, t: PreparedTrip;
  let pB: string;

  beforeAll(async () => {
    [A, B, C] = await Promise.all([newUser("A"), newUser("B"), newUser("C")]);
    t = await prepareTrip(A.sb, ["B이름", "D이름"], { arrive: false });
    pB = t.passengerMemberIds[0];
  });

  it("이름 목록을 보여 주고, 연결하면 빠진다", async () => {
    expect((await previewInvite(B.sb, t.code)).groupId).toBe(t.groupId);
    expect((await unclaimedMembers(B.sb, t.code)).map((m) => m.displayName)).toEqual(["B이름", "D이름"]);
    expect((await claimMember(B.sb, t.code, pB)).groupId).toBe(t.groupId);
    expect((await unclaimedMembers(B.sb, t.code)).map((m) => m.displayName)).toEqual(["D이름"]);
    expect(await code(claimMember(C.sb, t.code, pB))).toBe("MEMBER_TAKEN");
  });

  it("송금 링크는 카카오페이만 받는다", async () => {
    const r1 = await A.sb.rpc("set_pay_link", { p_group_id: t.groupId, p_link: "https://evil.com/x" });
    expect(r1.error?.message).toBe("INVALID_PAY_LINK");
    const r2 = await A.sb.rpc("set_pay_link", { p_group_id: t.groupId, p_link: "https://qr.kakaopay.com/abc" });
    expect(r2.error).toBeNull();
  });

  it("도착 전이거나 운전자가 아니면 정산 요청을 못 한다", async () => {
    await expect(requestSettlement(A.sb, t.tripId, 0, [])).rejects.toThrow("NOT_ARRIVED");
    await expect(requestSettlement(B.sb, t.tripId, 0, [])).rejects.toThrow("NOT_DRIVER");
    await A.sb.rpc("mark_arrived", { p_trip_id: t.tripId });
    await expect(requestSettlement(A.sb, t.tripId, 1, [{ from: t.driverMemberId, to: t.driverMemberId, amountWon: 1 }]))
      .rejects.toThrow("INVALID_TRANSFER");
  });

  it("정산 요청이 열린 화면에 10초 안에 도착한다", async () => {
    let unsubscribe = () => {};
    const got = new Promise<Trip>((resolve) => {
      subscribeGroup(B.sb, t.groupId, (trip) => { if (trip.settlement) resolve(trip); }).then((u) => { unsubscribe = u; });
    });
    await new Promise((r) => setTimeout(r, 2000)); // 구독이 붙을 시간
    await requestSettlement(A.sb, t.tripId, 11750, [{ from: pB, to: t.driverMemberId, amountWon: 11750 }]);
    const trip = await Promise.race([got, new Promise<never>((_, j) => setTimeout(() => j(new Error("10초 안에 안 옴")), 10_000))]);
    unsubscribe();
    expect(trip.settlement?.perPersonWon).toBe(11750);
  });

  it("보냈어요는 한 번만 기록되고, 남의 송금은 표시할 수 없다", async () => {
    const req = (await getTrip(B.sb, t.tripId)).settlement!.requestedAt;
    const s1 = await markSent(B.sb, t.tripId, 0, req);
    const s2 = await markSent(B.sb, t.tripId, 0, req);
    expect(s1.transfers[0].sentAt).not.toBeNull();
    expect(s2.transfers[0].sentAt).toBe(s1.transfers[0].sentAt);
    expect(await code(markSent(A.sb, t.tripId, 0, req))).toBe("NOT_YOUR_TRANSFER");
    expect(await code(markSent(B.sb, t.tripId, 0, "2000-01-01T00:00:00+09:00"))).toBe("SETTLEMENT_CHANGED");
    expect((await markReceived(A.sb, t.tripId, 0, req)).transfers[0].receivedAt).not.toBeNull();
  });

  it("다시 요청하면 같은 송금의 표시는 남고, 금액이 바뀌면 지워진다", async () => {
    await requestSettlement(A.sb, t.tripId, 11750, [{ from: pB, to: t.driverMemberId, amountWon: 11750 }]);
    let trip = await getTrip(B.sb, t.tripId);
    expect(trip.settlement?.transfers[0].sentAt).not.toBeNull();
    await requestSettlement(A.sb, t.tripId, 12000, [{ from: pB, to: t.driverMemberId, amountWon: 12000 }]);
    trip = await getTrip(B.sb, t.tripId);
    expect(trip.settlement?.transfers[0].sentAt).toBeNull();
  });

  it("그룹 밖 사람은 운행을 볼 수 없다", async () => {
    expect(await code(getTrip(C.sb, t.tripId))).toBe("NOT_FOUND");
  });
});
