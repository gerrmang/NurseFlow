# B3a Android 초대 관리·가입 검증과 PR 초안

## 변경 목적과 내용

병동 관리자가 Android에서 초대를 발급·공유·재발급·철회하고, 초대받은 사용자가 링크로 로그인·미리보기·가입한 뒤 해당 병동의 기존 B9a 공개 근무표로 들어가도록 한다.

관련 이슈: 없음. 기준은 `docs/feature-breakdown.md` §3.1·§4의 B3a다. 작업 브랜치는 최신 `dev`(`23af850`)에서 만든 `feat/android-invite-join`이다. B2·`feat/android-auth-invite`와는 독립이다.

연동 구분: **실서버 연동(`dev` 커밋 `23af850` 기준)**.
- 자동 E2E는 외부 OIDC만 가짜다.
- 실제 Kakao 계정 1개로 한 수동 검증은 아래 [실제 Kakao 로그인 검증](#실제-kakao-로그인-검증-2026-11-10-저녁-1계정)에 있다.

## 확정한 결정 (2026-11-10, 사용자)

- **§7 #6 링크 형식:** 개발용 사용자 스킴 `nurseflow://invite#token=<43자>`와, 사용자가 직접 붙여넣는 입력을 쓴다.
  - 붙여넣기는 스킴·호스트·fragment 토큰 형식을 검증한다. 클립보드는 자동으로 읽지 않는다.
  - **이 스킴은 개발 검증용이며 운영 링크가 아니다.** HTTPS App Links는 운영 도메인과 `assetlinks.json`이 정해진 뒤의 후속 작업이다.
- **§7 #7 대상 표시:** 서버 변경 없이 표시한다.
  - 연결 간호사가 있으면 관리자용 staff 목록의 이름과 `미가입/가입됨`을 보여 준다.
  - 연결 간호사가 없으면 `관리자 초대 · 대상 미지정` 또는 `새 간호사 초대 · 가입 시 기록 생성`이라는 고정 문구를 쓴다.
  - 행 구분은 발급·만료 시각과 초대 ID 끝 4자리로 한다. 메모는 저장하지 않는다.
- **§7 #21 검증 계정:** `backend/src/test`에 테스트 전용 `AndroidInvitationE2eServerTest` 1개를 B3a 범위로 추가한다. 가짜 OIDC 가상 계정은 `admin`·`nurse`·`admin2`·`other`이다. 운영 코드·API·스키마는 바꾸지 않았다.

공통 정책 문서(`feature-breakdown.md` §7 등)에는 이 결정을 반영하지 않았다. 결정 반영 문서 PR에서 따로 다룬다.

## 구현 요약

**계약**
- `InvitationApi`가 A1 경로 6개와 대상 표시용 `GET wards/{id}/staff`를 소비한다.
- 요청 본문 필드:
  - 발급: `role`, `nurseId?` (`WARD_ADMIN`에는 `nurseId`를 넣지 않는다)
  - 미리보기: `token`
  - 가입: `token`, `hospitalJoinedOn?` (`WARD_MEMBER`만 보낸다)
- `rank`, `careerStartedOn`, `expiresInHours`, `role` 필드는 정의하지 않았다.

**쓰기 클라이언트**
- 발급·재발급·철회·가입은 별도 클라이언트로 보낸다. 연결 실패 시 자동 재시도하지 않고, 유휴 연결도 재사용하지 않는다.
- 401은 기존과 같이 한 번 갱신한 뒤 재요청한다. 서버가 처리하지 않은 요청이기 때문이다.

**토큰 생명주기**
- 토큰은 링크 fragment에서만 받아 요청 본문으로 보낸다.
- `InviteToken`·요청 DTO·발급 응답·`IssuedLink`의 `toString()`은 토큰을 가린다. 로그 출력은 없다.
- 대기 중인 초대는 `PendingInvitationStore`(프로세스 메모리)에만 둔다. SavedState·Bundle·DataStore에는 저장하지 않는다.
- `MainActivity`는 링크를 받은 뒤 Activity intent에서 data를 지운다.
- 삭제 시점:
  - 가입 완료
  - 사용자의 명시적 취소
  - 종결 오류 화면의 `닫기`
  - 로그아웃
  - 로그인 계정 변경이나 세션 종료
- 로그인 전에 받은 초대는 로그인 후 이어진다. 로그인 취소·실패 시에도 유지하며, 로그인 화면에 안내와 `초대 취소`를 보여 준다.

**재생성·재시작**
- 회전과 Activity 재생성은 메모리 상태와 세션 ViewModel로 이어진다.
- `savedInstanceState`가 있으면 원래 실행 intent를 다시 처리하지 않는다.
- 프로세스가 재시작되면 대기 초대가 사라지고 사용자가 링크를 다시 연다(의도한 동작이며 실기기 확인은 하지 않음).

**늦은 응답·중복 방지**
- 초대마다 `seq`가 있고, 응답은 시작할 때의 `seq`가 여전히 현재일 때만 반영한다. 새 초대·취소·로그아웃이 늦게 온 응답보다 우선한다.
- 같은 토큰의 링크가 다시 오면 진행 중인 흐름을 유지한다.
- 가입 버튼은 전송 중 비활성화되고 ViewModel이 중복 요청을 무시한다. 관리자 쓰기도 한 번에 하나만 처리한다.

**응답 유실 처리 (자동 재전송 없음)**
- 가입:
  - NETWORK·5xx는 `결과 확인` 상태가 된다. `GET /wards`로 소속 여부를 확인하고, 소속이 없으면 다시 미리보기한 뒤 사용자가 명시적으로 다시 가입한다.
  - `INVITATION_ALREADY_REDEEMED`를 받아도 내 소속에 그 병동이 있으면 성공으로 처리한다.
- 발급·재발급·철회: 결과를 확인하지 못했다고 안내하고 목록을 다시 불러온다. 토큰은 다시 볼 수 없으므로, 새 링크가 필요하면 재발급하라고 안내한다.

**오류 구분**
- 서버 `code`를 먼저 보고 HTTP 상태로 보완한다.
- 구분하는 상태:
  - 만료, 철회, 사용됨, 없음
  - 이미 소속 (초대는 사용되지 않았다고 안내하고 `병동으로 이동`을 제공)
  - 간호사 연결 충돌, 관리자·간호사 기록 충돌, 이미 대기 중인 초대
  - 입사 연월 누락·오류
  - 관리자 권한 없음 (화면을 닫고 병동 목록을 새로 고침)
  - 네트워크·서버 오류
- 서버 `detail`과 토큰은 화면에 표시하지 않는다.

**화면**
- 관리자 병동 카드의 `초대 관리`에서 다음을 제공한다.
  - 미가입 간호사와 그 최신 초대 상태
  - 초대 목록 (상태·역할·대상·발급/만료)
  - 발급: 역할 선택, 간호사 기록 선택은 선택 사항
  - 1회 링크 대화상자: 복사 시 API 33 이상에서 `EXTRA_IS_SENSITIVE`, 공유는 `ACTION_SEND`
  - 재발급·철회 확인 대화상자 (PENDING·EXPIRED에서만)
- 가입 화면:
  - 병동·초대 역할·만료 시각·기존 기록 연결 여부를 보여 준다.
  - `WARD_MEMBER`는 `YYYY-MM`을 입력한다. 형식·미래·1950 이전을 사전 확인하며, 최종 판정은 서버가 한다.
  - `WARD_ADMIN`은 입력 없이 가입한다.
  - 성공하면 병동 목록을 새로 고친 뒤 해당 병동의 B9a 화면으로 들어간다.

**매니페스트**
- `MainActivity`에 `nurseflow://invite` VIEW·BROWSABLE 필터를 추가하고 `launchMode="singleTop"`으로 둔다.
- Kakao `kakao{키}://oauth`는 `AuthCodeHandlerActivity`에 그대로 남는다.

### 계획과 달라진 점

- **launchMode:** 계획에서는 `singleTask`였다. 그러나 `singleTask`는 같은 task 위의 Activity(진행 중인 Kakao 로그인 화면 등)를 지울 수 있고, `ActivityScenario` 기반 기존 기기 테스트와도 맞지 않았다(첫 실행에서 실패 확인). 그래서 `singleTop`으로 바꿨다.
  - 실행 중인 화면은 `onNewIntent`로 링크를 받는다. 이는 아래 기기 테스트에서 같은 인스턴스로 확인했다.
  - 다른 앱의 task에서 새 인스턴스가 생겨도 같은 메모리 초대를 공유한다.
- **프로세스 복원 시 처리:** 계획은 복원된 실행 intent를 다시 받아도 된다고 했다. 실제 구현은 `savedInstanceState`가 있으면 다시 처리하지 않는 쪽으로 더 보수적으로 했다. 완료하거나 취소한 링크가 다시 열리는 것을 막기 위해서다.

## 검증 결과 (2026-11-10, Claude)

모든 기기 검사는 읽기 전용·임시 데이터로 새로 부팅한 `Pixel_10`(Android 17, `emulator-5556`, `-read-only -wipe-data`)에서 실행했다. 사용자 DB·저장 세션·실제 개인정보는 쓰지 않았다.

**JVM·빌드·Lint**
- `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: `BUILD SUCCESSFUL`.
- JVM은 XML 기준 68개 통과, 실패·오류·건너뜀 0. 기존 41개에 신규 27개를 더했다.
  - `InviteLinkTest` 3
  - `PendingInvitationStoreTest` 3
  - `InvitationApiTest` 4
  - `JoinInvitationViewModelTest` 11
  - `InvitationAdminViewModelTest` 6
- Lint: 오류 0, 기존 경고 33개(B1 기준과 같음).

**`InvitationApiTest`의 무재시도 검사 보강**
- 쓰기 클라이언트를 일부러 `retryOnConnectionFailure(true)`로 바꾸는 변형 확인을 했다.
- 처음 테스트(새 연결에서 끊김)는 변형에도 통과해 차이를 잡지 못했다.
- 테스트를 "앞선 성공 요청이 남긴 연결 위에서 끊김"으로 고친 뒤 변형에서는 실패하고 원래 코드에서는 통과함을 확인했다. 변형은 되돌렸다.

**`:app:connectedDebugAndroidTest` (최종 코드)**
- `BUILD SUCCESSFUL`. XML 기준 28개 중 22개 통과, 6개 opt-in 건너뜀, 실패·오류 0.
- 건너뛴 6개: `LogoutRestartDeviceTest` 2단계, `RealBackendSessionDeviceTest`, `PublishedRosterDeviceTest`, `OAuthReturnDeviceTest`, 신규 `InvitationJoinDeviceTest`.
- Gradle 콘솔의 34개는 건너뜀을 중복으로 센 값이다.
- 신규 `InviteDeepLinkDeviceTest` 4개가 통과했다. 실제 매니페스트·`MainActivity`·Compose·HTTP를 쓰고 서버는 MockWebServer다.
  - 매니페스트가 `nurseflow://invite`를 `MainActivity`로만 연결한다.
  - 콜드 링크: 로그인 전 안내만 표시하고 병동명은 추측하지 않는다. intent의 data가 제거된다. 로그인하면 미리보기가 나오고, Activity를 재생성해도 미리보기를 다시 요청하지 않는다. 입사 연월 누락 오류 → 입력 → 가입 → B9a 진입. 요청 경로 어디에도 토큰이 없다.
  - 웜 링크: 실행 중인 같은 인스턴스가 `onNewIntent`로 받는다. 로그아웃하면 초대가 삭제된다.
  - 만료 링크와 형식 오류 링크가 각각 다른 화면을 보여 준다.
- 기존 B1 `ActivityRotationDeviceTest` 8개를 포함한 비 opt-in 검사가 모두 통과해 `singleTop` 변경에 따른 회귀가 없었다.

**Kakao OAuth 복귀 (opt-in)**
- `OAuthReturnDeviceTest` (`-e oauthReturnProbe true`, 로컬 Kakao 키 설정 빌드): **`OK (1 test)`, 3.909초**.
- 새 초대 스킴 필터가 Kakao OAuth 거절 딥링크의 `AuthCodeHandlerActivity` 경로와 충돌하지 않았다. 키 값은 출력하지 않았다.

**실서버 E2E: `InvitationJoinDeviceTest`(`-e inviteE2e true`) + `AndroidInvitationE2eServerTest`**
- 기기 **`OK (1 test)`, 16.526초**, 테스트 status 0. 서버 Gradle **`BUILD SUCCESSFUL`**.
- 실제 Spring 초대 서비스·HTTP와 H2 메모리 DB를 썼다. Kakao만 `FakeOidcProvider`이고, link base는 테스트 속성 `nurseflow://invite`다.
- 시나리오:
  1. admin이 `초대 관리`에서 미가입 간호사 기록을 연결한 WARD_MEMBER 초대를 발급하고 1회 링크를 받는다. `재발급`하면 새 링크가 나오고 원본은 `철회됨`이 된다.
  2. nurse(소속 없음)가 원본 링크를 열면 철회 화면이 나온다. 재발급 링크를 열면 기존 기록 연결 미리보기 → `2020-03` 입력 → 가입 → B9a 진입이 이어지고 소속 역할은 `WARD_MEMBER`다. 같은 링크를 다시 열면 사용됨 화면이다.
  3. admin 목록에 `가입 완료`와 `가상 미가입 간호사 · 가입됨`이 보이고 미가입 섹션은 사라진다. admin은 WARD_ADMIN 초대를 발급한다. API로 만든 두 초대 중 하나는 픽스처 제어로 만료시키고, 다른 하나는 UI로 철회한다. 목록에 `만료됨`·`철회됨`이 표시된다.
  4. nurse가 관리자 초대로 가입을 시도하면 이미 소속 화면이 나온다. 철회한 링크는 철회 화면이다.
  5. 로그아웃 상태에서 관리자 링크를 열면 병동명 없이 로그인 안내만 나온다. admin2로 로그인하면 흐름이 이어지고 `초대 역할: 관리자` 미리보기 → 입사 연월 없이 가입 → `NURSE_IDENTITY_REQUIRED` 안내(B9a 기존 처리)가 나온다. 소속 역할은 `WARD_ADMIN`이다.
  6. other 계정에서는 사용된 링크가 사용됨 화면, 만료시킨 링크가 만료 화면이며 소속은 생기지 않는다.
- 첫 실행은 테스트가 행 제목 전체(`철회됨 · 간호사 #…`)를 정확히 비교하려다 실패했다. 테스트만 부분 일치로 고치고 새 메모리 DB 픽스처로 다시 실행했다. 앱 코드 변경은 없었다.
- 그 뒤 관리자 권한 거부 후 재진입 처리를 수정했고(아래 자체 검토), 최종 APK로 다시 실행해 위 결과를 얻었다.

**백엔드**
- `./gradlew.bat test` (E2E 환경변수 없음): `BUILD SUCCESSFUL`. XML 기준 135개, 실패·오류 0, 건너뜀 5.
- 건너뛴 5개는 opt-in 기기 픽스처 3개를 포함하며, 신규 `AndroidInvitationE2eServerTest`도 기본 실행에서 건너뜀을 확인했다.
- 운영 코드는 변경하지 않았다.

**자체 검토 수정**
- 서버가 관리자 권한을 거부해 화면이 닫힌 뒤 같은 병동 초대 관리에 다시 들어가면, 남아 있던 거부 상태로 곧바로 다시 닫히는 문제가 있었다.
- `acknowledgeForbidden()`으로 고쳐 재진입하면 오류와 `다시 시도`를 보여 주게 했다. JVM 테스트에 이 경우를 추가했다.

**기타**
- `git diff --check`: 통과했다. 줄바꿈 변환(LF→CRLF) 경고만 있었다. 이 검사는 추적 중인 파일의 diff만 본다.

**실행하지 않은 것**
- GitHub CI: 커밋·푸시 전이므로 실행하지 않았다.

## 실제 Kakao 로그인 검증 (2026-11-10 저녁, 1계정)

**구성**
- 소스: 첫 커밋 `3b916a2`와 같은 앱 소스로 빌드한 debug APK를 설치했다.
- 기기: 창이 있는 `Pixel_10`(Android 17, `emulator-5556`)을 `-read-only -wipe-data`로 띄웠다. 임시 데이터이며, KakaoTalk 없이 Chrome만 있다.
- 백엔드: WSL 사본 `~/nf-b3a`, B2와 분리된 새 임시 H2 DB에서 실행했다.
  - Kakao REST 키와 Client Secret은 사용자가 만든 `~/.nurseflow-kakao.env`(권한 600)를 `source`로만 불러왔다.
  - Android 네이티브 앱 키는 스크립트가 `android/local.properties`에서 읽어 `NURSEFLOW_KAKAO_AUDIENCES`로 넘겼다. 키 값은 출력하지 않았다.
  - 세션 비밀값은 무작위로 만들었다. `NURSEFLOW_INVITE_LINK_BASE=nurseflow://invite`.
- Kakao 계정: 사용자의 실제 계정 1개. 로그인·동의와 관리자 화면 조작은 사용자가 직접 했다. 실명 등 개인정보는 기록하지 않았다.
- 가상 데이터: 실제 계정이 하나뿐이라 초대를 발급하는 쪽은 가상 관리자로 두고, 다음을 DB에 직접 넣었다.
  - 가상 병동 2개, 가상 관리자 2명, 미가입 간호사 기록 1건
  - 초대 5건: 간호사 연결, 간호사 추가, 만료, 철회, 관리자
  - 초대 토큰은 WSL 파일(600)에만 저장했고 DB에는 해시만 넣었다. 링크는 출력하지 않고 `adb am start`로만 기기에 보냈다.

**결과**

| 시나리오 | 결과 | 확인 방법 |
| --- | --- | --- |
| 로그아웃·앱 종료 상태에서 링크(콜드) | 로그인 화면에 초대 안내와 `초대 취소`만 표시, 병동명 없음 | 화면 덤프 |
| 실제 Kakao 로그인 뒤 초대 재개 | 미리보기에 병동·간호사 역할·기존 기록 연결 안내 표시 | 사용자 |
| 입사 연월 공란으로 가입 | 필수 입력 안내 | 사용자 |
| 가상 값 `2020-03`으로 가입 | 성공, 해당 병동 B9a 화면 "아직 확정된 근무표가 없습니다" | 사용자 |
| 사용된 링크 다시 열기 | "이미 사용된 초대예요…" | 화면 덤프 |
| 만료 링크 | "만료된 초대예요…" | 화면 덤프 |
| 철회 링크 | "철회된 초대예요…" | 화면 덤프 |
| 이미 소속된 병동의 다른 초대로 가입 | "이미 이 병동에 소속되어 있어요. 이 초대는 사용되지 않았어요." → `병동으로 이동`으로 병동 진입. DB에서 그 초대는 `PENDING` 유지 | 화면 덤프·DB |
| 관리자 초대로 두 번째 병동 가입 | "관리자 초대는 입사 연월 입력 없이 가입합니다." → 가입 → 간호사 기록 없음 안내(B9a). 병동 목록에 `관리자` 역할과 `초대 관리` 표시 | 화면 덤프 |
| 초대 관리: 목록·대상 표시·1회용/24시간 안내 | 정상 | 사용자 |
| 간호사 초대 발급과 1회 링크 안내·링크 형식 | 정상 | 사용자 |
| 링크 공유 | 공유 시트 호출 정상(실제 전송 안 함) | 사용자 |
| 발급 후 목록, 재발급(원본 철회됨·새 초대 사용 대기), 철회(안내·상태 변경) | 정상. DB에서 해당 병동 간호사 초대 2건이 `REVOKED` | 사용자·DB |
| 붙여넣기에 `abc` 입력 | "올바른 초대 링크가 아니에요." | 사용자 |
| 대기 초대가 있는 상태에서 프로세스 종료 후 재실행 | 홈 → `am kill`(PID 변경 확인) → 런처 재실행. 세션은 복원되고 대기 초대 없이 병동 목록 표시 | 화면 덤프 |
| 대기 초대가 있는 상태에서 세션 종료 | 가입 화면을 띄운 뒤 서버에서 refresh 토큰을 폐기하고 백엔드를 새 비밀값으로 재시작 → 가입 시도 → 401 뒤 갱신 거절로 로그아웃. 로그인 화면에 초대 안내 없음. 가입 요청은 서버에서 처리되지 않아 초대는 `PENDING` 유지 | 화면 덤프·DB |

**서버·기기 점검**
- DB(개수와 상태만 조회)
  - 실제 계정의 소속은 `가상 초대 병동` WARD_MEMBER, `가상 관리 병동` WARD_ADMIN이다.
  - 연결된 간호사 기록은 1건이다.
  - 초대 상태는 위 시나리오와 일치한다.
- 백엔드 로그
  - WARN·ERROR 0건.
  - 시드 초대 토큰, `Bearer `, JWT 접두어, `id_token`, `refresh_token`, `client_secret`은 0회.
- 기기 logcat
  - 시드 토큰은 8줄 있었고 모두 `adbd`가 기록한 검증용 `adb shell am start` 명령이다. 앱이 남긴 로그가 아니다.
  - 앱과 다른 프로세스에서 나온 줄은 0건이다.
  - adbd 밖에서 `token=<43자>`에 걸린 2줄은 Google Play 서비스 `PhenotypeModule`의 자체 필드였고 초대 링크가 아니다.
  - 앱에서 발급한 링크가 logcat에 남았는지는 위 방식으로 0건임을 확인했다.
  - 처음 집계는 토큰 목록을 불러오는 명령의 따옴표 처리 오류로 0건이 잘못 나왔다. 스크립트 파일로 다시 세어 위 결과를 얻었다.
- 미리보기의 만료 시각은 기기 시간대(UTC)로 표시됐다. 새로 만든 에뮬레이터의 기본 시간대다.

**이번에도 확인하지 못한 것 (미검증)**
- 두 실제 Kakao 계정 간 전환(실제 계정 1개). 계정 전환 격리는 가짜 OIDC E2E와 기기 테스트로만 확인했다.
- 복사 성공과 클립보드 민감 정보 가림. 미리보기가 표시되지 않아 확인하지 못했다.
- 실제 메신저(KakaoTalk 등)에서 `nurseflow://` 링크를 누를 수 있게 표시하는지. 링크는 adb로만 전달했다.
- KakaoTalk 앱 로그인 경로. 이 기기에서는 브라우저 계정 로그인만 확인했다.
- 화면에서 직접 로그아웃할 때 대기 초대가 지워지는지. 가입 화면에는 로그아웃 버튼이 없으므로 세션 종료 경로로 확인했고, 명시적 로그아웃은 기기 테스트로 확인했다.
- 물리 기기, 운영 서버, HTTPS App Links, GitHub CI.

## 재현 명령

첫 번째 터미널에서 `backend/`로 이동해 실행한다. 기기 검사가 `/finish`를 호출할 때까지 최대 10분 기다린다.

```powershell
$env:NURSEFLOW_INVITE_E2E = 'true'
.\gradlew.bat test --tests com.nurseflow.support.AndroidInvitationE2eServerTest --rerun --no-daemon
Remove-Item Env:NURSEFLOW_INVITE_E2E
```

두 번째 터미널에서 `android/`로 이동해 실행한다. 별도 임시 에뮬레이터를 쓰고, 제어 포트 18086이 열린 뒤 시작한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
$env:ANDROID_SERIAL = 'emulator-5556'
.\gradlew.bat :app:connectedDebugAndroidTest
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 reverse tcp:18085 tcp:18085
adb -s emulator-5556 reverse tcp:18086 tcp:18086
adb -s emulator-5556 shell am instrument -w -r -e inviteE2e true -e class com.nurseflow.app.feature.invite.InvitationJoinDeviceTest com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner
adb -s emulator-5556 reverse --remove-all
```

성공은 기기의 `OK (1 test)`와 서버의 `BUILD SUCCESSFUL`을 모두 확인해 판정한다. 실패 후에는 새 픽스처 서버로 다시 시작한다.

## API·데이터·설정 변경

- 운영 API·DB 스키마·서버 설정 변경은 없다. Android가 기존 A1 계약을 소비한다.
- Android BuildConfig에 `INVITE_LINK_BASE`를 추가했다.
  - debug는 `nurseflow://invite`이며, 서버가 `inviteUrl`을 주지 않을 때만 쓴다.
  - release는 빈 값이다. 이때는 링크를 만들지 않고 안내만 한다.
- 매니페스트: `MainActivity`의 `singleTop`과 초대 스킴 필터.
- 테스트 전용 opt-in: `NURSEFLOW_INVITE_E2E`, `inviteE2e`. 픽스처 포트는 loopback 18085·18086이다.

## 배포 영향과 되돌리기

Android 앱 업데이트만 필요하며 서버 배포나 데이터 이전은 없다.

되돌릴 때는 B3a 커밋을 되돌린 앱을 배포한다. 매니페스트 필터가 빠지면 링크 수신도 꺼진다. 백엔드 테스트 파일은 opt-in이며 배포물에 들어가지 않는다.

## 문서와 코드 차이 (이번 PR에서 고치지 않음, 후속 기록)

- OpenAPI는 WARD_ADMIN 가입에 `hospitalJoinedOn`을 받지 않는다고 적었지만, 서버는 받은 뒤 무시한다. 클라이언트는 보내지 않는다.
- redeem 오류 목록에 `INVALID_HOSPITAL_JOIN_MONTH`(400)와 `USER_NOT_FOUND`(401)가 빠져 있다.
- `backend/README.md`의 `expiresInHours`(기본 72h) 설명이 오래됐다. 실제 서버는 24시간 고정이고 이 필드를 400으로 거부한다.
- §7 #8(재발급 시 이전 링크 철회)은 문서상 미결이지만, 구현과 E2E로 철회됨을 확인했다.

## 남은 위험과 후속 작업

- **실제 Kakao 로그인:** 브라우저 계정 로그인 1계정으로 초대 재개·가입·오류·관리자 화면을 확인했다(위 절). 두 계정 전환과 KakaoTalk 앱 로그인 경로는 미검증이다.
- **클립보드:** 복사 성공과 민감 정보 가림을 확인하지 못했다.
- **실제 메신저:** KakaoTalk 등이 `nurseflow://` 링크를 누를 수 있는 링크로 보여 주는지 확인하지 않았다. 붙여넣기 입력으로 대체할 수 있다.
- **운영 링크:** HTTPS App Links(운영 도메인, `assetlinks.json`, `autoVerify`)와 B3b 웹 링크 통합(§7 #6 운영 부분)은 후속이다.
- **기기:** 물리 기기와 프로세스 재시작(`am kill`) 시 초대가 사라지는 동작은 실행해 확인하지 않았다.
- **오류 화면 캡처:** PR 증빙용 캡처는 아직 만들지 않았다. 가상 데이터로만 만든다.
- **목록 동기화:** 초대 목록의 `PENDING`은 서버가 조회 시점에 계산한다. 화면을 연 채 만료 시각이 지나도 새로고침 전까지는 `사용 대기`로 보인다.

## 최종 확인

- [x] 작업 브랜치 `feat/android-invite-join`, 대상 `dev`
- [x] 권한·초대 유효성·역할 부여는 서버 판정
- [x] 토큰을 path·query·로그에 넣지 않음, 가상 데이터만 사용
- [x] 검증 결과·생략한 검사·재현 절차 기록
- [x] 첫 커밋·푸시 (`feat/android-invite-join`)
- [x] 실제 Kakao 로그인 기반 초대 흐름 검증 (1계정. 두 계정 전환·클립보드·실제 메신저는 미검증)
- [ ] PR 생성 (별도 승인 필요)
- [ ] GitHub CI 확인과 Codex 리뷰
