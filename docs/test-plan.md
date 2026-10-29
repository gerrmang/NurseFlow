# Test Specification: 간호사 근무표 앱

초기 시연은 [2026-10-29 MVP 범위](mvp-scope.md)를 따른다. 아래 파일럿 시간 목표는 실제 병원 도입 평가에만 적용한다.

## 품질 게이트
1. 채택된 hard rule 위반 0건: 자동·수동·확정·공개 모두 검증.
2. 미충원, 특정 규칙 거부, timeout/실패, 증명된 infeasible을 혼동하지 않음.
3. 초안·부재정보·타 병동 정보에 대한 권한 우회 0건.
4. 관리자의 확정 전에는 draft가 숨겨지고, 확정 성공 시 publication이 원자적으로 적용됨.
5. 초대받지 않은 Kakao 로그인 사용자와 다른 병동 사용자의 데이터 접근 0건.
6. Android와 웹이 확정 후 같은 활성 공개 revision을 조회.
7. 비교 가능한 **후속 파일럿**에서 전체 월간 작업시간 <= 엑셀 기준 50%.

## P0 정책 계약 (모든 fixture에 버전 기록)
병원/병동·timezone, 월간 시간 범위 및 인접 월 조건, 실제 근무 시작/종료, 직원 자격/수요, 휴가·반차 interval 경계, 휴식 규칙, hard/soft 정책과 버전, fairness 산식/적격집단/분모, 기존 고정배치 처리, 미충원 확인/공개 정책, 생성 시간제한·status, 권한·공개 범위.

## Golden fixtures
- 전일 휴가, 오전/오후 반차; 근무와 정확히 맞닿거나 겹치는 interval.
- 자정 넘는 나이트 및 휴식시간 임계값.
- 전월 말 나이트→당월 1일 데이, 당월 말 나이트→익월 데이.
- 휴가/자격/필요인력 충돌로 인한 shortage.
- 가용 시간·계약량·야간 자격이 다른 직원의 fairness 비교.
- hard rules와 soft goals가 상충하는 사례.
- 알려진 feasible/infeasible 작은 모델 및 솔버 timeout.
- 재생성 시 고정 배치, 정책 변경, 생성 중 최신 데이터 변경.
- 다중 관리자 동시 편집·확정·중복 공개.
- draft/confirmed/published 및 다른 병동 권한.
- 초대 만료·1회 사용·철회·중복 수락·다른 병동 사용·간호사 연결 중복.

## Unit/property tests
- interval overlap, boundary, overnight, timezone, 월말·월초.
- 동일 validator의 rule code/설명 및 hard-before-soft 우선.
- fairness 산식 및 적격집합의 결정성.
- 빈/중복/누락 일정 입력 처리.
- 모든 생성 결과는 독립 validator 통과 또는 폐기/오류 상태.
- 작은 모델은 exhaustive oracle로 feasible/optimality/infeasible status 확인.
- timeout/취소/엔진오류를 불가능으로 분류하지 않는지 확인.

## Integration tests
- solver/heuristic 결과 → 독립 검증 → draft 저장.
- validator가 수동 저장, 확정, 공개에 모두 적용.
- 변경된 policy/leave/staff revision이 stale 초안을 무효화.
- 거부 시 수정은 원자적으로 반영되지 않으며 설명 제공.
- shortage 상태, 생성 status, 이유가 API/UI에서 일치.
- 공개 스냅샷 및 재공개 revision 관리.
- 서버에서 role·hospital·ward membership 확인; URL/object ID 변조 테스트.
- Kakao 로그인만 완료한 사용자는 병동 조회 거부; 초대 수락으로 사용자·membership·간호사 연결을 원자적으로 생성. 자기 권한 승격과 초대 재사용 거부.
- logs/export/errors에 사유·건강정보·타 병동 데이터가 남지 않는지 확인.

## E2E
1. 관리자가 병동 초대 링크를 발급하고, Kakao 로그인 사용자가 수락해 소속·역할을 얻는다. 초대 전에는 병동 자료가 보이지 않는다.
2. 관리자가 월 설정, 인원/수요, 승인 부재를 입력하고 자동 초안 생성.
3. 불가/미충원/fairness 이유 확인, 수동 수정 후 재검증.
4. draft는 간호사에게 숨기고, manager confirm 성공 시 활성 달력으로 공개. 기존 public revision은 새 draft 생성 중 그대로 노출.
5. 미충원 표도 확정 가능. 부족 슬롯/인원을 관리자가 확인했다는 기록을 남기고, hard rule 위반은 계속 차단.
6. 게시 후 변경은 새 revision으로 진행, stale 변경은 덮어쓰지 않음.
7. 타 병동/비인가 사용자 API 조회 및 export가 거부됨.
8. 생성 timeout/cancel/retry가 현재 유효한 초안을 덮지 않고 정확한 상태 노출.
9. Android와 웹의 병동 전체/내 근무 화면이 동일한 공개 revision을 사용. 관리자가 한쪽에서 확정한 결과를 다른 쪽에서 조회 가능.

## Android·웹 클라이언트 검증
- 도메인/화면 상태 로직은 Kotlin 단위 테스트로 규칙 결과와 화면 상태 변환을 검증한다.
- Android UI/E2E에서 관리자 달력 편성, 자동초안 검토, 확정/공개, 간호사 읽기 화면과 권한 상태를 확인한다.
- 웹 UI/E2E에서도 같은 역할·초대·편성·확정·조회 흐름과 오류/revision 상태를 확인한다.
- 다양한 화면 크기, 시스템 글자 크기, 키보드/스크린리더, 색상 이외의 가능/불가 표시를 확인한다.
- 두 클라이언트의 응답 모델은 같은 OpenAPI 계약과 서버 통합 테스트로 검증한다.

## 성능·관측
P0에서 최대 직원·월 슬롯·동시 관리자 규모와 SLO를 고정한 뒤 측정한다. duration, termination state, shortage count, validation failure type, input/policy/engine revision을 기록한다. telemetry에 이름, leave reason, 건강정보는 포함하지 않는다. cancel·retry·backup/restore의 일관성도 검증한다.

## 파일럿 시간 측정 프로토콜
- 엑셀과 앱의 동일한 작업범위를 정하고, 시작(데이터 준비 시작)부터 관리자 확정(활성 달력 적용)까지 시간을 기록.
- 준비·입력·생성 대기·검토·수정·재생성·확정/활성화 포함.
- 교육/최초 데이터 정리 시간은 분리 보고.
- 월 규모/정책 복잡도/수요/미충원/중단을 기록해 비교가능성 판단.
- 최소 월/관리자 관측수와 중앙값 등 통계 정의를 파일럿 전에 고정.
- 목표 <= 기준 시간 50%와 동시에 hard-rule/권한/게시 게이트를 통과해야 성공.

## 요구사항 연결
휴가/반차/야간 휴식 -> boundary fixtures; fairness -> fairness oracle; 미충원/진단 상태 -> shortage+timeout cases; 관리자 공개 -> lifecycle E2E; 개인정보/권한 -> API/export/log tests; 시간 목표 -> pilot protocol.
