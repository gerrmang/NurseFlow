# Claude 인계: 현행 작업 기준

## 작업 순서의 기준

현재 기능 순서·선행 관계·담당자·파일 소유 범위·완료 조건은 [`docs/feature-breakdown.md`](feature-breakdown.md)를 따른다. 진행 중인 브랜치와 PR, 검증 결과는 [`docs/work-status.md`](work-status.md)에서 확인한다. 이 문서에는 아키텍처 원칙만 둔다. 과거의 번호별 실행표나 프롬프트를 현재 작업 지시로 사용하지 않는다.

저장소 루트에서 작업을 시작한다. 변경 전 `AGENTS.md`를 읽고 `git status --short --branch`, `git worktree list`, 최신 원격 `dev`를 확인한다. 최신 `dev`에서 목적에 맞는 작업 브랜치를 사용하며 `main`이나 `dev`에서 직접 개발하지 않는다. 미커밋 변경을 보존하고 다른 worktree의 작업을 덮어쓰지 않는다.

## 먼저 읽을 자료

1. `AGENTS.md`, `CLAUDE.md`: 저장소 공통 규칙과 작업 시작 절차.
2. `docs/feature-breakdown.md`의 현재 기능 행과 §2 담당·파일 소유 원칙, `docs/work-status.md`: 이번 작업의 범위·선행 조건·진행 현황.
3. `docs/mvp-scope.md`, `docs/monthly-roster-rules-and-two-person-plan.md`, `docs/hospital-roster-policy-settings.md`: 확정된 제품 범위와 편성 정책.
4. `docs/openapi.yaml`, `docs/test-plan.md`, `docs/backend-error-auth-dto-conventions.md`: API 계약, 인수 기준, 백엔드 코드 경계.
5. 리뷰 작업은 `docs/ai-development-review-workflow.md`도 읽는다.
6. 현재 기능에 필요한 코드와 테스트만 확인한다. Android 인증 작업의 구현·검증 기록은 [`android/README.md`](../android/README.md)와 [`android/B1-verification.md`](../android/B1-verification.md)에 있다.

## 백엔드 구조 원칙

백엔드 Gradle 모듈 하나를 유지하고 `com.nurseflow` 아래 기능별 패키지로 나눈다. 프로젝트 전체에 공용 `api`, `service`, `dto` 바구니를 만들지 않는다. 필요한 패키지는 실제 코드가 생길 때 추가한다.

```text
com.nurseflow
├── common/
│   ├── error/                    # 공통 오류 응답·예외 매핑
│   └── security/                 # 공통 JWT/Spring Security 설정
├── auth/
│   ├── api/controller/
│   ├── api/dto/request/
│   ├── api/dto/response/
│   ├── application/service/
│   ├── domain/
│   └── persistence/              # 필요한 경우에만
├── invitation/
├── ward/
├── staff/
└── roster/
    ├── api/controller/
    ├── api/dto/request/
    ├── api/dto/response/
    ├── application/service/
    ├── domain/                   # 필수 근무 규칙
    ├── persistence/
    └── absence/                  # 근무표에 귀속되는 승인 부재
```

- Controller는 HTTP 경계와 서비스 호출, Service는 사용 사례와 트랜잭션, Domain은 필수 규칙, Persistence는 SQL과 저장소 접근을 담당한다.
- 요청·응답 DTO를 분리하고 DB 행이나 도메인 객체를 외부 응답으로 직접 노출하지 않는다. Application과 Domain은 API DTO나 Android 화면 모델에 의존하지 않는다.
- 공통 코드는 실제 여러 기능에서 재사용될 때만 `common`에 둔다. 새 의존성, 빈 패키지, 사용처 없는 추상화를 미리 만들지 않는다.
- 기존 roster 동작을 수정할 때 승인 부재 복사, 병동 잠금, revision·멱등성, 서버 근무 규칙, 초안과 공개본의 분리를 보존한다. 서버 검증기가 근무 유효성의 최종 권한이다.

## 기능 단위와 검증

기능 분해표의 한 기능 단위를 한 작업 브랜치와 검토 가능한 PR로 구현한다. 기능의 완료 조건에 필요한 API·DB·클라이언트·테스트를 함께 다루고, 선행 기능이나 계약 결정이 미완료면 breakdown에 적힌 조건을 따른다. 같은 파일을 다른 작업자와 동시에 수정하지 않는다.

백엔드 PR은 해당 OpenAPI 계약과 실제 응답을 함께 확인한다. Android PR은 로컬 필수 검사, CI에서 실제 실행하는 검사, 연결 기기에서 실행한 계측 검사를 구분해 결과와 생략 이유를 기록한다. 웹 작업은 프레임워크 선택과 CI 검사를 해당 기능의 breakdown에 반영한다.

희망 오프는 승인 연차·반차와 구분한다. 간호사는 마감 전에 희망 오프를 제출할 수 있고, 관리자는 대리 입력·검토·확정·반려를 수행한다. 마감 이후 예외와 실제 배치 금지 여부는 breakdown 및 서버 계약을 따른다.

## 안전한 자료 취급

가상 직원 데이터만 사용한다. 실제 간호사 정보, 휴가 사유·건강 정보, Kakao 비밀키·토큰, 로컬 `build/`, `.env`, `local.properties`를 프롬프트나 Git에 넣지 않는다. 검증 보고에는 비밀 값이나 개인 식별 정보를 기록하지 않는다.
