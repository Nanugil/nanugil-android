// 실제 Supabase에 시험용 사용자·그룹·운행을 만든다. live 테스트, E2E, 시연 데이터(seed)가 함께 쓴다.
import { createClient, type SupabaseClient } from "@supabase/supabase-js";

const url = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";
export const hasServer = url !== "" && key !== "";

export type TestUser = { sb: SupabaseClient; email: string; password: string; userId: string };

/** 무작위 이메일로 가입한 클라이언트. 대시보드에서 Confirm email이 꺼져 있어야 바로 세션이 생긴다. */
export async function newUser(displayName = "시험"): Promise<TestUser> {
  const sb = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } });
  const email = `nanugil-test+${crypto.randomUUID()}@example.com`;
  const password = crypto.randomUUID();
  const { data, error } = await sb.auth.signUp({ email, password, options: { data: { display_name: displayName } } });
  if (error) throw error;
  if (!data.session) throw new Error("가입 후 세션이 없어요. Supabase 대시보드에서 Confirm email을 꺼 주세요.");
  return { sb, email, password, userId: data.user!.id };
}

async function rpc<T>(sb: SupabaseClient, fn: string, args: Record<string, unknown>): Promise<T> {
  const { data, error } = await sb.rpc(fn, args);
  if (error) throw new Error(`${fn}: ${error.message}`);
  return data as T;
}

export type PreparedTrip = {
  groupId: string; tripId: string; code: string; driverMemberId: string; passengerMemberIds: string[];
};

/**
 * driver가 그룹을 만들고 이름만 있는 동승자를 넣은 뒤 운행을 시작해 동승자를 태운다.
 * arrive가 true면 도착 처리까지 한다.
 */
export async function prepareTrip(
  driver: SupabaseClient,
  passengerNames: string[],
  opts: { groupName?: string; destination?: string; driverName?: string; arrive?: boolean } = {},
): Promise<PreparedTrip> {
  const group = await rpc<{ id: string; invite_code: string }>(driver, "create_group", {
    p_name: opts.groupName ?? "시험 MT", p_destination_name: opts.destination ?? "가평",
    p_destination_lat: 37.83, p_destination_lng: 127.51, p_display_name: opts.driverName ?? "운전자",
  });
  const passengerMemberIds: string[] = [];
  for (const name of passengerNames) {
    passengerMemberIds.push((await rpc<{ id: string }>(driver, "add_passenger", { p_group_id: group.id, p_display_name: name })).id);
  }
  const trip = await rpc<{ id: string }>(driver, "start_trip", { p_group_id: group.id });
  const me = await rpc<{ member_id: string }>(driver, "join_trip", { p_trip_id: trip.id, p_vehicle_label: "1호차", p_fuel_type: "EV" });
  await rpc(driver, "set_trip_passengers", { p_trip_id: trip.id, p_member_ids: passengerMemberIds });
  if (opts.arrive ?? true) await rpc(driver, "mark_arrived", { p_trip_id: trip.id });
  return { groupId: group.id, tripId: trip.id, code: group.invite_code, driverMemberId: me.member_id, passengerMemberIds };
}

/** 운전자가 정산 요청을 올린다 (원래는 차량 앱이 하는 일). */
export function requestSettlement(
  driver: SupabaseClient, tripId: string, perPersonWon: number, transfers: { from: string; to: string; amountWon: number }[],
) {
  return rpc<unknown>(driver, "request_settlement", { p_trip_id: tripId, p_per_person_won: perPersonWon, p_transfers: transfers });
}
