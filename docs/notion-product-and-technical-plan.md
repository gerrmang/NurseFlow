# NurseFlow | 간호사 근무표 앱 기획 및 개발 계획

> Notion에 바로 붙여넣을 수 있는 제품·기술 통합 기획서 초안  
> 상태: 초기 기획 기록. 2026-10-29 이후의 시연 MVP 결정은 [MVP 범위](mvp-scope.md)가 우선한다. 현재 확정된 시연 구성은 Android/Compose + 반응형 웹 + Kotlin/Spring Boot + H2이며, 이 문서의 PostgreSQL·호스팅 문단은 실제 운영 환경을 위한 권장안이다.

## 1. 제품 한 줄 소개

병동 관리자가 근무 규칙과 이미 승인된 휴가 일정을 바탕으로 월간 근무표 초안을 자동 생성하고, 위반·미충원 이유를 확인한 뒤 수정·확정·공개하는 간호사 스케줄 관리 앱.

## 2. 해결할 문제와 목표

현재는 관리자가 엑셀에서 간호사별 휴가·반차, 데이·나이트 배치, 휴식 규칙을 하나씩 대조한다. 조건이 많아 편성 시간이 길고 규칙 충돌을 놓치기 쉽다.

**핵심 목표:** 월간 근무표의 준비부터 최종 공개까지 걸리는 시간을 비교 가능한 기존 엑셀 작업시간의 50% 이하로 줄인다.

- 현재 엑셀 기준 시간: 아직 측정되지 않음
- 측정 방법: 파일럿 시작 전 동일한 작업 범위로 기준선을 기록
- 안전 조건: hard rule 위반, 미검토 자동 공개, 비공개 일정 노출은 0건이어야 함
- 교육 및 최초 데이터 정리 시간은 운영 편성시간과 분리 보고

## 3. 사용자와 권한

| 사용자 | 역할 |
|---|---|
| 스케줄 관리자 / 수간호사 | 인력·근무 유형·필요 인원·규칙·이미 승인된 부재 등록, 자동/수동 편성, 검토·수정·확정 |
| 간호사 | 공개된 병동 전체 달력 또는 본인 일정만 선택해 조회. MVP에서 휴가 신청·결재나 표 편집은 하지 않음 |
| 병원/조직 관리자 | 사용자·병동 권한과 조직 설정. 필요 여부는 P0에서 확정 |

> 간호사는 병동 전체 달력 보기와 본인 일정만 보기 사이를 전환할 수 있다. 서버는 두 요청 모두 사용자와 병동 membership을 검사한다.

## 4. MVP 기능

### 관리자
- 월간 근무표 달력/명단 보기
- 간호사, 근무 유형, 실제 근무 시작·종료 시간, 필요 인원 등록
- 이미 승인된 휴가와 반차 시간을 일정으로 등록
- 직접 배치할 때 가능/불가능 여부와 규칙 사유 표시
- 규칙 기반 월간 자동 배치 초안 생성
- 빈 근무 칸, 부족 인원, 근무 배분 편차와 설명 확인
- 자동 생성된 초안 편집
- 미충원 현황을 확인한 뒤 관리자가 초안을 확정하면 해당 revision이 한 번에 게시되어 활성 달력에 적용
- 확정/게시된 표를 바꿀 때는 새 draft에서 검토·수정 후 다시 확정

### 간호사
- 공개된 스케줄 조회
- 미공개 초안, 타 병동 데이터, 부재의 상세 사유는 조회할 수 없음
- 병동 전체 보기 또는 본인 일정 보기 선택

### MVP에서 제외
- 급여, 출퇴근, 근태
- 휴가·반차 신청/결재
- 연차 잔여일수 계산 및 공식 HR 시스템 대체
- 관리자 확인 없는 자동 확정·게시
- 휴가/병가 사유나 진단정보 수집

## 5. 자동 배치 규칙

### 반드시 지킬 규칙 (Hard constraints)
1. 관리자가 등록한 승인 휴가 시간에 근무를 배치하지 않는다.
2. 반차 시간과 실제 근무 시간이 겹치면 배치하지 않는다. 예: 오후 반차면 오후 근무 불가.
3. 나이트 근무 다음날 데이 근무를 배치하지 않는다.
4. 실제 시간, 타임존, 전월·익월 근무를 고려한다.
5. 유효한 배치가 없으면 빈 칸으로 남기고 사유를 보여준다. 규칙을 자동 완화하지 않는다.

### 가능한 한 맞출 조건 (Soft objectives)
- 근무 종류와 부담을 간호사 사이에 가능한 한 균등하게 나눈다.
- 균등성 지표, 대상자, 가용 시간이나 계약 시간 보정은 병동과 합의한 뒤 설정한다.
- 우선순위 제안: 필수 규칙 준수 → 필요 인원 부족 최소화 → 균등 배분 개선.
- 시간 초과는 “불가능한 스케줄”의 증명이 아니다. 생성 상태와 근거를 분리해 표시한다.

### “AI 기반 자동 배치”의 범위
정해진 규칙을 보장하는 제약 최적화/스케줄링으로 접근한다. 생성형 AI가 근무 규칙을 임의로 바꾸거나 휴가를 승인하지 않는다. 휴리스틱과 제약 최적화 후보를 P0에서 같은 데이터와 검증기로 비교한 뒤 결정한다.

## 6. 기술 방향 제안

| 영역 | 제안 | 상태 |
|---|---|---|
| 앱 | Kotlin Android 앱, Jetpack Compose UI | Kotlin은 사용자 선호. Android/Compose는 계획용 가정, P0에서 확정 |
| 앱 내부 구조 | UI → ViewModel/화면 상태 → Repository → API/로컬 데이터 소스 | 제안 |
| 백엔드 | Kotlin + Spring Boot 기반 모듈형 단일 서비스 | 우선 후보. 서버 팀/운영 방식에 따라 Ktor와 비교 |
| 데이터베이스 | PostgreSQL을 로컬·테스트·운영에서 동일하게 사용 | 기본 추천. H2는 빠른 격리 단위 테스트에만 사용 |
| API | REST/JSON, `/api/v1`, OpenAPI 계약 우선 | 제안 |
| 자동 배치 작업 | API에서 작업을 등록하고 백그라운드 worker가 처리, 앱은 상태를 조회 | 제안. 병동 규모에 따라 P0에서 동기/비동기 확정 |
| 인증/보안 | Android OIDC 로그인 + Spring Security Resource Server | Spring Security 사용 권장. IdP 선택은 미결정 |

### DB 추천: PostgreSQL 기본, H2는 제한적으로
**권장:** 실제 DB를 PostgreSQL로 정하고, 개발자 로컬도 Docker PostgreSQL로 운영과 같은 DB 엔진을 사용한다. PostgreSQL은 시간 구간 자료형과 겹침 방지 exclusion constraint를 제공해 스케줄 구간 제약을 DB 차원에서도 보강할 수 있다. 앱/백엔드의 규칙 검증도 계속 유지해야 한다.

- H2는 빠른 단위 테스트 또는 화면/API 개발용 임시 프로토타입에만 둔다. H2의 MySQL/PostgreSQL compatibility mode는 기능 차이 전체를 재현하지 않으므로 운영 DB와 동작이 다른 SQL을 놓칠 수 있다.
- 통합 테스트는 운영과 같은 PostgreSQL 버전을 Testcontainers로 실행한다.
- 병원 운영팀이 MySQL만 지원하거나 이미 관리 인프라가 있다면 MySQL을 선택해도 된다. 그 경우에도 로컬·CI·스테이징·운영을 같은 DB 제품으로 맞추고 MySQL 컨테이너로 통합 테스트한다. PostgreSQL과 MySQL을 환경마다 섞지 않는다.

공식 문서: [PostgreSQL range/exclusion constraints](https://www.postgresql.org/docs/current/rangetypes.html), [H2 compatibility 차이](https://h2database.com/html/features.html), [Testcontainers PostgreSQL](https://java.testcontainers.org/modules/databases/postgres/).

### 인증 추천: Spring Security + OIDC
Spring Security는 인증·인가를 구현하는 보안 프레임워크라 사용할 수 있다. 다만 사용자 계정·로그인 제공자 자체를 뜻하지는 않는다. 권장 흐름은 다음과 같다.

1. Android 앱은 시스템 브라우저를 통해 OIDC Authorization Code + PKCE로 병원 또는 별도 identity provider(IdP)에 로그인한다. 앱에 client secret을 넣지 않고 embedded WebView 로그인도 사용하지 않는다.
2. IdP가 발급한 access token을 Android가 API 요청의 `Authorization: Bearer ...`에 붙인다.
3. Spring Security Resource Server가 서명, issuer, 만료, audience를 검증한다. 역할 변환과 `/wards/{wardId}` 접근 인가는 서버의 ward membership DB로 추가 확인한다. 토큰 role만으로 타 병동 데이터에 접근시키지 않는다.
4. 병원에 사용할 IdP가 있는지 P0에서 먼저 확인한다. 없다면 OIDC provider를 운영하는 방안과 계정·비밀번호·MFA·복구·감사까지 직접 운영할 책임을 비교한다. 직접 인증을 만들 때도 Spring Security를 사용하고 직접 암호/토큰 검증을 설계하지 않는다.

참고: [Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [OAuth 2.0 Native Apps RFC 8252](https://www.rfc-editor.org/rfc/rfc8252/).

### 왜 이 구성을 제안하나
- Android 공식 가이드는 Compose를 권장 UI 도구로 안내하고, 데이터 계층/Repository와 UI 상태 흐름을 권장한다.
- Kotlin은 Spring Framework가 공식적으로 지원하며 Spring Boot 기반 Kotlin 서비스 안내가 제공된다. REST API 구현은 Ktor도 공식 문서에서 지원한다.
- 자동 배치는 작업시간이 길어질 수 있어 API 요청을 붙잡기보다 작업 상태를 돌려주는 구조가 재시도, 취소, timeout 설명에 유리하다.
- 병원별 권한과 roster 버전을 한 경계에서 검증하도록 초기는 모듈형 단일 백엔드로 시작한다. 트래픽이나 조직 요구가 확인되기 전 마이크로서비스로 쪼개지 않는다.

## 7. 전체 시스템 구조

**Android 앱**
- 관리자 화면과 간호사 조회 화면
- 로그인/병동 선택, 권한 기반 메뉴
- 월간 달력과 roster 편집 UI
- API Repository 및 화면별 ViewModel
- 일정 검증 결과/작업 상태/미충원 사유 표시
- 오프라인 편집은 P0에서 요구 여부를 확인하기 전 지원하지 않는 것으로 가정

**Kotlin 백엔드**
- Auth/Organization: 사용자, 병원, 병동 membership, 역할
- Staff & Shift Setup: 간호사, 자격, 근무 종류, 근무 수요
- Roster: 월간 일정 버전, 배치, draft/published 상태, 확정 주체·시각
- Availability: 관리자가 넣은 승인 휴가/반차 시간대
- Rules & Validation: hard constraint 검증, 규칙 코드와 설명
- Scheduling: 생성 요청, 최적화 실행, shortage와 fairness 결과
- Confirmation & Audit: 관리자 확정 시 규칙 재검증, 공개 스냅샷, 변경 이력
- Database: 조직별 scope와 revision을 명시한 관계형 데이터

## 8. 핵심 데이터 모델 초안

| 엔티티 | 핵심 정보 |
|---|---|
| Organization | 병원 ID, 이름, 설정 |
| Ward | 조직 ID, 병동명, timezone, 공개 범위 |
| User / WardMembership | 사용자 ID, 역할, 병동 접근 범위 |
| NurseProfile | 병동 ID, 표시명/식별자, 근무 자격, 활성 여부 |
| ShiftType | 코드, 이름, 시작/종료 시간, 야간 여부 |
| StaffingRequirement | 날짜/근무 유형별 필요 인원 및 자격 조건 |
| Roster | 병동, 대상 월, revision, 상태, 정책 버전 |
| Assignment | roster revision, 간호사, 실제 시작·종료, 근무 유형, 고정 여부 |
| ApprovedAbsence | 간호사, 시작·종료 시간, 최소 분류. 사유 저장 안 함 |
| RulePolicy | 병동 규칙, hard/soft 분류, 정책 버전 |
| GenerationJob | 입력 revision, 엔진/정책 버전, 상태, 실행시간, 결과 요약 |
| AuditEvent | 관리자 변경·확정·공개 행위, 주체, 시각, revision |

### 상태와 버전
- Roster 상태: `DRAFT` → 관리자 확정 시 `PUBLISHED`
- 확정과 공개 적용은 한 번의 서버 트랜잭션/명령으로 처리한다. 확정 주체·시각·revision 및 미충원 확인을 기록한다.
- 미충원 표도 확정할 수 있다. 확정 전에 부족 슬롯 수와 근무별 목록을 보여주고 관리자가 확인하도록 한다.
- 새로운 draft를 만드는 동안 기존 공개 revision이 있다면 그 공개본은 계속 활성 달력에 보이고, 새 draft는 보이지 않는다.
- 정책·휴가·직원 정보가 바뀌면 기존 초안은 stale 처리하고 다시 검증한다.
- 모든 업데이트는 기대 revision(`If-Match` 또는 `version`)을 확인한다.
- 미충원 상태는 별도 수치와 사유로 저장한다. 규칙 위반을 빈 칸으로 숨기지 않는다.

## 9. API 계약이란 무엇인가
API 계약은 Android 앱과 백엔드가 서로 주고받을 요청·응답을 구현 전에 합의한 명세다. 화면마다 어떤 URL을 부를지, 권한이 무엇인지, 필드와 오류가 어떤 모양인지 정한다. 계약이 없으면 프론트는 `nurseId`를 보낸다고 가정하고 백엔드는 `staffId`를 기대하는 식의 불일치가 생겨 연결 단계에서 다시 고치게 된다.

OpenAPI 문서(`openapi.yaml`)를 API 계약의 기준으로 둔다. 여기에 method/path, query/path parameter, JSON schema, 권한, 성공응답, 오류코드, revision 동시성 규칙을 적는다. Android는 그 계약에 맞춘 mock으로 먼저 화면을 만들고, 백엔드는 같은 계약을 구현한다. 양쪽은 contract test로 실제 동작이 명세와 같은지 검사한다. OpenAPI는 사람과 도구가 함께 읽고 client/test 도구가 사용할 수 있는 언어 독립 API 설명 표준이다. [OpenAPI Specification](https://spec.openapis.org/oas/v3.0.4.html)

**예시: 관리자가 근무 슬롯에 간호사를 배정**
- 요청: `PUT /api/v1/rosters/{rosterId}/slots/{slotId}/assignment`
- 입력: `nurseId`, `shiftTypeId`, `expectedRosterRevision`
- 성공: 저장된 assignment와 새 revision 반환
- 야간 후 데이 위반: `422 HARD_RULE_VIOLATION`, `ruleCode=REST_AFTER_NIGHT`, 사람이 읽을 수 있는 사유 반환. DB 변경은 없음.
- 다른 관리자가 먼저 수정: `409 REVISION_CONFLICT`, 현재 revision 반환. 앱은 최신 표를 다시 받고 사용자가 수정 내용을 적용할지 결정한다.

이 정의가 “API 작업”의 시작 산출물이다. 현재 endpoint/DTO/보안 요구를 담은 [OpenAPI 초안](openapi.yaml)을 만들었다. 운영정책 P0에서 날짜·시간·역할·미충원 사유 데이터 범위를 확정한 뒤 초안을 고정한다. 미충원 공개 자체는 관리자가 확인 후 확정하는 것으로 정해졌다.

## 10. API 초안

Base path: `/api/v1`  
인증: Access token의 사용자로 서버가 병원/병동 권한을 확인한다. 요청의 `wardId`만 믿지 않는다.  
시간: API에는 ISO 8601 offset이 포함된 실제 시각을 쓰고, 병동 timezone 기준으로 월간 근무를 계산한다.

| Method | Endpoint | 목적 | 권한 |
|---|---|---|---|
| GET | `/me` | 로그인 사용자/역할/병동 조회 | 로그인 |
| GET | `/wards/{wardId}/staff` | 병동 간호사/자격 조회 | 관리자 |
| POST | `/wards/{wardId}/staff` | 간호사 추가 | 관리자 |
| GET | `/wards/{wardId}/shift-types` | 근무 유형 조회 | 병동 사용자 |
| PUT | `/wards/{wardId}/shift-types/{id}` | 근무 유형/시각 설정 | 관리자 |
| GET | `/wards/{wardId}/rosters?month=YYYY-MM` | 관리자용 draft/버전 상태 조회 | 관리자 |
| POST | `/wards/{wardId}/rosters` | 해당 월 draft 생성 | 관리자 |
| GET | `/rosters/{rosterId}` | roster와 revision 조회 | 권한 범위 내 |
| PUT | `/rosters/{rosterId}/requirements` | 필요 인원/자격 설정 | 관리자 |
| POST | `/rosters/{rosterId}/absences` | 승인된 휴가/반차 시간 등록 | 관리자 |
| POST | `/rosters/{rosterId}/assignments/validate` | 배치 전 가능 여부 및 사유 확인 | 관리자 |
| PUT | `/rosters/{rosterId}/slots/{slotId}/assignment` | 빈 슬롯 배정 또는 해당 슬롯 배치 수정 | 관리자 |
| DELETE | `/rosters/{rosterId}/slots/{slotId}/assignment` | 슬롯 배치 제거 | 관리자 |
| POST | `/rosters/{rosterId}/generation-jobs` | 자동배치 작업 시작 | 관리자 |
| GET | `/generation-jobs/{jobId}` | 진행상태/결과/미충원 요약 조회 | 관리자 |
| POST | `/rosters/{rosterId}/confirm` | 최신 revision 재검증 후 미충원 확인·확정·공개 적용을 원자적으로 수행 | 관리자 |
| GET | `/wards/{wardId}/published-rosters?month=YYYY-MM` | 병동 전체 공개 달력 조회 | 해당 병동 관리자/간호사 |
| GET | `/wards/{wardId}/published-rosters/mine?month=YYYY-MM` | 로그인한 간호사 본인 공개 일정만 조회 | 해당 병동 간호사/관리자 |

### 주요 API 동작
- 수동 배치 API는 `expectedRosterRevision` 또는 `If-Match`를 요구한다. 서버는 저장 직전 규칙을 다시 검증하고, 검증 실패 시 변경을 일부만 저장하지 않고 전체 요청을 거부한다.
**배치 사전 검증 응답 예시**
```json
{
  "allowed": false,
  "violations": [
    {
      "code": "REST_AFTER_NIGHT",
      "severity": "HARD",
      "message": "전날 나이트 근무 후 데이 근무를 배치할 수 없습니다."
    }
  ],
  "rosterRevision": 12
}
```

**자동배치 작업 상태**
- `QUEUED`, `RUNNING`, `COMPLETED`, `PARTIAL`, `TIMED_OUT`, `CANCELLED`, `FAILED`
- `PARTIAL`은 hard rule을 지킨 배치와 미충원 슬롯이 함께 있는 결과
- timeout/실패는 “해당 스케줄은 불가능”이라는 뜻이 아님
- 결과 반영 시 작업 시작 시점의 roster revision과 현재 revision이 같아야 함

**오류 응답**
- 공통 형식: `code`, `message`, `fieldErrors`, `ruleViolations`, `currentRevision`, `retryable`
- 예: `409 REVISION_CONFLICT`, `422 HARD_RULE_VIOLATION`, `403 WARD_ACCESS_DENIED`
- 서버 오류에 휴가 사유나 불필요한 개인정보를 포함하지 않음

## 11. 프론트엔드 화면/작업

| 화면 | 기능 |
|---|---|
| 로그인/병동 선택 | 권한이 있는 병동 목록과 현재 역할 표시 |
| 월간 근무표 | 달력/간호사별 배치, 필요·배치·미충원 수, 월 이동 |
| 근무 편집 | 근무 유형 선택, 배치 후보 가능 여부, 불가 사유, 저장 오류 |
| 승인 일정 관리 | 승인된 전일 휴가/반차 시간 등록·수정. 신청/결재는 없음 |
| 자동배치 시작/진행 | 월/정책 확인, 작업 상태, 취소, 결과 적용 가능 여부 |
| 초안 검토 | hard rule 결과, 미충원, 균등 배분 요약, 영향 간호사/날짜 |
| 확정/공개 | 한 번의 관리자 “확정 및 달력 적용” 동작. 미충원 수를 먼저 보여주고 stale revision이면 최신 표를 다시 불러오게 함 |
| 간호사 조회 | 공개본 읽기 전용, 허용 범위에 맞는 일정만 노출 |

### UX 원칙
- 초록/빨강만으로 의미를 전달하지 않는다. 아이콘과 텍스트로 “배치 가능/불가” 및 규칙 이유를 같이 제공한다.
- 앱 화면 폭이 좁으면 일별 상세 화면과 간호사 필터를 제공하고, 태블릿에서는 간호사×날짜 그리드를 넓게 보여준다.
- 색상/정렬에 민감한 상태는 접근성 라벨에도 포함한다.
- draft와 published 표를 시각적으로 확실히 구분한다.
- 관리자 화면은 모바일에서 모든 칸을 한 번에 편집하기 어렵기 때문에 태블릿 최적화 필요 여부를 사용자 조사에서 확인한다.

## 12. Backend / Frontend / API 작업 분담

### Backend 작업
1. 조직/사용자/병동 membership과 서버 권한 검사
2. 직원·자격·근무유형·월간 수요·부재 데이터 모델 및 CRUD
3. 공통 Rule Validator: manual update, auto result, manager confirm/publish 모두 같은 validator 사용
4. Roster revision/version 및 draft/published lifecycle
5. Generation Job API와 queue/worker, timeout/cancel/idempotency
6. 제약 최적화 엔진 후보 비교 후 hard rule + soft objective 구현
7. 미충원·생성상태·설명용 diagnostics
8. Audit trail, 오류 처리, 관측, backup/restore
9. API 문서 및 contract/integration 테스트

### Android Frontend 작업
1. Kotlin 프로젝트, navigation, dependency setup, theme/design system
2. 인증 세션과 ward role 기반 화면 분기
3. API DTO/Repository 및 화면 상태 모델
4. 월간 roster 조회/달력과 편집 UI
5. 가능 여부/규칙 설명, 휴가/반차 입력
6. 생성 job 상태 표시, 취소/새로고침/결과 가져오기
7. 초안 검토/편집, 미충원 및 fairness 설명
8. 확정/공개, 충돌 상태/재시도 UX
9. 간호사 공개 조회/접근성/화면 크기 검증

### API/Contract 작업
1. 기능 구현 전에 `openapi.yaml` 초안 작성
2. Resource·schema·role·error·revision semantics 정의
3. Android mock server와 contract test fixtures 제공
4. Backend와 Android가 공통 example/error/status contract를 사용
5. 주요 API 변경은 OpenAPI diff/review 후 Android client update
6. API v1은 versioned path와 deprecation policy 적용

## 13. 함께/순차 진행 순서

| 단계 | Backend | Android | API/QA 산출물 |
|---|---|---|---|
| P0 정책/기술 확정 | 서버 구성/DB/auth 후보 비교 | Android 최소 OS, Compose 여부, 화면 대상 확인 | ADR, 운영정책표, OpenAPI resource 초안 |
| P1 도메인/Contract | schema, 권한, validator 설계 | navigation, UI state, fake repository | OpenAPI v1 draft, ERD, role matrix |
| P2 기본 CRUD/수동 편성 | roster, staff, shifts, absences, assignments | 로그인, 달력, 수동 배치, availability reason | API mock, validator tests, contract tests |
| P3 자동배치 | generation job/worker/engine/diagnostics | job 진행 상태, 결과 요약/재적용 | benchmark, timeout/error fixtures |
| P4 검토/확정·공개 | confirm endpoint, snapshot, audit/version conflict | 미충원 검토 후 확정, 간호사 전체/본인 조회 | E2E, 권한, stale revision tests |
| P5 파일럿 | 안정화/관측/데이터 백업 | 현장 usability fixes | 기준선 대비 시간·shortage·수정 횟수 보고 |

### 작업 병렬화
- P0 정책·API schema 확정은 선행 작업이다.
- P1 이후 Android 화면 뼈대는 API mock을 기준으로 시작할 수 있다.
- Backend CRUD와 frontend 화면은 OpenAPI contract가 확정된 후 병렬 개발한다.
- 자동배치 결과 적용은 validator/versioning 선행 후 시작한다.
- 확정·공개는 roster version과 role enforcement가 검증된 뒤 붙인다.

## 14. OpenAPI 예시 계약 (초안)

**POST `/api/v1/rosters/{rosterId}/generation-jobs`**
Request
```json
{
  "expectedRosterRevision": 12,
  "preserveLockedAssignments": true
}
```
Response
```json
{
  "jobId": "job_123",
  "status": "QUEUED",
  "sourceRosterRevision": 12
}
```

**GET `/api/v1/generation-jobs/{jobId}`**
```json
{
  "jobId": "job_123",
  "status": "PARTIAL",
  "resultRosterRevision": 13,
  "summary": {
    "requiredAssignments": 120,
    "assigned": 116,
    "unfilled": 4,
    "hardRuleViolations": 0
  },
  "diagnostics": [
    {
      "code": "INSUFFICIENT_ELIGIBLE_STAFF",
      "message": "해당 근무에 배치 가능한 인원이 부족합니다.",
      "dates": ["2026-11-14"],
      "shiftType": "NIGHT"
    }
  ]
}
```

**POST `/api/v1/rosters/{rosterId}/confirm`**
- Header: `If-Match: "revision-13"`
- Body 예: `{"acknowledgeUnfilledCount": 4}`. 서버가 최신 revision의 미충원 수와 값이 같은지 확인한다.
- 서버 동작: 요청자와 ward membership 확인 → 최신 policy/absence/assignments 재검증 → hard violation/stale revision이면 거부 → 미충원 목록 및 수 확인 → 관리자의 명시적 확인을 받은 뒤 `PUBLISHED` snapshot 생성 및 활성 달력에 적용
- 미충원은 공개 가능하다. 이 요청에서 관리자가 미충원을 확인했음을 audit 기록에 남긴다.
- 기존 공개 revision이 있으면 새 snapshot 적용 시점에만 교체한다. 자동배치 API는 confirm을 호출하지 않는다.

> 요청/응답 필드 및 역할은 병동 정책 확정 후 OpenAPI source-of-truth에서 정리한다. 이 섹션은 협의용 예시다.

## 15. 우선순위와 의존성

### P0 — 사용자·병원 정책 결정
- Kotlin 기반 Android 앱 확정 범위, 최소 OS, Compose UI와 태블릿 요구
- 백엔드 언어/프레임워크, PostgreSQL/호스팅 위치, 인증 공급자, API 배포 구조
- 병동별 시간 규칙·필요 인원·자격·월 경계·공정성 규칙
- 미충원 확인 이벤트에 포함할 정보와 보존·삭제 정책; 병동 전체/본인 조회 권한은 확정되어 API에서 분리 적용
- 파일럿 크기, 엑셀 기준시간 측정 방법
- 엔진 후보 benchmark 기준 및 failure fallback

### P1 — API와 도메인 계약
산출물: [OpenAPI 초안](openapi.yaml) 확정, ERD, role/permission matrix, rule policy table, roster state diagram, API error catalogue, fake/mock responses.

### P2 — Admin MVP 편성
산출물: 관리자 달력, 수동 배치, 승인 일정 등록, 가능 여부와 이유, 서버 공통 validator.

### P3 — 자동 초안 생성
산출물: job API, background worker, 선택된 optimizer, hard-rule independent validator, fairness/shortage diagnostics, 결과 적용 UI.

### P4 — 검토·확정 및 달력 적용/간호사 조회
산출물: versioned confirm-and-publish endpoint, immutable published roster, read-only ward/personal nurse flows, role/ward security tests.

### P5 — 병동 파일럿
산출물: 파일럿 보고서(편성시간, 수정량, 미충원, rule violation, 사용성, 장애), 다음 반복 우선순위.

## 16. API와 보안 설계 원칙
- HTTPS만 허용한다. Spring Security가 JWT 서명·issuer·audience·만료를 검증하고 서버는 역할과 병동 membership을 DB에서 추가 확인한다. 경로의 `wardId`만 신뢰하지 않는다.
- 모든 ID를 받는 endpoint마다 object-level authorization을 검사한다. 단순히 현재 사용자 ID와 path ID를 비교하는 것으로 끝내지 않는다.
- 기본 거부 방식으로 endpoint role과 roster state를 검사한다. 간호사 전체 달력과 본인 달력 API를 분리해 서버에서 응답 범위를 제한한다.
- 요청 DTO와 응답 DTO는 허용 필드만 명시한다. JPA entity를 그대로 직렬화하지 않고, mass assignment와 부재/휴가 상세 사유 누출을 막는다.
- Android에는 client secret을 넣지 않는다. access/refresh token은 필요 최소 범위·수명으로 운영하며, 기기 저장이 필요하면 Android Keystore 기반 보호를 사용한다. 토큰과 일정/부재정보를 앱 로그에 남기지 않는다.
- 클라이언트의 불가 표시만 믿지 않고 서버에서 같은 규칙을 재검증한다.
- 모든 변경/확정 요청은 `If-Match`/expected revision을 사용해 stale overwrite를 막고, 한 transaction에서 재검증 후 all-or-nothing 저장한다.
- 자동배치와 확정 요청은 idempotency key를 지원해 중복 탭/재시도에 안전하게 처리한다.
- 요청 본문/배치 인원/페이지 크기에 상한을 두고, 자동배치에는 사용자·병동별 동시 실행 제한과 timeout/rate limit을 둔다.
- 월간 달력은 필요한 한 달의 데이터를 한 번에 조회해 N+1 요청을 피한다. 직원 목록은 pagination, 큰 응답은 범위 제한을 적용한다.
- roster 응답은 ward/month와 nurse/date 조건을 위한 인덱스 및 서버 측 범위 쿼리로 처리한다. 민감한 공개 일정은 CDN/public cache에 두지 않고 사용자 권한 범위의 캐시 정책을 적용한다.
- 승인 휴가의 이유는 받지 않으며 간호사 화면/API에는 absence detail을 노출하지 않는다.
- API 오류는 rule code와 해결 가능한 메시지를 주되 민감정보를 반환하지 않는다.
- 자동배치 결과는 비공개 draft에만 저장한다. 관리자의 confirm 요청이 성공할 때에만 공개 snapshot을 활성 달력에 반영하며 audit 이력을 남긴다.

## 17. 주요 위험과 대응

| 위험 | 대응 |
|---|---|
| 병동별 규칙 차이가 커 일정 엔진이 맞지 않음 | P0 지원 정책을 고정하고 미지원 규칙이 있는 병동은 파일럿 제외 |
| 휴리스틱/solver가 잘못된 배치 또는 그럴듯한 잘못된 상태 생성 | 독립 validator, status 분리, 알려진 feasible/infeasible fixtures |
| 작은 모바일 화면에서 월간 편성이 어렵다 | 태블릿 테스트, 간호사 필터/일별 상세, 사용성 파일럿 |
| stale draft가 최신 정책을 덮는다 | revision / If-Match / generation source revision |
| 간호사/병동 일정 정보가 노출된다 | server-side membership, DTO 최소화, draft/published 분리, 접근 테스트 |
| 자동 배치 시간 절감이 기대에 못 미친다 | 엑셀 기준선과 같은 범위 측정, 수정량/재생성 횟수 분석 |
| backend framework 선택이 팀 역량과 안 맞는다 | Spring Boot와 Ktor를 P0에서 팀 경험/운영 비용 기준으로 검토 |

## 18. 완료 판단 기준
- 필수 일정 규칙 위반 0건
- 자동 배치 미충원·timeout·불가능 상태가 구분되어 안내
- 미충원 표도 관리자 확인 후 확정할 수 있음
- 초안은 비공개이며 관리자의 확정이 성공하면 활성 달력에 공개 적용
- 간호사는 병동 전체 달력과 본인 일정 보기를 선택하고, API는 각 조회 범위만 반환
- 동시 수정/stale revision이 안전하게 거부 또는 재검증
- Android 앱과 API가 같은 계약/오류 코드를 사용
- 파일럿에서 비교 가능한 월간 편성 작업 시간이 기준선의 절반 이하

## 참고 문서
- [OWASP API Security Top 10 — Object-level authorization](https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/)
- [OWASP API Security Top 10 — Object property authorization](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/)
- [OWASP API Security Top 10 — Resource consumption](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption/)
- [OWASP Android KeyStore](https://mas.owasp.org/MASTG-KNOW-0043/)
- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Jetpack Compose UI architecture](https://developer.android.com/develop/ui/compose/architecture)
- [Spring Framework Kotlin support](https://docs.spring.io/spring-framework/reference/languages/kotlin.html)
- [Ktor REST API documentation](https://ktor.io/docs/server-create-restful-apis.html)
- [OpenAPI Specification](https://spec.openapis.org/oas/v3.0.4.html)
