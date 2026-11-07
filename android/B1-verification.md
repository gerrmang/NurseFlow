# B1 Android 로그인·세션 검증 기록

## 변경 목적과 내용

- 최신 확인한 `origin/dev` `e4ba671`에서 `feat/android-kakao-session`을 분기했습니다.
- `c060bf9`의 인증·네트워크·Keystore 기반을 선택적으로 이식했습니다. `9a27241`·`a6b0103`의 디버그 진단 의도는 키 해시 안내로 반영하되 오류 원문 로깅은 제외했습니다. `c1e6a51`의 소속 없음 안내만 반영하고 병동 생성 기능은 포함하지 않았습니다.
- 로그인·병동 조회는 기존 서버 계약을 호출하는 구현입니다. 자동 검사는 목업 HTTP 서버·가짜 인증 공급자를 사용하며, 아래 추가 검증에서 실제 Kakao 계정과 로컬 서버의 로그인·빈 병동 조회·세션 복원·로그아웃·재로그인도 확인했습니다.
- 초대 가입·직급 입력·병동 생성은 후속 B3a/B4a, 서버 근무표는 B9a 범위입니다.

## 관련 이슈

- 없음. 기준: `docs/feature-breakdown.md`의 B1.

## 검증

2026-11-07 로컬 검증:

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`: 최종 토큰 재전송 방지·백업 제외 수정까지 포함하여 통과(`BUILD SUCCESSFUL`, 24초).
- JVM 11개: 동시 401의 단일 회전, 만료/재사용 토큰 폐기, 오프라인 복원 재시도, 시작 시 만료 처리, 오프라인 로그아웃과 늦은 401, 회전/재로그인 화면 식별자, HTTP 재시도 상한, Bearer와 refresh body 계약, 병동 역할 목록, 오류 원문 비노출, 네트워크 오류 매핑.
- 빌드 도중 테스트 의존성이 추가된 첫 실행은 MockWebServer 미인식으로 실패했습니다. 설정을 다시 읽은 실행에서 해결했습니다.
- Lint는 오류 없이 통과했습니다. 라이브러리 업데이트 권고, 기존 프로토타입 리소스·아이콘·Modifier 관련 경고, 디버그 HTTP 허용 경고는 남아 있습니다.
- `git diff --check`: 통과.
- 설치된 `Pixel_10` AVD(Android 17)를 실행하고 Debug APK 설치·실행을 확인했습니다. 초기 검은 화면은 기기 절전 상태였고, 깨운 뒤 ADB 캡처로 정상 화면을 확인했습니다.
- 기기 UI 수동 확인: 로그인 첫 화면, Kakao 키 미설정 오류 안내, 가상 시연 진입, 서버 미공유 표시, 시연 나가기 후 로그인 복귀가 정상입니다.
- `./gradlew.bat :app:connectedDebugAndroidTest`: **3개 통과, 실패/오류/건너뜀 0**, `BUILD SUCCESSFUL`. 실제 Android Keystore/DataStore 암호문 저장과 새 저장소 인스턴스의 복호화, 새 SessionManager의 복원·회전·로그아웃, refresh 거절 시 기기 자격정보 폐기를 확인했습니다. 인증 API는 가짜 구현입니다.

2026-11-07 키 해시 수정 후 추가 검증:

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`: `BUILD SUCCESSFUL`(1분 7초). JVM 11개, 실패/오류/건너뜀 0. Lint 오류 없이 통과했으며 기존 경고는 남아 있습니다.
- 실제 Kakao 인증 후 앱의 내 병동 화면과 소속 없음 안내를 확인했습니다. 사용자 인증 조작은 사용자가 직접 수행했습니다. 키 해시 수정 뒤에는 앞서 발생한 `Auth/Misconfigured HTTP 401` 없이 로컬 서버 연동 화면에 도달했습니다.
- 로그인 상태에서 `adb shell am force-stop` 후 `am start`로 앱 프로세스를 재시작했습니다. PID 변경과 재인증 없이 내 병동 화면으로 복원되는 것을 확인했습니다.
- 앱의 로그아웃 버튼으로 로그인 화면에 복귀하고 이전 사용자·병동 표시가 사라지는 것을 확인했습니다.
- 로그아웃된 상태에서 `./gradlew.bat :app:connectedDebugAndroidTest`: 실제 Keystore/DataStore 기기 테스트 **3개 통과, 실패/오류/건너뜀 0**, `BUILD SUCCESSFUL`(26초).
- 기기 테스트 종료 후 대상 앱이 제거되어 동일 Debug APK를 재설치했습니다. 로그인 버튼 → 사용자의 Kakao `Continue` 직접 선택 → 앱 복귀 → 내 병동·소속 없음 안내까지 재로그인을 확인했습니다. 재설치가 포함되므로 이 단계만으로 로그아웃 상태의 프로세스 재시작 유지까지 검증했다고 보지는 않습니다.
- `backend/`에서 `./gradlew.bat test --tests com.nurseflow.auth.api.KakaoAuthHttpTest --tests com.nurseflow.invitation.api.InvitationRedemptionHttpTest --tests com.nurseflow.ward.api.WardBootstrapHttpTest`: **30개 통과, 실패/오류/건너뜀 0**, `BUILD SUCCESSFUL`(33초). 이 검사는 가짜 OIDC 공급자를 사용하며 실제 계정의 역할별 앱 시연을 대신하지 않습니다.
- 키·토큰·실사용자 식별자를 검증 문서나 별도 캡처 파일에 기록하지 않았습니다. 서버 코드·API·DB 스키마 변경은 없습니다.

2026-11-07 남은 B1 인수 검증 완료:

- 사용자가 두 번째 실제 Kakao 계정 대신 가상 계정 전환 자동 검증을 선택했습니다. `RealBackendSessionDeviceTest`에서 실제 Android `NurseFlowRoot`·네트워크 클라이언트와 실제 Spring HTTP 인증 필터·서비스·H2를 연결했습니다. Kakao OIDC만 가짜 공급자이며, 관리자·간호사 계정에는 서로 다른 가상 병동을 부여했습니다.
- 관리자 로그인 시 해당 병동·`관리자`, 간호사 계정으로 전환 시 다른 병동·`간호사`가 표시되는 것을 UI 접근성 트리로 검증했습니다. 전환 후 이전 사용자 인사말·병동·관리자 역할이 남지 않는 것도 확인했습니다.
- 별도 서버에서 access token 수명을 2초로 설정하고 기본 시간 오차 허용까지 포함해 64초를 기다렸습니다. 원래 토큰의 실제 `/me` 응답이 401임을 먼저 확인하고, 앱의 정상 API 조회가 access/refresh token을 교체해 성공하며 서버 DB의 회전 횟수가 정확히 1회인 것을 확인했습니다. 화면의 세션 식별자는 유지됐습니다.
- 로그아웃 후 메모리 자격정보·화면 제거, 서버 refresh 철회, 같은 빈 저장소를 사용하는 새 SessionManager가 로그아웃 상태로 시작하는 것을 확인했습니다. 이 E2E는 별도 메모리 TokenStore를 사용합니다. 실제 Keystore/DataStore는 앞서 통과한 기기 검사 3개로 검증했습니다.
- 첫 실서버 만료 검사는 `unexpected end of stream`으로 실패했습니다. 일반 조회와 인증 클라이언트 모두 끊긴 유휴 연결을 재사용할 수 있던 문제를 수정했습니다. 일반 GET만 연결 복구 재시도를 허용하고, 인증 API는 유휴 연결을 보관하지 않으며 refresh 자동 재전송은 계속 금지합니다. JVM 회귀 검사 2개를 추가했습니다.
- 수정 후 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 통과. JVM **13개 통과, 실패/오류/건너뜀 0**. 테스트 정리 보완 후 테스트 APK 빌드와 Lint도 다시 통과했습니다.
- `adb shell am instrument -w -e backendE2e true -e class com.nurseflow.app.core.session.RealBackendSessionDeviceTest com.nurseflow.app.test/androidx.test.runner.AndroidJUnitRunner`: **`OK (1 test)`**, 67.835초. 서버 fixture도 기기의 성공 완료 신호를 확인한 뒤 정상 종료했습니다.
- `backend/`의 `./gradlew.bat check`: **84개 통과, 실패/오류 0**. 명시적으로 켜야 하는 E2E 서버 fixture 1개는 이 일반 실행에서 건너뛰었습니다(위 별도 실행에서 통과). 기존 Kotlin 경고는 남아 있습니다.
- 테스트 서버는 loopback의 18081·18082와 별도 메모리 DB만 사용했습니다. 서버 종료·ADB reverse 제거 후 기존 8080 서버를 쓰는 앱을 다시 실행하여 원래 로그인 세션과 소속 없음 화면이 보존된 것을 확인했습니다. 사용자 DB 파일과 로컬 키 설정은 변경하지 않았습니다.
- **B1 로컬 구현·인수 검증 완료**. 재현 절차는 `android/README.md`에 기록했습니다. GitHub CI·PR 리뷰·병합 완료를 뜻하지 않습니다.

미검증:

- KakaoTalk 설치 경로, 사용자 취소와 키 해시 불일치에 대한 재현 회귀 검사.
- 기기 이전 차단과 오프라인 동작의 기기 E2E. 오프라인 상태 처리는 JVM 검사로 확인했습니다.
- 두 실제 Kakao 계정의 전환. 사용자 선택에 따라 역할 표시·계정 전환은 가상 계정과 실제 Android/서버로 검증했습니다. 실제 Kakao 인증·로컬 서버 연동은 앞선 사용자 직접 로그인으로 확인했습니다.
- 로그아웃 후 재설치 없이 프로세스를 재시작했을 때 로그인 화면 유지.
- GitHub CI·브랜치 보호·독립 PR 리뷰: 확인하지 않았습니다.

## API·데이터·설정 변경

- API 계약 및 DB 변경 없음.
- Android 로컬 설정: `kakao.nativeAppKey`, `nurseflow.apiBaseUrl`, `nurseflow.releaseApiBaseUrl`. 실제 값은 저장소에 기록하지 않습니다.
- Kakao SDK, Retrofit/OkHttp, Kotlin serialization, lifecycle, DataStore 및 JVM 테스트 의존성을 추가했습니다.
- 기존 사용자 파일 `nurseflow.mv.db`는 수정하지 않았습니다.
- 후속 검증에서 `AppContainer`에 기본 동작을 유지하는 서버 주소·저장소 주입 인자를 추가했습니다. 별도 테스트 컨테이너가 실제 사용자 자격정보를 덮어쓰지 않도록 하기 위한 변경입니다.
- 백엔드에는 opt-in 기기 검사용 테스트 fixture만 추가했습니다. 운영 API·설정·DB 스키마 변경은 없습니다.

## 배포 영향

- 앱 시작 화면이 로그인/세션 복원으로 바뀝니다. 기존 시연 화면은 별도 진입합니다.
- 릴리스는 HTTPS 서버 주소와 Kakao 앱 등록 후 별도 검증해야 합니다. 현재 기본 릴리스 주소는 의도적으로 연결되지 않습니다.
- 서버 배포·DB 이전은 필요하지 않습니다.
- 일반 GET은 유휴 연결 단절을 복구하며, 인증 API는 매번 새 연결을 사용합니다. 인증 토큰 회전의 전송 오류 재시도는 계속 비활성입니다. 테스트 fixture는 배포 코드에 포함되지 않습니다.

## 되돌리기

- 병합 전에는 이 작업 브랜치를 배포하지 않습니다. 병합 후 문제 시 B1 변경 커밋을 되돌린 앱을 배포합니다.
- 서버와 DB는 변경되지 않으며 로컬 세션 저장소는 이 앱에만 속합니다.

## 남은 위험과 후속 작업

- 실제 Kakao 로그인·프로세스 재시작 복원·로그아웃·재로그인과, 사용자가 선택한 가상 계정 기반 역할별 병동·계정 전환·실제 서버 만료 갱신까지 로컬 인수 검증을 완료했습니다. GitHub CI·PR 리뷰·병합은 별도 단계입니다.
- 실로그인 검증용 서버는 기존 DB와 분리된 `jdbc:h2:mem:b1-device-e2e`를 사용합니다. 키와 임시 세션 서명 비밀값은 프로세스 환경으로만 전달하며 기록하지 않습니다.
- 초기 SDK 실패에는 구체적인 오류 코드가 없어서 디버그 화면에 SDK enum/HTTP 상태와 고정 분류만 표시하도록 보완했습니다(오류 설명·URL·토큰 원문은 출력하지 않음). 동의 후 발생했던 `Auth/Misconfigured HTTP 401`은 사용자의 키 해시 수정 이후 정상 로그인으로 재확인했습니다.
- 오프라인 로그아웃은 기기에서만 즉시 반영됩니다. 서버 철회 요청 실패 시 서버 토큰 수명 정책이 적용됩니다.
- 기기 시연: 로그인 → 소속 없음 안내 → 사전 준비된 가상 관리자/간호사 계정의 병동·역할 확인 → 앱 종료/재실행 → 토큰 만료 후 조회 → 로그아웃 → 다른 계정 로그인 → 이전 사용자 목록이 남지 않는지 확인합니다.
- 키·토큰·실사용자 정보를 포함한 로그나 스크린샷을 기록하지 않습니다.

## 최종 확인

- [x] 작업 브랜치는 `dev` 기반
- [x] 근무 필수 규칙·권한은 서버 책임 유지
- [x] 초대·직급·병동 생성 기능 제외
- [x] 문서에 검증과 미검증 구분
- [x] 실제 Kakao/로컬 서버 기본 로그인·복원·로그아웃·재로그인
- [x] 가상 계정 + 실제 Android/서버의 병동 역할·계정 전환·토큰 만료 갱신
- [x] B1 로컬 구현·인수 검증 완료
- [ ] GitHub CI와 PR 리뷰

## PR P2 리뷰 보완 (2026-11-07)

### 변경 목적과 내용

- 화면 회전으로 composition이 제거될 때 로그인 ViewModel과 진행 중인 인증 작업이 취소되는 문제를 수정했습니다. Activity의 유지되는 ViewModel에 화면별 ViewModelStore를 두고, 세션 식별자 변경 또는 Activity의 최종 종료 때 정리합니다. 동일 세션의 토큰 갱신·표시 이름 변경과 시연 화면 진입은 화면 작업을 정리하지 않습니다.
- 인증 요청 직렬화와 로컬 자격정보 잠금을 분리했습니다. 로그아웃은 진행 중인 네트워크 응답을 기다리지 않고 메모리·저장소의 자격정보를 지운 뒤 서버 철회를 요청합니다. 세션 revision을 비교하여 로그아웃 전 시작된 로그인·복원·갱신의 늦은 성공과 복원 오류가 로컬 세션을 되살리지 못하게 했습니다.
- 관련 이슈: 별도 이슈 없음. 사용자가 전달한 P2 리뷰 2건에 대응합니다.

### 검증

- `android/`에서 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`: `BUILD SUCCESSFUL`. JVM **20개 통과, 실패/오류/건너뜀 0**. Lint **오류 0, 경고 33개**.
- 추가 회귀 검사 7개: 유지된 Activity 저장소를 새 소유자가 다시 조회한 뒤 진행 중인 인증 코루틴 완료, 갱신 시 화면 유지와 로그아웃·재로그인 시 교체, 세션 종료·최종 파괴 시 작업 취소, 진행 중인 갱신과 무관한 로그아웃 및 새 SessionManager의 로그아웃 유지, 늦은 복원 성공·오프라인/401 오류 무시, 늦은 로그인 응답 무시, 서버 철회 지연 중 로컬 폐기·새 로그인.
- `git diff --check`: 통과.
- 이번 보완에서는 실제 Activity 회전 중 Kakao SDK 인증, 기기 계측 검사, 실제 서버 E2E를 다시 실행하지 않았습니다. 회전 시 수명은 JVM에서 유지된 ViewModelStore와 실제 ViewModel 코루틴을 사용해 검사했으며 실제 Activity 재생성은 후속 기기 확인 대상입니다. GitHub CI·리뷰 상태는 조회하지 않았습니다.

### API·데이터·설정 변경과 배포 영향

- API 계약·DB 스키마·환경변수·의존성 변경 없음. 근무 필수 규칙과 권한의 서버 검증 책임을 유지합니다.
- Android 앱 업데이트가 필요하며 서버 배포·데이터 이전은 없습니다. 기존 사용자 DB 파일과 로컬 키 설정은 수정하지 않았습니다.

### 되돌리기와 남은 위험

- 문제 발생 시 이번 리뷰 보완 변경을 되돌린 Android 앱을 재배포합니다. 저장 형식을 바꾸지 않아 별도 데이터 복구는 필요하지 않습니다.
- 서버 철회는 로컬 폐기 뒤 최선 노력으로 수행합니다. 오프라인 또는 갱신과 철회의 서버 경합 시에도 기기 토큰은 복원하지 않으며, 서버 측 만료·철회 정책은 기존 계약을 따릅니다.
- 실제 Kakao 인증 도중 화면 회전 및 로그아웃 후 재설치 없는 프로세스 재시작의 기기 검증은 남아 있습니다. 키·실사용자 정보·토큰을 로그나 fixture에 추가하지 않았습니다.

## PR P3·P4 저장소 오류 보완 (2026-11-07)

### 변경 목적과 내용

- 관련 이슈: 별도 이슈 없음. 로그아웃 중 TokenStore `read()`·`clear()` 오류 전파(P3)와 회귀 검사 누락(P4)을 보완했습니다.
- 로그아웃에서 저장소 읽기가 실패해도 메모리 세션과 진행 중 요청의 revision을 폐기하고 저장소 삭제를 시도합니다. 읽지 못한 토큰의 서버 철회 요청은 생략합니다. 코루틴 취소는 그대로 전파하되 로컬 폐기는 수행합니다.
- 삭제 오류는 예외를 UI 코루틴으로 전파하지 않고 `LogoutFailed` 상태와 로그아웃 재시도 버튼으로 안내합니다. 삭제 성공을 가장하지 않으며, 같은 프로세스의 복원 재시도는 남은 토큰을 읽거나 갱신하지 않고 삭제를 다시 시도합니다. 읽을 수 있는 토큰은 삭제 실패 시에도 서버 철회를 시도합니다.
- 세션 복원 실패 화면의 “로그아웃하고 다시 로그인”도 동일한 오류 처리를 사용합니다. 삭제 성공 후 로그인 화면으로 돌아갑니다. 오류 원문·토큰은 화면이나 로그에 추가하지 않았습니다.

### 검증

- `android/`에서 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`: `BUILD SUCCESSFUL`(14초). JVM **26개 통과, 실패/오류/건너뜀 0**. Lint **오류 0, 경고 33개**.
- 추가 회귀 검사 6개: 읽기 실패 시에도 로컬 폐기, 복원 읽기 실패 뒤 로그아웃·재로그인, 삭제 실패의 재시도·잔존 토큰 복원 차단·서버 철회, 읽기와 삭제의 동시 실패, 갱신 401 처리 중 삭제 실패, 읽기 취소의 전파와 로컬 폐기. IOException·IllegalStateException을 가짜 저장소에 주입했습니다.
- `git diff --check`: 통과.
- 미실행: 기기의 실제 DataStore 장애 재현, 기기 UI 계측, 실제 Kakao·서버 E2E. 이번 검증은 JVM 오류 주입과 Android 컴파일·Lint이며 GitHub CI 상태는 조회하지 않았습니다.

### API·데이터·설정 변경과 배포 영향

- API 계약·DB·환경변수·저장 형식·의존성 변경 없음. 근무 규칙과 권한의 서버 검증 책임을 유지합니다. Android 앱 업데이트만 필요하며 서버 배포·데이터 이전은 없습니다.
- 사용자 DB 파일과 로컬 키 설정은 변경하지 않았습니다.

### 되돌리기와 남은 위험

- 문제 발생 시 이번 저장소 오류 보완 커밋을 되돌린 앱을 배포합니다. DB 복구는 필요하지 않습니다.
- 실제 디스크 삭제에 실패하면 저장된 토큰은 남을 수 있습니다. 이 프로세스에서는 메모리 인증과 복원을 차단하지만, 프로세스 재시작까지 이어지는 차단 표시는 추가하지 않았습니다. 서버 철회도 실패한 경우 재시작 후 복원을 막는 영속적 보장은 없으므로 삭제 재시도가 필요합니다.
- 저장소 오류 처리와 재시도 UI를 추가했으며 독립 PR 승인·GitHub CI 통과를 확인했다는 의미는 아닙니다.

## PR P2 복원 재시도 수명 보완 (2026-11-07)

### 변경 목적과 내용

- 관련 이슈: 별도 이슈 없음. 세션 복원 재시도 중 화면 회전 후 로딩 화면에 머무르는 P2 리뷰를 보완했습니다.
- 재시도 버튼의 `rememberCoroutineScope`를 제거하고 `AppContainer.restoreSession()`을 호출합니다. 최초 복원과 재시도가 같은 Application 소유 범위에서 실행되어 Activity 재생성·composition 종료로 취소되지 않습니다. 완료 시 기존 세션 상태 스트림을 통해 새 화면이 성공·실패 결과를 받습니다.
- 재시도 버튼은 작업을 시작하는 역할만 맡으며, 화면 생성 시 재요청을 자동 실행하지 않습니다. 로그아웃의 revision 기반 늦은 응답 차단은 유지됩니다.

### 검증

- `android/`에서 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: `BUILD SUCCESSFUL`. JVM **26개 통과, 실패/오류/건너뜀 0**. Lint **오류 0, 경고 33개**.
- 테스트 정리 코드의 실험 API 사용을 없앤 뒤 `:app:assembleDebugAndroidTest :app:lintDebug`를 다시 실행하여 통과했습니다.
- `RestoreRetryDeviceTest` 2개를 추가했습니다. 실제 AppContainer 재시도 진입점을 UI 코루틴에서 호출하고 해당 UI 범위를 취소한 뒤에도 요청이 살아 있는지 확인합니다. 지연된 응답의 성공 시 로딩 종료, 실패 시 오류 표시 상태와 후속 재시도를 검증합니다. 별도 메모리 TokenStore를 사용하고 Kakao·HTTP 요청은 하지 않습니다.
- 별도 임시 데이터 이미지를 쓰는 창 없는 `Pixel_10` 에뮬레이터에서 `adb -s emulator-5556 shell am instrument -w -e class com.nurseflow.app.core.session.RestoreRetryDeviceTest com.nurseflow.app.test/androidx.test.runner.AndroidJUnitRunner`: **`OK (2 tests)`**, 0.278초. 기존 AVD의 사용자 데이터 이미지는 사용하지 않았습니다. 첫 부팅 직후 실행은 테스트 결과 없이 `Process crashed`로 종료되었고, 같은 APK로 다시 실행하여 2개 모두 통과했습니다.
- `git diff --check`: 통과.
- 미실행: 실제 Kakao·서버 E2E와 Activity 회전 UI 조작. 수명 회귀 검사는 회전 때 사라지는 UI 코루틴 범위를 직접 취소하는 방식입니다. GitHub CI 상태는 조회하지 않았습니다.

### API·데이터·설정 변경과 배포 영향

- API 계약·DB·환경변수·저장 형식·의존성 변경 없음. 근무 규칙과 권한의 서버 검증 책임을 유지합니다. Android 앱 업데이트만 필요하며 서버 배포·데이터 이전은 없습니다.
- 기존 사용자 DB 파일과 로컬 키 설정은 변경하지 않았습니다.

### 되돌리기와 남은 위험

- 문제 발생 시 이번 복원 재시도 수명 보완 커밋을 되돌린 앱을 배포합니다. DB 복구는 필요하지 않습니다.
- 앱 프로세스 종료는 실행 중인 요청도 종료합니다. 다음 프로세스는 기존 최초 세션 복원 경로를 따릅니다. 화면 회전 시 진행 중인 요청을 재전송하지 않습니다.
