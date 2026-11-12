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
| B2 웹 기반 세션 | `feat/web-foundation-session` (`dev` `e4ca9bf` 반영) | [#39](https://github.com/gerrmang/NurseFlow/pull/39) (Ready) | 구현·주요 실제 Kakao 검증·A 리뷰 수정 완료. B3a 병합 후 최신 dev 통합 | 리뷰 수정 head `d47eb38`: 웹 55개 통과·빌드 성공, CI 3개 작업 성공. 통합 후 검증은 아래 2026-11-11 기록 | 개발 의존성 moderate 경고·기존 브라우저 검증 제한 유지. A 진단 후속은 별도 미반영 |
| B9a Android 공개 근무표 조회 | `feat/android-published-roster` (`dev` `fa47a5a` 통합) | [#34](https://github.com/gerrmang/NurseFlow/pull/34) | `dev` 병합 완료 (2026-11-10, 스쿼시 병합 커밋 `d1b8729`). 구현·기기·실서버 검증과 PR P3 수정 완료, 병합 전 Backend tests·Android debug build 통과 | [B9a 검증 기록](../android/B9a-verification.md), 아래 최종 검증 참조 | 기존 P2 해결, P3 수정 후 재리뷰 대기 |
| B3a Android 초대 관리·가입 | `feat/android-invite-join` | [#38](https://github.com/gerrmang/NurseFlow/pull/38) | `dev` 병합 완료 (2026-11-11, `e4ca9bf`). GitHub merged=true 확인 | 수정 head `691312c`: 단위 76개·빌드·Lint 성공, CI Backend·Android 성공. 기기 실행 제한은 기존 기록 유지 | 기존 알려진 제한은 android/B3a-verification.md 참조 |
| B3b 웹 초대 관리·가입 | `feat/web-invite-join` (`b352759` 기반 구현 `ff63354`) | [#45](https://github.com/gerrmang/NurseFlow/pull/45) (Draft) | 구현·코드 재리뷰 완료. 실제 Kakao 1계정 로그인, 가상 OIDC 3사용자+실제 backend의 발급·재발급·철회·간호사/관리자 가입을 브라우저에서 확인. 실제 Kakao 2계정 완료 조건은 미충족 | 2026-11-12 Windows 계약·Lint·타입·테스트 8파일 241개·빌드 성공. 실제 복사→붙여넣기·URL 정리·390×844 화면 확인. 개발용 재현 도구 추가. [B3b 검증 기록](../web/B3b-verification.md) | 두 번째 실제 Kakao 계정 없음. 실제 2계정 가입·저장소 값/Referer·실제 만료/응답 유실 등과 최종 CI는 미검증 |
| 에이전트 지침 정합성 | `docs/agent-guidance-alignment` | #32 | `dev` 병합 완료 (2026-11-09, 일반 병합 커밋 `ab8347d`. 스쿼시 원칙에서 벗어남) | `git diff --check` 통과. GitHub Actions `Verify` PR 실행 성공, `dev` push 실행 성공 | 없음 |
| A3 병원 검색 API | `feat/hospital-search` | #35 | `dev` 병합 완료 (2026-11-10, 스쿼시 병합 커밋 `23af850`) | 병합 전 `./gradlew test --offline` 134건 통과, CI 통과. 실제 2026.9. 파일 79,899곳 적재 0.7초, 검색 15~40ms | 없음 |
| A4 병동 생성 | `feat/ward-self-create` | #36 | `dev` 병합 완료 (2026-11-10, 스쿼시 병합 커밋 `6a2cf29`) | 2026-11-10 `./gradlew test --offline` 138건 통과(건너뜀 4). 같은 키 동시 요청 테스트는 중복 키 처리 제거 변이 3/3 실패로 검출력 확인. 이전 스키마 H2에 schema.sql 두 번 적용 테스트 통과 | 없음 |
| A5 경력·팀 내 순서 | `feat/staff-admin-career-adjust` | [#37](https://github.com/gerrmang/NurseFlow/pull/37) | `dev` 병합 완료. GitHub 메타데이터와 `origin/dev`에서 `bdc96a6` 확인 | 기존 검증 기록은 아래 보존. 이번 문서 갱신에서 A5 검사를 재실행하지 않음 | 없음 |
| B 검증 후속(Kakao 진단·초대 계약) | `fix/kakao-diagnostics-invite-contract` (`/private/tmp/NurseFlow-fix`) | [#40](https://github.com/gerrmang/NurseFlow/pull/40) `dev` 병합 완료(2026-11-11) | B2·B3a 실제 Kakao 검증 기록에 따른 A 후속. 토큰 교환 401 오류 코드 로그(KOE010), 초대 수락 계약 정정(동작 변경 없음), README 초대 설명, 수정·재리뷰 원칙 | 2026-11-10 `./gradlew test --offline` 150건 통과(건너뜀 4). 수정 전 401 본문 유실 재현 후 수정 | 없음 |
| A6 구성원 관리 | `feat/membership-admin` (`/private/tmp/NurseFlow-a6`) | PR #41 dev 병합 완료(`1205f04`) | 구현·Codex 리뷰 완료. 구성원 목록·관리자 지정/해제·비활성화·재활성화, 마지막 활성 관리자 보호(결정 4, 복구 절차 미구현), 비활성 구성원 즉시 접근 차단. dev 반영 후 두 가지 수정: 구성원 비활성화 시 연결 간호사의 팀 순번 정리(`7dae5c0`), 잠금 대기 중 권한 해제된 관리자의 변경 차단 `WardService.lockAsAdmin`과 초대 재발급·수락 잠금 순서 정정(`b1def48`) | 2026-11-11 `./gradlew test --offline` 209건 중 통과 204, 건너뜀 5, 실패 0(최신 dev `#40` 반영 후). 리뷰 P1(비활성 구성원 기록 재활성화로 편성 복귀) 수정 `d81916e`(MEMBER_INACTIVE, 편성 필터에 활성 WARD_MEMBER 요구), 본인 근무표 조회에도 같은 필터 `b674f92`. 외부 리뷰 P1(관리자 승격 시 활성 간호사 기록 → 편성 계산) 수정 `71dd50e`·`0d1c502`(결과 상태 검사, 관리자 역할 기록 재활성화 차단, 편성 조회 방어 필터), 예외 경로 취소·대기 테스트 DB 잠금 확인 `ac3005b`. PR #41 리뷰 P2(실행 중 자동배치 취소가 병동 잠금에 막힘) 수정 `4ca4e0d`, 재리뷰 P2·P3(취소와 결과 반영·조기 실패 경합, 테스트 동기화) 수정 `2b5640d`·이번 커밋. 순번 정리 제거 시 새 테스트 6건 실패, 잠금 뒤 재확인 제거 시 경합 테스트 2건 실패로 검출력 확인  2026-11-12 불변 조건 무작위 테스트 `77e2eae`(보호 코드 7개 중 6개 제거 시 검출). PR #41 재리뷰 P2(구성원 비활성화와 본인 프로필 쓰기 경합) 수정: 본인 쓰기도 `WardService.lockAsMember`로 병동 잠금 뒤 소속 재확인, 순서 강제 회귀 테스트 추가(수정 제거 시 200으로 실패 확인). P5(관리자 지정 충돌 조건 문서) 정정. `./gradlew test --offline` 218건 중 통과 213, 건너뜀 5, 실패 0 | A8 병합 시 근무 유형 쓰기도 `lockAsAdmin` 사용 필요 |
| 병원 목록 동봉(A3 후속) | `feat/hospital-bundled-list` (`/private/tmp/NurseFlow-hospital`) | PR 개설(최신 dev `1205f04` 반영) | B 개발 환경에서 경로 설정 없이 병원 검색·병동 생성이 되도록 공식 목록 5개 열(2026.9., 79,899행, gzip 약 2.5MB)을 서버에 동봉. 경로가 없으면 시작 시 같은·더 새 목록이 없을 때만 가져옴. 갱신 스크립트 `backend/scripts/extract_hospital_list.py`. Codex P4 2건 수정 `1a24ff9`. PR 리뷰 P3·P4·P5 수정: 동봉 목록이 마지막 적재와 다르면 가져오고(같은 기간 정정본 반영) 더 이전 기간·기간 미상이면 건너뜀, 스크립트는 1만 행 미만이면 기존 목록을 건드리지 않고 종료, 기간 미상 적재 뒤 복구 방법 문서화 | 2026-11-12 dev `1205f04` 반영 후 `./gradlew test --offline` 223건 중 통과 218, 건너뜀 5, 실패 0(리뷰 수정 후). web `api:check` 통과. 패키징된 jar를 별도 DB로 두 번 실행해 첫 실행 79,899행 가져옴, 두 번째 건너뜀 확인 | 없음 |
| A7 이후 | 해당 없음 | 미개설 | 미착수. A8 `feat/shift-type-settings`는 구현·리뷰 완료, 병원 목록 PR 다음에 dev 반영·`lockAsAdmin` 적용 후 PR | 해당 없음 | 없음 |

## B3b 브라우저·실제 backend 검증 및 Draft 준비 (2026-11-12 저녁, Codex)

- 기존 공유 worktree에서 `feat/web-invite-join`(`ff63354`)으로 복귀했다. B4a 코드·커밋은 가져오지 않았으며 사용자 DB를 보존했다. 기존 `b352759`는 현재 조회한 `origin/dev`를 포함한다.
- Windows Node v24.15.0: `npm run api:check`·`npm run lint`·`npm run typecheck`·`npm test -- --run`·`npm run build` 모두 성공. **8파일 241개 통과**, 실패·건너뜀 0, JS gzip **94.25 kB**. 이번에는 npm ci·변이 검사를 재실행하지 않았다. 재현 도구 추가 후 node --check·Lint도 성공.
- 사용자가 알려준 Ubuntu 기존 키 파일을 값 출력 없이 사용해 실제 Kakao용 backend(8081, 별도 메모리 DB)·웹(5173)을 실행했다. 사용자가 직접 1계정 로그인해 병동 목록·소속 없음 화면 진입 성공. 합성한 없는 초대 토큰의 실제 오류 안내와 fragment 제거도 확인했다. 키 파일·실제 계정 정보를 문서에 복사하지 않았다.
- 사용자가 두 번째 Kakao 계정이 없다고 알려, 별도 루프백 OIDC 테스트 공급자(18090)·실제 backend(18091, 별도 메모리 DB)·웹(5174)에서 가상 관리자/간호사/추가 관리자 3사용자 흐름을 검증했다. 실제 Spring 인증·JWT 서명/state/nonce·초대 API·DB를 사용하고 외부 공급자만 가상이다. 실제 계정 서버와 쿠키 이름도 분리했다. 실제 Kakao 2계정 시연 완료로 주장하지 않는다.
- 브라우저 결과: 기록 연결 발급·목록·현재 origin 웹 링크 복사→실제 Ctrl+V·재발급과 원본 철회·로그아웃 후 링크 진입→다른 가상 사용자 로그인→초대 복귀·미래 연월 오류·정상 간호사 가입·관리자 측 가입 완료. 연결 없는 초대 발급/철회 후 패널의 복사 버튼 제거, 관리자 초대의 연월 입력 없음·추가 관리자 가입·초대 관리 권한까지 확인했다. 사용된 링크 재진입의 오류와 닫기, 390×844 화면의 문구/버튼·가로 넘침 없음도 확인하고 viewport를 원복했다.
- 임시 공급자를 `web/scripts/b3b-browser-fixture.mjs`로 보존했다(모듈 import 정리, 검증 로직 동일). 개발 전용·루프백·가상 계정/키이며 앱 번들에 포함되지 않는다. 실제 API로 시드하는 재현 명령과 실제/가상 구분은 `web/B3b-verification.md`에 있다. 신규 앱 결함은 발견하지 않았으며 운영 앱·서버 코드·API/DB 스키마는 수정하지 않았다.
- 남은 조건: 실제 Kakao 2계정 가입은 두 번째 계정 부재로 미실행. 저장소 값 직접 읽기·Referer·전체 히스토리·권한 차단·실제 만료/응답 유실·연결 없는 간호사 가입 등은 이번 브라우저 검증에서 확인하지 않았다. 목업 테스트를 실제 외부 계정 검증으로 대신 기록하지 않는다. Draft PR에 이 조건을 명시한다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-invite-join`에서 개발용 재현 도구·두 검증/현황 문서만 추가 커밋·푸시하고 Draft PR을 게시한다. backend·OpenAPI diff 없음, `git diff --check` 통과. 서버 8081/18091·웹 5173/5174·가상 공급자 18090과 각각의 브라우저 탭은 검증 후 실행 상태다. 메모리 DB라 종료 시 시연 데이터가 사라진다. 사용자 DB·local.properties·Ubuntu 키 파일 보존, 병합·배포 없음.

## B3b Draft PR #45 게시 (2026-11-12, Codex)

- 검증 기록·개발용 가상 OIDC 도구를 `2ee90bb`에 커밋·푸시하고 [PR #45](https://github.com/gerrmang/NurseFlow/pull/45)를 `gh`로 생성했다. `OPEN`, `isDraft=true`, 대상 `dev`, 게시 head `2ee90bbb6af0a7651b3573cf3c6206531d290c45`를 확인했다. 실제 Kakao 2계정 인수 검증 미완료를 Draft 사유로 명시했다.
- 게시 직후 [Verify 43225192104](https://github.com/gerrmang/NurseFlow/actions/runs/43225192104)의 Backend tests·Android debug build·Web checks는 실행 중이었다. 최종 문서 커밋의 CI는 해당 head로 별도 확인한다. CI 통과·독립 승인·병합 준비 완료를 주장하지 않는다.
- 같은 공유 worktree·`feat/web-invite-join`에서 이 게시 기록만 후속 커밋·푸시한다. 기존 검증 후 앱 코드는 바뀌지 않아 자동 검사·브라우저 검사를 재실행하지 않았다. `git diff --check` 통과, 미추적 사용자 DB 보존. 실제 2계정이 준비되면 본문 5절의 외부 계정 인수 검증을 이어가며 병합·배포는 하지 않는다.

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

## B3a 인계 (2026-11-10, Claude)

- **작업 공간:** 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, 브랜치 `feat/android-invite-join`, 분기점 `23af850`(= `origin/dev`). 검증된 B3a 변경을 첫 커밋으로 `origin/feat/android-invite-join`에 푸시했다. 진행 순서(사용자 확정)는 B3a 첫 커밋·푸시 → B2 실제 Kakao·브라우저 검증과 커밋 → 이 브랜치로 돌아와 B3a 실제 검증이다.
- **보존한 미추적 항목:** `nurseflow.mv.db`와 `web/node_modules`(B2 브랜치에서 무시되던 잔여물)는 B3a 범위가 아니다. 커밋에 넣지 않는다.
- **B2 기록:** B2 브랜치(`feat/web-foundation-session` `767abd6`)의 이 파일 인계 기록은 dev에 없으므로 이 브랜치에 가져오지 않았다.
- **결정 (사용자 확정):**
  - §7 #6: 개발용 `nurseflow://invite#token=`과 명시적 붙여넣기를 쓴다. 운영 HTTPS App Links는 후속이다.
  - §7 #7: 역할 기반 고정 대상 문구를 쓰고 메모는 없다.
  - §7 #21: A 소유 테스트 전용 `backend/src/test/.../AndroidInvitationE2eServerTest.kt` 1개를 B3a 범위로 승인받았다.
  - 공통 정책 문서 반영은 별도 결정 반영 문서 PR에서 한다.
- **계획과 달라진 점:** `MainActivity`를 `singleTask` 대신 `singleTop`으로 했다. Kakao 로그인 중인 Activity가 지워질 위험과 기존 `ActivityScenario` 테스트와의 비호환 때문이다. 이유와 검증은 검증 기록에 있다.
- **문서와 코드 차이 (후속 A 또는 문서 PR):**
  - OpenAPI의 WARD_ADMIN `hospitalJoinedOn` 서술
  - redeem 오류 목록의 `INVALID_HOSPITAL_JOIN_MONTH`·`USER_NOT_FOUND` 누락
  - `backend/README.md`의 `expiresInHours` 서술
  - §7 #8 미결 표기 (구현은 재발급 시 원본을 철회함)
- **다음 단계:**
  1. PR 생성은 별도 승인 후. GitHub CI 확인
  2. Codex 리뷰 (P1/P2 수정)
  3. 오류 상태 화면 캡처 (가상 데이터)
- **실제 Kakao 검증 (2026-11-10 저녁):** 사용자 실제 계정 1개, 임시 에뮬레이터·임시 DB, 가상 병동·초대. 결과는 검증 기록의 같은 이름 절. 서버 로그 WARN·ERROR 0, 토큰 0회. logcat의 시드 토큰은 검증용 adb 명령(adbd)에만 있었다.
- **미검증:** 두 실제 계정 전환, KakaoTalk 앱 로그인, 클립보드 복사·가림, 실제 메신저의 사용자 스킴 링크 표시, 물리 기기, 화면에서의 명시적 로그아웃 시 대기 초대 삭제(기기 테스트로만 확인).

## B3b 인계 (2026-11-12, Claude)

- **작업 공간:** 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, 브랜치 `feat/web-invite-join`, 기준 HEAD `0ed5b4c2bb7443d719180433c3593ad9c3b579ba`(= `origin/dev`). 원격 추적은 해제했다(`dev`로 잘못 푸시되지 않도록). **커밋·푸시·PR은 하지 않았고 모든 변경이 미커밋 상태다.**
- **B4a 보존:** `feat/android-ward-create`(`d7ce955`, origin과 동일)에서 `git switch -c feat/web-invite-join origin/dev`로 분기했다. B4a 변경·커밋은 이 브랜치에 없고 `git log origin/dev..HEAD`는 비어 있다. 미추적 `nurseflow.mv.db`는 건드리지 않았다.
- **변경 범위:** `web/` 안(신규 `src/invite/*`, `src/pages/JoinInvitePage.tsx`, `InvitationAdminPage.tsx`, 테스트·가짜 서버 파일, 수정 `App.tsx`·`main.tsx`·`KakaoCallbackPage.tsx`·`LoginPage.tsx`·`WardsPage.tsx`·`nurseflowApi.ts`·`types.ts`·`styles.css`·기존 테스트 설정·`README.md`·`B3b-verification.md`)과 이 문서뿐이다. `backend/**`·`docs/openapi.yaml`·공통 정책 문서는 수정하지 않았다.
- **사용자 승인 결정(2026-11-11):** (1) 로그인 왕복용 sessionStorage 제한 저장(초대 토큰 1개·`expiresAt`, 로그인 버튼에서만 저장, 읽기→삭제→검증→복원, 10분은 논리 만료, 삭제 실패 시 자동 복원 중단), (2) 현재 웹 origin으로 `/invite#token=` 링크 직접 생성(서버 `inviteUrl` 미사용). 승인 시 보완 4건(URL 읽기·제거 분리와 StrictMode, 로그인 복귀 1회 소비와 목적지 일원화, 삭제 실패 처리, 응답 유실 후 조회 결과의 정확한 표현)을 모두 반영했다. 공통 정책 문서(`feature-breakdown.md` §7)에는 아직 반영하지 않았다(별도 결정 반영 문서 PR).
- **Codex 리뷰 지점**
  1. `src/invite/pendingInvite.ts`: 저장·소비·폐기 경로(`consumeLoginReturn`의 1회 메모, 삭제 실패 분기, `observeSession`의 소유·폐기 규칙), 저장 항목이 토큰·만료 외에 없는지.
  2. `src/pages/JoinInvitePage.tsx`: fragment 읽기(렌더 중 순수 읽기)와 제거·등록(효과)의 분리, 가입 `Uncertain` 흐름(`verifyOutcome`의 `/wards` → 미리보기 재조회 판정), 늦은 응답 폐기(`current()`), 쓰기 이중 제출 가드.
  3. `src/invite/callbackDestination.ts`와 `KakaoCallbackPage.tsx`: 목적지 결정이 한 곳인지, 기존 로그인 성공·취소·실패 동작이 그대로인지.
  4. `src/pages/InvitationAdminPage.tsx`: 쓰기 뒤 재조회 결과별 문구(`composeNotice`), 발급 토큰이 컴포넌트 state에만 있고 닫으면 사라지는지, 연결 후보 필터(`linkableNurses`).
  5. 서버 코드·detail이 화면에 새지 않는지(`invitationMessages.ts`), 토큰이 URL·router state·로그·DOM에 남지 않는지.
- **알려진 제한·관찰:** `SessionManager.beginEpoch`는 쓰기를 포함한 모든 진행 중 API 요청을 취소하므로 로그아웃·계정 전환 중에는 서버가 처리했지만 화면이 결과를 보지 못할 수 있다(화면은 사라지고 이후 조회로 확인). 계약 관찰 4건(비활성 간호사 기록 가입 시 `ALREADY_WARD_MEMBER`, 관리자 초대의 `hospitalJoinedOn` 형식 검사, §7 #8 미결 표기, B3a 문서 차이 중 3건 해결)은 [검증 기록](../web/B3b-verification.md) 2.4절.
- **남은 일:** Codex 리뷰와 수정. 실연동용 가상 병동·가상 테스트 계정 2개 준비는 병원 CSV와 별개로 확인한다(병동 생성은 공식 병원 데이터 적재가 필요). 실서버·Kakao·실제 브라우저 검증 절차는 검증 기록 5절. 그 뒤 PR(`dev` 대상, 스쿼시 병합)을 연다. B9b 웹 공개 근무표는 B3b 완료·리뷰 뒤 별도 작업이다.

## B3b Codex 리뷰 (2026-11-12 13:17 KST)

- **결과:** P3(런타임) 3건. 수정·재리뷰 전이며 병합 준비 완료로 보지 않는다. 애플리케이션 코드는 수정하지 않았고 커밋·푸시·PR도 하지 않았다.
  1. `web/src/pages/InvitationAdminPage.tsx:375–377,236`: 복사 실패로 수동 링크 A가 표시된 상태에서 재발급 B를 받으면 같은 `IssuedLinkPanel`의 `manualText`가 유지되어, 새 링크 제목 아래 철회된 A가 남는다. 토큰 변경 시 복사 상태를 초기화하고 이전 복사 작업의 늦은 응답도 폐기해야 한다.
  2. `web/src/pages/InvitationAdminPage.tsx:168–170,222`: 선택한 간호사가 다른 초대 발급·연결 등으로 후보에서 빠지고 새로고침하면 화면은 '연결하지 않음'이지만 요청은 이전 `nurseId`를 보낸다. 후보를 확인한 실제 표시값으로 본문을 만들거나 선택 상태를 명시적으로 초기화해야 한다.
  3. `web/src/pages/InvitationAdminPage.tsx:153–161,236`: 현재 발급 패널의 초대를 철회해도 패널·복사 버튼·'24시간 동안 사용할 수 있음' 설명이 남아 무효 링크를 공유할 수 있다. 패널에 invitationId를 연결해 철회·재조회 결과에 따라 숨기거나 무효화하고, 재발급 응답 유실 때의 이전 패널도 점검해야 한다.
- **직접 검증:** WSL `~/nf-b3b/web/src`와 공유 worktree의 `web/src`를 `diff -qr`로 대조해 차이 없음 확인. 해당 사본에서 `npm test -- --run`: **8개 파일 228개 통과**. `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm run build` 모두 exit 0. JS gzip 93.94 kB. `npm ci`는 재실행하지 않았다. `git diff --check` 통과.
- **재현:** WSL 사본에만 임시 `codex-review.test.tsx`를 추가해 위 3개 정상 기대값을 검사했고 **3개 모두 해당 결함으로 실패**했다(수동 입력값 A 유지, 요청에 이전 nurseId 포함, 철회 후 패널 유지). 재현 후 임시 파일은 삭제했다. 공유 worktree에는 테스트나 운영 코드 변경을 남기지 않았다.
- **미검증:** 실제 브라우저·클립보드·Kakao·실서버·두 계정 시연·GitHub CI. 목업 검증으로 이 항목의 완료를 주장하지 않는다.
- **인계 기준:** 경로 `C:\Users\jjang\IdeaProjects\NurseFlow`, 브랜치 `feat/web-invite-join`, HEAD `0ed5b4c2bb7443d719180433c3593ad9c3b579ba`. `docs/work-status.md` 제외 tracked diff SHA-256 `d6b6380d96ae3fbf176d507d319819cb356b547c64a1e4142f24563728adbfb4`. 신규 `web/**` 17개 파일을 경로순 정렬한 `SHA256 + 두 공백 + 경로 + LF` manifest의 SHA-256 `3d9252c003c965e472cb3171ca337bb18399df6c2e72b7fd906130c7c8483f17`. 검토 주체 Codex. 기존 미추적 `nurseflow.mv.db` 보존.

## B3b Codex 리뷰 P3 3건 수정 (2026-11-12, Claude)

- **상태:** 수정 완료, **Codex 재리뷰 대기**. 커밋·푸시·PR 없음. `backend/**`·`docs/openapi.yaml` 변경 0줄. 수정 파일은 `web/src/pages/InvitationAdminPage.tsx`, `InvitationAdminPage.test.tsx`(정식 테스트 9개 추가)와 이 문서·`web/B3b-verification.md`뿐이다.
  1. 발급 패널을 `invitationId`로 key를 줘 재발급 시 새로 마운트(복사·수동 복사 상태 초기화). 한 패널 안에서는 마지막 복사 시도만 화면을 바꾸고, 이전 시도의 늦은 성공·실패는 버린다.
  2. `issue()`가 화면과 같은 기준(현재 후보 목록에 있는 선택만 유효)으로 `nurseId`를 만들어, 후보에서 빠진 간호사는 보내지 않는다.
  3. 발급 패널이 `invitationId`와 가용 상태를 갖고, 재조회 성공 때마다 목록의 해당 초대 상태(철회·사용·만료·목록에 없음)를 반영한다. PENDING이 아니면 복사 버튼과 '24시간' 안내를 없애고 사용 불가 안내만 보인다. 다른 초대 철회는 영향 없다. 재조회 실패(상태 불명)는 패널을 그대로 두고 기존 '결과 미확인' 안내를 유지한다. 재발급 응답 유실도 재조회로 이전 패널이 철회됨으로 바뀐다.
- **같은 패턴 점검:** 가입 화면·로그인 복귀 경로는 변경하지 않았다. 확인 대기 중(`confirming`)인 초대가 다른 곳에서 상태가 바뀌어도 확인 상자가 남는 점은 서버가 최종 판정하므로 그대로 뒀다.
- **검증(Windows, Node, 이번에는 test·build도 차단 없이 실행):** `api:check` 일치, `lint`·`typecheck` 성공, `npm test -- --run` **8개 파일 237개 통과**(기준 228 + 신규 9, 실패·건너뜀 0), `build` 성공(JS gzip 93.2 kB), `git diff --check` 통과. 변이 3개(패널 key 제거, 화면과 다른 nurseId 전송, 상태 반영 제거)를 각각 신규 테스트가 검출했고 원복했다. WSL 사본은 이번에 사용하지 않았다(WSL에서 Linux Node가 없어 Windows npm만 잡혀 실행 불가).
- **미검증:** 실제 브라우저·클립보드·Kakao·실서버·두 계정 시연·GitHub CI·기기. 목업 검증이며 완료를 주장하지 않는다.
- **공유 인계:** `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-invite-join`, HEAD `0ed5b4c`, 미커밋 구현을 이어서 사용. 미추적 `nurseflow.mv.db` 보존.

## B3b Codex 재리뷰 (2026-11-12)

- **결과:** 기존 P3 1번(복사 상태)·2번(간호사 선택값) 해결 확인. 3번(철회 패널)은 재조회 성공 경로만 해결됐고 **P3(런타임) 1건이 남음**. B3b 수정·재리뷰를 먼저 마친 뒤 B9b 별도 구현으로 이어간다.
- **남은 P3:** `web/src/pages/InvitationAdminPage.tsx:151–175`에서 DELETE 204를 받았어도 해당 발급 패널을 즉시 무효화하지 않고 목록 재조회에만 의존한다. 발급 → 같은 초대 철회 성공 → 목록 조회 실패이면 이미 철회된 링크를 계속 복사할 수 있고 24시간 사용 가능 안내도 남는다. **철회 성공 응답은 결과가 확인된 상태**이며, 철회 응답 자체를 잃은 상태와 구분해야 한다. `confirmed()`의 대상 invitationId를 쓰기 결과에 연결해 성공 시 해당 패널을 즉시 REVOKED 처리하고, 응답 유실 때는 목록 재조회로 판정해야 한다.
- **재현 근거:** 정식 테스트 `InvitationAdminPage.test.tsx:498`은 `failBefore('list')`만 주입하고 철회 요청은 정상 처리한다. 그런데도 복사 버튼이 남는 것을 정상 기대값으로 검사하므로 현재 237개 통과가 이 경로의 정확성을 증명하지 못한다. 이 테스트는 성공 철회 시 복사 불가를 검사하도록 바꾸고, 철회 응답 유실+목록 조회 실패는 별도 테스트로 분리해야 한다. 무관한 초대 철회 유지 테스트도 보존한다.
- **직접 검증(Windows):** `npm test -- --run` **8개 파일 237개 통과**, `npm run api:check`·`npm run lint`·`npm run typecheck`·`npm run build` 모두 exit 0. 이번 build JS gzip **94.19 kB**. `git diff --check` 통과. WSL·npm ci는 재실행하지 않았다. 실제 브라우저·클립보드·Kakao·실서버·GitHub CI는 미검증이다.
- **인계 기준:** 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, 브랜치 `feat/web-invite-join`, HEAD `0ed5b4c2bb7443d719180433c3593ad9c3b579ba`. 상태 문서 제외 tracked diff SHA-256 `d6b6380d96ae3fbf176d507d319819cb356b547c64a1e4142f24563728adbfb4`, 신규 web 17개 파일 manifest SHA-256 `d7d37224b55bd4b68ccb05b81597c44fc6e182ecddbd0a5fcc882a6374f7c79d`(앞선 리뷰와 같은 계산 방식). 검토 주체 Codex. 코드 수정·커밋·푸시·PR·B9b 구현은 하지 않았다. 기존 미추적 DB 보존.

## B3b 재리뷰 P3 수정: 철회 성공 후 재조회 실패 (2026-11-12, Claude)

- **상태:** 수정 완료, **Codex 재리뷰 대기**. 커밋·푸시·PR·B9b 시작 없음. `backend/**`·`docs/openapi.yaml` 변경 0줄. 수정 파일: `web/src/pages/InvitationAdminPage.tsx`, `InvitationAdminPage.test.tsx`, 이 문서, `web/B3b-verification.md`.
- **수정:** `run()`이 철회 대상 `invitationId`를 쓰기 결과에 연결한다. 철회 요청이 성공(204)하면 현재 발급 패널이 그 초대일 때 즉시 REVOKED로 바꾸고, 이후 목록 조회가 실패해도 복사 버튼·수동 복사란·24시간 안내가 다시 나오지 않는다(재조회 실패 안내는 그대로 표시). 다른 초대의 철회는 패널을 유지한다. 철회 응답 유실·실패는 성공으로 보지 않고 재조회 결과로만 판단하며, 재조회도 실패하면 '결과 미확인' 안내와 기존 패널을 유지한다. 목록 반영 시 이미 사용 불가로 확인된 패널을 PENDING 목록이 되살리지 못하게 막았다(재조회마다 요청 번호로 이전 응답은 버려지므로 이중 방어).
- **테스트:** 기존 '재조회 실패' 테스트의 잘못된 기대(철회 성공인데 복사 버튼 유지)를 바로잡고 분리: 철회 204+조회 실패(복사·수동 복사란 불가, 실패 안내 유지), 응답 유실+조회 성공(조회 상태 반영), 서버가 처리하지 못한 철회+조회 성공(패널 유지), 응답 유실+조회 실패(미확인 안내·패널 유지), 늦은 이전 조회 응답이 철회 상태를 되돌리지 못함. 무관한 초대 철회 유지·복사 상태 초기화·늦은 복사 폐기·간호사 선택값 테스트는 유지.
- **검증(Windows):** `api:check` 일치, `lint`·`typecheck` 성공, `npm test -- --run` **8개 파일 241개 통과**(기존 237 + 순증 4, 실패·건너뜀 0), `build` 성공(JS gzip 약 93.3 kB), `git diff --check` 통과. 철회 대상 연결을 끊는 변이를 신규 테스트가 검출했고 원복했다. 늦은 응답 가드 단독 변이는 요청 번호 방어와 겹쳐 따로 검출되지 않는다.
- **미검증:** 실제 브라우저·클립보드·Kakao·실서버·두 계정 시연·GitHub CI·기기. WSL·`npm ci`는 재실행하지 않았다.
- **공유 인계:** `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-invite-join`, HEAD `0ed5b4c`, 미커밋 구현 이어서 사용. 미추적 `nurseflow.mv.db` 보존.

## B3b Codex 최종 수정 재리뷰 (2026-11-12 13:39 KST)

- **결과:** 남은 P3(철회 성공 후 목록 조회 실패) 해결 확인. 기존 P3 3건과 후속 잔여 경로 모두 해결됐으며, 이번 수정 범위에서 추가 조치할 결함을 발견하지 못했다. 코드 리뷰 완료이며 실연동 완료·병합 완료를 의미하지 않는다.
- **검토:** `confirmed()`가 철회 대상 ID를 전달하고, `run()`이 성공 응답을 받은 경우에만 해당 패널을 즉시 REVOKED로 바꾸는 것을 확인했다. 다른 초대 철회는 현재 패널을 유지한다. 응답 유실은 재조회로 판정하며, 이미 무효인 패널은 PENDING 조회 결과로 되살아나지 않는다. 무효 패널은 복사·수동 복사란·24시간 안내를 렌더하지 않는다. 요청 번호·세션/컴포넌트 수명 검사와 기존 복사 상태·간호사 선택값 수정도 확인했다.
- **직접 검증(Windows):** `npm test -- --run` **8개 파일 241개 통과**, `npm run api:check`·`npm run lint`·`npm run typecheck`·`npm run build` 모두 exit 0. 실제 이번 build JS gzip **94.25 kB**. `git diff --check` 통과. backend·OpenAPI diff 없음. npm ci·WSL·변이 검사는 이번 재리뷰에서 실행하지 않았다. 늦은 조회 테스트는 요청 번호 방어를 검사하며, 가짜 서버가 보류 시점의 PENDING 응답 본문을 고정하지 않으므로 상태 가드 자체의 독립 변이 검출까지 주장하지 않는다.
- **남은 완료 조건:** 실제 브라우저·클립보드 권한·Kakao 로그인 왕복·실서버 관리자/간호사 두 계정 시연과 PR/CI 확인. 코드 리뷰를 마쳤으므로 이 검증 준비와 별개로 B9b를 별도 기능 작업으로 진행할 수 있다.
- **인계 기준:** 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, 브랜치 `feat/web-invite-join`, HEAD `0ed5b4c2bb7443d719180433c3593ad9c3b579ba`. 상태 문서 제외 tracked diff SHA-256 `d6b6380d96ae3fbf176d507d319819cb356b547c64a1e4142f24563728adbfb4`, 신규 web 17개 파일 manifest SHA-256 `a9d576564f313fabb8f010cd20be80a202cf517290e224d2f1a0e492b9590bfe`(앞선 리뷰와 같은 계산 방식). 검토 주체 Codex. 운영 코드 수정·커밋·푸시·PR·B9b 시작 없음. 미추적 DB 보존.

## B3b·B4a 최신 dev 반영 완료 (2026-11-12, Codex)

- 최신 `origin/dev`는 `b352759`(#41 A6 구성원 관리, #42 병원 목록 동봉 포함)이며 두 작업 브랜치 모두 이 커밋을 조상으로 포함한다. B9b는 이번 요청 대상이 아니다.
- **B3b:** `feat/web-invite-join` HEAD를 `0ed5b4c`에서 `b352759`로 fast-forward했다. 기존 B3b 작업 파일만 임시 보관 후 복원했으며 충돌 없음. 보존된 web 28개 파일을 임시 보관본과 LF 정규화하여 대조해 차이 0개 확인(상태 문서는 dev 내용과 3-way 병합). 기존 B3b 구현은 미커밋 상태를 유지한다. 최신 OpenAPI와 생성 타입은 dev에서 함께 반영됐다.
- **B3b 직접 검증:** Windows에서 `npm run api:check`·`npm run lint`·`npm run typecheck`·`npm run build` 모두 exit 0, `npm test -- --run` **8개 파일 241개 통과**, JS gzip 94.25 kB. `git diff --check` 통과. 앞선 최종 재리뷰에서 P3는 모두 해결 확인됐으며, 표의 '재리뷰 대기'는 이 기록으로 대체한다.
- **B4a:** 기존 `d7ce955`와 최신 dev를 충돌 없이 병합한 로컬 통합 커밋 `405a8e5727a1e9de8f5c3e5c72ce521b60c372a2`. `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon` **BUILD SUCCESSFUL**, 80개 작업 중 8개 실행·72개 UP-TO-DATE(단위 테스트는 캐시 재사용). 상세·변경된 실연동 준비 절차는 B4a 브랜치의 상태 기록과 `android/B4a-verification.md`에 남겼다.
- **인계:** 현재 공유 worktree는 `feat/web-invite-join`, HEAD `b352759`, B3b 미커밋 구현이 복원돼 있다. B4a는 로컬 브랜치에 통합 커밋이 있고 원격에는 아직 푸시하지 않았다. B3b 커밋·두 브랜치 푸시·PR·dev/main 병합·배포는 하지 않았다. 백엔드 테스트·실서버·Kakao·브라우저/기기·CI는 이번에 실행하지 않았다. 사용자 DB·local.properties 보존.

## B3b·B4a 커밋·푸시 인계 (2026-11-12, Codex)

- 사용자 요청으로 두 작업 브랜치의 커밋·원격 푸시를 진행한다. B3b는 최신 dev `b352759` 위에 검토 완료한 `web/**`와 이 상태 문서만 커밋하며, B4a는 이미 만든 dev 통합 커밋 `405a8e5`를 푸시한다. PR 개설·dev/main 병합·배포는 범위에 없다.
- 직전 통합 검증 결과: B3b 테스트 8개 파일 241개 통과, api:check·lint·typecheck·build 성공. B4a 단위 테스트·앱/테스트 APK 빌드·Lint 명령 BUILD SUCCESSFUL(단위 테스트는 UP-TO-DATE). 이후 기능 코드 변경이 없어 재실행하지 않았고 `git diff --check`를 다시 확인했다.
- 실브라우저·실서버·Kakao·기기·CI 미검증을 유지한다. B3b 기존 P3는 최종 재리뷰에서 모두 해결 확인됐다. 사용자 DB는 스테이징·커밋·푸시 대상에서 제외한다. 푸시 후 두 원격 브랜치 SHA와 로컬 SHA가 같은지 확인하고 현재 B3b 브랜치에서 인계한다.

## 기타 브랜치

- `feat/android-auth-invite` (`95cf33d`): **병합 대상에서 제외(2026-11-09 사용자 결정).** 현재 A/B 기능 분해 이전(11/2~11/3)의 백엔드+Android 통합 시제품 브랜치다. 같은 기능이 #17~#20, A1(#27), B1(#30)로 따로 병합됐고, `dev`와 합치면 174개 파일이 충돌하며, 병동 직접 생성(`74c9036`)은 #24 이후 A3·A4로 다시 기획됐다. 원격에 참고용으로만 남기고, B3a(첫 화면 선택·초대 가입 화면)·B4a(병동 생성 화면) 작업 때 최신 `dev`에서 새로 구현하며 참고한다. 이 브랜치에서 PR을 열거나 이어서 작업하지 않는다.

## B2 인계 (2026-11-10, Claude)

### 브랜치와 보존한 변경

- 최신 `origin/dev` `23af850`(A3 #35 병합 포함)에서 `git switch --merge -c feat/web-foundation-session origin/dev`로 브랜치를 만들었다.
- 이전 브랜치 `feat/android-published-roster`에 있던 미커밋 Codex 기록("A3 PR #35 B4a/B4b 소비자 계약 검토" 절)은 3-way 병합으로 그대로 옮겼다. 전환 전 diff 사본과 비교해 내용이 바뀌지 않았음을 확인했다.
- 미추적 `nurseflow.mv.db`는 건드리지 않았다.
- 이 브랜치는 원격 추적을 해제했다(`dev`로 잘못 푸시되지 않도록).
- 사용자 지시에 따라 B2 변경만 커밋해 `origin/feat/web-foundation-session`에 푸시했다.
  - Codex 절은 이 커밋에서 빼고 공유 worktree에 미커밋 상태로 보존했다.
  - `nurseflow.mv.db`도 커밋하지 않았다.
  - PR 생성·병합·배포는 하지 않았다.

### 구현 범위 (B 소유 경로만)

- 수정한 경로:
  - `web/**` 신설
  - `.github/workflows/verify.yml`: `Web checks` 작업 추가만 하고 기존 줄은 바꾸지 않음
  - 루트 `README.md`, `CLAUDE.md`, 이 문서
- 수정하지 않은 경로: `backend/**`, `docs/openapi.yaml`, `docs/feature-breakdown.md`.
- §7 #19 결정 사항:
  - "React+Vite 확정"과 호스팅 후보는 [웹 안내](../web/README.md)에 기록했다.
  - breakdown 반영은 별도 결정 반영 문서 PR로 한다.

### 세션 설계

- 쿠키를 바꾸는 refresh, 코드 교환, logout은 공통 락 `nurseflow-web-session` 안에서만 실행한다. Web Locks API를 쓰고, 없으면 탭 안 대기열로 대신한다.
- 이 세 호출은 중간에 취소하지 않는다.
- 로그아웃은 `SigningOut` → 락 대기 → 서버 logout → `SignedOut` 또는 `LogoutFailed` 순서다.
- 콜백 경로에서는 시작 시 복원 refresh를 생략한다.
- epoch로 늦게 도착한 응답을 버린다.
- 서버 `AuthService.logout`은 이미 회전된 토큰으로도 계열을 폐기함을 코드로 확인했다.

### 검증

- **통과**: `api:check`, `lint`, `typecheck` 3종.
- **미실행**: `npm test`, `npm run build`, 로컬 실서버, 실제 Kakao.
- **원인**: 스마트 앱 컨트롤(`VerifiedAndReputablePolicyState = 1`)이 `@rolldown/binding-win32-x64-msvc`를 차단한다. WASI 대체 바인딩도 Windows 경로를 해석하지 못해 실패했다.
- **WSL 재검증 (2026-11-10 16:45~17:05, Claude)**: 사용자가 WSL Ubuntu 26.04.1을 설치했다.
  - **설치한 도구**: 공식 배포본 Node v24.22.2와 Temurin JDK 21.0.13+9를 `~/.local`에 설치했다(체크섬 확인, sudo 없음).
  - **작업 위치**: WSL 홈 사본 `~/nf-b2`에서 실행했다. 공유 worktree, Windows `node_modules`, `nurseflow.mv.db`는 건드리지 않았다.
  - **CI와 같은 6단계**: 모두 통과했다(테스트 49/49, 빌드 JS gzip 86.07 kB).
    - 첫 실행에서는 테스트 6건이 실패했다. 모두 테스트 코드 결함(fetch를 생성 시점에 잡아 둠, 역할 중복 질의)이었고 수정했다.
    - 변이 검사로 가짜 서버가 응답 시점의 쿠키를 읽던 결함을 찾아, 요청 시점의 쿠키를 쓰도록 고쳤다. 이제 9개 중 등가 1개를 뺀 8개를 검출한다.
  - **로컬 백엔드 연동(curl, 프록시 경유)**: dev(Kakao 키 없음) 20/20, preview(가짜 키, Kakao 미호출) 23/23.
  - **보안 점검**: 번들에 Web Storage·`console.log`·테스트 문자열 0회. 서버 로그에 WARN/ERROR와 쿠키·토큰 값 0건.
- **실제 Kakao 검증 (2026-11-10 저녁, Claude 진행·사용자 직접 조작)**
  - **구성**: WSL 사본에서 백엔드·웹을 띄웠다. 매 실행 새 임시 H2 DB를 쓰고 access token 수명은 1분으로 줄였다.
  - **비밀값**: Kakao 키와 Client Secret은 사용자가 만든 `~/.nurseflow-kakao.env`(권한 600)를 `source`로만 불러왔고 값은 출력하지 않았다.
  - **첫 시도 실패**: 원인은 Kakao 앱의 Client Secret 활성화와 서버 설정 누락이었다(토큰 교환 401 `KOE010`). state·쿠키 단계는 정상이었다. 사용자가 secret을 추가한 뒤 해결됐다.
  - **사용자 확인(웨일, 실제 계정 1개)**: 로그인, 소속 없음 화면, F5·새 탭 복원, 1분 뒤 갱신, 두 탭 동시 갱신, 로그아웃 전파, 브라우저 재시작 복원, 취소 후 로그인 화면 복귀가 모두 정상이었다.
  - **서버 확인**
    - DB 개수·시각: 로그인 시도 소비 각 1회, 두 탭 갱신 구간의 순차 회전(계열 폐기 없음), 로그아웃 시 계열 일괄 폐기.
    - 로그: WARN·ERROR 0건이고, 토큰·쿠키·코드·키 값은 0회다.
  - **미검증**: 두 실제 계정 전환, 웨일 외 브라우저, 쿠키 속성·Web Storage·Console·주소창·콜백 재사용의 브라우저 측 확인, 쿠키 차단 안내, GitHub CI.
  - **A 후속**: Kakao 토큰 401일 때 서버 로그의 `kakaoErrorCode`가 비는 진단 공백.
- 상세 결과는 [B2 검증 기록](../web/B2-verification.md) 5절에 있다.

### 다른 단위 상태 관찰

- 위 표의 A3 행은 "PR 열림"으로 남아 있다. 그러나 `origin/dev`에는 A3 #35가 `23af850`으로 병합돼 있다(2026-11-10 15:47 KST).
- A3 행은 B2 범위가 아니어서 고치지 않았다.

## 리뷰 지점 기록

Claude와 Codex가 같은 진행 기록을 사용한다. PR 또는 정기 리뷰를 마칠 때 공유 worktree별 경로, 브랜치, 리뷰한 `HEAD` 커밋 SHA, 미커밋 diff 해시, 시각, 검토 주체를 기록한다. 해시는 재현 가능하도록 `git diff HEAD -- . ':!docs/work-status.md' | shasum -a 256`으로 계산해 상태 기록 파일 자체를 제외한다. 인계받은 에이전트는 먼저 이 기준점을 확인하고 변경된 경우 새 검토 지점을 추가한다. 로컬 도구별 상태 파일을 유일한 기록으로 삼지 않는다.

| 작업 경로 | 브랜치 | 리뷰한 HEAD SHA | 미커밋 diff 해시 | 리뷰 시각 | 검토 주체 | 범위/결과 |
| --- | --- | --- | --- | --- | --- | --- |
| `C:\Users\jjang\IdeaProjects\NurseFlow` | `feat/android-published-roster` | `ab8347dde6464379c8670cde6f937f408d349eb8` | `66410e40794e40222067c249c692fc76ca704e07ecc39f8383e5f4768eb9e2cf` (tracked diff, 신규 파일 해시는 위 기록) | 2026-11-09 | Codex | 기존 P2 해결 확인 후 기기 회귀·실서버 공개/재공개·권한 검증 완료. 같은 미커밋 브랜치에서 PR 준비 가능. 운영 코드 추가 수정 없음 |
| `C:\Users\jjang\IdeaProjects\NurseFlow` | `feat/android-invite-join` | `23af850d2ee7e85a7bf6fa690f9714dc73b3bceb` | `1b59e8e03cbbe34eff816efaedf75177dca7fc381e828dd9a52dba5a49aa1762` (추적 파일 diff. 신규 파일은 미포함) | 2026-11-10 | Claude | B3a 구현 자체 검토. 관리자 권한 거부 후 재진입 시 바로 다시 닫히는 문제를 수정했고, 무재시도 테스트가 실제 결함을 잡지 못하던 것을 변형 검사로 발견해 보강. 독립 리뷰 전 |
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
| `/private/tmp/NurseFlow-fix` | `fix/kakao-diagnostics-invite-contract` | `b2e8d04aa894c7d916f2927facba53616939cc5a` | 미커밋 변경 없음 | 2026-11-10 | Codex | B 후속 리뷰. Kakao 진단·초대 계약·README는 문제없음. P3(관측성) HIRA 클라이언트에도 401 본문 유실 가능성 |
| `/private/tmp/NurseFlow-fix` | `fix/kakao-diagnostics-invite-contract` | `97f0bdd` | 미커밋 변경 없음 | 2026-11-10 | Claude | HIRA 클라이언트를 같은 HTTP 클라이언트로 맞추고 401·403 로그 고정 테스트 추가. **정정:** 커밋 `97f0bdd`의 메시지는 'HIRA 401 사유 코드가 비었다'고 적었으나 확인 결과 HIRA는 GET이라 원래 읽혔다(실제 결함 아님, 테스트는 이전 클라이언트에서도 통과). 본문 유실은 Kakao 토큰 교환 같은 POST의 401에서만 생긴다 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `73990f9` (구현 커밋. 상태 문서 커밋 전) | 미커밋 변경 없음 | 2026-11-10 | Claude(Sonnet) | A6 구현 전체: 계약 선행 커밋, 구성원 목록·PATCH, 활성 필터(rolesIn·membershipsOf·roster 구성원/조회 권한), 마지막 관리자 보호, 동시 테스트, 스키마 업그레이드 테스트. Codex 리뷰 전 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `4c4762ea12436bd9bcd78be67b90b5ba6c4b076f` | 미커밋 변경 없음 | 2026-11-10 | Codex | A6 리뷰. P1/P2 없음. 모든 접근 경로 active 필터·마지막 관리자 보호·잠금 확인. P3(테스트) 잠금 대기 중 호출자 권한 상실 경로 미고정 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `da361f61d44b0ce4efc6ca0d171aa483ebf267c0` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P3 수정: 순서 강제 테스트, 변이 3/3 검출 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `7dae5c07d56d03e93db3a3e2ab5d6836f60a8783` | 미커밋 변경 없음 | 2026-11-11 | Codex | dev(A5) 반영 후 순번 정리 수정 재리뷰. REQUEST CHANGES: P2 관리자 쓰기 경로가 병동 잠금 전에만 권한 확인(잠금 대기 중 권한 해제 시 변경 완료). 순번 정리·Bean 의존 순환·활성 필터는 문제없음 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `b1def48214ea374fe51f0d59424b844a1e08a946` | 미커밋 변경 없음 | 2026-11-11 | Codex | 위 P2 수정 재리뷰. APPROVE, 지적 없음. 관리자 쓰기 경로 전수 확인, 초대 수락 잠금 순서·자동생성 실패 처리·경합 테스트 확인 |
| GitHub PR #41 리뷰 | `feat/membership-admin` | `91557be936c4caf08a08527b8cc38d9e642f598b` | 해당 없음(PR diff 기준) | 2026-11-11 | hyen11 | CHANGES_REQUESTED. P2 회귀: `b1def48`에서 자동배치 취소가 병동 잠금을 먼저 잡아, 병동 잠금을 쥔 실행 중 생성을 취소하지 못함 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `4ca4e0daec2906120794f0bc3fb6503c7af8e0e6` | 미커밋 변경 없음 | 2026-11-11 | Codex | 위 P2 수정 재리뷰. P2(dev에 있던 경합): 마지막 취소 확인 뒤 커밋된 취소가 RUNNING으로 응답되고 결과가 반영됨 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `2b5640d97320b689c7d15464cd9718f9accb65be` | 미커밋 변경 없음 | 2026-11-11 | Codex | 결정 직전 작업 행 잠금 재리뷰. P1/P2 없음. P3 조기 실패 경로가 접수된 취소를 FAILED로 덮어씀, P3 테스트의 sleep 동기화 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `e64055bd84898282e11a9c574e82d5d1ae5167ac` | 미커밋 변경 없음 | 2026-11-11 | Codex | 조기 실패 수정 재리뷰. P3 예외 경로 markFailed가 접수된 취소를 FAILED로 덮어씀, P3 cancelArrived가 DB 잠금 시도 전 신호라 대기 증명 불가 |
| 외부 리뷰(사용자 전달) | `feat/membership-admin` | `91557be936c4caf08a08527b8cc38d9e642f598b` | 해당 없음 | 2026-11-11 | 외부 | P1 간호사를 관리자로 지정하면 활성 간호사 기록이 남아 근무 인원으로 계산, P2 취소 대기(이미 `4ca4e0d`로 해결), P4 마지막 관리자 비활성화 안내 문구 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `71dd50e5c88f19937769995e5efb14960a2db632` | 미커밋 변경 없음 | 2026-11-11 | Codex | P1 수정 재리뷰. P1 비활성 상태 지정 후 재활성화·비활성 구성원 간호사 활성화 우회, P1 편성 조회에 관리자 방어 필터 없음, P4 대기 테스트가 취소 세션을 식별하지 않음 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `0d1c50269d06ee016589009739712eb4689076d8` | 미커밋 변경 없음 | 2026-11-11 | Codex | 위 지적 수정 재리뷰. APPROVE, 지적 없음. SESSIONS.EXECUTING_STATEMENT 필터는 Claude가 H2에서 실행 확인(취소 잠금 제거 변이 시 실패) |
| 외부 리뷰(사용자 전달) | `feat/membership-admin` | `56b197dd6b05581effe293005ca7368d656e68c0` | 해당 없음 | 2026-11-11 | 외부 | P1 구성원 비활성화 뒤 직원 API로 기록을 재활성화하면 병동 접근이 막힌 간호사가 다시 편성됨 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `d81916e9c9513dd527640e651756304130996341` | 미커밋 변경 없음 | 2026-11-11 | Codex | 위 P1 수정 재리뷰. P2(Claude 판단 P3) 본인 근무표 조회가 편성 필터를 우회 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `b674f92bbf1b48f651ae88a8d21857783ea353b3` | 미커밋 변경 없음 | 2026-11-11 | Codex | 수정 확인, 지적 없음. 정상 구성원의 본인 근무표 경로 유지 확인 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `77e2eae3a64756ca4292a8d330b27d9e32c2e4be` | 미커밋 변경 없음 | 2026-11-12 | hyen11 | PR #41 재리뷰 CHANGES_REQUESTED: P2 비활성화·본인 프로필 쓰기 경합, P5 관리자 지정 충돌 조건 문서. 다음 커밋에서 수정 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `2624a21` | 미커밋 변경 없음 | 2026-11-12 | Codex | APPROVE. 다른 구성원 전용 쓰기 경로 없음, 잠금 순서 유지 확인. 낮은 위험(테스트가 모든 대기 세션을 셈) 지적에 따라 다음 커밋에서 대기 문장을 병동 잠금·간호사 UPDATE로 한정 |
| `/private/tmp/NurseFlow-hospital` | `feat/hospital-bundled-list` | `163e9ff`(dev) 기반, 이 커밋 | 미커밋 변경 없음 | 2026-11-12 | Claude | 병원 목록 동봉(B 개발 환경 요청): 공식 파일에서 서버가 쓰는 5개 열만 gzip(2026.9., 79,899행, 약 2.5MB)으로 `src/main/resources/hospitals/`에 두고, 경로 미지정 시 시작할 때 같은·더 새 목록이 없으면 가져옴. 갱신 스크립트 `backend/scripts/extract_hospital_list.py`. `./gradlew test --offline` 179건 중 통과 174, 건너뜀 5, 실패 0. 더 새 목록 보호 조건 제거 시 테스트 실패 확인. PR #41 병합 뒤 PR 예정 |
| `/private/tmp/NurseFlow-hospital` | `feat/hospital-bundled-list` | `4b5e60c` | 미커밋 변경 없음 | 2026-11-12 | Codex | P1~P3 없음, P4 2건(README 표 사이 문단으로 초대 링크 행이 표 밖에 렌더링, 갱신 스크립트가 기존 파일을 먼저 지움). 다음 커밋에서 수정. bootJar 실행 확인 권고에 따라 패키징된 jar를 별도 DB로 두 번 실행: 첫 실행 79,899행 가져옴, 두 번째 건너뜀 |
| GitHub PR #42 리뷰·외부 리뷰 | `feat/hospital-bundled-list` | `365088a900f53eb9d7eaf318251babbfeb6f82de` | 해당 없음(PR diff 기준) | 2026-11-12 | hyen11·외부 | P3 같은 기간 정정본 동봉 목록이 반영되지 않음(경로 방식과 동작 불일치), P4 추출 스크립트가 행 수 확인 전에 기존 목록 교체, P5 기간 미상 목록이 자동 갱신을 막는 조건 문서 누락. 다음 커밋에서 수정 |
| `/private/tmp/NurseFlow-hospital` | `feat/hospital-bundled-list` | `061d1d9` | 미커밋 변경 없음 | 2026-11-12 | Codex | 리뷰 수정 재리뷰. 규칙·테스트·스크립트 문제 없음. P3(문서) `HospitalDatasetStartupImport` KDoc만 이전 조건으로 남음, 다음 커밋에서 수정. 같은 기간 동봉 목록의 비활성화 비율 가드 전용 테스트는 선택 보강(같은 `importCsv` 경로라 기존 가드 테스트가 적용) |
| 외부 리뷰(사용자 전달) | `feat/hospital-bundled-list` | `98acc41` | 해당 없음 | 2026-11-12 | 외부 | P5 날짜 없는 CSV의 파일명만 바꿔 다시 넣으면 내용이 같아 건너뛰므로 복구되지 않는 조건이 문서에 없음. 다음 커밋에서 README 안내와 테스트 고정 |

이 문서는 Claude와 Codex가 각 기능 단위 작업 후 진행 결과를 함께 기록하는 단일 공유 현황이다.

## A3 PR #35 B4a/B4b 소비자 계약 검토 (2026-11-10, Codex)

> 아래는 이전 head에서 수행한 계약 검토의 시점 기록이다. 현재 코드에 지적이 남아 있다는 판정이 아니다.

- 후속 게시: 사용자의 행별 리뷰 요청에 따라 [COMMENT 리뷰 5837141400](https://github.com/gerrmang/NurseFlow/pull/35#pullrequestreview-5837141400)을 head `68b5981`에 게시했다. `docs/openapi.yaml` 368행(입력 검증, discussion_r4521393260), 359~361행(검색 보장 범위, discussion_r4521393265)에 대응하는 diff hunk와 position 29/22를 GitHub API로 확인했다. 연결 도구는 403이어서 인증된 로컬 GitHub CLI를 사용했다. 아래의 'GitHub 리뷰 게시 없음'은 최초 정적 검토 시점 기록이며 이 후속 요청으로 게시 완료했다. 코드 변경·커밋·푸시·병합은 하지 않았다.

- 검토 대상: GitHub PR #35의 최초 OpenAPI 계약 `aa1915c`와 최신 head `68b59814641f6341e0bfe611946db8b713ef3834`. `docs/openapi.yaml`, 검색 Controller/DTO/Service, DB·실시간 검색 경로와 관련 테스트를 정적으로 대조했다. 현재 로컬에는 A3가 없으므로 GitHub 연결 도구로 읽었으며 브랜치를 전환하지 않았다.
- 판정: 검토 범위에서 P1/P2 없음. P3(계약/입력) 1건: OpenAPI 368행은 trim 후 2~50자만 명시하지만, DB 경로는 문자·숫자만 남긴 뒤에도 2자 이상을 요구한다. `(가)`는 계약상 허용되지만 400 `INVALID_REQUEST`이며 `HospitalDatasetSearchHttpTest`에도 거절 기대가 있다. 정규화 후 최소 길이를 계약에 명시하고 DB/실시간 경로의 공통 검증으로 맞춰야 B4 입력 안내가 일치한다.
- P3(계약/검색) 1건: OpenAPI 359~361행의 기호 무시·정확 일치/병원급 우선 정렬은 DB 경로에서만 구현된다. 실시간 경로는 trim한 query를 yadmNm으로 보내고 외부 페이지 순서를 그대로 반환한다. 공통 보장으로 구현하거나, 해당 규칙이 DB 경로에 한정됨을 계약에 명시한다. 외부 API의 실제 검색·정렬 동작은 이번에 호출해 확인하지 않았다.
- 소비자 확인: `source`·`externalId`로 선택 항목을 식별하고 `name`·`address`로 동명 병원을 구분할 수 있다. `category`는 선택적 nullable로 소비한다. 200 빈 목록과 503 두 오류 코드를 별도 상태로 처리하고 `attribution`을 표시한다. page 100 이후는 요청하지 않고 검색어 구체화를 안내해야 한다. 검색 실행 시 호출하고 늦게 도착한 이전 검색 결과는 폐기한다.
- 범위 제한: `POST /wards`는 여전히 BootstrapWardRequest 계약이다. 병원 후보 검색 검토가 A4 병동 생성 계약 합의나 B4 전체 실연동 완료를 뜻하지 않는다. A4에서 hospital 스냅샷·Idempotency-Key·오류 계약을 별도로 검토한다.
- 검증: GitHub 원본 파일·PR diff·기존 테스트 기대값 대조. Gradle·기기·외부 API·실데이터 검사는 실행하지 않았다(원격 A3 정적 계약 리뷰). 최신 head의 Verify run `42672162190`은 조회 시 completed/failure였다. 이번에 CI 실패 원인을 재검증하거나 재실행하지 않았다. 코드/API/DB 변경·배포 영향 없음. GitHub 리뷰 게시·커밋·푸시·병합 없음.
- 공유 인계: 현재 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-published-roster`, HEAD `209de0db506429050885968e9cd2c3d81cc14192`를 보존했다. 기존 Android 수정 4개와 미추적 `nurseflow.mv.db`는 검토·수정하지 않았다. 이 절만 현재 공유 브랜치에 남기며, A3 구현자가 위 두 계약 항목을 반영한 뒤 재검토한다. 기록 시각 2026-11-10 14:00 KST 이후. 기존 tracked diff 해시는 `abca27372b8422cbb7f54dec0e67a5019c213e961a89a5f58cfb9e486247c36b` (`git diff HEAD -- . ':!docs/work-status.md' | Out-String`의 UTF-8 바이트 SHA256; PowerShell 줄바꿈 포함).

## 미커밋 기록 보존·로컬 브랜치 정리 (2026-11-10, Codex)

- 사용자 요청으로 위 A3 계약 검토 미커밋 기록을 현재 B2 브랜치의 별도 문서 커밋에 보존한다. B2 구현 커밋 `55fb61b`와 가상 검증 결과는 변경하지 않았다.
- `git fetch origin --prune` 성공. GitHub PR #33·#34의 `merged=true`·대상 `dev`, 병합 커밋 `fa47a5a`·`d1b8729`의 `origin/dev` 포함, 로컬 A2 head의 원본 PR head 포함을 확인한 뒤 로컬 `feat/auth-web-session-v2`·`feat/android-published-roster`만 삭제했다. 원격 브랜치는 삭제하지 않았다.
- 공유 worktree는 `C:\Users\jjang\IdeaProjects\NurseFlow`, 현재 브랜치는 `feat/web-foundation-session`으로 유지한다. `main`·`dev`·B2 브랜치와 미추적 `nurseflow.mv.db`를 보존했다. 문서 변경만이므로 앱·서버 테스트와 브라우저·Kakao·CI 검증은 재실행하지 않았다.

## B2 Draft PR·CI 기록 정정 (2026-11-10, Codex)

- [PR #39](https://github.com/gerrmang/NurseFlow/pull/39)는 open·Draft, 대상 `dev`다. 구현 head `0f4ddd4344cd80eecd93ea11af471e87c70471fc`의 [Verify 42826773185](https://github.com/gerrmang/NurseFlow/actions/runs/42826773185)에서 Backend tests·Android debug build·Web checks 모두 성공을 GitHub API로 확인했다.
- `git diff cd66358 0f4ddd4 --name-only -- web`는 검증 문서와 `src/api/schema.gen.ts`만 반환했다. 생성 타입은 `import type`으로 소비된다. 번들 크기만으로 런타임 동일성을 단정하던 표현을 이 diff 근거로 정정했다. 서버 변경·실제 Kakao 재검증 여부와 구분한다.
- A5 `bdc96a6` 반영 완료. A 후속 진단·초대 계약 `be6d78c` 미반영에 따른 Draft·KOE010 재확인 대기를 유지한다. 미검증 브라우저·계정·코드 리뷰 항목도 그대로다.
- 검증: GitHub PR·작업별 CI 결과, Git diff와 타입 참조 확인. 문서만 수정해 로컬 앱·서버·기기 검사는 재실행하지 않았다. 후속 문서 커밋의 CI는 푸시 뒤 최종 head 기준으로 확인한다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-foundation-session`에서 문서만 커밋·푸시한다. 사용자 DB를 보존하고 다른 기능 커밋·원격 병합·Draft 해제·배포는 하지 않는다.

## B2 PR #39 A 리뷰 수정 (2026-11-11, Codex)

- 기준 `00724e4`, A 리뷰 `5846276341`·`5852145291`을 코드와 대조했다. 병동 목록 병원명(P3), Web Locks 접속·브라우저 지원 조건(P3), BroadcastChannel 로그아웃 전파(P3), no-referrer(P4), 동시 로그인 재시도 안내(P4)를 반영했다. 서버·API·DB는 수정하지 않았다.
- Windows typecheck·lint 성공. WSL의 분리 사본에서 npm ci·api:check·lint·typecheck·테스트 55/55·build 모두 성공. 즉시 탭 로그아웃과 늦은 refresh 폐기·알림 실패·채널 검증, 동명 병동/nullable 병원명 회귀가 포함된다.
- audit: 개발 체인 moderate 10개 패키지, high/critical 0, omit=dev 0. 제안된 주 버전 하향·force 변경은 적용하지 않았고 호환 수정판 대기를 기록했다. 실제 브라우저·Kakao·실서버 검사는 이번 수정 후 재실행하지 않았다.
- 공유 인계: `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-foundation-session`에서 B2만 커밋·푸시한다. DB·Windows node_modules를 보존한다. Draft와 A 후속 미병합 대기를 유지하며, 수정 후 CI·재리뷰를 별도로 확인한다. 리뷰 원문과 수정 상세는 web/B2-verification.md에 있다.

## B3a Draft PR·CI 기록 정정 (2026-11-10, Codex)

- [PR #38](https://github.com/gerrmang/NurseFlow/pull/38)는 open·Draft, 대상 `dev`다. 구현 head `878cc713ac98e6c6eab96d01386f1d08498951aa`의 [Verify 42826307031](https://github.com/gerrmang/NurseFlow/actions/runs/42826307031)에서 Backend tests·Android debug build 모두 성공을 GitHub API로 확인했다. B2 웹 코드는 이 브랜치에 없으므로 Web checks는 없다.
- A5 `bdc96a6` 반영 완료. A 후속 진단·초대 계약 `be6d78c` 미반영에 따른 Draft·계약 재대조 대기를 유지한다. 실제 Kakao 통합 후 재검증·전체 코드 리뷰 등 기존 제한도 유지한다.
- 검증: GitHub PR·작업별 CI 결과 확인과 문서 변경의 `git diff --check`. 문서만 수정해 로컬 앱·서버·기기 검사는 재실행하지 않았다. 후속 문서 커밋의 CI는 푸시 뒤 최종 head 기준으로 확인한다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-invite-join`에서 문서만 커밋·푸시한다. 사용자 DB와 미추적 `web/` 잔여물을 보존했다. B2 커밋을 섞지 않았고 병합·Draft 해제·배포는 하지 않는다. 두 브랜치 기록 정리 후 원래 B2 브랜치로 복귀한다.

## B3a PR #38 A 리뷰 수정 (2026-11-11, Codex)

- 기준 `155ec1c`, A 리뷰 `5846275956`·`5852145036`을 코드와 대조했다. nullable 병원명 DTO·병동 목록 표시(P3), 링크 설정 없는 빌드의 발급·재발급 사전 차단(P3)을 반영했다. 개발 스킴 가로채기·로그인 중 프로세스 종료 시 초대 유실(P4)은 알려진 제한으로 README에 명시했다. API·DB·서버 fixture 변경 없음.
- 단위 검사 76개 통과(실패·오류·건너뜀 0), 앱·기기 테스트 APK 빌드 성공, Lint 오류 0·기존 경고 33개. JSON 테스트 설정 정리 후 단위·Lint 재실행 성공. `git diff --check` 통과.
- 신규 기기 화면 테스트는 컴파일만 확인했다. 읽기 전용 Pixel_10을 두 번 시작했지만 ADB offline으로 기기 검사 실행은 시작하지 못했다. 이번 에뮬레이터 프로세스만 종료했다. 실제 Kakao·실서버 E2E·백엔드 검사는 이번 수정 뒤 재실행하지 않았다.
- 공유 인계: `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-invite-join`에서 B3a만 커밋·푸시한 뒤 원래 B2 브랜치로 복귀한다. DB·Windows node_modules 잔여물을 보존한다. Draft·A 후속 미병합 대기를 유지하고 수정 후 CI·재리뷰는 별도로 확인한다.

## B2 PR #39 최신 dev 통합·병합 준비 (2026-11-11, Codex)

- B3a PR #38의 dev 병합 `e4ca9bf`를 확인하고 현재 B2 브랜치에 병합했다. 공유 현황 문서 충돌만 발생했으며 B2·B3a 검증·리뷰·인계 기록을 모두 보존했다. 기능 코드는 충돌 없이 반영됐다.
- 이전 dev `bdc96a6`과 새 dev `e4ca9bf` 사이에 web·OpenAPI·서버 운영 코드·CI 변경은 없다. B2 A 리뷰 수정 `d47eb38`은 유지한다. PR #39는 조회 시 Ready였으므로 Draft로 되돌리지 않는다. A의 수정 재확인과 기존 APPROVED 리뷰도 확인했으며 새 head의 승인 강제 여부로 해석하지 않는다.
- 통합 후 WSL에서 오프라인 npm ci·api:check·lint·typecheck·테스트 55/55·build 모두 성공. git diff --check 통과. 기기·실제 Kakao·실서버·audit은 이번에 재실행하지 않았고, 개발 서버도 시작하지 않았다. 최종 GitHub CI는 푸시 후 확인한다.
- A 진단·초대 계약 후속 be6d78c는 여전히 미반영이며 별도 후속으로 유지한다. B3a는 이미 병합됐고 B2만 최신 dev 기준으로 갱신한다. 병합·자동 병합·배포는 하지 않는다.
- 공유 인계: C:\Users\jjang\IdeaProjects\NurseFlow, feat/web-foundation-session에서 통합 커밋·푸시한다. 기존 nurseflow.mv.db와 사용자 로컬 설정·잔여물을 보존한다.
