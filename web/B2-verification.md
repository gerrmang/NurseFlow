# B2 웹 기반·세션 검증 기록

**현재 상태 (2026-11-10): 구현 완료·검증 대기**
- B2 구현은 `feat/web-foundation-session`에 커밋해 원격 브랜치로 푸시했다. 기준은 `dev` `23af850`이다. PR은 열지 않았다.
- 커밋 직전에 공유 worktree와 WSL에서 최종 검증한 사본을 체크섬으로 비교했다.
  - 소스, 테스트, `package.json`, `package-lock.json`, `docs/openapi.yaml`이 모두 같다.
  - 검증 뒤에 바뀐 파일은 문서 두 개(`web/README.md`, 이 문서)뿐이다.
- WSL Ubuntu에서 CI와 같은 6단계를 실행해 모두 통과했다: `npm ci` → `api:check` → `lint` → `typecheck` → `test`(49/49) → `build`.
- 로컬 백엔드 연동은 Vite 개발 서버와 빌드 결과물로 띄운 preview 서버 양쪽에서 프록시를 거쳐 확인했다(curl 기준, 각 20/20과 23/23).
- 아직 하지 않은 검증은 다음 세 가지이며, 통과로 표시하지 않는다.
  - **실제 브라우저에서의 화면 동작**: 브라우저 자동화 도구를 쓰지 않았다.
  - **실제 Kakao 로그인 왕복**: 키 설정을 준비한 뒤 별도로 진행한다.
  - **GitHub CI**.

## 변경 목적과 내용

- **변경 범위**: `web/` 신설, `.github/workflows/verify.yml`에 `web` 작업 추가, 루트 `README.md`·`CLAUDE.md`·`docs/work-status.md` 갱신.
- **수정하지 않은 파일**: 백엔드와 `docs/openapi.yaml`.
- **기술 구성**: React 19, React Router 8(라이브러리 모드), Vite 8, TypeScript 5.9.
  - 타입은 `openapi-typescript`로 `docs/openapi.yaml`에서 생성한다.
  - 테스트는 Vitest, jsdom, Testing Library로 한다.
  - 응답 캐시 라이브러리와 MSW는 쓰지 않는다.
- **소비 계약**: A2 `/api/v1/auth/web/*`, `GET /api/v1/me`, `GET /api/v1/wards`. 실제 서버 코드(`WebAuthController`, `WebClientRequestGuard`, `AuthProperties`, `AuthService`)와 대조했다.
- **세션 설계**: [README의 세션 규칙](README.md#세션-규칙)을 따른다.
  - 쿠키를 바꾸는 세 호출(refresh, 코드 교환, logout)은 공통 락 `nurseflow-web-session` 안에서만 실행한다.
  - 진행 중인 refresh는 취소하지 않는다.
  - 로그아웃은 `SigningOut` 상태를 거쳐 `SignedOut` 또는 `LogoutFailed`로 끝난다.
  - 콜백 경로에서는 복원 refresh를 생략한다.
  - epoch로 늦게 도착한 응답을 버린다.
- **서버 동작 확인**: `AuthService.logout`은 이미 회전된 refresh 토큰으로도 계열 전체를 폐기한다(코드로 확인). 그래서 별도 계약 요청은 필요 없다.
- **의존성 버전**: 모두 공개된 지 2주 이상 된 정확한 버전으로 고정했다(`package.json`, `package-lock.json`).
  - TypeScript는 `typescript-eslint`(<6.1)와 `openapi-typescript`(^5)의 peer 범위 때문에 5.9.3을 쓴다.

## 검증

### 1. Windows 11 (Node v24.15.0, npm 11.12.1)

| 명령 | 결과 |
| --- | --- |
| `npm run api:check` | `API types match docs/openapi.yaml.` |
| `npm run lint` | 오류·경고 0 |
| `npm run typecheck` | 오류 0 |
| `npm test`, `npm run build`, `npm run dev` | **실행 불가**. 원인은 아래 |

- **원인**: 이 PC는 Windows 스마트 앱 컨트롤이 켜져 있다(`VerifiedAndReputablePolicyState = 1`).
  - 그래서 `@rolldown/binding-win32-x64-msvc`를 불러오면 "An Application Control policy has blocked this file"로 실패한다.
  - WASM 대체 바인딩도 Windows 경로를 해석하지 못해 실패했다. 이 바인딩은 `--no-save`로 임시 설치했다가 `npm prune`으로 제거했고, `package.json`·`package-lock.json`이 바뀌지 않았음을 해시로 확인했다.
  - 스마트 앱 컨트롤은 바꾸지 않았다.
- `npm audit`에서 moderate 1건이 나왔다.
  - 개발 전용 ESLint 체인의 `minimatch` ReDoS 권고 GHSA-g3hv-75wg-m249다.
  - 런타임 번들에는 포함되지 않는다.
  - 수정판은 ESLint 9로 내리는 방법뿐이라 적용하지 않았다.

### 2. WSL Ubuntu 26.04.1: CI와 같은 검사 (2026-11-10 16:45)

- **실행 환경**
  - Node v24.22.2와 npm 11.19.1을 공식 Linux x64 배포본으로 `~/.local`에 설치했다(SHASUMS256 확인, sudo 없음).
  - PATH에 잡히던 Windows의 `npm`은 쓰지 않았다.
- **작업 위치**
  - worktree의 `web/`(`node_modules`, `dist` 제외)과 `docs/openapi.yaml`을 WSL 홈 `~/nf-b2`에 rsync로 복사해 실행했다.
  - 공유 worktree와 Windows `node_modules`, 루트 `nurseflow.mv.db`는 읽기만 했다.
  - 최종 실행한 소스 트리 해시(앞 16자리)는 `a251cfea0dc7844d`다.

| 단계 | 결과 |
| --- | --- |
| `npm ci --no-audit --no-fund` | 259개 패키지 설치, exit 0 |
| `npm run api:check` | `API types match docs/openapi.yaml.` |
| `npm run lint` | exit 0 |
| `npm run typecheck` | exit 0 |
| `npm test -- --run` | **3개 파일, 49개 테스트 모두 통과** (`authApi` 6, `App` 17, `SessionManager` 26) |
| `npm run build` | 성공. `index.html` 0.44 kB, CSS 1.75 kB, JS 270.50 kB(gzip 86.07 kB) |

- **첫 실행에서 실패한 6건**: 제품 코드가 아니라 테스트 코드 결함이었고, 수정한 뒤 재실행해 통과했다.
  - 5건: 매니저가 `server.fetch`를 생성 시점에 그대로 잡아 두어, 이후 건 `vi.spyOn`이 적용되지 않았다. 매니저가 매번 속성을 통해 호출하도록 바꿨다.
  - 1건: 테스트용 `<output>` 요소의 암묵적 `status` 역할 때문에 같은 역할의 요소가 두 개로 잡혔다.
- **번들 점검**: 프로덕션 번들에 `localStorage`, `sessionStorage`, `indexedDB`, `console.log`, 테스트 전용 문자열(`fakeAuthServer`, `kauth.example.test`, `access-`, `refresh-`)이 각각 0회 나온다.

### 3. 세션 테스트의 검출력: 변이 검사 (WSL 사본에서만 실행)

`SessionManager.ts`를 한 군데씩 일부러 망가뜨린 뒤 전체 테스트를 실행했다. 끝난 뒤 원본으로 되돌리고 49/49가 통과함을 다시 확인했다.

| 변이 | 결과 |
| --- | --- |
| 1. 콜백 경로에서도 시작 시 복원 refresh 호출 | 통과(미검출). **등가 변이**다. `restore()`가 `exchanging` 상태에서는 아무것도 하지 않으므로 실제 호출이 일어나지 않는다. 방어선이 두 겹이어서 생긴 결과다 |
| 2. refresh 결과의 epoch 확인 제거 | 검출(1건): "교환 뒤 늦게 도착한 복원 응답이 새 로그인을 덮지 않음" |
| 3. 코드 교환을 락 밖에서 실행 | 검출(1건) |
| 4. logout을 락 밖에서 실행 | 검출(2건): 진행 중인 refresh 대기, 다른 탭 refresh의 순서 |
| 5. refresh를 락 밖에서 실행 | 검출(2건) |
| 6. 단일 비행 refresh 제거 | 검출(2건): 동시 401에 refresh 1회, 복원 1회 |
| 7. 401 무한 재시도 | 검출. 무한 루프로 실행이 멈춰 49건 중 29건만 완료됐다 |
| 8. logout 실패를 성공으로 처리 | 검출(2건) |
| 9. 로그아웃 시작 때 epoch 증가 생략 | 검출(1건) |

- **처음에는 변이 2가 검출되지 않았다.** 가짜 서버가 붙잡아 둔 refresh를 풀 때, 요청을 보낸 시점의 쿠키가 아니라 풀린 시점의 쿠키를 읽었기 때문이다.
- 실제 브라우저처럼 **요청을 보낸 시점의 쿠키**를 쓰도록 `fakeAuthServer`를 고쳤고, 그 뒤 검출됐다.

### 4. 로컬 백엔드 연동 (WSL, 프록시 경유 curl)

- **백엔드**
  - WSL 사본 `~/nf-b2/backend`에서 Temurin JDK 21.0.13+9(Adoptium 체크섬 확인)로 `./gradlew bootRun`을 실행했다.
  - Windows checkout의 `gradlew`는 CRLF 줄바꿈이라 WSL 사본에서만 LF로 바꿨다.
  - 임시 H2 파일 DB는 `~/nf-b2/h2/`에 두었다.
  - `NURSEFLOW_SESSION_SECRET`은 실행할 때마다 무작위로 만들고 출력하지 않았다.
  - `NURSEFLOW_KAKAO_REDIRECT_URIS=http://localhost:5173/auth/kakao/callback`.
- **웹**: 같은 사본에서 실행했다. 백엔드는 2.4~2.7초 만에 기동했다.
- **Windows에서 접근**: `http://localhost:5173/wards` 200, 프록시를 거친 refresh 401. WSL의 localhost 전달이 동작한다.

| 구성 | 결과 |
| --- | --- |
| `npm run dev` + 백엔드(Kakao 키 없음) | **20/20 통과** |
| `npm run build` 결과물로 `npm run preview` + 백엔드(**가짜 키** `local-placeholder-not-a-kakao-key`) | **23/23 통과** |

- **가짜 키**: Kakao 키 형식만 흉내 낸 자리표시자 값이다. 서버가 인가 주소를 만들 수 있게 하려고만 썼다. 반환된 Kakao 주소로는 이동하지 않았으므로 Kakao에는 요청이 가지 않았다.
- **확인한 항목**
  - **SPA 경로**: `/`, `/login`, `/wards`, `/auth/kakao/callback`, `?code&state`가 붙은 콜백, 존재하지 않는 경로가 모두 SPA 셸(`index.html`)로 응답한다. dev와 preview 모두 확인했다.
  - **가드**
    - 쿠키 없이 refresh하면 401 `INVALID_REFRESH_TOKEN`.
    - 다음 경우는 모두 403 `WEB_CLIENT_REQUIRED`다: `X-NurseFlow-Client` 헤더가 없을 때, `Origin: http://evil.example`일 때, `Origin: http://127.0.0.1:5173`일 때, `Sec-Fetch-Site: cross-site`일 때.
  - **로그아웃(세션 없음)**: 204. 응답 헤더 `nf_refresh=; Path=/api/v1/auth/web; Max-Age=0; …; Secure; HttpOnly; SameSite=Strict`.
  - **Bearer API**: 토큰 없이 `/me`는 401 `UNAUTHORIZED`, 가짜 토큰으로 `/wards`는 401.
  - **로그인 시작(키 없음)**: 503 `IDENTITY_PROVIDER_NOT_CONFIGURED`.
  - **로그인 시작(가짜 키)**
    - 200을 받고, 인가 주소는 `https://kauth.kakao.com/oauth/authorize`다.
    - 파라미터는 `client_id`, `nonce`, `redirect_uri`, `response_type`, `state`(43자)다. `redirect_uri`는 콜백 주소와 정확히 같다.
    - 응답 쿠키: `nf_kakao_login=<값>; Path=/api/v1/auth/web; Max-Age=600; …; Secure; HttpOnly; SameSite=Strict`.
  - **코드 교환(Kakao 호출 전 단계에서 거부)**: 다음 경우는 모두 `INVALID_LOGIN_STATE`다.
    - 서버에 없는 state.
    - 실제 state지만 브라우저 바인딩 쿠키가 없을 때.
  - **Redirect URI**: 쿼리를 덧붙이면 `INVALID_REDIRECT_URI`.
  - **로그**: 백엔드 로그에 WARN/ERROR 0건, 쿠키·Bearer 값 0건.
- 실행 스크립트는 저장소 밖(세션 scratchpad)에 두었다. 재현 절차는 [README](README.md#로컬-실행)를 따른다.

### 아직 실행하지 않은 검증

- **실제 브라우저에서의 SPA 동작**: 브라우저 자동화 도구를 쓰지 않았다. 사용자가 직접 확인할 항목은 다음과 같다.
  - 첫 진입 시 로그인 화면이 나온다.
  - Kakao 키가 없을 때 로그인 버튼을 누르면 "웹 로그인 설정이 아직 완료되지 않았어요" 안내가 나온다.
  - DevTools Network에서 요청에 `Origin`과 `X-NurseFlow-Client`가 실린다.
  - Application 탭의 Local·Session Storage가 비어 있다.
  - `/wards`를 새로고침하면 로그인 화면으로 이동한다.
- **실제 Kakao 브라우저 인수**: Kakao 개발자 계정이 팀원 소유여서, 키와 Redirect URI 설정이 준비된 뒤 별도로 진행한다.
  - **준비**
    - Kakao Developers 앱에 Web 플랫폼 `http://localhost:5173`과 Redirect URI `http://localhost:5173/auth/kakao/callback`을 등록한다.
    - REST API 키는 환경변수로만 넣는다.
    - `NURSEFLOW_AUTH_SESSION_ACCESSTOKENTTL=1m`을 준다.
    - 가상 테스트 계정 2개를 쓴다.
  - **브라우저**: Chrome, Edge, Firefox. Safari(WebKit)는 Windows에서 실행할 수 없어 미검증으로 남는다.
  - **시나리오**
    - Strict 쿠키를 쓰는 로그인 왕복.
    - 새로고침, 새 탭, 브라우저 재시작 뒤 복원.
    - 다른 사이트 링크로 들어왔을 때의 복원.
    - 콜백 URL을 다시 열 때 코드를 재사용하지 않는다.
    - 동의 화면에서 취소.
    - 두 탭에서 만료 뒤 동시에 갱신.
    - 한 탭이 refresh하는 중 다른 탭에서 로그아웃한 뒤 두 탭 새로고침.
    - 로그아웃 뒤 새로고침, 다른 계정으로 로그인.
    - 쿠키 속성과 Web Storage, URL, 콘솔에 토큰과 코드가 없는지 점검.
    - 쿠키 차단 안내.
  - **실행 위치**: 개발 서버를 WSL에서 띄우고 Windows 브라우저에서 `http://localhost:5173`으로 접속하는 구성으로 확인했다(WSL localhost 전달).
- **실서버에서 세션이 성공하는 경로**(로그인 → 복원 → 갱신 → `/me`·`/wards`): 실제 Kakao 로그인 없이는 refresh 쿠키를 얻을 수 없어 단위·컴포넌트 테스트(가짜 서버)로만 확인했다.
- **GitHub CI의 `Web checks`**: PR이 열려야 실행된다.

## API·데이터·설정 변경

- API, DB 변경은 없다.
- **새 설정**
  - `web/.env.example`: `VITE_DEV_API_TARGET`만 있다. 키는 없다.
  - 서버 환경변수는 기존 A2 키를 그대로 쓴다.
- **CI**: `Web checks` 작업을 추가했다(`actions/setup-node@v7`, `web/.nvmrc`=24, `npm ci` → `api:check` → `lint` → `typecheck` → `test` → `build`). 기존 backend·android 작업은 바꾸지 않았다(`git diff`에서 삭제된 줄 없음).

## 배포 영향

- 배포는 하지 않았다. 운영 구성안(같은 출처 리버스 프록시, SPA 폴백)은 [README](README.md#운영-제안-아직-배포하지-않음)에 있다.
- 저장소에 연결된 Vercel GitHub 앱이 `web/`을 자동으로 빌드하거나 미리보기를 만들 수 있다. 첫 PR 체크에서 확인한다.

## 되돌리기

- `web/`과 CI `web` 작업, 문서만 바뀌었다. 스쿼시 커밋 하나를 `git revert`하면 된다.
- 서버와 Android에는 영향이 없다.

## 남은 위험과 A와 합의할 계약 항목

| # | 내용 |
| --- | --- |
| C1 | WebKit 등에서 교차 사이트 리다이렉트로 열린 콜백 페이지의 fetch에 Strict `nf_kakao_login` 쿠키가 실리지 않으면 `INVALID_LOGIN_STATE`가 난다. 실브라우저 결과에 따라 이 쿠키만 `SameSite=Lax`로 바꾸는 안을 A와 합의한다 |
| C2 | Web Locks가 없는 브라우저에서 탭 간 refresh가 겹치면 계열이 폐기될 수 있다. 선택 사항으로 서버 쪽 짧은 재사용 유예를 검토한다 |
| C3 | `GET /wards`에 `Cache-Control: private, no-store`가 없다(`/me`에는 있음). 클라이언트는 `cache: 'no-store'`로 보완했다 |
| C4 | OpenAPI 정정. `bearerAuth` 설명에 `/auth/web/*` 발급 경로가 빠졌고, `ApiProblem`의 `instance`·`traceId`·`retryable`은 서버가 보내지 않는다 |
| C5 | 로그아웃 때 `nf_kakao_login`은 지우지 않는다. 10분 TTL이라 영향은 작다 |
| C6 | Safari의 `http://localhost` Secure 쿠키 처리가 확인되지 않았다 |

## 최종 확인

- [x] 대상 브랜치 `feat/web-foundation-session`(`dev` `23af850` 기준) 커밋·푸시. PR·병합·배포 없음
- [x] CI와 같은 6단계 통과(WSL), 테스트 49/49
- [x] 세션 경합 테스트의 검출력 확인(변이 9개 중 등가 1개를 뺀 8개 검출)
- [x] 로컬 백엔드 연동: dev 20/20, preview 23/23 (curl 기준)
- [x] 키, 토큰, 실명 없음. 테스트 사용자는 가상. 번들과 서버 로그 점검
- [x] 백엔드, OpenAPI 변경 없음. 기존 `nurseflow.mv.db`와 미커밋 Codex 기록 보존
- [ ] 실제 브라우저에서의 SPA 화면 확인
- [ ] 실제 Kakao 브라우저 왕복
- [ ] GitHub CI와 PR 리뷰
