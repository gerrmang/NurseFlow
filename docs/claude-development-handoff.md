# Claude Code 인계: NurseFlow 시연 MVP

## 시작 전 확인

1. Claude Code의 프로젝트 폴더를 `/Users/sihwan/Documents/ChatGPT/NurseFlow`로 선택한다. CLI를 쓰면 이 폴더에서 `claude`를 실행한다.
2. 첫 세션은 **Local, Worktree 끔**으로 연다. 현재 `feat/roster-rule-validator`에 아직 커밋하지 않은 백엔드·Android·문서 변경이 있으므로 새 worktree에는 이 작업이 복사되지 않는다.
3. 첫 작업은 현재 변경을 읽고 안전한 커밋/PR 분리 순서를 설계하는 것이다. 변경 내용을 폐기·덮어쓰기·강제 푸시하지 않는다.
4. 기획 기준은 [초기 MVP 범위](mvp-scope.md), API 계약 초안은 [OpenAPI](openapi.yaml), 개발·리뷰 분담은 [AI 작업 흐름](ai-development-review-workflow.md)이다. Figma의 최신 화면은 [NurseFlow 디자인](https://www.figma.com/design/QtqAXph795SVIVHujdZvHF/NurseFlow?node-id=7-221)에 있다.
5. 현재 Claude Code CLI에는 MCP 서버가 설정되어 있지 않다. Figma 화면을 직접 읽게 하려면 Claude Code 로그인 후 공식 Figma 플러그인을 설치하고 `/plugin`에서 Figma 계정을 연결한다. Codex에 연결된 Figma 플러그인은 Claude로 자동 전달되지 않는다.

```bash
claude plugin install figma@claude-plugins-official
```

## Opus 설계용 첫 메시지

아래를 Claude Code에서 **Opus**로 실행한다. 이 단계에서는 앱 코드를 편집하지 않는다.

> NurseFlow 저장소의 `CLAUDE.md`, `docs/mvp-scope.md`, `docs/requirements.md`, `docs/openapi.yaml`, `docs/project-plan.md`, `docs/test-plan.md`, `docs/ai-development-review-workflow.md`를 읽고 `git status --short --branch`와 현재 미커밋 변경을 확인해 주세요. 초기 MVP는 가상 데이터, Android+웹 공통 Spring Boot API, 로컬 H2, Kakao 로그인 뒤 관리자 초대, 관리자 입력 휴가·반차, 규칙 기반 비공개 초안과 관리자 확정·공개입니다. 첫 구현 단위로 **기존 작업 정리와 Kakao 로그인·초대·병동 membership**을 설계해 주세요. Android와 웹의 인증/세션 경계, 초대 링크 만료·재사용·권한 상승 방지, 최초 관리자 생성, 필요한 OpenAPI 변경, DB 변경, 테스트 사례, 브랜치/PR 분할을 문서로 제시해 주세요. 기존 Figma에는 새 로그인·초대 화면이 없으므로 필요한 디자인 변경도 명시해 주세요. 기존 코드의 검증된 부분을 재사용하고, 새 worktree에 빠질 미커밋 파일을 명시해 주세요. 불확실한 선택은 근거와 함께 표시하되 구현은 시작하지 마세요.

## Sonnet 구현용 후속 메시지

Opus 설계 결과를 확인한 다음 Claude Code에서 **Sonnet**으로 전환해 아래 메시지를 사용한다.

> 합의된 Opus 설계와 `docs/mvp-scope.md`를 기준으로 첫 번째 기능 단위만 구현해 주세요. 현재 브랜치의 미커밋 변경을 보존하고, 작업 범위와 파일 소유를 먼저 확인한 뒤 진행하세요. OpenAPI 계약, Spring Boot API/DB, 필요한 Android·웹 연결을 같은 기능 단위로 맞추고, 초대받지 않은 계정의 병동 접근 차단·초대 1회 사용·권한 상승 방지·중복 수락을 테스트하세요. 실사용 병원 데이터와 비밀키를 넣지 마세요. 변경 후 필요한 빌드/테스트를 실행하고, 변경 파일·실행 결과·검증하지 못한 부분·다음 PR 대상을 보고하세요. `dev`나 `main`에 직접 병합하지 마세요.

## Codex 검토와 합치기

- 현재 채팅의 **NurseFlow 30분 변경분 리뷰**가 새 diff를 읽기 전용으로 확인한다. 변경이 없다면 조용히 끝낸다.
- P1/P2 발견 사항은 Sonnet이 같은 기능 브랜치에서 수정한 뒤 다시 검토한다. 병합 직전에는 정기 점검 외에 PR 전체 리뷰와 CI 검증을 수행한다.
- 첫 체크포인트를 커밋·푸시한 뒤부터는 Claude Desktop의 Worktree를 켜고 독립 기능을 `dev` 기반 별도 브랜치에서 진행할 수 있다. Codex 정기 리뷰는 `git worktree list`에서 해당 작업 폴더도 찾아 읽도록 설정되어 있다.
