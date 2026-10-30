# Project Plan: 간호사 근무표 앱

초기 시연 범위: [MVP 결정](mvp-scope.md) · 요구사항: [PRD](requirements.md) · 테스트: [Test Spec](test-plan.md) · 인터뷰 원본: [요구사항 명세](requirements-interview.md)

## RALPLAN-DR
### 원칙
1. Hard rule은 모든 경로에서 강제한다.
2. 부족 인원은 미충원으로 드러내며 규칙 위반으로 메우지 않는다.
3. 자동 배치는 초안까지만, 관리자가 확인 후 확정하면 활성 달력에 공개 적용한다.
4. 최소 정보·최소 권한, 병원/병동 경계는 서버에서 집행한다.
5. 시간 효율은 안전/검토 품질과 함께 측정한다.

### 결정 동인
- 규칙 및 날짜 경계 정확성.
- 미충원·불균등의 설명과 관리자 편집 부담.
- 병동 파일럿의 실제 작성시간 절감 및 운영 복잡도.

### 선택지
- A. 결정적 휴리스틱 + local search: 설명이 간단하고 빠르게 시작 가능. 결합 제약에서 가능 해를 놓칠 수 있음.
- B. 제약 최적화 솔버 + 독립 validator: 복합 제약 모델이 자연스럽고 작은 문제의 증명 가능성. 모델링·운영 비용 및 timeout 상태 설명 필요.
- C. 수동 편성 UX 우선 후 자동화: 현장 학습이 빠르나 자동 월간 초안이라는 핵심 MVP 목표를 만족하지 않음.
**초기 시연 ADR 결정:** “AI 기반 자동배치”는 생성형 AI가 규칙을 임의 판단하는 방식이 아니라 hard constraints와 soft objectives를 적용하는 일정 최적화로 해석한다. 시연 MVP는 Kotlin Android/Compose와 반응형 웹, 공통 Kotlin/Spring Boot API, 로컬 H2 파일 DB를 사용한다. 초기 생성 엔진은 결정적 휴리스틱과 독립 validator로 시작하고, 실제 병동 파일럿 전에 제약 최적화 솔버와 같은 입력·시간 한도에서 비교한다. 어느 안도 안전·충원 기준에 못 미치면 결과를 미충원으로 보고하고 필수 규칙을 완화하지 않는다.

## 단계 및 출구 기준
### P0 — 초기 시연 MVP 범위와 가상 정책 확정
플랫폼(Android/Compose + 반응형 웹), 공통 서버(Spring Boot), 시연 DB(H2), Kakao 로그인 후 관리자 초대, 관리자 입력 확정 불가 일정, 관리자만 확정·공개하는 경계는 [MVP 결정](mvp-scope.md)에서 확정했다.
가상 병동의 간호사·자격 구성, Day/Evening/Night별 날짜 수요, 반차 구간, 균등 배분 산식, 초대 링크 만료·철회와 첫 관리자 초기화, Android App Links/웹 링크 및 Kakao Redirect URI를 API 계약과 fixture로 구체화한다. 실제 병원 호스팅·운영 DB·보존정책·오프라인 지원은 초기 시연 범위 밖으로 둔다.
**Exit:** 두 클라이언트에 공통인 역할표·초대 흐름·가상 정책표·OpenAPI 계약·완전 충원/미충원 fixture와 시연 인수 기준이 문서화된다.

### P1 — 도메인·권한·규칙 검증기
Kakao OIDC 로그인·초대 수락, 사용자/병동 membership·간호사 연결·역할 변경, 직원·자격·근무유형·수요 슬롯·확정 부재·정책 revision·monthly roster revision 모델과 서버 validator를 만든다.
**Exit:** 초대 만료·재사용·권한 상승 차단과 unit/property/integration 월 경계 fixture가 통과한다. 모든 write path가 공통 validator와 병동 권한 검사를 사용한다.

### P2 — 관리자 수동 편성
Android와 웹의 월간 달력, 인력/수요 입력, 사전 취합된 불가 interval, 배치/수정, 가능/불가 상태와 텍스트 이유.
**Exit:** 두 클라이언트가 같은 API/revision을 사용하고 UI/API 우회 모두 hard rule을 차단한다. 미공개 초안/부재정보는 관리자에게만 보인다.

### P3 — 자동 배치 초안
초기 결정적 휴리스틱, hard rule과 fairness objective, typed shortage, timeout/cancel/status, 생성 입력·policy·engine revision. 생성형 모델의 토큰 사용 없이 독립 validator를 통과한 결과만 초안에 반영한다.
**Exit:** 대표 fixtures에서 결과 독립 검증 통과, violation=0, timeout/shortage/infeasible 상태 분리, stale 결과 안전 폐기.

### P4 — 관리자 검토·확정·공개와 간호사 조회
Android·웹의 편집/고정 배치, 재검증, 확정본 및 불변 공개 snapshot, 공개/재공개, 병동 전체/본인 read scope.
**Exit:** E2E에서 확정 전 초안 노출 0, 동시 수정·중복 공개 안전, 공개 범위와 사유 분리, Android·웹의 동일 활성 revision을 검증한다.

### P5 — 가상 데이터 시연과 실제 파일럿 분리
초기 MVP에서는 가상 병동의 완전 충원/미충원 시나리오, 초대/권한, Android·웹 동기화, 수정량·재생성·shortage·hard violation·공개 오류를 검증한다.
**시연 Exit:** [MVP 인수 기준](mvp-scope.md)을 통과한다. 이후 실제 병원 파일럿에서 기준선 대비 동일 범위 작업시간과 사용자 피드백을 측정한다. 엑셀 대비 전체 작업시간 <=50%는 파일럿의 출시 판단 기준이며 가상 데이터 시연의 성과라고 주장하지 않는다.

## 정량 인수 및 검증
1. 생성·수동 저장·확정·공개 모든 경로 hard violation 0, validator 독립 실행.
2. 엔진 비교는 동일 입력/정책/validator/resource budget, coverage·shortage·fairness·runtime·status·설명 품질 보고.
3. heuristic 실패/timeout과 증명된 infeasible 구별.
4. 전월 나이트→당월 데이, 익월 배치·반차 overlap 포함.
5. policy/leave/staff revision이 바뀐 결과와 동시 편집은 stale 상태로 거절.
6. 게시된 revision만 조회되며 역할·병동 경계 검증.
7. fairness 산식/대상/분모 P0 지정; 증명되지 않은 결과를 최적이라 부르지 않음.
8. 파일럿은 데이터 준비~공개까지 측정하고 교육시간 분리, 기준선의 절반 이하 달성 여부 판단.

## 프리모텀 (실패 시나리오)
1. 월초 경계에서 전월 나이트를 잊어 필수 휴식 위반 → 인접 월 문맥을 validator 입력으로 강제하고 자료가 없으면 검증 불완료 상태.
2. solver timeout을 불가능이라고 안내하거나 fairness가 충원보다 우선 → typed statuses, objective 우선순위, 작은 oracle 및 독립 validator.
3. 미공개 초안/부재정보가 API·타 병동 경로로 유출 → 서버 tenant boundary, 역할별 E2E, export/log 검사.

## 확장 테스트 계획
- Unit/property: 시간 interval·월 경계·timezone·hard validator·fairness·status 전이·작은 최적화 oracle.
- Integration: 같은 validator 자동/수동/확정/공개, revision invalidation, shortage/explanation, access control.
- E2E: 관리자 월 편성→자동 초안→수정→확정→공개→간호사 조회; 미공개/타 병동/부재 이유 거부.
- Performance: P0 규모에서 response/generation SLO, timeout/cancel/retry, benchmark A/B.
- Observability: duration/status/shortage/policy-engine revisions, 개인정보 제외, 백업·복구/공개버전 일관성.
- Pilot: 비교 시간 protocol, 편집량·shortage·규칙 위반·초기 교육 및 데이터 정리 별도.

## 위험과 대응
| 위험 | 완화 |
|---|---|
| 미정 병원 정책이 모델/엔진을 뒤집음 | P0 policy contract, unsupported case는 파일럿 제외 |
| 미충원과 완성 roster 혼동 | typed shortage와 관리자 acknowledge/publication gate |
| timeout이 불가능으로 오인 | 상태 분리, 증명 상태에만 infeasible 표시 |
| 공개 후 오래된 수정 덮어쓰기 | immutable published snapshot, revision check |
| 휴가/근무정보 노출 | 최소수집, 서버의 병원/병동 authorization, logs/exports 검증 |
| 기준선이 다른 달과 비교됨 | 동일 범위/규모 및 표본 프로토콜을 파일럿 전 고정 |
| 솔버가 공정성/커버리지 악화 | lexicographic priority, A/B benchmark, independent validator |

## 구현 시작 전에 확정
P0 항목을 합의한다. 특히 실제 근무 시간·연속 월 경계·필수 인력 자격·fairness·고정 배치 재생성·미충원 확인 이벤트 형식·호스팅/보존·파일럿 비교 protocol. 법적/기관 정책은 적용 국가와 병원별로 확인한다.
