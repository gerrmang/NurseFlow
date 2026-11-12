# B3b 웹 초대 관리·가입 검증 기록

## 브라우저·실제 백엔드 검증 (2026-11-12 저녁, Codex)

기준은 `feat/web-invite-join`의 구현 커밋 `ff63354`와 서버 `b352759`다. **실제 Kakao 1계정 로그인 확인**, **가상 OIDC 사용자 3명으로 실제 backend 초대 흐름 확인**을 구분한다. 실제 Kakao 계정 2개를 이용한 관리자→간호사 시연은 사용자가 두 번째 계정이 없다고 알려 미실행이다. 해당 완료 조건을 충족했다고 주장하지 않는다. 최초 Draft 게시 뒤 사용자 요청으로 [PR #45](https://github.com/gerrmang/NurseFlow/pull/45)를 일반 PR로 전환했다. 미검증 조건은 유지한다.

- **자동 검사:** Windows Node v24.15.0에서 `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 성공. 테스트 **8개 파일 241개 통과**, 실패·건너뜀 0. JS **303.58 kB**, gzip **94.25 kB**. 개발용 재현 스크립트 추가 후 `node --check scripts/b3b-browser-fixture.mjs`, `npm run lint`도 성공. `npm ci`는 설치된 잠금 파일 의존성을 사용해 이번에 재실행하지 않았다.
- **실제 Kakao:** Ubuntu의 기존 로컬 키 파일을 값 출력 없이 환경변수로 읽어 별도 메모리 DB의 backend(8081)를 실행했다. 웹 `http://localhost:5173`에서 사용자가 직접 로그인했고 `/wards` 및 소속 없음 화면까지 성공했다. 합성된 존재하지 않는 토큰으로 실제 미리보기 API의 "초대를 찾을 수 없어요" 안내와 fragment 제거도 확인했다. 실제 계정 이름·키·토큰은 검증 문서에 기록하지 않는다.
- **가상 사용자 실서버:** 별도 backend(18091, `jdbc:h2:mem:b3b_fictional_e2e`), 웹(5174), 루프백 가상 OIDC 공급자(18090)를 사용했다. 실제 Spring 인증·서명 검증·초대 서비스·HTTP·H2 저장을 사용하며, 공급자만 가짜다. 실제 Kakao 인증 검증과 혼동하지 않는다. 실제 계정 서버와 쿠키 이름도 분리했다.
- **준비:** 가상 관리자가 실제 웹 인증→병원 검색→병동 생성→미가입 간호사 생성 API로 시드했다. 동봉 병원 79,899건을 적재했다. backend 운영 코드·API 계약·DB 스키마 변경은 없다.

| 브라우저에서 실행한 흐름 | 확인 결과 |
| --- | --- |
| 관리자 병동 목록→초대 관리→가상 간호사 기록 선택→발급 | 사용 대기·미가입·24시간 만료 안내 표시 |
| 웹 링크 복사→병동 목록 입력란에 실제 Ctrl+V | 현재 origin의 `/invite#token=` 링크, 43자 토큰 확인. 도구의 clipboard.readText는 빈 문자열이어서 성공 근거로 쓰지 않았고 실제 붙여넣기로 확인 |
| 재발급→확인 | 새 링크 사용 대기, 원본 철회됨 표시 |
| 로그아웃→재발급 링크 진입→가상 간호사 로그인 | `/invite` 복귀, fragment·콜백 쿼리 제거, 병원·병동·역할·만료·기록 연결 안내 표시 |
| 미래 연월→정상 가상 연월 입력→가입 | 미래 연월 오류, 이후 성공. 간호사 병동 목록 및 관리자 초대 목록의 가입 완료·기록 가입됨 확인 |
| 이미 사용된 링크 재진입 | 이미 사용된 초대 안내, 닫기 후 병동 목록 복귀 |
| 연결 없는 간호사 초대 발급→철회→확인 | 새 기록 생성 안내, 철회됨 표시 및 기존 발급 패널의 복사 버튼/유효 안내 제거 |
| 관리자 역할 초대→다른 가상 사용자 로그인→가입 | 연월 입력란 없음, 관리자 가입 성공, 관리자 초대 관리 진입 및 가입 완료 표시 |
| 390×844 화면의 관리자 미리보기·사용된 링크 오류 | 문구·버튼 표시, 문서 폭과 스크롤 폭 모두 390으로 가로 넘침 없음. 도구 스크린샷으로 확인 후 viewport 원복 |

신규 애플리케이션 결함은 발견하지 않았다. 브라우저 도구의 읽기 전용 평가 범위에서는 sessionStorage를 직접 읽을 수 없어 실제 저장소 값 삭제·Referer 헤더·브라우저 히스토리 전체를 확인했다고 주장하지 않는다. 로그인 왕복의 초대 복귀와 URL 정리는 화면에서 확인했다. clipboard/sessionStorage 권한 차단, 만료된 실제 초대, 연결 없는 간호사 초대의 실제 가입, 연동 중 응답 유실은 이번 브라우저 실행에서 미검증이며 기존 fake/JVM과 서버 검사를 대신 실연동 결과로 적지 않는다.

**가상 사용자 실서버 재현 도구**

`scripts/b3b-browser-fixture.mjs`는 이번에 사용한 임시 공급자를 개발용으로 보존한 것이다(모듈 import 형식 정리, 로직 동일). 앱 번들에 포함되지 않고 실제 Kakao 키를 사용하지 않는다. 모든 식별자·client secret은 가상이며 서명 키와 인가 코드는 메모리에만 둔다. 운영 backend에 연결하지 않는다. 새 메모리 DB마다 시드는 한 번만 실행한다.

1. `web/`에서 `node scripts/b3b-browser-fixture.mjs` 실행.
2. 별도 PowerShell의 `backend/`에서 다음과 같이 실행(기존 실제 계정 서버와 다른 포트·DB·쿠키):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot'
$env:NURSEFLOW_SESSION_SECRET=[guid]::NewGuid().ToString()+[guid]::NewGuid().ToString()
$env:NURSEFLOW_KAKAO_REST_API_KEY='b3b-fictional-client'
$env:NURSEFLOW_KAKAO_CLIENT_SECRET='b3b-fictional-secret'
$env:NURSEFLOW_KAKAO_REDIRECT_URIS='http://localhost:5174/auth/kakao/callback'
.\gradlew.bat bootRun '--args=--server.address=127.0.0.1 --server.port=18091 --spring.datasource.url=jdbc:h2:mem:b3b_fictional_e2e;DB_CLOSE_DELAY=-1 --nurseflow.auth.kakao.issuer=http://localhost:18090 --nurseflow.auth.kakao.jwk-set-uri=http://127.0.0.1:18090/.well-known/jwks.json --nurseflow.auth.kakao.authorize-uri=http://localhost:18090/authorize --nurseflow.auth.kakao.token-uri=http://127.0.0.1:18090/token --nurseflow.auth.web.refresh-cookie-name=nf_b3b_fixture_refresh --nurseflow.auth.web.login-cookie-name=nf_b3b_fixture_login' --no-daemon
```

3. `web/`의 별도 셸에서 `$env:VITE_DEV_API_TARGET='http://127.0.0.1:18091'` 뒤 `npm run dev -- --host localhost --port 5174` 실행.
4. backend 기동·동봉 목록 적재 후 `Invoke-RestMethod -Method Post http://127.0.0.1:18090/__seed`. 실제 API로 가상 관리자·병동·간호사를 만들며 반환은 병동 ID·성공 여부뿐이다.
5. `http://localhost:5174`에서 로그인 버튼→가상 관리자 선택. 발급 링크를 복사하고 로그아웃→링크 열기→가상 간호사/가상 추가 관리자 선택으로 역할별 가입을 재현한다. 공급자는 시작할 때 키를 새로 만들므로 재시작 시 테스트 backend도 함께 재시작한다.

공유 인계: 같은 worktree·브랜치에서 이 검증과 개발용 재현 도구를 추가한다. 서버(8081/18091)·웹(5173/5174)·가상 공급자(18090)는 실행 상태다. 실제 계정과 가상 사용자 브라우저 탭은 분리했고 viewport는 복원했다. 사용자 파일 DB·local.properties·Ubuntu 키 파일은 변경하지 않았다. 아래 최초 구현·리뷰 기록은 시점별 기록이며 최신 결과는 이 절을 우선한다.

## 최신 dev 통합 (2026-11-12, Codex)

- `feat/web-invite-join` HEAD에 `origin/dev` `b352759`(#41 A6, #42 병원 목록 동봉)를 반영하고 기존 미커밋 B3b 변경을 충돌 없이 복원했다. 아래 `0ed5b4c`는 최초 분기 기준이다.
- 기존 web 작업 파일 28개를 보관본과 LF 정규화 비교해 차이 없음 확인. Windows에서 api:check·lint·typecheck·build 성공, 테스트 **8개 파일 241개 통과**, build JS gzip 94.25 kB. `git diff --check` 통과.
- Codex 최종 재리뷰에서 기존 P3와 후속 잔여 경로 모두 해결 확인했다. 실브라우저·Kakao·실서버·두 계정·CI는 미검증이며 커밋·푸시·PR은 하지 않았다. 병원 목록이 서버에 동봉되어 별도 CSV 전달 없이 실연동을 준비할 수 있다.

- 작업: `feat/web-invite-join` (최신 `origin/dev` `0ed5b4c`에서 분기, 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`). B4a 브랜치(`feat/android-ward-create`, `d7ce955`)는 건드리지 않았고 그 커밋을 가져오지 않았다.
- 범위: `web/**`와 `docs/work-status.md`. `backend/**`와 `docs/openapi.yaml`은 수정하지 않았다(`git diff origin/dev -- backend docs/openapi.yaml` 0줄).
- **최초 구현 시점 상태: 목업 기반 검증 완료, 실서버·실제 Kakao·브라우저 미검증.** 이후 검증은 위 최신 절, 현재 미검증 항목은 4절을 확인한다.
- 커밋·푸시·PR 생성·병합·배포는 하지 않았다.

## 1. 변경 내용

| 구분 | 내용 |
| --- | --- |
| 관리자 | `/wards/:wardId/invitations`: 역할(WARD_MEMBER/WARD_ADMIN)·간호사 기록 연결 발급, 발급 직후 링크 복사 패널, 상태·대상 표시 목록, 재발급·철회(확인 단계), 새로고침. 병동 목록의 관리자 병동 카드에 "초대 관리" 진입 |
| 초대받은 사용자 | `/invite`: 링크 진입 또는 붙여넣기, 로그아웃 상태 안내와 로그인, 병원·병동·역할·만료 미리보기, WARD_MEMBER 입사 연월 입력, WARD_ADMIN은 입력 없이 가입, 가입 후 병동 목록 갱신과 "○○ 병동에 가입했어요" 안내 |
| 공통 | `src/invite/*`(링크·상태 저장소·콜백 이동 결정·안내 문구), `nurseflowApi.ts`에 초대·직원 조회 메서드 7개, `KakaoCallbackPage`의 목적지 결정 일원화, `WardsPage`의 붙여넣기·대기 초대 배너 |
| 테스트 | 가짜 초대 서버(`fakeInvitationBackend.ts`), 가짜 저장소(`fakeStorage.ts`), 공통 하네스(`harness.tsx`), 신규 테스트 파일 5개 |

## 2. 설계 결정

사용자 승인 결정(2026-11-11)과 승인 시 보완 4건을 그대로 반영했다. 자세한 규칙은 [웹 안내](README.md)의 "초대 링크와 가입 (B3b)"에 있다.

### 2.1 링크와 토큰
- 웹 링크는 `${window.location.origin}/invite#token=${encodeURIComponent(token)}`로 직접 만든다. 서버 `inviteUrl`은 웹 링크에 쓰지 않는다(테스트에서 가짜 서버가 `nurseflow://…`를 돌려줘도 사용하지 않음을 확인).
- 토큰은 fragment로만 받고, 읽기(렌더 중 순수 읽기)와 제거·등록(효과 단계, `replace`)을 분리했다. 같은 토큰의 재등록은 멱등이다.
- 원문 토큰은 상시 노출하지 않는다. 복사 실패 때만 읽기 전용 입력란에 보인다. localhost 출처에서는 "이 PC에서만 열린다" 안내를 보인다.
- 운영 도메인·App Links 통합은 후속이다. `/invite` SPA 폴백은 README에 문서화했다.

### 2.2 로그인 왕복 (승인된 sessionStorage 예외)
- 저장: 로그인 버튼을 누를 때만, `{v:1, token, expiresAt}` 한 항목(키 `nurseflow.inviteReturn.v1`). 10분은 **논리적 만료**이며 물리적 삭제 시각을 보장하지 않는다고 문서화했다.
- 복귀: `consumeLoginReturn()`은 읽기 → 삭제 → 검증 → 복원 순서이고, 앱 로드당 첫 결과를 기억해 재렌더·StrictMode 재실행이 덮어쓰지 못한다.
- 목적지: `callbackDestination()` 순수 함수 한 곳에서 `/wards`·`/login`·`/invite`를 결정한다.
- 실패 처리: 저장 실패(경고 후 "그래도 로그인"), 읽기 실패, **삭제 실패(읽은 것으로 보지 않고 자동 복원 중단)**, 만료, 값 손상은 모두 "링크를 다시 열거나 붙여넣어 주세요" 안내로 전환한다.
- 계정: 로그아웃 상태에서 받은 초대는 첫 로그인한 계정이 이어받고, 이후 다른 계정·새 epoch·로그아웃·세션 만료·다른 탭의 로그아웃에서는 폐기한다.
- 인증 access/refresh 토큰 정책은 바꾸지 않았다. 번들에서 `localStorage` 0회, `indexedDB` 0회, `sessionStorage` 1회(이 예외 하나).

### 2.3 쓰기 요청과 응답 유실
- 서버 계약: 초대 API에는 `Idempotency-Key`가 없다. 철회만 멱등(이미 철회돼도 204)이고, 가입 재전송은 409 `INVITATION_ALREADY_REDEEMED`다.
- 기존 HTTP 클라이언트: `authorizedFetch`가 401일 때 갱신 후 한 번만 다시 보낸다(서버가 처리 전에 거절한 경우). 그 밖의 자동 재시도는 없다. 쓰기는 한 번에 하나, 진행 중 버튼 비활성, 렌더 전 연속 클릭은 ref 가드로 막는다.
- 관리자: 쓰기 뒤 목록을 다시 불러오고, 다시 불러오기에 성공했을 때만 "목록을 새로 불러왔어요"를 표시한다. 실패하면 "목록도 불러오지 못했어요"와 "최신 목록이 아닐 수 있어요"를 보인다. 링크를 받지 못한 발급은 새 '사용 대기' 초대를 재발급하라고 안내한다.
- 가입: 네트워크·5xx는 "결과 미확인" 상태가 되고 "결과 확인"만 제공한다. 확인은 `/wards` 조회 → (병동 없음) 미리보기 재조회의 두 단계이며, 병동 없음만으로 미가입을 확정하지 않는다.
  - 미리보기 200: 수동 가입 재시도 제공("가입이 확인되지 않았어요. 초대가 아직 유효하니…").
  - 미리보기 409 사용됨: 충돌 → 미확인 유지. 미리보기·`/wards` 조회 실패: 미확인 유지. 만료·철회: 종료 안내.
  - redeem 409 사용됨도 같은 판정 경로를 탄다.
- 로그아웃·계정 전환으로 epoch가 바뀌면 `SessionManager`가 쓰기를 포함한 진행 중 API 요청을 취소(`ABORTED`)한다. 서버는 이미 처리했을 수 있으므로, 이 경우 화면은 사라지고 다시 로그인한 뒤 병동 목록·초대 목록으로 확인해야 한다(이전 계정의 늦은 응답은 새 화면에 반영되지 않음).

### 2.4 계약 대조 결과 (`origin/dev` 서버 코드 ↔ `docs/openapi.yaml`)
- 경로·본문·응답·오류 코드는 계약과 일치한다. 미리보기·가입은 로그인이 필요하고 토큰은 본문으로만 보낸다.
- 계약에 적히지 않은 동작(수정하지 않고 기록):
  1. 비활성 간호사 기록이 있는 사용자의 WARD_MEMBER 가입도 `ALREADY_WARD_MEMBER`(실제 소속은 아님).
  2. WARD_ADMIN 초대에 `hospitalJoinedOn`을 보내면 무시되지만 형식 검사는 먼저 하므로 잘못된 형식은 400이다. 웹은 관리자 초대에 이 필드를 보내지 않는다.
  3. `docs/feature-breakdown.md` §7 #8은 미결로 남아 있으나 코드는 재발급 시 원본을 철회한다(웹은 철회를 전제로 안내).
  4. `docs/work-status.md` B3a 절의 "문서와 코드 차이" 4건 중 3건은 #40(`0ed5b4c`)에서 해결됐다.
- 연결 간호사 후보는 `GET /wards/{id}/staff?includeInactive=false` 결과에서 `active && !linkedAccount`이고 대기 중 초대가 없는 기록이다(마지막 조건은 초대 목록과 대조해 웹이 계산).

## 3. 실행한 검증과 결과

### 3.1 Windows (Node v24, npm)
- `npm run api:check`: 성공(`API types match docs/openapi.yaml.`)
- `npm run lint`, `npm run typecheck`: 성공.
- `npm test`·`npm run build`는 스마트 앱 컨트롤의 rolldown 차단 때문에 Windows에서 실행하지 못한다([웹 안내](README.md)의 로컬 실행 참고). WSL에서 실행했다.

### 3.2 WSL Ubuntu(Node v24.22.2), 사본 `~/nf-b3b` — CI `Web checks`와 같은 6단계 (2026-11-12)
| 단계 | 결과 |
| --- | --- |
| `npm ci` | 259 패키지 설치 |
| `npm run api:check` | 성공 |
| `npm run lint` | 성공 |
| `npm run typecheck` | 성공 |
| `npm test -- --run` | **8개 파일 228개 통과, 실패·건너뜀 0** (기존 3개 파일 55개 + 신규 173개) |
| `npm run build` | 성공. JS 302.28 kB(gzip 93.94 kB), CSS 2.74 kB |

신규 테스트: `inviteLink.test.ts` 25, `invitationMessages.test.ts` 51, `pendingInvite.test.ts` 28, `JoinInvitePage.test.tsx` 44, `InvitationAdminPage.test.tsx` 25.

### 3.3 테스트가 보는 시나리오 (모두 가짜 서버·가짜 저장소·실제 SessionManager)
- 링크: fragment 읽기·거절 형식, 주소 제거, StrictMode에서 초대 1개(seq 1) 등록, 토큰이 DOM·주소에 없음, 미리보기 요청 본문만 토큰 포함, 붙여넣기 허용·거절.
- 로그인 왕복: 로그인 버튼에서만 저장, 성공·취소·교환 실패 각각의 복귀(StrictMode), 첫 결과 메모(저장소 재읽기 없음), 10분 만료, 값 손상, 저장 실패, 읽기 실패, **삭제 실패에서 자동 복원 중단**, 콜백 외 시작 시 정리, 초대 없는 로그인은 `/wards`.
- 세션: 로그아웃·계정 전환(USER_A→USER_B)·다른 탭 로그아웃 상태에서 초대 폐기와 미가입, 로그아웃 뒤 늦은 미리보기·목록 응답 폐기, 화면의 발급 링크가 세션과 함께 사라짐.
- 가입: 입사 연월 사전 검사와 서버 판정, WARD_ADMIN 본문에 필드 없음, 이중 클릭·렌더 전 연속 클릭 시 요청 1회, 만료·철회·사용됨·없음·이미 소속·연결 충돌·미지 코드의 안내(서버 코드·detail 미노출).
- 응답 유실(가입): (a) 서버 처리 후 응답 유실 → 재전송 없이 결과 확인으로 완료, (b) 처리 전 실패 → 미리보기 재확인 후 수동 재가입, (c) 결과 확인 조회 실패(병동 조회·미리보기 조회 각각) → 미확인 유지, 사용됨 충돌 → 미확인 유지, 이미 소속되어 사용됨 → 가입 완료.
- 관리자: 상태·대상 라벨, 연결 후보 필터, 요청 본문, 복사 내용(현재 origin 링크·앱용 링크), 이중 클릭, 발급 응답 유실(재전송 없음·재조회·재발급 안내), 재조회 실패 시 "새로 불러왔어요" 미표시, 발급 성공 후 재조회 실패, 재발급·철회 확인과 취소, 철회 응답 유실, 이미 사용·철회된 링크, 403/권한 없음, 첫 로드 실패 재시도, 병동 목록의 관리자 전용 진입.

### 3.4 변이 검사 (WSL 사본에서만 실행, 각 변이 후 원복)
12개 변이를 하나씩 적용해 전체 테스트를 실행했고 **모두 1개 이상 실패로 검출**됐다.

| 변이 | 검출한 실패 수 |
| --- | --- |
| M1 주소의 fragment를 지우지 않음 | 3 |
| M2 로그인 복귀를 호출마다 다시 읽음 | 4 |
| M3 삭제 실패를 읽은 것으로 취급 | 2 |
| M4 관리자 이중 제출 가드 제거 | 1 (렌더 전 연속 클릭 테스트 추가 후) |
| M5 가입 이중 제출 가드 제거 | 1 (같은 이유로 추가) |
| M6 계정이 바뀌어도 초대 유지 | 2 |
| M7 로그아웃해도 소유 초대 유지 | 3 |
| M8 재조회 실패에도 "새로 불러왔어요" | 1 |
| M9 병동 없음만으로 미가입 단정(미리보기 생략) | 2 |
| M10 웹 링크에 서버 방식 링크 사용 | 1 |
| M11 관리자 가입에 `hospitalJoinedOn` 전송 | 7 |
| M12 로그인 성공 시 초대로 가지 않음 | 1 |

처음 M4·M5는 전체 테스트가 통과(생존)했다. UI 쪽 잠금만 검사하는 테스트였기 때문이다. 같은 `act` 안에서 두 번 클릭하는 테스트를 추가한 뒤 두 변이 모두 검출했다. 최초 M11(`hospitalJoinedOn: undefined`)은 JSON 직렬화에서 필드가 사라지는 등가 변이여서 빈 문자열을 보내는 변이로 바꿨다.

### 3.5 정적·빌드 점검
- 번들(`dist/assets/*.js`): `localStorage` 0, `indexedDB` 0, `sessionStorage` 1(`nurseflow.inviteReturn.v1` 사용처 1), `console.log` 0, 테스트용 토큰 문자열 0.
- `vite preview`(WSL)에서 `curl`로 `/`, `/invite`, `/wards`, `/wards/<id>/invitations`, `/auth/kakao/callback` 모두 HTTP 200과 `<div id="root">`(SPA 폴백) 확인. `index.html`의 `no-referrer` 메타 유지.
- `git diff --check`: 통과(줄바꿈 변환 안내만). backend·OpenAPI diff 0줄.

## 4. 남은 미검증 항목과 이유 (브라우저 검증 후 갱신)

**목업·가상 OIDC 실서버·실제 Kakao 검증은 구분한다.** 위 최신 절의 결과 외에 아래 항목은 남아 있다.

| 항목 | 이유 |
| --- | --- |
| 실제 Kakao 2계정 관리자→간호사 가입과 초대 복귀 | 두 번째 계정이 없음. 실제 Kakao 로그인은 1계정, 관리자/간호사/추가 관리자 초대 흐름은 가상 OIDC+실제 backend로 각각 확인 |
| 연결 없는 간호사 초대의 가입, 실제 만료·연결 충돌·응답 유실 | 발급/철회와 연결된 간호사·관리자 가입은 실행. 나머지는 기존 fake 테스트 범위이며 이번 브라우저 실연동으로 확인하지 않음 |
| 전체 화면 크기·소프트 키보드·권한 차단 | 390×844의 관리자 미리보기/사용됨 오류와 실제 Ctrl+V만 확인 |
| 실제 sessionStorage 삭제·Referer·전체 히스토리 | 도구의 평가 범위에서 저장소 접근 불가. URL fragment·콜백 쿼리 제거와 로그인 후 초대 복귀는 브라우저에서 확인 |
| 클립보드 권한 차단·비로컬 HTTP | localhost의 복사→실제 붙여넣기는 확인. 거절 시 수동 복사와 보안 컨텍스트 차이는 실제 브라우저에서 미검증 |
| Web Locks 환경의 로그인 동시성과 이 기능의 상호작용, 웨일 외 브라우저 | B2의 기존 미검증 항목과 같다 |
| GitHub CI | 최종 PR head 기준으로 게시 후 확인 |
| 의존성 audit | 이번에 재실행하지 않았다 |

## 5. 실연동 절차 (후속)

준비: 가상 관리자·가상 간호사 Kakao 테스트 계정 2개, 병원 목록을 적재한 로컬 `dev` 백엔드(또는 A의 시연 시드), 웹 개발 서버 `http://localhost:5173`(README 로컬 실행 절차). Kakao 키는 셸 환경변수로만 넣는다.

1. 관리자 계정으로 로그인해 가상 병동을 만든다(B4 또는 시드). 내 병동 카드의 "초대 관리"로 들어간다.
2. WARD_MEMBER 초대를 만들고(간호사 기록 연결 있는 경우·없는 경우 각각) "웹 링크 복사"로 링크를 얻는다. 목록에 '사용 대기'가 보이는지 확인한다.
3. 다른 브라우저 프로필 또는 시크릿 창에서 링크를 연다. 로그아웃 상태 화면 → 로그인 → `/invite` 복귀 → 병원·병동·역할·만료 미리보기 확인(주소창에 fragment가 남지 않는지 포함).
4. 입사 연월(예: 2021-03)을 넣고 가입한다. 병동 목록에 새 병동이 보이고 "가입했어요" 안내가 뜨는지, 관리자 화면 목록이 '가입 완료'로 바뀌는지 확인한다.
5. 오류 화면(가상 데이터): 재발급 후 이전 링크(철회), 24시간이 지난 링크 또는 시간을 당긴 시드(만료), 이미 가입한 링크(사용됨), 이미 소속 병동의 새 초대(이미 소속), 연결된 기록을 다른 계정이 쓴 경우(연결 충돌), 미래 입사 연월(입력 오류)을 각각 확인하고 화면을 캡처한다.
6. 계정 전환: 가입 전에 로그아웃하고 다른 계정으로 로그인했을 때 초대가 이어지지 않는지 확인한다.
7. 응답 유실(선택): 브라우저 개발자 도구의 오프라인 전환으로 가입 요청 직후 연결을 끊고 "결과 확인"으로 복구되는지 확인한다.

## 6. 되돌리기와 영향

- API·DB·서버 설정 변경 없음. 웹 정적 파일만 변경되므로 이 브랜치의 변경을 되돌리면 된다.
- 새 브라우저 저장소 사용은 sessionStorage 키 1개(`nurseflow.inviteReturn.v1`)뿐이며, 되돌린 뒤 남은 항목은 어떤 코드도 읽지 않고 탭을 닫으면 사라진다.
- 배포 영향: 운영 프록시의 SPA 폴백이 `/invite`와 `/wards/<id>/invitations`를 `index.html`로 응답해야 한다(README의 Caddy·nginx 예시는 이미 해당).

## 7. Codex 리뷰 P3 3건 수정 (2026-11-12)

- 결함: (1) 재발급 뒤 수동 복사란에 이전 링크 잔존·이전 복사의 늦은 응답, (2) 화면 선택값과 다른 nurseId 전송, (3) 철회된 초대의 발급 패널 잔존.
- 수정: 패널을 invitationId로 key해 새 링크마다 복사 상태를 초기화하고 마지막 복사 시도만 반영. issue()는 현재 후보에 있는 선택만 전송. 패널이 invitationId와 가용 상태를 가지며 재조회 성공 시 목록 상태(철회·사용·만료·없음)를 반영해 PENDING이 아니면 복사 버튼·유효 안내를 없앤다. 재조회 실패면 상태를 단정하지 않는다.
- 신규 테스트 9개(InvitationAdminPage.test.tsx): 복사 실패 후 재발급, 늦은 성공·실패, 한 패널의 복사 순서, 후보 제외 뒤 nurseId 미전송, 자기 초대 철회, 재발급 응답 유실 후 재조회, 무관한 철회, 재조회 실패.
- 결과(Windows): api:check·lint·typecheck 성공, 테스트 8개 파일 **237개 통과**(228 + 9), build 성공(gzip 93.2 kB), git diff --check 통과. 변이 3개 모두 검출 후 원복. backend·OpenAPI 변경 없음.
- 미검증: 실제 브라우저·클립보드·Kakao·실서버·GitHub CI. Codex 재리뷰 대기.

## 8. 재리뷰 P3 수정: 철회 성공 후 재조회 실패 (2026-11-12)

- 결함: 철회 DELETE 204 뒤 목록 조회가 실패하면 철회된 링크의 복사 버튼·수동 복사란·24시간 안내가 남았다.
- 수정: 철회 대상 `invitationId`를 쓰기 결과에 연결해 성공 즉시 해당 패널을 REVOKED로 전환. 다른 초대 철회는 패널 유지. 응답 유실은 성공으로 보지 않고 재조회로 판단, 재조회도 실패하면 결과 미확인. 확인된 사용 불가 상태를 PENDING 목록이 되돌리지 못하게 가드.
- 테스트: 잘못된 기대의 기존 테스트를 바로잡고 5개로 분리(204+조회 실패 / 유실+조회 성공 / 서버 미처리+조회 성공 / 유실+조회 실패 / 늦은 조회). 변이(대상 연결 제거) 검출 후 원복.
- 결과(Windows): api:check·lint·typecheck 성공, 테스트 8개 파일 **241개 통과**, build 성공, git diff --check 통과. backend·OpenAPI 변경 없음.
- 미검증: 실제 브라우저·클립보드·Kakao·실서버·GitHub CI. Codex 재리뷰 대기.
