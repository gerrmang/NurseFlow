# B9b 웹 공개 근무표 조회 검증

2026-11-18, Codex. 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-published-roster`. `git fetch origin` 후 최신 `origin/dev` `9d4799156b13b6214e5e4f116d86b224dd5e1898`에서 분기했다. B4b·B5b 미병합 코드·커밋은 포함하지 않았다.

## 범위와 구현

- `web/**`, `docs/work-status.md`만 변경. backend·OpenAPI·DB·Android·CI 설정 변경 없음.
- 병동 카드 → `/wards/:wardId/published-roster`. 월 선택·이전/다음 달·새로고침, 본인/병동 전체 전환, 날짜별 달력·선택 날짜 상세·월 전체 목록.
- 관리자이고 전체 조회 권한이 있으면 전체 보기, 그 외 본인 보기로 시작. 전체 버튼은 `canViewWardCalendar`에 따라 표시한다. 조회마다 `GET /wards`로 소속·권한을 갱신하고 실제 조회 권한은 서버가 검증한다.
- 본인·전체 공개 API만 호출한다. 공개 없음과 본인 배정 없음, 간호사 기록 미연결, 비소속·권한, 네트워크·서버 오류를 구분한다. 관리자 본인 조회 불가 시 전체 보기로 이동 가능하다.
- 시간은 응답의 병동 시간대, 근무 코드는 공개 응답 값으로 표시한다. 야간 종료일이 시작일과 다르면 연도를 포함한 날짜를 붙인다. 미배정 날짜를 휴가·오프로 추측하지 않는다.
- 세션 epoch·계정·병동별 store, 요청 ticket으로 오래된 응답 폐기. 로딩·오류 시 기존 일정 제거, 로그아웃·계정 교체·병동 이탈 시 화면과 요청 결과 격리. 일정은 Web Storage에 저장하지 않는다. 기존 authorizedFetch의 no-store·401 갱신을 사용한다.
- 첫 대상 월은 브라우저 현재 월이다. 재진입 시 현재 월·기본 보기로 새로 조회한다. 자동 실시간 갱신은 없으며 재진입·새로고침으로 공개본 변경을 반영한다. 공개 이력·비교·초안 편집·확정 UI는 제외했다.

## 자동 검사

최종 Windows `web/`에서 모두 exit 0:

- `npm run api:check`: 생성 타입과 OpenAPI 일치.
- `npm run lint`, `npm run typecheck`: 통과.
- `npm test -- --run`: 12파일 중 11파일 통과·실서버 opt-in 1파일 건너뜀. **281개 통과, opt-in 1개 건너뜀, 실패 0**. 신규 기본 검사 29개(API 2, store/날짜 15, 화면 12). 기존 252개 포함.
- `npm run build`: 성공, JS gzip 96.92 kB.
- `git diff --check`: 통과. 기존 문서 LF→CRLF 안내만 발생.

검사 범위: API 경로·쿼리·인코딩, 관리자/간호사 기본 보기, 전체 권한 제거·비소속 직접 진입, 공개 없음·미연결·403·404·네트워크 오류와 재시도, 빈 배정, 월·보기 전환 중 늦은 응답, 이탈 중 소속/일정 응답, 다른 병동 응답 거절, 재진입 최신 공개본, 윤년·연말·1~9999년 월 경계, 병동 시간대·야간 종료일, 로그아웃·계정 교체 시 이전 일정 제거.

초기 신규 검사에서 야간 종료일의 ICU 표시(`11. 2.`)와 테스트 기대값(`11월 2일`) 불일치 2건이 있었다. 실제 연도 경계도 명확하도록 `YYYY-MM-DD` 표시로 통일한 뒤 통과했다. 서버 검사 타입명은 실제 OpenAPI의 `Roster`로 정정했다.

## 실제 backend 인수 검사

JDK 21, 변경 없는 backend를 `127.0.0.1:18091`, 별도 `jdbc:h2:mem:b9b_fictional_e2e`로 실행했다. 공식 동봉 병원 목록 79,899행 적재. 사용자 파일 DB는 사용하지 않았다. 기존 가상 OIDC 공급자(18090)와 웹(5174), 별도 B9b 쿠키를 사용했다. 실제 Spring 인증·서명·API·DB이며 외부 로그인 공급자와 계정만 가상이다.

`NURSEFLOW_B9B_E2E=1`로 `src/roster/PublishedRosterStore.server.test.ts` **1개 별도 통과**, 실패 0:

- 공개 전 및 초안 생성 후 공개 없음, 관리자 간호사 기록 미연결 오류.
- 공개 API로 가상 병동·근무 유형·초대 수락한 활성 간호사를 준비하고 야간 2칸 중 1칸 배정·미충원 1칸을 확인 후 확정.
- 본인 1배정, 전체 2칸·미충원 1, 병동 시간대·근무 코드, 본인 응답 no-store·absences 필드 없음 확인.
- 수정 초안을 다른 날짜로 배정해도 기존 공개본 유지. 재확정 후 store 재조회로 변경된 날짜만 표시.
- 비소속 조회 404 `WARD_NOT_FOUND`, 가입했지만 배정 없는 다른 간호사의 빈 본인 일정, 관리자 기본 전체 보기 확인.

초기 실서버 검사는 가상 authorize/token 주소 누락으로 중단됐다. 주소를 명시해 backend를 재기동했다. 이후 검증용 HTTP 어댑터에서 대소문자가 다른 Content-Type 키가 중복되어 초대 요청이 실패해 `Headers.set`으로 합쳤고 최종 통과했다. 운영 앱·서버 코드는 이 문제로 변경하지 않았다.

### 재현

서로 다른 터미널에서 실행한다. `NURSEFLOW_SESSION_SECRET`은 실행할 때 임시값을 만들며 출력하지 않는다. 아래 키는 가상 공급자 전용 상수다.

```powershell
# web/
node scripts/b3b-browser-fixture.mjs
```

```powershell
# backend/, JDK 21
$env:NURSEFLOW_SESSION_SECRET=[guid]::NewGuid().ToString()+[guid]::NewGuid().ToString()
$env:NURSEFLOW_KAKAO_REST_API_KEY='b3b-fictional-client'
$env:NURSEFLOW_KAKAO_AUDIENCES='b3b-fictional-client'
$env:NURSEFLOW_KAKAO_CLIENT_SECRET='b3b-fictional-secret'
$env:NURSEFLOW_KAKAO_REDIRECT_URIS='http://localhost:5174/auth/kakao/callback'
.\gradlew.bat bootRun '--args=--server.address=127.0.0.1 --server.port=18091 --spring.datasource.url=jdbc:h2:mem:b9b_fictional_e2e;DB_CLOSE_DELAY=-1 --nurseflow.auth.kakao.issuer=http://localhost:18090 --nurseflow.auth.kakao.jwk-set-uri=http://127.0.0.1:18090/.well-known/jwks.json --nurseflow.auth.kakao.authorize-uri=http://localhost:18090/authorize --nurseflow.auth.kakao.token-uri=http://127.0.0.1:18090/token --nurseflow.auth.web.refresh-cookie-name=nf_b9b_fixture_refresh --nurseflow.auth.web.login-cookie-name=nf_b9b_fixture_login' --no-daemon
```

```powershell
# web/, 별도 터미널
$env:VITE_DEV_API_TARGET='http://127.0.0.1:18091'
npm run dev -- --host 127.0.0.1 --port 5174
```

```powershell
# web/, 검사 후 env 제거
$env:NURSEFLOW_B9B_E2E='1'
npm test -- --run src/roster/PublishedRosterStore.server.test.ts
Remove-Item Env:NURSEFLOW_B9B_E2E
```

실행마다 새 가상 병동을 생성한다. 생성 상한에 도달하면 메모리 backend를 재시작한다. 실제 계정·운영 서버에서는 실행하지 않는다. 가상 공급자의 `/__seed`는 B3b용이므로 이번 검증에서는 호출하지 않는다.

## 브라우저 확인

`http://localhost:5174`에서 가상 관리자 로그인 → 병동 카드 → 공개 전 병동 안내 및 공개된 병동 전체 조회 확인. 날짜 선택으로 야간 `22:00 → 다음 날 07:00` 표시, 다음 달 공개 없음, Enter 키로 이전 달 복귀, 관리자 본인 조회 미연결 안내 → 전체 보기 복귀를 확인했다.

로그아웃 후 가상 간호사로 로그인해 본인 보기 기본값·야간 배정·병동 전체 전환 제공을 확인했다. 390×844 모바일 크기에서 달력과 월 입력·버튼의 줄바꿈을 스크린샷으로 확인했고, DOM의 clientWidth/scrollWidth가 모두 375로 가로 넘침이 없었다. Enter 키로 날짜를 선택해 상세 표시를 확인했다. viewport를 원복하고 검증 탭을 닫았다. 실제 Kakao 인증과 구분한다.

## 제한·배포·공유 인계

- 실제 Kakao·운영 서버·물리 휴대폰·다중 브라우저·실제 망 단절·전체 키보드 탭 순서는 미검증. 경합·세션 교체와 권한별 UI는 자동 검사로 확인했고 실제 서버 재공개는 opt-in으로 확인했다. 독립 리뷰·GitHub CI는 미실행.
- Android·backend 전체 회귀는 변경 없는 웹 기능 단위라 재실행하지 않았다. API·DB·운영 설정 변경 없음. 수동 배포 없음. 롤백은 B9b 변경 revert와 이전 웹 빌드 복원.
- 검증용 backend·OIDC·웹 프로세스 3개 종료, 18090/18091/5174 LISTENING 없음 확인. 메모리 검증 데이터는 종료로 소멸했다.
- 같은 `feat/web-published-roster`에서 미커밋 구현과 검증 기록으로 인계한다. 커밋·푸시·PR 게시·병합 없음. PR 설명 초안은 `B9b-pr-draft.md`. 기존 사용자 DB·trace·DB 복구 미커밋 기록을 보존한다.
