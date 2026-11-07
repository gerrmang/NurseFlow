# NurseFlow Android

Kotlin + Jetpack Compose 앱입니다. B1에서는 Kakao 로그인, NurseFlow 세션 복원·갱신·로그아웃, 서버의 내 병동 목록과 역할 표시를 연결합니다. 소속이 없으면 병동 만들기·초대 수락을 안내합니다. 두 기능의 실행 화면은 각각 B4a·B3a 후속 작업입니다.

기존 Figma 기반 `DemoRoster`는 **가상 근무표 시연 보기 (서버 미연동)**에서만 엽니다. 가상 병동과 근무표의 수정·공개는 서버 또는 다른 사용자에게 공유되지 않습니다. 서버에서 조회한 병동을 가상 근무표와 연결하지 않습니다.

## 로컬 실행

JDK 17 이상과 Android SDK 35가 필요합니다. `android/local.properties`는 Git에서 제외됩니다. 실제 키를 문서나 커밋에 넣지 마세요.

| 로컬 설정 | 용도 |
| --- | --- |
| `sdk.dir` 또는 `ANDROID_HOME` | 설치된 Android SDK 경로 |
| `kakao.nativeAppKey` 또는 환경변수 `KAKAO_NATIVE_APP_KEY` | Kakao 앱의 네이티브 앱 키 |
| `nurseflow.apiBaseUrl` | 디버그 API 주소. 기본 `http://10.0.2.2:8080/` |
| `nurseflow.releaseApiBaseUrl` | 릴리스 HTTPS API 주소. 기본은 연결되지 않는 `https://api.nurseflow.invalid/` |

Kakao Developers에서 Android 패키지 `com.nurseflow.app`, 사용 중인 서명의 키 해시, Kakao 로그인과 OpenID Connect 설정을 확인하세요. 디버그 로그인 실패 화면에는 키 해시 확인 안내가 표시됩니다. SDK 오류 원문·토큰은 로그에 기록하지 않습니다. 백엔드도 같은 앱에 대한 Kakao audience를 허용해야 합니다. [Kakao 공식 Android 문서](https://developers.kakao.com/docs/ko/kakaologin/android)를 참고하세요.

`android/`에서 실행합니다.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

로그아웃된 테스트 기기/에뮬레이터가 연결돼 있으면 `./gradlew.bat :app:connectedDebugAndroidTest`로 실제 Keystore/DataStore의 암호화 저장·세션 복원·폐기를 검사할 수 있습니다. 인증 공급자는 가짜 구현이며 Kakao 키나 실제 계정은 필요하지 않습니다. 기존 저장 세션이 있으면 덮어쓰지 않고 테스트를 건너뜁니다.

기기에 APK를 설치하려면 연결한 기기에서 `./gradlew.bat :app:installDebug`를 실행합니다. 에뮬레이터는 호스트 서버에 `10.0.2.2`로 접속하며, 실기기는 접근 가능한 개발 서버 주소를 설정해야 합니다. HTTP는 디버그 빌드에서만 허용됩니다.

## 세션과 데이터 경계

- Kakao SDK ID 토큰과 매번 생성한 nonce를 `POST /api/v1/auth/kakao/session`에 전달합니다.
- NurseFlow access token은 메모리에만, refresh token은 Android Keystore의 AES-GCM으로 암호화해 DataStore에 저장합니다. 백업·기기 이전에서 앱 데이터를 제외합니다.
- 앱 시작 시 refresh token으로 복원합니다. 오프라인이면 저장된 토큰을 보존하고 재시도 화면을 제공합니다.
- 인증 API와 일반 API의 HTTP 클라이언트를 분리합니다. 일반 GET 조회는 끊긴 유휴 연결을 복구하고, 401은 갱신 후 한 번만 재시도하며 동시 401은 mutex로 갱신을 공유합니다. 인증 API는 매번 새 연결을 사용하고 토큰 회전 요청의 전송 오류 자동 재시도는 끕니다.
- refresh 거절은 로컬 세션을 폐기합니다. 로그아웃 시 메모리·저장 토큰과 화면의 사용자/병동 상태를 제거합니다. 오프라인에서는 서버 측 철회를 보장할 수 없습니다.
- `GET /api/v1/me`와 `GET /api/v1/wards`를 소비합니다. 역할은 서버 응답을 표시하며 근무표 유효성·권한 판단을 대신하지 않습니다.
- 초대 토큰 입력·초대/프로필/병동 생성 API와 서버 근무표 조회는 B1 범위에 포함하지 않습니다.

검증 결과와 실로그인 후속 확인은 [B1 검증 기록](B1-verification.md)에 정리합니다.

## 로컬 서버 연동 기기 회귀 검사

`RealBackendSessionDeviceTest`는 가상 관리자·간호사의 역할 표시, 계정 전환 시 화면 폐기, 실제 access token 만료 뒤 401과 단일 refresh 회전을 검사합니다. Android 화면과 HTTP 클라이언트, Spring 인증 필터·서비스·H2는 실제 구현을 사용하며 Kakao OIDC 공급자만 가짜입니다. 별도 메모리 토큰 저장소와 메모리 DB를 사용하므로 기존 앱 자격정보와 사용자 DB를 덮어쓰지 않습니다.

첫 번째 PowerShell의 `backend/`에서 실행합니다. 서버는 loopback의 18081(API)·18082(테스트 제어)에만 열리고, 기기 검사 종료 또는 10분 뒤 종료됩니다. 이 fixture는 테스트 소스에만 있으며 배포 파일에 포함되지 않습니다.

```powershell
$env:NURSEFLOW_ANDROID_E2E = 'true'
./gradlew.bat test --tests com.nurseflow.support.AndroidSessionE2eServerTest
Remove-Item Env:NURSEFLOW_ANDROID_E2E
```

두 번째 PowerShell의 `android/`에서 실행합니다(`adb`는 Android SDK의 `platform-tools/adb.exe`). 첫 번째 서버의 `http://127.0.0.1:18082/stats`가 응답한 뒤 기기를 연결합니다.

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb reverse tcp:18081 tcp:18081
adb reverse tcp:18082 tcp:18082
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e backendE2e true -e class com.nurseflow.app.core.session.RealBackendSessionDeviceTest com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner
adb reverse --remove tcp:18081
adb reverse --remove tcp:18082
```

이 검사는 2초 토큰 수명과 서버의 기본 시간 오차 허용을 실제로 기다리므로 1분 이상 걸립니다. 일반 기기 테스트에서는 명시적 `backendE2e=true`가 없으면 건너뜁니다. 두 실제 Kakao 계정의 인증 전환을 검증하는 검사는 아닙니다.

## Activity 재생성과 인증 콜백 회귀 검사

`ActivityRotationDeviceTest`는 실제 `MainActivity`·Compose 화면·ViewModel·HTTP 클라이언트를 사용합니다. 로그인 버튼을 누른 뒤 `ActivityScenario.recreate()`로 Activity 인스턴스를 교체하고, 이전 Activity가 파괴된 것도 확인합니다. 이후 Kakao SDK 형식의 성공·취소·오류 콜백을 전달해 로그인 완료 또는 재로그인을 검사합니다. 복원 재시도의 성공·실패도 실제 Activity 재생성 전후로 확인합니다.

외부 SDK 호출만 `KakaoLoginSdk`의 가짜 구현으로 교체합니다. `KakaoLoginClient`의 콜백 변환·취소 처리, 로그인 ViewModel, nonce 생성, 세션 HTTP 요청은 실제 코드입니다. 가짜 HTTP 서버는 기기 loopback에만 열리고 가상 토큰·사용자만 사용합니다. 실제 KakaoTalk·브라우저 인증 화면과 OAuth 딥링크 복귀는 이 검사의 범위에 포함되지 않습니다.

`IsolatedSessionTestRunner`는 테스트 Application을 사용해 시작 시 사용자 저장 세션을 읽거나 갱신하지 않습니다. 앱의 기본 실행은 기존 Application과 SDK 구현을 그대로 사용합니다. 다음 명령으로 실행합니다.

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.nurseflow.app.ui.ActivityRotationDeviceTest com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner
```

PR Verify는 JVM 테스트 실행과 앱·계측 테스트 APK 컴파일을 수행합니다. 기기 계측 테스트 실행은 연결된 테스트 기기에서 별도로 수행합니다.
