// 서버 없이 도는 테스트: 같은 그룹 채널이 아직 목록에 남아 있을 때(이전 화면이 나가는 중) 새로 구독하면
// 남은 채널을 재사용하지 말고 새 채널을 만들어야 한다. 재사용하면 새 콜백이 중복으로 버려져 실시간이 끊긴다.
import { createClient } from "@supabase/supabase-js";
import { expect, it } from "vitest";
import { subscribeGroup } from "@/lib/api";

it("남아 있는 같은 그룹 채널을 지우고 새 채널에 구독한다", async () => {
  const sb = createClient("http://127.0.0.1:9", "anon-key", { auth: { persistSession: false } });
  await subscribeGroup(sb, "g1", () => {});           // 앞 화면: 구독 해제 전에
  const first = sb.getChannels().find((c) => c.topic === "realtime:group:g1");
  await subscribeGroup(sb, "g1", () => {});           // 다음 화면이 같은 그룹을 구독
  const now = sb.getChannels().filter((c) => c.topic === "realtime:group:g1");
  expect(now).toHaveLength(1);
  expect(now[0]).not.toBe(first);
  sb.realtime.disconnect();
});

it("두 구독이 엇갈려도(앞 구독을 늦게 해제) 오류 없이 채널 하나만 남는다", async () => {
  const sb = createClient("http://127.0.0.1:9", "anon-key", { auth: { persistSession: false } });
  // React StrictMode처럼: 첫 구독이 끝나기 전에 두 번째 구독이 시작되고, 첫 구독은 끝나자마자 해제된다
  const first = subscribeGroup(sb, "g2", () => {}).then((off) => off());
  const second = subscribeGroup(sb, "g2", () => {});
  await expect(Promise.all([first, second])).resolves.toBeDefined();
  await new Promise((r) => setTimeout(r, 50));
  expect(sb.getChannels().filter((c) => c.topic === "realtime:group:g2")).toHaveLength(1);
  sb.realtime.disconnect();
});
