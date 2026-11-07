# Pleos Vehicle API 제공 항목 조사

정산에 필요한 차량 데이터를 Pleos Connect에서 얻을 수 있는지 조사한 문서다.

- 조사일: 2026-11-06
- 기준: Vehicle SDK 2.3.2, NaviHelper SDK 3.0.5, FusedLocation SDK 1.1.2 공식 문서
- 문서 조사만 했고 빌드나 실제 호출은 하지 않았다. 예시 값은 모두 가상의 값이다.

## 결론

- **통행료 실결제액과 주유 금액은 Pleos SDK 문서에서 찾지 못했다.**
- 주유비는 **사용한 연료 리터 × 근처 주유소 유가**로 추정한다. 리터는 Android 표준 속성 `FUEL_LEVEL`, 유가는 오피넷 API에서 가져온다.
- `FUEL_LEVEL`은 Pleos SDK에 없어 차량 지원 여부 확인과 기술 지원팀 검토가 필요하다.

## 역할별 담당

| 역할 | 담당자 | 맡은 일 | 이 문서에서 볼 곳 |
|---|---|---|---|
| 팀장 | [@apkool12](https://github.com/apkool12) | 화면 디자인 및 구현, 시연영상, 최종 결정 | [정산 화면, 결정할 일](#팀장-화면-시연-최종-결정) |
| PM | [@yxxunseo](https://github.com/yxxunseo) | 일정, 이슈, 앱 뼈대, 통합 | [의존성, 권한, 이슈](#pm-일정-이슈-앱-뼈대-통합) |
| 개발1 | [@GongsBB](https://github.com/GongsBB) | 차량 데이터, 정산 | [차량 값, 유가, 정산 계산](#개발1-차량-데이터-정산) |
| 개발2 | [@kimjh2567](https://github.com/kimjh2567) | 서버와 실시간 통신 | [오피넷 서버 호출, 위치 전달](#개발2-서버와-실시간-통신) |
| 개발3 | [@WonDongWoo1](https://github.com/WonDongWoo1) | 지도와 그룹 드라이브 | [현재 위치, 합류점 추천](#개발3-지도와-그룹-드라이브) |

## 항목별 제공 여부

| 항목 | 제공 | API | 단위 | 권한 | 담당 |
|---|---|---|---|---|---|
| GPS | NaviHelper로 제공 | NaviHelper `getCurrentLocationInfo` → `onCurrentLocationInfo` (FusedLocation은 준비 중) | 위도, 경도 | `NAVI_ROUTE` | 개발3 |
| 속도 | SDK에 없음 | AAOS `PERF_VEHICLE_SPEED` | m/s | `android.car.permission.CAR_SPEED` (dangerous) | 개발1 (표시는 개발3) |
| 주행거리 | 제공 | `Odometer.getCurrentDistanceDriven` | km | `CAR_DRIVE` | 개발1 |
| 트립 거리 | 제공 | `getTripCurrentDrivingDistance`, `getTripSinceRefillDrivingDistance` | m | `CAR_DRIVE` | 개발1 |
| 연비 | 제공 | `Fuel.getCurrentFuelEconomy`, `getCumulativeFuelEconomy` | L/100km | `CAR_ENERGY` | 개발1 |
| 연료 잔량(%) | 제공 | `Fuel.getFuelPercent` | % | `CAR_ENERGY` | 개발1 (합류점은 개발3) |
| 연료 잔량(리터) | SDK에 없음 | AAOS `FUEL_LEVEL` | mL | `android.car.permission.CAR_ENERGY` (dangerous) | 개발1 |
| 연료 탱크 용량 | SDK에 없음 | AAOS `INFO_FUEL_CAPACITY` | mL | `android.car.permission.CAR_INFO` (normal) | 개발1 |
| 배터리 | 제공 | `EvBattery.getEvBatteryStateOfCharge`, `getCurrentEvEfficiency` | %, km/kWh | `CAR_ENERGY` | 개발1 (합류점은 개발3) |
| 통행료 실결제액 | 찾지 못함 | `Toll`은 하이패스 음성 안내 볼륨 API뿐 | - | `CAR_TOLL` | 개발1 |
| 통행료 예상액 | 제공 | NaviHelper `RouteStartInfo.tollFare`, `DestinationInfo.tollFare` | 원 | `NAVI_ROUTE` | 개발1 |
| 근처 주유소 유가 | 외부 API | 오피넷 `aroundAll.do` | 원/L | 오피넷 인증키 | 개발1 (서버 호출은 개발2) |

## 공통 사용법

```kotlin
// app/build.gradle.kts — 현재 프로젝트에는 Vehicle만 있다
implementation("ai.pleos.playground:Vehicle:2.3.2")
implementation("ai.pleos.playground:NaviHelper:3.0.5")
// FusedLocation은 아직 "준비 중"이라 신청할 수 없다
```

- **호출 순서:** `Vehicle(context)` → `initialize()` → `getFuel()` 등으로 도메인 객체 획득 → 사용 → `release()`. 비동기 콜백이 끝나기 전에 `release()`를 부르면 안 된다.
- **결과 전달:** 값은 `onComplete` / `onFailed` 콜백으로 오며, `null`일 수 있다.
- **지원 여부:** 차종마다 다르므로 `checkXxxCapabilities`로 먼저 확인한다.
- **사용 가능한 SDK:** Vehicle State와 NaviHelper는 신청 없이 바로 쓸 수 있다. FusedLocation은 "준비 중"(Coming Soon)이라 아직 신청할 수 없다.
- **에뮬레이터 네트워크:** 기본 설정으로 띄우면 외부 도메인을 찾지 못한다. 외부 API를 호출하려면 `-dns-server 8.8.8.8,1.1.1.1` 옵션으로 띄운다.

| API 유형 | 이름 형식 | 기능 |
|---|---|---|
| Capability | `checkXxxCapabilities` | 지원 여부 확인 |
| Get | `getXxx`, `getCurrentXxx` | 현재 값 1회 조회 |
| Callback | `registerXxx`, `unregisterXxx` | 값이 바뀔 때마다 수신 |

## 개발1: 차량 데이터, 정산

담당: [@GongsBB](https://github.com/GongsBB)

### 주행거리

| API | 단위 | 활용 |
|---|---|---|
| `getCurrentDistanceDriven` | km | 출발과 도착 값의 차이로 주행거리 계산 |
| `registerDistanceDriven` | km | 주행 중 거리 표시 |
| `getTripSinceRefillDrivingDistance` | m | 값이 줄어들면 주유로 추정 |

### 연비

`FUEL_LEVEL`을 못 쓰는 차량의 대체 계산과 화면 표시에만 쓴다. 단위가 L/100km라 km/L는 `100 ÷ 값`이다.

| API | 활용 |
|---|---|
| `getCurrentFuelEconomy` | 대체 계산, 화면 표시 |
| `getCumulativeFuelEconomy` | 현재 연비가 `null`일 때 대체 값 |

### 연료·배터리

| API | 단위 | 활용 |
|---|---|---|
| `FUEL_LEVEL` | mL | **주유비 계산의 기준값** |
| `INFO_FUEL_CAPACITY` | mL | `FUEL_LEVEL` 미지원 시 %를 리터로 환산 |
| `INFO_FUEL_TYPE` | 목록 | 오피넷 제품 코드 선택 |
| `EV_BATTERY_LEVEL` | Wh | 전기차 전력 사용량 계산 |
| `getFuelPercent`, `registerFuelPercent` | % | 잔량 표시, 합류점 추천 기준 |
| `getEvBatteryStateOfCharge`, `registerEvBatteryStateOfCharge` | % | 전기차 잔량 표시, 합류점 추천 기준 |
| `getCurrentEvEfficiency` | km/kWh | 전기차 충전비 추정 |

- AAOS 속성은 `CarPropertyManager`로 읽는다. 지원 여부는 차종 이름이 아니라 `CarPropertyManager`로 확인한다.
- `FUEL_LEVEL`은 값이 흔들릴 수 있어 출발과 도착 값은 정차 상태에서 읽는다.

### 속도

Vehicle SDK에는 없고, Pleos FAQ가 `PERF_VEHICLE_SPEED`를 안내한다. km/h는 m/s 값에 3.6을 곱한다. 정산에는 쓰지 않는다. `CAR_SPEED`의 App Market 심사 통과 여부는 확인이 필요하다.

### 통행료

| API | 활용 |
|---|---|
| `onRouteStarted(RouteStartInfo)` | 출발 시 `tollFare` 저장 |
| `getDestinationInfo()`, `onDestinationInfo` | 경로 변경 후 `tollFare` 다시 조회 |
| `onDestinationArrived` | 정산 화면을 띄우는 신호 (요금은 없음) |

`tollFare`는 경로 예상 요금이라 경로 이탈이나 할인이 있으면 실제 결제액과 달라진다.

### 근처 주유소 유가

오피넷은 푸시 없이 요청할 때마다 가격을 조회한다. 유가를 푸시로 주는 공개 API는 찾지 못했고, 정산은 특정 시점의 가격만 있으면 되므로 필요할 때만 조회한다.

```
GET https://www.opinet.co.kr/api/aroundAll.do?out=json&x={KATEC_X}&y={KATEC_Y}&radius=3000&sort=2&prodcd=B027&certkey={인증키}
```

| 요청 항목 | 값 |
|---|---|
| `x`, `y` | 기준 좌표 (**KATEC**) |
| `radius` | 반경, 최대 5000 (m) |
| `prodcd` | `B027` 휘발유, `D047` 경유, `B034` 고급휘발유, `K015` 자동차부탄 |
| `sort` | `1` 가격순, `2` 거리순 |

| 조회 시점 | 쓰임 |
|---|---|
| 그룹 운행 출발 | 주유하지 않은 차량의 적용 유가 |
| 주유 감지 | 주유한 차량의 적용 유가 |
| 합류점 추천 | 추천 주유소의 현재 가격 표시 |

- **호출 한도:** 일반 API는 하루 300회, 기업·기관 제휴 시 1,500회다.
- **응답:** 주유소 코드, 상표, 상호, 판매가격, 거리, 좌표를 준다.
- **한계:** 주유소가 보고한 가격이라 늦거나 틀릴 수 있다. 실제 들른 주유소는 알 수 없고 카드·셀프 할인도 반영되지 않는다.

### 정산 방식

```
사용 연료(L) = 출발 시 연료량 + 주유량 − 도착 시 연료량
주유비 추정 = 사용 연료 × 적용 유가
통행료 = tollFare 예상액 + 운전자가 수정할 수 있는 입력칸
```

- **기준 위치:** 이동 중 주유했으면 주유 지점, 아니면 출발지다. 여러 번 주유했으면 주유량으로 가중 평균한다.
- **주유 감지:** `FUEL_LEVEL`이 최저점보다 2 L(임시값) 이상 오르면 주유로 판단한다. 그룹 운행 시작 전의 주유는 세지 않는다.
- **유종:** `INFO_FUEL_TYPE`으로 제품 코드를 고른다. 고급휘발유처럼 구분이 안 되면 운전자가 고른다.
- **전기차:** 충전 요금 출처를 아직 찾지 못했다.

리터를 지원하지 않는 차량은 아래 순서로 대체한다.

| 순서 | 사용 연료 계산 | 정확도 |
|---|---|---|
| 1 | `FUEL_LEVEL` 차이 | 가장 좋음 |
| 2 | `getFuelPercent` 차이 × `INFO_FUEL_CAPACITY` | 60 L 탱크 기준 약 0.6 L 오차 |
| 3 | 누적 주행거리 × 연비 ÷ 100 | 연비 값의 초기화 시점 미확인 |

### 유가 기준 선택

운전자가 정산 전에 차량별로 고른다. 기본값은 근처 주유소 유가다.

| 기준 | 적용 유가 |
|---|---|
| 근처 주유소 유가 | 기준 위치에서 가장 가까운 주유소의 가격 |
| 근처 최저가 주유소 유가 | 기준 위치 반경 3 km(임시값) 안에서 가장 싼 주유소의 가격 |

`aroundAll.do`를 `sort=2`로 한 번 호출해 첫 번째 주유소를 근처 가격으로, `PRICE`가 가장 낮은 주유소를 최저가로 쓰고 두 가격을 함께 저장한다.

### 정산 예시

차량 2대, 총 8명(운전자 2명, 동승자 6명)이다. 차량 B는 이동 중 30.0 L를 주유했지만, 이번 이동에 쓴 15.0 L만 비용으로 잡는다.

| 값 | 차량 A | 차량 B |
|---|---:|---:|
| 출발 연료량 | 42.0 L | 20.0 L |
| 주유량 | 0.0 L | 30.0 L |
| 도착 연료량 | 33.0 L | 35.0 L |
| 사용 연료 | 9.0 L | 15.0 L |
| 통행료 | 6,700원 | 6,500원 |

| 계산 | 근처 주유소 유가 (1,700원/L) | 근처 최저가 주유소 유가 (1,650원/L) |
|---|---:|---:|
| 차량 A 비용 (주유비 + 통행료) | 22,000원 | 21,550원 |
| 차량 B 비용 (주유비 + 통행료) | 32,000원 | 31,250원 |
| 전체 비용 | 54,000원 | 52,800원 |
| 1인 부담액 (8명) | 6,750원 | 6,600원 |

근처 주유소 유가 기준으로 운전자 A는 15,250원, 운전자 B는 25,250원을 받고, 동승자 6명은 각자 6,750원을 보낸다. 원 단위로 나누어떨어지지 않을 때의 처리는 아직 정하지 않았다.

### 개발1이 확인할 것

- [ ] 통행료 결제 내역을 받을 다른 경로가 있는지 공모전 주최 측이나 Pleos 쪽에 문의한다.
- [ ] 기술 지원팀에 `FUEL_LEVEL`, `INFO_FUEL_CAPACITY` 사용 검토를 요청하고, 공모전 제출용 APK에도 권한 승인 절차가 적용되는지 묻는다.
- [ ] 에뮬레이터에서 연료를 읽고 조작할 방법을 찾는다. Virtual Console에는 Odometer 카드는 있지만, Energy 섹션에는 EV 항목만 있고 연료 잔량·트립 거리·연비 카드는 없다. 이대로면 `FUEL_LEVEL` 기반 주유 감지를 시연하기 어렵다.
- [ ] 실제 `FUEL_LEVEL` 값을 보고 주유 감지 기준 2 L를 조정한다.
- [ ] 오피넷 인증키를 발급받고, 응답 형식과 응답 개수 제한을 실제 키로 확인한다.
- [ ] 전기차 충전 요금의 출처를 찾는다.
- [ ] 트립 거리와 현재 연비가 시동을 껐다 켜면 초기화되는지 확인한다.
- [ ] 클라우드 방식의 별도 Vehicle Data API 내용을 확인한다.

## 개발2: 서버와 실시간 통신

담당: [@kimjh2567](https://github.com/kimjh2567)

오피넷 요청 항목은 개발1의 [근처 주유소 유가](#근처-주유소-유가)를 본다.

- **오피넷 호출:** 인증키를 APK에 넣지 않고 서버에서 호출한다. 앱은 서버에 좌표만 보낸다.
- **좌표 변환:** 위도·경도(WGS84)를 KATEC으로 변환해서 보낸다.
- **결과 저장:** 같은 지역의 조회 결과는 서버에 잠시 저장해 여러 그룹이 함께 쓴다.
- **위치 공유:** 개발3이 받은 위치를 일행 차량에 전달한다. 전송 형식은 개발3과 맞춘다.
- **유가 주기 조회:** 시연에서 유가를 계속 갱신해 보여 줄 때만 추가한다. 하루 300회 한도에서는 지역 1곳이 약 5분 간격, 3곳이 약 15분 간격이다.

확인할 것:

- [ ] 오피넷 가격 반영 주기와 하루 300회 한도가 서비스 규모에 맞는지 확인한다.

## 개발3: 지도와 그룹 드라이브

담당: [@WonDongWoo1](https://github.com/WonDongWoo1)

### GPS

FusedLocation은 "준비 중"이라 아직 신청할 수 없다. 당장은 NaviHelper의 현재 위치를 쓴다.

| API | 활용 |
|---|---|
| `getCurrentLocationInfo()` | 현재 위치 요청 |
| `onCurrentLocationInfo` | 요청 결과 수신. 그룹 화면 표시와 일행 위치 공유에 사용 |

- 권한은 `NAVI_ROUTE`다.
- FusedLocation이 사용 가능해지면 `registerFusedLocationCallback`으로 위치 변경을 받는 방식으로 바꾼다.

확인할 것:

- [ ] NaviHelper에 위치 변경을 계속 받는 방법이 있는지, 없으면 몇 초 간격으로 요청할지 정한다.

### 합류점 추천

- **잔량 기준:** 개발1의 [연료·배터리](#연료배터리) 표에 있는 `registerFuelPercent`, `registerEvBatteryStateOfCharge`를 쓴다.
- **주유소 가격:** 합류점 후보의 가격은 서버를 거쳐 오피넷에서 받는다.
- **속도 표시:** 차량별 속도는 개발1의 [속도](#속도) 값을 쓴다.

## 팀장: 화면, 시연, 최종 결정

담당: [@apkool12](https://github.com/apkool12)

### 정산 화면

- 금액이 실제 결제액이 아니라 추정치임을 표시한다.
- 통행료와 유가를 운전자가 수정할 수 있는 입력칸을 둔다.
- 두 유가 기준의 가격을 함께 보여 주고 고르는 선택지를 둔다.
- `onDestinationArrived`를 받으면 정산 화면을 띄운다.

### 시연

- Virtual Console에 연료 잔량 카드가 없어 주유 감지를 그대로 시연하기 어렵다. 개발1의 확인 결과를 보고 시나리오를 정한다. 주행거리와 EV 배터리 SOC는 Virtual Console로 바꿀 수 있다.
- 경로 주변 유가를 계속 갱신해 보여 줄지 정한다.

### 최종 결정

- **유가 기준:** 차량별로 고를지 그룹 전체에 하나로 적용할지, 기본값과 최저가 반경 3 km를 그대로 쓸지 정한다.
- **공모전 심사:** 기술 활용성은 Pleos SDK·API를 핵심 기능에 활용했는지 평가한다. 주행거리, 위치, 통행료, 연료 %는 Pleos SDK로 유지하고 리터만 Android 속성으로 보완한다.

## PM: 일정, 이슈, 앱 뼈대, 통합

담당: [@yxxunseo](https://github.com/yxxunseo)

- **의존성:** `app/build.gradle.kts`에 NaviHelper를 추가한다.
- **권한:** 매니페스트에 `NAVI_ROUTE`, `android.car.permission.CAR_ENERGY`, `android.car.permission.CAR_INFO`를 추가한다. dangerous 등급은 앱 실행 중 권한 요청도 필요하다.
- **Car API:** `android` 블록에 `useLibrary("android.car")`를 추가한다.
- **차종별 차이:** NaviHelper는 내비게이션 장착 차량에서만 완전히 호환된다.
- **이슈와 통합:** 각 역할의 확인 항목을 이슈로 등록하고, 차량 값을 넘기는 인터페이스를 개발자들과 맞춘다.
