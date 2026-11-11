# B2 웹 기반·세션 검증 기록

**현재 상태 (2026-11-10 갱신): 실제 Kakao 로그인 1계정·웨일 브라우저 검증 완료, 일부 항목 미검증**
- 실제 Kakao 계정 1개와 웨일 브라우저로 로그인·복원·갱신·두 탭·로그아웃·재시작 복원·취소를 확인했다. 결과는 아래 [5. 실제 Kakao 브라우저 검증](#5-실제-kakao-브라우저-검증-2026-11-10-웨일-1계정)에 있다.
- 미검증: 두 실제 계정 간 전환, Chrome·Edge·Firefox·Safari, 쿠키 속성·Web Storage·Console의 브라우저 측 확인, 콜백 URL 재사용, GitHub CI.

**이전 상태 (2026-11-10 오전): 구현 완료·검증 대기**
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

### 5. 실제 Kakao 브라우저 검증 (2026-11-10, 웨일, 1계정)

**구성**
- WSL 사본 `~/nf-b2`를 현재 브랜치(`767abd6`)와 동기화했다. 소스 해시 앞 16자리는 `3cac094903aeff8f`(web·backend·docs)다.
- 백엔드는 JDK 21 `bootRun`, 웹은 Vite dev(`http://localhost:5173`)로 띄웠다.
- 매 실행마다 새 임시 H2 파일 DB를 썼다(`~/nf-b2/h2-kakao-<시각>`). 사용자 DB는 쓰지 않았다.
- 비밀값 처리
  - Kakao REST API 키와 Client Secret은 사용자가 WSL `~/.nurseflow-kakao.env`(권한 600)에 저장했다. 실행 스크립트가 `source`로만 불러왔고 값은 출력하지 않았다.
  - `NURSEFLOW_SESSION_SECRET`은 실행할 때마다 무작위로 만들었다.
- 검증 관찰을 위해 `NURSEFLOW_AUTH_SESSION_ACCESSTOKENTTL=1m`으로 access token 수명을 줄였다.
- Kakao 계정은 사용자의 실제 계정 1개다. 로그인과 동의는 사용자가 직접 했다. 실명 등 개인정보는 기록하지 않았다.

**첫 시도 실패와 원인**
- 증상: 로그인 후 "카카오 로그인에 실패했어요"가 표시됐다.
- 서버 응답: `/auth/web/kakao/code`가 401 `INVALID_AUTHORIZATION_CODE`를 반환했다(서버 코드와 로그로 확인).
- 정상이던 부분: state와 브라우저 결합 쿠키 확인은 통과했다. 실패한 곳은 토큰 교환 단계다.
- 원인 확인
  - 같은 키로 Kakao 토큰 엔드포인트에 가짜 코드를 보내자 401 `invalid_client`/`KOE010`이 나왔다.
  - Kakao 앱에 Client Secret이 켜져 있는데 서버에 설정되지 않은 상태였다.
- 조치: 사용자가 Client Secret을 키 파일에 추가했다.
- 재확인: 같은 확인에서 400 `invalid_grant`/`KOE320`(가짜 코드에 대한 정상 응답)이 나왔다. 새 DB로 다시 실행했다.
- 진단 공백(A 후속): 이 401에서 서버 로그는 `kakaoErrorCode=null`이었다. 401 응답 본문을 읽지 못하는 것으로 보인다.

**사용자가 직접 확인한 결과 (웨일)**

| 항목 | 결과 |
| --- | --- |
| 실제 Kakao 로그인 → 내 병동(소속 없음) 화면 | 성공 |
| F5·새 탭에서 세션 복원 | 유지 |
| 1분 경과 후 앱 새로고침 버튼 | 로그인 유지 |
| 1분 경과 후 두 탭 연속 F5 | 양쪽 로그인 유지 |
| 로그아웃 후 다른 탭 F5 | 양쪽 로그아웃 |
| 브라우저 창 전체 종료·재실행 | 로그인 복원 |
| Kakao 화면에서 취소·뒤로 가기 | NurseFlow 로그인 화면으로 복귀 |

**서버에서 확인한 결과** (서버를 종료한 뒤 임시 DB의 개수와 시각만 조회. 이름 등 개인정보 열은 조회하지 않음)
- 사용자 1명, 병동 소속 0건이다.
- 로그인 시도
  - 시작 4건, 소비 2건이다.
  - 소비된 2건은 각각 생성 1초 안에 한 번씩만 소비됐다.
  - 소비되지 않은 2건은 서버 기동 점검용 curl 1건과 동의 취소 1건이다.
- refresh 토큰: 2개 계열, 11건이다. 회전 9회, 폐기 11건, 남은 유효 토큰 0건이다.
- 1번째 계열
  - 로그인 뒤 복원·갱신으로 8회 회전했다.
  - 두 탭 새로고침 구간(각 2초 간격의 연속 회전 2쌍)에서도 계열 폐기 없이 순차적으로 회전했다. 탭 간 refresh 직렬화가 실제 브라우저에서 동작한 것이다.
  - 로그아웃 시각에 계열 전체가 함께 폐기됐다.
- 2번째 계열
  - 재로그인 뒤 브라우저 재시작 복원으로 1회 회전했다.
  - 이후 계열 전체가 같은 시각에 폐기됐다.
  - 2초 뒤 소비되지 않은 로그인 시도가 이어진다. 로그아웃 뒤 취소 검사를 한 흐름과 맞다.
  - DB만으로는 로그아웃 폐기와 재사용 탐지 폐기를 완전히 구분할 수 없다. 사용자 관찰(양쪽 로그아웃, 이후 정상 재로그인 시도)과 함께 로그아웃으로 판단했다.
- 로그
  - 재실행 뒤 백엔드 로그 24줄에 WARN 0건, ERROR 0건이다.
  - 백엔드·웹 로그에서 다음 문자열은 모두 0회다: `Bearer `, `nf_refresh=`, `nf_kakao_login=`, `id_token`, `access_token`, `refresh_token`, `code=`, `client_secret`, JWT 접두어 `eyJ`, 실제 REST API 키와 Client Secret 값.

**이번에 확인하지 못한 것 (미검증)**
- 두 실제 Kakao 계정 간 전환. 테스트 계정이 1개뿐이다.
- Chrome·Edge·Firefox. 웨일(Chromium 계열)만 확인했다.
- Safari·WebKit(C1·C6).
- 브라우저에서의 쿠키 속성 확인. 서버가 내려주는 `Set-Cookie` 속성은 4절의 curl 검증에서 확인했다.
- Local·Session Storage가 비어 있는지 브라우저에서 확인하는 것. 번들 점검에서 저장소 API 사용 0회는 확인했다.
- Console 출력 확인.
- 주소창에 `code`·`state`가 남지 않는지 확인하는 것.
- 콜백 URL 재사용. 서버의 로그인 시도가 각각 한 번씩만 소비된 것은 확인했다.
- 브라우저 Network에서 refresh 요청 횟수를 세는 것. 서버 측 회전 횟수로 대신 확인했다.
- 쿠키 차단 안내.

### 6. Client Secret 누락 재현과 복구 (2026-11-10 밤, 웨일, 1계정)

**A 후속 수정과의 관계**
- Kakao 토큰 교환 401에서 원인 코드를 로그에 남기는 A 수정은 `origin/fix/kakao-diagnostics-invite-contract`(`be6d78c`)에 있다. 이 브랜치는 PR이 없고 dev에 병합되지 않았다(dev는 `6a2cf29`).
- 이 브랜치에는 A 변경을 섞지 않았다. 아래 결과는 dev와 같은 현재 서버 코드 기준이다.

**구성**
- 5절과 같은 WSL 임시 서버를 매번 새 임시 DB로 띄웠다.
- Client Secret은 키 파일을 수정하지 않고, 실행 스크립트 안에서만 해당 변수를 해제해 뺐다.

**결과**

| 실행 | 확인 | 결과 |
| --- | --- | --- |
| Secret 제외 (`221749`) | curl로 로그인 시작 → 가짜 코드로 교환(state·쿠키는 임시 파일, 출력 안 함) | `/auth/web/kakao/code` 401 `INVALID_AUTHORIZATION_CODE`. 서버 로그 `status=401 kakaoErrorCode=null` |
| Secret 제외 (`221941`) | 사용자 실제 Kakao 로그인·동의 | 앱에 기존 안내 "카카오 로그인에 실패했어요. 잠시 후 다시 시도해 주세요." 유지(사용자 확인). 서버 로그도 같은 401 거절이며 **`KOE010`은 남지 않음** |
| 참고: A 브랜치 서버 | `git archive be6d78c backend`를 저장소 밖에 풀어 같은 curl 재현(브랜치는 바꾸지 않음) | 서버 로그 `status=401 kakaoErrorCode=KOE010`. A가 병합되면 이 진단 공백이 해소됨 |
| Secret 복구 (`222549`) | 가짜 코드 교환 → 사용자 실제 로그인 | 가짜 코드에는 Kakao 400 `KOE320`(client 인증 통과). 실제 로그인은 내 병동(소속 없음) 화면까지 성공(사용자 확인). 실제 로그인 과정의 WARN 0건 |

**노출 점검 (세 실행의 백엔드·웹 로그)**
- 다음은 모두 0회다.
  - 실제 REST API 키와 Client Secret 값
  - `code=`, `state=`, 가짜 인가 코드 문자열
  - `Bearer `, JWT 접두어 `eyJ`, `id_token`, `access_token`, `refresh_token`, `client_secret`
  - `nf_refresh=`, `nf_kakao_login=`
  - Kakao 응답 원문(`invalid_client`, `Bad client`, `error_description`)
- 로그에 남은 것은 HTTP 상태와 `KOE` 코드(사용자 정보 없음)뿐이다.

**검증 중 발생한 운영 실수와 조치**
- Secret을 복구해 첫 재시작을 할 때, Secret을 뺀 이전 서버가 종료되지 않은 채 8080을 계속 점유했다. 새 서버는 "Port 8080 was already in use"로 기동에 실패했다.
- 그 결과 사용자의 첫 복구 확인은 이전 서버로 처리되어 실패로 보였다. 앱 결함이 아니다.
- 포트 점유 프로세스 기준으로 종료하고, 포트가 비어 있지 않으면 시작하지 않도록 실행 스크립트(저장소 밖)를 고쳤다. 그 뒤 다시 확인했다.

**미검증**
- 이번 6절의 `KOE010` 로그 확인은 A 미병합 브랜치의 참고 실행에서만 했다. dev 기준으로는 A 병합 뒤 다시 확인한다.

### 7. 최신 dev 반영과 재검증 (2026-11-10 밤)

**반영 내용**
- A4(#36)와 A5(#37, `bdc96a6`)가 병합된 `origin/dev`를 병합 커밋 `ad1a22e`로 반영했다.
- 충돌은 없었다. dev 변경은 백엔드와 공유 문서뿐이며, `web/`과 CI 파일은 바뀌지 않았다.
- `docs/openapi.yaml`이 바뀌어 `npm run api:check`가 "stale"로 실패했다. `npm run api:gen`으로 `src/api/schema.gen.ts`를 다시 만들었다.
  - 생성 타입만 바뀌었고 웹 코드는 바꾸지 않았다.
  - 빌드 결과 JS 크기는 270.50 kB(gzip 86.07 kB)다. 크기 일치만으로 코드 동일성을 판단하지 않는다. Codex가 `git diff cd66358 0f4ddd4 --name-only -- web`를 확인한 결과 변경은 이 검증 문서와 `src/api/schema.gen.ts`뿐이며, 생성 타입은 `src/api/types.ts`의 `import type`으로 참조된다. 해당 비교 범위에서 웹 런타임 소스 변경은 없다. 서버의 A4·A5 동작 변경은 별도이며, dev 반영 후 실제 Kakao 브라우저 검증을 다시 했다는 뜻은 아니다.
- dev의 `WardMembership`에 `hospitalName`이 추가됐다. 웹 병동 목록에는 아직 표시하지 않는다(후속 후보).

**A 후속 수정 상태 (PR 대기 항목)**
- `origin/fix/kakao-diagnostics-invite-contract`(`be6d78c`)는 dev에 반영되지 않았다. 이 브랜치가 401 교환 거절의 `KOE010` 로그 수정을 담고 있다.
- 따라서 dev 기준 `KOE010` 로그는 다시 확인하지 않았다. B2에는 섞지 않았다.

**재검증**

| 검사 | 결과 |
| --- | --- |
| Windows: `api:check`·`typecheck`·`lint` | 모두 통과 |
| WSL(CI와 같은 6단계, 트리 해시 `b5b58ea3c8e1306e`): `npm ci`→`api:check`→`lint`→`typecheck`→`test`→`build` | 모두 exit 0. 테스트 3개 파일 49/49, 빌드 성공 |
| 실서버(WSL, dev 반영 백엔드, Client Secret 포함, 새 임시 DB) | SPA 경로 6개 모두 셸 응답 |
| | refresh: 쿠키 없음 401 `INVALID_REFRESH_TOKEN`. 헤더 없음·악성 Origin·cross-site는 403 `WEB_CLIENT_REQUIRED` |
| | `/me` 토큰 없음 401, 가짜 토큰 `/wards` 401 |
| | 세션 없는 logout은 204이며 쿠키 속성은 `Path=/api/v1/auth/web; Max-Age=0; Secure; HttpOnly; SameSite=Strict` |
| | 쿼리가 붙은 redirect URI는 400 `INVALID_REDIRECT_URI` |
| | 가짜 코드 교환은 앱에 401 `INVALID_AUTHORIZATION_CODE`를 반환한다. 서버 로그 `status=400 kakaoErrorCode=KOE320`(client 인증 통과) |
| 로그 노출 | 키·Secret 값, 토큰·쿠키·코드·state·Kakao 응답 원문 0회. WARN은 가짜 코드 재현 1건뿐이다 |

**백엔드 테스트**
- B2는 백엔드를 바꾸지 않는다. 같은 dev 백엔드(`bdc96a6`)의 전체 테스트는 B3a 브랜치 반영 검증에서 167건, 실패 0으로 확인했다.

**다시 하지 않은 것**
- dev 반영 뒤 실제 Kakao 브라우저 로그인. 5·6절의 실제 로그인은 반영 전 코드로 했다. 그 사이 웹 런타임 코드는 바뀌지 않았다.

### 아직 실행하지 않은 검증 (2026-11-10 오전 기준, 5·6·7절에서 일부 해소)

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
- **GitHub CI의 `Web checks`**: PR #39 구현 head `0f4ddd4344cd80eecd93ea11af471e87c70471fc`의 [Verify 42826773185](https://github.com/gerrmang/NurseFlow/actions/runs/42826773185)에서 Backend tests·Android debug build·Web checks 모두 성공했다. 아래 PR 기록 참조.

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
| C1 | WebKit 등에서 교차 사이트 리다이렉트로 열린 콜백 페이지의 fetch에 Strict `nf_kakao_login` 쿠키가 실리지 않으면 `INVALID_LOGIN_STATE`가 난다. 실브라우저 결과에 따라 이 쿠키만 `SameSite=Lax`로 바꾸는 안을 A와 합의한다. 2026-11-10 웨일(Chromium)에서는 Kakao에서 돌아온 콜백의 state 확인이 통과했다(5절). WebKit은 미검증 |
| C7 | Kakao 토큰 엔드포인트가 401을 반환하면 서버 로그의 `kakaoErrorCode`가 비어 설정 오류(`KOE010` 등)를 진단하기 어렵다(A 후속) |
| C2 | Web Locks가 없는 브라우저에서 탭 간 refresh가 겹치면 계열이 폐기될 수 있다. 선택 사항으로 서버 쪽 짧은 재사용 유예를 검토한다 |
| C3 | `GET /wards`에 `Cache-Control: private, no-store`가 없다(`/me`에는 있음). 클라이언트는 `cache: 'no-store'`로 보완했다 |
| C4 | OpenAPI 정정. `bearerAuth` 설명에 `/auth/web/*` 발급 경로가 빠졌고, `ApiProblem`의 `instance`·`traceId`·`retryable`은 서버가 보내지 않는다 |
| C5 | 로그아웃 때 `nf_kakao_login`은 지우지 않는다. 10분 TTL이라 영향은 작다 |
| C6 | Safari의 `http://localhost` Secure 쿠키 처리가 확인되지 않았다 |

## 최종 확인

- [x] 대상 브랜치 `feat/web-foundation-session`(`dev` `bdc96a6` 반영), [Draft PR #39](https://github.com/gerrmang/NurseFlow/pull/39) 개설. 병합·배포 없음
- [x] CI와 같은 6단계 통과(WSL), 테스트 49/49
- [x] 세션 경합 테스트의 검출력 확인(변이 9개 중 등가 1개를 뺀 8개 검출)
- [x] 로컬 백엔드 연동: dev 20/20, preview 23/23 (curl 기준)
- [x] 키, 토큰, 실명 없음. 테스트 사용자는 가상. 번들과 서버 로그 점검
- [x] 백엔드, OpenAPI 변경 없음. 기존 `nurseflow.mv.db`와 미커밋 Codex 기록 보존
- [x] 실제 브라우저에서의 SPA 화면 확인 (웨일, 사용자 직접 확인. 다른 브라우저는 미검증)
- [x] 실제 Kakao 브라우저 왕복 (1계정. 두 계정 전환은 미검증)
- [ ] 쿠키 속성·Web Storage·Console·콜백 재사용의 브라우저 측 확인
- [x] 구현 head `0f4ddd4`의 GitHub CI 성공(아래 기록). 후속 문서 커밋의 CI는 최종 head 기준으로 별도 확인
- [ ] 전체 코드에 대한 Codex PR 리뷰

## PR·CI 현황 확인 (2026-11-10, Codex)

- [PR #39](https://github.com/gerrmang/NurseFlow/pull/39): 대상 `dev`, open·Draft. 확인한 구현 head `0f4ddd4344cd80eecd93ea11af471e87c70471fc`.
- GitHub API로 [Verify 42826773185](https://github.com/gerrmang/NurseFlow/actions/runs/42826773185)의 completed/success와 Backend tests·Android debug build·Web checks 각각의 성공을 확인했다. 이는 위 구현 head의 결과이며 후속 문서 커밋까지 포함한 검사로 표현하지 않는다.
- A5 PR #37 병합 커밋 `bdc96a6`은 반영됐고, A 진단·초대 계약 후속 `be6d78c`는 별도 브랜치에 남아 있다. Draft 사유와 dev 기준 `KOE010` 재확인 대기를 유지한다.
- 이번 작업은 diff·타입 참조·PR·CI 메타데이터 확인과 문서 정정이다. 앱·서버·브라우저 검사는 재실행하지 않았으며 전체 코드 리뷰나 독립 승인으로 주장하지 않는다. 문서 커밋·푸시 뒤 최종 head의 CI를 별도로 확인한다.

## A 리뷰 수정·검증 (2026-11-11, Codex)

- 대상: [A 리뷰 5846276341](https://github.com/gerrmang/NurseFlow/pull/39#pullrequestreview-5846276341), [보완 리뷰 5852145291](https://github.com/gerrmang/NurseFlow/pull/39#pullrequestreview-5852145291), 기준 head `00724e4`.
- P3 병원명: 병동 목록에 nullable `hospitalName`을 표시한다. 같은 이름 병동 두 개를 병원명으로 구분하고 null·필드 생략인 기존 병동도 정상 표시하는 컴포넌트 검사를 추가했다.
- P3 Web Locks: 지원 접속 주소를 HTTPS 또는 이 PC의 localhost로 명시했다. 미지원 환경의 대체 잠금은 단일 탭에만 적용되며 다중 탭 인증을 지원하지 않는다고 README에 기록했다.
- P3 다른 탭 로그아웃: BroadcastChannel에는 `{type:'logout'}`만 전달한다. 수신 탭의 메모리 토큰과 화면을 바로 정리하고 epoch로 진행 중 응답을 폐기한다. 서버 logout은 원래 탭에서만 기존 공통 잠금 안에서 실행한다. 채널 오류·미지원은 서버 logout을 막지 않는다. 미지원 브라우저는 기존 다음 refresh 시 정리하는 제한이 남는다.
- P4 Referer: 메타 정책을 `no-referrer`로 바꿨다. 최초 콜백 URL의 호스트 접근 로그는 별도 배포 설정으로 쿼리를 제외해야 한다고 README에 남겼다. 운영 호스트 설정·실제 브라우저 네트워크 검사는 이번에 실행하지 않았다.
- P4 동시 로그인: 다른 로그인 창을 닫고 한 창에서 다시 시작하도록 `INVALID_LOGIN_STATE` 안내를 구체화하고 콜백 오류 화면 검사에 반영했다.
- P4 개발 의존성: WSL에서 `npm audit --package-lock-only`를 다시 실행했다. moderate 10개 패키지(ESLint·typescript-eslint·minimatch 체인), high/critical 0이며 `--omit=dev`는 0건이다. 이전 moderate 1건은 당시 결과로 보존한다. 현재 audit이 제안하는 수정은 ESLint 9 등 주 버전 변경/하향이며 `npm outdated`에서 확인한 typescript-eslint 최신 `8.72.2`도 권고 영향 범위에 있다. `--force`·임의 override·무관한 의존성 업그레이드는 적용하지 않았고 호환 수정판 추적은 후속으로 유지한다.
- 검증: Windows `typecheck`·`lint` 성공. 저장소 밖 WSL 사본 `~/nf-b2-review-20261111`에서 Linux Node v24.22.2로 `npm ci` → `api:check` → `lint` → `typecheck` → `test -- --run` → `build` 모두 성공. 테스트 **55/55**(기존 49+신규 6), 빌드 성공.
- 신규 세션 검사: 유효 access token이 남은 다른 탭 즉시 로그아웃, 진행 중 refresh의 늦은 응답 폐기와 서버 폐기 순서, 알림 실패 시 서버 logout 성공, 채널의 단순 신호·잘못된 메시지 무시·미지원 처리. 실제 브라우저·Kakao·서버 검증은 이번 수정 후 재실행하지 않았다. API·DB·서버 코드 변경 없음. 최종 CI와 수정 후 재리뷰는 푸시 뒤 확인한다.
