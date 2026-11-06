# Pleos Vehicle API 제공 항목 조사

정산 기능에 필요한 차량 데이터(GPS, 속도, 주행거리, 연비, 연료/배터리, 통행료)를 Pleos Connect에서 얻을 수 있는지 조사하고, API별 기능과 활용 방법을 정리한 문서다.

- 조사일: 2026-11-06
- 기준: Vehicle SDK 2.3.2, NaviHelper SDK 3.0.5, FusedLocation SDK 1.1.2 공식 문서
- 검증 범위: 문서 조사만 했다. 예시 코드는 공식 문서의 시그니처로 작성했지만 빌드나 실제 호출은 해 보지 않았다.
- 예시 값: 문서의 모든 예시 값은 설명용으로 만든 가상의 값이며, 실제 측정값이 아니다.

## 결론

**통행료 실결제액과 주유 금액은 Pleos SDK 문서에서 찾지 못했다.** README의 "하이패스 결제 기록"과 "실제 주유 기록 우선" 설계는 현재 문서 기준으로 구현할 수 없다.

주유비는 **사용한 연료 리터 × 근처 주유소 유가**로 추정한다. 리터 값은 Android 표준 속성 `FUEL_LEVEL`에서 읽고, 유가는 오피넷 반경 내 주유소 API에서 가져온다. `FUEL_LEVEL`은 Pleos SDK에 없는 속성이라 차량 지원 여부 확인과 기술 지원팀 검토가 필요하다.

## 항목별 제공 여부

| 항목 | 제공 | API | 단위·값 | 권한 |
|---|---|---|---|---|
| GPS | 제공 | FusedLocation SDK `getLastKnownLocation`, `registerFusedLocationCallback` | 위도, 경도, 고도, 방향, 시각 | `pleos.car.permission.FUSED_LOCATION` (dangerous) |
| 속도 | Vehicle SDK에는 없음 | AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED` | m/s, 공식 FAQ가 안내하는 방법 | `android.car.permission.CAR_SPEED` (dangerous) |
| 주행거리 | 제공 | `Odometer.getCurrentDistanceDriven` | 누적 km | `CAR_DRIVE` |
| 트립 거리 | 제공 | `getTripCurrentDrivingDistance`, `getTripSinceRefillDrivingDistance` | m | `CAR_DRIVE` |
| 연비 | 제공 | `Fuel.getCurrentFuelEconomy`, `getCumulativeFuelEconomy`, `getSinceChargeFuelEconomy` | L/100km | `CAR_ENERGY` |
| 연료 잔량(%) | 제공 | `Fuel.getFuelPercent` | % (정수) | `CAR_ENERGY` |
| 연료 잔량(리터) | Vehicle SDK에는 없음 | AAOS `FUEL_LEVEL` | mL | `android.car.permission.CAR_ENERGY` (dangerous) |
| 연료 탱크 용량 | Vehicle SDK에는 없음 | AAOS `INFO_FUEL_CAPACITY` | mL | `android.car.permission.CAR_INFO` (normal) |
| 배터리 | 제공 | `EvBattery.getEvBatteryStateOfCharge`, `getCurrentEvEfficiency` | %, km/kWh | `CAR_ENERGY` |
| 통행료 실결제액 | 찾지 못함 | `Toll` 도메인은 하이패스 음성 안내 볼륨 API뿐 | - | `CAR_TOLL` |
| 통행료 예상액 | 제공 | NaviHelper `RouteStartInfo.tollFare`, `DestinationInfo.tollFare` | 원 (`DestinationInfo` 기준) | `NAVI_ROUTE` |
| 근처 주유소 유가 | 외부 API | 오피넷 `aroundAll.do` | 원/L, 반경 최대 5km | 오피넷 인증키 |

## 공통 사용법

### 의존성

```kotlin
// app/build.gradle.kts — 현재 프로젝트에는 Vehicle만 있다
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.2")
}
```

### Vehicle SDK 호출 순서

`Vehicle`을 만들고 `initialize()`로 연결한 뒤, `getFuel()` 같은 메서드로 도메인 객체를 얻어 사용한다. 끝나면 `release()`로 해제한다.

```kotlin
val vehicle = Vehicle(context)
vehicle.initialize()

val fuel = vehicle.getFuel()
val odometer = vehicle.getOdometer()

// 비동기 콜백이 끝나기 전에 release()를 부르면 안 된다
vehicle.release()
```

| API 유형 | 이름 형식 | 기능 | 결과 전달 |
|---|---|---|---|
| Capability | `checkXxxCapabilities` | 지원 여부 확인 | `onComplete` / `onFailed` |
| Get | `getXxx`, `getCurrentXxx` | 현재 값 1회 조회 | `onComplete` / `onFailed` |
| Callback | `registerXxx`, `unregisterXxx` | 값이 바뀔 때마다 수신 | 리스너의 `onXxxUpdated` |

결과는 바로 반환되지 않고 콜백으로 온다. 값이 없으면 `null`이 올 수 있으니 항상 처리한다.

### 지원 여부 확인

차종마다 지원 API가 다르므로 값을 읽기 전에 확인한다.

```kotlin
fuel.checkFuelPercentCapabilities(
    onComplete = { config ->
        when (config.accessType) {
            AccessType.READ, AccessType.READ_WRITE -> { /* getFuelPercent 호출 */ }
            else -> { /* 미지원: 연료 표시를 숨기거나 수동 입력으로 전환 */ }
        }
    },
    onFailed = { e -> /* 조회 실패 처리 */ },
)
```

## API별 기능과 활용 방법

### 1. GPS — FusedLocation SDK

| API | 기능 | 나누길 활용 |
|---|---|---|
| `isSupported()` | 위치 서비스가 차량에 있는지 확인 | 위치 공유 기능을 켤지 판단 |
| `getLastKnownLocation(callback)` | 가장 최근 위치 1회 조회 (새로 측정하지는 않음) | 그룹 화면 첫 표시 |
| `registerFusedLocationCallback(listener)` | 위치가 바뀔 때마다 수신 | 일행 위치 실시간 공유 |
| `unregisterFusedLocationCallback(listener)` | 위치 수신 중지 | 운행 종료 시 위치 공유 중단 |
| `showLocationAccessBlockedPopup()` | 위치 접근 차단 안내 팝업 표시 | 개인정보 설정으로 막혔을 때 안내 |

```kotlin
val fusedLocation = FusedLocation(context)
fusedLocation.initialize()

val listener = object : FusedLocationListener {
    override fun onChanged(location: LocationData?) {
        location ?: return
        // 예시 값: latitude=37.8313, longitude=127.5095, heading=42.0
        sendToGroup(location.latitude, location.longitude, location.timestampMillis)
    }

    override fun onError(exception: Exception) {
        // 위치 수신 오류 처리
    }
}

fusedLocation.registerFusedLocationCallback(listener)

// 운행 종료 시
fusedLocation.unregisterFusedLocationCallback(listener)
fusedLocation.release()
```

- `FUSED_LOCATION`은 dangerous 등급이라 매니페스트 선언과 함께 앱 실행 중 권한 요청이 필요하다.
- `LocationData`에는 속도 필드가 없다.
- 정산 계산에는 쓰지 않는다. 일행 위치 공유 담당과 인터페이스를 맞출 때 참고한다.

### 2. 속도 — AAOS CarPropertyManager

Vehicle SDK에는 차량 속도 API가 없다. Pleos FAQ는 계기판과 같은 값을 쓰려면 `PERF_VEHICLE_SPEED`를 읽으라고 안내한다.

| API | 기능 | 나누길 활용 |
|---|---|---|
| `CarPropertyManager.registerCallback` | 속도 변경 수신 | 그룹 화면의 차량별 속도 표시 |
| `VehiclePropertyIds.PERF_VEHICLE_SPEED` | 차량 신호 기반 속도 (m/s) | km/h로 바꾸려면 3.6을 곱함 |

```kotlin
// 아래 코드는 Pleos 문서가 아닌 Android 표준 API 기준이다
val car = Car.createCar(context)
val manager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

val callback = object : CarPropertyManager.CarPropertyEventCallback {
    override fun onChangeEvent(value: CarPropertyValue<*>) {
        val kmh = (value.value as Float) * 3.6f // 예시 값: 27.8 m/s → 약 100 km/h
    }

    override fun onErrorEvent(propertyId: Int, areaId: Int) {
        // 속도 수신 오류 처리
    }
}

manager.registerCallback(
    callback,
    VehiclePropertyIds.PERF_VEHICLE_SPEED,
    CarPropertyManager.SENSOR_RATE_NORMAL,
)
```

- 정산 계산에는 필요 없다.
- `CAR_SPEED`는 dangerous 권한이라 앱 실행 중 권한 요청이 필요하다. Pleos App Market 심사 통과 여부는 확인이 필요하다.

### 3. 주행거리 — Vehicle SDK Odometer

| API | 기능 | 단위 | 나누길 활용 |
|---|---|---|---|
| `getCurrentDistanceDriven` | 차량 누적 주행거리 조회 | km | 출발과 도착 시 값의 차이로 주행거리 계산. 정산 화면 표시와 연비 대체 계산에 사용 |
| `registerDistanceDriven` | 누적 주행거리 변경 수신 | km | 주행 중 거리 실시간 표시 |
| `getTripCurrentDrivingDistance` | 현재 주행 세션 거리 조회 | m | 참고용 (초기화 시점 미확인) |
| `getTripSinceRefillDrivingDistance` | 마지막 주유 이후 거리 조회 | m | 값이 줄어들면 주유로 추정 |

```kotlin
// 출발할 때 호출하고, 도착할 때 다시 호출해 차이를 구한다
odometer.getCurrentDistanceDriven(
    onComplete = { km: Float? ->
        // 예시 값: 출발 12340.5, 도착 12460.5 → 120.0 km
        if (km != null) saveOdometer(km)
    },
    onFailed = { e -> /* 조회 실패 처리 */ },
)
```

### 4. 연비 — Vehicle SDK Fuel

주유비의 기본 계산은 리터 방식으로 바꿨다. 연비는 `FUEL_LEVEL`을 쓸 수 없는 차량의 대체 계산과 화면 표시에 쓴다.

| API | 기능 | 나누길 활용 |
|---|---|---|
| `getCurrentFuelEconomy` | 현재 주행 세션 연비 | 리터를 못 읽는 차량의 대체 계산, 화면 표시 |
| `getCumulativeFuelEconomy` | 차량 운행 기간 전체 연비 | 현재 연비가 `null`일 때 대체 값 |
| `getSinceChargeFuelEconomy` | 마지막 주유 이후 연비 | 비교 참고용 |
| `getInstantaneousFuelEconomy` | 순간 연비 | 값이 크게 흔들릴 수 있어 정산에는 부적합 |

```kotlin
fuel.getCurrentFuelEconomy(
    onComplete = { lPer100km: Float? ->
        // 예시 값: 7.5 L/100km = 약 13.3 km/L
        val kmPerLiter = lPer100km?.let { 100f / it }
    },
    onFailed = { e -> /* 조회 실패 처리 */ },
)
```

단위가 L/100km라서 값이 작을수록 연비가 좋다. 화면에 km/L로 표시할 때만 `100 ÷ 값`으로 바꾼다.

### 5. 연료·배터리 — Vehicle SDK Fuel, EvBattery

| API | 기능 | 단위 | 나누길 활용 |
|---|---|---|---|
| `getFuelPercent` | 연료 잔량 조회 | % | 현재 잔량 표시 |
| `registerFuelPercent` | 연료 잔량 변경 수신 | % | 합류점 추천 기준, 잔량 상승으로 주유 사실 감지 |
| `getEvBatteryStateOfCharge` | EV 배터리 잔량 조회 | % | 전기차 잔량 표시 |
| `registerEvBatteryStateOfCharge` | EV 배터리 잔량 변경 수신 | % | 전기차 합류점 추천 기준 |
| `getCurrentEvEfficiency` | 현재 주행 세션 전비 | km/kWh | 전기차 충전비 추정 |

```kotlin
val fuelListener = object : FuelPercentListener {
    override fun onFuelPercentUpdated(value: Int?) {
        // 예시 값: 18 → 기준 20% 미만이면 합류점 추천 시작
        if (value != null && value < LOW_FUEL_PERCENT) requestMeetingPoint()
    }

    override fun onFailed(e: Exception) {
        // 수신 실패 처리
    }
}

fuel.registerFuelPercent(fuelListener)
```

`onFailed`는 공식 Callback 가이드의 리스너 예시를 따랐다. `FuelPercentListener`의 부모인 `VehicleListener` 정의는 빌드할 때 확인이 필요하다.

#### 리터 값 읽기 — AAOS CarPropertyManager

Vehicle SDK는 연료를 %로만 주므로 리터 값은 Android 표준 속성에서 읽는다.

| 속성 | 기능 | 단위 | 나누길 활용 |
|---|---|---|---|
| `FUEL_LEVEL` | 현재 연료량 | mL | **주유비 계산의 기준값** |
| `INFO_FUEL_CAPACITY` | 연료 탱크 용량 | mL | `FUEL_LEVEL` 미지원 시 % 값을 리터로 환산 |
| `INFO_FUEL_TYPE` | 차량이 쓰는 연료 종류 | 목록 | 오피넷 제품 코드 선택 |
| `EV_BATTERY_LEVEL` | EV 배터리 잔량 | Wh | 전기차 전력 사용량 계산 |

```kotlin
// Pleos 문서가 아닌 Android 표준 API 기준이다. 전역 속성이라 area는 0이다.
val config = manager.getCarPropertyConfig(VehiclePropertyIds.FUEL_LEVEL)
if (config != null) {
    val fuelMl = manager.getFloatProperty(VehiclePropertyIds.FUEL_LEVEL, 0) // 예시 값: 42000.0
}
```

- `FUEL_LEVEL`은 dangerous 권한이라 앱 실행 중 권한 요청이 필요하다. 매니페스트의 `pleos.car.permission.CAR_ENERGY`와는 다른 권한이다.
- Pleos 문서는 SDK에 없는 특수 속성을 쓰려면 구현 전에 기술 지원팀 검토를 받으라고 안내한다.
- 차종마다 지원 여부가 다르므로 차종 이름으로 나누지 말고 `CarPropertyManager`로 확인한다.
- Android 문서는 연료량의 정밀도를 밝히지 않는다. 경사나 연료 출렁임으로 값이 흔들릴 수 있어, 출발과 도착 값은 정차 상태에서 읽는 편이 안전하다.

### 6. 통행료 — NaviHelper SDK

| API | 기능 | 나누길 활용 |
|---|---|---|
| `onRouteStarted(RouteStartInfo)` | 경로 시작 시 거리, 예상 시간, `tollFare` 전달 | 출발 시 통행료 예상액 저장 |
| `getDestinationInfo()` | 목적지 정보 요청 | 경로 변경 후 예상액 다시 조회 |
| `onDestinationInfo(DestinationInfo)` | `getDestinationInfo()`의 결과 전달 | 목적지까지의 `tollFare`(원) 저장 |
| `onDestinationArrived(DestinationArrivedInfo)` | 도착 시 거리, 시간, 이름, 주소 전달 | 정산 화면을 띄우는 신호 (요금은 없음) |

```kotlin
val naviHelper = NaviHelper(applicationContext)

val naviListener = object : NaviHelperEventListener {
    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
        saveTollEstimate(routeStartInfo.tollFare) // 예시 값: 6700
    }

    override fun onDestinationInfo(destinationInfo: DestinationInfo) {
        saveTollEstimate(destinationInfo.tollFare)
    }

    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
        showSettlementScreen()
    }

    override fun onError(errorCode: NaviErrorCode) {
        // 내비 오류 처리
    }
}

naviHelper.initialize(
    onConnected = {
        naviHelper.addListener(naviListener)
        naviHelper.getDestinationInfo()
    },
    onFailed = { throwable -> /* 연결 실패 처리 */ },
)

// 종료 시
naviHelper.removeListener(naviListener)
naviHelper.release()
```

- `tollFare`는 경로에 필요한 요금이며 실제 결제액이 아니다.
- 전달되는 이벤트는 내비게이션 앱의 구현에 따라 달라질 수 있다고 문서에 적혀 있다.
- `Toll` 도메인의 API는 하이패스 음성 안내 볼륨 조회와 설정뿐이라 정산에 쓸 수 없다.

### 7. 근처 주유소 유가 — 오피넷 API

한국석유공사가 운영하는 외부 API다. 앱이 푸시로 받는 방식이 아니라, 필요할 때 요청해서 그 시점의 가격을 조회하는 방식이다.

| API | 기능 | 나누길 활용 |
|---|---|---|
| 반경 내 주유소 `aroundAll.do` | 좌표 중심 반경 5km 이내 주유소의 가격과 거리 조회 | 출발지나 주유 지점의 유가 조회 |
| 주유소 상세정보 (ID) | 주유소 코드로 상세 정보 조회 | 합류점 후보 주유소 정보 표시 (요청 항목 미확인) |

| 요청 항목 | 값 |
|---|---|
| `certkey` | 오피넷 인증키 |
| `out` | `xml` 또는 `json` |
| `x`, `y` | 기준 좌표 (**KATEC**) |
| `radius` | 반경, 최대 5000 (m) |
| `prodcd` | `B027` 휘발유, `D047` 경유, `B034` 고급휘발유, `K015` 자동차부탄 |
| `sort` | `1` 가격순, `2` 거리순 |

```
GET https://www.opinet.co.kr/api/aroundAll.do?out=json&x={KATEC_X}&y={KATEC_Y}&radius=3000&sort=2&prodcd=B027&certkey={인증키}
```

응답에는 주유소 코드(`UNI_ID`), 상표(`POLL_DIV_CD`), 상호(`OS_NM`), 판매가격(`PRICE`), 거리(`DISTANCE`), 좌표가 들어 있다.

- **호출 한도:** 일반 API는 하루 300회다. 기업·기관 제휴를 하면 1,500회로 늘릴 수 있다. 위치가 바뀔 때마다 호출하지 말고 출발, 주유 감지, 합류점 추천 때만 호출한다.
- **호출 위치:** 인증키를 APK에 넣지 않고 나누길 서버에서 호출한다. 앱은 서버에 좌표만 보낸다.
- **좌표 변환:** FusedLocation의 위도·경도(WGS84)는 KATEC으로 변환해서 보내야 한다.
- **가격 신뢰도:** 주유소가 보고한 가격을 게시하는 구조다. 공식 안내와 공공데이터포털은 실시간으로 표기하지만, 보고가 늦거나 틀린 주유소가 있을 수 있어 운전자 수정 입력을 남겨 둔다.
- **주유소 특정:** 주유 감지 시 가장 가까운 주유소로 추정할 뿐, 실제로 들른 주유소를 알 수는 없다. 카드 할인이나 셀프 할인도 반영되지 않는다.

#### 유가 조회 방식 결정

**실시간 푸시 없이, 필요할 때 조회하는 방식으로 구현한다.** 유가를 푸시나 스트리밍으로 주는 공개 SDK와 API는 찾지 못했다. 정산은 특정 시점의 가격 하나만 있으면 되고, 푸시를 만들어도 원본이 주유소 보고 값이라 정확도가 오르지 않는다.

| 조회 시점 | 쓰임 |
|---|---|
| 그룹 운행 출발 | 주유하지 않은 차량의 적용 유가 |
| 주유 감지 | 주유한 차량의 적용 유가 |
| 합류점 추천 | 추천 주유소의 현재 가격 표시 |

같은 지역의 조회 결과는 서버에 잠시 저장해 여러 그룹이 함께 쓴다.

시연에서 경로 주변 유가를 계속 갱신해 보여 줘야 할 때만 서버 주기 조회를 추가한다. 서버가 주기적으로 오피넷을 조회하고, 바뀐 가격을 일행 위치 공유용 실시간 연결로 차량에 보내는 방식이다. 하루 300회 한도에서는 조회 지역 1곳이 약 5분 간격, 3곳이 약 15분 간격이다.

## 예시 자료: API 값으로 정산 계산하기

차량 2대, 총 8명(운전자 2명, 동승자 6명)이 함께 이동한 경우다. 모든 값은 가상의 값이다. 차량 A는 주유하지 않았고, 차량 B는 이동 중에 30.0 L를 주유했다.

### 1단계. API로 모은 값

| 값 | 출처 API | 차량 A | 차량 B |
|---|---|---:|---:|
| 출발 시 연료량 | `FUEL_LEVEL` | 42.0 L | 20.0 L |
| 주유량 | `FUEL_LEVEL` 상승분 | 0.0 L | 30.0 L |
| 도착 시 연료량 | `FUEL_LEVEL` | 33.0 L | 35.0 L |
| 적용 유가 | 오피넷 `aroundAll.do` | 1,700원/L (출발지 근처) | 1,700원/L (주유 지점 근처) |
| 통행료 예상액 | `tollFare` | 6,700원 | 6,500원 |

### 2단계. 차량별 비용

| 계산 | 차량 A | 차량 B |
|---|---:|---:|
| 사용 연료 (`출발 연료량 + 주유량 − 도착 연료량`) | 9.0 L | 15.0 L |
| 주유비 추정 (`사용 연료 × 적용 유가`) | 15,300원 | 25,500원 |
| 통행료 | 6,700원 | 6,500원 |
| **차량 비용 합계** | **22,000원** | **32,000원** |

주유한 30.0 L 전체가 아니라 이번 이동에 쓴 15.0 L만 비용으로 잡는다. 남은 연료는 운전자가 나중에 쓰기 때문이다.

### 3단계. 1인 부담액과 정산

전체 비용은 54,000원이고, 8명으로 나누면 1인 부담액은 6,750원이다.

| 대상 | 낸 돈 | 1인 부담액 | 정산 |
|---|---:|---:|---|
| 운전자 A | 22,000원 | 6,750원 | 15,250원 받음 |
| 운전자 B | 32,000원 | 6,750원 | 25,250원 받음 |
| 동승자 6명 | 0원 | 6,750원 | 각자 6,750원 보냄 (총 40,500원) |
| **합계** | **54,000원** | | 보낸 돈 40,500원 = 받은 돈 40,500원 |

### 계산 코드 예시

`FUEL_LEVEL` 값이 들어올 때마다 `onFuelLevel`을 부른다. 최저점보다 기준 이상 오르면 주유로 판단하므로, 작은 흔들림은 주유로 세지 않는다.

```kotlin
// 2L는 임시값이다. 실제 센서 값으로 조정이 필요하다.
const val REFILL_THRESHOLD_ML = 2000f

class FuelTracker(private val startMl: Float) {
    private var lastMl = startMl
    private var lowMl = startMl
    var refilledMl = 0f
        private set

    fun onFuelLevel(ml: Float) {
        lastMl = ml
        lowMl = minOf(lowMl, ml)
        if (ml - lowMl >= REFILL_THRESHOLD_ML) {
            refilledMl += ml - lowMl
            lowMl = ml
        }
    }

    fun usedLiters(): Float = (startMl + refilledMl - lastMl) / 1000f
}

fun carCostWon(usedLiters: Float, fuelPriceWon: Int, tollWon: Int): Float =
    usedLiters * fuelPriceWon + tollWon

val carA = carCostWon(9.0f, 1700, 6700) // 22000.0
val carB = carCostWon(15.0f, 1700, 6500) // 32000.0
val perPerson = (carA + carB) / 8 // 6750.0
```

같은 로직을 Python으로 옮겨 세 경우를 검증했다. 값이 흔들리기만 한 경우, 주유 후 값이 한 번에 뛴 경우, 주유 중 값이 조금씩 오른 경우 모두 사용량이 맞았다. Kotlin 코드 자체를 실행한 것은 아니다.

원 단위로 나누어떨어지지 않을 때의 처리 방식은 아직 정하지 않아 이 예시에 넣지 않았다.

## 정산 설계에 미치는 영향

1. **통행료:** `tollFare`는 경로에 필요한 요금이라 실제 결제액이 아니다. 경로 이탈이나 할인이 있으면 달라질 수 있고, 도착 이벤트(`DestinationArrivedInfo`)에는 거리와 시간만 있고 요금은 없다.
2. **주유비:** 실제 결제 금액 API는 찾지 못했다. `FUEL_LEVEL`로 사용 리터와 주유량은 구할 수 있지만, 금액은 오피넷 유가를 곱한 추정치다.
3. **유가:** SDK에 유가 정보가 없어 오피넷을 쓴다. 인증키 보호와 호출 한도 때문에 나누길 서버를 거쳐야 하므로, 미정인 백엔드 결정이 먼저 필요하다.
4. **전기차:** 충전 요금 API를 찾지 못했다. 오피넷의 주유소 API도 전기차 충전 요금을 주지 않으므로 요금 출처를 따로 찾아야 한다.

## 제안하는 정산 방식

```
사용 연료(L) = 출발 시 연료량 + 주유량 − 도착 시 연료량
주유비 추정 = 사용 연료 × 적용 유가
통행료 = tollFare 예상액 + 운전자가 수정할 수 있는 입력칸
```

- **적용 유가:** 이동 중 주유했으면 주유 지점에서 가장 가까운 주유소의 가격을 쓰고, 주유하지 않았으면 출발지에서 가장 가까운 주유소의 가격을 쓴다. 여러 번 주유했으면 주유량으로 가중 평균한다.
- **유종:** `INFO_FUEL_TYPE`으로 오피넷 제품 코드를 고른다. 고급휘발유처럼 차량 정보로 구분할 수 없는 경우는 운전자가 고르게 한다.
- **차량이 리터를 지원하지 않을 때:** 아래 순서로 대체한다.

| 순서 | 사용 연료 계산 | 정확도 |
|---|---|---|
| 1 | `FUEL_LEVEL` 차이 | 가장 좋음 |
| 2 | `getFuelPercent` 차이 × `INFO_FUEL_CAPACITY` | 1% 단위라 60 L 탱크 기준 약 0.6 L(1,700원/L면 약 1,000원) 오차 |
| 3 | 주행거리 × 연비 ÷ 100 | 주유 여부와 무관하게 계산되지만 연비 값의 초기화 시점이 미확인 |

- **주행거리:** 트립 값은 "현재 주행 세션"의 정의가 문서에 없어, 휴게소에서 시동을 끄면 초기화될 수 있다. 그래서 대체 계산에는 누적 주행거리의 차이를 쓴다.
- **시동 꺼짐:** 앱이 꺼진 동안 주유하면 다음 실행 때 값이 한 번에 오른다. 위 로직은 이 경우도 주유로 세지만, 그룹 운행 시작 전에 넣은 주유는 세지 않는다.
- **README 수정:** "금액은 추정치이며 운전자가 수정할 수 있음"으로 고쳐야 한다. 수정 입력은 "운전자가 따로 할 일을 만들지 않는다"는 원칙과 부딪히므로 팀 합의가 필요하다.

## 구현 전에 챙길 것

- **의존성:** `app/build.gradle.kts`에는 Vehicle SDK만 있어서, NaviHelper와 FusedLocation을 쓰려면 추가해야 한다.
- **권한:** 매니페스트에 `FUSED_LOCATION`, `NAVI_ROUTE`, `android.car.permission.CAR_ENERGY`, `android.car.permission.CAR_INFO`가 없다. `FUSED_LOCATION`과 `android.car.permission.CAR_ENERGY`는 dangerous 등급이라 앱 실행 중 권한 요청도 필요하다.
- **Car API:** `CarPropertyManager`를 쓰려면 `app/build.gradle.kts`의 `android` 블록에 `useLibrary("android.car")`가 필요하다.
- **오피넷:** 인증키를 발급받고, 나누길 서버에 WGS84→KATEC 변환과 API 호출을 구현한다.
- **공모전 심사:** 기술 활용성은 Pleos SDK·API를 핵심 기능에 활용했는지 평가한다. 주행거리, 위치, 통행료, 연료 %는 Pleos SDK로 유지하고 리터만 Android 속성으로 보완한다.
- **차종별 차이:** Vehicle SDK와 FusedLocation 지원은 차종마다 다르다. Vehicle SDK는 `checkXXXCapabilities`로 먼저 확인하고, 콜백 값이 `null`인 경우도 처리해야 한다. NaviHelper는 내비게이션 장착 차량에서만 완전히 호환된다.

## 아직 확인하지 못한 것

- [ ] 통행료 결제 내역을 받을 다른 경로가 있는지 공모전 주최 측이나 Pleos 쪽에 문의한다. 문서에서 찾지 못했다는 것이지, 없다고 확정된 것은 아니다.
- [ ] 에뮬레이터에서 연료와 주행거리를 조작할 수 있는지 확인한다. 가상 콘솔 안내에서는 속도 변경만 확인했고, 시연 준비에 직접 영향을 준다.
- [ ] Pleos 기술 지원팀에 `FUEL_LEVEL`, `INFO_FUEL_CAPACITY` 사용 검토를 요청한다. 공모전 제출용 APK에도 권한 승인 절차가 적용되는지 함께 묻는다.
- [ ] 에뮬레이터에서 `FUEL_LEVEL`을 읽고 값을 바꿀 수 있는지 확인한다.
- [ ] 실제 `FUEL_LEVEL` 값의 흔들림을 보고 주유 감지 기준 2 L를 조정한다.
- [ ] 오피넷 가격 반영 주기와 하루 300회 한도가 서비스 규모에 맞는지 확인한다.
- [ ] 전기차 충전 요금의 출처를 찾는다.
- [ ] 문서의 예시 코드를 빌드하고 에뮬레이터에서 호출해 본다.
- [ ] 트립 거리와 현재 연비가 시동을 껐다 켜면 초기화되는지 확인한다.
- [ ] 클라우드 방식의 별도 Vehicle Data API 내용을 확인한다. 이번 조사에서는 보지 않았다.

## 출처

- [Vehicle SDK 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [Vehicle SDK 도메인 객체 사용법](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object)
- [Vehicle SDK Get](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/get-property)
- [Vehicle SDK Callback](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/callback)
- [Vehicle SDK Capability](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability)
- [Vehicle SDK 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [Fuel: getCurrentFuelEconomy](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/get-current-fuel-economy)
- [Fuel: registerFuelPercent](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/register-fuel-percent)
- [Odometer: getCurrentDistanceDriven](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-current-distance-driven)
- [Toll: getCurrentEtcsVoiceGuidanceVolume](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/toll/get-current-etcs-voice-guidance-volume)
- [NaviHelper 초기화](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release)
- [NaviHelper 이벤트 리스너](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)
- [NaviHelper: DestinationInfo](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/entry)
- [NaviHelper: RouteStartInfo](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-start-info/entry)
- [NaviHelper 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [FusedLocation 위치 조회](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)
- [FusedLocation 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)
- [API 호환성](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)
- [차량 앱 FAQ(속도)](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq)
- [가상 콘솔 안내](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [Android Car Hardware API](https://developer.android.com/training/cars/apps/library/car-hardware-api)
- [Android VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- [오피넷 유가정보 API 안내](https://www.opinet.co.kr/user/custapi/custApiInfo.do)
- [오피넷 반경 내 주유소 API](https://www.opinet.co.kr/user/custapi/openApiInfoDtl.do?apiId=3)
- [공공데이터포털 반경 내 주유소 API](https://www.data.go.kr/data/15150464/openapi.do)
- [한국도로공사 주유소별 가격, 업체 현황 API](https://www.data.go.kr/data/15076636/openapi.do)
- [공모전 심사·시상](https://appdevchallenge.auto-hmg.io/guide/judging-awards)
