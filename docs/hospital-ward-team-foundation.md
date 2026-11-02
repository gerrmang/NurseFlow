# 병원 → 병동 → 팀 → 간호사 구조 설계와 기능 단위

상태: **2026-11-02 설계 + H1 구현(백엔드·Android 화면·API 연동)**. H1(병원 소속·권한 기반과 병동 생성)만 `feat/hospital-ward-foundation`에 구현했다. H2 이후는 설계이며 런타임에 없다. 각 기능 단위는 백엔드와 Android 화면·실제 API 연동을 함께 완료한다.

**선행 변경**: 중앙 `ErrorCode`/`BusinessException`/`@CurrentSubject`를 도입한 `fix/backend-conventions-rest`는 PR #12로 `dev`에 병합되었다(병합 커밋 `8d29a07`, 원 커밋 이력 보존). H1은 그 위에서 구현했고 `dev` 대비 H1 범위는 `164933a..feat/hospital-ward-foundation`이다. 추가 rebase는 필요 없다. 병원별 정책 입력 항목은 `docs/hospital-roster-policy-settings.md`(별도 문서 PR, 미병합)를 따르고 이 문서는 소속 구조·권한·적용 시점·기능 순서를 정한다.

## 1. 현재 코드와의 차이

| 영역 | 현재 `dev` 코드 | 목표 |
| --- | --- | --- |
| 인증 | Spring Resource Server가 OIDC JWT를 검증. Controller가 `jwt.subject`를 서비스에 전달. 사용자 테이블·Kakao 연동·초대 없음. | Kakao 로그인·초대 수락(기능 2~4) 뒤에도 같은 subject 기반 권한 확인 유지 |
| 권한 | `ward_memberships(ward_id, subject, role∈{WARD_ADMIN, WARD_MEMBER}, can_view_ward_calendar)`만 있음. 병원 개념 없음. | 병원 역할 `HOSPITAL_ADMIN`을 **별도 테이블**에 두고 병동 역할과 상속 관계를 만들지 않음 |
| 조직 | `wards(ward_id, name, time_zone)`. 생성 API 없음(직접 DB 입력). | `hospitals` → `wards.hospital_id` → `teams` → 기간별 팀 소속 |
| 간호사 | `nurses(ward_id, subject, display_name, qualifications, active)`. 초대 수락 여부·관리자 제외 판정 없음. 자동배치는 `active` 간호사 전체 사용. | 초대 수락·활성·`WARD_MEMBER`·비관리자만 편성 대상 (기능 4·5에서 판정) |
| 근무 유형 | `shift_types`가 병동 단위, 실제 시각만 저장, 환산시간 없음. | 병원 기본 근무 유형 + 실제 시각과 월간 환산시간 분리 + 병동 예외는 허용 항목만 |
| 스키마 변경 | `schema.sql`의 `CREATE TABLE IF NOT EXISTS`만 사용. 기존 파일 DB의 테이블은 갱신되지 않음. | 반복 실행 가능한 `ALTER ... IF NOT EXISTS`와 기존 파일 DB 업그레이드 테스트 |
| 오류 | 중앙 `ErrorCode`/`BusinessException`/`GlobalExceptionHandler`(PR #12로 `dev`에 병합). 병원 기능 오류 코드는 없음. | 병원 기능 오류 코드를 중앙 `ErrorCode`에 추가했다 |
| 클라이언트 | Android는 `DemoRoster` 가상 데이터만 표시. 네트워크 계층 없음. | 서버 화면은 `core/network`·`feature/organization`으로 분리하고 시연 화면과 섞지 않음 |

## 2. 목표 모델과 결정

1. **병원**은 공통 근무 시각·환산시간·편성 정책의 기본값을 가진다. **병동**은 하나의 병원에 속하고 팀 구성과 병동 설정을 가진다. 병원 정책의 병동 예외는 정책 항목별 `wardOverridable` 목록에 있는 항목만 허용하고, 나머지 항목의 병동 값 저장은 서버가 거부한다.
2. **권한 분리**: `HOSPITAL_ADMIN`은 병원 정책 수정·병동 생성을 담당한다. `WARD_ADMIN`은 자기 병동만 관리한다. 병원 관리자도 근무표 작업은 `ward_memberships`의 병동 역할이 있어야 한다. 병동 관리자 역할은 다른 병동이나 병원 전체 권한으로 확장되지 않는다.
3. **병원·최초 병원 관리자 등록**은 서버 운영자 설정(`nurseflow.bootstrap.hospital.*`)으로만 한다. HTTP endpoint·공개 가입·클라이언트가 보낸 역할 값으로 관리자 권한을 주지 않는다.
4. **병동 생성 = 병동 + 최초 `WARD_ADMIN` 소속**을 한 트랜잭션에서 만든다. 현재 검증 가능한 신원은 요청자뿐이므로 최초 병동 관리자는 요청한 병원 관리자다. 다른 사람에게 병동 관리를 넘기는 것은 `WARD_ADMIN` 역할 초대(기능 3·4)로 처리한다.
5. **팀 개수는 저장하지 않는다.** 활성 팀 목록의 개수로 계산한다. "팀 N개 만들기"는 이름 규칙으로 팀 행을 일괄 생성하는 명령일 뿐이고 별도 `teamCount` 열을 두지 않는다. 팀 삭제 대신 비활성화로 과거 근무표의 팀 참조를 보존한다.
6. **팀원 수 ≠ 필요 인원.** 팀원 수는 소속 기록에서 계산하고, 날짜·팀·근무별 필요 인원은 근무표 수요(`roster_slots`/후속 팀별 수요)에 따로 저장한다.
7. **기본 소속 팀과 타 팀 지원은 분리한다.** 같은 시점에 간호사의 기본 소속 팀은 병동 안에서 하나다(`team_assignments`의 기간 비중첩 제약). 타 팀 지원은 별도 허용 기록(기능 10)이며 소속을 바꾸지 않는다.
8. **편성 대상**은 초대를 수락한 활성 `WARD_MEMBER` 간호사이며 관리자 계정은 제외한다. 이 판정은 초대 수락(기능 4) 이후에만 정확해진다.
9. **실제 근무 시각과 월간 환산시간을 별도 열로 저장한다.** 문서의 예시 숫자(D/E 7.5, N 9, 168시간 등)는 시연 설정 후보이며 코드 기본값으로 고정하지 않는다.

## 3. 적용 시점·공개본 보존·초안 재검증

- **적용 시점**: 병원 정책 버전과 팀 소속 변경은 `effective_from_month`(YYYY-MM)를 가진다. 기본값은 **다음 편성 월**이다. 정책·소속 행은 수정하지 않고 새 버전·새 기간 행을 추가한다.
- **공개본 불변**: 확정 시 근무표에 `policy_version_id`와 칸별 `team_id`(실제 배치 팀)·근무 유형의 실제 시각·환산시간을 고정 저장한다. 이후 정책·소속이 바뀌어도 공개된 근무표의 의미와 집계는 저장된 값으로 계산한다.
- **초안 감지**: 초안·생성 작업은 시작할 때의 `policy_version_id`와 `team_assignment_revision`(병동 단위 증가 값)을 기록한다. 현재 월에 적용되는 정책·소속이 바뀌면 해당 월 초안을 `STALE_INPUTS`로 표시하고, 실행 중인 생성 작업은 결과 적용 전에 버전을 비교해 `FAILED(STALE_INPUTS)`로 종료한다.
- **현재 초안에 즉시 적용**: 관리자가 적용 시점을 현재 초안 월로 지정하면 초안은 자동으로 바뀌지 않는다. 명시적 재검증 요청이 새 버전으로 모든 칸을 다시 검사하고 위반을 보여준다. 확정은 재검증되지 않은 초안을 거부한다(`STALE_INPUTS`).
- **서버 집행**: 팀 소속·지원 허용·정책 hard rule은 후보 조회, 수동 저장, 자동 생성, 확정 경로가 같은 서버 검증기를 사용한다.

## 4. 기능 단위 (한 단위 = 한 브랜치 = 한 `dev` PR)

| 순서 | 기능 단위 | 핵심 API/DB | 완료 기준 |
| --- | --- | --- | --- |
| **H1** | **병원 소속·권한 기반과 병동 생성 (이번 PR)** | `hospitals`, `hospital_memberships`, `wards.hospital_id/created_at/created_by`; 운영자 초기 설정; `GET /hospitals`, `GET·POST /hospitals/{id}/wards`, `GET /hospitals/{id}/wards/{wardId}`, 본인 병동 `GET /wards`; Android 병원·병동 선택·생성 화면 | 병원 간·병동 간 접근 차단, 일반 사용자 생성 거부, 최초 관리자 실패 롤백, 멱등·동시 생성, 기존 파일 DB 업그레이드, Android 상태·오류·멱등 재시도와 실제 서버 연동 |
| H2 | 팀 목록·일괄 생성·비활성화 | `teams(team_id, ward_id, name, sort_order, active)`; `GET/POST /wards/{wardId}/teams`, `POST .../teams:bulk-create {count, namePattern}`, `PATCH .../teams/{teamId}` | 팀 개수 = 활성 팀 수, 이름 중복·병동 경계, 소속 인원이 있는 팀 비활성화 규칙 |
| H3 | 기간별 기본 소속 팀 지정 | `team_assignments(nurse_id, team_id, effective_from_month, effective_to_month)`, 병동 `team_assignment_revision` | 같은 시점 하나의 팀, 기본 다음 월 적용, 팀원 수 계산, 공개본·초안 영향 표시 |
| H4 | 병원 근무 유형·실제 시각·환산시간 정책 버전 | `hospital_policy_versions`, `hospital_shift_types`(실제 시각·휴게·환산시간 분리) | 시각 겹침·환산값 검증, 정책 버전 증가, 병원 관리자 전용 |
| H5 | 병동 예외 허용 목록 | `ward_policy_overrides(ward_id, policy_key, value, effective_from_month)` | 허용되지 않은 항목 거부, 병원 기본값 대비 계산 결과 조회 |
| H6 | 근무표 입력 고정·재검증 | `rosters.policy_version_id`, `team_assignment_revision`, 칸별 `team_id` 스냅숏; `POST /rosters/{id}/revalidate` | 공개본 집계 불변, 초안 `STALE_INPUTS`, 생성 작업 버전 충돌 처리, 확정 차단 |
| H7 | 편성 대상 판정 | 초대 수락·활성·`WARD_MEMBER`·비관리자 판정과 월별 대상 조회 | 기능 4(초대 수락) 이후. 관리자 계정 제외를 서버에서 강제 |

팀별 필요 인원·선임(기능 9), 허용형 팀 간 지원(기능 10), 휴일·목표시간 등은 `docs/claude-next-stage.md` 순서를 따른다.

## 5. H1 상세

- **DB**: `hospitals(name UNIQUE, time_zone, created_at)`, `hospital_memberships(hospital_id, subject, role CHECK HOSPITAL_ADMIN, granted_at)`. `wards`에 `hospital_id`(FK, nullable), `created_at`, `created_by`를 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`로 추가하고 `UNIQUE(hospital_id, name)` 인덱스를 둔다. 기존 병동은 `hospital_id = NULL`로 남아 기존 근무표 동작이 그대로이고 병원 API에는 나타나지 않는다. 기존 병동을 병원에 연결하는 절차는 후속 운영 작업이다.
- **초기 설정**: `nurseflow.bootstrap.hospital.name`, `.time-zone`(기본 `Asia/Seoul`), `.admin-subject`가 있으면 기동 시 병원과 최초 `HOSPITAL_ADMIN`을 등록한다. 같은 이름·같은 관리자로 반복 실행하면 아무것도 바꾸지 않는다. 같은 이름의 병원에 다른 subject를 넣으면 기동을 실패시키고 관리자를 추가하지 않는다. 로그에는 병원 ID와 생성 여부만 남긴다.
- **권한 판정**: `HOSPITAL_ADMIN` → 허용. 같은 병원 병동에 병동 역할만 있음 → 403 `HOSPITAL_ADMIN_REQUIRED`. 무관한 사용자·없는 병원 → 404 `HOSPITAL_NOT_FOUND`. 다른 병원 병동·병원 미지정 병동 단건 조회 → 404 `WARD_NOT_FOUND`.
- **병동 생성**: 이름 정규화(앞뒤 공백 제거, 1~100자, 제어 문자 금지) → 권한 확인 → 병원 행 `FOR UPDATE` 잠금 → `Idempotency-Key` 재시도 확인(요청자·본문 지문) → 이름 중복 확인 → 병동·`WARD_ADMIN` 소속·재시도 기록 저장. 하나라도 실패하면 모두 롤백한다. 동시 요청은 병원 잠금으로 직렬화하고 유일 인덱스가 2차 방어선이다.
- **오류 코드**(중앙 `ErrorCode`): `SUBJECT_REQUIRED`(401), `HOSPITAL_NOT_FOUND`·`WARD_NOT_FOUND`(404), `HOSPITAL_ADMIN_REQUIRED`(403), `INVALID_WARD_NAME`·`INVALID_TIME_ZONE`(400), `WARD_NAME_TAKEN`·`IDEMPOTENCY_KEY_REUSED`(409). 알 수 없는 요청 필드(`role` 등)는 400 `INVALID_REQUEST`. Controller는 `@CurrentSubject`로 subject를 받는다.
- **본인 병동 조회** `GET /api/v1/wards`: 본인 `ward_memberships`의 병동만 역할·병원 이름과 함께 돌려준다. 일반 병동 구성원은 이 목록으로 병동을 선택하며 병원 권한이나 다른 병동은 노출되지 않는다. 병원 관리자 역할만으로는 병동이 나타나지 않는다.
- **Android**: `core/network`(전송·인증 헤더·`ApiProblem.code` 해석), `core/session`(세션 출처), `feature/organization/{data,domain,ui}`(DTO·저장소, 모델, ViewModel·화면)를 분리했다. 로딩·빈 목록·입력 오류·세션 만료(401)·권한(403)·숨김(404)·통신 실패·5xx와 재시도를 처리한다. 병동 생성은 결과가 불확실한 실패(통신 실패·5xx·해석할 수 없는 2xx 응답) 뒤 같은 입력이면 같은 `Idempotency-Key`를 재사용하고, 입력을 바꾸거나 확정 응답을 받으면 새 키를 쓴다. 생성 요청이 진행 중인 동안에는 병원 전환·양식 변경·병동 선택·재제출을 화면과 ViewModel 모두에서 막고, 응답은 요청을 보낸 병원의 양식에만 반영한다. 생성 성공 시 목록을 다시 읽고 새 병동을 선택한다. 선택한 서버 병동의 근무표 화면은 아직 없고 `DemoRoster` 시연 화면은 별도 진입으로만 열린다.
- **로그인 경계**: 실제 Kakao 로그인이 없다. 릴리스 빌드는 서버 주소·세션이 없어 "로그인 필요"만 보인다. 디버그 빌드만 개발용 OIDC 토큰 입력 화면을 제공하며 토큰은 메모리에만 두고, 서버는 같은 issuer·audience·서명 검증을 한다. 앱 코드에 토큰·관리자 계정을 하드코딩하지 않는다.
- **로컬 통합 검증**: `scripts/local-e2e/run-hospital-ward-e2e.sh`가 127.0.0.1 전용 OIDC 발급자, 백엔드 jar(임시 H2 파일 DB, 운영자 초기 설정), Android 네트워크·저장소 코드의 실제 HTTP 호출 테스트를 연결한다.
- **하지 않은 것**: Kakao 로그인·세션 교환, 사용자 테이블, 초대 발급·수락, 병원 관리자 추가·회수 API, 감사 로그 테이블, 기존 병동의 병원 연결, 팀·정책, 웹 클라이언트, 선택한 병동의 근무표 서버 연동. 운영 코드에 인증 우회를 추가하지 않았고 테스트 전용 `JwtDecoder`는 테스트 소스에만 있다.

## 6. 남은 위험과 결정 필요 항목

- 병원 관리자 추가·회수와 감사 기록 형식(권한 변경 이력 테이블)은 사용자 테이블·초대 기능과 함께 정한다.
- 기존 `hospital_id = NULL` 병동을 어느 병원에 연결할지는 운영 데이터에 따라 다르므로 수동 운영 절차로 남긴다. 연결 전까지 해당 병동은 병원 정책을 상속하지 않는다.
- 병원 이름 전역 유일은 시연용 단순화다. 다기관 운영 시 기관 식별자 기준으로 바꾼다.
- `mutation_replays` 재시도 조회는 근무표와 병원 기능에서 같은 테이블을 각자 질의한다. 후속 정리 PR에서 공통 저장소로 합친다.
- 실제 로그인부터의 E2E(Kakao → 서버 세션 → Android 화면)와 Android 기기·에뮬레이터 화면 조작은 검증하지 않았다. 기능 2(Kakao 인증 서버)와 Android 로그인 화면이 선행 작업이다.

## 7. H2(팀 목록·일괄 생성·비활성화)에서 할 일

- 백엔드: `teams` 테이블과 `GET/POST /wards/{wardId}/teams`, `POST .../teams:bulk-create`, `PATCH .../teams/{teamId}`. 병동 관리자(`WARD_ADMIN`) 권한, 팀 개수는 활성 팀 수로 계산, 이름 중복·병동 경계·비활성화 규칙, 기존 파일 DB 업그레이드 테스트.
- Android: 선택한 병동의 팀 관리 화면(목록·일괄 생성·이름 변경·비활성화)을 같은 `core/network`와 오류·멱등 처리로 연결하고 로컬 통합 검증 스크립트에 팀 시나리오를 추가.
- H1에서 남긴 제약: 병동 선택 뒤 근무표가 서버와 연결되지 않음, 병원 여러 개일 때 병원 선택은 순환 전환 방식, 디버그 토큰 입력은 Kakao 로그인 연결 시 제거.
