# 공휴일·휴일 달력 클라이언트(B) 인수인계

작성 2026-11-26. 기준 `dev` `3e30752`(A13 PR #58 병합본). 계약의 원본은 `docs/openapi.yaml`이며, 이 문서와 다르면 OpenAPI를 따른다.

## 1. 현재 상태 요약

| 영역 | 상태 |
| --- | --- |
| 서버: 국가 공휴일 가져오기·DB 저장·매일 갱신 | 완료 (A13) |
| 서버: 병동별 달력 항목(공휴일 보완·병동 휴무·근무일 지정) | 완료 (A13) |
| 서버: 월 목표시간 계산·개인별 시간 진단 | 완료 (A13) |
| 웹 화면 | 없음. `web/src/api/schema.gen.ts`에 타입만 생성됨 |
| Android 화면 | 없음 |
| 자동배치(초안 생성)에 공휴일 반영 | 없음. A19 범위 |
| 간호사용 공개 근무표에 공휴일 표시 | 없음. 아래 API는 모두 관리자 전용 |

`docs/feature-breakdown.md`의 B7 절은 "휴일·목표시간 설정(A13 후속 화면)은 B10에 포함하거나 별도 행 추가"로 남겨 두었다. 이 문서의 화면 요구는 그 후속 작업용이다.

## 2. API

모두 `x-required-role: WARD_ADMIN`. `month`는 `YYYY-MM`.

| 메서드·경로 | 용도 | 동시성 |
| --- | --- | --- |
| `GET /wards/{wardId}/holiday-calendar?month=` | 그 달의 병동 항목, 국가 공휴일, 월 목표 조회 | 응답 `ETag` = 병동 공통 정책 revision |
| `PUT /wards/{wardId}/holiday-calendar?month=` | 그 달의 병동 항목 **전체 교체** | `If-Match` 필수(정책 ETag) |
| `POST /wards/{wardId}/holiday-calendar/refresh?month=` | 국가 공휴일 지금 가져오기 | ETag 불필요, 정책 revision 안 바뀜 |
| `GET/PUT /wards/{wardId}/target-hour-policy` | 하루 기준시간, 연차·반차·SOF 환산, Night 전담 목표 | PUT은 `If-Match` 필수 |
| `GET /rosters/{rosterId}/hour-diagnostics` | 초안·공개본의 간호사별 시간 진단 | ETag 없음(조회 때 계산) |

### 응답 `HolidayCalendar`

```json
{
  "month": "2026-10",
  "days": [{ "date": "2026-10-09", "kind": "WARD_WORKDAY", "name": null }],
  "publicHolidays": [
    { "date": "2026-10-03", "kind": "PUBLIC_HOLIDAY", "name": "개천절" },
    { "date": "2026-10-05", "kind": "SUBSTITUTE_HOLIDAY", "name": "대체공휴일" },
    { "date": "2026-10-09", "kind": "PUBLIC_HOLIDAY", "name": "한글날" }
  ],
  "holidaySource": { "configured": true, "fetchedAt": "2026-11-26T18:30:00Z" },
  "target": {
    "month": "2026-10", "daysInMonth": 31, "weekendDays": 9, "holidayDays": 1,
    "wardRestDays": 0, "wardWorkdays": 1, "restDays": 10, "targetDays": 21,
    "dailyStandardMinutes": 480, "generalTargetMinutes": 10080
  },
  "policyRevision": 3
}
```

예시 값은 설명용이다(토요일 개천절은 주말과 겹쳐 세지 않고, 한글날은 병동이 근무일로 지정해 목표일이 되며, 남은 평일 공휴일은 대체공휴일 하나다). 실제 이름·날짜는 서버가 돌려준 값을 그대로 쓴다.

- `days`: 이 병동이 등록한 항목만. 같은 날짜의 `publicHolidays`보다 우선한다.
- `publicHolidays`: 서버 DB의 국가 공휴일(모든 병동 공통). `PUBLIC_HOLIDAY` 또는 `SUBSTITUTE_HOLIDAY`.
- `holidaySource.configured`: 서버에 공휴일 API 키가 있는지. `fetchedAt`이 `null`이면 그 달을 가져온 적이 없다.
- `target`: 화면에 숫자를 직접 계산하지 말고 이 값을 표시한다(서버가 권위).

### 요청 `HolidayCalendarInput` (PUT)

```json
{ "days": [{ "date": "2026-10-09", "kind": "WARD_WORKDAY", "name": null }] }
```

- 그 달의 병동 항목 전체를 보낸다. 빈 배열이면 그 달 병동 항목이 모두 지워진다. 국가 공휴일(`publicHolidays`)은 보내지 않는다.
- 날짜당 한 항목, 그 달 날짜만, 최대 31개. `name`은 50자 이하의 공개 정보만(개인 사유 금지).
- 토·일은 저장하지 않아도 자동으로 휴식일로 센다.

### 오류 코드

| 코드 | HTTP | 화면 처리 |
| --- | --- | --- |
| `INVALID_MONTH` | 400 | 월 선택값 오류 |
| `INVALID_HOLIDAY_CALENDAR` | 400 | 중복 날짜·다른 달 날짜·알 수 없는 종류. 입력 확인 안내 |
| `STALE_POLICY_REVISION` | 412 | 다른 곳에서 정책이 바뀜. 최신 달력을 다시 조회한 뒤 재저장 안내 |
| `HOLIDAY_MONTH_OUT_OF_RANGE` | 400 | 지금 가져오기는 작년~내년만 가능 |
| `HOLIDAY_SOURCE_NOT_CONFIGURED` | 503 | 서버에 키 없음. "공휴일을 직접 입력하세요" 안내 |
| `HOLIDAY_SOURCE_UNAVAILABLE` | 503 | 가져오기 실패. 저장된 공휴일은 그대로이므로 기존 달력을 유지 |

## 3. 달력 표시 규칙

한 날짜의 최종 상태는 아래 순서로 정한다.

1. `days`에 그 날짜 항목이 있으면 그 `kind`를 쓴다.
2. 없으면 `publicHolidays`의 항목을 쓴다.
3. 둘 다 없으면 토·일은 주말, 나머지는 평일.

| kind | 의미 | 표시 제안 | 월 목표 영향 |
| --- | --- | --- | --- |
| `PUBLIC_HOLIDAY` | 국가 공휴일(가져온 값 또는 병동이 직접 보완) | 빨간 날 + 이름 | 평일이면 휴식일 +1, 주말이면 중복 안 셈 |
| `SUBSTITUTE_HOLIDAY` | 대체공휴일 | 빨간 날 + "대체" 표시 | 위와 같음 |
| `WARD_REST_DAY` | 이 병동만의 휴무일 | 공휴일과 다른 색(예: 회색 휴무) | 평일이면 휴식일 +1 |
| `WARD_WORKDAY` | 주말·공휴일을 이 병동에서만 목표일로 셈 | 원래 공휴일 표시 + "근무일 지정" 배지 | 그 날을 목표일로 셈 |

- 국가 공휴일과 병동 항목은 출처를 구분해 보여 준다(예: "국가 공휴일" / "병동 지정"). 병동 항목이 국가 공휴일을 덮었으면 원래 공휴일 이름도 함께 보여 주는 편이 오해가 적다.
- 같은 날 공휴일이 겹치면 서버가 이미 하루로 합쳐서 준다. 클라이언트는 합치지 않는다.
- 근무표 화면(B10 초안 검토)에서 달력 헤더에 같은 규칙으로 공휴일을 표시하면 관리자 검토가 쉬워진다. 이때도 `GET holiday-calendar`를 그대로 쓴다.

## 4. 설정 화면 요구사항 (관리자)

- 월 선택 + 달력 그리드. 각 날짜에 위 규칙의 최종 상태를 표시.
- 날짜를 눌러 병동 항목 추가·변경·삭제: 공휴일 보완(`PUBLIC_HOLIDAY`, 이름 입력), 병동 휴무(`WARD_REST_DAY`), 근무일 지정(`WARD_WORKDAY`, 주말·공휴일에서만 의미 있음). 편집은 로컬에 모았다가 한 번에 PUT.
- 월 목표 요약: `targetDays`, `restDays`, `generalTargetMinutes`(시간 단위로 표시), 내역(`weekendDays`, `holidayDays`, `wardRestDays`, `wardWorkdays`). 저장 응답의 `target`으로 갱신.
- 공휴일 출처 상태: `configured=false`면 "자동 공휴일이 꺼져 있어 직접 입력이 필요"를 안내하고 지금 가져오기 버튼을 숨기거나 비활성화. `fetchedAt`이 있으면 "마지막 갱신 시각"을 병동 현지 시각으로 표시, `null`이면 "아직 가져오지 않음".
- 지금 가져오기 버튼: 임시공휴일 발표 직후 같은 경우용. 서버가 매일 03:30(Asia/Seoul)에 이번 달~12개월 뒤를 자동 갱신하므로 기본 동선에 두지 않는다. 10분 안에 같은 달을 다시 누르면 서버는 호출 없이 저장값을 돌려준다(오류 아님). 버튼 연타 방지를 둔다.
- 목표시간 정책 화면(`target-hour-policy`)은 같은 설정 영역에 두되 별도 저장. 값은 분 단위이므로 시간·분으로 변환해 표시하고, Night 전담 값의 `null`은 "일반 값 사용"으로 표시.
- ETag: 달력 PUT과 정책 PUT은 **병동 공통 정책 revision**을 공유한다. 하나를 저장하면 다른 설정 화면이 가진 ETag가 낡으므로, 저장 응답의 `ETag`를 공통 상태에 반영하고 412가 오면 재조회를 안내한다. 지금 가져오기는 revision을 바꾸지 않는다.

## 5. 시간 진단 표시 (B10 범위)

`GET /rosters/{rosterId}/hour-diagnostics`는 공휴일·병동 달력이 반영된 목표 대비 간호사별 실제·환산·잔여·부족·초과를 준다. 관리자 전용이며 간호사에게 노출하지 않는다. 자정을 넘는 Night는 끝나는 날의 월에 귀속되므로(`carriedInShifts`·`carriedOutShifts`) 숫자를 클라이언트에서 다시 계산하지 않는다.

## 6. 알려진 공백

- **자동배치 미반영**: 초안 생성 엔진(`roster/application/generation`)은 공휴일·병동 달력을 읽지 않는다. 공휴일은 현재 시간 진단과 월 목표에만 쓰인다. 배치 반영은 A19(`feat/generation-engine-v2`) 범위다. 클라이언트는 "자동배치가 공휴일을 고려했다"는 문구를 쓰지 않는다.
- **간호사용 표시 없음**: 위 API가 모두 관리자 전용이라 간호사 앱·웹 근무표에는 공휴일을 표시할 서버 응답이 없다. 필요하면 공개 근무표 응답 확장 또는 간호사용 조회 API를 A 쪽에 먼저 요청한다. 클라이언트에 공휴일 목록을 하드코딩하지 않는다.
- **운영 키**: 배포 환경에 `NURSEFLOW_HOLIDAY_API_KEY`(없으면 `NURSEFLOW_HOSPITAL_API_KEY`)가 설정됐는지는 확인하지 않았다. 없으면 자동 갱신이 꺼지고 수동 입력만 된다(`holidaySource.configured=false`).

## 7. 권장 작업 순서

1. 웹 관리자 휴일 달력·목표시간 설정 화면(B7b 병합 후, `web/**`). 실서버에서 조회→편집→저장→재조회, 412·503 처리 시연.
2. Android 같은 화면(B7a 병합 후, `android/**`).
3. B10a·B10b 초안 검토 화면에 달력 헤더 공휴일 표시와 시간 진단 패널.
4. 간호사 근무표 공휴일 표시가 필요하면 A 쪽 API 추가 후 B11(공개 근무표 화면)에서 반영.
5. 자동배치 공휴일 반영은 A19 결과를 따른다. 클라이언트 변경은 진단 문구 정도로 예상한다.

1·2는 `docs/feature-breakdown.md`에 별도 행으로 추가하거나 B10에 포함할지 먼저 정한다.
