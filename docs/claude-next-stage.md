# Claude 인계: 기반 코드 병합 후 첫 개발 단계

이 문서는 `feat/roster-rule-validator`의 검증·PR 병합이 끝난 뒤 사용한다. 시작 전에 `git fetch origin`, `git status --short --branch`, `git log --oneline origin/dev -3`로 실제 브랜치와 작업 상태를 확인한다. 다음 기능 브랜치는 최신 `dev`에서 만든다. `main`은 안정된 시연 기준본으로 유지한다.

## 다음 목표

**Kakao 로그인 → 관리자 초대 링크 → 병동 소속·권한 생성**을 Android와 웹에서 공유할 서버 계약으로 만든다. 현재 백엔드는 일반 OIDC JWT 검증과 병동 membership 조회만 있고, 카카오 로그인 교환·초대·계정 생성·권한 변경 API는 없다. Android는 가상 데이터 시연 화면이며 웹 클라이언트도 아직 없다. [초기 MVP 범위](mvp-scope.md)가 제품 기준이고 [OpenAPI](openapi.yaml)는 인증/초대 부분을 채워야 한다.

## Opus 설계 요청

> `CLAUDE.md`, `docs/mvp-scope.md`, `docs/requirements.md`, `docs/openapi.yaml`, `docs/test-plan.md`, `docs/ai-development-review-workflow.md`와 현재 `dev`의 코드를 읽어 주세요. 첫 개발 기능은 Kakao OIDC 로그인 및 관리자 발급 1회 초대 링크입니다. Android와 웹 각각의 인증 코드/세션 흐름, 서버 토큰 검증과 저장, 초대 토큰 해시·만료·철회·재사용 방지, 최초 관리자 부여, 기존 가상 간호사 연결/신규 생성, 타 병동 접근 차단을 설계해 주세요. OpenAPI 요청·응답·오류·권한, H2 스키마, 트랜잭션 경계, 위협/실패 시나리오와 검증 케이스를 제시하고, Figma에 없는 로그인·초대 화면의 최소 흐름도 적어 주세요. 실제 병원 데이터나 비밀키는 사용하지 말고, 설계 산출물을 `docs/`에 남기세요. 구현은 이 단계에서 시작하지 마세요.

## Sonnet 구현 요청

> 합의된 Opus 설계에서 첫 번째 작고 검증 가능한 기능 단위를 최신 `dev`에서 분기해 구현해 주세요. 우선 서버의 초대 생성·수락·병동 membership과 OpenAPI를 맞추고, 초대 전 접근 차단·만료·1회 사용·다른 계정 재사용·권한 상승·중복 수락 테스트를 추가하세요. 이후 Android와 웹의 로그인/초대 UI를 별도 작은 작업으로 연결하세요. 운영 비밀키는 환경변수로만 주입하고 저장소에는 예시 값도 넣지 마세요. 가상 데이터만 사용하고, 실행한 테스트·미검증 항목·API/DB 변경·롤백 방안을 PR에 적어 주세요. PR 대상은 `dev`이며 `main`으로 직접 병합하지 마세요.

## Claude 실행 순서

1. Claude Code **Opus**로 설계 요청을 실행한다.
2. 설계가 코드와 MVP에 맞는지 확인하고, Claude Code **Sonnet**으로 기능 브랜치에서 구현한다.
3. 백엔드 테스트와 관련 클라이언트 빌드를 실행한다. Codex 30분 리뷰의 P1/P2 발견 사항을 수정한 뒤 `dev` PR을 병합한다.
4. 다음 기능 단위는 직원·근무 유형 설정 → Android/웹 공통 API 연결 → 자동 편성·확정 E2E 순서로 진행한다.

Kakao Developers 앱 설정, Android 패키지/키 해시 및 웹 Redirect URI는 실제 로그인 E2E 전에 사용자 계정에서 등록해야 한다. 이 값과 비밀키를 문서·Git에 쓰지 않는다.
