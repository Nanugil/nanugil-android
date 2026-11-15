// 서버 연결 준비 상태 점검: npm run check:server
// 실제 데이터를 만들지 않고 읽기·존재 확인만 한다.
const url = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";

type Check = { name: string; ok: boolean; detail: string; fix?: string };
const checks: Check[] = [];
const add = (c: Check) => checks.push(c);

/** 함수가 서버에 있는지: 404/PGRST202 = 없음, 429 = 판단 불가(요청 제한), 5xx = 서버 오류, 401·403·42501(익명 권한 없음)·400 = 있음 */
async function rpcState(fn: string, body: Record<string, unknown>): Promise<{ ok: boolean; detail: string }> {
  const r = await fetch(`${url}/rest/v1/rpc/${fn}`, {
    method: "POST", headers: { apikey: key, "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
  const err = await r.json().catch(() => ({})) as { code?: string; message?: string };
  const detail = `${fn} → HTTP ${r.status}${err.code ? ` ${err.code}` : ""}`;
  if (r.status === 404 || err.code === "PGRST202") return { ok: false, detail: `${detail} (함수 없음)` };
  if (r.status === 429) return { ok: false, detail: `${detail} (요청 제한, 잠시 후 다시 점검)` };
  if (r.status >= 500) return { ok: false, detail: `${detail} (서버 오류: ${err.message ?? ""})` };
  return { ok: true, detail };
}

async function main() {
  add({ name: "환경변수", ok: url !== "" && key !== "", detail: url ? url : "비어 있음",
    fix: "web/.env.local에 NEXT_PUBLIC_SUPABASE_URL, NEXT_PUBLIC_SUPABASE_ANON_KEY를 넣으세요." });
  if (!url || !key) return report();

  const settings = await fetch(`${url}/auth/v1/settings`, { headers: { apikey: key } }).then((r) => r.json()).catch(() => null);
  add({ name: "서버 응답", ok: !!settings, detail: settings ? "Auth 설정 읽음" : "응답 없음", fix: "URL·키가 맞는지, 프로젝트가 일시정지 상태가 아닌지 확인하세요." });
  if (settings) {
    add({ name: "이메일 가입 켜짐", ok: settings.external?.email === true, detail: String(settings.external?.email),
      fix: "Authentication → Sign In / Providers → Email을 켜세요." });
    add({ name: "Confirm email (시연은 끔 권장)", ok: settings.mailer_autoconfirm === true || process.env.ALLOW_EMAIL_CONFIRM === "1",
      detail: `mailer_autoconfirm=${settings.mailer_autoconfirm}`,
      fix: "시연·live 테스트: Authentication → Email → Confirm email을 끄세요. 실서비스에서 SMTP와 함께 켜려면 ALLOW_EMAIL_CONFIRM=1 로 이 항목을 건너뜁니다 (확인 링크는 /auth/confirm이 처리)." });
  }

  const base = await rpcState("join_group", { p_code: "AAAAAA", p_display_name: "x" });
  add({ name: "기존 마이그레이션(그룹·운행)", ok: base.ok, detail: base.detail,
    fix: "supabase/migrations/20261111000000_group_trip.sql부터 순서대로 적용하세요." });
  const web = await rpcState("claim_member", { p_code: "AAAAAA", p_member_id: "00000000-0000-0000-0000-000000000000" });
  add({ name: "동승자 웹 마이그레이션", ok: web.ok, detail: web.detail,
    fix: "SQL Editor에 supabase/migrations/20261113000000_passenger_web.sql 내용을 붙여 넣고 실행하세요." });
  const mark = await rpcState("mark_transfer_sent", { p_trip_id: "00000000-0000-0000-0000-000000000000", p_index: 0, p_requested_at: null });
  add({ name: "송금 표시 함수(최신 시그니처)", ok: mark.ok, detail: mark.detail,
    fix: "마이그레이션 파일이 최신인지 확인하세요 (requestedAt을 받는 버전)." });
  report();
}

function report() {
  console.log("\n나누길 서버 점검\n");
  for (const c of checks) console.log(`${c.ok ? "✓" : "✗"} ${c.name}  (${c.detail})${c.ok || !c.fix ? "" : `\n    → ${c.fix}`}`);
  const bad = checks.filter((c) => !c.ok).length;
  console.log(bad ? `\n${bad}개 항목을 먼저 처리하세요. 처리 후 npm run test:live 로 실제 동작을 확인합니다.` : "\n모두 준비됐습니다. npm run test:live → npm run e2e 순서로 확인하세요.");
  process.exitCode = bad ? 1 : 0;
}

main().catch((e) => { console.error(e); process.exitCode = 1; });
