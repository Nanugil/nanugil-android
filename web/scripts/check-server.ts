// 서버 연결 준비 상태 점검: npm run check:server
// 실제 데이터를 만들지 않고 읽기·존재 확인만 한다.
const url = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";

type Check = { name: string; ok: boolean; detail: string; fix?: string };
const checks: Check[] = [];
const add = (c: Check) => checks.push(c);

async function rpcExists(fn: string, body: Record<string, unknown>): Promise<number> {
  const r = await fetch(`${url}/rest/v1/rpc/${fn}`, {
    method: "POST", headers: { apikey: key, "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
  return r.status; // 404 = 함수 없음(마이그레이션 미적용), 401/403/400 = 함수는 있음(익명은 권한 없음)
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
    add({ name: "Confirm email 꺼짐", ok: settings.mailer_autoconfirm === true, detail: `mailer_autoconfirm=${settings.mailer_autoconfirm}`,
      fix: "Authentication → Sign In / Providers → Email → Confirm email을 끄세요 (켜 두면 가입 직후 로그인이 안 되고 메일 한도에 걸립니다)." });
  }

  const base = await rpcExists("join_group", { p_code: "AAAAAA", p_display_name: "x" });
  add({ name: "기존 마이그레이션(그룹·운행)", ok: base !== 404, detail: `join_group → HTTP ${base}`,
    fix: "supabase/migrations/20261111000000_group_trip.sql부터 순서대로 적용하세요." });
  const web = await rpcExists("claim_member", { p_code: "AAAAAA", p_member_id: "00000000-0000-0000-0000-000000000000" });
  add({ name: "동승자 웹 마이그레이션", ok: web !== 404, detail: `claim_member → HTTP ${web}`,
    fix: "SQL Editor에 supabase/migrations/20261113000000_passenger_web.sql 내용을 붙여 넣고 실행하세요." });
  const mark = await rpcExists("mark_transfer_sent", { p_trip_id: "00000000-0000-0000-0000-000000000000", p_index: 0, p_requested_at: null });
  add({ name: "송금 표시 함수(최신 시그니처)", ok: mark !== 404, detail: `mark_transfer_sent(p_requested_at) → HTTP ${mark}`,
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
