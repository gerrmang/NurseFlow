# NurseFlow 백엔드 패키지 설계 (Opus Task #0)

상태: **2026-10-30 설계 초안**
범위: `docs/claude-next-stage.md` 0번 — 기능 경계·클래스 이동표·의존 방향·PR 범위·회귀 테스트 설계
구현 금지: 이 문서를 검토·승인한 뒤 1번 PR에서 코드를 변경한다.

---

## 1. 설계 원칙

| 원칙 | 상세 |
|------|------|
| **Gradle 모듈 단일** | `backend/` 하나. 패키지 경계로만 계층을 분리한다. |
| **기능 우선 패키지** | `com.nurseflow.{feature}` 아래에 api / application / domain / persistence를 둔다. |
| **TRI-BE 참고** | TRI-BE의 `api` → `application` → `domain` 모듈 구조를 패키지 수준으로 축소한다. Controller·DTO는 `api/`, 사용 사례는 `application/`, 규칙은 `domain/`, SQL은 `persistence/`에 둔다. |
| **빈 패키지 금지** | 실제 파일이 이동·생성될 패키지만 만든다. |
| **API·DB·런타임 동일** | 1번 PR은 엔드포인트 경로, 요청·응답 JSON 구조, schema.sql, H2 동작을 변경하지 않는다. |
| **새 의존성 없음** | build.gradle.kts를 수정하지 않는다. |

---

## 2. 대상 패키지 구조

```
com.nurseflow
├── NurseFlowBackendApplication.kt          (유지)
│
├── common/
│   ├── error/
│   │   ├── ApiProblem.kt                   # Problem JSON 응답 DTO
│   │   ├── ApiException.kt                 # 범용 HTTP 예외 (기존 RosterApiException 이름 변경)
│   │   └── GlobalExceptionHandler.kt       # @RestControllerAdvice (기존 RosterApiErrors)
│   └── security/
│       └── SecurityConfig.kt               # 기존 SecurityConfig 이동
│
└── roster/
    ├── api/
    │   ├── controller/
    │   │   └── RosterController.kt         # @RestController 엔드포인트만
    │   └── dto/
    │       ├── request/
    │       │   ├── CreateRosterRequest.kt
    │       │   ├── ReplaceRequirementsRequest.kt   # + StaffingRequirement
    │       │   ├── ApprovedAbsenceInput.kt
    │       │   ├── ValidateAssignmentRequest.kt
    │       │   ├── AssignNurseRequest.kt
    │       │   ├── ConfirmRosterRequest.kt
    │       │   └── StartGenerationRequest.kt
    │       └── response/
    │           ├── RosterSummary.kt
    │           ├── RosterView.kt           # + SlotView, AssignmentView
    │           ├── ApprovedAbsenceView.kt  # + AbsenceMutation
    │           ├── AssignmentValidation.kt
    │           ├── GenerationJobResponse.kt  # + StaffingSummary
    │           ├── PublishedCalendar.kt     # PublishedWardCalendar + PublishedPersonalCalendar + PersonalAssignment
    │           ├── RuleDiagnostic.kt
    │           └── Items.kt
    │
    ├── application/
    │   ├── service/
    │   │   └── RosterService.kt            # 사용 사례 조율만 (persistence 호출로 SQL 제거)
    │   └── generation/
    │       └── GenerationCoordinator.kt    # + GenerationExecutorConfig
    │
    ├── domain/
    │   ├── model/
    │   │   ├── ShiftAssignment.kt
    │   │   ├── ApprovedAbsence.kt          # + ApprovedAbsenceKind
    │   │   ├── ShiftKind.kt
    │   │   └── RuleViolation.kt            # + RuleSeverity
    │   └── rule/
    │       └── RosterRuleValidator.kt
    │
    └── persistence/
        ├── entity/
        │   ├── DbRoster.kt
        │   ├── DbSlot.kt
        │   ├── DbNurse.kt
        │   └── ShiftDefinition.kt
        └── repository/
            └── RosterJdbcRepository.kt     # JdbcTemplate 쿼리 전체 이관
```

---

## 3. 클래스 이동표

### 3.1 `com.nurseflow.SecurityConfig` → `com.nurseflow.common.security`

| 현재 위치 | 현재 클래스/함수 | 이동 대상 패키지 | 대상 파일 | 비고 |
|-----------|-----------------|-----------------|----------|------|
| `SecurityConfig.kt` | `SecurityConfig` | `common.security` | `SecurityConfig.kt` | 패키지만 이동 |

### 3.2 `com.nurseflow.roster.api.RosterApi.kt` 분리 (339줄 → 7+ 파일)

| 현재 클래스 | 이동 대상 패키지 | 대상 파일 | 비고 |
|------------|-----------------|----------|------|
| `RosterController` | `roster.api.controller` | `RosterController.kt` | 엔드포인트만 유지. import 경로 변경 |
| `RosterApiErrors` | `common.error` | `GlobalExceptionHandler.kt` | 클래스명 변경. roster 외 기능도 사용 |
| `RosterApiException` | `common.error` | `ApiException.kt` | 클래스명 변경 → `ApiException` |
| `ApiProblem` | `common.error` | `ApiProblem.kt` | 이동만 |
| `CreateRosterRequest` | `roster.api.dto.request` | `CreateRosterRequest.kt` | |
| `StaffingRequirement` | `roster.api.dto.request` | `ReplaceRequirementsRequest.kt` | 같은 파일에 유지 |
| `ReplaceRequirementsRequest` | `roster.api.dto.request` | `ReplaceRequirementsRequest.kt` | |
| `ApprovedAbsenceInput` | `roster.api.dto.request` | `ApprovedAbsenceInput.kt` | |
| `ValidateAssignmentRequest` | `roster.api.dto.request` | `ValidateAssignmentRequest.kt` | |
| `AssignNurseRequest` | `roster.api.dto.request` | `AssignNurseRequest.kt` | |
| `ConfirmRosterRequest` | `roster.api.dto.request` | `ConfirmRosterRequest.kt` | |
| `StartGenerationRequest` | `roster.api.dto.request` | `StartGenerationRequest.kt` | |
| `RosterSummary` | `roster.api.dto.response` | `RosterSummary.kt` | |
| `RosterView` | `roster.api.dto.response` | `RosterView.kt` | `SlotView`, `AssignmentView` 동반 |
| `SlotView` | `roster.api.dto.response` | `RosterView.kt` | RosterView와 동일 파일 |
| `AssignmentView` | `roster.api.dto.response` | `RosterView.kt` | RosterView와 동일 파일 |
| `ApprovedAbsenceView` | `roster.api.dto.response` | `ApprovedAbsenceView.kt` | `AbsenceMutation` 동반 |
| `AbsenceMutation` | `roster.api.dto.response` | `ApprovedAbsenceView.kt` | |
| `AssignmentValidation` | `roster.api.dto.response` | `AssignmentValidation.kt` | |
| `GenerationJob` | `roster.api.dto.response` | `GenerationJobResponse.kt` | `StaffingSummary` 동반 |
| `StaffingSummary` | `roster.api.dto.response` | `GenerationJobResponse.kt` | |
| `PublishedWardCalendar` | `roster.api.dto.response` | `PublishedCalendar.kt` | |
| `PublishedPersonalCalendar` | `roster.api.dto.response` | `PublishedCalendar.kt` | `PersonalAssignment` 동반 |
| `PersonalAssignment` | `roster.api.dto.response` | `PublishedCalendar.kt` | |
| `RuleDiagnostic` | `roster.api.dto.response` | `RuleDiagnostic.kt` | domain → DTO 변환 `from()` 유지 |
| `Items<T>` | `roster.api.dto.response` | `Items.kt` | 제네릭 래퍼. 향후 `common`으로 승격 가능 |

### 3.3 `com.nurseflow.roster.application.RosterService.kt` 분리 (797줄 → 2+ 파일)

| 현재 요소 | 이동 대상 패키지 | 대상 파일 | 비고 |
|----------|-----------------|----------|------|
| `RosterService` (사용 사례 메서드) | `roster.application.service` | `RosterService.kt` | SQL 호출을 repository 호출로 교체 |
| `DbRoster` (private data class) | `roster.persistence.entity` | `DbRoster.kt` | `internal` 가시성 |
| `DbSlot` (private data class) | `roster.persistence.entity` | `DbSlot.kt` | `internal` |
| `DbNurse` (private data class) | `roster.persistence.entity` | `DbNurse.kt` | `internal` |
| `ShiftDefinition` (private data class) | `roster.persistence.entity` | `ShiftDefinition.kt` | `internal` |
| `NewSlot` (private data class) | `roster.persistence.entity` | (RosterJdbcRepository 내부) | 슬롯 생성 전용 |
| `ValidationContext` (private data class) | `roster.application.service` | `RosterService.kt` 내부 유지 | service 전용 |
| JDBC 쿼리 메서드 전체 | `roster.persistence.repository` | `RosterJdbcRepository.kt` | 아래 상세 |

**RosterJdbcRepository로 추출할 메서드** (현재 RosterService 내 private 메서드):

| 메서드 | 설명 |
|--------|------|
| `findRoster(id)` | roster 조회 |
| `mapRoster(rs)` | ResultSet → DbRoster |
| `slots(rosterId)` | roster_slots JOIN shift_types JOIN nurses |
| `absences(rosterId)` | approved_absences 조회 |
| `findNurse(id, wardId)` | 병동 간호사 조회 |
| `memberships(wardId, subject)` | ward_memberships 역할 조회 |
| `lockWard(wardId)` | `SELECT ... FOR UPDATE` 병동 잠금 |
| `activePublished(wardId, month)` | 활성 공개 roster 조회 |
| `increment(id)` | revision+1 |
| `parseMonth(value)` | YYYY-MM 파싱 (유틸) |
| `replay(key, action, payload, type)` | 멱등성 replay 조회 |
| `recordReplay(key, action, payload, response)` | 멱등성 기록 저장 |
| `readGenerationJob(jobId)` | generation_jobs 조회 |
| `jobRosterId(jobId)` | job → roster 연결 조회 |
| `finishGeneration(...)` | generation 결과 업데이트 |
| `generationCancelled(jobId)` | cancel_requested 확인 |
| `initialTimeLimitNanos(jobId)` | time_limit 조회 |
| roster INSERT/UPDATE/DELETE 쿼리들 | createRoster, addAbsence 등에서 직접 호출하는 SQL |

### 3.4 `com.nurseflow.roster.application.GenerationCoordinator.kt` (변경 최소)

| 현재 요소 | 이동 대상 패키지 | 대상 파일 | 비고 |
|----------|-----------------|----------|------|
| `GenerationExecutorConfig` | `roster.application.generation` | `GenerationCoordinator.kt` | 같은 파일 유지 |
| `GenerationCoordinator` | `roster.application.generation` | `GenerationCoordinator.kt` | 패키지만 이동 |

### 3.5 `com.nurseflow.roster.domain.RosterRuleValidator.kt` 분리

| 현재 요소 | 이동 대상 패키지 | 대상 파일 | 비고 |
|----------|-----------------|----------|------|
| `ShiftKind` | `roster.domain.model` | `ShiftKind.kt` | |
| `ApprovedAbsenceKind` | `roster.domain.model` | `ApprovedAbsence.kt` | |
| `RuleSeverity` | `roster.domain.model` | `RuleViolation.kt` | |
| `ShiftAssignment` | `roster.domain.model` | `ShiftAssignment.kt` | |
| `ApprovedAbsence` | `roster.domain.model` | `ApprovedAbsence.kt` | |
| `RuleViolation` | `roster.domain.model` | `RuleViolation.kt` | |
| `RosterRuleValidator` | `roster.domain.rule` | `RosterRuleValidator.kt` | import만 변경 |

---

## 4. 패키지 의존 방향

```
roster.api.controller
  ├──→ roster.api.dto.request
  ├──→ roster.api.dto.response
  ├──→ roster.application.service
  ├──→ roster.application.generation
  └──→ common.error

roster.application.service
  ├──→ roster.domain.model
  ├──→ roster.domain.rule
  ├──→ roster.persistence.repository
  ├──→ roster.persistence.entity
  ├──→ roster.api.dto.request       (입력 DTO — 향후 Command 분리 가능)
  ├──→ roster.api.dto.response      (출력 DTO — 향후 분리 가능)
  └──→ common.error

roster.application.generation
  ├──→ roster.application.service
  └──→ roster.api.dto.request       (StartGenerationRequest)
  └──→ roster.api.dto.response      (GenerationJob)

roster.domain.model
  └── (의존 없음 — 순수 값 객체)

roster.domain.rule
  └──→ roster.domain.model

roster.persistence.repository
  ├──→ roster.persistence.entity
  ├──→ roster.domain.model
  └──→ common.error                 (fail() 예외 던지기)

roster.persistence.entity
  └──→ roster.domain.model          (ShiftKind, ShiftAssignment 변환)

common.error
  └── (의존 없음)

common.security
  └── (Spring Security만 사용)
```

**금지 방향**:
- `domain` → `api`, `application`, `persistence` 참조 금지
- `persistence` → `api` 참조 금지
- `common` → 기능 패키지(`roster`, `auth` 등) 참조 금지

**허용된 임시 결합** (1번 PR 범위):
- `application.service` → `api.dto.request/response`: Service가 API DTO를 직접 반환하는 현재 구조를 1번 PR에서는 유지한다. Command/Result 객체 분리는 향후 auth/invitation 기능 추가 시 평가한다.

---

## 5. 보존해야 할 비즈니스 불변식

1번 PR이 보존을 증명해야 하는 서버 동작:

| 분류 | 불변식 | 현재 구현 위치 |
|------|--------|---------------|
| **필수 근무 규칙** | Night 다음날 Day 금지 (`REST_AFTER_NIGHT`) | `RosterRuleValidator.validate()` |
| | 같은 간호사 근무 시간 중복 금지 (`SHIFT_TIME_OVERLAP`) | `RosterRuleValidator.validate()` |
| | 승인 휴가·반차 시간 겹침 금지 (`APPROVED_LEAVE_OVERLAP`, `APPROVED_HALF_DAY_OVERLAP`) | `RosterRuleValidator.validate()` |
| | 무효 근무 시간 구간 거부 (`INVALID_SHIFT_INTERVAL`) | `RosterRuleValidator.validate()` |
| | 비활성·자격미달 간호사 배치 거부 (`NURSE_INACTIVE`, `QUALIFICATION_MISSING`) | `RosterService.candidateViolations()` |
| **월 경계** | 인접 월 공개 근무·부재와 교차 검증 | `loadValidationContext()`, `draftAbsenceConflicts()`, `validateAbsence()` |
| **트랜잭션·병동 잠금** | 모든 변경 경로에서 `lockWard()` → `SELECT ... FOR UPDATE` | `lockAuthorizedRoster()`, `createRoster()` |
| | revision/ETag 낙관적 병행 제어 (`checkRevision()`) | 모든 mutating 메서드 |
| | 멱등성 키 replay (`replay()` / `recordReplay()`) | 생성·배정·확정·부재 추가·자동배치 시작·취소 |
| **초안/공개 경계** | DRAFT 상태에서만 수정 가능 (`ensureDraft()`) | 모든 mutating 메서드 |
| | 간호사 API는 `PUBLISHED + active=TRUE`만 조회 | `publishedWard()`, `publishedMine()` |
| | 관리자만 DRAFT 조회 (`visibleToAdmin()`) | `getRoster()`, `listRosters()` |
| | 확정 시 기존 active 공개본 비활성화 후 새 공개 | `confirm()` |
| **자동 배치** | 필수 규칙 위반 0건만 적용, 위반 시 전체 폐기 | `processGeneration()` 끝 final validation |
| | locked 배치 보존 옵션 | `processGeneration()` `preserveLocked` |
| | 취소·시간 초과 상태 구분 (`CANCELLED`, `TIMED_OUT`, `PARTIAL`, `COMPLETED`) | `processGeneration()` |
| **부재 복사** | 새 초안 생성 시 기존 공개 부재를 새 roster로 복사 | `createRoster()` |
| **권한** | `WARD_ADMIN` 필수 (초안 관리), `WARD_MEMBER` (공개 조회), 병동 전체 조회 권한 | `requireAdmin()`, `requireMember()`, `requireWardView()` |

---

## 6. 회귀 테스트 계획

### 6.1 기존 테스트 보존

현재 `RosterServiceIntegrationTest` (222줄, 5개 테스트):
1. `confirm rechecks next-month day against newly published night` — 월 경계 Night→Day
2. `concurrent adjacent-month confirms cannot both publish conflicting shifts` — 동시 확정 병동 잠금
3. `confirm rejects draft absence crossing into published prior-month shift` — 부재·공개 근무 교차
4. `generation respects approved leave and publishes only after explicit confirm` — 자동 배치 + 부재 + 미확정
5. `queued generation can be cancelled without changing draft revision` — 취소
6. `same-month replacement draft retains approved leave before new assignments` — 부재 복사 + 재할당

**1번 PR 전에 기존 5개 테스트가 모두 통과해야 하고, PR 후에도 동일하게 통과해야 한다.**

### 6.2 1번 PR에서 추가할 테스트

패키지 이동으로 import만 바뀌기 때문에 행위 변경은 없지만, 다음 테스트를 추가해 구조 분리가 동작을 깨뜨리지 않음을 확인한다:

| 테스트 | 목적 | 분류 |
|--------|------|------|
| `RosterRuleValidatorTest` — Night→Day 거부 | domain.rule 분리 후 validator 독립 동작 확인 | Unit |
| `RosterRuleValidatorTest` — 시간 중복 거부 | 같은 간호사 동시간 배치 | Unit |
| `RosterRuleValidatorTest` — 부재 겹침 거부 | 승인 휴가·반차와 근무 시간 겹침 | Unit |
| `RosterRuleValidatorTest` — 유효한 배치 허용 | 규칙 위반 없는 정상 배치 | Unit |
| `RosterRuleValidatorTest` — 월 경계 Night (10/31 Night → 11/1 Day) | 월 경계 교차 | Unit |
| `RosterJdbcRepositoryTest` — roster CRUD + revision 증가 | persistence 분리 후 SQL 정합성 | Integration |
| `RosterJdbcRepositoryTest` — 멱등성 replay 저장·조회 | replay 로직 분리 후 동작 | Integration |
| `RosterJdbcRepositoryTest` — lockWard FOR UPDATE 실행 | 잠금 쿼리 정상 실행 | Integration |
| 기존 `RosterServiceIntegrationTest` 5개 전체 통과 | 엔드포인트·사용 사례 전체 회귀 | Integration |

### 6.3 빌드 검증 명령

```bash
cd backend
./gradlew clean check
```

CI에서 다음을 확인한다:
- 컴파일 성공 (import 경로 전체 갱신)
- 기존 + 신규 테스트 전체 통과
- `schema.sql` 변경 없음
- `application.yml` 변경 없음
- `build.gradle.kts` 변경 없음

---

## 7. 1번 PR 범위와 순서

**브랜치**: `chore/roster-package-structure` (from latest `dev`)
**PR 대상**: `dev`

### 작업 순서 (각 단계가 컴파일·테스트 통과 상태를 유지)

1. **domain 분리**: `roster.domain.model/` + `roster.domain.rule/`로 enum, data class, validator 이동. import 갱신. `RosterRuleValidatorTest` 추가.
2. **common.error 추출**: `ApiException`, `ApiProblem`, `GlobalExceptionHandler`를 `common.error/`로 이동. `RosterApiException` 타입 별칭 또는 이름 변경. import 갱신.
3. **common.security 이동**: `SecurityConfig` → `common.security.SecurityConfig`. 패키지만 변경.
4. **persistence 추출**: `DbRoster`, `DbSlot`, `DbNurse`, `ShiftDefinition`을 `roster.persistence.entity/`로 이동. JDBC 쿼리 메서드를 `RosterJdbcRepository`로 추출. `RosterService`는 repository 호출로 교체. `RosterJdbcRepositoryTest` 추가.
5. **DTO 분리**: `RosterApi.kt`에서 request/response data class를 개별 파일로 이동. `RosterApi.kt`에는 controller만 남기고 `RosterController.kt`로 이름 변경.
6. **패키지 정리**: `roster.application.service/`, `roster.application.generation/` 하위로 이동. 빈 패키지 제거.
7. **최종 검증**: `./gradlew clean check` 전체 통과 확인. 변경 파일 목록에 `schema.sql`, `application.yml`, `build.gradle.kts`가 없음을 확인.

### PR에 기록할 항목

- 검증: `./gradlew clean check` — 기존 5개 + 신규 N개 테스트 통과
- 미검증: HTTP 요청 E2E (서버 기동 테스트는 CI 범위 밖 — `@SpringBootTest`로 대체)
- API 변경: 없음
- DB 변경: 없음
- 배포 영향: 없음 (패키지 이동만)
- 롤백: `git revert` 1 커밋

---

## 8. 향후 기능 단위의 패키지·API·DB 경계 개요

1번 PR 이후 추가될 기능 단위의 패키지 경계를 미리 정의한다. 각 기능의 세부 API·DB 설계는 해당 기능 구현 전 Opus 검토에서 확정한다.

### 8.1 Task #2 — Kakao 인증 서버

```
com.nurseflow.auth/
├── api/controller/AuthController.kt
├── api/dto/request/    # KakaoLoginRequest, TokenRefreshRequest
├── api/dto/response/   # AuthTokenResponse, CurrentUserResponse
├── application/service/AuthService.kt
├── domain/             # (필요 시 토큰 검증 규칙)
└── persistence/        # sessions 또는 refresh_token 저장
```

- 새 테이블: `users`, `refresh_tokens` (또는 세션)
- SecurityConfig에 Kakao OIDC provider 등록
- 첫 관리자 설정: 환경변수 또는 CLI로 `ward_memberships` 초기 레코드 생성
- 기존 `ward_memberships.subject`를 Kakao sub와 연결

### 8.2 Task #3 — 관리자 초대 발급·철회

```
com.nurseflow.invitation/
├── api/controller/InvitationController.kt
├── api/dto/request/    # CreateInvitationRequest
├── api/dto/response/   # InvitationResponse
├── application/service/InvitationService.kt
├── domain/             # 초대 토큰 생성·해시·만료 규칙
└── persistence/        # invitations 테이블
```

- 새 테이블: `invitations` (ward_id, role, token_hash, nurse_id_link, expires_at, redeemed, revoked)
- 토큰 원문은 응답에만, 서버에는 SHA-256 해시만 저장

### 8.3 Task #4 — 초대 수락·병동 소속 생성

```
com.nurseflow.invitation/  (3번과 같은 패키지)
└── application/service/InvitationRedeemService.kt
```

- 초대 수락 트랜잭션: `users` + `ward_memberships` + `nurses` 원자적 생성/연결
- 기존 `nurses.subject` 컬럼과 연결
- 중복 수락, 자기 권한 승격, 타 병동 접근 차단

### 8.4 Task #5 — 팀·간호사 경력 프로필 API

```
com.nurseflow.staff/
├── api/controller/StaffController.kt
├── api/dto/request/    # NurseProfileInput, TeamAssignmentRequest, CareerPolicyInput
├── api/dto/response/   # NurseProfileResponse, CareerStageResponse
├── application/service/StaffService.kt
├── domain/             # 경력 단계 계산 규칙 (연차·직급 → 1~5)
└── persistence/        # teams, nurse_profiles, career_policies
```

- 새 테이블: `teams` (ward_id, name), `nurse_career_profiles` (nurse_id, years_of_experience, rank, career_stage, confirmed_by, confirmed_at), `career_stage_policies` (ward_id, year_range_start, year_range_end, rank, stage, version)
- 경력 단계 경계값은 DB에 저장 (코드 고정 금지)
- 미확인 프로필은 편성 점수 제외

### 8.5 Task #9 — 팀·날짜별 필요 인원 입력

- 기존 `roster_slots`의 staffing requirement에 팀 소속 필드 추가
- `roster.api.dto.request/` 확장

### 8.6 Task #10 — 관리자 허용형 팀 간 지원근무

```
com.nurseflow.roster/
└── domain/
    └── rule/
        └── CrossTeamSupportRule.kt  # 허용 범위 판정
```

- 새 테이블: `cross_team_supports` (nurse_id, target_team_id, shift_type_or_period, granted_by, revoked_at)
- 후보·수동·자동·확정 경로에서 동일한 서버 판정 사용
- 허용 변경 시 기존 초안 재검증

### 8.7 Task #11 — 팀별 경력 구성 최적화·진단

- `roster.domain.rule/`에 경력 분포 점수 계산 추가
- 필수 규칙·충원 우선, 경력 쏠림은 soft goal
- 미달 사유 구분: `미충원`, `경력 구성 미달`, `프로필 미확인`

---

## 9. 리팩터링과 무관한 파일

다음 파일은 1번 PR에서 변경하지 않는다:

- `backend/build.gradle.kts`
- `backend/settings.gradle.kts`
- `backend/src/main/resources/schema.sql`
- `backend/src/main/resources/application.yml`
- `backend/gradlew`, `backend/gradlew.bat`
- `docs/*` (이 설계 문서 제외)
- `android/*`
- `.github/*`

---

## 10. 위험과 완화

| 위험 | 완화 |
|------|------|
| import 경로 대량 변경으로 인한 컴파일 실패 | 단계별 이동 (4.7절), 각 단계에서 `./gradlew check` |
| `RosterService`에서 persistence 추출 시 트랜잭션 경계 변경 | `@Transactional`은 `RosterService`에 유지. repository는 트랜잭션 없이 호출만 담당 |
| `RosterApiException` → `ApiException` 이름 변경이 테스트에 영향 | 기존 테스트의 import와 catch 구문 일괄 갱신. 예외 필드(status, code, ruleViolations)는 유지 |
| private → internal 가시성 변경 | 같은 Gradle 모듈이므로 `internal`은 접근 가능. 외부 모듈 없으므로 노출 범위 동일 |
