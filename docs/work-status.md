# 공유 작업 현황

## 갱신 규칙

- Claude와 Codex는 기능 단위, PR, 리뷰를 마친 뒤 같은 작업 브랜치에서 이 문서를 갱신한다.
- 진행 상태와 검증 결과는 확인한 사실만 적고, 미검증 항목과 생략 사유를 구분한다.
- 비밀키, 토큰, 실제 간호사 정보와 부재·건강 정보는 기록하지 않는다.
- 새 작업을 시작하기 전에 [기능 분해표](feature-breakdown.md)의 현재 기능 행과 파일 소유 범위, 이 문서의 진행 현황을 확인한다.

## 기능 단위 현황

| 단위 | 브랜치 | PR | 상태 | 검증 | 미해결 리뷰 지적 |
| --- | --- | --- | --- | --- | --- |
| 기능 분해표 | 해당 없음 | #29 | `dev` 병합 완료 | 병합 확인 | 없음 |
| README | 해당 없음 | #28 | `dev` 병합 완료 | 병합 확인 | 없음 |
| A1 초대·경력 월 | `feat/invitation-24h-career-month` | #27 | `dev` 병합 완료 | 병합 확인 | 없음 |
| B1 Android Kakao 세션 | `feat/android-kakao-session` | #30 | `dev` 병합 완료 (2026-11-09, 일반 병합 커밋 `9239fa6`) | 병합 확인. §8-7의 스쿼시 병합 원칙에서 벗어남; 이후 PR은 스쿼시 병합 | P1 1건, 아래 미해결 리뷰 지적 참조 |
| A2 웹 세션 API | `feat/auth-web-session-v2` | 미개설 | `dce0002`(OpenAPI 계약), `55a1019`(구현) 푸시함 | 2026-11-09 로컬 백엔드 `./gradlew test`: 테스트 104개, 실패 0개, 건너뜀 1개(`WebKakaoSessionHttpTest` 포함). 실제 Kakao E2E 미검증(목업만) | 없음 |
| B2 웹 기반 세션 | `feat/web-foundation-session` | 미개설 | 미착수. A2 계약에 의존 | 해당 없음 | 없음 |
| A3 이후 | 해당 없음 | 미개설 | 미착수 | 해당 없음 | 없음 |

## 미해결 리뷰 지적

- **P1 · Android 기기 E2E가 기존 사용자의 토큰 복원에 접근할 수 있음** — PR #30은 이미 병합됐으므로 후속 수정 브랜치가 필요하다. `android/app/src/androidTest/kotlin/com/nurseflow/app/core/session/RealBackendSessionDeviceTest.kt:41-43`에서 테스트 컨테이너를 만든 뒤 기본 `NurseFlowApplication`으로 실제 `MainActivity`를 실행한다. `android/app/src/main/kotlin/com/nurseflow/app/NurseFlowApplication.kt:15,18`의 기본 컨테이너 생성 경로와 `android/app/src/main/kotlin/com/nurseflow/app/AppContainer.kt:19`의 초기화 복원 호출 때문에 테스트 컨테이너를 설정하기 전에 기존 사용자의 실제 refresh token을 읽거나 회전할 수 있다. 테스트용 Application/runner를 사용하거나 복원을 차단한다.

## 기타 브랜치

- `feat/android-auth-invite` (`95cf33d`): `dev`보다 12개 커밋 앞서 있고 109개 파일을 변경한다. 현재 A/B 기능 분해보다 앞서 만들어진 브랜치로, 백엔드 초대·인증·직원 기능과 Android 흐름을 포함한다. 처리 방침 미정이며 그대로 병합하지 않는다. 처리 결정은 사용자에게 확인한다.

## 리뷰 지점 기록

병합/PR 단위 리뷰는 worktree마다 경로, 브랜치, 리뷰한 `HEAD` 커밋 SHA, 미커밋 diff 해시(예: `git diff HEAD | shasum`), 리뷰 시각을 아래에 기록한다. 이 지점의 커밋과 diff가 바뀌면 새 기록을 추가한다. 30분 정기 점검의 지점은 로컬 미추적 `.codex/review-state.json`에만 저장한다. 해당 파일은 Git에 포함하지 않는다.

| 작업 경로 | 브랜치 | 리뷰한 HEAD SHA | 미커밋 diff 해시 | 리뷰 시각 | 범위/결과 |
| --- | --- | --- | --- | --- | --- |
| 기록 대기 | 기록 대기 | 기록 대기 | 기록 대기 | 기록 대기 | PR #30 Codex 리뷰에서 위 P1 지적 확인. 원 리뷰의 worktree 경로, HEAD SHA, diff 해시, 리뷰 시각은 확인 가능한 정보가 없어 미기록 |

이 문서는 Claude와 Codex가 각 기능 단위 작업 후 진행 결과를 함께 기록하는 단일 공유 현황이다.
