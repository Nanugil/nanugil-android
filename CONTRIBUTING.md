                                                                                                                                                                                                            # 기여 가이드

나누길 팀의 GitHub 협업 규칙이다. 팀 공통 규칙(GitHub 공통 규칙)을 이 레포에 맞게 줄였다.

> main은 언제든 제출할 수 있는 상태 · 작업은 develop에서 딴 짧은 브랜치에서 · 커밋은 `타입(영역): 내용` · PR로만 합치고 squash · 하루 끝엔 무조건 push

## 작업 흐름 한눈에

```bash
git switch develop && git pull              # 최신 develop에서 시작
git switch -c feat/12-group-drive-map       # 작업 브랜치 만들기
# ... 작업 ...
git add -p && git commit -m "feat(map): 일행 차량 위치 표시"
git push -u origin HEAD                     # 하루 끝엔 완성 안 됐어도 push
gh pr create --fill                         # 또는 GitHub 웹에서 PR 만들기
```

리뷰 1명이 승인하면 **Squash and merge**로 합친다. 브랜치는 머지할 때 자동으로 지워진다.

## 1. 브랜치: main + develop

제출본과 작업 중인 코드를 나누려고 `main`과 `develop` 두 갈래를 둔다.

```
main             ← 제출·시연용. 직접 커밋·push 금지
 ↑ 마일스톤마다 develop → main (팀장·PM)
develop          ← 기능을 모으는 통합 브랜치. 직접 push 금지
 ↑ PR (squash)
feat/... fix/... ← develop에서 따서 작업하고, 머지하면 삭제
```

- 이름: `<타입>/<이슈번호>-<내용>` (이슈가 없으면 `<타입>/<영역>-<내용>`)
- 영어 소문자·숫자·하이픈만. 한글, 공백, 대문자 금지.
- 며칠 안에 머지한다. 오래 사는 브랜치는 충돌 덩어리가 된다.
- 작업 브랜치는 항상 `develop`에서 따고, PR 대상도 `develop`이다.

```
feat/12-group-drive-map
fix/34-location-share-not-stopping
feat/share-split-calculation
docs/readme-setup-guide
```

**영역** (제안이며 팀이 바꿀 수 있음)

| 영역 | 범위 |
|---|---|
| `ui` | 화면, 컴포넌트, 테마 |
| `map` | 지도, 위치 공유, 합류점 안내 |
| `share` | 비용 산출, 1/N 정산, 정산 요청 |
| `data` | Vehicle API 연동, 저장소, 네트워크 |
| `infra` | Gradle, 빌드 설정, CI |

## 2. 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/ko/v1.0.0/) 형식이다. 타입·영역은 영어 소문자, 내용은 **한국어 개조식**으로 레포 전체를 통일한다.

```
<타입>(<영역>): <무엇을 했는지>

<왜 바꿨는지 — 필요할 때만>

Refs #12
```

| 타입 | 언제 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서만 |
| `refactor` | 동작은 그대로, 구조만 개선 |
| `chore` | 설정·의존성·빌드 등 잡일 |

- 예: `feat(map): 일행 차량 위치 표시`, `fix(share): 원 단위 반올림 오류 수정`, `docs: README 설치 방법 추가`
- `(영역)`은 선택. 호환이 깨지는 변경은 `feat!:` 또는 꼬리말 `BREAKING CHANGE:`.
- 제목은 50자 안쪽, 마침표 없이.
- 한 커밋 = 한 가지 변경. 기능 추가와 리팩터링을 섞지 않는다.
- 본문엔 "무엇"이 아니라 "왜"를 쓴다. 무엇은 diff가 말해 준다.
- `test` `style` `perf` `ci` 타입은 실제로 필요해질 때 추가한다.

## 3. Pull Request

- 모든 변경은 PR로 합친다. 제목은 커밋 메시지 형식 그대로 쓴다(squash하면 이게 develop의 커밋 메시지가 된다).
- 작게: 바뀐 줄 400줄 / 파일 10개를 넘으면 쪼갤 수 있는지 먼저 본다. (대량 이름 변경·자동 생성 코드는 예외)
- 리뷰 1명 승인 후 머지. 머지는 squash, 머지한 브랜치는 바로 삭제.
- 본문에 `Closes #12`를 쓰면 기본 브랜치(develop)에 머지될 때 이슈가 자동으로 닫힌다.
- 본문 양식은 [PR 템플릿](.github/pull_request_template.md)으로 자동으로 채워진다.

## 4. 이슈

- 할 일은 이슈로 만든다. **New issue**에서 **버그** 또는 **기능** 양식을 고른다(빈 이슈는 막아 둠).
- 라벨은 최소로: 종류 `bug` `feature` `docs` + 우선순위 `P0`(지금) `P1`(이번 주) `P2`(언젠가) + `blocked`.

## 5. 하루 끝엔 무조건 push

로컬에만 있는 커밋은 없는 것과 같다. 하루 작업이 끝나면 완성이 안 됐어도 작업 브랜치를 push한다. 작업 브랜치는 보여 주는 곳이 아니라 잃어버리지 않는 곳이다. 워크트리나 임시 폴더를 지우기 전에도 원격에 올라갔는지 확인한다.

```bash
git status -sb            # [ahead N]이 보이면 아직 안 올라간 커밋이 있다
git push -u origin HEAD   # 현재 브랜치를 원격에 올린다
```

## 6. 비밀값

- `.env`, `local.properties`, 서명 키(`*.jks`, `*.keystore`)는 커밋하지 않는다(`.gitignore`에 등록됨).
- 필요한 변수는 이름만 적은 `.env.example`로 공유한다. CI용 키는 GitHub Secrets에 둔다.

## 7. 릴리스

- 버전은 SemVer `MAJOR.MINOR.PATCH`를 쓴다. 시연이나 제출 시점에 `v1.0.0`처럼 태그를 달고 GitHub Releases의 자동 릴리스 노트를 쓴다.

```bash
git switch develop && git pull
git tag v1.0.0 && git push origin v1.0.0
```

## 8. 문서는 어디에

| 종류 | 위치 |
|---|---|
| 기획·회의록·보고서·리스크 | 레포 밖 팀 노트(옵시디언 `10 Projects/<프로젝트>/`) |
| 트러블슈팅 기록 | [`docs/troubleshooting/`](docs/troubleshooting) |
| 기술 의사결정 기록(ADR) | [`docs/decisions/`](docs/decisions) |
| 설계 스펙, 구현 계획 | `docs/` 아래 |

새 문서는 각 폴더의 `_template.md`를 복사해 `NNN-제목.md`로 저장하고 README 표에 한 줄 추가한다. 같은 내용을 양쪽에 쓰지 않는다. 팀 노트에는 레포 문서 경로만 적는다.

## 레포 관리자 체크리스트 (레포 만들 때 한 번)

- [ ] 레포 기본 브랜치를 `develop`으로 바꾸고, main과 develop 둘 다 보호 규칙을 건다.
- [ ] main·develop 보호(Settings → Rules → Rulesets): PR 필수 · 승인 1명 · force push 금지 · 브랜치 삭제 금지. "CI 통과 필수"는 CI가 생기면 추가한다. 비공개 레포는 요금제에 따라 일부 기능이 안 될 수 있다.
- [ ] 머지 방식(Settings → General): squash만 허용
- [ ] 머지 후 브랜치 자동 삭제("Automatically delete head branches") 켜기
- [ ] Secret scanning · push protection 켜기
- [ ] Dependabot 보안 업데이트 켜기. 버전 업데이트는 주 1회로 묶는다.
- [ ] 라벨 만들기: `bug` `feature` `docs` `P0` `P1` `P2` `blocked` (이슈 양식의 라벨은 레포에 있어야 붙는다)
