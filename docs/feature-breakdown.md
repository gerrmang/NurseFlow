# NurseFlow 기능별 개발 breakdown (초안)

상태: **2026-11-07 실행 계획. breakdown은 PR #29로 병합됐고 A1은 PR #27에서 진행 중이다.**
현재 구현 상태표 기준 커밋: `origin/dev` `5ca51cc` (PR #26 병합 시점). 새 기능을 시작할 때는 더 최신 `dev`의 코드 근거를 다시 확인한다.

읽은 자료: `AGENTS.md`, `docs/runbooks/git-flow.md`, `docs/pre-breakdown-scope-2026-11-04.md`, `docs/mvp-scope.md`, `docs/requirements.md`, `docs/monthly-roster-rules-and-two-person-plan.md`, `docs/hospital-roster-policy-settings.md`, `docs/openapi.yaml`, `docs/test-plan.md`, `docs/ai-development-review-workflow.md`, 그리고 `backend/`·`android/`·`.github/` 코드.

판정 기준: 문서에 적혀 있다는 이유로 완료로 보지 않았다. **코드와 테스트가 있는 것만 "구현 완료"**로 적었다.

| 상태 | 뜻 |
| --- | --- |
| 구현 완료 | `dev` 코드와 테스트가 최신 기획을 충족한다 |
| 일부 구현 | 기반은 있으나 최신 기획의 일부가 빠졌다 |
| 기존 구현 수정 필요 | 구현은 있으나 최신 기획과 **반대되거나 바꿔야 하는** 동작이 있다 |
| 미구현 | `dev`에 코드가 없다 (문서·OpenAPI·미병합 브랜치에만 있을 수 있음) |

---

## 1. 현재 구현 상태표

### 1.1 계정·병동·초대

| 기능 | 현재 코드 근거(파일·클래스) | 상태 | 최신 기획과 차이 | 필요한 수정 |
| --- | --- | --- | --- | --- |
| Kakao ID 토큰 → NurseFlow 세션 | `auth/api/controller/AuthController`, `auth/application/service/AuthService`·`KakaoIdTokenVerifier`·`SessionTokenService`, `users`·`auth_refresh_tokens` 테이블, `KakaoAuthHttpTest`(13) | 구현 완료 | 실제 Kakao 앱 E2E는 미검증(가짜 OIDC 공급자로만 테스트) | 클라이언트 연동 PR에서 실제 앱 키로 E2E 확인 |
| 웹용 Kakao 코드 교환·HttpOnly 쿠키 | `dev`에 없음. 미병합 브랜치 `feat/auth-web-session`(`c4153a1`)에만 있음 | 미구현 | 웹 로그인의 서버 경로가 없다 | 최신 `dev` 기준으로 다시 만들어 PR |
| 본인 정보·소속 병동 목록 | `MeController`(`GET /me`), `WardController.myWards`(`GET /wards`), `WardJdbcRepository` | 일부 구현 | 역할은 반환하지만 병원명 표시가 없다(병원 개념이 없음). "최근 선택 병동"은 클라이언트 몫 | 병동 생성 PR에서 병원 표시 정보 추가 |
| 실제 병원 검색 | 없음 | 미구현 | 외부 공식 API 연동, 키 서버 보관, 빈 결과·장애 구분 필요 | 신규 서버 기능 |
| 최초 관리자 병동 생성 | `WardController.bootstrap`, `WardService.bootstrapWard`, `common/config/BootstrapProperties`, `bootstrap_state` 테이블, `WardBootstrapHttpTest`(4) | 기존 구현 수정 필요 | 운영자 코드(`NURSEFLOW_BOOTSTRAP_CODE`)가 있어야 하고 서버 전체에서 **1회만** 생성 가능. 병원 선택 없음 | 병원 검색 결과 + 병동명으로 누구나 자기 병동을 만드는 계약으로 교체하고 코드 방식 제거 |
| 초대 발급·목록·철회 | `WardInvitationController`, `InvitationService`, `CreateInvitationRequest`, `ward_invitations`, `InvitationStatus`, `WardInvitationHttpTest`(6) | 기존 구현 수정 필요 | 유효기간 기본 72시간·1~168시간 선택(`expiresInHours`). 재발급 동작 없음. 관리자 초대는 대상 표시가 없음 | 24시간 고정, 대상별 상태·재발급 지원 |
| 초대 미리보기·수락 | `InvitationRedemptionController`, `InvitationRedemptionService`, `RedeemInvitationRequest`, `InvitationRedemptionHttpTest`(13) | 기존 구현 수정 필요 | 수락 시 `careerStartedOn`과 `rank`를 **함께** 받는다(둘 다 또는 둘 다 없음). 원자적 소속 생성·역할 고정·동시 수락 차단은 충족 | 입사 연월만 받도록 변경. 직급 입력 제거 |
| 구성원 권한 변경·비활성화 | 없음 (`mvp-scope.md`에 `PATCH /wards/{wardId}/memberships/{userId}` 계약 후보만 있음) | 미구현 | 추가 관리자 지정·비활성화·마지막 관리자 보호가 없다 | 신규 서버 기능 |
| 감사 기록 | `common/audit/AuditLog`, `audit_events` (WARD_BOOTSTRAPPED, INVITATION_*, TEAM_*, STAFF_*, PROFILE_*, CAREER_POLICY_UPDATED) | 일부 구현 | 이벤트 이름만 남고 경력 조정 **값 이력**은 없음 | 경력 조정 이력 테이블 추가 |

### 1.2 팀·간호사·경력

| 기능 | 현재 코드 근거 | 상태 | 최신 기획과 차이 | 필요한 수정 |
| --- | --- | --- | --- | --- |
| 팀 목록·생성·이름/순서 수정·삭제 | `TeamController`, `StaffService.createTeam/updateTeam/deleteTeam`, `ward_teams`(sort_order, active) | 일부 구현 | 팀을 하나씩 만든다. "팀 수 설정"은 없다(활성 팀 수 = 팀 개수). 소속 간호사가 있는 팀은 삭제 거부 | 팀 수 설정 UX를 기존 API로 할지, 일괄 API를 둘지 결정 필요 |
| 간호사 목록·가상 기록 생성·팀 지정 | `StaffController`(`/staff`, `/staff/{id}/team`), `StaffService.staff`(정렬: 팀 순서 → 입사 연월 빠른 순 → 이름) | 구현 완료 | 기본 정렬은 "팀별 경력자 순"과 일치. 정렬에 미확인 입력값도 쓰인다 | 관리자 팀 내 순서가 생기면 정렬 기준 갱신 |
| 경력 단계 기준·직급 목록 | `TeamController.careerPolicy`, `staff/domain/CareerPolicy`(기본 12/36/72/120개월), `ward_career_policies.ranks` | 기존 구현 수정 필요 | 직급 목록이 간호사 입력용으로 쓰인다 | 직급을 간호사 입력에서 분리(관리자 전용 또는 제거, 결정 필요) |
| 간호사 본인 경력 입력 | `StaffController` `GET/PUT /my-profile`, `CareerProfileRequest`(careerStartedOn + rank 필수) | 기존 구현 수정 필요 | 직급이 필수 | 입사 연월만 받음 |
| 관리자 확인·조정 | `StaffController` `PUT /staff/{id}/profile`, `POST .../profile/confirm`, `nurses.confirmed_*` 컬럼 | 일부 구현 | 제출값을 그대로 확인할 뿐 **편성 단계 직접 조정·팀 내 순서·선임 자격**이 없다. 조정 이력 없음 | 관리자 조정 필드·이력 추가, 원본 입사 연월 보존 |
| 편성 대상 판정 | `RosterJdbcRepository.activeNurses`(병동의 `active` 간호사 기록 전부), `InvitationRedemptionService`(관리자 초대 수락자는 간호사 기록이 없어야 함) | 기존 구현 수정 필요 | 계정이 연결되지 않은 가상 기록과 소속이 끊긴 사용자의 기록도 자동배치 대상이 된다. 관리자는 간호사 기록이 없어서 결과적으로 제외된다 | 대상 = 초대 수락·활성·`WARD_MEMBER`·비관리자로 서버 판정. 시연용 가상 기록 처리 방식 결정 필요 |

### 1.3 근무 설정·규칙

| 기능 | 현재 코드 근거 | 상태 | 최신 기획과 차이 | 필요한 수정 |
| --- | --- | --- | --- | --- |
| 근무 유형(D/E/N 시각) | `shift_types` 테이블(code, kind, start/end, spans_midnight), `ShiftKindConstraintMigration`. **Controller 없음.** `docs/openapi.yaml`에는 `/wards/{wardId}/shift-types` CRUD가 정의돼 있음 | 미구현 (문서만) | 관리자가 입력할 API가 없어 DB 직접 입력으로만 생성된다. 휴게·실제 인정시간·월 목표 환산시간 컬럼 없음 | 근무 유형 CRUD와 시간 필드 구현, OpenAPI와 실제 일치 |
| 특수 근무 코드·반차 구간 정책 | 없음 | 미구현 | 관리자 정의 코드, 반차 배치 불가 구간·환산시간 | 후속 정책 PR (초기 범위는 결정 필요) |
| 승인 VY·반차 등록 | `RosterController` `/rosters/{id}/absences`, `approved_absences`(APPROVED_LEAVE / APPROVED_HALF_DAY, 시간 구간), `RosterRuleValidator`의 겹침 판정 | 일부 구현 | 구간 겹침 금지는 동작. VY라는 코드 이름·환산시간·반차 표준 구간이 없다. 같은 달 새 초안에 공개본의 부재를 복사함 | 시간 환산과 VY 상태(연속근무 계산에서 별도 처리) 연결 |
| 필요 인원 입력 | `PUT /rosters/{id}/requirements`(날짜·근무 유형·인원·자격) → `roster_slots` | 일부 구현 | **팀 단위가 아니다.** 선임 필요 칸 개념 없음 | 팀별 수요와 팀·근무별 선임 1명 칸 추가 |
| 필수 규칙 검증기 | `roster/domain/rule/RosterRuleValidator`: 구간 오류, 휴가·반차 겹침, 동시간 중복, Night→다음 날 Day, Evening→다음 날 Day. `RosterService.candidateViolations`: 비활성, 자격 부족. 인접 월 공개 근무 포함 | 일부 구현 | 연속근무 5일, 허용 근무 유형, 확정 희망 오프, 팀 간 지원 허용, 선임 자격 칸이 없다 | 규칙별 PR로 같은 검증기에 추가 (후보·수동·자동·확정 공통) |
| 근무 형태(일반/DE 전담/N 전담) | 없음 | 미구현 | | 신규 |
| 연속근무 최대 5일 | 없음 | 미구현 | 월 경계 포함, `OF`/`SOF`만 초기화, `VY`는 근무일도 초기화일도 아님 | 신규 규칙 |
| 희망 오프(제출·확정·반려·마감·예외) | 없음 | 미구현 | | 신규 |
| 휴일 달력·월 목표시간·개인별 시간 | 없음 | 미구현 | | 신규 |
| SOF·Night 전담 순환 | 없음 | 미구현 | | 신규 (결정 사항 많음) |
| 팀 간 지원근무 허용 | 없음 | 미구현 | | 신규 |
| 경력 구성·공정성 진단 | 없음 (자동배치는 근무 수·Night 수 오름차순 탐욕 배치) | 미구현 | | 신규 |
| 설정 안내·생성 전 점검 | 없음 | 미구현 | 필수 설정 누락 시 생성 차단, 선임 수요/공급 사전 진단 | 신규 |

### 1.4 근무표 작성·공개

| 기능 | 현재 코드 근거 | 상태 | 최신 기획과 차이 | 필요한 수정 |
| --- | --- | --- | --- | --- |
| 초안 생성·조회 | `RosterController` `/wards/{id}/rosters`, `/rosters/{id}`, `RosterService.createRoster` | 일부 구현 | 같은 달 초안 1개, 공개본이 있으면 **부재만 복사**하고 배치는 복사하지 않는다 | 공개 후 수정 초안은 공개 배치를 복사해 시작 |
| 후보 검증·수동 배치·해제 | `/assignments/validate`, `/slots/{id}/assignment` PUT/DELETE, ETag revision, `mutation_replays` 멱등성 | 구현 완료 (현재 규칙 범위) | 새 규칙은 검증기 확장으로 자동 반영돼야 함 | 규칙 PR마다 이 경로 테스트 추가 |
| 자동배치 작업 | `GenerationCoordinator`, `RosterService.processGeneration`(탐욕 배치, 고정 배치 보존, 취소·시간 제한, 최종 재검증 실패 시 전체 폐기), `generation_jobs` | 일부 구현 | 팀·선임·연속근무·희망 오프·목표시간·경력 구성 미반영 | 엔진 확장 PR |
| 확정·공개 | `RosterService.confirm`: 빈 근무표 거부, 미충원이면 `acknowledgeUnfilledCount`가 실제 수와 같아야 함, 필수 규칙 위반 시 거부, 기존 공개본 비활성화 후 공개 | 일부 구현 | 미충원 **수만** 확인하고 기록하지 않는다. 월 목표시간 미달 확인 없음. 선임 미충원 구분 없음 | 진단별 명시 확인과 확인 기록을 공개 revision에 연결 |
| 공개 근무표 조회(전체/본인) | `/wards/{id}/published-rosters`, `/published-rosters/mine`, `requireWardView`·`requireMember` | 구현 완료 | 공개본이 없으면 `404 PUBLISHED_ROSTER_NOT_FOUND`. 간호사 화면의 "아직 확정된 근무표가 없습니다"는 클라이언트가 이 코드로 표시해야 함 | 클라이언트 처리, 필요 시 응답 계약 보완 |
| 이전/현재 공개본 비교·변경 표시 | 이전 공개본 행은 `rosters.active=FALSE`로 남지만 조회 API가 없다 | 미구현 | | 공개 이력·비교 API |
| 동시 편집·멱등성 | `checkRevision`(ETag), `lockWard`, `mutation_replays`, `RosterJdbcRepositoryTest`·`RosterServiceIntegrationTest` | 구현 완료 | | 새 쓰기 API도 같은 방식 적용 |

### 1.5 클라이언트·CI

| 기능 | 현재 코드 근거 | 상태 | 최신 기획과 차이 | 필요한 수정 |
| --- | --- | --- | --- | --- |
| Android 앱 | `android/app/.../MainActivity.kt`, `ui/NurseFlowApp.kt`(942줄, `DemoRoster` 가상 데이터) | 미구현 (시연 화면만) | 네트워크·로그인·초대·서버 근무표 없음. 미병합 `feat/android-auth-invite`(`95cf33d`)에 Kakao 로그인·세션·초대 가입·내 경력 화면이 있음(직급 입력, 코드 없는 병동 생성 포함) | 그 브랜치의 로그인 관련 커밋을 최신 `dev`로 옮기고 직급 입력·병동 생성 부분은 새 계약으로 다시 작성 |
| 웹 클라이언트 | `web/` 없음 | 미구현 | 프레임워크 미확정 (`mvp-scope.md`: 구현 선택) | 신규 |
| CI | `.github/workflows/verify.yml`: `backend ./gradlew test`, `android ./gradlew :app:assembleDebug` | 일부 구현 | 웹 빌드·테스트 없음. 문서는 `check` 기준이나 CI는 `test`. GitHub의 필수 검사·브랜치 보호 설정은 확인되지 않음 | 웹 작업 시 CI 작업 추가 |
| OpenAPI 계약 | `docs/openapi.yaml` | 일부 구현 | shift-types는 정의만 있고 구현 없음. 병원 검색·병동 생성·24시간 초대·입사 연월 단독·희망 오프·확인 기록·공개 비교는 미정의 | 각 서버 PR에서 해당 경로만 갱신 |

> 참고: 테스트 수(`@Test` 기준, `5ca51cc`): Kakao 인증 13, 병동 생성 4, 초대 발급 6, 초대 수락 13, 팀·경력 9 + 단계 계산 3, roster 검증기 15, roster 통합 7, roster 저장소 5, 요청 검증 6, 마이그레이션 1, 오류 처리 2.

---

## 2. 담당과 파일 소유 원칙

| 담당 | 역할 | 소유 경로(이 사람만 수정) |
| --- | --- | --- |
| **A** | 서버·편성 엔진·API 계약 | `backend/**`, `docs/openapi.yaml`, `backend/README.md` |
| **B** | Android·웹 클라이언트·E2E·시연 데이터 스크립트·CI | `android/**`, `web/**`(신규), `scripts/**`(신규), `.github/workflows/**`, `README.md`, `android/README.md`, `CLAUDE.md`, `docs/claude-next-stage.md` |

- 공통 정책 문서(`mvp-scope.md`, `pre-breakdown-scope-2026-11-04.md`, `requirements.md`, `monthly-roster-rules-and-two-person-plan.md`, `hospital-roster-policy-settings.md`, `test-plan.md`, 이 문서)는 **결정 반영 문서 PR**에서만 수정한다. 한 번에 한 사람만 수정하고, 기능 PR에서는 건드리지 않는다.
- **API 계약 먼저**: A는 서버 PR을 열 때 `docs/openapi.yaml` 변경을 첫 커밋으로 올리고 B에게 소비자 검토를 받는다. 계약 합의 뒤 B는 계약 기반 목업으로 화면을 진행할 수 있다.
- **목업 완료와 실연동 완료를 구분한다.** B의 PR 설명에 "목업 기반" 또는 "실서버 연동(`dev` 커밋 ○○○ 기준)"을 적는다. 클라이언트 기능 PR은 해당 서버 PR이 `dev`에 병합된 뒤 실서버 연동까지 확인하고 병합하는 것이 원칙이다. 목업만으로 병합할 때는 화면이 시연 데이터임을 표시하고 "실연동 후속 PR"을 별도 행으로 남긴다.
- 쌓인(stacked) 브랜치를 만들지 않는다. 앞 PR이 병합된 뒤 최신 `dev`에서 다음 브랜치를 만든다.
- 클라이언트 PR은 기본적으로 **Android와 웹을 따로** 연다(같은 기능의 a/w 쌍).

---

## 3. 2인 작업 분담표

표기: `A1`처럼 담당자+번호. 선행 PR이 병합돼야 착수(또는 "계약 합의 후 목업 착수").
열 "API·DTO·DB 변경"은 A의 서버 PR에서 확정한다. B 행에는 소비하는 계약을 적었다.

### 3.1 1단계: 계정·병동·초대

| 순서/PR 가칭 | 사용자에게 보이는 완료 결과 | 담당자 | 선행 PR | 변경 영역·파일 소유 | API·DTO·DB 변경 | 핵심 권한·근무 규칙 | 인수 테스트 | 병합 조건 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 · A1 `feat/invitation-24h-career-month` | 초대는 24시간 뒤 만료된다. 간호사는 가입 때 병원 입사 연월만 입력하고 실제 경력 정보는 관리자가 설정한다 | A | 없음 | `backend/invitation/**`, `backend/staff/**`(프로필 입력부), `openapi.yaml` | 초대 생성에서 `expiresInHours` 제거, 재발급 API 추가, 가입·본인 프로필에는 `hospitalJoinedOn`만 입력, `careerStartedOn`·직급은 관리자 경로로 분리 | WARD_MEMBER는 가입 시 병원 입사 연월 필수. 재발급하면 이전 미사용 링크를 철회한다. WARD_ADMIN은 간호사 기록을 만들지 않는다. | 24시간 만료 경계, 만료·철회·이미 사용된 초대의 재발급, 병원 입사 연월 누락·형식 검사, 실제 경력 정보 분리 저장 | CI 통과, Codex P1/P2 해결, B의 계약 검토 |
| 1 · B1 `feat/android-kakao-session` | Android에서 Kakao로 로그인하고 내 병동 목록(역할 표시)을 본다. 소속이 없으면 "병동 만들기 / 초대 수락" 안내 | B | 없음 (서버 API는 이미 있음) | `android/**` | 소비: `/auth/kakao/session`, `/auth/session/refresh`, `/auth/logout`, `/me`, `GET /wards` | refresh token은 Keystore로 암호화해 저장, 401이면 한 번만 갱신, 로그아웃 시 캐시 폐기 | JVM 단위 테스트(세션 갱신·오류 매핑), 에뮬레이터 실로그인 시연 기록, `assembleDebug` | CI, 실서버 연동 확인(로컬 서버), 키·토큰이 로그·커밋에 없음 |
| 2 · A2 `feat/auth-web-session` | 웹 로그인용 서버 경로가 생긴다 (화면은 B2) | A | 없음 | `backend/auth/**`, `openapi.yaml`, `application.yml` 설정 키 | `POST /auth/kakao/code`(코드 교환), refresh는 HttpOnly 쿠키, CSRF 대책 | Redirect URI 허용 목록, state·nonce 검증 | 가짜 OIDC 공급자로 성공·state 불일치·재사용 거부 | CI, B의 계약 검토. 기존 `feat/auth-web-session` 브랜치는 참고만 하고 최신 `dev`에서 다시 작성 |
| 2 · B2 `feat/web-foundation-session` | 웹에서 Kakao 로그인 후 내 병동 목록을 본다 | B | A2(실연동), 웹 프레임워크 결정 | `web/**`(신규), `.github/workflows/verify.yml`(웹 작업 추가) | 소비: A2, `/me`, `/wards` | 쿠키 기반 refresh, 토큰을 localStorage에 두지 않음 | 웹 단위 테스트, 빌드, 로그인→목록 시연 | CI에 웹 작업 추가·통과 |
| 3 · A3 `feat/hospital-search` | (서버) 병원 이름으로 검색하면 실제 공식 데이터의 병원명·주소·식별자가 나온다 | A | 없음 (키 발급 필요) | `backend/hospital/**`(신규, 검색 전용), `application.yml`, `openapi.yaml` | `GET /api/v1/hospitals/search?query=&page=` → 이름·주소·외부 식별자·출처. 외부 키는 환경변수. DB 변경 없음 | 로그인 사용자만 호출, 병원 권한 개념 없음. 외부 응답의 필요한 필드만 반환 | 가짜 외부 서버로 정상·빈 결과·장애(시간 초과·5xx)·동명 병원, 실제 키 수동 호출 기록 | CI, 실제 API 호출 확인 기록, 키 미노출 |
| 4 · A4 `feat/ward-self-create` | 로그인한 사용자가 병원을 고르고 병동명을 입력해 자기 병동을 만들고 관리자가 된다 | A | A3 | `backend/ward/**`, `backend/common/config/BootstrapProperties.kt`(삭제), `schema.sql`, `openapi.yaml` | `POST /wards` 요청 교체: `{hospital{source, externalId, name, address}, wardName, timeZone?}` + `Idempotency-Key`. `wards`에 병원 표시 스냅샷 컬럼. `bootstrapCode`·`BOOTSTRAP_*` 오류 코드 제거. `GET /wards`에 병원명 | 생성자만 그 병동의 `WARD_ADMIN`(간호사 기록은 만들지 않음). 같은 병원을 골라도 기존 병동 권한 없음 | 생성·재시도 멱등·동시 생성·타 병동 접근 거부·기존 H2 파일 DB 업그레이드 | CI, 결정 사항 1~3(§7) 반영 |
| 5 · B3a `feat/android-invite-join` | 관리자는 초대 발급·복사·공유·상태 확인·재발급·철회, 간호사는 링크를 열어 로그인→병동/역할 확인→입사 연월 입력→가입 | B | A1, B1 | `android/**` | 소비: 초대 API(A1) | 초대 화면은 관리자만. 링크 토큰은 URL fragment로만 받고 로그에 남기지 않음 | 만료·철회·이미 소속·이미 사용된 링크 오류 화면, 딥링크 수신 테스트, 실서버 가입 시연 | A1 병합 후 실연동 확인 |
| 5 · B3b `feat/web-invite-join` | 웹에서 같은 초대 관리·가입 흐름 | B | A1, B2 | `web/**` | 소비: A1 | 같음 | 같음(웹) | 같음 |
| 6 · B4a `feat/android-ward-create` | Android에서 병원 검색→선택→병동명 입력→병동 생성, 생성 뒤 설정 안내 첫 화면(항목 목록만) | B | 계약: A3·A4 합의 후 목업 / 실연동: A4 병합 | `android/**` | 소비: A3, A4 | 검색 빈 결과와 외부 장애를 다르게 표시, 동명 병원은 주소 표시, 생성 재시도 시 같은 `Idempotency-Key` | 목업·실서버 각각 시연, 오류 상태 단위 테스트 | 실연동 확인 |
| 6 · B4b `feat/web-ward-create` | 웹 같은 흐름 | B | A3·A4, B2 | `web/**` | 같음 | 같음 | 같음 | 같음 |

### 3.2 2단계: 구성원·팀·경력·대상자

| 순서/PR 가칭 | 사용자에게 보이는 완료 결과 | 담당자 | 선행 PR | 변경 영역·파일 소유 | API·DTO·DB 변경 | 핵심 권한·근무 규칙 | 인수 테스트 | 병합 조건 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 7 · A5 `feat/staff-admin-career-adjust` | 관리자가 실제 경력 정보와 직급을 확인하고, 각 팀 안에서 경력순을 직접 정한다 | A | A1 | `backend/staff/**`, `schema.sql`, `openapi.yaml` | 병원 입사 연월과 실제 경력 정보를 분리한다. 관리자가 편성 단계·선임 자격·팀 내 순서와 변경 이력을 설정한다. | 팀 안에서 위에 놓인 간호사가 더 선임이다. 병원 입사 연월이 늦더라도 수동 순서가 우선한다. | 실제 경력 프로필 검사, 드래그 순서 저장·재조회, 날짜순과 수동 순서가 다를 때 수동 순위 적용, 권한 검사 | CI, 결정 사항 10·11 적용 |
| 8 · A6 `feat/membership-admin` | 관리자가 구성원 목록을 보고 추가 관리자 지정·해제·비활성화를 한다 | A | A4 | `backend/ward/**`(구성원 부분), `openapi.yaml` | `GET /wards/{id}/members`, `PATCH /wards/{id}/memberships/{userId}`, 감사 기록 | 자기 권한 승격 금지, **마지막 관리자 해제·비활성화 금지**, 비활성 구성원은 즉시 접근 불가 | 권한 조합 표 테스트, 마지막 관리자 보호, 동시 변경 | CI, 결정 사항 4 반영 |
| 9 · A7 `feat/roster-eligibility` | 자동배치·충원 계산에 초대 수락한 활성 간호사만 들어가고 관리자는 빠진다. 관리자가 간호사별 포함/제외 이유를 본다 | A | A6 | `backend/roster/**`(대상 조회), `openapi.yaml` | 대상자 조회 API(포함/제외 사유), `activeNurses` 판정 변경 | 대상 = 초대 수락·활성·`WARD_MEMBER`·비관리자. 소속 해제·비활성 시 대상 제외 | 가상 기록·비활성·관리자·타 병동 각각 제외 사유 | CI, 결정 사항 9 반영 |
| 10 · B5a `feat/android-staff-team` | Android 관리자: 팀 수·이름 설정, 간호사 배치, 팀 안의 경력순 드래그 정렬, 실제 경력 정보·선임 자격·구성원 관리 | B | A5, A6, B1 | `android/**` | 소비: 팀·staff·A5·A6 | 관리자만 정렬·수정 가능. 순서는 입사 연월과 달라도 그대로 적용 | 순서 변경·저장·재조회, 권한 오류, 동시 수정(409) | 실연동 확인 |
| 10 · B5b `feat/web-staff-team` | 웹 같은 기능 | B | A5, A6, B2 | `web/**` | 같음 | 같음 | 같음 | 같음 |
| 11 · B6 `chore/demo-seed-script` | 한 번의 명령으로 가상 병동(A/B팀, 경력 1~5단계 간호사, 근무 유형)을 API로 채운다 | B | A5, A7 (A8 이후 갱신) | `scripts/demo/**`(신규) | 공개 API만 사용, 서버 코드 변경 없음 | 실제 이름·실데이터 금지 | 스크립트 실행 후 조회 결과 확인 | 가상 데이터만 사용 |

### 3.3 3단계: 근무 설정·필수 규칙

| 순서/PR 가칭 | 사용자에게 보이는 완료 결과 | 담당자 | 선행 PR | 변경 영역·파일 소유 | API·DTO·DB 변경 | 핵심 권한·근무 규칙 | 인수 테스트 | 병합 조건 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 12 · A8 `feat/shift-type-settings` | 관리자가 D/E/N의 시작·종료·자정 통과·휴게·실제 인정시간·월 목표 환산시간을 입력한다 | A | A4 | `backend/roster/**` 또는 신규 `backend/policy/**`, `schema.sql`, `openapi.yaml`(기존 shift-types 정의 갱신) | `/wards/{id}/shift-types` CRUD 구현, `shift_types`에 휴게·인정·환산 컬럼, 병동 정책 revision | 관리자만 수정. 사용 중인 근무 유형 변경 시 기존 초안은 재검증 대상 표시 | 구간 검증·자정 경과·월 경계, 기존 초안 영향 표시 | CI, OpenAPI와 구현 일치 |
| 13 · A9 `feat/team-demand-senior` | 관리자가 팀·날짜·근무별 필요 인원을 입력하면 각 팀의 D/E/N마다 선임 필요 칸 1개가 생기고 주니어로 채워지지 않는다 | A | A5, A8 | `backend/roster/**`, `schema.sql`, `openapi.yaml` | `roster_slots.team_id`, 선임 필요 표시, 요구 입력 DTO에 팀, 검증 코드 `SENIOR_REQUIRED` | 선임 칸은 선임 자격자만. 불가하면 미충원(선임) | 후보·수동·자동·확정 4경로에서 같은 거부, 팀 미지정 간호사 처리 | CI |
| 14 · A10 `feat/work-pattern-rule` | 간호사별 근무 형태(일반/Day·Evening 전담/Night 전담)에 맞지 않는 근무는 배치되지 않는다 | A | A7 | `backend/staff/**`(형태 저장), `backend/roster/domain/rule/**`, `openapi.yaml` | `nurses.work_pattern`, 규칙 `SHIFT_KIND_NOT_ALLOWED` | 4경로 동일 | 형태별 허용·거부, 월 경계 | CI |
| 15 · A11 `feat/consecutive-work-rule` | 6일 연속 근무가 되는 배치가 거부된다 | A | A10 | `backend/roster/domain/rule/**`, `RosterService` | 규칙 `CONSECUTIVE_WORK_LIMIT`, 상한값은 병동 정책(기본 5는 결정된 값) | `OF`/`SOF`만 초기화, `VY`는 근무일도 초기화일도 아님, 전·다음 달 공개 근무 포함 | 월 경계·VY 끼임·고정 배치 사례 | CI |
| 16 · B7a `feat/android-schedule-settings` | Android 관리자: 근무 유형 시간, 팀별 필요 인원, 승인 VY·반차 등록, 간호사 근무 형태 입력 | B | A8, A9, A10 | `android/**` | 소비: A8~A10, 기존 absences | 관리자 전용, 사유 입력 없음 | 입력 검증 오류 표시, 영향 표시 | 실연동 확인 |
| 16 · B7b `feat/web-schedule-settings` | 웹 같은 기능 | B | 같음, B2 | `web/**` | 같음 | 같음 | 같음 | 같음 |

### 3.4 4단계: 희망 오프·시간·팀 지원·Night

| 순서/PR 가칭 | 사용자에게 보이는 완료 결과 | 담당자 | 선행 PR | 변경 영역·파일 소유 | API·DTO·DB 변경 | 핵심 권한·근무 규칙 | 인수 테스트 | 병합 조건 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 17 · A12 `feat/off-requests` | 간호사가 마감 전 희망 오프를 내고, 관리자가 대리 입력·확정·반려하며, 마감 뒤 예외를 접수한다. 확정된 날은 배치되지 않는다 | A | A11 | 신규 `backend/offrequest/**`, `backend/roster/domain/rule/**`, `schema.sql`, `openapi.yaml` | 희망 오프 테이블(상태·입력 주체·판단 시각), 월별 마감, 예외 접수, 규칙 `CONFIRMED_OFF_REQUEST` | 미확정 = 선호(미반영 사유), 확정 = 배치 금지. 마감 뒤 간호사 직접 수정 불가. 영향받는 초안 재검증 표시 | 상태 전이, 마감 경계, 권한, 4경로 거부, 공개본 유지 | CI, 결정 사항 15 |
| 18 · A13 `feat/target-hours` | 관리자가 휴일 달력·기준 시간을 정하면 초안에서 개인별 실제/환산/목표/잔여/초과 시간이 보인다 | A | A8 | 신규 `backend/policy/**`(달력·목표), `backend/roster/**`(진단 조회), `schema.sql`, `openapi.yaml` | 휴일 달력, 일 기준시간, 일반/전담 목표, 시간 진단 API | 시간 진단은 관리자만. 법정 상한은 상수로 넣지 않음 | 30일·휴식일 9일·8시간 = 168시간, 주말·공휴일 중복 제거, Night 월 귀속 | CI, 결정 사항 14 |
| 19 · A14 `feat/cross-team-support` | 관리자가 허용한 범위에서만 다른 팀 근무 배치가 된다 | A | A9 | `backend/roster/**`, `schema.sql`, `openapi.yaml` | 허용 기록(간호사·대상 팀·근무/기간)·철회, 규칙 `CROSS_TEAM_NOT_ALLOWED` | 4경로 동일, 허용 배치는 **배치 팀** 충원에 계산, 철회 시 초안 재검증 | 허용 전/범위 밖/철회 후 거부, 동시간 중복 | CI |
| 20 · A15 `feat/sof-night-dedicated` | 일반 간호사 월 Night 6회 이상이면 SOF 1일이 필요하다는 진단, Night 전담 2명 3개월 순환·월 15회 검증 | A | A10, A13 | `backend/roster/**`, `backend/policy/**`, `openapi.yaml` | SOF 상태, 전담 기간 기록 | 기준값은 병동 정책. SOF 이월 없음 | 6회 경계, 전담 교대·월 경계 | CI, 결정 사항 16. 크면 SOF와 Night 전담을 두 PR로 나눔 |
| 21 · B8a `feat/android-off-requests` | Android 간호사 희망 오프 제출·상태 확인, 관리자 취합·확정·반려·예외 접수 | B | A12, B3a | `android/**` | 소비: A12 | 마감 뒤 간호사 입력 잠금 | 상태별 화면, 마감 경계 | 실연동 확인 |
| 21 · B8b `feat/web-off-requests` | 웹 같은 기능 | B | A12, B3b | `web/**` | 같음 | 같음 | 같음 | 같음 |

### 3.5 5단계: 검토·확정·공개·변경 비교

| 순서/PR 가칭 | 사용자에게 보이는 완료 결과 | 담당자 | 선행 PR | 변경 영역·파일 소유 | API·DTO·DB 변경 | 핵심 권한·근무 규칙 | 인수 테스트 | 병합 조건 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 22 · B9a `feat/android-published-roster` | Android 간호사가 공개된 병동 전체/본인 근무표를 전환해 보고, 공개 전에는 "아직 확정된 근무표가 없습니다"와 대상 월을 본다. DemoRoster는 별도 시연 진입으로 분리 | B | B1 (서버 API는 이미 있음) | `android/**` | 소비: `/published-rosters`, `/mine` | 초안·부재 사유 비노출 | 404(공개 없음)·403·월 이동 | 실서버 확인 |
| 22 · B9b `feat/web-published-roster` | 웹 같은 기능 | B | B2 | `web/**` | 같음 | 같음 | 같음 | 같음 |
| 23 · A16 `feat/confirm-acknowledgements` | 확정할 때 미충원(일반·선임)과 개인별 목표시간 미달을 **각각** 대상·규모·사유와 함께 확인해야 공개되고, 확인 기록이 공개 revision에 남는다 | A | A9, A13 | `backend/roster/**`, `schema.sql`, `openapi.yaml` | `ConfirmRosterRequest` 확장(진단별 확인), 확인 기록 테이블 | 필수 규칙 위반은 확인해도 차단. 서버가 계산한 진단과 확인 내용이 다르면 거부 | 확인 누락·불일치·위반 차단, 멱등 재시도 | CI, 결정 사항 20 |
| 24 · A17 `feat/revision-draft-compare` | 공개 후 수정 초안이 현재 공개 배치를 복사해 시작되고 영향 날짜·간호사가 표시된다. 공개 이력과 이전/현재 비교 API가 생긴다 | A | A16 | `backend/roster/**`, `openapi.yaml` | 공개본에서 초안 생성, 공개 이력 목록, 비교 응답(변경 날짜·근무만) | 비교는 공개본끼리만. 초안·부재 사유 비노출. 재확정 전 공개본 유지 | 비교 정확도, 권한, 동시 재확정 | CI, 결정 사항 17·18 |
| 25 · A18 `feat/setup-readiness-precheck` | 관리자가 첫 초안 생성 전에 빠진 설정 목록을 보고, 팀·근무별 선임 수요 대비 가용 선임 부족을 미리 안내받는다. 필수 설정이 없으면 생성 거부 | A | A9, A13 | `backend/roster/**`, `openapi.yaml` | 준비 상태 API, 생성 전 점검 API | 규칙을 완화하지 않고 알림만 | 누락 항목별 응답, 선임 부족 사례 | CI |
| 26 · A19 `feat/generation-engine-v2` | 자동배치가 필수 규칙을 지키고 팀별 경력 구성을 개선하며 진단을 따로 보여준다 | A | A11, A12, A14, A15, A18 | `backend/roster/application/**`, `backend/roster/domain/**`, `openapi.yaml`(진단 응답) | 생성 작업 진단 확장, 입력·정책 revision 고정 | 필수 규칙 위반 0건. 팀 안 수동 순서를 상대 경력순으로 사용하며 병원 입사 연월보다 우선 | 날짜순과 수동 순서가 다른 fixture, 완전 충원·미충원 사례, 4경로 결과 일치 | CI, 결정 사항 22. 필요하면 "필수 규칙 엔진"과 "소프트 목표 점수"로 두 PR 분리 |
| 27 · B10a `feat/android-draft-review` | Android 관리자: 초안 달력 편집, 후보 가능/불가 사유, 자동배치 진행·취소, 미충원·선임·시간·경력 분포 진단, 진단별 확인 후 확정 | B | A16, A18, B7a | `android/**` | 소비: 기존 roster API, A16, A18 | 확정 버튼은 확인 항목 모두 체크해야 활성(서버가 최종 판정) | 409 revision 충돌, 확인 불일치 오류, 생성 취소 | 실연동 확인 |
| 27 · B10b `feat/web-draft-review` | 웹 같은 기능 (관리자 편집 작업량이 커서 웹 화면 우선 검토 권장) | B | 같음, B7b | `web/**` | 같음 | 같음 | 같음 | 같음 |
| 28 · B11 `feat/client-revision-compare` | Android·웹 간호사 화면에 변경 표시와 이전/현재 공개본 비교 | B | A17, B9a, B9b | `android/**`, `web/**` (작으면 한 PR, 크면 a/w 분리) | 소비: A17 | 공개본만 비교 | 비교 표시, 이력 없음 상태 | 실연동 확인 |
| 29 · B12 `test/cross-client-e2e` | 관리자 생성→초대 가입→설정→자동배치→검토·확인→확정→Android·웹이 같은 공개 revision 조회를 한 번에 재현 | B | A19, B10a·b, B11 | `scripts/e2e/**`, `.github/workflows/**`(선택) | 없음 | 가상 데이터만 | 완전 충원·미충원 두 시나리오 | 시연 기록 첨부 |

---

## 4. PR별 상세

형식: **포함 / 제외 / 범위 / 계약 합의 상대 / 충돌·함께 수정할 파일 / 중요한 예외 / 완료 증명**

### A1 `feat/invitation-24h-career-month`
- 포함: 초대 24시간 고정(요청의 `expiresInHours` 제거), 재발급(같은 역할·같은 간호사 연결로 새 토큰 발급), 수락·`my-profile`에서 `rank` 제거, 미리보기 응답 정리, OpenAPI 갱신.
- 제외: 관리자 단계 조정(A5), 병동 생성(A4), 클라이언트 화면.
- 범위: 백엔드만.
- 계약 합의: B (B3a·B3b가 소비).
- 충돌·함께 수정: `CreateInvitationRequest`, `RedeemInvitationRequest`, `StaffRequests.CareerProfileRequest`, `InvitationRedemptionService`, `StaffService`(프로필 제출), `WardInvitationHttpTest`·`InvitationRedemptionHttpTest`·`StaffCareerProfileHttpTest`. 전역 `fail-on-unknown-properties: true`라서 예전 클라이언트가 `rank`를 보내면 400이 된다. `dev`의 Android에는 아직 이 클라이언트가 없지만, 미병합 Android 브랜치를 옮길 때 B가 맞춘다.
- 예외: 만료 정각, 재발급 중 이전 링크로 동시 수락, 철회된 초대 재발급, 이미 소속된 사용자.
- 완료 증명: 시간 고정 테스트(Clock 주입), HTTP 테스트, `./gradlew test`.

### B1 `feat/android-kakao-session`
- 포함: 미병합 `feat/android-auth-invite`의 로그인·세션 커밋(`c060bf9`, `9a27241`, `a6b0103`, `c1e6a51`)을 최신 `dev`로 옮기기(`5ca51cc`에 git 충돌 없이 적용되는 것만 확인했다. 빌드는 미확인이며, `c060bf9`에는 초대 가입·직급 입력 화면도 들어 있어 이 PR에서는 로그인 범위만 남기고 나머지는 걷어낸다), 병동 목록·역할 표시, 소속 없음 안내.
- 제외: 초대 가입 화면의 직급 입력(A1 계약으로 B3a에서 다시 작성), 코드 없는 병동 생성(`74c9036`, A4 계약으로 B4a에서 다시 작성), 근무표 서버 조회(B9a).
- 범위: Android만.
- 계약 합의: 없음 (기존 API 소비).
- 충돌·함께 수정: `android/app/build.gradle.kts`(Kakao SDK·네트워크 의존성), `AndroidManifest.xml`, `MainActivity.kt`, `NurseFlowApp.kt`.
- 예외: Kakao 미설치·취소, 키 해시 불일치(디버그 안내), refresh 만료·재사용 탐지로 강제 로그아웃, 오프라인.
- 완료 증명: JVM 단위 테스트, `assembleDebug`, 로컬 서버 실로그인 시연 기록.

### A2 `feat/auth-web-session`
- 포함: 웹 Kakao 인가 코드 교환, HttpOnly·Secure refresh 쿠키, 쿠키 기반 갱신·로그아웃, 허용 Redirect URI 설정.
- 제외: 웹 화면(B2).
- 범위: 백엔드. 계약 합의: B.
- 충돌: `AuthController`, `AuthService`, `AuthProperties`, `SecurityConfig`(CSRF·CORS), `application.yml`.
- 예외: state 불일치, 코드 재사용, 쿠키 없는 갱신, 교차 사이트 요청.
- 완료 증명: 가짜 OIDC 공급자 HTTP 테스트.

### B2 `feat/web-foundation-session`
- 포함: `web/` 골격(프레임워크 결정 후), OpenAPI 기반 API 클라이언트, 로그인·병동 목록, CI 웹 작업.
- 제외: 업무 화면.
- 범위: 웹·CI. 계약 합의: A (A2).
- 충돌: `.github/workflows/verify.yml`(B 소유. A의 백엔드 작업 줄은 건드리지 않음), 루트 `README.md`.
- 예외: 쿠키 차단 브라우저, 세션 만료.
- 완료 증명: 웹 테스트·빌드 CI, 로컬 시연.

### A3 `feat/hospital-search`
- 포함: 외부 공식 병원 정보 API 어댑터(인터페이스 + 실제 구현 + 테스트용 가짜 구현), 결과 정규화(이름·주소·외부 식별자·출처), 시간 제한, 빈 결과(200 빈 목록)와 장애(별도 오류 코드) 구분, 검색어 길이·페이지 크기 제한.
- 제외: 병동 생성(A4), 검색 결과 캐시·로컬 복제, 지도 API.
- 범위: 백엔드. 계약 합의: B (B4a·B4b).
- 충돌: `application.yml`(키 환경변수), `SecurityConfig`(외부 호출 없음), `ErrorCode`.
- 예외: 외부 응답 형식 변경, 시간 초과, 호출 한도 초과, 특수문자 검색어.
- 완료 증명: 가짜 외부 서버 테스트, 실제 키로 수동 호출 기록(키 값은 기록하지 않음), 출처 표시 문구 확인.

### A4 `feat/ward-self-create`
- 포함: `POST /wards` 교체, 병원 표시 스냅샷 저장, 생성자 `WARD_ADMIN`, `Idempotency-Key`, `BootstrapProperties`·`bootstrap_state` 사용 제거, `GET /wards` 응답에 병원명, 기존 H2 파일 DB 호환.
- 제외: 구성원 관리(A6), 설정 안내 서버 판정(A18).
- 범위: 백엔드. 계약 합의: B.
- 충돌: `WardController`, `WardService`, `WardRequests`, `WardJdbcRepository`, `schema.sql`, `ErrorCode`(BOOTSTRAP_* 제거), `WardBootstrapHttpTest`(→ 생성 테스트로 교체), `backend/README.md`, `application.yml`(`nurseflow.bootstrap.code` 제거).
- 예외: 같은 사용자 동시 생성, 재시도, 같은 병원·같은 병동명(결정 사항 3), 병원 선택 검증 실패(결정 사항 2).
- 완료 증명: HTTP·동시성 테스트, 이전 스키마 파일 DB에 새 `schema.sql` 적용 테스트.

### B3a / B3b 초대 관리·가입 (Android / 웹)
- 포함: 관리자 초대 발급(역할·간호사 연결), 링크 복사·공유, 대상별 상태(대기·사용·만료·철회)와 미가입자, 재발급·철회. 간호사 링크 열기→로그인→미리보기→입사 연월→가입→병동 진입.
- 제외: 구성원 권한 변경(B5).
- 범위: 각각 Android, 웹. 계약 합의: A (A1).
- 충돌: Android `AndroidManifest.xml`(딥링크), 웹 라우팅.
- 예외: 만료·철회·사용됨·이미 소속·로그인 취소 후 재개, 링크 형식(결정 사항 6).
- 완료 증명: 실서버 가입 시연 2계정(관리자·간호사), 오류 상태 화면 캡처.

### B4a / B4b 병원 검색·병동 생성 (Android / 웹)
- 포함: 검색→후보(동명은 주소)→병동명→생성, 결과 없음·외부 장애·재시도 화면, 생성 뒤 "설정 안내" 화면 골격(항목 목록과 상태 표시만).
- 제외: 설정 항목 입력(B5·B7), 미등록 병원 수동 등록(결정 사항 1 전까지 화면 없음).
- 범위: 각각. 계약 합의: A (A3·A4). A3·A4 계약 합의 후 목업으로 시작 가능, **병합은 A4 병합 뒤 실연동 확인 후**.
- 예외: 네트워크 단절, 생성 응답 유실 후 재시도.
- 완료 증명: 목업·실서버 시연.

### A5 `feat/staff-admin-career-adjust`
- 포함: 병원 입사 연월과 실제 경력 시작 연월·직급 분리, 관리자 편성 단계·선임 자격·팀 내 순서 저장, 조정 이력 조회. 팀 안에서 드래그한 순서가 상대 경력순의 기준이다.
- 제외: 팀 수 일괄 설정(결정 사항 13), 근무 형태(A10).
- 범위: 백엔드. 계약 합의: B (B5).
- 충돌: `StaffService`, `StaffJdbcRepository`, `StaffResponses`, `CareerPolicy`, `schema.sql`, `StaffCareerProfileHttpTest`.
- 예외: 병원 입사 연월과 수동 경력순이 다른 경우, 비활성 간호사 조정, 동시 조정.
- 완료 증명: HTTP 테스트, 드래그 순서 저장·재조회와 이력 순서 검증.

### A6 `feat/membership-admin`
- 포함: 구성원 목록, 역할 변경(관리자 추가·해제), 비활성화, 감사 기록, 마지막 관리자 보호.
- 제외: 관리자 계정 상실 복구 절차(결정 사항 4. 결정 전에는 "마지막 관리자 해제 금지"만).
- 범위: 백엔드. 계약 합의: B.
- 충돌: `WardService`, `WardJdbcRepository`, `ErrorCode`, 간호사 기록 비활성화 시 `StaffService`.
- 예외: 자기 자신 해제, 동시 해제로 관리자 0명, 비활성 사용자의 기존 세션.
- 완료 증명: 권한 표 테스트.

### A7 `feat/roster-eligibility`
- 포함: 서버 편성 대상 판정 함수 하나로 통일(자동배치·후보·충원·공정성 계산이 같은 함수 사용), 대상자·제외 사유 조회 API.
- 제외: 근무 형태 판정(A10).
- 범위: 백엔드. 계약 합의: B.
- 충돌: `RosterJdbcRepository.activeNurses`, `RosterService.candidateViolations`·`processGeneration`, `RosterServiceIntegrationTest`(가상 간호사 기록을 쓰는 기존 테스트 fixture 수정 필요).
- 예외: 월 중 비활성화, 초대 수락 전 가상 기록, 관리자 겸 간호사 기록.
- 완료 증명: 제외 사유별 테스트, 기존 roster 회귀 테스트.

### B5a / B5b 팀·간호사·구성원 관리 (Android / 웹)
- 포함: 팀 수·이름 설정(기존 팀 API 사용), 간호사 팀 배치, 팀 안의 경력순 드래그 정렬, 병원 입사 연월·실제 경력 정보 조회, 구성원 역할·비활성화.
- 제외: 근무 형태 입력(B7).
- 범위: 각각. 계약 합의: A (A5·A6).
- 예외: 소속 간호사가 있는 팀 삭제(409), 마지막 관리자 해제 거부 메시지.
- 완료 증명: 실서버 시연, 권한 오류 화면.

### B6 `chore/demo-seed-script`
- 포함: 공개 API로 가상 병동·팀·간호사·근무 유형을 채우는 스크립트와 사용법.
- 제외: 서버 내부 시드 코드, 실데이터.
- 범위: `scripts/demo/**`. 결정 사항 9·21에 따라 계정 연결 방식이 달라진다.
- 완료 증명: 새 H2 DB에서 스크립트 실행 후 조회.

### A8 `feat/shift-type-settings`
- 포함: 근무 유형 CRUD(이미 OpenAPI에 정의된 경로를 실제 구현과 맞춤), 휴게·실제 인정시간·월 목표 환산시간 필드, 병동 정책 revision 증가, 기존 초안 재검증 표시.
- 제외: 특수 코드(초기 범위 결정 필요), 휴일 달력(A13).
- 범위: 백엔드. 계약 합의: B (B7).
- 충돌: `shift_types` 스키마, `ShiftDefinition`, `RosterJdbcRepository.findShiftDefinition`, `ShiftKindConstraintMigration`.
- 예외: 사용 중 근무 유형 비활성화, 시각 겹침, 자정 경과.
- 완료 증명: HTTP·마이그레이션 테스트.

### A9 `feat/team-demand-senior`
- 포함: 팀별 필요 인원 입력, 팀·날짜·D/E/N마다 선임 필요 칸 1개, 선임 칸 검증, 선임 미충원 진단.
- 제외: 팀 간 지원(A14), 경력 분산(A19).
- 범위: 백엔드. 계약 합의: B.
- 충돌: `ReplaceRequirementsRequest`, `roster_slots`, `RosterService.replaceRequirements`·`view`, `RosterView` 응답(팀·선임 필드 추가 → 클라이언트 영향).
- 예외: 팀 없는 병동, 필요 인원 0, 선임 자격자 없음, 팀 삭제.
- 완료 증명: 4경로 동일 거부 테스트.

### A10 `feat/work-pattern-rule` / A11 `feat/consecutive-work-rule`
- 포함: 각각 하나의 규칙과 그 저장 필드, 검증기 확장, 4경로 테스트.
- 제외: Night 전담 순환(A15).
- 범위: 백엔드. 계약 합의: B (진단 코드 표시).
- 충돌: `RosterRuleValidator`(두 PR이 같은 파일을 바꾸므로 **순서대로** 진행), `RosterRuleValidatorTest`.
- 예외: 월 경계, VY 끼임, 전·다음 달 공개 근무, 고정 배치.
- 완료 증명: 검증기 단위 테스트 + 통합 테스트.

### B7a / B7b 근무 설정 화면 (Android / 웹)
- 포함: 근무 유형 시간, 팀별 필요 인원, 승인 VY·반차 등록(기존 absences API), 간호사 근무 형태.
- 제외: 희망 오프(B8), 휴일·목표시간 설정(A13 후속 화면은 B10에 포함하거나 별도 행 추가).
- 계약 합의: A.
- 완료 증명: 실서버 입력·재조회 시연.

### A12 `feat/off-requests`
- 포함: 제출(본인·관리자 대리, 입력 주체 기록), 확정·반려(판단 시각), 월 마감, 마감 뒤 예외 접수, 확정분 배치 금지 규칙, 영향 초안 재검증 표시.
- 제외: 한도·주말 목표의 최적화(A19).
- 계약 합의: B (B8).
- 충돌: 새 패키지 위주, `RosterRuleValidator` 입력 확장.
- 예외: 마감 정각, 이미 배치된 날짜의 확정, 공개본 영향, 같은 날 중복 제출.
- 완료 증명: 상태 전이·권한·4경로 테스트.

### A13 `feat/target-hours`
- 포함: 병동 달력(주말·공휴일·대체휴일·예외), 기준 일 시간, 일반/전담 목표, 초안별 개인 시간 진단.
- 제외: 공개 확인(A16), 주간 상한.
- 계약 합의: B.
- 예외: 자정 넘는 Night의 월 귀속(결정 사항 14), 월 중 입사·비활성.
- 완료 증명: 168시간 예시 재현, 경계 테스트.

### A14 `feat/cross-team-support`
- 포함: 허용 기록·철회, 검증 규칙, 배치 팀 기준 충원 계산.
- 계약 합의: B.
- 충돌: `RosterRuleValidator`, A9의 팀 칸.
- 예외: 철회 후 기존 초안, 범위 경계 날짜.
- 완료 증명: 허용 전·안·밖·철회 테스트.

### A15 `feat/sof-night-dedicated`
- 포함: SOF 필요 진단(일반 N 6회 이상 → 같은 달 1일), Night 전담 지정·순환 기록, 전담 월 Night 최소 횟수 진단.
- 제외: SOF 이월, 잔여시간 이월.
- 예외: 전담 인원 부족, 순환 중도 변경, 월 경계 Night.
- 완료 증명: 경계 테스트. 결정 사항 16 전에는 착수하지 않는다.

### B8a / B8b 희망 오프 화면 (Android / 웹)
- 포함: 간호사 제출·상태, 관리자 취합·대리 입력·확정·반려·예외 접수, 마감 표시.
- 계약 합의: A (A12).
- 완료 증명: 간호사·관리자 2계정 실서버 시연.

### B9a / B9b 공개 근무표 조회 (Android / 웹)
- 포함: 병동 전체/본인 전환, 월 이동, 공개 없음 상태(`PUBLISHED_ROSTER_NOT_FOUND`를 "아직 확정된 근무표가 없습니다"로 표시), Android의 `DemoRoster`를 시연 전용 진입으로 분리.
- 제외: 변경 비교(B11).
- 계약 합의: 기존 API. 응답에 팀·선임 필드가 추가되면(A9) 후속 갱신.
- 충돌: Android `NurseFlowApp.kt`(시연 화면 분리).
- 완료 증명: 공개 전·후 실서버 시연. **서버 API가 이미 있어 1단계와 병행 가능**하다.

### A16 `feat/confirm-acknowledgements`
- 포함: 확정 요청에 진단별 확인(일반 미충원, 선임 미충원, 개인별 목표시간 미달), 서버 재계산과 비교, 확인 기록 저장·조회.
- 제외: 수정 초안·비교(A17).
- 계약 합의: B (B10).
- 충돌: `ConfirmRosterRequest`(기존 `acknowledgeUnfilledCount` 대체 방식), `RosterService.confirm`, `RosterServiceIntegrationTest`.
- 예외: 확인 사이 초안 변경(revision 불일치), 진단 0건, 멱등 재시도.
- 완료 증명: 확정 경로 통합 테스트.

### A17 `feat/revision-draft-compare`
- 포함: 공개본 기반 수정 초안 생성(배치·부재 복사), 영향 표시, 공개 이력, 공개본 간 비교 API.
- 제외: 푸시 알림.
- 충돌: `RosterService.createRoster`(같은 달 초안 정책 변경), `RosterJdbcRepository`.
- 예외: 수정 초안이 있는 상태에서 정책 변경, 동시 재확정.
- 완료 증명: 비교 결과 fixture 테스트.

### A18 `feat/setup-readiness-precheck`
- 포함: 필수 설정 준비 상태 API, 생성 전 선임 수요/공급 점검, 필수 미설정 시 생성 거부.
- 계약 합의: B (설정 안내 화면).
- 완료 증명: 누락 항목별 테스트.

### A19 `feat/generation-engine-v2`
- 포함: 모든 필수 규칙을 쓰는 생성, 우선순위(필수 → 충원·선임 → 월 목표 → 선호·경력 분산·공정성), 진단 분리, 정책·입력 revision 고정, 결정적 결과. 경력 순위는 A5에서 저장한 팀 내 수동 순서를 사용한다.
- 제외: "최적" 표시, 생성형 AI 판단.
- 충돌: `RosterService.processGeneration` 대부분, `GenerationCoordinator`.
- 완료 증명: 고정 fixture(완전 충원·미충원·경력 구성 미달), 성능 상한 측정.

### B10a / B10b 초안 검토·확정 (Android / 웹), B11 비교 표시, B12 교차 클라이언트 E2E
- 포함: 위 표의 완료 결과.
- 계약 합의: A (A16~A19).
- 예외: 생성 중 다른 관리자 편집(409), 확인 불일치, 공개 직후 다른 클라이언트 조회.
- 완료 증명: 두 클라이언트 실서버 시연, B12 스크립트.

---

## 5. 의존 순서 요약

```
A1 ──► B3a, B3b ──────────────────────────────┐
B1 ──► B3a, B4a, B5a, B9a                     │
A2 ──► B2 ──► B3b, B4b, B5b, B9b              │
A3 ──► A4 ──► A6 ──► A7 ──► A10 ──► A11 ──► A12
        │      └──► B5a/b                      │
        └──► B4a/b                             ▼
A1 ──► A5 ──► A9 ◄── A8 ──► A13 ──► A15     A19 (엔진)
              │                       ▲       ▲
              └──► A14 ───────────────┼───────┘
A9 + A13 ──► A16 ──► A17 ──► B11      │
A9 + A13 ──► A18 ─────────────────────┘
A16 + A18 ──► B10a/b ──► B12
```

- A의 백엔드 PR은 같은 `RosterService`·`RosterRuleValidator`를 여러 번 바꾸므로 **한 번에 하나씩** 병합한다(A10 → A11 → A12 순서 고정).
- B는 서버가 준비된 기능(B1, B9a/b)부터 하고, 그다음 A의 계약이 합의된 기능을 목업으로 진행한다.

---

## 6. 첫 번째로 착수할 PR 2개

| PR | 담당 | 이유 |
| --- | --- | --- |
| **A1 `feat/invitation-24h-career-month`** | A | 열린 결정 사항이 없고(24시간 고정·입사 연월만은 확정), 범위가 작고, 초대 가입 화면(B3)의 계약을 먼저 고정한다. 병원 검색·병동 생성은 결정 사항 1~3과 외부 API 키가 필요해 그 사이 결정을 받는다 |
| **B1 `feat/android-kakao-session`** | B | 필요한 서버 API가 이미 `dev`에 있고, 미병합 브랜치의 로그인 커밋이 최신 `dev`에 git 충돌 없이 적용됨을 확인했다(빌드는 PR에서 확인). A1과 파일이 전혀 겹치지 않는다 |

두 PR이 병합되면 A는 A3(병원 검색, 키 준비되면)·A2, B는 B9a(공개 근무표 조회)·B3a로 넘어간다.

---

## 7. 구현 전에 결정해야 하는 사항

아래 항목 중 미결정인 정책은 해당 기능 PR 전에 정한다. 12번은 사용자 결정으로 해결돼 후속 기능의 기준에 반영했다.

| # | 결정할 내용 | 막히는 PR |
| --- | --- | --- |
| 1 | 검색되지 않는 병원·의료기관 처리: 관리자 임시 입력 허용 / 별도 확인 요청 / 생성 불가 | A4, B4 |
| 2 | 병동 생성 시 서버가 선택한 병원의 외부 식별자를 외부 API로 다시 확인할지, 클라이언트가 보낸 스냅샷을 저장할지 | A4 |
| 3 | 같은 병원·같은 병동명의 병동을 여러 관리자가 만들 때 허용 여부와 목록 표시 구분 방법 | A4, B4 |
| 4 | 관리자 계정을 잃었을 때(퇴사·Kakao 계정 상실) 병동 인계 절차. 다른 관리자가 없을 때 복구 경로 | A6 |
| 5 | 병원 정보 API 키 발급 주체, 운영 활용 승인, 호출 한도, 결과 캐시 허용 여부, 출처 표시 문구 | A3 |
| 6 | 초대 링크 형식: Android 사용자 정의 스킴과 웹 주소를 하나의 https 링크로 통합할지, 도메인 | B3a, B3b |
| 7 | 관리자 초대처럼 연결 간호사 기록이 없는 초대에서 "대상"을 어떻게 표시할지(메모 필드 여부) | A1(선택), B3 |
| 8 | 재발급 시 이전 미사용 링크를 자동 철회할지 | A1 |
| 9 | Kakao 계정이 연결되지 않은 가상 간호사 기록을 시연·자동배치 대상에 넣을 수 있게 할지(예: 시연 모드, 관리자 포함 표시) | A7, B6 |
| 10 | 경력 단계 기본 경계값(현재 코드 12/36/72/120개월)을 유지할지, 직급 필드를 관리자 전용으로 남길지 없앨지 | A5 |
| 11 | 선임 자격 판정: 관리자 수동 지정만 / 단계 기준 자동 제안 후 관리자 확정 | A5, A9 |
| 12 | **해결됨:** 관리자가 팀 안에서 정한 드래그 순서가 상대 경력순이다. 위에 놓인 간호사가 더 선임이며, 병원 입사 연월과 달라도 수동 순서가 자동배치의 경력 판단에 우선한다. | A5, A19, B5 |
| 13 | 팀 수를 줄일 때 소속 간호사가 있는 팀 처리, 팀 수 일괄 설정 API 필요 여부 | A5, B5 |
| 14 | 자정을 넘는 Night의 월 귀속, 공휴일 데이터 출처(수동 입력 / 외부 데이터), 기준 일 시간·목표시간의 시연 기본값 | A13 |
| 15 | 희망 오프 월 한도·주말 목표·마감일 기본값, 마감 뒤 간호사가 앱에서 예외를 요청할 수 있는지 | A12 |
| 16 | SOF 배치를 자동으로 할지 관리자가 할지, Night 전담 3개월 순환의 시작 기준·인원 부족·중도 변경 처리 | A15 |
| 17 | 공개본 비교 범위(직전 revision만 / 임의 두 revision), 간호사 화면의 변경 표시 유지 기간 | A17, B11 |
| 18 | 공개 후 변경이 생겼을 때 수정 초안을 관리자가 직접 만들지, 변경 이벤트가 자동으로 만들지 | A17 |
| 19 | 웹 프레임워크 확정(이전 인계 메모에는 React+Vite 예정으로 적혀 있음), 시연용 웹 호스팅 위치 | B2 |
| 20 | 공개 확인 기록에 남길 내용: 총량만 / 대상별 목록 스냅샷 | A16 |
| 21 | 시연용 Kakao 테스트 계정 수와 관리 방법 | B3, B6, B12 |
| 22 | 자동배치 방식: 결정적 휴리스틱 확장 / 제약 솔버 도입(새 의존성 필요) — `requirements.md`의 P0 비교 항목 | A19 |
| 23 | 특수 근무 코드·반차 표준 구간을 초기 MVP에 넣을지 | A8 후속 |

---

## 8. 작업 순서와 검토 기준

1. **breakdown 검토**: 이 문서는 PR #29로 `dev`에 병합됐다. A1 PR #27과 B1 PR #30도 `dev`에 병합됐다. 현재 기능별 진행은 [공유 작업 현황](work-status.md)을 확인한다.
2. **기능 브랜치**: 최신 `dev`에서 `feat/...`(또는 `fix/`, `chore/`, `test/`)를 만든다. 쌓인 브랜치는 만들지 않는다. 한 사람이 동시에 여는 기능 PR은 1개, 목업 PR을 포함해도 2개까지로 한다.
3. **설계 기록**: 각 PR 시작 시 API·DB·권한·예외·인수 기준을 PR 설명 초안에 먼저 적는다(`ai-development-review-workflow.md`). 서버 PR은 OpenAPI 변경을 첫 커밋으로 올리고 B가 소비자 관점에서 검토한다.
4. **구현·검증**:
   - **로컬 필수 검사**: 백엔드는 `./gradlew test`와 설정된 검사 항목을 확인한다. Android는 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`를 실행한다. 웹은 B2 이후 빌드·테스트를 실행한다.
   - **CI 검사**: `.github/workflows/verify.yml` 기준으로 백엔드는 `./gradlew test --no-daemon`, Android는 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon`을 실행한다.
   - **기기 검사**: 에뮬레이터/기기에서 `./gradlew :app:connectedAndroidTest`를 실행한다. CI에서는 실행하지 않으므로 결과 또는 생략 사유를 PR에 기록한다. 실제 Kakao·외부 API 연동 여부도 "목업/실연동"으로 구분해 적는다.
5. **CI**: PR에서 CI 통과. GitHub의 필수 검사·브랜치 보호는 설정 확인 전까지 강제된다고 적지 않는다.
6. **Codex 리뷰**: 병합 전 변경 전체를 리뷰한다. 우선순위는 실제 영향도로 정하고 결함 종류는 태그로 붙인다(`ai-development-review-workflow.md`). P1·P2는 병합 전 수정, 실행 오류 가능성이 있는 P3도 수정한다.
7. **병합**: `dev`로 **스쿼시 병합**(`git-flow.md`). 병합된 브랜치는 삭제한다. 최근 PR들이 일반 병합 커밋으로 들어간 점은 이 단계부터 바로잡는다.
8. **다음 기능 착수**: 병합 후 최신 `dev`에서 다음 브랜치를 만든다. 상대 담당자의 계약 PR이 열려 있으면 목업 작업만 진행한다.
9. **릴리스**: 단계(§3.1~§3.5)가 끝날 때마다 검증된 `dev → main` PR을 검토한다. `main`에 기능 브랜치를 직접 병합하지 않는다.

### 병합 체크리스트 (모든 기능 PR 공통)

- [ ] 대상 브랜치 `dev`, 스쿼시 병합
- [ ] 필수 근무 규칙은 서버에서 후보·수동·자동·확정 4경로 동일 판정
- [ ] 초안·부재 사유·타 병동 데이터 비노출
- [ ] OpenAPI와 구현 일치(서버 PR), 목업/실연동 구분(클라이언트 PR)
- [ ] 검증 결과와 생략한 검사 기록
- [ ] 실데이터·Kakao 키·토큰·외부 API 키 미포함
