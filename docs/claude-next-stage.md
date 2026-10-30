# Claude 인계: 기능별로 하나씩 개발하기

이 문서는 기반 코드가 `dev`와 `main`에 병합된 뒤의 작업 지시서다. Claude Code 또는 Claude Desktop에서 **저장소 루트**를 열고 최신 `dev`에서 시작한다. 개별 파일을 채팅에 복사하거나 GitHub URL만 전달할 필요는 없다. 시작할 때 `git fetch origin`, `git status --short --branch`, `git log --oneline origin/dev -3`으로 실제 브랜치와 미커밋 변경을 확인한다. 기능 브랜치는 항상 최신 `dev`에서 만들고, `main`은 검증된 시연 기준본으로 유지한다.

## 먼저 읽을 자료

1. `AGENTS.md`, `CLAUDE.md`: 저장소 규칙, 브랜치와 커밋 방식.
2. `docs/mvp-scope.md`, 이 문서: 확정된 MVP 범위와 단계별 개발 순서. 오래된 기획과 충돌하면 MVP 범위가 우선한다.
3. `docs/openapi.yaml`, `docs/test-plan.md`: 서버 계약과 인수 기준.
4. `docs/runbooks/git-flow.md`, `docs/ai-development-review-workflow.md`: PR, 검증, Codex 리뷰 흐름.
5. 현재 단계에 관련된 코드만 추가로 읽는다. 첫 단계에는 `backend/src/main/kotlin/com/nurseflow/roster/`, `backend/src/main/kotlin/com/nurseflow/SecurityConfig.kt`, `backend/src/main/resources/schema.sql`, `backend/src/test/kotlin/com/nurseflow/roster/`가 해당한다. Android 화면 단계에는 `android/README.md`와 `android/app/src/main/kotlin/com/nurseflow/app/ui/NurseFlowApp.kt`를 읽는다.

실제 간호사 정보, 휴가 사유, Kakao 비밀키·토큰, 로컬 `build/`·`.env`·`local.properties`는 Claude 프롬프트나 Git에 넣지 않는다.

## 코드 구조 원칙

[TRI-BE의 모듈 구성](https://github.com/wingwogus/TRI-BE/blob/dev/backend/settings.gradle.kts)과 [인증 Controller](https://github.com/wingwogus/TRI-BE/blob/dev/backend/api/src/main/kotlin/com/tribe/api/auth/AuthController.kt)·[Service](https://github.com/wingwogus/TRI-BE/blob/dev/backend/application/src/main/kotlin/com/tribe/application/auth/AuthService.kt)의 API/Application/Domain 경계와 기능별 패키지를 참고한다. NurseFlow 초기 MVP에서는 **백엔드 Gradle 모듈 하나를 유지**하고, `com.nurseflow` 아래에 기능을 먼저 나눈다. `api`, `service`, `dto`를 프로젝트 전체의 최상위 공용 바구니로 만들지 않는다. 필요한 패키지만 실제 코드가 생길 때 추가한다.

```text
com.nurseflow
├── common/
│   ├── error/                    # 공통 Problem 응답·예외 매핑
│   └── security/                 # 공통 JWT/Spring Security 설정
├── auth/
│   ├── api/controller/           # AuthController
│   ├── api/dto/request/          # 인증 요청 DTO
│   ├── api/dto/response/         # 인증 응답 DTO
│   ├── application/service/      # 인증 사용 사례·트랜잭션
│   ├── domain/                   # 인증 도메인 규칙
│   └── persistence/              # 저장이 필요한 경우에만
├── invitation/                   # 초대 발급·철회·1회 수락
├── ward/                         # 병동 소속·역할·권한
├── staff/                        # 간호사·근무 유형 설정
└── roster/
    ├── api/controller/
    ├── api/dto/request/
    ├── api/dto/response/
    ├── application/service/      # 초안·배정·자동생성·확정 사용 사례
    ├── domain/                   # 서버의 필수 근무 규칙
    ├── persistence/              # H2 조회·저장
    └── absence/                  # 근무표에 귀속되는 승인 부재
```

- `invitation`, `ward`, `staff`도 커지면 같은 `api/controller`, `api/dto/request`, `api/dto/response`, `application/service`, `domain`, `persistence` 경계를 따른다. 한 파일에 모든 기능의 Controller·Request·Response를 모으지 않는다. 요청/응답 DTO를 분리하고 DB 행이나 도메인 객체를 그대로 외부 응답으로 노출하지 않는다.
- Controller는 HTTP 입력·출력과 서비스 호출을 담당한다. Service는 한 사용 사례와 트랜잭션 경계를 담당한다. Domain은 근무 필수 규칙을 보관한다. Persistence는 SQL과 저장소 접근을 담당한다. Application과 Domain이 API DTO나 Android 화면 모델을 참조하지 않게 한다.
- 공통 코드는 실제 두 기능 이상에서 재사용할 때만 `common`에 둔다. 새 의존성, 빈 패키지, 인터페이스만 있는 추상 계층을 미리 만들지 않는다.
- 기존 `RosterApi.kt`의 Controller·Request·Response·오류 처리와 큰 `RosterService.kt`를 **동작을 바꾸지 않는 작은 PR**에서 순차적으로 분리한다. 승인 부재 복사, 병동 잠금, revision/멱등성, Night 다음날 Day 금지, 월 경계, 초안/공개 분리는 유지한다. 서버 검증기가 최종 권한이다.
- Android는 API 연결 단계에서 기능별 `feature/auth`, `feature/ward`, `feature/roster` 화면·상태와 `core/network`를 분리한다. 현재 `DemoRoster`의 시연 동작을 보존하고, 인증·서버 공개본 연동 전까지 공유 데이터처럼 표현하지 않는다. 웹도 같은 기능 경계를 사용하되 프레임워크 구조에 맞게 구현한다.

### 추가 편성 규칙: 팀별 경력 구성

병동 안의 소속 팀별로 근무표를 편성한다. 초대 수락·첫 로그인 흐름에서 근무연차와 직급을 입력받고, **병동 관리자가 설정한 연차 구간·직급 기준**으로 경력 단계 1~5를 계산한다. 관리자가 입력 프로필을 확인한 뒤에만 해당 단계를 경력 구성 점수에 사용한다. 프로필 수정 시 재확인 전까지 새 입력은 계산에서 제외한다. 같은 팀·날짜·근무의 확인된 단계 구성이 가능한 한 고르게 되도록 자동배치한다. 이 목표는 필수 규칙과 필요 인원 충원 뒤에 적용한다. 단계가 몰리더라도 배치·확정을 임의로 막지 않고 관리자에게 실제 분포와 미달 이유를 표시한다. `미충원`, `경력 구성 미달`, `프로필 미확인`은 구분한다. 기존 서버에는 팀·경력 단계 데이터와 이 최적화가 아직 없다. 구체적 연차 경계값과 직급 명칭을 코드에 고정하지 않는다. 자세한 제품 기준은 [MVP 범위](mvp-scope.md)를 따른다.

팀 간 지원근무 허용 범위는 아직 미정이다. Opus는 소속 팀과 배치 팀을 구분한 선택지를 제시하고, 사용자 결정 전에 자동배치의 팀 간 이동을 임의로 구현하지 않는다.

## 한 번에 하나의 기능 단위

**한 기능 단위 = 한 브랜치 = 한 검토 가능한 `dev` PR**로 진행한다. 기능을 API, Service, DTO, DB, 클라이언트 계층별로 따로 완료 처리하지 않는다. 해당 기능이 실제로 작동하는 데 필요한 계층과 테스트를 같은 PR에 포함한다. 각 기능을 시작할 때 Opus가 그 기능의 API·DB·권한·실패 시나리오를 검토하고, Sonnet은 합의된 기능 하나만 구현한다. 현재 기능의 PR과 CI·Codex 리뷰가 끝나기 전에는 다음 기능 구현을 시작하지 않는다. 큰 기능은 독립 검증이 가능한 더 작은 동작으로 나눈다.

| 순서 | 기능 단위와 완료 기준 |
| --- | --- |
| 0 | **Opus 설계**: 기능 경계, 현재 클래스 이동표, 의존 방향, PR 순서와 기존 동작 보존 테스트를 `docs/`에 정리. 이 단계에서 구현하지 않는다. |
| 1 | **백엔드 패키지 정리**: roster Controller/DTO/Service/Domain/Persistence 경계를 분리. API 응답·DB 스키마·런타임 동작은 유지하고 회귀 테스트와 CI로 증명한다. 새 의존성은 추가하지 않는다. |
| 2 | **Kakao 인증 서버**: Android·웹 공통 로그인/세션 계약, 토큰 검증, 시연용 첫 관리자 설정, 초대 전 병동 접근 차단을 구현. 가짜 OIDC 공급자를 이용한 인증·실패 테스트를 포함한다. 실제 Kakao 설정 전에는 E2E 성공을 주장하지 않는다. |
| 3 | **관리자 초대 발급·철회**: 역할·병동이 지정된 만료형 1회 토큰을 발급하고 해시만 저장한다. 관리자 권한·철회·만료를 테스트한다. |
| 4 | **초대 수락·병동 소속 생성**: 유효한 초대의 소진, membership/역할·간호사 연결을 한 트랜잭션에서 처리한다. 중복 수락·타 병동 접근·권한 상승을 테스트한다. |
| 5 | **팀·간호사 경력 프로필 API**: 관리자의 팀 소속 지정, 병동별 연차 구간·직급 정책, 간호사의 근무연차·직급 입력, 1~5단계 계산과 관리자 확인·수정, 간호사 목록·비활성화를 구현한다. 미확인 입력은 구성 점수에 넣지 않고 경계값·권한·정책 변경을 테스트한다. |
| 6 | **Android 인증·초대·프로필 화면**: 서버 계약을 연결하고 초대 전/후 및 첫 프로필 입력·오류 상태를 확인한다. |
| 7 | **웹 인증·초대·프로필 화면**: Android와 같은 서버 계약을 사용한다. 웹 프레임워크 선택을 설계 기록에 남긴다. |
| 8 | **근무 유형 관리 API**: Day/Evening/Night 시간 설정과 유효한 시간 구간 검증을 구현한다. |
| 9 | **팀·날짜별 필요 인원 입력**: 월·팀·날짜·근무 유형별 요구 인원과 자격 조건을 입력·수정한다. |
| 10 | **팀별 경력 구성 최적화·진단**: 필수 규칙·충원을 우선하고 같은 팀·날짜·근무의 확인된 1~5 경력 단계 쏠림을 줄인다. 단계 미달·미충원·프로필 미확인 사유를 구분한다. |
| 11 | **Android 근무표 API 연결**: 시연 데이터 경계를 서버 조회·수정으로 교체하고 본인/병동 공개본과 관리자 초안을 분리한다. |
| 12 | **웹 근무표 UI**: 동일 API와 공개 revision으로 관리자·간호사 핵심 화면을 연결한다. |
| 13 | **교차 클라이언트 E2E**: 자동배치→검토→확정→Android·웹의 같은 공개 revision 조회를 검증한다. |

각 PR에는 변경된 OpenAPI·DB/환경 설정, 실행한 테스트, 실행하지 못한 테스트, 배포 영향, 롤백과 남은 위험을 적는다. 병합 직전 Codex 리뷰에서 P1/P2와 실제 실행 오류를 수정하고 CI 통과를 확인한다. `main`에는 기능 브랜치를 직접 병합하지 않는다.

## Claude Opus에 보낼 첫 프롬프트

> 저장소 루트의 `AGENTS.md`, `CLAUDE.md`와 이 문서의 **먼저 읽을 자료**를 확인하고 최신 `dev` 기준으로 작업해 주세요. NurseFlow를 기능별 패키지 아래 Controller, Request DTO, Response DTO, Service, Domain, Persistence로 분리할 설계를 먼저 작성해 주세요. TRI-BE의 경계는 참고하되 백엔드 Gradle 모듈은 하나로 유지합니다. 현재 파일·클래스별 이동표, 의존 방향, 보존해야 할 트랜잭션·병동 잠금·필수 근무 규칙·공개 경계, 먼저 필요한 회귀 테스트, 1번 패키지 정리 PR의 범위와 검증 명령을 `docs/`에 제시해 주세요. 이후 2번 Kakao 인증, 3번 초대 발급·철회, 4번 수락·소속 생성도 각각 별도 PR로 구현할 수 있도록 API·DB 경계를 제안해 주세요. 인증 단계의 Android·웹 코드/콜백과 세션 흐름, 서버 토큰 검증·저장, 첫 관리자 설정, 초대 토큰 해시·만료·철회·재사용 방지, 기존 가상 간호사 연결과 타 병동 접근 차단을 빠뜨리지 마세요. 새로 확정된 병동 내 팀별 편성과 근무연차·직급 기반 1~5 경력 단계 구성 목표도 5·9·10번의 별도 기능 단위로 설계해 주세요. 단계 기준은 병동 관리자가 설정하며 간호사 입력은 관리자 확인 후 편성 점수에 사용합니다. 구체적 연차 경계값과 직급 명칭은 코드에 고정하지 마세요. **이번 요청에서는 설계 문서만 작성하고 구현을 시작하지 마세요.** 실제 데이터나 비밀키를 사용하지 마세요.

## Claude Sonnet에 보낼 단계별 구현 프롬프트

> 승인된 Opus 설계와 이 문서의 **한 번에 하나의 기능 단위** 표를 확인해 주세요. **이번 작업은 표의 1번(백엔드 패키지 정리)만** 진행합니다. 최신 `dev`에서 `chore/...` 브랜치를 만들고, 기존 동작을 보호하는 테스트를 먼저 확인한 뒤 roster의 Controller, Request/Response DTO, Service, Domain, Persistence를 기능 경계에 맞게 분리해 주세요. API 응답, H2 스키마, 서버 필수 규칙, 트랜잭션·병동 잠금, revision/멱등성과 공개 시점은 유지하세요. 새 의존성은 추가하지 마세요. 백엔드 `./gradlew check`와 관련 빌드를 실행하고 PR 설명에 검증·미검증·API/DB·배포·롤백을 기록해 `dev`로 PR을 여세요. **1번 PR의 검토·병합 전에는 2번 이후를 구현하지 말고 결과를 보고한 뒤 멈추세요.** 다음 작업은 새 요청에서 해당 번호를 지정하고 최신 `dev`에서 다시 시작합니다.

1번이 병합된 뒤에는 위 Sonnet 프롬프트의 **단계 번호, 기능, 브랜치 이름, 완료 기준**만 다음 단계에 맞춰 바꿔서 한 번에 하나씩 전달한다. Opus 설계가 바뀌거나 API·DB 경계에 새 결정이 필요하면 그 기능의 구현 전에 설계를 다시 검토한다.

Kakao Developers 앱 설정, Android 패키지/키 해시와 웹 Redirect URI는 실제 로그인 E2E 전에 사용자 계정에서 등록해야 한다. 해당 값과 비밀키는 문서·Git에 쓰지 않는다.
