# NurseFlow

간호사 3교대 근무표를 작성·검토·공개하는 초기 시연 프로젝트입니다.

## 저장소 구성

| 경로 | 내용 | 실행 환경 |
| --- | --- | --- |
| [`backend/`](backend/) | Kotlin + Spring Boot API, 로컬 H2 시연 DB | IntelliJ IDEA, JDK 21 |
| [`android/`](android/) | Kotlin + Jetpack Compose 앱 | Android Studio, Android SDK 35 |
| [`docs/`](docs/) | 제품 범위, 편성 규칙, API 계약, 개발 안내 | 편집기 |
| `web/` | 웹 클라이언트 | 아직 구현되지 않음 |

## 현재 상태

- **백엔드:** 로그인·세션, 병동 소속·초대, 팀·프로필, 기본 근무표 작성과 공개 API가 있습니다. 일부 기능은 아직 구현되지 않았습니다. 특히 근무 유형 설정 API가 없어, 근무표 기능을 쓰려면 로컬 H2에 근무 유형을 준비해야 합니다.
- **Android:** 현재 `dev`의 근무표 화면은 `DemoRoster` 시연 데이터를 앱 안에서 사용합니다. 입력한 변경은 서버에 저장되거나 다른 사용자와 공유되지 않습니다.
- **웹:** 아직 없습니다.
- **외부 연동:** 로그인 테스트는 가짜 OIDC 공급자를 사용합니다. 실제 Kakao 앱 설정을 이용한 로그인 E2E는 확인되지 않았습니다. 운영 DB·호스팅도 시연 범위에 포함되지 않습니다.

자세한 기능 범위는 [MVP 안내](docs/mvp-scope.md), 편성 정책은 [월간 편성 규칙과 2인 분담](docs/monthly-roster-rules-and-two-person-plan.md), 서버 설정과 구현 상태는 [백엔드 안내](backend/README.md)에서 확인할 수 있습니다.

## 로컬 실행

1. **백엔드:** `backend/`를 IntelliJ IDEA에서 열고 JDK 21을 사용합니다. `backend/`에서 `./gradlew test`로 검사합니다. 서버 실행에는 `NURSEFLOW_SESSION_SECRET`과 `NURSEFLOW_KAKAO_AUDIENCES` 설정이 필요합니다. 환경 변수와 데이터 준비 방법은 [백엔드 안내](backend/README.md)를 참고하세요.
2. **Android:** `android/`를 Android Studio에서 열고 SDK 35로 동기화한 뒤 에뮬레이터에서 `app` 구성을 실행합니다. 빌드는 `android/`에서 `./gradlew :app:assembleDebug`로 할 수 있습니다.
3. **에뮬레이터 연결:** Android 에뮬레이터에서 개발 컴퓨터의 백엔드는 `10.0.2.2:8080`으로 접근합니다. 실기기는 개발 컴퓨터의 네트워크 주소를 사용합니다. HTTP는 디버그 빌드에서만 사용하고, 배포 환경에는 HTTPS를 적용합니다.

## 브랜치와 변경 절차

기능 작업은 최신 `dev`에서 짧은 작업 브랜치를 만들어 진행하고, `dev` 대상 PR로 검토받습니다. 릴리스는 검증 후 `dev`에서 `main`으로 PR을 엽니다. 세부 규칙은 [Git 흐름 안내](docs/runbooks/git-flow.md)를 참고하세요.
