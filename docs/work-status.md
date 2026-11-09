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
| B1 Android Kakao 세션 | `feat/android-kakao-session` | #30 | `dev` 병합 완료 (2026-11-09, 일반 병합 커밋 `9239fa6`) | 로컬 단위·기기·실서버 검증은 [`B1 검증 기록`](../android/B1-verification.md)에 기록. GitHub CI·PR 리뷰 여부는 별도 확인 | 기록된 미해결 P1 없음. 병합 방식은 아래 참고 |
| A2 웹 세션 API | `feat/auth-web-session-v2` | #33 | PR 열림. Codex 리뷰(P2 1건)와 hyen11 리뷰(P3·P4·P5 각 1건) 지적 모두 수정. 스쿼시 병합 대기 | 2026-11-09 로컬 `./gradlew test --offline`: 108건 통과, 건너뜀 1. 동시 state 테스트(`FOR UPDATE` 제거 변이 3/3 실패)와 client_secret 전달(전달 줄 제거 변이 실패) 검출력 확인. 실제 Kakao E2E 미검증(목업만) | 없음 |
| B2 웹 기반 세션 | `feat/web-foundation-session` | 미개설 | 미착수. A2 계약에 의존 | 해당 없음 | 없음 |
| 에이전트 지침 정합성 | `docs/agent-guidance-alignment` | #32 | `dev` 병합 완료 (2026-11-09, 일반 병합 커밋 `ab8347d`. 스쿼시 원칙에서 벗어남) | `git diff --check` 통과. GitHub Actions `Verify` PR 실행 성공, `dev` push 실행 성공 | 없음 |
| A3 이후 | 해당 없음 | 미개설 | 미착수 | 해당 없음 | 없음 |

## 리뷰 상태

- PR #30의 `RealBackendSessionDeviceTest`는 `IsolatedSessionTestRunner`와 테스트 전용 `Application`/`TokenStore`를 사용한다. 앞서 기록된 “개발자 저장 토큰에 접근할 수 있다”는 P1은 이 코드와 맞지 않아 철회했다. 실제 사용자 세션을 사용하지 않는다는 검증 기록은 [`B1 검증 기록`](../android/B1-verification.md)을 참조한다.
- PR #32의 이전 head `5d4f45b`에서 발견한 사항은 수정 head `f10d598`에 반영했다. PR #30 P1 지적은 테스트 전용 runner를 고려하지 않은 오류로 철회했다. `.gitignore`의 광범위한 `.codex/` 제외를 제거하고, Claude·Codex 공통 worktree 규칙과 누락된 Claude 문서 갱신, breakdown의 A1/B1 병합 상태를 추가했다.

- B1(#30)과 지침 정합성(#32)이 일반 병합 커밋으로 들어갔다. 이후 기능 PR은 GitHub의 "Squash and merge"로 병합한다.

## 기타 브랜치

- `feat/android-auth-invite` (`95cf33d`): **병합 대상에서 제외(2026-11-09 사용자 결정).** 현재 A/B 기능 분해 이전(11/2~11/3)의 백엔드+Android 통합 시제품 브랜치다. 같은 기능이 #17~#20, A1(#27), B1(#30)로 따로 병합됐고, `dev`와 합치면 174개 파일이 충돌하며, 병동 직접 생성(`74c9036`)은 #24 이후 A3·A4로 다시 기획됐다. 원격에 참고용으로만 남기고, B3a(첫 화면 선택·초대 가입 화면)·B4a(병동 생성 화면) 작업 때 최신 `dev`에서 새로 구현하며 참고한다. 이 브랜치에서 PR을 열거나 이어서 작업하지 않는다.

## 리뷰 지점 기록

Claude와 Codex가 같은 진행 기록을 사용한다. PR 또는 정기 리뷰를 마칠 때 공유 worktree별 경로, 브랜치, 리뷰한 `HEAD` 커밋 SHA, 미커밋 diff 해시, 시각, 검토 주체를 기록한다. 해시는 재현 가능하도록 `git diff HEAD -- . ':!docs/work-status.md' | shasum -a 256`으로 계산해 상태 기록 파일 자체를 제외한다. 인계받은 에이전트는 먼저 이 기준점을 확인하고 변경된 경우 새 검토 지점을 추가한다. 로컬 도구별 상태 파일을 유일한 기록으로 삼지 않는다.

| 작업 경로 | 브랜치 | 리뷰한 HEAD SHA | 미커밋 diff 해시 | 리뷰 시각 | 검토 주체 | 범위/결과 |
| --- | --- | --- | --- | --- | --- | --- |
| GitHub PR #32 diff (`/private/tmp/NurseFlow-guidance`) | `docs/agent-guidance-alignment` | `5d4f45bcf7e5247d85f9f1a38fd0a59a081c499e` | `bffafc453496740f281aca22552cd1dcaf80516a2150a8bb741aa42efbdca3ab` (`git diff origin/dev...origin/pr-32 | shasum -a 256`) | 2026-11-09 | Codex | PR 메타데이터·diff·리뷰·체크 및 B1 격리 테스트 경로를 재검토. PR은 열림; GitHub 리뷰 없음, 결합 상태에서 Vercel만 성공 확인. 재검토 사항은 위에 기록 |
| `/private/tmp/NurseFlow-guidance` | `docs/agent-guidance-alignment` | `f10d598b330de17a47a954558196e8df0be4a4ec` | `e638b1aa27f5c0cccc9f855d1cb5b9c19834bd1b1410a80b260b69a29cbb5fb5` (`git diff HEAD^ HEAD -- . ':!docs/work-status.md' | shasum -a 256`) | 2026-11-09 | Codex | Claude·Codex가 같은 worktree·브랜치·상태 기록·검증 기준을 이어 쓰도록 정리. `git diff --check` 통과. PR #32에 푸시 완료 |
| `/private/tmp/NurseFlow-a2-review` | `feat/auth-web-session-v2` | `9a8ef4d64a5f50579853fada8ce3b823d66ec962` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`(미커밋 변경 없음) | 2026-11-09 | Codex | A2 전체 읽기 전용 리뷰. P1 없음. P2(테스트) 1건: 같은 state 동시 교환 테스트 부재. state 소비·refresh 회전 행 잠금, redirect URI 정확 일치, nonce·aud·iss·exp, 쿠키 속성, CSRF 인터셉터 순서, 오류 매핑, 로그 비노출, Android 경로 무변경, OpenAPI 일치 확인 |
| `/private/tmp/NurseFlow-a2-review` | `feat/auth-web-session-v2` | `0ab0a5449d2e3a5aef5ac3eb2e6c5500ea65b6f0` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | 2026-11-09 | Claude | 위 P2를 동시 콜백 테스트로 수정. Claude가 지적을 코드와 대조해 확인한 뒤 수정했고 Codex 재리뷰는 하지 않음 |
| GitHub PR #33 리뷰 | `feat/auth-web-session-v2` | `f30a59bd971b4cd7304b5bbc9a6478354e439f46` | 해당 없음(PR diff 기준) | 2026-11-09 | hyen11 | P1·P2 없음. P3 기본 포트 Origin 정규화, P4 client_secret 회귀 테스트, P5 OpenAPI cross-site 조건 |
| `/private/tmp/NurseFlow-a2-review` | `feat/auth-web-session-v2` | `00cbffff08cbc17490bd8db2f8194fde3d23b0ad` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`(미커밋 변경 없음) | 2026-11-09 | Claude | 위 P3(`b6d56aa`)·P4(`e371fa5`)·P5(`00cbfff`) 수정. 재리뷰 전 |

이 문서는 Claude와 Codex가 각 기능 단위 작업 후 진행 결과를 함께 기록하는 단일 공유 현황이다.
