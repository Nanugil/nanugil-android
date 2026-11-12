# 동승자 웹 (web/) 설계

- 날짜: 2026-11-13
- 상태: 초안
- 관련: [그룹·운행 API](../../group-trip-api.md), [ADR-001 백엔드·실시간](../../decisions/001-backend-realtime.md), Figma `나누길` (로고·브랜드, 와이어프레임 v5)

## 1. 왜 만드나

나누길 차량 앱은 운전자만 쓴다. 동승자는 차량 앱이 없어서 지금은 정산 결과를 운전자 차량 화면의 QR로만 볼 수 있다. 내리고 나면 얼마를 누구에게 보내야 하는지 다시 확인할 방법이 없다.

동승자가 휴대폰으로 가입하고, 초대 코드로 그룹에 들어가고, 도착하면 "정산 요청"을 받아 카카오페이로 보내는 웹을 만든다. 현대자동차그룹 공모전 출품작이라 가입·로그인이 실제로 동작해야 한다(목업 불가).

## 2. 목표 / 하지 않을 것 / 실패로 보는 것

**목표**
- 이메일 회원가입·로그인 (Supabase Auth, 실제 계정)
- 초대 코드 6자리 → 그룹 미리보기 → 참여
- 운전자가 정산 요청을 누르면 동승자 화면에 바로 뜬다 (실시간)
- 내가 보낼 금액·받는 사람·카카오페이 송금 버튼, "보냈어요" 표시
- 차량 앱과 같은 브랜드로 보일 것

**하지 않을 것 (이번 범위 밖)**
- 차량 앱의 QR 로그인 (확장 계획으로 시연 영상에만 소개)
- 웹에서 그룹 만들기·운행 시작 (운전자 몫, 차량 앱에서만)
- 결제 직접 처리. 송금은 카카오페이 링크로 넘긴다
- 카카오 로그인, 비밀번호 찾기 메일 (2단계)

**실패로 보는 것**
- 운전자에게 주행 중 조작을 요구하는 흐름이 생기는 것
- 정산 금액이 차량 앱과 1원이라도 다른 것 (웹은 계산하지 않고 받은 값을 보여 주기만 한다)
- 이메일 외 연락처(전화번호 등)를 모으는 것

## 3. 사용 흐름

1. **출발 전** — 운전자 차량 화면(03 대기실)에 초대 코드와 QR(`https://<웹 주소>/join?code=4F7K2Q`)이 뜬다. 동승자가 찍는다.
2. **가입** — 처음이면 이메일·비밀번호·표시 이름으로 가입하고, 있으면 로그인한다. 끝나면 원래 가던 `/join`으로 돌아간다.
3. **참여** — 코드가 채워진 채로 그룹 이름·인원을 미리 본다(`preview_invite`). 운전자가 이름만으로 미리 넣어 둔 멤버가 있으면 "이 중에 나예요"를 고를 수 있다(이름 연결). 없으면 새로 참여한다(`join_group`).
4. **운행 중** — 그룹 화면에 "운행 중 · 목적지"가 실시간으로 보인다. 할 일은 없다.
5. **도착** — 운전자가 차량에서 정산 요청을 누르면 동승자 화면이 정산 화면으로 바뀌고 브라우저 알림이 뜬다.
6. **송금** — "카카오페이로 보내기"를 누르면 받는 운전자의 송금 링크가 열린다. 돌아와서 "보냈어요"를 누른다. 운전자 차량 화면(09)의 체크와 같은 값을 쓴다.

## 4. 화면

| 경로 | 화면 | 대응하는 차량 화면 |
| --- | --- | --- |
| `/login`, `/signup` | 로그인·가입 | 없음 |
| `/join?code=` | 6칸 코드 입력 → 미리보기 → 이름 연결 → 참여 | 04 코드로 참여 |
| `/` | 내 그룹 목록, 진행 중 운행, 받은 정산 요청 | 01 홈 |
| `/groups/[id]` | 그룹 멤버, 운행 상태(실시간) | 03 대기실 |
| `/trips/[id]/settle` | 내 부담액(큰 숫자), 보낼 곳, 송금 버튼, 그룹 전체 송금 현황 | 08 정산 요약, 09 정산 보냄 |

모바일 세로 화면 기준. 한 화면에 할 일은 하나만 둔다.

## 5. 디자인 방향

차량 앱 v5와 같은 앱으로 보여야 한다. 구현할 때 `hallmark` 스킬로 화면을 만들고, 기준은 Figma 파일이다.

- **색** (Figma 로고·브랜드 보드): Primary `#1E5EFF`(버튼·내 차·강조), Navy `#002C5F`, Amber `#FFB020`(목적지), Danger `#E5484D`, Success `#12A150`(송금 완료), Ink `#0F172A`, Surface `#F1F5F9`. Emotion 테마 객체 하나에 모아 둔다
- **바탕**: v5처럼 어두운 네이비 배경 위에 반투명 글래스 카드. 라이트 모드도 지원한다(Surface 바탕 + 흰 카드)
- **글자**: v5 와이어프레임의 Pretendard. 크기 단계는 브랜드 보드의 비율(Display·Title·Heading·Body·Caption)을 휴대폰에 맞게 줄여 쓴다. 정산 금액은 Display 크기로 화면에서 가장 크게
- **아이콘**: Material Symbols Rounded. 차량 화면의 탭 아이콘(드라이브·내 차·정산)과 같은 모양을 고른다
- **움직임** (GSAP): 의미가 있는 곳 네 군데에만 쓴다
  - 정산 요청이 도착하면 정산 카드가 아래에서 올라오고, 1인 금액이 0원부터 올라간다
  - 코드 6칸을 입력하면 칸이 하나씩 채워지고, 틀린 코드면 좌우로 흔들림
  - "보냈어요"를 누르면 체크 표시가 그려지고 상태 칩이 Success 색으로 바뀜(09 화면의 체크와 같은 모양)
  - 멤버 송금 현황이 실시간으로 바뀌면 그 행만 짧게 강조
  - `prefers-reduced-motion`이면 전부 끄고 바로 최종 상태를 보여 준다
- **컴포넌트**: 6칸 코드 입력(04), 멤버 행 + 상태 칩 "송금 완료/확인 전"(09), 1인 금액 큰 카드(08)를 그대로 옮긴다
- 차량 화면의 3D 도시 지도 배경은 쓰지 않는다. 휴대폰에서는 무겁고 할 일이 없다

## 6. 기술 선택

- **Next.js (App Router) + TypeScript**, 레포의 `web/` 폴더. Vercel에 Root Directory `web`으로 배포
- **스타일: Emotion** (`@emotion/react`, `@emotion/styled`). App Router에서는 Emotion이 클라이언트 컴포넌트에서만 동작한다. 그래서 `useServerInsertedHTML`로 스타일을 넣는 레지스트리를 루트 레이아웃에 하나 두고, 스타일을 쓰는 컴포넌트는 `"use client"`로 둔다. 테마는 `ThemeProvider` 하나
- **애니메이션: GSAP** (`gsap`, `@gsap/react`의 `useGSAP`). 컴포넌트가 사라질 때 애니메이션이 자동으로 정리되게 `useGSAP`만 쓴다
- **아이콘: Material Symbols Rounded**. `material-symbols` npm 패키지로 웹폰트를 직접 포함한다(외부 CDN 의존 없음). MUI 컴포넌트 라이브러리는 쓰지 않는다
- **Supabase**: 차량 앱과 같은 프로젝트. `@supabase/supabase-js` + `@supabase/ssr`(쿠키 세션)
- 환경변수: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`. service_role 키는 쓰지 않는다
- 서버 데이터는 모두 기존 RLS와 RPC로 읽고 쓴다. Next.js 쪽에 별도 API 서버를 두지 않는다

## 7. 서버 변경 (마이그레이션 1개)

테이블은 4개를 그대로 유지하고 열만 추가한다.

**열 추가**
- `group_members.pay_link text` — 운전자의 카카오페이 송금 링크. `https://…kakaopay.com/…`만 허용(차량 앱 `normalizeKakaoPayLink`와 같은 규칙). 지금 기기에만 있는 값을 서버로 옮긴다
- `trips.settlement jsonb` — 정산 요청 시점의 결과. 정산을 요청한 뒤에 값을 고쳐도 바뀌지 않는다(차량 앱 `SettlementRequest`와 같은 의미)

```json
{
  "requestedAt": "2026-11-15T18:20:00+09:00",
  "requestedBy": "<운전자 member_id>",
  "perPersonWon": 11750,
  "transfers": [
    { "from": "<member_id>", "to": "<member_id>", "amountWon": 11750, "sentAt": null, "receivedAt": null }
  ]
}
```

**RPC 추가** (기존과 같이 `security definer`, 오류는 코드 문자열)
- `set_pay_link(p_group_id, p_link)` — 내 멤버 행에만
- `request_settlement(p_trip_id, p_settlement jsonb)` — 이 운행의 운전자만, 도착 후. 모든 `from`·`to`가 이 운행 멤버인지 확인한다. 다시 부르면 덮어쓴다
- `mark_transfer_sent(p_trip_id, p_index)` — `from`이 나일 때만
- `mark_transfer_received(p_trip_id, p_index)` — `to`가 나일 때만
- `claim_member(p_group_id, p_member_id)` — `user_id`가 비어 있는(이름만 있는) 멤버를 내 계정에 연결. 초대 코드 검증을 통과한 직후에만

**실시간**: `trips`는 이미 `supabase_realtime` 발행 대상이다. 웹은 `group:{그룹 ID}` 채널에서 `trips` 변경(UPDATE)을 받아 `settlement`가 생기거나 바뀌면 화면을 갱신한다. 기존 RLS가 그룹 멤버에게만 행을 보낸다.

**새 오류 코드**: `NOT_ARRIVED`(재사용), `INVALID_TRANSFER`, `NOT_YOUR_TRANSFER`, `MEMBER_TAKEN`, `INVALID_PAY_LINK`

## 8. 알림

- **1단계 (마감 전)**: 웹이 열려 있으면 실시간으로 화면이 바뀌고, 알림 권한을 허락했다면 `Notification`으로 "정산 요청이 왔어요 · 11,750원"을 띄운다. 홈 화면에 추가(PWA 매니페스트)를 안내한다
- **2단계**: Web Push. 서비스 워커 + `push_subscriptions` 저장 + `trips.settlement`가 바뀔 때 Supabase Edge Function이 푸시를 보낸다. iPhone은 홈 화면에 추가한 경우에만(iOS 16.4+) 받을 수 있다. 이때 테이블이 하나 늘어난다
- 웹이 닫혀 있는 사람을 위한 대안: 정산 화면의 "카톡으로 공유" 버튼(Web Share API)으로 링크를 단톡방에 올린다

## 9. 차량 앱 쪽 변경

웹이 실제 데이터를 받으려면 차량 앱이 서버에 연결되어 있어야 한다. 지금은 목업이다(`NaviDriveViewModel`의 `RouteFollowingGroupRepository`, 초대 코드 6자리면 통과).

- 03 대기실: 초대 코드 옆에 `/join?code=` QR 표시
- 송금 링크 등록(`PayLinkSetupActivity`) 시 `set_pay_link`도 부른다
- 정산 요청 시 `SettlementRequest`를 member_id 기준으로 바꿔 `request_settlement`로 올린다
- 09 화면의 받음 체크는 `mark_transfer_received`로 올리고, 동승자가 누른 "보냈어요"(`sentAt`)를 표시

마감까지 차량 앱 연결이 어려우면, 시연은 시드 스크립트(`supabase/seed_demo.sql`)로 그룹·운행·정산을 넣어 웹 쪽만 실제로 동작시킨다.

## 10. 보안·개인정보

- 이메일은 `auth.users`에만 있고 다른 멤버에게 보이지 않는다. 멤버에게는 표시 이름만 보인다
- 대시보드에서 이메일 확인(Confirm email)은 끄고 가입 속도 제한을 건다. 이메일 확인을 끈 것은 공모전 시연용 설정이라고 README에 적는다
- `docs/group-trip-api.md`의 "차량 앱은 익명 로그인", "동승자 연락처를 저장하지 않는다" 문장을 이 설계에 맞게 고친다. 가입 화면에 개인정보 수집 항목(이메일, 표시 이름) 한 줄을 넣는다
- 송금 링크는 카카오페이 도메인만 열고, `rel="noopener noreferrer"`로 연다

## 11. 순서 (마감 11/15 기준)

1. 마이그레이션 + 시드 (서버)
2. `web/` 뼈대: Next.js + Emotion 레지스트리·테마 + Pretendard + Material Symbols
3. 가입·로그인 → `/join` → `/` (웹)
4. `/trips/[id]/settle` + 실시간 + 브라우저 알림 + GSAP 움직임 (웹)
5. Vercel 배포, 차량 화면에 QR
6. 시간이 남으면 차량 앱 연결(9장), 아니면 마감 후에

## 12. 내가 정한 것 (확인 부탁)

- 테이블을 늘리지 않고 `trips.settlement jsonb`에 송금 목록을 담았다. 송금 건별로 따로 조회하는 일이 없어서다
- 운전자가 이름만으로 넣은 멤버와 웹 가입자가 겹치지 않게 "이 중에 나예요" 연결을 넣었다
- Web Push는 2단계로 미뤘다. 마감 전에는 열린 화면의 실시간 갱신과 브라우저 알림까지만 한다
- 글꼴은 v5의 Pretendard를 따랐다(브랜드 보드는 Noto Sans KR)
- Material 아이콘은 MUI(`@mui/icons-material`) 대신 Material Symbols 웹폰트로 했다. MUI 아이콘 패키지는 `@mui/material`까지 함께 설치해야 해서다
- GSAP 움직임은 5장의 네 군데로 제한했다
- 정산 계산은 웹에서 하지 않는다. 차량 앱 `SettlementCalculator`가 유일한 계산 위치다

## 13. 구현하는 사람에게 맡기는 것

- 컴포넌트 구조, 상태 관리
- 가입 폼 검증 문구, 빈 화면·오류 화면 문구 (오류 코드 표의 안내 문구 말투를 따른다)
- 테스트: RPC 권한 검사(남의 송금 표시 불가 등)는 기존 `ServerLiveTest` 방식으로 하고, 웹은 가입 → 참여 → 정산 확인 흐름 하나만 E2E로 확인
