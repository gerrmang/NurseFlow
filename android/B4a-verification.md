# B4a Android 병원 검색·병동 생성 검증 기록

## 변경 목적과 내용

로그인한 사용자가 Android에서 공식 병원 목록의 병원을 검색해 고르고, 병동 이름을 입력해 병동을 만든 뒤 관리자가 되도록 한다. 생성 뒤에는 설정 안내 골격을 보여 주고, 만든 병동의 초대 관리·근무표로 바로 이동할 수 있게 한다.

기준은 `docs/feature-breakdown.md`의 B4a다. 작업 브랜치는 최신 `dev`(`0ed5b4c`)에서 만든 `feat/android-ward-create`다. 서버(A3 `GET /api/v1/hospitals/search`, A4 `POST /api/v1/wards`)와 `docs/openapi.yaml`은 바꾸지 않았다.

연동 구분: **목업 및 로컬 실서버 핵심 흐름 검증 완료(2026-11-12).** 운영 서버 검증은 아니다.
- 최신 dev `b352759`(#42)를 통합한 `405a8e5`에서 실제 backend와 동봉 목록을 사용했다. Android 17에서 실제 Kakao 로그인 → 병원 검색 → 생성 → 목록 갱신, 실제 서버의 응답 유실 (a)·(b)를 확인했다. 상세는 아래 최신 검증 절에 있다.
- JVM 및 Compose 화면 테스트는 fake/MockWebServer를 사용한다. 실제 서버 검증과 구분하며, 아래 2026-11-11 기록은 당시 결과를 보존한 것이다.

## CI 응답 유실 테스트 주소 고정 (2026-11-13, Codex)

- PR #44 head `3a737a5`의 Verify 43221785489는 Backend·Web 성공, Android 단위 140개 중 응답 유실 5개 실패였다. 실패 작업 재실행 attempt 2도 동일했다. 진단 커밋 `8b13420`의 Verify 43276828688도 같은 5개가 실패했고, 전체 예외에서 재진입 기대 `ALREADY_CREATED`가 실제 `NETWORK`가 되는 것과 가상 병동 2개 기대가 1개인 것을 확인했다.
- 일반 Windows·Linux 실행은 통과했으나 테스트 JVM 전용 hosts 파일에 `127.0.0.1 localhost`, `::1 localhost`를 함께 넣고 `-Djdk.net.hosts.file=<테스트 파일>`로 실행하니 **같은 5개 실패**(타임아웃 3개·assertion 2개)가 재현됐다. 시스템 hosts 파일은 변경하지 않았다.
- 원인은 단일 주소에 바인딩한 MockWebServer를 `localhost`라는 이중 주소 이름으로 호출한 fixture다. 응답 유실로 첫 경로가 실패한 뒤 OkHttp가 다른 주소 경로를 먼저 선택할 수 있고, 그 주소에는 서버가 없어 후속 요청이 `NETWORK`로 끝났다. 실제 앱의 무재시도 정책을 변경할 사안으로 단정하지 않는다.
- `WardCreationApiTest.signedIn`에서 MockWebServer 바인딩과 URL을 모두 **127.0.0.1**로 고정했다. 키 재사용·재생·중복 방지·자동 재전송 없음 assertion과 10초 대기 제한은 유지했다. 단위 테스트의 상세 예외 로그 보강도 유지한다. 앱·API·DB·CI 워크플로 변경 없음.
- 수정 후 Windows 해당 11개 통과. 같은 이중 주소 파일, Linux JDK 21, assertions·2코어·512MB 조건에서 **전체 140개 통과**(직접 JUnit, 13.613초). Linux 실행은 Windows에서 컴파일한 동일 클래스/의존성을 사용했으며 Linux Gradle 전체 빌드라고 주장하지 않는다.
- Windows에서 CI와 같은 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon`도 **BUILD SUCCESSFUL**, 71개 작업 중 48개 실행·23개 UP-TO-DATE. 단위 XML 합계 140개, 실패·오류·건너뜀 0. Lint·기기 UI는 테스트 주소 수정 범위라 이번에 재실행하지 않았다.
- 수정 전 5개 실패→수정 후 통과를 실제 네트워크 단절 테스트로 확인했다. 실서버·Kakao·에뮬레이터는 이번에 다시 켜지 않았다. 배포 영향 없음. 되돌리기는 테스트 주소 고정을 제거하면 되지만 이중 주소 환경의 CI 실패가 돌아온다. 푸시 후 최종 CI는 해당 head에서 별도 확인한다.

## Android 17 재검증 및 실서버 연동 (2026-11-12, Codex)

**환경과 수정**
- 기존 Pixel_10(Android 17/API 37.2, `emulator-5554`)을 `-no-snapshot-load`로 콜드 부팅했다. 데이터 초기화·새 이미지 설치 없이 Chrome의 Kakao 로그인 화면이 열렸고, 사용자가 직접 로그인한 뒤 생성 화면으로 자동 이동했다. 이전 Chrome `CredentialManager` 충돌은 이번에 재현되지 않았으며, 근본 원인을 확인하거나 Chrome을 수정한 것은 아니다.
- `:app:dependencyInsight --configuration debugAndroidTestRuntimeClasspath --dependency espresso-core --offline --no-daemon`으로 Compose UI 테스트가 Espresso **3.5.0**을 가져옴을 확인했다. 수정 전 B4a UI 테스트 2건에서 `InputManager.getInstance` 예외가 재현됐다.
- `androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")`를 추가했다. [공식 수정 내역](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)의 `getSystemService` 사용 수정에 해당한다. 운영 앱 코드·compileSdk/targetSdk·서버·API·DB 스키마는 바꾸지 않았다.
- 설치된 JDK 21로 실행했다. 첫 재시도의 Android Studio JBR 25에서는 Gradle이 `25.0.3` 오류로 기기 테스트 실행 전에 실패했고, JDK 21로 바로잡았다.
- backend `bootRun`은 임시 세션 비밀값과 `jdbc:h2:mem:b4a_device_verification;DB_CLOSE_DELAY=-1`, 로컬 audience 설정으로 실행했다. 동봉 목록 **79,899건** 적재를 확인했다. 사용자 파일 DB·`local.properties`는 변경하지 않았다.

**실제 앱·서버 확인**
- `로그인하고 병동 만들기` → 사용자 Kakao 로그인 → 생성 화면 진입 성공. 실제 목록에서 `365` 검색 후 병원명·주소·종별과 결과 구체화 안내를 확인했다.
- 가상 병동 `B4a-verify-1112` 생성 → 관리자 설정 안내 → `초대 관리 열기` 즉시 이동 → 빈 초대 목록 → 병동 목록에서 병동·병원명 표시를 확인했다. 초대는 발급하지 않았다.
- 같은 병원·같은 이름으로 다시 생성하면 실제 서버가 **409**를 반환하고 앱이 "같은 병원·이름의 병동을 이미 만들었어요"와 목록 이동을 표시했다.
- 선택 병원과 입력 이름을 유지한 채 실제 가로 회전 후 상태가 보존됐다. 회전 설정은 원래 `free`로 복원했다. 가로 화면의 모든 버튼 접근성·소프트 키보드 전체 동작을 확인한 것은 아니다.
- 존재하지 않는 가상 검색어를 입력하고 Android Enter 키로 검색해 빈 결과 안내와 `건강보험심사평가원 전국 병의원 및 약국 현황 (2026.9. 기준)` 출처를 확인했다. 검색 결과의 주소 표시는 확인했으나, 이름이 정확히 같은 병원 두 곳을 골라 비교하지는 않았다.

**실서버 응답 유실 (a)·(b)**
- 사용자 명시 허용 뒤 PC 루프백 임시 프록시를 사용했다. 실제 backend로만 전달하고 생성 요청에서 한 번 연결을 끊었다. 본문·토큰·키 값은 로그나 파일에 기록하지 않았으며, 키/병동 ID의 일치 여부만 메모리에서 비교했다.
- (a) `B4a-loss-a-1112`: 요청을 읽고 실제 backend로 전달하기 전에 연결 종료. 생성 요청 수는 1 증가했지만 backend 전달 수는 증가하지 않아 자동 재전송이 없음을 확인했다. 앱의 결과 미확인 안내 → `다시 시도`에서 **같은 키**, 실제 서버 **201**, 생성 완료 화면을 확인했다.
- (b) `B4a-loss-b-1112`: 실제 backend의 **201** 응답을 받은 뒤 앱에 보내기 전에 연결 종료. 앱이 결과 미확인 상태가 됐고 자동 재전송은 없었다. `다시 시도`에서 **같은 키·같은 병동 ID**, 재생된 **201**, 생성 완료 화면을 확인했다.
- 마지막 실제 `GET /api/v1/wards` 응답의 가상 이름별 개수를 프록시 메모리에서 집계했다. 기본 생성·(a)·(b) 병동은 **각각 1개**였다. DB 직접 조회는 하지 않았다.
- 프록시 설정은 원래 `null`로 복원하고 프로세스·임시 스크립트를 제거했다. 이후 프록시 없는 앱 재실행에서도 세 병동이 목록에 각각 한 번 보였다.

**자동 검사와 한계**
- `./gradlew.bat :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon`: **BUILD SUCCESSFUL**, 63개 작업 중 18개 실행. 이때 단위 작업은 UP-TO-DATE였다. Lint XML: 오류 **0**, 경고 **35**, Information **1**.
- 이후 `./gradlew.bat :app:testDebugUnitTest --rerun-tasks --no-daemon`: **BUILD SUCCESSFUL**, 24개 작업 모두 실행. XML 합계 **140개**, 실패·오류·건너뜀 **0**.
- 수정된 테스트 APK만 `adb install -r`로 설치한 뒤 `adb shell am instrument -w -r -e class com.nurseflow.app.feature.ward.create.WardCreateScreenUiTest com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner`: **OK (2 tests)**, 두 메서드 모두 status code 0. 스크롤 접근성·422 안내/거절 행 비활성화 확인. 이 검사는 fake API를 사용한다. Gradle의 기존 connected HTML/XML은 수정 전 실패 기록이며, 성공 근거는 이번 직접 instrumentation 출력이다.
- 백엔드 전체 테스트·전체 기기 테스트·CI·운영 서버는 이번에 실행하지 않았다. 배포 영향 없음. 되돌리기는 Espresso 명시 의존성 추가를 제거하면 되지만, Android 17의 기존 UI 테스트 초기화 오류가 돌아온다.
- 공유 인계: `feat/android-ward-create`, HEAD `405a8e5`의 미커밋 변경은 테스트 의존성과 검증 문서다. PR 미개설 유지, 커밋·푸시·병합·배포 없음. 에뮬레이터와 검증용 backend(8080)는 실행 상태로 둔다. 메모리 DB이므로 backend 종료 시 검증 병동·서버 세션이 사라진다.

## 구현 요약

**계약 소비 (`WardCreationApi`, 기존 `NurseFlowApi`와 분리)**
- `GET api/v1/hospitals/search?query&page&size`: 화면은 page 1, size 20만 쓴다. 응답 `items[{source, externalId, name, address, category?}]`, `totalCount`, `attribution`.
- `POST api/v1/wards`: 헤더 `Idempotency-Key`(UUID), 본문은 정확히 `{"hospital":{"source","externalId"},"wardName"}`다. 병원명·주소·`timeZone`은 보내지 않는다(서버가 병원 정보를 직접 확정하고 기본 시간대 Asia/Seoul을 쓴다). 201은 `MembershipDto`.
- 클라이언트는 `invitationApi`와 같은 무재시도 설정이다(유휴 연결 미재사용, 연결 실패 자동 재전송 없음, 401은 한 번 갱신 후 재요청). 실시간 대체 조회를 고려해 읽기 제한시간만 30초다. 본문 로깅은 없다.

**화면 흐름**
- 병동 목록의 `병동 만들기` 버튼과 빈 병동 카드의 버튼, 로그인 화면의 `로그인하고 병동 만들기`로 들어간다.
- 검색(버튼·키보드 Search 액션으로만 실행) → 병원 선택(병원명·주소·종별 표시, 동명 병원은 주소로 구분) → 병동 이름 → 완료와 설정 안내.
- 병원을 직접 입력하는 경로는 없다. 결과 아래에 항상 `attribution`을 표시한다. `totalCount`가 한 페이지보다 크면 "검색어를 더 구체적으로" 안내를 보인다.
- 설정 안내는 앱이 제공하는 기능 여부만 보여 준다(구성원 초대: 지금 가능, 나머지: "앱에서 곧 설정할 수 있어요"). 서버의 설정 완료 여부는 추정하지 않는다.
- 클라이언트 검색어 검증은 서버 규칙(trim 후 2~50자, 제어 문자 불가, 기호·공백을 뺀 글자·숫자 2자 이상)을 그대로 따르는 안내용이며, 최종 판단은 서버가 한다.

**생성 화면 ViewModel 수명 (사용자 보완 1)**
- 세션 ViewModel 저장소에 고정 키 `ward-create` 하나만 둔다. 진입할 때마다 키가 늘어나지 않는다.
- 진입과 종료(닫기·완료 후 이동)마다 `reset()`을 호출한다. 회전·재구성에서는 같은 인스턴스가 유지되어 상태가 이어진다.
- `reset()`은 검색어·결과·선택·병동 이름·키/초안·오류·생성 결과를 지우고 진행 중인 검색·생성 요청을 취소하며 세대 번호를 올려, 그 전에 시작한 요청의 응답(성공·실패 모두)은 버려진다.
- 생성 중에는 닫기·뒤로가기·입력이 잠긴다.

**생성 직후 이동 (사용자 보완 2)**
- 201을 받으면 `WardsViewModel.onCreated(membership)`이 반환된 병동을 목록 상태에 즉시 합친다(`wardId`로 중복 제거). 이어서 화면을 가리지 않는 조용한 새로고침을 한다.
- 새로고침 결과에 새 병동이 아직 없어도 서버 목록에 나타나기 전까지 세션 동안 유지한다. 새로고침이 실패해도 새 병동은 사라지지 않는다.
- 그래서 `초대 관리 열기`·`이 병동 근무표 보기`가 목록 로드 완료를 기다리거나 목록으로 되돌아가지 않는다. 근무표 진입 시 기존 `wardEntry += 1`을 유지한다.
- 목록을 아직 불러오기 전에 생성이 끝난 경우에는 새 병동만 먼저 보이고(인사말은 이름을 모르므로 생략), 새로고침 결과가 오면 서버 목록과 합쳐진다.

**로그인 후 병동 만들기 의도 (사용자 보완 3)**
- 결정은 순수 함수 `resolveCreateIntent(armed, phase, invitationPending)`에 있고 단위 테스트한다.
- 우선순위: 대기 중인 초대 > 병동 생성. 초대가 있으면 의도는 **지우고** 나중으로 미루지 않는다.
- 로그인 화면에서만 설정한다. 첫 로그인(SignedIn)에서 한 번만 소비한다(열거나, 초대에 밀려 지운다). 로그아웃 실패·복원 실패 상태에서는 지운다.
- 취소 카드의 `취소` 버튼, 일반 `카카오로 로그인` 버튼(= 그냥 로그인)은 의도를 지운다.
- 로그아웃·세션 만료·다른 계정으로 전환(userId/generation 변경)은 모두 첫 로그인에서 이미 소비한 뒤이므로 다시 실행되지 않는다.

## Idempotency-Key 정책

- 키는 "생성 시도 내용" `Draft(source, externalId, trim한 wardName)` 하나에 묶는다. 이 내용은 서버가 재생 해시로 쓰는 값(계정 + trim한 본문)과 같은 기준이다. 키와 초안은 ViewModel 메모리에만 있고 저장·로깅하지 않는다.
- 제출할 때 현재 초안이 키를 쥔 초안과 같으면 같은 키, 다르면 새 UUID다. 공백 차이만 있으면 같은 초안이다.
- 응답 유실(NETWORK)·5xx·알 수 없는 오류: 키를 유지하고 "결과를 확인하지 못했어요. 같은 내용으로 다시 시도하면 중복으로 만들어지지 않아요"와 `다시 시도`를 보여 준다.
- 유실 뒤 입력을 바꾸면 새 키다. 이때 "이전 요청으로 병동이 이미 만들어졌을 수 있다"는 안내와 `병동 목록 확인`을 함께 보여 준다.
- 201: 키·초안을 폐기한다. 이후 진입은 `reset()`으로 새 상태다.
- 확정 거절(400, 409 중복·상한, 422, 503 미적재): 서버는 성공했을 때만 재생 기록을 남기므로 키를 유지해도 무해하고, 입력이 바뀌면 자연히 새 키가 된다.
- 409 `IDEMPOTENCY_KEY_REUSED`: 키와 초안을 지우고(다음 제출은 새 키) 자동 재전송은 하지 않는다.
- 프로세스 종료(키 소실) 뒤 같은 내용을 다시 제출하면 새 키이므로, 첫 요청이 성공했다면 서버가 `WARD_ALREADY_CREATED`로 막고 화면이 "이미 만든 병동" 안내와 `병동 목록 보기`를 보여 준다.
- 키는 마지막 초안 하나만 기억한다. 유실 A → 내용 B 제출(유실) → 다시 A처럼 되돌리면 새 키가 되어 서버의 중복 방지(같은 계정·병원·이름)가 막는다. 아래 테스트가 이 경로를 확인한다.
- 자동 재시도는 없다. 401 갱신 뒤 한 번 재요청하는 것은 같은 요청·같은 헤더라 안전하다.

## 오류 처리

서버 `code`를 먼저, 다음에 HTTP 상태로 분류한다. 서버 `detail`은 표시하지 않는다.

| 코드/상태 | 검색 | 생성 |
| --- | --- | --- |
| 200 빈 `items` | "검색 결과가 없어요" + attribution | 해당 없음 |
| 503 `HOSPITAL_SEARCH_UNAVAILABLE` | 외부 서비스 일시 장애 안내, 다시 검색 | 같은 안내 |
| 503 `HOSPITAL_SEARCH_NOT_CONFIGURED` | "서버에 병원 목록이 아직 준비되지 않았어요" | 같은 안내(생성 불가) |
| status 0 `NETWORK` | 네트워크 확인, 다시 검색 | 결과 미확인, 키 유지, 같은 내용 재시도 |
| 400 `INVALID_REQUEST` | 검색어 안내 | 병동 이름 확인 |
| 422 `HOSPITAL_NOT_FOUND` | - | 선택 해제 후 검색 단계로 복귀, 다시 검색 안내 |
| 409 `WARD_ALREADY_CREATED` | - | "이미 만든 병동" + `병동 목록 보기` |
| 409 `WARD_CREATION_LIMIT_REACHED` | - | 상한 안내, 생성 버튼 비활성 |
| 409 `IDEMPOTENCY_KEY_REUSED` | - | 키 갱신, 다시 시도 안내(자동 재전송 없음) |
| 401 `USER_NOT_FOUND` | 세션 종료(`container.logout()`) | 세션 종료 |
| 401 그 외 | "로그인이 필요해요" | 같은 안내, 키 유지 |
| 5xx 그 외 | 서버 오류, 다시 검색 | 결과 미확인, 키 유지 |

## 검증 결과 (2026-11-11, Claude)

실행 위치 `android/`, 명령 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --rerun-tasks`: **`BUILD SUCCESSFUL`**.

**JVM 단위 테스트** (P3 수정 전 기록. 수정 후 결과는 "Codex 리뷰 P3 2건 수정" 절)
- XML(`app/build/test-results/testDebugUnitTest`) 기준 **133개 통과, 실패·오류·건너뜀 0**. 기존 76개(B3a 리뷰 수정 후 기록과 같음)에 신규 57개가 더해진 값이다. 신규 파일별 개수:
  - `WardCreateViewModelTest` 30
  - `WardCreationApiTest` 11 (MockWebServer)
  - `CreateIntentTest` 7
  - `WardsViewModelTest` 5
  - `HospitalQueryTest` 4
- 그 외 기존 파일의 개수는 회귀 확인으로 그대로 실행했고 모두 통과했다.

**Lint**: 오류 0, 경고 33개(기존 기준과 같음).

**사용자 보완 항목별 테스트 이름**
1. 생성 화면 수명·초기화·늦은 응답 폐기 (`WardCreateViewModelTest`)
   - `recompositionOrRotationKeepsStateBecauseTheSameModelSurvives`
   - `resetOnReentryClearsEverythingIncludingTheKey`
   - `resetClearsACreatedWard`
   - `searchResponseFromBeforeResetIsDiscarded`, `searchFailureFromBeforeResetIsDiscarded`
   - `createResponseFromBeforeResetIsDiscarded`, `createFailureFromBeforeResetIsDiscarded`
2. 생성 직후 이동 (`WardsViewModelTest`)
   - `createdWardIsVisibleImmediatelyAndSurvivesAReloadThatDoesNotYetListIt`
   - `reloadContainingTheCreatedWardDoesNotDuplicateIt`
   - `createdWardIsDedupedByWardId`
   - `failedSilentRefreshKeepsTheCreatedWardVisible`
   - `createdBeforeFirstLoadIsShownAndMergedWithTheLaterList`
3. 로그인 후 병동 만들기 의도 (`CreateIntentTest`)
   - `pendingInvitationWinsAndTheIntentIsDroppedNotDeferred`
   - `afterConsumptionLogoutSessionInvalidAndAccountSwitchNeverRefire`
   - `failedLogoutOrRestoreDropsTheIntent`, `cancelledIntentNeverOpens`, `signInOpensTheCreateScreenAndConsumesTheIntent`, `staysArmedWhileLoginIsStillPossible`, `notArmedDoesNothing`
4. 응답 유실 (`WardCreationApiTest`, 상태를 가진 MockWebServer dispatcher: 키 기록, 첫 호출에서 병동 생성 뒤 연결 끊기, 성공만 재생 기록)
   - (a) `caseA_requestNeverReachedTheServer_nothingWasCreatedAndTheSameKeyCreatesOnce`
   - (b) `caseB_wardCreatedButResponseLost_sameKeyRetryReplaysAndExactlyOneWardExists`
   - `caseB_changedInputAfterLossUsesANewKeyAndIsWarned`
   - `caseB_duplicateGuardAnswersAlreadyCreatedWhenTheOriginalContentIsSentWithAFreshKey`
   - `reenteringWithTheSameContentAfterALostResponseIsStoppedByTheDuplicateGuard`
   - `lostCreateResponseIsNotRetriedAutomatically` (요청 수 1)

**그 밖의 주요 검증 내용**
- 늦게 도착한 이전 검색 결과 폐기, 재검색 시 이전 결과 무시, 빈 결과·NETWORK·503 두 종류·400 구분, attribution 보존.
- 검색어 `(가)`·1자·51자·제어 문자는 서버 호출 없이 거절한다.
- 생성 본문은 선택한 후보의 `source`·`externalId`와 trim한 `wardName`뿐이다(MockWebServer가 JSON 키 집합을 확인: 최상위 `hospital`, `wardName`, 하위 `source`, `externalId`).
- `Idempotency-Key` 헤더·Bearer·한글 query 인코딩·page/size, problem+json 코드 파싱.
- 생성 중 중복 탭 무시, 같은 키 재사용·변경 시 새 키·공백 차이는 같은 키·성공 뒤 새 키·키 충돌 뒤 새 키.
- 422 → 선택 해제·검색 단계, 409 두 종류 분기, `USER_NOT_FOUND` → 세션 종료 콜백.

**응답 유실 (a)와 (b)를 구분한 결과 (모두 MockWebServer fake, 실서버 아님)**
- (a) 서버가 요청을 처리하기 전에 끊긴 경우: fake에 병동이 만들어지지 않았고, 같은 키로 다시 시도하면 병동이 1개 만들어지고 화면이 완료 상태가 된다. 클라이언트는 (a)와 (b)를 구별할 수 없어 두 경우 모두 같은 "결과 미확인" 상태가 된다.
- (b) 서버가 병동을 만들었지만 응답이 유실된 경우: 같은 `Idempotency-Key`가 다시 전송되고, 두 번째 응답은 첫 병동(`ward-1`)을 재생하며, fake 안의 병동은 정확히 1개이고, 화면은 완료 상태가 된다.
- MockWebServer는 dispatcher에서 접속 시점 끊기를 흉내 낼 수 없어, (a)는 "요청을 읽었지만 처리하지 않고 응답 없이 연결을 끊는 서버"로 만들었다. 서버에 저장되는 것이 없다는 점은 같다.
- **(b)는 MockWebServer fake로만 재현했다. 실제 서버의 재생 동작(`WardService`의 재생 해시·기록 저장)은 이번에 검증하지 않았다.**

**기기·실서버**
- `adb devices`: 연결된 기기·에뮬레이터 없음(`adb`는 PATH에 없어 SDK `platform-tools`의 것을 썼다). 그래서 **디버그 빌드 설치, 로그인 화면 진입 버튼 확인, 회전 상태 유지 등 기기 수동 확인은 하지 않았다.**
- MockWebServer 기반 기기 UI 시연은 로그인 fake 구성이 커서 이번 범위에서 제외했다.
- 이 환경에서는 Compose UI 테스트를 쓰지 않았다. 화면 배치(예: 좁은 화면에서의 줄바꿈)와 키보드 Search 동작은 사람이 직접 보지 못했다.
- `:app:connectedDebugAndroidTest`는 기기가 없어 실행하지 않았다. `assembleDebugAndroidTest`로 기기 테스트 코드가 컴파일되는 것만 확인했다.

**저장소 확인**
- `git diff --check`: 통과(CRLF 변환 안내만 출력).
- `git diff --stat origin/dev -- backend docs/openapi.yaml`: 출력 없음(변경 없음).
- `nurseflow.mv.db`, `local.properties`: 변경하지 않았다. `nurseflow.mv.db`는 계속 미추적이다.

## Codex 리뷰 P3 2건 수정 (2026-11-11, Claude)

**1. 이름 입력 단계 스크롤·키보드**
- `SearchStep`과 `NameStep`은 각자 `verticalScroll` Column 하나로 감싸 `weight(1f)`로 남은 높이를 쓴다. 병원 정보·입력·유실 안내·오류·생성/재시도/병동 목록 확인 버튼이 모두 같은 스크롤 안에 있다.
- 검색 결과는 한 페이지(최대 20개)를 Column으로 그려 `LazyColumn` 중첩을 없앴다. 설정 안내(`WardSetupGuide`)는 기존 `LazyColumn` 하나이며 다른 스크롤 안에 들어 있지 않다. 화면 본문마다 스크롤 컨테이너는 하나다.
- 키보드는 루트의 `safeDrawingPadding()`(IME 포함)이 높이를 줄이고, 그 안의 스크롤이 버튼까지 닿게 한다. 별도 `imePadding`은 넣지 않았다. 실제 키보드 표시는 기기에서 확인하지 못했다.
- 생성 중 입력·`닫기`·`병원 다시 고르기` 비활성화와 `NurseFlowRoot`의 뒤로가기 잠금은 바꾸지 않았다.

**2. 422 `HOSPITAL_NOT_FOUND` 안내**
- 검색 단계 맨 위에 "선택한 병원을 찾을 수 없어요. 다시 검색해 주세요."를 표시한다(`createError`가 `HOSPITAL_NOT_FOUND`일 때, 테스트 태그 `hospitalNotFoundNotice`).
- 서버가 거절한 병원은 `WardCreateState.invalidHospitals`(`source:externalId`)에 기록한다. 이전 결과 목록에서 그 행은 비활성화되고 "선택할 수 없어요"가 붙으며, `select()`도 거절한다. 다른 병원은 고를 수 있다. `reset()`은 이 기록을 지운다.
- 안내 해제 시점: **새 검색이 실제로 시작될 때**(클라이언트 검증을 통과해 `Loading`이 될 때), 또는 **다른 병원을 고를 때**. 검색어 입력만으로나 클라이언트 검증 실패로는 지워지지 않는다. 한도 도달 오류(`LIMIT_REACHED`)는 이 규칙으로 지워지지 않는다.

**검증 결과 (이 수정 후, `--rerun-tasks`로 80개 작업 모두 실제 실행, UP-TO-DATE 없음)**
- 명령: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`(2분 40초).
- JVM 단위 테스트: XML 기준 **140개 통과**, 실패·오류·건너뜀 0(이전 133 + 신규 7). `WardCreateViewModelTest`는 30 → 37개.
  - 신규: `refusedHospitalCannotBePickedAgainAndOthersCan`, `notFoundNoticeStaysUntilANewSearchStartsThenClears`, `invalidSearchTextKeepsTheNotFoundNoticeBecauseNoSearchStarted`, `pickingADifferentHospitalClearsTheNotFoundNotice`, `searchFailureAfterNotFoundReplacesTheNoticeWithTheSearchError`, `resetForgetsRefusedHospitals`, `refusedLimitErrorIsNotClearedByANewSearch`.
  - 이 테스트들은 **상태만** 확인한다. 안내 문구가 화면에 보이는지는 확인하지 않는다.
- 테스트 APK: `app-debug-androidTest.apk` 빌드 성공. Compose UI 테스트 의존성 `ui-test-junit4:1.7.0`(androidTest)·`ui-test-manifest:1.7.0`(debug)를 추가했다.
- Lint: 오류 0, 경고 35개. 늘어난 2개는 새 테스트 의존성의 "최신 버전 있음"(`GradleDependency`)이다.
- `git diff --check` 통과(줄바꿈 안내만). `git diff --stat origin/dev -- backend docs/openapi.yaml` 비어 있음. `nurseflow.mv.db`·`local.properties` 변경 없음.

**Compose UI 테스트: 작성·컴파일만 했고 실행하지 못했다**
- `WardCreateScreenUiTest`(`androidTest`, 가상 데이터·fake API)는 두 가지를 검사하도록 썼다.
  - `hospitalNotFoundNoticeIsShownOnTheSearchStepAndTheRefusedRowIsDisabled`: 422 안내 노출, 거절된 행을 눌러도 선택 단계로 가지 않음, 다른 병원은 선택됨, 선택 뒤 안내 사라짐.
  - `nameStepButtonsAreReachableOnAShortScreenWithALongLossNotice`: 320dp×180dp 상자 안에서 `병동 목록 확인`과 `병동 만들기` 버튼을 `performScrollTo`+`performClick`으로 눌러 닿는지.
- 기기·에뮬레이터가 연결되지 않아(`adb devices` 빈 목록) 실행하지 못했다. 따라서 **스크롤 접근성과 422 안내 노출은 UI에서 검증되지 않았다.** 위 단위 테스트(상태)와 구분한다.
- 기존 `recompositionOrRotationKeepsStateBecauseTheSameModelSurvives`는 같은 ViewModel 인스턴스의 상태 유지만 확인한다. 실제 Activity 회전이나 Compose 연결을 검증한 것이 아니다.

**재리뷰와 후속**
- Codex 재리뷰 대기. 실서버 검색→생성→목록 갱신, 실제 Kakao, 기기 확인(위 UI 테스트 실행 포함: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nurseflow.app.feature.ward.create.WardCreateScreenUiTest`), 응답 유실 (a)(b)의 실서버 확인은 PR 병합 전에 남아 있다.

## 미검증 항목

- 실서버의 `IDEMPOTENCY_KEY_REUSED`·상한(5개), 유실 뒤 입력 변경, 목록 미적재/외부 장애 서버. 기존 fake/JVM 검증과 구분한다.
- 실제 로그인 중 초대 대기와 생성 의도 경합, 생성 중 뒤로가기·입력 잠금, 소프트 키보드·다양한 화면 크기 전체 수동 검사.
- 이름이 정확히 같은 실제 병원 두 곳의 주소 비교. 주소·종별 표시와 출처 기준 시점 자체는 실기기에서 확인했다.
- 운영 서버·물리 기기·전체 instrumentation 회귀 검사. 위 B4a Compose 2건의 fake 화면 검사는 실행·통과했다.

## 실서버 연동 절차 (데이터 적재 후)

PR 병합 전에 확인한다. 토큰·Idempotency-Key 값은 기록하지 않는다. 병동 이름은 가상 이름만 쓴다.

1. 최신 dev 백엔드를 실행하고 동봉 목록 적재 로그로 건수를 확인한다. 외부 목록을 별도로 사용할 때만 `NURSEFLOW_HOSPITAL_DATASET_PATH`를 설정한다.
2. 기기 앱에서 Kakao 로그인 → `병동 만들기` → 실제 병원명 검색한다. 동명 병원이 주소로 구분되는지, `attribution`의 기준 시점이 보이는지 확인한다. 빈 결과와 `HOSPITAL_SEARCH_NOT_CONFIGURED`(데이터 미적재 서버) 안내도 각각 본다.
3. 가상 병동명으로 생성한다. 목록이 갱신되고 `hospitalName`이 표시되는지, 설정 안내의 `초대 관리 열기`가 목록 새로고침 완료 전에도 목록으로 되돌아가지 않는지 확인한다.
4. 같은 병원·같은 이름으로 다시 생성해 `WARD_ALREADY_CREATED`와 "이미 만든 병동" 안내를 확인한다.
5. 응답 유실 두 경우를 따로 재현한다.
   - (a) 서버가 요청을 처리하기 전에 닿지 않는 경우: 생성 직전에 서버를 중단하거나 기기 네트워크를 끊고 제출한다. 병동이 만들어지지 않았는지 서버 DB·목록으로 확인한 뒤, 서버를 복구하고 `다시 시도`로 병동이 1개 만들어지는지 확인한다.
   - (b) 서버가 만든 뒤 응답이 유실되는 경우: 서버가 요청을 처리한 직후 응답이 가기 전에 연결이 끊기도록 한다(예: 프록시나 방화벽으로 응답만 차단). 같은 내용으로 `다시 시도`했을 때 같은 키가 재전송되어 병동이 1개로 남는지 목록과 서버 DB로 확인한다. 이 동작은 서버의 재생 기록을 실제로 검증하는 유일한 방법이다.
   - 두 경우 모두 입력을 바꿔 제출했을 때의 새 키 안내도 확인한다.
6. 상한(5개)과 `IDEMPOTENCY_KEY_REUSED`는 선택 확인이다.
7. `로그인하고 병동 만들기`와 초대 링크 대기가 겹칠 때 초대가 우선하고 생성 화면이 나중에 열리지 않는지 확인한다.
