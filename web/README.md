# NurseFlow 웹

React + TypeScript + Vite로 만든 SPA입니다. 현재 범위는 Kakao 로그인, 쿠키 기반 세션 복원·갱신·로그아웃, 내 병동 목록과 역할 표시(B2), 관리자 초대 관리와 초대 링크 가입(B3b)입니다. 병동 생성(B4b), 구성원 관리(B5b), 공개 근무표(B9b)는 아직 없습니다.

## 구조

| 경로 | 내용 |
| --- | --- |
| `src/api/schema.gen.ts` | `docs/openapi.yaml`에서 생성한 타입(`npm run api:gen`). 직접 고치지 않습니다 |
| `src/api/http.ts` | fetch 래퍼. `/api/v1` 상대 경로만 쓰고, 오류는 `status`와 서버 `code`만 담습니다 |
| `src/api/authApi.ts` | `/api/v1/auth/web/*` 호출. 항상 `X-NurseFlow-Client: web`과 `credentials: 'same-origin'`을 보냅니다 |
| `src/session/SessionManager.ts` | 세션 상태 기계. access token은 메모리에만 둡니다 |
| `src/session/sessionLock.ts` | 쿠키를 바꾸는 호출(refresh, 코드 교환, logout)을 직렬화하는 공통 락 `nurseflow-web-session` |
| `src/pages/*` | 로그인, Kakao 콜백, 내 병동, 초대 가입(`JoinInvitePage`), 초대 관리(`InvitationAdminPage`), 404 화면 |
| `src/invite/*` | 초대 링크 만들기·읽기(`inviteLink.ts`), 초대 상태와 로그인 왕복 저장(`pendingInvite.ts`), 콜백 이동 결정(`callbackDestination.ts`), 서버 코드별 안내 문구(`invitationMessages.ts`) |
| `src/messages.ts` | 오류 코드와 안내 문구 표 |

## 세션 규칙

- **토큰 보관**
  - access token은 `SessionManager`의 메모리에만 있습니다.
  - refresh token은 서버가 HttpOnly·Secure·SameSite=Strict 쿠키(`nf_refresh`, `Path=/api/v1/auth/web`)로만 다룹니다.
  - localStorage, sessionStorage, IndexedDB에는 인증 토큰이나 사용자 정보를 저장하지 않습니다.
  - **유일한 예외**는 초대 가입의 로그인 왕복용 sessionStorage 항목 하나입니다(아래 "초대 링크와 가입 (B3b)").
- **state·nonce**: 서버가 만듭니다. 웹은 `POST /auth/web/kakao/authorization`이 돌려준 `authorizationUrl`로 이동하기만 합니다.
- **공통 세션 락**
  - refresh, 코드 교환, logout은 모두 Web Locks API의 `nurseflow-web-session` 락 안에서만 실행합니다. 이 API가 없는 브라우저에서는 탭 안의 대기열로 대신합니다.
  - 이 세 호출은 중간에 취소(abort)하지 않습니다. 취소된 fetch라도 브라우저가 `Set-Cookie`를 반영할 수 있고 서버는 요청을 처리하기 때문입니다.
  - refresh token은 회전되므로, 이미 회전된 쿠키를 다시 쓰면 세션 계열 전체가 폐기됩니다. 락이 탭 사이의 이런 경합을 막습니다.
  - **웹 인증은 HTTPS 또는 이 PC의 `http://localhost:5173`에서만 사용합니다.** Web Locks가 없는 환경의 대체 대기열은 한 탭 안에서만 동작합니다. `http://<LAN IP>:5173` 같은 안전하지 않은 출처에서는 탭 간 회전을 보장하지 않으며 지원하는 로그인 접속 주소가 아닙니다. HTTPS여도 Web Locks 미지원 브라우저의 다중 탭 인증은 지원하지 않습니다.
- **epoch**
  - 로그인, 로그아웃, 만료가 일어날 때마다 올립니다. 이전 epoch에서 시작한 응답은 버립니다.
  - 일반 API 요청(`/me`, `/wards`)만 `AbortController`로 취소합니다.
- **401 처리**
  - 동시에 401이 여러 개 오면 refresh는 한 번만 합니다.
  - 요청마다 재시도는 한 번뿐입니다. 재시도도 401이면 로그인 화면으로 이동해 "로그인이 만료됐어요"를 표시합니다.
- **Kakao 콜백**
  - 앱 시작 시 `/auth/kakao/callback?code&state`이면 복원 refresh를 보내지 않고 코드 교환만 합니다.
  - 주소창의 code와 state는 즉시 지웁니다.
  - `index.html`의 `no-referrer` 정책으로 URL 정리 전에 요청되는 리소스에도 콜백 쿼리를 Referer로 보내지 않습니다. 최초 콜백 요청 URL 자체는 정적 호스트에 전달되므로 운영 프록시·호스트 접근 로그에서도 콜백 쿼리를 기록하지 않아야 합니다.
  - 여러 탭에서 동시에 로그인을 시작하면 나중 로그인 시도가 앞선 로그인 쿠키를 바꿀 수 있습니다. `INVALID_LOGIN_STATE` 안내가 나오면 다른 로그인 창을 닫고 한 창에서 다시 시작합니다.
- **로그아웃 순서**
  1. 메모리 토큰을 지우고 `SigningOut`("로그아웃하고 있어요…") 상태가 됩니다.
  2. 락을 기다립니다. 진행 중인 refresh가 끝나 최신 쿠키가 반영된 뒤 서버 logout을 보냅니다.
  3. 성공하면 로그인 화면으로 갑니다. 실패하면 `LogoutFailed` 화면에서 재시도할 수 있습니다.
  - 시작 시 `BroadcastChannel`(`nurseflow-web-logout`)로 로그아웃 신호만 보내 다른 탭의 access token·화면과 진행 중 응답을 즉시 정리합니다. 다른 탭은 서버 logout을 중복 호출하지 않습니다. 사용자 ID나 토큰을 채널에 보내지 않습니다.
  - BroadcastChannel 미지원·차단 환경에서는 다음 refresh 실패 전까지 다른 탭에 기존 access token과 화면이 남을 수 있습니다. 전파는 서버 logout 성공의 대체가 아니며, 서버 logout이 실패하면 해당 탭에서 재시도해야 합니다.

## 초대 링크와 가입 (B3b)

### 웹 초대 링크

- 관리자가 만든 직후 화면에서 복사하는 링크는 **현재 웹 출처**로 직접 만듭니다: `${window.location.origin}/invite#token=${encodeURIComponent(token)}`.
  - 서버 응답의 `inviteUrl`은 웹 링크에 쓰지 않습니다. Android 개발 스킴(`nurseflow://invite#token=`)이거나 없을 수 있습니다.
  - 운영 도메인은 정하지 않았습니다. 운영 HTTPS App Links(웹·앱 링크 통합)는 후속 작업입니다(기능 분해 §7 #6).
- "앱 붙여넣기용 복사"는 Android 앱이 받는 `nurseflow://invite#token=…` 문구를 복사합니다(개발 단계 결합).
- 원문 토큰과 링크는 화면에 상시 표시하지 않습니다. 클립보드 복사가 실패할 때만 읽기 전용 입력란에 보여 줍니다. 토큰은 발급·재발급 응답에서 한 번만 받으며, 저장소·로그·오류에 남기지 않습니다. 패널을 닫거나 화면을 벗어나면 사라집니다.
- 출처가 `localhost`/`127.0.0.1`이면 "이 PC에서만 열린다"는 안내를 보입니다. 다른 기기에서 열려면 그 기기가 접근할 수 있는 주소(HTTPS 호스트)에서 웹을 제공해야 합니다.

### 링크 읽기와 토큰 보호

- 토큰은 URL fragment(`#token=`)로만 받습니다. 형식은 43자 `[A-Za-z0-9_-]`이고 그 밖의 형식(추가 파라미터 포함)은 거절합니다.
- 읽기와 제거를 분리합니다. 렌더 중에는 `location.hash`를 읽기만 하고, 효과 단계에서 초대를 등록(같은 토큰이면 멱등)하고 주소의 fragment를 `replace`로 제거합니다. React StrictMode의 이중 실행에서도 초대는 하나만 등록됩니다.
- 토큰은 router state, URL, 로그, 오류 객체에 넣지 않습니다. 미리보기·가입 요청은 본문으로만 보냅니다. `index.html`의 `no-referrer`와 fragment 특성으로 Referer·서버 접근 로그에 토큰이 남지 않습니다.
- 붙여넣기는 이 출처의 `/invite#token=…`, `nurseflow://invite#token=…`, 43자 토큰 원문만 받습니다. 읽은 직후 입력란을 비웁니다.

### 로그인 왕복과 sessionStorage 예외

미리보기·가입은 로그인이 필요하고, Kakao 로그인은 페이지 전체 이동이라 메모리의 초대가 사라집니다. 그래서 다음 **예외 하나**를 승인받아 둡니다(2026-11-11).

- **저장 내용**: 키 `nurseflow.inviteReturn.v1`에 `{v, token, expiresAt}`만. 초대 토큰 1개와 만료 시각뿐입니다. 인증 access/refresh 토큰의 정책은 바뀌지 않습니다.
- **저장 시점**: 로그아웃 상태의 초대 화면에서 사용자가 로그인 버튼을 누를 때만. 저장에 실패하면 "로그인한 뒤 링크를 다시 열거나 붙여넣어 달라"고 안내하고, 사용자가 "그래도 로그인"을 눌러야 이동합니다.
- **읽기**: Kakao 콜백 화면이 앱 로드당 한 번만 읽습니다(첫 결과를 기억해 두 번째 호출이 덮어쓰지 않음). 순서는 읽기 → **삭제** → 형식·만료 검증 → 복원입니다. 삭제에 실패하면 읽은 것으로 보지 않고 자동 복원하지 않으며 링크 재진입·붙여넣기 안내로 전환합니다.
- **유효기간 10분은 논리적 만료**입니다. 읽을 때 `expiresAt`과 10분 한도를 검증해 거절할 뿐, 그 시각에 항목이 물리적으로 지워진다고 보장하지 않습니다. 탭이 닫히거나 삭제가 실패하면 항목이 남을 수 있고, 콜백이 아닌 경로로 앱이 시작될 때(최선 노력)와 다음 읽기에서 정리됩니다.
- **정리**: 초대 취소, 가입 완료, 로그아웃, 세션 종료·만료, 다른 탭의 로그아웃에서 메모리와 항목을 지웁니다(삭제 실패는 무시하되 메모리는 항상 지움).
- **계정**: 로그아웃 상태에서 받은 초대는 **첫 로그인한 계정이 이어받습니다**(정상 복귀). 로그인된 뒤에는 그 계정(userId·epoch)에 묶이고, 다른 계정·새 세션이 되면 폐기합니다.
- **복구**: 저장 실패·읽기 실패·삭제 실패·만료·값 손상 때는 초대를 복원하지 않고 "받은 초대 링크를 다시 열거나 붙여넣어 주세요"를 보입니다.
- **알려진 제한**: 공용 PC에서 로그인 이동 중 탭을 닫으면 최대 10분(논리적) 분량의 초대 토큰이 그 탭의 sessionStorage에 남을 수 있습니다. 탭을 닫으면 sessionStorage는 브라우저가 지웁니다.

### 화면 수명과 쓰기 요청

- 가입 화면은 `key=userId:epoch:seq`로 그려 계정·세션·초대가 바뀌면 처음부터 시작하고, 이전 요청의 늦은 응답은 버립니다. 링크를 여러 개 열면 마지막 링크가 이전 것을 대체하지만 가입 요청이 진행 중일 때는 대체하지 않습니다.
- 쓰기(발급·재발급·철회·가입)는 **자동 재시도하지 않습니다**. 예외는 기존 `authorizedFetch`의 "401이면 갱신 후 한 번 다시 전송"뿐이며, 서버가 처리하기 전에 거절한 경우입니다. 쓰기는 한 번에 하나이고 진행 중에는 버튼을 막습니다(렌더 전 연속 클릭도 ref로 막음).
- 응답을 받지 못한 쓰기는 결과를 단정하지 않습니다. 관리자 화면은 쓰기 뒤 목록을 다시 불러오고, **다시 불러오기에 성공했을 때만** "목록을 새로 불러왔어요"라고 합니다. 가입은 "결과 확인"으로 `/wards`를 조회하고, 병동이 없으면 미리보기를 다시 조회해 초대가 유효할 때만 수동 가입을 제공합니다. 조회가 실패하거나 결과가 충돌하면 "결과 미확인" 상태를 유지합니다.
- 서버의 `detail`과 사용자 입력은 화면에 그대로 쓰지 않습니다. 안내 문구는 `src/invite/invitationMessages.ts`의 서버 `code` 기준 표뿐입니다.

## 로컬 실행

필요한 것: Node.js 24(`web/.nvmrc`), JDK 21(백엔드).

1. **백엔드**: `backend/`에서 실행합니다. 값은 셸 환경변수로만 넣고 커밋하지 않습니다.
   ```bash
   export NURSEFLOW_SESSION_SECRET='<32바이트 이상 임의 값>'
   export NURSEFLOW_KAKAO_REST_API_KEY='<Kakao REST API 키>'          # 없으면 로그인 시작이 503
   export NURSEFLOW_KAKAO_REDIRECT_URIS='http://localhost:5173/auth/kakao/callback'
   ./gradlew bootRun
   ```
   - Kakao Developers 앱에도 Web 플랫폼 `http://localhost:5173`과 위 Redirect URI를 등록해야 합니다.
   - 허용 Origin은 Redirect URI에서 자동으로 정해집니다(`http://localhost:5173`).
2. **웹**: `web/`에서 실행합니다.
   ```bash
   npm ci
   npm run dev        # http://localhost:5173
   ```
   - 반드시 `http://localhost:5173`으로 접속합니다. `127.0.0.1`로 접속하면 Origin과 쿠키 호스트가 달라져 로그인이 실패합니다.
   - 포트는 `strictPort`로 고정했습니다. 5173이 이미 쓰이고 있으면 다른 포트로 바꾸지 않고 실패합니다. Redirect URI가 포트까지 정확히 같아야 하기 때문입니다.
   - 백엔드 주소를 바꿔야 할 때만 `.env.example`을 참고해 `VITE_DEV_API_TARGET`을 지정합니다.

### 검사 명령 (CI의 `Web checks`와 같음)

```bash
npm ci
npm run api:check   # src/api/schema.gen.ts가 docs/openapi.yaml과 같은지 확인
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

`docs/openapi.yaml`이 바뀌면 `npm run api:gen`으로 타입을 다시 만들고 함께 커밋합니다.

> Windows의 스마트 앱 컨트롤이 켜져 있으면 Vite 8의 번들러 네이티브 바이너리(rolldown `.node`)가 차단됩니다. 이 경우 `npm test`·`npm run build`·`npm run dev`가 실행되지 않습니다. `typecheck`·`lint`·`api:check`는 영향을 받지 않습니다. 스마트 앱 컨트롤은 한 번 끄면 Windows를 다시 설치하기 전에는 켤 수 없으니 끄지 말고 WSL에서 실행하세요.
>
> - **Node 설치**: WSL에 Linux용 Node 24를 설치합니다. 이때 PATH에 잡힌 Windows의 `npm`을 쓰지 않도록 주의합니다.
> - **작업 위치**: Windows 쪽 `node_modules`를 덮어쓰지 않도록 WSL 홈에 사본을 만들어 실행합니다. 사본에는 `web/`과 `docs/openapi.yaml`이 필요합니다.
> - **백엔드**: WSL에서 띄울 때 Windows checkout의 `gradlew`가 CRLF 줄바꿈이면 실행되지 않습니다. 사본에서 LF로 바꿉니다.
> - **브라우저 접속**: WSL에서 띄운 개발 서버는 Windows 브라우저에서 `http://localhost:5173`으로 접속할 수 있습니다(WSL localhost 전달).

## 프록시

API와 웹은 **같은 출처**에서 제공해야 합니다. 이유는 다음과 같습니다.

- 서버는 CORS를 허용하지 않습니다.
- 인증 쿠키는 `SameSite=Strict`입니다.
- `/api/v1/auth/web/*`는 허용된 `Origin`과 `X-NurseFlow-Client: web` 헤더를 요구합니다.

### 개발

`vite.config.ts`는 dev 서버와 preview 서버 모두에서 `/api`를 `http://localhost:8080`으로 프록시합니다.

- 브라우저가 보낸 `Origin`(`http://localhost:5173`)이 그대로 서버에 전달됩니다.
- 쿠키 Path(`/api/v1/auth/web`)가 같고 Domain 속성이 없으므로 쿠키를 재작성할 필요가 없습니다.

### 운영 (제안, 아직 배포하지 않음)

리버스 프록시 하나가 HTTPS를 종료합니다. 경로별 처리는 다음과 같습니다.

- `/api/v1/*` → Spring(8080)
- 나머지 → `web/dist` 정적 파일. 파일이 없으면 `index.html`로 응답합니다(SPA 폴백). 그래야 `/wards`, `/invite`, `/wards/<id>/invitations`, `/auth/kakao/callback`을 새로고침하거나 직접 열어도 동작합니다. 초대 링크(`/invite#token=…`)의 fragment는 서버로 전송되지 않으므로 서버 경로는 `/invite`입니다.

캐시 정책:

- `index.html`은 `no-cache`.
- 해시가 붙은 `assets/*`는 장기 캐시.
- `/api` 응답의 캐시 헤더는 바꾸지 않습니다.

프록시는 `Origin`·`Set-Cookie`·`Cookie` 헤더를 변형하지 않아야 합니다.

Caddy 예:

```caddyfile
nurseflow.example.com {
	encode gzip
	handle /api/v1/* {
		reverse_proxy 127.0.0.1:8080
	}
	handle {
		root * /srv/nurseflow/web/dist
		@html path / /index.html
		header @html Cache-Control "no-cache"
		header /assets/* Cache-Control "public, max-age=31536000, immutable"
		try_files {path} /index.html
		file_server
	}
}
```

nginx 예:

```nginx
server {
    listen 443 ssl;
    server_name nurseflow.example.com;
    # ssl_certificate ...;

    location /api/v1/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
    }
    location /assets/ {
        root /srv/nurseflow/web/dist;
        add_header Cache-Control "public, max-age=31536000, immutable";
    }
    location / {
        root /srv/nurseflow/web/dist;
        add_header Cache-Control "no-cache";
        try_files $uri /index.html;
    }
}
```

운영 서버 환경변수:

- `NURSEFLOW_KAKAO_REDIRECT_URIS=https://<도메인>/auth/kakao/callback`
- 필요하면 `NURSEFLOW_WEB_ALLOWED_ORIGINS=https://<도메인>`
- `NURSEFLOW_WEB_COOKIE_SECURE`는 기본값 `true`를 유지합니다.

Kakao Developers에도 같은 도메인과 Redirect URI를 등록합니다.

호스팅 위치는 아직 정하지 않았습니다(`docs/feature-breakdown.md` §7 #19). 후보와 주의할 점은 다음과 같습니다.

| 후보 | 장점 | 주의할 점 |
| --- | --- | --- |
| 단일 VM·컨테이너에 Caddy와 Spring | 같은 출처를 가장 단순하게 구성할 수 있고 H2 파일 DB와도 맞습니다 | |
| Vercel에 SPA를 두고 `/api/v1/*`를 외부 백엔드로 rewrite | | 백엔드 호스팅을 따로 정해야 합니다. 미리보기 URL은 매번 바뀌어 Redirect URI를 정확히 맞출 수 없으므로 고정 도메인에서만 로그인할 수 있습니다. rewrite가 `Origin`과 `Set-Cookie`를 그대로 전달하는지도 확인해야 합니다 |

## 검증 기록

[`B2-verification.md`](B2-verification.md)
