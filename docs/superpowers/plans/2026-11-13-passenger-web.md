# 동승자 웹 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 동승자가 휴대폰 웹에서 가입하고, 초대 코드로 그룹에 들어가고, 운전자의 정산 요청을 실시간으로 받아 카카오페이로 보내게 한다.

**Architecture:** 기존 Supabase 프로젝트에 마이그레이션 하나(열 2개, RPC 6개)를 더한다. `web/`의 Next.js 앱은 RLS와 RPC만으로 데이터를 읽고 쓰며 따로 API 서버를 두지 않는다. 정산 금액은 차량 앱이 계산해서 올리고, 웹은 받은 값을 보여 주기만 한다.

**Tech Stack:** Next.js 15 (App Router) · TypeScript · Emotion (`@emotion/react`, `@emotion/styled`) · GSAP (`gsap`, `@gsap/react`) · Material Symbols Rounded (`material-symbols`) · Pretendard (`pretendard`) · `@supabase/supabase-js` + `@supabase/ssr` · Vitest · Playwright · pnpm

**Spec:** `docs/superpowers/specs/2026-11-13-passenger-web-design.md`

## Global Constraints

- 브랜치: `feat/<이슈번호>-passenger-web` (develop에서 분기, develop에 직접 커밋 금지). 이슈 번호는 시작 전에 GitHub 이슈를 만들어 받는다
- 커밋: Conventional Commits, 한국어 제목, 예 `feat(web): …`, `feat(server): …`. 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 테이블은 4개 유지(`groups`, `group_members`, `trips`, `trip_members`). 열만 추가한다
- 새 RPC는 기존 방식대로 `language plpgsql security definer set search_path = ''`, `public`·`anon`에서 실행 권한 회수, `authenticated`에만 허용. 오류는 `raise exception '<코드>'`. 초대 코드를 검사하는 RPC만 결과의 `error` 열로 돌려준다(실패 횟수를 기록해야 해서)
- 웹 환경변수는 `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`만 쓴다. service_role 키 금지
- 웹은 정산을 계산하지 않는다. `trips.settlement`에 있는 값만 보여 준다
- 색 토큰(정확한 값): Primary `#1E5EFF`, Navy `#002C5F`, Amber `#FFB020`, Danger `#E5484D`, Success `#12A150`, Ink `#0F172A`, Surface `#F1F5F9`
- 글꼴 Pretendard, 아이콘 Material Symbols Rounded만. MUI 패키지 설치 금지
- GSAP 움직임은 스펙 5장의 네 군데만. 모두 `prefers-reduced-motion: reduce`이면 바로 최종 상태
- 화면 작업(Task 4~7)은 `hallmark` 스킬을 먼저 불러 진행하고, Figma `ht5nPdmTuT1pb2ODrivuag`의 v5 화면 04·08·09를 기준으로 삼는다
- 사람이 읽는 한국어 문구(화면 문구, 문서, PR 본문)는 해요체로 쓰고, 내보내기 전에 humanize-korean으로 윤문한다
- 금액 표기는 `11,750원` (천 단위 쉼표 + `원`)

## Review Focus

1. **가입·로그인 뒤 초대 코드가 사라지는 경우** — QR로 `/join?code=4F7K2Q`에 들어왔다가 로그인 화면을 거쳐도, 끝나면 코드가 채워진 `/join`으로 돌아와야 한다 → Task 4 `safeNext` 테스트, Task 8 E2E
2. **정산 화면을 열어 둔 채 운전자가 요청을 보내는 경우** — 비공개 채널 인증이 빠지면 오류 없이 아무것도 오지 않는다. 10초 안에 화면이 바뀌어야 한다 → Task 2 live 테스트 realtime 항목
3. **"보냈어요"를 두 번 누르거나 남의 송금을 표시하는 경우** — 두 번째는 시간을 바꾸지 않고, 남의 것은 `NOT_YOUR_TRANSFER` → Task 2 live 테스트
4. **운전자가 값을 고쳐 정산을 다시 요청하는 경우** — 이미 "보냈어요"를 누른 송금은 (보낸 사람, 받는 사람, 금액)이 같으면 표시가 남아야 한다 → Task 1 규칙, Task 2 live 테스트
5. **그룹에는 있지만 이번 운행에 안 탔거나 보낼 돈이 없는 사람이 정산 화면을 여는 경우** — 오류 대신 "보낼 돈이 없어요"와 그룹 전체 현황을 보여 준다. 받는 쪽(운전자)이 웹으로 열면 받을 목록을 보여 준다 → Task 3 `viewFor` 테스트

---

### Task 0: 작업 브랜치

- [ ] **Step 1:** GitHub 이슈 "동승자 웹(가입·초대 코드 참여·정산 알림)"을 만들고 번호 N을 받는다
- [ ] **Step 2:** `git switch develop && git pull && git switch -c feat/N-passenger-web`
- [ ] **Step 3:** 설계 문서와 이 계획을 첫 커밋으로 넣는다

```bash
git add docs/superpowers/specs/2026-11-13-passenger-web-design.md docs/superpowers/plans/2026-11-13-passenger-web.md
git commit -m "docs: 동승자 웹 설계·구현 계획 추가"
```

---

### Task 1: 서버 마이그레이션

**Files:**
- Create: `supabase/migrations/20261113000000_passenger_web.sql`
- Modify: `docs/group-trip-api.md` (52행 로그인, 131행 동승자 연락처, RPC 목록, 309행 오류 코드 표)

**Interfaces:**
- Produces (SQL):
  - 열 `group_members.pay_link text null`, check `pay_link ~ '^https://([a-z0-9-]+\.)*kakaopay\.com(/.*)?$'`
  - 열 `trips.settlement jsonb null`. 모양: `{ requestedAt, requestedBy, perPersonWon, transfers: [{ from, to, amountWon, sentAt, receivedAt }] }` (member_id는 uuid 문자열, 시각은 timestamptz ISO 문자열 또는 null)
  - `set_pay_link(p_group_id uuid, p_link text) returns void` — 내 멤버 행만. 형식이 틀리면 `INVALID_PAY_LINK`. `null`이면 지운다
  - `request_settlement(p_trip_id uuid, p_per_person_won bigint, p_transfers jsonb) returns jsonb` — `p_transfers`는 `[{from, to, amountWon}]`. 결과는 저장된 `settlement`
  - `mark_transfer_sent(p_trip_id uuid, p_index int) returns jsonb`
  - `mark_transfer_received(p_trip_id uuid, p_index int) returns jsonb`
  - `invite_unclaimed_members(p_code text) returns table (error text, member_id uuid, display_name text)` — `user_id is null`인 멤버 목록. 오류면 한 행에 `error`만
  - `claim_member(p_code text, p_member_id uuid) returns table (error text, group_id uuid, group_name text, member_count int)` — `join_group`과 같은 모양
- 새 오류 코드: `INVALID_TRANSFER`, `NOT_YOUR_TRANSFER`, `MEMBER_TAKEN`, `INVALID_PAY_LINK` (`NOT_ARRIVED`, `NOT_DRIVER`, `ALREADY_MEMBER`, `INVALID_CODE`, `TOO_MANY_ATTEMPTS`는 재사용)

> 스펙과 다른 점(이 계획에서 정함): `claim_member`는 그룹 ID 대신 초대 코드를 받는다. 이렇게 해야 "초대 코드 검증 직후에만"을 서버가 보장할 수 있다. 참여 전에는 RLS 때문에 멤버 목록을 읽을 수 없어서 `invite_unclaimed_members`를 더했다. `request_settlement`는 jsonb 하나 대신 1인 금액과 송금 목록을 따로 받고, `requestedAt`·`requestedBy`·`sentAt`·`receivedAt`은 서버가 채운다.

- [ ] **Step 1: 규칙 확인 (SQL 작성 전)**
  - `request_settlement`: `_my_driver_member_id(p_trip_id)`로 운전자 확인. 도착 조건은 `save_trip_cost`와 같다(`arrived_at is not null or ended_at is not null`, 아니면 `NOT_ARRIVED`). 각 송금은 `from <> to`, `amountWon > 0`이어야 하고 `from`·`to`가 모두 이 운행의 `trip_members.member_id`여야 한다. 아니면 `INVALID_TRANSFER`. `p_per_person_won >= 0`
  - 다시 요청하면: 새 송금 중에 기존 `settlement`에 (from, to, amountWon)이 같은 송금이 있으면 그 `sentAt`·`receivedAt`을 이어받는다. 나머지는 null
  - `mark_transfer_sent`: 운행이 속한 그룹의 내 member_id가 `transfers[p_index].from`이 아니면 `NOT_YOUR_TRANSFER`. 범위 밖 index거나 `settlement`가 null이면 `INVALID_TRANSFER`. `sentAt`이 이미 있으면 그대로 둔다. 갱신은 `jsonb_set(settlement, array['transfers', p_index::text, 'sentAt'], to_jsonb(now()))`
  - `mark_transfer_received`: 같은 방식으로 `to`와 `receivedAt`
  - `claim_member`: `_find_group_by_code` → 이미 멤버면 `ALREADY_MEMBER` → `p_member_id`가 그 그룹 멤버이고 `user_id is null`일 때만 `update … set user_id = auth.uid()`. 아니면 `MEMBER_TAKEN`. 오류는 결과 열로 돌려준다
- [ ] **Step 2:** 마이그레이션 SQL 작성. 맨 아래에 새 함수 6개 모두 `revoke all … from public, anon; grant execute … to authenticated;`
- [ ] **Step 3: 적용** — `psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f supabase/migrations/20261113000000_passenger_web.sql` (DB URL은 대시보드 Settings → Database에서 받는다. Supabase CLI는 설치돼 있지 않다)
  Expected: `ALTER TABLE` 2개, `CREATE FUNCTION` 6개, 오류 없음
- [ ] **Step 4: 대시보드 설정** — Authentication → Providers → Email의 "Confirm email" 끄기, Rate Limits에서 가입 제한 확인
- [ ] **Step 5: 문서 수정** — `docs/group-trip-api.md`: 로그인 줄을 "차량 앱은 익명 로그인, 동승자 웹은 이메일 가입"으로, 연락처 줄을 "이메일은 로그인에만 쓰고 다른 멤버에게 보이지 않는다, 전화번호는 저장하지 않는다"로 바꾼다. RPC 표와 오류 코드 표에 새 항목 추가. 윤문 후 반영
- [ ] **Step 6: 커밋** — `feat(server): 정산 요청·송금 표시·이름 연결 RPC와 송금 링크 열 추가`
  (동작 검증은 Task 2의 live 테스트가 맡는다)

---

### Task 2: web 뼈대 + 서버 API 래퍼 + live 테스트

**Files:**
- Create: `web/package.json`, `web/tsconfig.json`, `web/next.config.ts`, `web/.env.example`, `web/vitest.config.ts`, `web/.gitignore`
- Create: `web/src/lib/supabase/browser.ts`, `web/src/lib/supabase/server.ts`
- Create: `web/src/lib/types.ts`, `web/src/lib/api.ts`, `web/src/lib/errors.ts`
- Create: `web/tests/helpers.ts`, `web/scripts/seed-demo.ts`
- Test: `web/tests/server.live.test.ts`

**Interfaces:**
- Consumes: Task 1 RPC 전부, 기존 `preview_invite`, `join_group`, `create_group`, `add_passenger`, `start_trip`, `join_trip`, `set_trip_passengers`, `mark_arrived`
- Produces:
  - `createBrowserSupabase(): SupabaseClient` (browser.ts), `createServerSupabase(): Promise<SupabaseClient>` (server.ts, `cookies()` 사용)
  - types.ts:
    ```ts
    type Transfer = { from: string; to: string; amountWon: number; sentAt: string | null; receivedAt: string | null }
    type Settlement = { requestedAt: string; requestedBy: string; perPersonWon: number; transfers: Transfer[] }
    type Member = { id: string; userId: string | null; displayName: string; payLink: string | null }
    type Trip = { id: string; groupId: string; destinationName: string; startedAt: string; endedAt: string | null; settlement: Settlement | null }
    type Group = { id: string; name: string; destinationName: string }
    type InviteResult = { groupId: string; groupName: string; memberCount: number }
    ```
  - api.ts (모두 `sb: SupabaseClient`를 첫 인자로 받고, 실패하면 `ServerError`를 던진다):
    `previewInvite(sb, code): Promise<InviteResult>`, `joinGroup(sb, code, displayName): Promise<InviteResult>`, `unclaimedMembers(sb, code): Promise<{ id: string; displayName: string }[]>`, `claimMember(sb, code, memberId): Promise<InviteResult>`, `myGroups(sb): Promise<Group[]>`, `groupMembers(sb, groupId): Promise<Member[]>`, `groupTrips(sb, groupId): Promise<Trip[]>`(최신순), `getTrip(sb, tripId): Promise<Trip>`, `myMemberId(sb, groupId): Promise<string>`, `markSent(sb, tripId, index): Promise<Settlement>`, `markReceived(sb, tripId, index): Promise<Settlement>`, `subscribeGroup(sb, groupId, onTripChange: (trip: Trip) => void): Promise<() => void>`
  - errors.ts: `class ServerError extends Error { code: string }`, `messageFor(code: string): string` (예: `INVALID_CODE` → "초대 코드를 다시 확인해 주세요", `NOT_YOUR_TRANSFER` → "내 송금만 표시할 수 있어요", 모르는 코드 → "잠시 후 다시 시도해 주세요")
  - tests/helpers.ts: `newUser(): Promise<SupabaseClient>`(무작위 이메일 `nanugil-test+<uuid>@example.com`으로 가입한 클라이언트), `prepareArrivedTrip(driver, passengerName): Promise<{ groupId, tripId, code, driverMemberId, passengerMemberId }>` — seed·E2E도 쓴다
  - `pnpm --dir web seed [--settle]` (scripts/seed-demo.ts) — 데모 그룹 "2026 신소재공학과 MT"(목적지 "청평호 펜션"), 운전자 2명·이름만 있는 동승자 6명, 도착 처리된 운행 1개. 초대 코드와 운전자 계정 이메일·비밀번호를 콘솔에만 출력. `--settle`이면 README 예시대로 정산 요청까지 올린다(1인 11,750원). 스펙의 `supabase/seed_demo.sql` 대신 TypeScript로 바꿨다. 운전자 계정은 Auth API로 만들어야 해서 SQL만으로는 안 된다

- [ ] **Step 1: 뼈대** — `pnpm create next-app web --ts --app --src-dir --no-tailwind --eslint --use-pnpm --import-alias "@/*"` 후
  `pnpm --dir web add @emotion/react @emotion/styled gsap @gsap/react material-symbols pretendard @supabase/supabase-js @supabase/ssr qrcode` ·
  `pnpm --dir web add -D vitest @playwright/test @types/qrcode tsx`.
  `next.config.ts`에 `compiler: { emotion: true }`. scripts: `test`(vitest run, live 제외), `test:live`(`vitest run tests/server.live.test.ts`), `e2e`(`playwright test`), `seed`(`tsx scripts/seed-demo.ts`)
- [ ] **Step 2: live 테스트 작성** (`web/tests/server.live.test.ts`). `.env.local`에 키가 없으면 `describe.skip`. 사용자 3명(A 운전자, B 동승자, C 남). A가 `prepareArrivedTrip`으로 운행을 준비하고, `mark_arrived` 전에 멈춘 상태도 한 번 쓴다. B는 `claimMember(code, B 이름 멤버 id)`로 들어온다. 테스트:
  - `unclaimedMembers`가 B 이름을 돌려주고, `claimMember` 뒤에는 빈 목록
  - C가 같은 멤버로 `claimMember` → `MEMBER_TAKEN`
  - `set_pay_link(group, 'https://evil.com/x')` → `INVALID_PAY_LINK`, `'https://qr.kakaopay.com/abc'` → 성공
  - 도착 전 `request_settlement` → `NOT_ARRIVED`
  - B가 `request_settlement` → `NOT_DRIVER`
  - `from == to`이거나 운행 밖 멤버 → `INVALID_TRANSFER`
  - **realtime**: B가 `subscribeGroup` 후 A가 `request_settlement(trip, 11750, [{from:B, to:A, amountWon:11750}])` → B 콜백이 10초 안에 `settlement.perPersonWon === 11750`을 받음
  - B가 `markSent(trip, 0)` 두 번 → 두 번째 결과의 `sentAt`이 첫 번째와 같음
  - A가 `markSent(trip, 0)` → `NOT_YOUR_TRANSFER`, A가 `markReceived(trip, 0)` → `receivedAt`이 채워짐
  - A가 같은 송금으로 다시 `request_settlement` → `sentAt`·`receivedAt` 유지. 금액을 12000으로 바꿔 다시 → 둘 다 null
  - C(그룹 밖)가 `getTrip` → 행이 안 보임(RLS)
- [ ] **Step 3:** `pnpm --dir web test:live` → 래퍼가 없어 FAIL
- [ ] **Step 4:** `supabase/*.ts`, `types.ts`, `errors.ts`, `api.ts`, `helpers.ts` 구현. DB의 snake_case를 camelCase로 바꾸는 일은 api.ts 안에서만 한다. `subscribeGroup`은 `await sb.realtime.setAuth()` 뒤 `sb.channel(\`group:${groupId}\`, { config: { private: true } })`에 `postgres_changes` `{ event: 'UPDATE', schema: 'public', table: 'trips', filter: \`group_id=eq.${groupId}\` }`를 걸고 해제 함수를 돌려준다
- [ ] **Step 5:** `pnpm --dir web test:live` → PASS, `pnpm --dir web seed --settle` → 초대 코드 출력. 기존 `ServerLiveTest`와 마찬가지로 이 테스트도 실행할 때마다 서버에 시험용 그룹이 남는다
- [ ] **Step 6: 커밋** — `feat(web): Next.js 뼈대와 Supabase API 래퍼, 서버 live 테스트 추가`

---

### Task 3: 순수 함수 (정산 보기·코드·링크·금액)

**Files:**
- Create: `web/src/lib/settlement.ts`, `web/src/lib/inviteCode.ts`, `web/src/lib/payLink.ts`, `web/src/lib/format.ts`
- Test: 같은 이름의 `*.test.ts` 4개

**Interfaces:**
- Consumes: `Settlement`, `Transfer` (Task 2)
- Produces:
  - `type TransferStatus = 'pending' | 'sent' | 'received'`, `statusOf(t: Transfer): TransferStatus` — `receivedAt`이 있으면 `received`, 아니면 `sentAt`이 있으면 `sent`
  - `type SettlementView = { toSend: { index: number; transfer: Transfer }[]; toReceive: { index: number; transfer: Transfer }[]; perPersonWon: number; allDone: boolean }`
  - `viewFor(s: Settlement, myMemberId: string): SettlementView` — `allDone`은 송금이 하나 이상이고 모두 `received`일 때 true
  - `normalizeInviteCode(input: string): string | null` — 공백 제거·대문자, `^[A-HJ-NP-Z2-9]{6}$`이 아니면 null
  - `isKakaoPayLink(url: string | null): boolean` — Task 1 check와 같은 정규식
  - `formatWon(n: number): string` — `Intl.NumberFormat('ko-KR')` + `원`

- [ ] **Step 1: 실패하는 테스트 작성**
  ```ts
  // settlement.test.ts
  const s = { requestedAt: '2026-11-15T18:20:00+09:00', requestedBy: 'A', perPersonWon: 11750, transfers: [
    { from: 'B', to: 'A', amountWon: 11750, sentAt: null, receivedAt: null },
    { from: 'C', to: 'A', amountWon: 11750, sentAt: '2026-11-15T18:30:00+09:00', receivedAt: null },
    { from: 'D', to: 'E', amountWon: 11750, sentAt: 'x', receivedAt: 'y' },
  ]}
  expect(viewFor(s, 'B').toSend.map(x => x.index)).toEqual([0])
  expect(viewFor(s, 'A').toReceive.map(x => x.index)).toEqual([0, 1])
  expect(viewFor(s, 'Z')).toEqual({ toSend: [], toReceive: [], perPersonWon: 11750, allDone: false })
  expect(statusOf(s.transfers[1])).toBe('sent'); expect(statusOf(s.transfers[2])).toBe('received')
  expect(viewFor({ ...s, transfers: [s.transfers[2]] }, 'Z').allDone).toBe(true)
  // inviteCode.test.ts
  expect(normalizeInviteCode(' 4f7k2q ')).toBe('4F7K2Q'); expect(normalizeInviteCode('4F7K2O')).toBeNull(); expect(normalizeInviteCode('4F7K2')).toBeNull()
  // payLink.test.ts
  expect(isKakaoPayLink('https://qr.kakaopay.com/abc')).toBe(true)
  expect(isKakaoPayLink('http://qr.kakaopay.com/abc')).toBe(false)
  expect(isKakaoPayLink('https://kakaopay.com.evil.io/x')).toBe(false); expect(isKakaoPayLink(null)).toBe(false)
  // format.test.ts
  expect(formatWon(11750)).toBe('11,750원'); expect(formatWon(0)).toBe('0원')
  ```
- [ ] **Step 2:** `pnpm --dir web test` → FAIL (모듈 없음)
- [ ] **Step 3:** 네 파일 구현
- [ ] **Step 4:** `pnpm --dir web test` → PASS
- [ ] **Step 5: 커밋** — `feat(web): 정산 보기·초대 코드·송금 링크·금액 표기 함수 추가`

---

### Task 4: 테마·공용 UI·인증 화면

**Files:**
- Create: `web/src/ui/theme.ts`, `web/src/ui/EmotionRegistry.tsx`, `web/src/ui/Providers.tsx`, `web/src/ui/Icon.tsx`, `web/src/ui/GlassCard.tsx`, `web/src/ui/Button.tsx`, `web/src/ui/motion.ts`
- Create: `web/src/app/layout.tsx`, `web/src/app/login/page.tsx`, `web/src/app/signup/page.tsx`, `web/src/middleware.ts`, `web/src/lib/next.ts`, `web/public/manifest.webmanifest`
- Test: `web/src/lib/next.test.ts`

**Interfaces:**
- Produces:
  - `theme`: `colors`(Global Constraints의 7색 + `bg`, `card`, `cardBorder`, `textMuted`를 다크·라이트 두 벌), `type`(`display`, `title`, `heading`, `body`, `caption` — 브랜드 보드 56/40/32/28/24 비율을 휴대폰용으로 줄인 px), `radius`, `space`
  - `EmotionRegistry`: `useServerInsertedHTML`로 스타일을 넣는 클라이언트 컴포넌트(Emotion 공식 App Router 예제 방식, `createCache({ key: 'css' })`, `cache.compat = true`)
  - `<Icon name="directions_car" />` — `material-symbols-rounded` 클래스 span, `aria-hidden`
  - `<GlassCard>`, `<Button variant="primary" | "ghost">`
  - motion.ts: `usePrefersReducedMotion(): boolean`, Task 5~7이 쓰는 `useShake(ref, trigger: number)`, `useRiseIn(ref)`, `useCountUp(ref, to: number)`, `useDrawCheck(ref, active: boolean)`, `useFlashOnChange(ref, key: unknown)` — 모두 `useGSAP` 기반이고, reduced motion이면 최종 상태로 바로 설정
  - `safeNext(next: string | null): string` (lib/next.ts) — `/`로 시작하고 `//`로 시작하지 않는 경로만 허용, 아니면 `/`
  - middleware: 로그인 안 된 요청이 `/login`·`/signup`이 아니면 `/login?next=<원래 경로+쿼리>`로 보낸다. `@supabase/ssr` 공식 middleware 방식으로 세션 쿠키를 갱신한다

- [ ] **Step 1: 실패하는 테스트**
  ```ts
  expect(safeNext('/join?code=4F7K2Q')).toBe('/join?code=4F7K2Q')
  expect(safeNext('//evil.com')).toBe('/'); expect(safeNext('https://evil.com')).toBe('/'); expect(safeNext(null)).toBe('/')
  ```
- [ ] **Step 2:** `pnpm --dir web test` → FAIL
- [ ] **Step 3:** `hallmark` 스킬을 불러 디자인 기준을 잡은 뒤 테마·공용 UI·레이아웃(Pretendard, `material-symbols/rounded.css` import, 다크 기본 + `prefers-color-scheme: light`)과 로그인·가입 화면을 구현한다. 가입 입력: 이메일, 비밀번호(8자 이상), 표시 이름(1~20자, `user_metadata.display_name`). 가입 화면에 "이메일과 표시 이름만 받아요. 이메일은 다른 멤버에게 보이지 않아요" 한 줄. 성공하면 `router.replace(safeNext(next))`. manifest: 이름 "나누길", `theme_color` `#002C5F`, `display: standalone`
- [ ] **Step 4:** `pnpm --dir web test` → PASS, `pnpm --dir web build` → 성공
- [ ] **Step 5: 확인** — `pnpm --dir web dev` 후 `/join?code=4F7K2Q`로 들어가면 `/login?next=%2Fjoin%3Fcode%3D4F7K2Q`로 가고, 가입하면 `/join?code=4F7K2Q`로 돌아온다
- [ ] **Step 6: 커밋** — `feat(web): 테마·공용 UI와 이메일 가입·로그인 화면 추가`

---

### Task 5: 코드로 참여 (`/join`)

**Files:**
- Create: `web/src/app/join/page.tsx`, `web/src/app/join/JoinFlow.tsx`, `web/src/ui/CodeInput.tsx`

**Interfaces:**
- Consumes: `normalizeInviteCode`, `previewInvite`, `unclaimedMembers`, `claimMember`, `joinGroup`, `messageFor`, `useShake`
- Produces: `<CodeInput value onChange onComplete />` — 6칸(v5 화면 04 모양), 붙여 넣기 지원, `autoCapitalize="characters"`

- [ ] **Step 1:** `hallmark`로 04 화면을 휴대폰 세로에 맞춰 옮긴다. 단계: ① 코드 입력(쿼리 `code`가 있으면 채운 채로 바로 미리보기) ② 미리보기 카드(그룹 이름, 인원) ③ 이름 고르기: `unclaimedMembers` 목록 + "목록에 없어요, 새로 참여" ④ 완료 → `/groups/<id>`로 이동
- [ ] **Step 2:** 오류 처리: `INVALID_CODE`면 `useShake` + 안내 문구, `TOO_MANY_ATTEMPTS`면 입력 비활성 + 안내, `ALREADY_MEMBER`면 오류 없이 그 그룹 화면으로 이동(그룹 ID는 `previewInvite` 결과), `MEMBER_TAKEN`이면 목록을 다시 불러온다. 새로 참여할 때 표시 이름 기본값은 `user_metadata.display_name`
- [ ] **Step 3: 확인** — `prepareArrivedTrip`으로 만든 그룹 코드로 dev 서버에서 ①→④를 직접 해 본다. 틀린 코드 5번 → 여섯 번째에 `TOO_MANY_ATTEMPTS` 안내
- [ ] **Step 4: 커밋** — `feat(web): 초대 코드로 그룹 참여 화면 추가`

---

### Task 6: 홈과 그룹 화면 (`/`, `/groups/[id]`)

**Files:**
- Create: `web/src/app/page.tsx`, `web/src/app/groups/[id]/page.tsx`, `web/src/app/groups/[id]/GroupLive.tsx`, `web/src/ui/MemberRow.tsx`, `web/src/ui/StatusChip.tsx`, `web/src/lib/notify.ts`

**Interfaces:**
- Consumes: `myGroups`, `groupMembers`, `groupTrips`, `myMemberId`, `subscribeGroup`, `statusOf`, `viewFor`, `formatWon`
- Produces:
  - `<MemberRow name trailing />`, `<StatusChip status: TransferStatus />` — "확인 전"(회색) / "보냈어요"(Primary) / "송금 완료"(Success), 09 화면 모양
  - `notifySettlement(groupName: string, amountWon: number): void` — `Notification.permission === 'granted'`일 때만 "정산 요청이 왔어요 · 11,750원"
  - `askNotificationPermission(): Promise<void>` — 버튼을 누를 때만 부른다(자동 요청 금지)

- [ ] **Step 1:** 홈: 내 그룹 카드 목록. 카드마다 진행 중 운행("운행 중 · 목적지")과, 나에게 온 정산 요청이 있으면 금액과 "정산하기" 버튼. 그룹이 없으면 "초대 코드로 참여" 버튼(`/join`)
- [ ] **Step 2:** 그룹 화면: 멤버 목록과 운행 상태. `GroupLive`가 `subscribeGroup`으로 trips 변경을 받아 갱신한다. 새로 `settlement`가 생기고 내 `toSend`가 있으면 `notifySettlement` 후 `/trips/<id>/settle`로 이동. 아래쪽에 "알림 받기" 버튼(권한이 `default`일 때만)과 "홈 화면에 추가하면 알림을 더 잘 받아요" 안내
- [ ] **Step 3: 확인** — 그룹 화면을 열어 둔 채 다른 터미널에서 `pnpm --dir web seed --settle`로 정산 요청을 올리면 10초 안에 정산 화면으로 넘어간다
- [ ] **Step 4: 커밋** — `feat(web): 홈·그룹 화면과 정산 요청 실시간 알림 추가`

---

### Task 7: 정산 화면 (`/trips/[id]/settle`)

**Files:**
- Create: `web/src/app/trips/[id]/settle/page.tsx`, `web/src/app/trips/[id]/settle/SettleView.tsx`, `web/src/ui/AmountCard.tsx`

**Interfaces:**
- Consumes: `getTrip`, `groupMembers`, `myMemberId`, `viewFor`, `statusOf`, `isKakaoPayLink`, `markSent`, `markReceived`, `subscribeGroup`, `formatWon`, `MemberRow`, `StatusChip`, `useRiseIn`, `useCountUp`, `useDrawCheck`, `useFlashOnChange`

- [ ] **Step 1:** `hallmark`로 08·09 화면을 옮긴다. 위에서부터 `AmountCard`(1인 금액, Display 크기, `useRiseIn` + `useCountUp`), 내가 보낼 송금 카드(받는 사람 이름, 금액, "카카오페이로 보내기", "보냈어요"), 그룹 전체 송금 현황(`MemberRow` + `StatusChip`)
- [ ] **Step 2:** "카카오페이로 보내기"는 받는 사람 `payLink`가 `isKakaoPayLink`일 때만 `<a target="_blank" rel="noopener noreferrer">`로 보인다. 아니면 비활성 + "운전자가 아직 송금 링크를 등록하지 않았어요"
- [ ] **Step 3:** "보냈어요" → `markSent` → `useDrawCheck`로 체크를 그리고 칩을 Success로 바꾼다. 받는 사람(운전자)이 웹으로 열면 `toReceive` 목록에 "받았어요" 버튼(`markReceived`). 보낼 송금이 없으면 "보낼 돈이 없어요". `settlement`가 null이면 "아직 정산 요청 전이에요" + 그룹 화면 링크
- [ ] **Step 4:** `subscribeGroup`으로 다른 사람의 표시가 바뀌면 그 행만 `useFlashOnChange`. 위쪽에 "카톡으로 공유" 버튼: `navigator.share({ title: '나누길 정산', url: location.href })`, 지원하지 않으면 링크 복사
- [ ] **Step 5: 확인** — 휴대폰 폭(390px)에서 한 화면에 금액과 보내기 버튼이 다 보인다. 움직임 줄이기를 켜면 숫자가 바로 최종 금액으로 나온다
- [ ] **Step 6: 커밋** — `feat(web): 정산 확인·송금 표시 화면 추가`

---

### Task 8: 시연 데이터·E2E·배포

**Files:**
- Create: `web/e2e/flow.spec.ts`, `web/playwright.config.ts`, `web/README.md`
- Modify: `README.md` (기술 스택의 실시간 통신·백엔드 "미정" → Supabase, 동승자 웹 소개와 주소, 이메일 확인을 끈 것은 공모전 시연용이라는 메모)

**Interfaces:**
- Consumes: `newUser`, `prepareArrivedTrip`, `pnpm --dir web seed` (Task 2)

- [ ] **Step 1: E2E 작성** (`flow.spec.ts`, 휴대폰 뷰포트 390×844): `prepareArrivedTrip`으로 만든 코드로 `/join?code=…` 접속 → 로그인 화면으로 가는지 확인 → 가입 → 코드가 채워진 참여 화면 → 동승자 이름 고르기 → 그룹 화면 → 테스트 안에서 `request_settlement` → 10초 안에 정산 화면에 `11,750원` → "보냈어요" → 칩이 "보냈어요"
- [ ] **Step 2:** `pnpm --dir web e2e` → PASS (키가 없으면 skip)
- [ ] **Step 3: 배포** — Vercel 프로젝트를 만들고 Root Directory를 `web`으로 두고 환경변수 2개를 등록한다. 배포 주소에서 Step 1 흐름을 휴대폰으로 직접 해 본다
- [ ] **Step 4:** README 수정(윤문 후), `web/README.md`에 실행·시드·테스트 명령 정리
- [ ] **Step 5: 커밋** — `feat(web): 시연 데이터 스크립트·E2E 추가, README에 동승자 웹 안내`
- [ ] **Step 6:** PR `feat: 동승자 웹 (가입·초대 코드 참여·정산 알림)` → develop. 본문에 `Closes #N`과 확인 방법(live 테스트·E2E·배포 주소). 윤문 후 올린다

---

### Task 9 (마감 후 가능): 차량 앱 연결

마감 전에는 Task 8의 시드로 시연하고, 이 Task는 시간이 남을 때만 한다. 앱이 목업 저장소(`RouteFollowingGroupRepository`)를 Supabase로 바꾼 뒤에야 실제로 의미가 있고, 그 전환은 이 계획 범위 밖이다.

**Files:**
- Modify: `app/src/main/java/com/nanugil/app/data/server/GroupTripApi.kt`
- Modify: `app/src/main/java/com/nanugil/app/ui/navi/GroupScreens.kt` (03 대기실 QR)
- Modify: `app/src/main/java/com/nanugil/app/ui/settlement/PayLinkSetupActivity.kt`, `app/src/main/java/com/nanugil/app/ui/settlement/SettlementSession.kt`
- Test: `app/src/androidTest/java/com/nanugil/app/data/server/ServerLiveTest.kt`

**Interfaces:**
- Produces (`GroupTripApi`): `suspend fun setPayLink(groupId: String, link: String?)`, `suspend fun requestSettlement(tripId: String, perPersonWon: Long, transfers: List<Transfer>)`(`Transfer`는 `share/SettlementCalculator.kt`의 것이고 id는 member_id), `suspend fun markTransferReceived(tripId: String, index: Int)`

- [ ] **Step 1:** `ServerLiveTest`에 세 래퍼 호출 테스트를 추가 → FAIL → 구현 → `./gradlew connectedDebugAndroidTest` PASS
- [ ] **Step 2:** 03 대기실에 `https://<배포 주소>/join?code=<코드>` QR(기존 `ui/settlement/QrCode.kt` 재사용). 송금 링크를 저장할 때 `setPayLink`, 정산 요청할 때 `requestSettlement`, 09 화면에서 체크할 때 `markTransferReceived`
- [ ] **Step 3: 커밋** — `feat(server): 차량 앱에서 정산 요청·송금 링크를 서버에 올림`
