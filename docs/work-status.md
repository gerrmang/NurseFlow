# 공유 작업 현황

## 갱신 규칙

- Claude와 Codex는 기능 단위, PR, 리뷰를 마친 뒤 같은 작업 브랜치에서 이 문서를 갱신한다.
- 진행 상태와 검증 결과는 확인한 사실만 적고, 미검증 항목과 생략 사유를 구분한다.
- 비밀키, 토큰, 실제 간호사 정보와 부재·건강 정보는 기록하지 않는다.
- 새 작업을 시작하기 전에 [기능 분해표](feature-breakdown.md)의 현재 기능 행과 파일 소유 범위, 이 문서의 진행 현황을 확인한다.

## A·B 공유: 확정 결정 요약 (2026-11-10)

- 번호 결정: 1·2·3(A4), 5(A3), 6(B3a), 9(A7), 10·11(A5), 12, 19(B2) 해결, 4 보류. 세부는 [기능 분해 §7](feature-breakdown.md)과 구현 중 확정 정책 §7.1.
- B가 화면에 반영할 서버 동작: 병원 검색은 검색 실행 시 호출·출처 표시(A3), 병동 생성 409 두 종류(`WARD_CREATION_LIMIT_REACHED`, `WARD_ALREADY_CREATED`)와 503(목록 미적재) 안내(A4), 초대 미리보기 `hospitalName` 표시(A4), 팀 순서 409 `TEAM_ORDER_STALE` 시 다시 불러오기(A5).
- 로컬 실연동에 필요한 서버 환경변수: `NURSEFLOW_KAKAO_REST_API_KEY`, `NURSEFLOW_KAKAO_REDIRECT_URIS=http://localhost:5173/auth/kakao/callback`(웹 로그인), `NURSEFLOW_HOSPITAL_DATASET_PATH`(병원 검색·병동 생성). 키 값은 공유 문서·채팅에 두지 않고 Kakao Developers 멤버 초대나 비밀번호 관리자로 공유한다.

## 기능 단위 현황

| 단위 | 브랜치 | PR | 상태 | 검증 | 미해결 리뷰 지적 |
| --- | --- | --- | --- | --- | --- |
| 기능 분해표 | 해당 없음 | #29 | `dev` 병합 완료 | 병합 확인 | 없음 |
| README | 해당 없음 | #28 | `dev` 병합 완료 | 병합 확인 | 없음 |
| A1 초대·경력 월 | `feat/invitation-24h-career-month` | #27 | `dev` 병합 완료 | 병합 확인 | 없음 |
| B1 Android Kakao 세션 | `feat/android-kakao-session` | #30 | `dev` 병합 완료 (2026-11-09, 일반 병합 커밋 `9239fa6`) | 로컬 단위·기기·실서버 검증은 [`B1 검증 기록`](../android/B1-verification.md)에 기록. GitHub CI·PR 리뷰 여부는 별도 확인 | 기록된 미해결 P1 없음. 병합 방식은 아래 참고 |
| A2 웹 세션 API | `feat/auth-web-session-v2` | #33 | `dev` 병합 완료 (2026-11-09, 스쿼시 병합 커밋 `fa47a5a`) | 2026-11-09 로컬 `./gradlew test --offline`: 108건 통과, 건너뜀 1. 동시 state 테스트(`FOR UPDATE` 제거 변이 3/3 실패)와 client_secret 전달(전달 줄 제거 변이 실패) 검출력 확인. 실제 Kakao E2E 미검증(목업만) | 없음 |
| B2 웹 기반 세션 | `feat/web-foundation-session` | 미개설 | 미착수. A2 PR #33이 `dev`에 병합되어 선행 조건 충족 | 해당 없음 | 없음 |
| B9a Android 공개 근무표 조회 | `feat/android-published-roster` (`dev` `fa47a5a` 통합) | [#34](https://github.com/gerrmang/NurseFlow/pull/34) | `dev` 병합 완료 (2026-11-10, 스쿼시 병합 커밋 `d1b8729`). 구현·기기·실서버 검증과 PR P3 수정 완료, 병합 전 Backend tests·Android debug build 통과 | [B9a 검증 기록](../android/B9a-verification.md), 아래 최종 검증 참조 | 기존 P2 해결, P3 수정 후 재리뷰 대기 |
| 에이전트 지침 정합성 | `docs/agent-guidance-alignment` | #32 | `dev` 병합 완료 (2026-11-09, 일반 병합 커밋 `ab8347d`. 스쿼시 원칙에서 벗어남) | `git diff --check` 통과. GitHub Actions `Verify` PR 실행 성공, `dev` push 실행 성공 | 없음 |
| A3 병원 검색 API | `feat/hospital-search` | #35 | `dev` 병합 완료 (2026-11-10, 스쿼시 병합 커밋 `23af850`) | 병합 전 `./gradlew test --offline` 134건 통과, CI 통과. 실제 2026.9. 파일 79,899곳 적재 0.7초, 검색 15~40ms | 없음 |
| A4 병동 생성 | `feat/ward-self-create` | #36 | `dev` 병합 완료 (2026-11-10, 스쿼시 병합 커밋 `6a2cf29`) | 2026-11-10 `./gradlew test --offline` 138건 통과(건너뜀 4). 같은 키 동시 요청 테스트는 중복 키 처리 제거 변이 3/3 실패로 검출력 확인. 이전 스키마 H2에 schema.sql 두 번 적용 테스트 통과 | 없음 |
| A5 경력·팀 내 순서 | `feat/staff-admin-career-adjust` (`/private/tmp/NurseFlow-a5`) | #37 | PR 열림. 결정 10·11 반영. 팀 안 드래그 순서·선임 자격·편성 단계 조정·조정 이력. Codex 리뷰 P2 2건·P3·P4, /code-review 10건 수정. 최신 `dev`(A4) 반영. hyen11 계약 검토(`5feb4f7`)·스쿼시 병합 대기 | 2026-11-10 최신 `dev`(A4) 반영 후 `./gradlew test --offline` 157건 통과(건너뜀 4) | 없음 |
| A6·A7 이후 | 해당 없음 | 미개설 | 미착수. A6은 A4 병합으로 착수 가능, A7은 A6 병합 대기. 결정 4(보류)·9 반영 예정 | 해당 없음 | 없음 |

## 리뷰 상태

- PR #30의 `RealBackendSessionDeviceTest`는 `IsolatedSessionTestRunner`와 테스트 전용 `Application`/`TokenStore`를 사용한다. 앞서 기록된 “개발자 저장 토큰에 접근할 수 있다”는 P1은 이 코드와 맞지 않아 철회했다. 실제 사용자 세션을 사용하지 않는다는 검증 기록은 [`B1 검증 기록`](../android/B1-verification.md)을 참조한다.
- PR #32의 이전 head `5d4f45b`에서 발견한 사항은 수정 head `f10d598`에 반영했다. PR #30 P1 지적은 테스트 전용 runner를 고려하지 않은 오류로 철회했다. `.gitignore`의 광범위한 `.codex/` 제외를 제거하고, Claude·Codex 공통 worktree 규칙과 누락된 Claude 문서 갱신, breakdown의 A1/B1 병합 상태를 추가했다.
- PR #34 (`209de0db506429050885968e9cd2c3d81cc14192`) Codex COMMENT 리뷰를 2026-11-10 게시했다. P3 1건: 기본 MINE 모드는 간호사 기록이 없는 `WARD_ADMIN`에게 서버의 `NURSE_IDENTITY_REQUIRED`를 일반 권한 오류로 보여준다. 전체 조회 권한이 있으면 우회 가능하며 보안 문제는 아니다.

- B1(#30)과 지침 정합성(#32)이 일반 병합 커밋으로 들어갔다. 이후 기능 PR은 GitHub의 "Squash and merge"로 병합한다.

## B9a 인계 (2026-11-09)

- **구현**(`android/**`만): `NurseFlowApi`에 `published-rosters`·`/mine` 조회와 DTO, `feature/roster/PublishedRosterViewModel`·`PublishedRosterScreen`, 병동 카드 선택 → 근무표 화면(`NurseFlowRoot`). 병동 전체는 근무 시간대(병동 `timeZone`)로 표시하며 D/E/N을 추측하지 않는다. `canViewWardCalendar`가 false면 "병동 전체" 탭을 숨긴다. 본인 assignments가 비면 "이번 달 배정된 근무가 없습니다", `PUBLISHED_ROSTER_NOT_FOUND`면 "아직 확정된 근무표가 없습니다"+대상 월로 구분한다. 오류는 `code` 기준(비소속 `WARD_NOT_FOUND` 404는 공개 없음으로 취급하지 않음). 월·모드 요청마다 ticket으로 늦은 응답을 폐기한다. 세션 변경 시 `SessionViewModelStore`가 ViewModel을 비우고 선택 병동도 세션 식별자별로 초기화된다.
- **DemoRoster 분리**: 가상 시연 진입은 로그인 상태(`SignedIn`)가 아닐 때만 노출되고 실서버 조회 실패 시 자동 대체가 없다. (로그인 전 화면 마커로 쓰는 기존 `ActivityRotationDeviceTest`가 복원 중에도 버튼을 기대해 `SignedIn` 외 상태에서 표시하도록 유지.)
- **권한 구분**: `mine`(멤버십만)과 전체 조회(`canViewWardCalendar`)의 권한 차이는 정상적인 권한 구분이며 계약 결함이 아니다.
- **계약 관찰(수정하지 않음)**: 병동 전체 슬롯에는 `shiftTypeId`·시간만 있고 근무 코드가 없으며, `GET /wards/{id}/shift-types`는 OpenAPI에만 있고 backend에는 구현이 없다. 코드명 표시는 후속 계약이 필요하다.
- **실행·결과**(`android/`, 2026-11-09):
  - `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`: `BUILD SUCCESSFUL`. JVM **36개 통과, 실패/오류/건너뜀 0**(신규 `PublishedRosterViewModelTest` 9개 + `ApiIntegrationTest` 1개 추가). 신규 검사: 성공, 본인 빈 배정과 공개 없음 구분, 전체/본인 전환, 권한 없는 전체 탭 차단, 월 이동 중 늦은 응답 폐기, 모드 전환 중 늦은 응답 폐기, 403·네트워크 재시도, 비소속 404, 저장소 정리 후 결과 미반영, 경로·쿼리·오류 코드.
  - 별도 읽기 전용 `Pixel_10` 에뮬레이터(`-read-only`, 사용자 데이터 이미지 미사용, `emulator-5556`)에서 `:app:connectedAndroidTest`: **22개 실행, 건너뜀 3(opt-in), 실패 0**. 기존 `IsolatedSessionTestRunner`로 개발자 저장 세션을 쓰지 않는다. 첫 실행에서 시연 버튼 숨김 때문에 `ActivityRotationDeviceTest` 2개가 실패해 표시 조건을 고친 뒤 통과했다.
  - `git diff --check`: 통과(CRLF 변환 경고만).
- **Codex 리뷰 P2 수정(같은 병동 재진입 시 오래된 상태 유지)**: `NurseFlowRoot`가 병동 선택 때마다 `wardEntry`(세션 식별자별 `rememberSaveable`)를 올리고, `PublishedRosterScreen`이 `LaunchedEffect(entry)`로 `PublishedRosterViewModel.enter(token)`을 호출한다. 첫 진입은 init의 조회가 처리하고, 같은 token(recomposition·Activity 재생성)은 재조회하지 않으며, 새 진입(token 변경)만 현재 월·모드로 재조회한다. 기존 요청 취소·늦은 응답 폐기·세션 격리 로직은 그대로다.
  - 재실행 `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`: `BUILD SUCCESSFUL`. JVM **39개 통과, 실패/오류/건너뜀 0**(`PublishedRosterViewModelTest` 12개). 신규 회귀 3개: 공개 없음 → 서버 공개 → 재진입 시 공개본 표시와 중복 요청 없음, 기존 배정 → 재공개 → 재진입 시 새 배정으로 교체(월·모드 유지), 재진입 전 요청의 늦은 응답 폐기. 이번 수정 후 기기 `connectedAndroidTest`와 `git diff --check`는 재실행하지 않았다(직전 diff 검사는 통과). 실서버 미검증 상태는 유지한다.
- **생략한 검사와 이유**: (1) **실서버 공개 전·후 조회·전체/본인 전환·월 이동**: `backend/**`를 수정할 수 없고 기존 opt-in fixture(`AndroidSessionE2eServerTest`)는 공개 근무표를 시드하지 않아 실행하지 못했다. 위 결과는 모두 목업(MockWebServer·가짜 API)이다. (2) B9a 전용 기기 UI 계측(NotPublished·탭 전환 화면)은 추가하지 않았다. (3) Lint·GitHub CI는 실행하지 않았다.
- **남은 일**: 공개 근무표를 시드한 가상 서버(관리자·간호사 가상 계정)로 공개 전 404 → 공개 후 `mine`/전체 → 월 이동을 실기기에서 확인한 뒤 완료 표시. 같은 worktree·브랜치(`feat/android-published-roster`)에서 이어가며, 필요 시 backend fixture 확장은 별도 단위로 합의한다.

## B9a Codex 로컬 리뷰 (2026-11-09)

- 검토 범위: 현재 B9a tracked diff와 미추적 `feature/roster` 구현·테스트, 세션 ViewModel 소유 구조, 서버 공개 조회 DTO·권한, 기존 opt-in fixture. 애플리케이션 코드는 수정하지 않았다.
- **최초 지적 P2 [데이터/상태], 아래 재검토에서 해결 확인**: `NurseFlowRoot.kt:61`은 세션 저장소에서 병동별 ViewModel을 재사용하지만, 조회는 `PublishedRosterViewModel`의 최초 생성·월/모드 변경·오류 재시도 때만 했다. 병동 목록으로 나갔다가 같은 병동에 재진입해도 서버를 다시 조회하지 않아, 그 사이 공개·재공개한 근무표 대신 이전 배정 또는 `NotPublished` 상태가 유지됐다.
- 검증: `git diff --check` 직접 실행 통과(줄바꿈 변환 경고). 기존 JVM XML 보고서 4개에서 합계 36개, 실패/오류/건너뜀 0 확인. 이번 리뷰에서 Gradle·기기 검사는 재실행하지 않았으며, 앞선 구현자의 실행 기록과 구분한다.
- 실서버 검증은 여전히 미완료다. 기존 `AndroidSessionE2eServerTest`는 계정별 별도 병동과 멤버십만 생성하고 간호사 연결·근무표는 생성하지 않는다. 격리된 가상 데이터로 공개 전·후 및 본인/전체·월 이동 검증을 준비해야 한다. backend fixture 확장은 기존 Android 소유 범위를 넘으므로 별도 범위 합의가 필요하다.
- 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-published-roster`에서 Claude가 위 P2를 수정하고 검증한다. 실서버 확인 전 완료 표시 금지. 커밋·푸시·PR 생성·병합하지 않았다.
- 리뷰 기준: HEAD `ab8347dde6464379c8670cde6f937f408d349eb8`; tracked diff SHA256 `b6cfd8aeeee2a94953bf29143c855f9043dafb47cfb5206ba0b699e4a44e11f7` (`git diff HEAD -- . ':!docs/work-status.md'` 원본 출력). 미추적 파일은 이 diff에 포함되지 않으므로 별도 SHA256도 기록한다: `PublishedRosterScreen.kt` `1b5560d302ad335e5f5148611f256463b26946333ffb809ebe2a200c53cd46fe`, `PublishedRosterViewModel.kt` `68764bd17b7c0d53bb088f829222e3b8e169d9736b39c83a88a032f9ae680558`, `PublishedRosterViewModelTest.kt` `715817372f3aece99706eaa901384642310603459db9af93a0584aaf2492d5e6`. 검토 주체 Codex.

## B9a P2 수정 재검토 (2026-11-09, Codex)

- `wardEntry` 증가 → `LaunchedEffect(entry)` → `enter(token)` 연결과 회귀 테스트 3개를 확인했다. 첫 진입·동일 token에서는 중복 조회하지 않고, 새 진입에서는 현재 월·모드를 유지해 재조회한다. 기존 P2는 해결됐으며 이번 수정 범위에서 추가 조치할 결함은 발견하지 못했다.
- 기존 JVM XML 보고서 합계 39개, 실패/오류/건너뜀 0을 확인했다. Codex가 현재 수정 상태에서 `git diff --check`를 직접 실행해 통과했다(줄바꿈 변환 경고만). Gradle·기기 검사는 이번 재검토에서 재실행하지 않았다. 수정 후 기기 검사 및 실서버 공개 전·후 검증은 여전히 미완료다.
- 같은 worktree·브랜치에서 기기 검사와 격리된 가상 서버의 실연동 검증을 이어간다. 애플리케이션 코드 수정·커밋·푸시·병합 없음.
- HEAD `ab8347dde6464379c8670cde6f937f408d349eb8`; tracked diff SHA256 `66410e40794e40222067c249c692fc76ca704e07ecc39f8383e5f4768eb9e2cf` (앞선 리뷰와 같은 방식). 미추적 파일 SHA256: `PublishedRosterScreen.kt` `e7e70e622e14b1fbd21ac46aaab04b3e08581e67c519b5c30a556105a83ae9e4`, `PublishedRosterViewModel.kt` `56a4af766f076ec972a78ee20ba7129787d411a8bebb9fc1da0f53f52190d9ef`, `PublishedRosterViewModelTest.kt` `42e387e91583fa228eb6c844055d9e067bcd0eb2356c958f5e7acf75203d9e16`.

## B9a 최종 로컬 검증 (2026-11-09, Codex)

- 사용자의 마무리 지시에 따라 같은 공유 worktree·브랜치를 이어받았다. 검증을 위해 `backend/src/test/**`에 별도 opt-in `AndroidRosterE2eServerTest`, `android/app/src/androidTest/**`에 `PublishedRosterDeviceTest`를 추가했다. 서버 운영 코드·API·DB 스키마는 변경하지 않았고, 기존 B1 fixture도 보존했다. 사용자 로컬 DB와 저장 세션은 사용하지 않았다.
- 실제 Spring 서버(loopback 18083), H2 메모리 DB, 가상 OIDC/계정과 실제 Android 화면을 연결했다. 공개 전 404/안내 → 공개 후 재진입 → 본인/전체 전환 → 월 이동 → 재공개 후 같은 월·모드 재진입 → 본인 배정 없음 → 로그아웃/권한 제한 계정 전환 → 전체 403·탭 숨김/본인 조회 성공을 확인했다.
- Android 단위 검사·앱/테스트 APK 빌드·Lint 명령 성공. 단위 검사는 `UP-TO-DATE`이며 XML 39개·실패/오류/건너뜀 0 확인. Lint 오류 0·기존 경고 33개. 수정 후 일반 기기 검사 성공: XML 23개 중 18개 통과·5개 opt-in 건너뜀·실패/오류 0. 이후 B9a opt-in을 별도 실행해 `OK (1 test)`, 11.480초와 서버 fixture 1개 통과를 확인했다.
- 백엔드 전체 검사 `test --no-daemon`: 91개 중 89개 통과·2개 opt-in 건너뜀·실패/오류 0. 이후 별도 opt-in 필터 실행도 `BUILD SUCCESSFUL`이다. 초기 fixture 반환 타입 컴파일 오류와 기기 테스트의 화면 전환 대기 오류를 수정한 뒤 최종 실행이 통과했다. 상세 재현·기존 보고 수치와의 구분은 [B9a 검증 기록](../android/B9a-verification.md)에 있다.
- 앞선 인계의 실서버 미검증 제한은 이번 검증으로 해소됐다. B9a 구현·로컬 인수 검증은 완료했으며 PR/CI/병합은 별도다. 외부 Kakao 인증 성공·운영 서버·물리 기기, GitHub CI·보호 설정은 이번에 검증하지 않았다.
- 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-published-roster`의 미커밋 변경을 이어간다. 커밋·푸시·PR 생성·병합하지 않았다. 테스트 fixture는 앱/서버 배포에 포함되지 않으며 롤백은 B9a 앱 변경을 되돌리면 된다.
- 최종 테스트 대기 보완 후 `:app:lintDebug`도 재실행해 성공했다. 검증 서버 정상 종료와 추가한 reverse 포트 18083/18084 제거, 이번에 시작한 읽기 전용 에뮬레이터 종료를 확인했다. 신규 기기 테스트 SHA256 `d2109000511e3917ed2f2f34e615e3ec51c0fb6d81e1c76d8236dfdea65d7a8f`, 신규 서버 fixture SHA256 `5cefdf9031fdb6e06e009df36c1b3374c4bb0de7c8c8634461e100da5fee8935`; 나머지 구현·단위 테스트의 해시는 위 P2 재검토 기준과 같다.

## B9a PR 개설 (2026-11-09, Codex)

- 사용자 지시에 따라 B9a 파일 12개만 커밋·푸시하고 [PR #34](https://github.com/gerrmang/NurseFlow/pull/34)를 `dev` 대상으로 열었다. 원격 `dev`의 A2 스쿼시 커밋 `fa47a5a`에 rebase했고, 공유 상태 문서 충돌은 A2의 최신 기록과 B9a의 기록을 모두 보존해 해결했다. PR 본문에 **Squash and merge**를 명시했으며 병합·자동 병합 설정은 하지 않았다.
- A2 통합 후 `backend/ .\gradlew.bat test --no-daemon`: `BUILD SUCCESSFUL`, XML 109개 중 107개 통과·2개 opt-in 건너뜀·실패/오류 0. 신규 파일까지 포함한 `git diff origin/dev...HEAD --check`에서 발견한 화면 파일 EOF 공백은 커밋 전에 정리했다.
- PR 구현 head `f4cd1ed64a19097973be1cbff0dcf7066391daad`에서 GitHub Actions [Verify run 42613579824](https://github.com/gerrmang/NurseFlow/actions/runs/42613579824)의 `Backend tests`(1분)·`Android debug build`(1분 14초) 성공을 확인했다. Vercel 및 Vercel Preview Comments도 성공. 강제 보호·승인 규칙은 확인하지 않았다.
- A2 통합 상태의 B9a 실서버 기기 E2E를 재실행해 `OK (1 test)`, 13.549초, 테스트 status 0과 대응 서버 `BUILD SUCCESSFUL`을 확인했다. 최종 문서 갱신 뒤 후속 CI도 확인한다. 병합은 하지 않는다.
- GitHub 연결 도구는 PR 생성에서 403을 반환해, 정상 인증된 로컬 GitHub CLI로 생성했다. 로컬 DB 파일은 미추적 상태로 보존했다. 같은 worktree·브랜치에서 PR #34의 후속 검토를 이어간다.

## B9a PR 리뷰 P3 수정 (2026-11-10, Codex)

- 리뷰 기준: PR #34의 `209de0db506429050885968e9cd2c3d81cc14192`, gerrmang의 COMMENT 리뷰 1건. 간호사 연결 기록이 없는 관리자에게 `NURSE_IDENTITY_REQUIRED` 403을 일반 권한 거부로 안내하던 P3를 확인했다.
- `RosterContent.NurseIdentityRequired`를 추가하고 일반 403보다 먼저 오류 코드로 분기한다. 화면에는 "본인 근무표에 연결된 간호사 정보가 없습니다."를 표시하고, 전체 조회 권한이 있을 때만 "병동 전체 보기" 버튼을 제공한다. 전체 조회 권한 검사와 서버 판정은 유지한다.
- 신규 단위 회귀 2개: 간호사 정보 미연결과 권한 거부 구분 및 전체 전환, 정보 미연결이어도 전체 권한 없는 계정은 전체 API 미호출. 기존 실서버 기기 시나리오에 간호사 기록 없는 관리자 로그인·실제 오류 코드·정확한 안내·전체 보기 성공을 추가했다. 서버 코드·API·DB·환경 설정·의존성 변경은 없다.
- Android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: 성공. XML 41개 통과, 실패/오류/건너뜀 0. Lint 오류 0·기존 경고 33개. `:app:connectedAndroidTest` 성공: XML 23개 중 18개 통과·5개 opt-in 건너뜀·실패/오류 0. 이후 B9a opt-in 실서버 기기 검사 `OK (1 test)`, 11.790초, 테스트 status 0과 대응 서버 `BUILD SUCCESSFUL` 확인. 관리자 정보 미연결 안내와 전체 보기 버튼이 실제 서버 응답으로 동작한다.
- opt-in 서버 검사가 캐시 결과를 재사용하는 것을 확인해, 실제 실행이 필요한 재현 명령에 `test --rerun`을 명시했다. 같은 worktree·브랜치에서 PR #34를 갱신하며, 로컬 DB는 보존하고 스쿼시 병합은 수행하지 않는다.
- `git diff --check` 통과. 백엔드 전체 검사는 서버 코드 변경이 없어 재실행하지 않았고, 실서버 경로는 위 opt-in 서버 검사로 검증했다. 외부 Kakao 인증·운영 서버·물리 기기는 이번에 검증하지 않았다. 수정 후 독립 재리뷰는 아직 받지 않았다.

## 기타 브랜치

- `feat/android-auth-invite` (`95cf33d`): **병합 대상에서 제외(2026-11-09 사용자 결정).** 현재 A/B 기능 분해 이전(11/2~11/3)의 백엔드+Android 통합 시제품 브랜치다. 같은 기능이 #17~#20, A1(#27), B1(#30)로 따로 병합됐고, `dev`와 합치면 174개 파일이 충돌하며, 병동 직접 생성(`74c9036`)은 #24 이후 A3·A4로 다시 기획됐다. 원격에 참고용으로만 남기고, B3a(첫 화면 선택·초대 가입 화면)·B4a(병동 생성 화면) 작업 때 최신 `dev`에서 새로 구현하며 참고한다. 이 브랜치에서 PR을 열거나 이어서 작업하지 않는다.

## 리뷰 지점 기록

Claude와 Codex가 같은 진행 기록을 사용한다. PR 또는 정기 리뷰를 마칠 때 공유 worktree별 경로, 브랜치, 리뷰한 `HEAD` 커밋 SHA, 미커밋 diff 해시, 시각, 검토 주체를 기록한다. 해시는 재현 가능하도록 `git diff HEAD -- . ':!docs/work-status.md' | shasum -a 256`으로 계산해 상태 기록 파일 자체를 제외한다. 인계받은 에이전트는 먼저 이 기준점을 확인하고 변경된 경우 새 검토 지점을 추가한다. 로컬 도구별 상태 파일을 유일한 기록으로 삼지 않는다.

| 작업 경로 | 브랜치 | 리뷰한 HEAD SHA | 미커밋 diff 해시 | 리뷰 시각 | 검토 주체 | 범위/결과 |
| --- | --- | --- | --- | --- | --- | --- |
| `C:\Users\jjang\IdeaProjects\NurseFlow` | `feat/android-published-roster` | `ab8347dde6464379c8670cde6f937f408d349eb8` | `66410e40794e40222067c249c692fc76ca704e07ecc39f8383e5f4768eb9e2cf` (tracked diff, 신규 파일 해시는 위 기록) | 2026-11-09 | Codex | 기존 P2 해결 확인 후 기기 회귀·실서버 공개/재공개·권한 검증 완료. 같은 미커밋 브랜치에서 PR 준비 가능. 운영 코드 추가 수정 없음 |
| GitHub PR #32 diff (`/private/tmp/NurseFlow-guidance`) | `docs/agent-guidance-alignment` | `5d4f45bcf7e5247d85f9f1a38fd0a59a081c499e` | `bffafc453496740f281aca22552cd1dcaf80516a2150a8bb741aa42efbdca3ab` (`git diff origin/dev...origin/pr-32 | shasum -a 256`) | 2026-11-09 | Codex | PR 메타데이터·diff·리뷰·체크 및 B1 격리 테스트 경로를 재검토. PR은 열림; GitHub 리뷰 없음, 결합 상태에서 Vercel만 성공 확인. 재검토 사항은 위에 기록 |
| GitHub PR #34 diff | `feat/android-published-roster` | `209de0db506429050885968e9cd2c3d81cc14192` | `635976c6eab08175715a8813697306c26a4f43895ca820571c9c4f98cbe244d7` (`git diff fa47a5afc1bf3b92ed2cb7436e711ba2d57a8295..origin/feat/android-published-roster | shasum -a 256`) | 2026-11-10 | Codex | 12개 파일 읽기 전용 검토. P3 1건을 COMMENT 리뷰로 게시; Verify run 42613978040 성공 |
| `/private/tmp/NurseFlow-guidance` | `docs/agent-guidance-alignment` | `f10d598b330de17a47a954558196e8df0be4a4ec` | `e638b1aa27f5c0cccc9f855d1cb5b9c19834bd1b1410a80b260b69a29cbb5fb5` (`git diff HEAD^ HEAD -- . ':!docs/work-status.md' | shasum -a 256`) | 2026-11-09 | Codex | Claude·Codex가 같은 worktree·브랜치·상태 기록·검증 기준을 이어 쓰도록 정리. `git diff --check` 통과. PR #32에 푸시 완료 |
| `/private/tmp/NurseFlow-a2-review` | `feat/auth-web-session-v2` | `9a8ef4d64a5f50579853fada8ce3b823d66ec962` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`(미커밋 변경 없음) | 2026-11-09 | Codex | A2 전체 읽기 전용 리뷰. P1 없음. P2(테스트) 1건: 같은 state 동시 교환 테스트 부재. state 소비·refresh 회전 행 잠금, redirect URI 정확 일치, nonce·aud·iss·exp, 쿠키 속성, CSRF 인터셉터 순서, 오류 매핑, 로그 비노출, Android 경로 무변경, OpenAPI 일치 확인 |
| `/private/tmp/NurseFlow-a2-review` | `feat/auth-web-session-v2` | `0ab0a5449d2e3a5aef5ac3eb2e6c5500ea65b6f0` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | 2026-11-09 | Claude | 위 P2를 동시 콜백 테스트로 수정. Claude가 지적을 코드와 대조해 확인한 뒤 수정했고 Codex 재리뷰는 하지 않음 |
| GitHub PR #33 리뷰 | `feat/auth-web-session-v2` | `f30a59bd971b4cd7304b5bbc9a6478354e439f46` | 해당 없음(PR diff 기준) | 2026-11-09 | hyen11 | P1·P2 없음. P3 기본 포트 Origin 정규화, P4 client_secret 회귀 테스트, P5 OpenAPI cross-site 조건 |
| `/private/tmp/NurseFlow-a2-review` | `feat/auth-web-session-v2` | `00cbffff08cbc17490bd8db2f8194fde3d23b0ad` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`(미커밋 변경 없음) | 2026-11-09 | Claude | 위 P3(`b6d56aa`)·P4(`e371fa5`)·P5(`00cbfff`) 수정. 재리뷰 전 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/hospital-search` | `08180db18edd7512e5033390c6469a76f2b8e75e` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`(미커밋 변경 없음) | 2026-11-10 | Codex | A3 전체 읽기 전용 리뷰. P2(데이터) 1건: 닫히지 않은 따옴표가 뒤 행을 삼켜 최소 행 수만 넘으면 대량 비활성화. 트랜잭션 롤백, 해시 건너뛰기, zip 선택, LIKE 와일드카드 비노출, 키 비로깅, OpenAPI 일치 확인. Gradle 실행은 샌드박스 제한으로 못 함 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/hospital-search` | `56434503bf8c763e837071a10890fd58580c32a4` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | 2026-11-10 | Claude | 위 P2 수정: 닫는 따옴표 뒤 구분자 강제·미종결 따옴표 오류·10% 초과 비활성화 거부·적재 ID 기반 비활성화. Codex 재리뷰 전 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/hospital-search` | `283ffb6fcea6971094f593df9c2f113685a73c67` | 미커밋 변경 없음 | 2026-11-10 | Codex | P2 수정(`5643450`) 재리뷰. P1/P2 없음, 병합 가능 판정. P3(런타임) 1건: 길이 제한이 따옴표 필드에만 적용 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/hospital-search` | `a68b578984da0ea3d8c4f93b7044b1ea8eacf671` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P3 수정(모든 필드 2,000자 제한)과 테스트 보강. 야간 QA 결과는 A3 행 참조 |
| GitHub PR #35 리뷰 | `feat/hospital-search` | `68b59814641f6341e0bfe611946db8b713ef3834` | 해당 없음(PR diff 기준) | 2026-11-10 | hyen11 | B4a/B4b 소비자 관점 계약 검토. P1/P2 없음. 응답 필드·200 빈 결과/503 구분은 화면에 충분. P3 2건: 정규화 후 최소 길이의 계약 명시·공통 검증, 기호 무시·정렬의 보장 범위 명시 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/hospital-search` | `a3755fd3438ac30d3f168cc82e631c4a627ba12b` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P3 2건 수정: 두 경로 공통 최소 길이 검증, 대체 경로는 외부 API 일치·정렬을 따른다고 계약에 명시. hyen11 재확인 전 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `aa91e6915da63f22efa82c89196dc69f10a1d56e` | 미커밋 변경 없음 | 2026-11-10 | Codex | A4 전체 읽기 전용 리뷰. P1/P2 없음. 권한 분리·목록 기반 병원 검증·멱등 트랜잭션 경계 확인. P3(테스트) 업그레이드 검사가 메모리 DB만 사용, P4(문서) breakdown 요청 예시 불일치 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `918e34682b125370fe4f472ddfbfc247c2013eb8` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P3·P4 수정: H2 파일 DB를 닫고 다시 열어 업그레이드 검증, 요청 예시 정정 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `6a284e0fc5078bfdb07d2dc179acbe391e4da1a1` | 미커밋 변경 없음 | 2026-11-10 | Claude(/code-review) | 10건 중 7건 수정: 초대 미리보기 hospitalName, 공백만 다른 재시도 replay, 멱등 저장소 공용화, 병원 확인 조회 순서·존재 확인 비용, 행 매퍼 중복, source 상수, 대체 경로 계약 명시. 미수정 3건(생성 횟수 제한, 잠금 시간 초과 시 500, replay JSON 형식 변경 호환)은 PR 코멘트에 사유와 함께 기록 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `4d3f73d7d4a5a4e7ac49851b430e928561491222` | 미커밋 변경 없음 | 2026-11-10 | Codex | 상한·공용 replay 이후 재리뷰. REQUEST CHANGES: P2 잠금을 기다린 같은 키 요청이 중복 오류로 실패, P3 새 오류 코드 계약 누락. roster 멱등 동작·잠금 순서·USER_NOT_FOUND는 문제없음 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `2ab2b92c9e9e8a436b0dbe04c8e12e1f45d612e7` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P2·P3 수정: 계정 잠금 직후 replay 재확인, 같은 키 동시 테스트 강화(변이 3/3 검출), 계약·README 보강 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `fbbfe031f0f285da4d54357db497aa13a21cbc82` | 미커밋 변경 없음 | 2026-11-10 | Codex | P2 수정 확인: 잠금 뒤 replay 재확인은 READ_COMMITTED에서 타당, 부분 쓰기 없음, 계약·README 일치. 남은 P3(테스트): 동시 테스트가 경합 구간을 매번 강제하지 못함. 서비스에 테스트용 동기화 지점이 필요해 수정하지 않음 |
| GitHub PR #36 리뷰 | `feat/ward-self-create` | `d82fa2da5778601b3ade50367005b143250e649b` | 해당 없음(PR diff 기준) | 2026-11-10 | hyen11 | P1~P3·P5 없음. P4(테스트) 2건: 잠금 뒤 replay 재확인 경로를 강제하지 못함, 다른 계정 같은 키 경합·롤백 미검증 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `bbd7b550202490ccdbed8f14823f2b03ca5b3da3` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P4 2건 수정: 테스트 전용 하위 클래스로 순서 강제. 두 경로 변이 각 3/3 검출 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `2a4967badad87b4781a4b30825ea04d6d3c48e2b` | 미커밋 변경 없음 | 2026-11-10 | Codex | A5 리뷰. P2 생성·활성 변경이 병동 잠금 밖, P2 비활성 간호사 순번 잔존, P3 동일 경력 재저장 이력, P4 계약 required·테스트·B5 범위. 권한·단계 범위·roster 코드 무영향 확인 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `68c6b412ba1b16f71a347adfae4990b1dbd17e97` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 지적 모두 수정. Codex 재리뷰 전 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `8295380c5f148acd9154e4bd77e55a46fc0f65fe` | 미커밋 변경 없음 | 2026-11-10 | Claude(/code-review) | A5 10건 모두 수정: 이력 seq 정렬, 팀 삭제·이동 잠금, 편성 설정 행 잠금, 활성 변경 시에만 병동 잠금(lockWard 재사용), 본인 화면 MyStaffProfile, stageOverride 필수, 순번 연속, 죽은 검사 제거, 조인 개선 |
| GitHub PR #37 리뷰 | `feat/staff-admin-career-adjust` | `9d16d482a78219d143abefbeaa338996af1b7a47` | 해당 없음(PR diff 기준) | 2026-11-10 | hyen11 | P2 2건: 잠금 여부를 잠금 밖 조회로 정해 활성화·순서 저장과 경합(중복 순번), 경력 입력·확정이 행 잠금 밖에서 이전 값을 읽어 이력 왜곡 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `4fd88149eba66b4428e0bd46b8f77d9aa7deec00` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P2 2건 수정: 간호사 수정 항상 병동 잠금, 경력 경로 행 잠금. 순서 강제 테스트 2건, 변이 각 3/3 검출 |
| GitHub PR #37 리뷰 | `feat/staff-admin-career-adjust` | `53ed3dcfaf68da433dc2ffdb025647c4860e50a2` | 해당 없음(PR diff 기준) | 2026-11-10 | hyen11 | P2: 팀 이동·해제 시 이전 팀 순번에 빈칸(1,3)·선임 2부터 시작 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `fbf71cb5a71a00e1b6097cb9420895d74f24a186` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P2 수정(팀 이동·해제에 순번 정리). 같은 패턴 점검 결과 A6 구성원 비활성화 경로가 남아 A6 dev 반영 때 처리 예정 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `87e1d1f3d1075a77cf0df545bce7db03a9958c78` | 미커밋 변경 없음 | 2026-11-10 | Codex | 팀 이동 순번 수정 재리뷰(`53ed3dc..HEAD`). 지적 없음. 잠금 안 읽기·판단·쓰기, 이력 이전 값, 잠금 순서(권한→병동→대상 행), staff 패키지의 순번 해제 경로 전수 확인 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `931686c9c1f790bd030bc6e5c76bdf31a5ad313e` | 미커밋 변경 없음 | 2026-11-10 | Codex | PR 전체 불변 조건 감사(I1~I8). I1·I2·I3·I5·I6·I7·I8 통과. I4 TEAM_ASSIGNED가 팀 이름을 기록(설계상 화면용 문자열로 판단, 계약 명시), P3 순서 저장의 잠금 전 팀 확인 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `16258026d63b7a5921a50743772ebe4a22b4e006` | 미커밋 변경 없음 | 2026-11-10 | Claude | 무작위 불변 조건 테스트 추가, 이 테스트가 찾은 '삭제된 팀의 활성 간호사' 수정, 위 P3 수정, I4 계약 명시 |

이 문서는 Claude와 Codex가 각 기능 단위 작업 후 진행 결과를 함께 기록하는 단일 공유 현황이다.
