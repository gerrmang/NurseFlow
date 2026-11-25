# B8a Android 희망 오프 검증

## 기기 인수 완료 (2026-11-25 20:44 KST)

- 사용자 요청에 따라 미완료였던 실제 Android 화면·두 계정 실서버 인수를 끝냈다. 아래 절의 '기기 미완료/Draft 대기'는 당시 기록이며, 현재 상태는 이 절을 따른다.
- 원인: `netsh interface ipv4 show excludedportrange protocol=tcp`에서 Windows 예약 범위 **5517~5616** 확인. 기본 에뮬레이터 포트 5554/5562/5584의 임시 바인딩은 권한 오류였고 18082/18083은 성공했다. Windows 예약 설정을 바꾸지 않고 콘솔/ADB 포트를 **18082/18083**으로 지정해 연결했다.
- 기존 사용자 Pixel_10은 보존했다. 같은 Android 37.2 x86_64 Google Play 시스템 이미지로 `app/build/b8a-avd/`에 검증 전용 AVD를 만들고 snapshot 없이 실행했다. 새 기기의 디버깅 허용을 처리한 뒤 인증된 serial **emulator-18082**로 실제 APK 설치·ADB reverse·계측을 수행했다. 앱 인증과 서버 권한은 기존 코드 그대로다.
- 첫 Compose 7개 실행은 **5개 성공·2개 실패**였다. 실패는 화면 밖 LazyColumn 항목에 바로 `performScrollTo()`를 호출한 검사 코드였다. 목록의 `performScrollToNode()`로 항목을 찾고 표시 여부를 단언하도록 수정했다. 실서버 검사에도 같은 패턴을 적용해 비동기 조회 결과를 기다리며 실제로 스크롤한다. 수정 후 Compose 단독 **7/7 성공**.
- 최종 통합 명령(저장소 루트):

```powershell
./android/scripts/verify-off-requests.ps1 -Serial emulator-18082 -Adb '<SDK>/platform-tools/adb.exe' -WithServer
```

- **OK (8 tests), 37.328초, 실패·오류·건너뜀 0**. 가상 API Compose 7개와 실제 Activity/Compose/Retrofit/Spring/H2 두 계정 1개를 같은 계측 실행에서 통과했다. 스크립트는 adb exit 0만 믿지 않고 JUnit 성공 수와 실패·건너뜀을 검사한다.
- 실제 기기에서 간호사 신청→Activity 재생성→로그아웃→관리자 로그인/확정→로그아웃→간호사 재로그인/확정 상태·철회 불가, 서버 재조회 결과 일치를 확인했다. 인증 공급자·계정은 가상 OIDC이고 Spring/API/DB·화면은 실제 구현이다.
- `app/build/b8a-device-results/instrumentation.txt`, `b8a-admin-confirmed.png`, `b8a-nurse-confirmed.png`를 수집했다. 두 화면을 직접 열어 가상 간호사 확정 상태, 관리자 철회 제공·본인 철회 없음, 고정 성공 안내와 달력 요일/날짜 정렬을 확인했다. 산출물은 build 아래에만 두고 커밋하지 않는다.
- 최종 기기 검사 APK/Lint: JDK 21 `:app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon` **BUILD SUCCESSFUL(39초)**, 57작업 중 7개 실행·50개 UP-TO-DATE, Lint 오류 0·기존 경고 27. 이번 변경은 계측 검사·재현 스크립트·문서이며 API/ViewModel/운영 코드는 그대로다. 앞서 실행한 JVM 239개/실제 Spring opt-in 성공은 유지한다. `git diff --check` 통과.
- Android README에 Windows 예약 포트 점검·우회와 재현 명령을 남겼다. 기기 인수 완료 결과로 PR 본문을 갱신하고 Draft를 해제한다. 실제 Kakao 인증·물리 기기·운영 검증과 독립 승인은 별도 범위로 명시한다.

## PR 게시 전 최종 확인 (2026-11-25)

- 사용자 요청으로 전체 B8a 변경과 P3 수정을 자체 재검토했다. 서버 계약·세션/권한·상태 전이·마감/한도·경합/결과 유실·화면 안내/정렬·검사/fixture에서 추가로 조치할 결함을 찾지 못했다. 독립 에이전트·GitHub 승인 리뷰가 아니다.
- fetch 후 최신 dev는 여전히 `3e30752`이며 추가 통합이 필요하지 않다. 기기 인수 미완료를 명시해 dev 대상 Draft PR로 준비한다.
- B8a opt-in 포함 최종 단위 **239개 통과·기존 B5a 선택 1개 건너뜀·실패/오류 0**. 실제 Spring 1개도 다시 통과했다. 아래의 기본 검사 238개/2개 건너뜀과 opt-in 여부가 다르다.
- JDK 21 Android 전체 단위/앱·기기 테스트 APK/Lint **BUILD SUCCESSFUL(1분 25초)**, 80작업 중 14개 실행·66개 UP-TO-DATE. Lint 오류 0·기존 경고 27. `git diff --check`·fixture `node --check` 통과. 검증 후 이번 가상 공급자·메모리 서버만 종료했으며 18090/18091의 listen 없음 확인.

## P3 화면 수정 (2026-11-25)

- 리뷰 지적 2건 반영: 오류·결과 미확인·재조회/계속 버튼을 목록 바깥의 고정 상태 영역으로 옮겼다. 긴 안내는 이 영역 안에서 스크롤할 수 있고 접근성 live region으로 알린다. 잘못된 월 한도·마감일은 필드 옆에도 오류를 표시한다.
- 요일을 날짜와 동일한 전체 폭·7개 weight 열의 Row로 바꾸고 각 열의 텍스트를 중앙 정렬했다.
- 화면 회귀 3개 추가: 하단 정책 오류가 보이는지, 응답 유실 후 목록 위로 돌아가지 않고 복구할 수 있는지, 2026년 12월의 날짜와 요일 7개 열 중심이 일치하는지. 기존 4개와 합쳐 Compose 7개다.
- 수정 후 기본 단위 검사 XML 240개 중 **238개 통과·실서버 선택 검사 2개 건너뜀·실패/오류 0**. UI 수정 범위여서 가상 Spring fixture와 실제 Kakao/서버/웹 전체 검사는 다시 실행하지 않았다. 아래 초기 구현의 239개/1개 결과는 B8a 실서버 opt-in을 켠 당시 기록이다.
- 수정 후 JDK 21 Android 전체 단위·앱/기기 테스트 APK·Lint **BUILD SUCCESSFUL(1분 29초)**, 80작업 중 19개 실행·61개 UP-TO-DATE. Lint 오류 0·기존 경고 27, `git diff --check` 통과. `adb devices -l`에 연결 기기가 없어 추가 3개를 포함한 화면 검사는 실행하지 못했다. APK 컴파일과 기기 실행 결과를 구분한다.

## 기준과 범위

- 2026-11-25, 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-off-requests`. fetch 후 최신 `origin/dev` `3e30752`에서 분기. A12와 B3a를 소비하며 B8b·B7 미병합 코드를 쌓지 않는다.
- 소유 범위는 `android/**`와 `docs/work-status.md`. 서버·OpenAPI·DB 스키마·웹·CI·환경변수·의존성 변경 없음. 사용자 `nurseflow.mv.db`·`nurseflow.trace.db`는 보존·커밋 제외.
- 간호사 본인 신청·상태·이력·마감 전 미확정 철회, 관리자 취합·필터·대리 입력·확정·반려·확정분 철회, 정책 설정. 날짜는 한 건씩 제출하며 변경은 철회 후 신규 신청.
- 최신 결정 15와 A12에 따라 마감 뒤 예외 요청은 제출 가능하고 본인 철회는 불가하다. 예외는 관리자 확정 전 편성에 반영되지 않는다. 월 한도는 source=NURSE이며 SUBMITTED/CONFIRMED인 건만 센다. 관리자 대리 입력은 제외한다.

## 세션·경합·장애

- 세션 userId/generation 및 병동에 ViewModel/API를 묶고, 전송·401 갱신 재시도 전에 동일 세션인지 공통 HTTP 계층에서 확인한다. 활성화·쓰기 전·조회 뒤 병동 소속과 역할을 재확인한다.
- 읽기는 ticket/epoch로 늦은 응답을 폐기한다. 화면 이탈 시 읽기·쓰기를 취소하고 데이터·입력을 지우며 진행 중 저장은 결과 미확인으로 남긴다. 같은 세션 재진입 후 재조회와 사용자 확인으로 재개한다.
- 쓰기는 후속 전체 조회까지 직렬화한다. 신청·판단에는 멱등 키가 없어 통신/5xx/깨진 성공 응답을 자동 재전송하지 않는다. 결과 미확인 동안 월 이동·쓰기 차단, 재조회와 명시 확인 필요. 성공 후 재조회 실패도 편집 차단.
- 정책만 정확한 `"policy-N"` ETag와 `If-Match`를 사용한다. 409 상태 충돌과 412 정책 충돌은 재조회해 최신 목록·설정을 표시한다. 응답의 날짜·월·상태·출처·시각·대상·ETag를 검사하며 무본문·깨진 응답은 빈 성공으로 취급하지 않는다.
- 기기 표시 시간대를 명시하며 서버 `deadline` 절대시각 정각부터 예외로 안내한다. 최종 판정은 서버에 맡긴다. 화면을 열어 둔 동안 시각을 갱신한다. 사유·건강정보·토큰 로그 없음.

## Android 자동 검사

JDK 21, 기존 사용자 Gradle 캐시, `android/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon
```

- 최종 실행은 `NURSEFLOW_B8A_E2E=1`, **BUILD SUCCESSFUL(1분 34초)**. 80작업 중 19개 실행·61개 UP-TO-DATE. XML 총 240개 중 **239개 통과·기존 B5a 실서버 opt-in 1개 건너뜀·실패/오류 0**. 신규 43개(API 11, ViewModel 31, 실제 Spring 1) 모두 통과했다. 이번 B8a 실서버 opt-in은 건너뛰지 않았다.
- 앱·기기 테스트 APK 빌드 성공. 신규 Compose 4개와 실서버 기기 1개도 컴파일 성공했지만 기기 실행은 못 했다. Lint 오류 0·경고 27(기존과 동일). `git diff --check`, `node --check android/scripts/b8a-server.mjs` 성공.
- API·ViewModel 회귀: 본인/관리자 입력, 한도 0과 집계, 마감 직전/정각, 확정/반려/철회, 날짜·중복·윤년·혼합 월·중복 ID·무본문·잘못된 JSON/ETag, 409/412, 결과 유실·쓰기 직렬화·취소·후속 조회 실패, 세션 전환·권한 상실·늦은 월/이탈 응답.
- 최초 신규 Compose 검사 컴파일에서 미지원 `last()`를 사용해 실패했다. 대화상자 범위의 semantics 선택자로 수정했다. 제품 코드 컴파일은 그 전에 통과했다.

## 실제 Spring 인수

서버 코드는 변경하지 않고 `backend/`에서 JDK 21 `bootJar --offline --no-daemon` 성공(4작업 UP-TO-DATE). 별도 터미널, 저장소 루트:

```powershell
node android/scripts/b8a-server.mjs
```

루프백 18090(OIDC)/18091(Spring), 새 메모리 H2, 가상 병원 CSV, 실행 중 생성한 서명 비밀값만 사용한다. 기존 서비스가 포트를 쓰면 실패하며 사용자 DB·기존 환경의 API 키를 읽지 않는다. 종료 시 자신이 만든 Java/공급자만 정리한다. `android/`에서:

```powershell
$env:NURSEFLOW_B8A_E2E='1'
.\gradlew.bat :app:testDebugUnitTest --tests '*OffRequestRealServerTest' --offline --no-daemon
Remove-Item Env:NURSEFLOW_B8A_E2E
```

- 실제 Retrofit·ViewModel·Spring·H2로 가상 관리자·간호사·비소속 신원 검증. 초대 가입→빈 목록→본인 신청→한도·중복 거부→철회/재신청→관리자 확정/확정분 철회→한도 밖 대리 입력→정책 저장/오래된 ETag 412.
- 간호사 정책/확정 거부와 비소속 조회 거부의 실제 오류 코드를 검사한다. 마감 뒤 예외 접수·본인 철회 거부·관리자 반려, 소속 비활성화 후 목록 폐기도 확인한다. 가짜 HTTP 응답 검사가 아니라 실제 API 연동이다. 실제 Kakao 로그인은 사용하지 않는다.
- 실제 Spring 검사 **1개 통과·실패/건너뜀 0**(최종 0.945초). 실제 앱 화면의 두 계정 시연을 통과한 것으로 해석하지 않는다.

## 기기 화면 검사

APK 설치 뒤, 연결된 테스트 기기에 다음 검사를 실행한다. 고립된 테스트 Application·TestTokenStore를 사용해 사용자 로그인 세션을 읽지 않는다.

```powershell
adb shell am instrument -w -e class com.nurseflow.app.feature.offrequest.OffRequestScreenUiTest com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner
adb reverse tcp:18090 tcp:18090
adb reverse tcp:18091 tcp:18091
adb shell am instrument -w -e b8aE2e true -e class com.nurseflow.app.feature.offrequest.OffRequestRealServerDeviceTest com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner
```

- Compose 7개: 초기 제출 확인·상태·철회, 마감 후 예외·철회 없음, 관리자 대리 입력·확정·철회, 정책 확인 저장 4개와 P3 회귀 3개. 가상 API 검사와 실서버 검사를 구분한다.
- 실서버 기기 1개: 실제 Activity/Compose/HTTP로 간호사 신청→회전→로그아웃→관리자 확정→로그아웃→간호사 재진입 상태 확인. 가상 OIDC 공급자만 사용한다.
- **초기 기기 연결 실패 이력(현재 해결)**. 기존 Pixel_10(Android 37.2) AVD를 읽기 전용·snapshot 저장 없음·창 숨김으로 시작했다. 기본/SwiftShader, 명시 포트, Vulkan 비활성화·2코어/2GB 최소 설정을 시도했으며 게스트 부팅 완료 로그도 있었지만 `adb devices -l`은 연결 기기를 반환하지 않았다. `device 'emulator-5554/5556' not found`, 루프백 ADB 연결 거부/임시 offline이 반복됐다. 당시 APK 설치·계측 실행·화면 캡처는 수행되지 않았다. 상단의 예약 포트 우회 후 기기 인수 완료 기록이 이를 갱신한다.
- 초기 시도의 에뮬레이터·가상 공급자·메모리 서버만 PID/부모/명령행을 확인해 종료했다. 사용자 AVD 데이터 초기화·이미지 설치·저장 스냅샷 변경은 하지 않았다. 당시 후속이던 기기 두 계정 실서버 시연은 상단 기록대로 완료했다.

## 제한·배포·되돌리기

- 실제 Kakao 두 계정 인증, 물리 기기, 물리망 단절, 실제 다중 관리자 경합, 운영은 미검증. 독립 코드 리뷰·GitHub PR/CI는 별도다. backend·웹 전체 회귀는 서버/웹 변경이 없어 재실행하지 않는다.
- 관리자 대리 입력 후보는 서버 편성 대상 조회를 사용한다. 과거 요청에 연결된 간호사가 현재 조회에 없으면 이름 확인 불가로 표시한다. 공식 결재·주말 최적화·초안 편집·공개는 범위 밖이다.
- 미저장 입력은 재조회·이탈 시 초기화될 수 있고 실시간 구독은 없다. 결과 미확인 상태는 세션 메모리에만 있어 프로세스 종료 후 유지되지 않는다. 재진입은 항상 최신 목록을 읽으며 서버 중복/상태 검사가 후속 쓰기를 방어한다.
- Android 빌드에 영향. 수동 배포 없음. 롤백은 B8a 변경 revert와 이전 Android 빌드 복원. 서버/API/DB/환경 추가 변경 없음.
