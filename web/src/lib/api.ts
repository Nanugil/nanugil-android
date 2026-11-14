// 동승자 웹이 쓰는 서버 호출. 쓰기는 모두 RPC, 읽기는 RLS가 걸린 테이블 조회다 (docs/group-trip-api.md).
// DB의 snake_case를 화면용 camelCase로 바꾸는 일은 이 파일에서만 한다.
import type { PostgrestError, SupabaseClient } from "@supabase/supabase-js";
import { ServerError } from "./errors";
import type { Group, InviteResult, Member, Settlement, Trip } from "./types";

type Row = Record<string, unknown>;

function check<T>(res: { data: T; error: PostgrestError | null }): T {
  if (res.error) throw new ServerError(res.error.message);
  return res.data;
}

/** 초대 코드 RPC는 오류를 예외 대신 첫 행의 error 열로 돌려준다 (실패 횟수 기록 때문). */
function inviteRows(rows: Row[] | null): Row[] {
  const first = rows?.[0];
  if (first?.error) throw new ServerError(String(first.error));
  return rows ?? [];
}

function toInvite(rows: Row[] | null): InviteResult {
  const r = inviteRows(rows)[0];
  if (!r) throw new ServerError("INVALID_CODE");
  return { groupId: String(r.group_id), groupName: String(r.group_name), memberCount: Number(r.member_count) };
}

export function toTrip(r: Row): Trip {
  return {
    id: String(r.id), groupId: String(r.group_id), destinationName: String(r.destination_name),
    startedAt: String(r.started_at), endedAt: (r.ended_at as string | null) ?? null,
    settlement: (r.settlement as Settlement | null) ?? null,
  };
}

export async function previewInvite(sb: SupabaseClient, code: string): Promise<InviteResult> {
  return toInvite(check(await sb.rpc("preview_invite", { p_code: code })));
}

export async function joinGroup(sb: SupabaseClient, code: string, displayName: string): Promise<InviteResult> {
  return toInvite(check(await sb.rpc("join_group", { p_code: code, p_display_name: displayName })));
}

export async function unclaimedMembers(sb: SupabaseClient, code: string): Promise<{ id: string; displayName: string }[]> {
  return inviteRows(check(await sb.rpc("invite_unclaimed_members", { p_code: code })))
    .map((r) => ({ id: String(r.member_id), displayName: String(r.display_name) }));
}

export async function claimMember(sb: SupabaseClient, code: string, memberId: string): Promise<InviteResult> {
  return toInvite(check(await sb.rpc("claim_member", { p_code: code, p_member_id: memberId })));
}

export async function myGroups(sb: SupabaseClient): Promise<Group[]> {
  const rows = check(await sb.from("groups").select("id, name, destination_name").order("created_at", { ascending: false }));
  return (rows ?? []).map((r) => ({ id: r.id, name: r.name, destinationName: r.destination_name }));
}

export async function groupMembers(sb: SupabaseClient, groupId: string): Promise<Member[]> {
  const rows = check(await sb.from("group_members").select("id, user_id, display_name, pay_link")
    .eq("group_id", groupId).order("joined_at"));
  return (rows ?? []).map((r) => ({ id: r.id, userId: r.user_id, displayName: r.display_name, payLink: r.pay_link }));
}

/** 그룹의 운행, 최근 것부터 */
export async function groupTrips(sb: SupabaseClient, groupId: string): Promise<Trip[]> {
  const rows = check(await sb.from("trips").select("*").eq("group_id", groupId).order("started_at", { ascending: false }));
  return (rows ?? []).map(toTrip);
}

export async function getTrip(sb: SupabaseClient, tripId: string): Promise<Trip> {
  const row = check(await sb.from("trips").select("*").eq("id", tripId).maybeSingle());
  if (!row) throw new ServerError("NOT_FOUND"); // 없거나 RLS로 안 보임
  return toTrip(row);
}

export async function myMemberId(sb: SupabaseClient, groupId: string): Promise<string> {
  const { data } = await sb.auth.getUser();
  if (!data.user) throw new ServerError("NOT_MEMBER");
  const row = check(await sb.from("group_members").select("id").eq("group_id", groupId).eq("user_id", data.user.id).maybeSingle());
  if (!row) throw new ServerError("NOT_MEMBER");
  return row.id;
}

/** requestedAt은 화면이 보고 있는 정산의 것. 그사이 다시 요청됐으면 SETTLEMENT_CHANGED */
export async function markSent(sb: SupabaseClient, tripId: string, index: number, requestedAt: string): Promise<Settlement> {
  return check(await sb.rpc("mark_transfer_sent", { p_trip_id: tripId, p_index: index, p_requested_at: requestedAt })) as Settlement;
}

export async function markReceived(sb: SupabaseClient, tripId: string, index: number, requestedAt: string): Promise<Settlement> {
  return check(await sb.rpc("mark_transfer_received", { p_trip_id: tripId, p_index: index, p_requested_at: requestedAt })) as Settlement;
}

// 같은 그룹 채널 구독은 한 번에 하나씩 진행한다. 화면이 빠르게 다시 그려지면(StrictMode, 화면 이동)
// 두 구독이 동시에 "채널 없음"을 보고 같은 토픽을 만들려다 이미 subscribe된 채널에 콜백을 붙이게 된다.
const subscribeQueue = new Map<string, Promise<unknown>>();

/**
 * 그룹 채널(group:{그룹 ID}, private)에서 운행 변경을 받는다. 돌려준 함수를 부르면 구독을 끊는다.
 * 비공개 채널이라 구독 전에 setAuth로 로그인 토큰을 실어야 한다. 빠지면 조용히 아무것도 오지 않는다.
 */
export async function subscribeGroup(
  sb: SupabaseClient, groupId: string, onTripChange: (trip: Trip) => void,
  onStatus?: (status: "SUBSCRIBED" | "CHANNEL_ERROR" | "TIMED_OUT" | "CLOSED") => void,
): Promise<() => void> {
  const topic = `realtime:group:${groupId}`;
  const prev = subscribeQueue.get(topic) ?? Promise.resolve();
  let release = () => {};
  const mine = new Promise<void>((r) => { release = r; });
  subscribeQueue.set(topic, prev.then(() => mine));
  await prev;
  try {
    await sb.realtime.setAuth();
    // 앞 화면이 남긴 같은 토픽 채널을 지운다. 남아 있으면 supabase-js가 그 채널을 돌려줘 새 콜백이 버려진다.
    await Promise.all(sb.getChannels().filter((c) => c.topic === topic).map((c) => sb.removeChannel(c)));
    const channel = sb
      .channel(`group:${groupId}`, { config: { private: true } })
      .on("postgres_changes", { event: "*", schema: "public", table: "trips", filter: `group_id=eq.${groupId}` },
        (payload) => { if (payload.new && "id" in payload.new) onTripChange(toTrip(payload.new as Row)); })
      // 연결(재연결 포함)될 때마다 알려 준다: 그 사이에 바뀐 값은 화면이 다시 읽어 와야 한다
      .subscribe((status) => onStatus?.(status as "SUBSCRIBED" | "CHANNEL_ERROR" | "TIMED_OUT" | "CLOSED"));
    return () => { void sb.removeChannel(channel); };
  } finally {
    release();
  }
}
