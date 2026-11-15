# 서버 연결 순서 (동승자 웹 + Supabase)

브랜치 `feat/26-passenger-web` 기준입니다. 순서대로 하면 30분 안에 실제 서버로 가입·참여·정산까지 확인할 수 있습니다.

## 0. 지금 상태 확인

```bash
cd web
npm install
npm run check:server
```

✗로 표시된 항목이 할 일입니다. 2026-11-15 기준으로 남은 항목은 아래 두 가지입니다.

## 1. Supabase 대시보드 설정 (5분)

1. **Authentication → Sign In / Providers → Email → Confirm email 끄기**
   켜 두면 가입 직후 로그인되지 않고 확인 메일 발송 한도(시간당 몇 통)에 바로 걸립니다. 공모전 시연용 설정입니다. 실서비스 전에는 SMTP를 연결한 뒤 다시 켭니다.
2. **SQL Editor → 새 쿼리 → `supabase/migrations/20261113000000_passenger_web.sql` 전체 붙여 넣고 실행**
   `ALTER TABLE` 2개와 함수 9개(새로 만드는 7개, 기존 함수를 바꾸는 `_find_group_by_code`·`leave_group` 2개)가 오류 없이 끝나야 합니다. **새 Supabase 프로젝트라면** `supabase/migrations/`의 파일을 이름 순서대로 모두 실행합니다. 같은 파일을 두 번 실행하면 "already exists" 오류가 나므로 한 번만 실행합니다.

적용 전에 로컬에서 미리 확인하려면 저장소 루트에서 `supabase/tests/run.sh`를 실행합니다(로컬 Postgres로 마이그레이션 전체와 권한 규칙을 시험합니다).

## 2. 실제 동작 확인 (10분)

```bash
cd web
npm run check:server   # 모두 ✓인지
npm run test:live      # 실제 서버에서 가입·참여·정산 요청·실시간 알림·송금 표시를 끝까지 시험
npm run e2e            # 브라우저로 가입부터 송금 완료 표시까지 (처음 한 번 npx playwright install chromium)
```

`test:live`와 `e2e`는 실행할 때마다 시험용 사용자와 그룹을 서버에 남깁니다.

## 3. 시연 데이터 만들기

```bash
npm run seed -- --settle
```

"2026 신소재공학과 MT" 그룹(운전자 2명, 동승자 6명, 1인 11,750원)을 만들고 초대 코드와 운전자 계정을 출력합니다. 휴대폰으로 `/join?code=<초대 코드>`에 들어가 동승자 이름을 고르면 바로 정산 화면을 볼 수 있습니다.

## 4. 배포 (Vercel)

1. Vercel에서 이 저장소를 가져오고 **Root Directory를 `web`** 으로 지정합니다.
2. 환경변수 `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`를 넣습니다. service_role 키는 넣지 않습니다.
3. 배포 주소를 Supabase **Authentication → URL Configuration → Site URL**에 넣습니다.
4. 휴대폰으로 배포 주소에 접속해 2번 흐름을 다시 확인합니다.

## 5. 차량 앱 연결 (다음 단계)

차량 앱은 아직 일부 화면이 목업 저장소(`RouteFollowingGroupRepository`, `FakeGroupLocationRepository`)를 씁니다. 서버로 바꾸는 작업은 이슈 #18에서 다룹니다. 동승자 웹과 맞물리려면 차량 앱이 다음 RPC를 호출해야 합니다(`docs/group-trip-api.md` 5장).

| 차량 앱 동작 | RPC |
| --- | --- |
| 송금 링크 등록 | `set_pay_link(p_group_id, p_link)` |
| 정산 요청 | `request_settlement(p_trip_id, p_per_person_won, p_transfers)` |
| 수령 확인 | `mark_transfer_received(p_trip_id, p_index, p_requested_at)` |
| 대기실 QR | `https://<배포 주소>/join?code=<초대 코드>` |

## 문제가 생기면

| 증상 | 원인과 해결 |
| --- | --- |
| 가입 후 "가입 확인 메일을 보냈습니다"만 나옴 | Confirm email이 켜져 있습니다. 1-1을 확인하세요 |
| `email rate limit exceeded` | 확인 메일 한도 초과입니다. Confirm email을 끄고 1시간 뒤 다시 시도하세요 |
| 초대 코드 입력 후 "일시적인 오류" | 동승자 웹 마이그레이션이 적용되지 않았습니다(`check:server`의 claim_member가 404) |
| 정산 화면이 실시간으로 안 바뀜 | Realtime이 켜져 있는지, `trips`가 `supabase_realtime` 발행에 들어 있는지 확인하세요(기존 마이그레이션이 추가합니다) |
| 개발 서버에서 화면이 예전 모습으로 보임 | Turbopack 캐시 문제입니다. 개발 서버를 끄고 `web/.next`를 지운 뒤 다시 실행하세요 |
