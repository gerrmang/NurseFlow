# B4b 웹 병원 검색·병동 생성 검증

## 2026-11-18 역할 검증 리뷰 수정

- 생성 응답의 역할 배열에 `WARD_ADMIN`이 있어야 성공으로 처리한다. 빈 배열과 `WARD_MEMBER`만 있는 배열은 결과 미확인 상태로 유지하고 동일 Idempotency-Key·본문으로 재시도한다.
- 회귀 사례 2개는 수정 전 실패했다(대상 38개 중 2개 실패·36개 통과). 수정 후 웹 전체 302개 통과·실서버 opt-in 1개 건너뜀·실패 0. API 계약·린트·타입·빌드 검사 모두 exit 0, JS gzip 97.53 kB. 루트 README의 웹 구현 현황도 갱신했다.
- 실제 Kakao·브라우저·실서버·backend·Android 전체 회귀는 웹 응답 검증/문서 수정 범위라 재실행하지 않았다. API/DB/환경 변경과 수동 배포 없음. 자체 diff 검토 완료, 독립 재리뷰·수정 후 CI는 별도 확인한다. 되돌리기는 이 수정 revert와 이전 웹 빌드 복원이다. 아래는 최초 구현 당시 검증 기록이다.

## PR #46 리뷰 수정 (2026-11-18)

- 종료 경고를 라우트 바깥 `WardCreationGuard`에서 세션별로 관리한다. 생성 중·결과 미확인 상태로 목록에 이동해도 유지되며, 늦은 성공·재시도 성공·새 요청 확인·계정 전환·로그아웃·해제에서 정리된다.
- 생성 응답 필수 필드와 타입을 검증한 뒤에만 재시도 키를 지운다. `201 null`·`{}` 등 비정상 응답에서는 오류 안내와 입력 잠금을 표시하고 같은 키·본문을 재사용한다. 선택적 병원명의 null/생략은 허용한다.
- 회귀 23개 추가. 최종 Windows `web`에서 `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 exit 0. **300개 통과·실서버 opt-in 1개 건너뜀·실패 0**, JS gzip 97.53 kB. `git diff --check` 통과. 아래 2026-11-16 결과와 구분한다.
- beforeunload 차단은 jsdom 이벤트로 검증했다. 실제 브라우저 종료 대화상자·실제 Kakao·실서버·Android·backend 전체 검사는 이번 웹 상태/응답 검증 수정에서 재실행하지 않았다. 독립 재리뷰·수정 후 CI는 미확인이다. API/DB/환경 변경·배포 없음. 되돌리기는 이번 웹 수정 revert·이전 웹 빌드 복원.

2026-11-16, Codex. 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, 기존 `feat/web-ward-create`에서 미커밋 초안을 이어 구현했다. 시작 HEAD `9d47991`은 로컬 `origin/dev`와 같다. 원격 최신 상태·GitHub CI·PR 리뷰는 이번에 조회하지 않았다.

## 구현 범위

- `web/**`와 `docs/work-status.md`. 검색 API·생성 API 소비, `/wards/new`, 병원명/주소/출처/페이지, 오류별 안내, 생성 후 설정 안내와 초대 관리 연결.
- `WardCreationFlow`가 세션 epoch별 메모리에서 상태를 유지한다. 이전 검색 응답은 ticket으로 폐기하고, 생성 중 중복 전송을 막는다. 결과 미확인에서는 입력을 잠그고 같은 키·본문으로 수동 재시도한다. SPA 목록 왕복은 유지하고 페이지 새로고침·세션 경계는 복구하지 않는다.
- 생성 요청은 공식 병원 식별자와 trim한 병동명, `Asia/Seoul`. 서버의 생성 상한·중복·권한 판단을 유지했다. 서버·OpenAPI·DB·환경변수 계약 변경 없음. 기존 DB 복구 기록과 파일을 보존했다.

## 자동 검사

- `web`: `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 성공. 최종 10파일 277개 통과·실패 0, 실서버 opt-in 1개 기본 건너뜀. 별도 opt-in 실행에서는 1개 통과. JS gzip 97.34 kB, `git diff --check` 통과.
- 신규 단위·화면 검사는 잘못된 검색 입력, 이전 응답 폐기, 빈 결과/장애, 페이지 상한, 동명 병원 주소 선택, 100/101자 경계, 중복 클릭, 생성 오류, 응답 유실 키/본문 보존, 목록 왕복, 새 요청 확인, 계정 전환을 검사한다.
- 최초 typecheck에서 초안의 필수 타입 `timeZone` 누락을 발견해 명시했다(서버 기본값도 Asia/Seoul). 신규 화면 테스트의 잘못된 Testing Library 옵션, StrictMode의 두 번 조회, 요청별 AbortSignal 비교를 정정한 뒤 재실행했다. 테스트 기대값 변경을 제품 결함 수정으로 기록하지 않는다.

## 실제 backend·브라우저

- JDK 21로 현재 backend를 수정 없이 루프백 `18091`, 별도 `jdbc:h2:mem:b4b_fictional_e2e`로 기동했다. 병원 동봉 목록 79,899행 적재 확인. 기존 파일 DB는 사용하지 않았다.
- 기존 `scripts/b3b-browser-fixture.mjs`를 `18090`에서 사용하고 웹 `5174`를 연결했다. 실제 Spring 인증·서명 검증·API·DB를 사용하고 로그인 공급자와 사용자만 가상이다. 실제 Kakao 검증과 구분한다. 시드는 호출하지 않았다.
- 브라우저: 로그인→소속 없음→생성 화면, 빈 검색 결과/출처, `365` 검색 840곳/10개 후보, 동명 병원의 주소 구별과 선택, 가상 검증 병동 생성, 설정 안내, 서버 목록의 병원명·관리자 표시, 해당 병동 초대 관리 진입 성공. 성공 화면 스크린샷으로 레이아웃 확인.
- 최초 샌드박스 실행은 소켓 접근 제한으로 로컬 연결이 실패했다. 검증용 Node/Vite를 별도 권한으로 다시 실행한 뒤 같은 IAB에서 성공했다. 네이티브 Whale 선택 오류도 있었으나 최종 검증에는 사용하지 않았다. 운영 보안 설정은 바꾸지 않았다.
- 실제 backend 연동 검사 `WardCreationFlow.server.test.ts` **1개 통과**: 전송 전 연결 단절, 서버의 201 처리 후 응답 폐기를 각각 재현하고 실제 클라이언트 flow/API로 같은 키 재시도. 각 병동 1개·총 2개 증가, 관리자 소속, 같은 키 재생, 새 키 중복 409, 다른 본문/같은 키 409 확인. 실제 물리 네트워크를 끊은 검사가 아니라 전송 경계에서 오류를 주입한 검사다.

## 연동 검사 재현

`B3b-verification.md`의 가상 공급자·backend·웹 명령을 사용하되 DB는 `jdbc:h2:mem:b4b_fictional_e2e`, refresh/login 쿠키는 `nf_b4b_fixture_refresh`/`nf_b4b_fixture_login`으로 분리한다. 기존 실제 계정 서버와 사용자 파일 DB에 연결하지 않는다. `__seed`는 호출하지 않는다.

새 메모리 서버에서 `web/` PowerShell:

```powershell
$env:NURSEFLOW_B4B_E2E='1'
npm test -- --run src/ward/WardCreationFlow.server.test.ts
Remove-Item Env:NURSEFLOW_B4B_E2E
```

검사는 가상 admin2로 병동 2개를 만든다. 반복 실행하면 계정 생성 상한에 도달하므로 공급자와 메모리 backend를 함께 재시작한다. 기본 테스트에서는 이 검사 1개를 건너뛴다. 소켓 제한 환경에서는 로컬 연결 권한이 있는 셸이 필요하다.

## 미검증·배포·인계

- 실제 Kakao, 모바일 화면 크기, 브라우저의 실제 종료 경고, 실서비스 외부 검색 장애·목록 미적재·생성 상한은 이번 실연동에서 미검증. 오류 경로는 목업 자동 검사로 확인했다. Android·backend 전체 회귀는 클라이언트 단위라 재실행하지 않았다. 독립 재리뷰·GitHub CI는 미실행.
- 웹 정적 빌드에만 영향. 배포·커밋·푸시·PR·병합 없음. 되돌리기는 B4b 웹 변경을 이전 버전으로 복원. 새 환경변수·DB 마이그레이션 없음.
- 같은 브랜치·미커밋 변경에서 인계한다. 검증용 웹·공급자·backend를 종료했고 5174/18090/18091 LISTENING 없음 확인. 가상 서버 데이터는 메모리 DB 종료로 소멸했다. `docs/work-status.md`의 B4b 절에 동일 결과를 기록했다.
