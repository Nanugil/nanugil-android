# 나누길 동승자 웹

차량 앱이 없는 동승자가 휴대폰에서 쓰는 웹입니다. 가입하고, 운전자 화면의 초대 코드로 그룹에 들어가고, 도착 후 운전자가 보낸 정산 요청을 받아 카카오페이로 송금합니다.

- 설계: [`docs/superpowers/specs/2026-11-13-passenger-web-design.md`](../docs/superpowers/specs/2026-11-13-passenger-web-design.md)
- 서버 API: [`docs/group-trip-api.md`](../docs/group-trip-api.md) 5장 정산

## 처음 설정

1. `.env.example`을 `.env.local`로 복사하고 Supabase URL과 anon 키를 넣습니다. service_role 키는 쓰지 않습니다.
2. 마이그레이션 `supabase/migrations/20261113000000_passenger_web.sql`이 Supabase에 적용돼 있어야 합니다.
3. 공모전 시연용으로 Supabase 대시보드의 Authentication → Email → **Confirm email을 꺼 둡니다.** 켜 두면 가입 직후 로그인이 되지 않고 확인 메일 발송 한도에 금방 걸립니다.

## 명령

| 명령 | 하는 일 |
| --- | --- |
| `npm install` | 의존성 설치 |
| `npm run dev` | 개발 서버 (http://localhost:3000) |
| `npm test` | 단위 테스트 (서버 없이) |
| `npm run test:live` | 실제 Supabase에 붙는 서버 테스트. 실행할 때마다 시험용 사용자·그룹이 남습니다 |
| `npm run e2e` | 가입부터 정산까지 브라우저 테스트 (처음 한 번 `npx playwright install chromium`) |
| `npm run seed` | 시연 그룹·운행을 만들고 초대 코드와 운전자 계정을 출력. `npm run seed -- --settle`이면 정산 요청까지 |
| `npm run build` | 배포용 빌드 |

DB 마이그레이션은 로컬 Postgres로 따로 시험할 수 있습니다: `supabase/tests/run.sh`

## 배포

Vercel에서 Root Directory를 `web`으로 두고 환경변수 `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`를 넣습니다.
