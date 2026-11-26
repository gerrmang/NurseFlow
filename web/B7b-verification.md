# B7b 웹 관리자 근무 설정 검증

## A13 포함 최신 dev 통합 (2026-11-25)

- PR #59의 `b2f5448`에 최신 dev `3e30752`(A13 #58)을 충돌 없이 통합했다. B7b 동작 코드 변경 없음. backend·OpenAPI·웹 생성 타입은 dev와 동일하고 A13 관리 화면은 추가하지 않는다.
- 웹 api:check·typecheck·lint·build exit 0. 전체 447개 통과·opt-in 4개 건너뜀·실패 0(451개/25파일), JS gzip 114.84 kB. 통합 JAR·가상 OIDC·새 메모리 H2에서 B7b 인수 1개 통과·실패/건너뜀 0(3.40초).
- backend는 B7a 최신 dev 통합과 동일한 코드/JAR이므로 직전 전체 검사 304개 통과·opt-in 5개 건너뜀·실패/오류 0을 재사용했다. 브라우저·실제 Kakao·물리 기기·Android 로컬 전체·운영 검사는 반복하지 않았다. Android는 최종 PR CI에서 확인한다. 기존 독립 리뷰 결과를 유지한다.
- API/DB/환경·서버 규칙의 B7b 추가 변경·수동 배포 없음. 롤백은 통합 커밋 첫 부모 기준 revert 및 이전 웹 빌드 복원. 검증용 서버/공급자 종료·포트 해제는 작업 현황과 최종 정리에서 확인한다.

## A14 포함 dev 재통합 검증 (2026-11-24 23:38 KST)

- 사용자 요청으로 `9e72855`에서 최신 `origin/dev` `db5e4120d04f9cd259bdb4ad509088ebf1598cd6`(A14 #57)을 같은 B7b 브랜치에 통합했다. 충돌은 작업 현황 문서 한 곳이며 B7b와 A14 기록을 모두 보존했다. B7b 동작 코드는 바꾸지 않았다. backend·OpenAPI·웹 생성 타입은 dev와 동일하고 지원 허용 관리 UI는 새로 추가하지 않는다.
- 웹 api:check·typecheck·lint·build exit 0. 전체 **447개 통과·opt-in 4개 건너뜀·실패 0**(총 451개/25파일), JS gzip 114.84 kB. JDK 21 backend `test bootJar --offline --no-daemon` BUILD SUCCESSFUL(1분 54초), XML 총 294개 중 **289개 통과·opt-in 5개 건너뜀·실패/오류 0**. `git diff --cached --check` 통과.
- A14 통합 JAR·가상 OIDC·새 메모리 H2 `b7b_a14_merge_e2e`에서 실제 서버 인수 **1개 통과·실패/건너뜀 0**, exit 0. 검증용 Java·공급자 종료와 18090/18091 포트 해제를 확인했다. 사용자 DB·trace는 변경하지 않았다.
- 이번 재통합에서 브라우저·실제 Kakao·운영·물리 기기·Android 로컬 전체 검사는 반복하지 않았다. 최종 PR CI에서 Android를 포함해 확인한다. API·DB·환경·서버 규칙의 B7b 추가 변경 없음. 웹 빌드 영향, 수동 배포 없음. 롤백은 이번 통합 커밋을 첫 부모 기준 revert하고 이전 웹 빌드를 복원한다.
- 이전 최종 head `9e72855`의 Verify `45406922044` 전체 성공은 확인된 기록이며 이번 통합 head CI와 구분한다. 현재 PR #59의 본문·원격 head와 최종 CI를 푸시 후 확인한다.

- 게시: [PR #59](https://github.com/gerrmang/NurseFlow/pull/59), 대상 `dev`, 일반 PR. 구현·통합 head `deeb6bb`, 원격 SHA 일치 확인. GitHub Verify와 최종 게시 기록은 `docs/work-status.md` 최신 절을 확인한다. 아래 '게시 없음' 표현은 당시 시점 기록이다.
- 원격 검증: 문서 head `1739798`의 [Verify 45406333985](https://github.com/gerrmang/NurseFlow/actions/runs/45406333985)에서 Backend tests·Android debug build·Web checks·Demo seed checks 모두 success. 이전 구현 head에서는 기존 Android 테스트 1건이 실패했고 동일 Android 소스의 최신 실행은 통과했다. 정확한 검사명과 제한은 공유 현황에 기록했다. GitHub 승인 리뷰는 별도다.

## 최신 dev 통합 검증 (2026-11-24 22:40 KST)

- B7b 구현·P3 수정을 `bb6cef2`에 보존한 뒤 최신 `origin/dev` `74886b6`(A11 #54·B6 #51·A12 #56)을 같은 브랜치에 통합했다. 충돌은 `docs/work-status.md` 한 곳이며 양쪽 이력을 모두 보존했다. B6 시연 모드 화면과 B7b 메뉴 연결을 함께 유지했다. backend·OpenAPI·웹 생성 타입은 dev와 동일하다.
- 웹 api:check·typecheck·lint·build exit 0. 전체 검사 **447개 통과·opt-in 4개 건너뜀·실패 0**(총 451개/25파일), JS gzip 114.84 kB. `git diff --cached --check` 통과.
- JDK 21 backend `test bootJar --offline --no-daemon` BUILD SUCCESSFUL(1분 31초, 8개 작업 중 7개 실행·1개 UP-TO-DATE). XML 총 288개 중 **283개 통과·opt-in 5개 건너뜀·실패/오류 0**.
- 통합 JAR·가상 OIDC·별도 새 메모리 H2 `b7b_dev_merge_e2e`에서 B7b 실서버 인수 **1개 통과·실패/건너뜀 0**, exit 0. 검증용 서버·공급자 종료와 18090/18091 포트 해제를 확인했다. 사용자 파일 DB는 보존했다.
- 이번 통합에서 브라우저·실제 Kakao·물리 기기·운영·Android 로컬 전체 검사는 반복하지 않았다. Android 추가 수정이 없으며 원격 PR CI에서 확인한다. 독립 리뷰 후 B7b 동작 코드는 바꾸지 않았고, 통합 diff에서 팀 관리 링크와 B6 화면 공존을 확인했다. 최종 원격 CI와 PR 게시 결과는 공유 현황에 기록한다.
- 최신 dev를 통합했지만 B7b가 추가로 바꾸는 API·DB·설정·서버 규칙은 없다. 웹 정적 빌드 영향, 배포 없음. 기능 롤백은 B7b 변경 제거·이전 웹 빌드 복원, 통합 커밋만 되돌릴 때는 첫 부모 기준 revert를 사용한다.

## 독립 리뷰 P3 수정 검증 (2026-11-24 22:32 KST)

- 기존 승인 부재 편집 시 각 시간 필드가 원래 표시값과 같으면 서버의 원본 `startsAt`·`endsAt`을 전송한다. 변경한 시간 필드만 병동 시간대로 변환한다. 다른 필드만 수정할 때 초·소수초·UTC 오프셋과 DST 반복 시각의 원래 회차를 보존한다. 새로 입력하거나 실제 변경한 모호한 DST 시각은 계속 거절한다.
- 원본 시각과 새 시각의 순서는 문자열 대신 절대 시각으로 비교하며, Java Instant의 나노초 정밀도를 보존한다. 같은 정밀도 패턴을 찾아 승인 부재 API 응답 검증의 `Date.parse` 비교도 함께 맞췄다.
- 수정 전 새 화면 회귀 6건 실패·기존/추가 안전 검사 14건 통과로 결함을 검출했다. 수정 후 관련 3파일 39건 통과. 초/소수초·서로 다른 오프셋·DST 두 회차·1ns 간격·한쪽 시간만 변경·새 모호 시각 거절·역전 구간 거절을 확인했다.
- 최종 전체 웹 검사 **437개 통과·opt-in 4개 건너뜀·실패 0**(총 441개/25파일), typecheck·lint·build exit 0, JS gzip 114.55 kB, `git diff --check` 통과. 계약/생성 타입 변경이 없어 직전 api:check 결과를 유지한다.
- 독립 수정 재리뷰: 원 P3 해결, 새 P1~P5 발견 없음. 별도 에이전트가 수정 회귀 10개를 실행해 통과·실패 0(exit 0), 필터로 15개 선택 제외. 전체 회귀 결과는 위 부모 실행 결과와 구분한다. 상세 기준은 `docs/work-status.md` 최신 절에 기록했다.
- 서버·브라우저·실제 Kakao·Android·GitHub CI는 이번 클라이언트 원본 시각 보존 수정에서 재실행하지 않았다. 아래 실제 서버/브라우저 기록은 최초 구현 검증이며, 이번 변경은 실제 페이지와 API 직렬화를 통과하는 DOM 회귀로 검사했다.

## 범위와 기준

- 2026-11-24, 같은 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`에서 최신 `origin/dev` `00f086c` 기반 `feat/web-schedule-settings`로 분기했다. B6 `chore/demo-seed-script`와 사용자 `nurseflow.mv.db`·`nurseflow.trace.db`를 보존했다. 별도 worktree는 만들지 않았다.
- 실서버 연동(`dev` `00f086c`): A8 근무 유형 CRUD/preview, A9 팀별 수요·선임 칸, A10 근무 형태, 기존 초안·승인 부재 API를 소비한다. backend·OpenAPI·생성 타입·DB·의존성·환경변수 계약 변경 없음.
- `/wards/:wardId/schedule-settings`의 네 메뉴와 내 병동·팀 관리 연결을 추가했다. 정책/초안 ETag, 쓰기 직렬화, 최신 값 재조회, 권한/세션 경계, 늦은 응답 폐기, 응답 유실 시 재전송 차단을 적용했다. 승인 부재는 병동 시간대로 변환하고 사유를 수집하지 않는다.
- 수요 API는 전체 교체이며 기존 필요 자격을 조회할 수 없다. 기존 칸의 수요는 읽기 전용으로 보여주고 자동 복원을 하지 않는다. 모든 수요·자격을 새로 작성해 전체 교체한다는 체크와 확인 단계를 거친다. 기존 배치가 있으면 UI·서버가 교체를 차단한다. 배치 해제·자동배치·확정 편집은 B10b 후속이다.

## 최초 구현 자동 검증 (아래 수치는 수정 전 시점 기록)

최종 실행: Windows, 2026-11-24 15:30 KST. 명령은 `web/`에서 실행한다.

- `api:check`·`typecheck`·`lint` 모두 exit 0. 전체 웹 테스트 **427개 통과·opt-in 4개 건너뜀·실패 0**, 21파일 통과·4파일 건너뜀(총 431개/25파일). B7b 신규 기본 검사는 43개이며 새 opt-in 1개는 아래 실제 서버 검사로 별도 실행했다.
- 최종 실제 서버 인수 검사 **1개 통과·실패/건너뜀 0**, exit 0. 기본 전체 검사에서 건너뛴 B7b opt-in과 같은 검사다.
- `build` exit 0, 127 modules, JS gzip 114.44 kB. `git diff --check` 통과. backend·OpenAPI diff 0줄.
- 검증용 Java·공급자·Vite를 종료하고 18090·18091·5174 포트가 더 이상 listen하지 않음을 확인했다. 자체 diff·호출 경로 점검 완료, 독립 리뷰와 CI는 별도다.

| 명령 | 검증 내용 |
| --- | --- |
| `npm run api:check` | OpenAPI 생성 타입 일치 |
| `npm run typecheck` | 앱·설정·테스트 TypeScript |
| `npm run lint` | 전체 웹 ESLint |
| `npm test -- --run` | 기존 웹 회귀 + B7b API/Store/시간대/화면 회귀 |
| `npm run build` | 정적 프로덕션 번들 |
| `NURSEFLOW_B7B_E2E=1` 후 `npm test -- --run src/schedule/ScheduleSettingsStore.server.test.ts` | 가상 OIDC + 실제 Spring + 별도 메모리 H2 인수 검사 |

- B7b API: 정책·초안 ETag, 생성 멱등 키, 전체 교체 필드·false/0 보존, ID 인코딩, 부재 쓰기, 서버 오류 코드, 무본문/비정상 성공 응답·잘못된 날짜/시간대 거절.
- Store: 관리자 사전 확인, 동시 부재 변경 중 혼합 revision 거절, 쓰기 직렬화·저장 후 조회, 412·기존 배치 오류, 응답 유실/5xx/비정상 JSON 잠금, 저장 성공 후 조회 실패 잠금, 권한 상실 데이터 폐기, 월 전환의 늦은 응답 폐기, 오래된 정책 preview 차단.
- 화면: 비관리자 직접 URL 차단, 자정·인정시간 입력 검사, 변경 영향 확인, 기존 OTHER 처리, 팀·필요 자격 포함 전체 교체 확인, 기존 배치 차단, 초안 자동 생성 없음, 병동 시간대 반차 변환·사유 입력 없음, 응답 유실 잠금, 로그아웃 폐기, 근무 형태 저장 후 선택 유지·실패 시 GENERAL 추정 금지.
- 시간대: Asia/Seoul·UTC·고정 오프셋·자정 경계, New York의 존재하지 않거나 중복되는 DST 시각, 잘못된 날짜·시간대.
- 중간 검사에서 타입 가드의 unknown 축소·테스트 인자, 미사용 테스트 변수, Headers 기대값을 정정했다. 근무 형태 재조회 테스트는 StrictMode의 두 번 읽기에 같은 Response를 재사용해 실패했으며 매번 새 응답을 반환하는 대역으로 수정했다. 최종 결과와 구분한다.

## 실제 서버 인수·브라우저 확인

- 가상 관리자/간호사 신원은 기존 `scripts/b3b-browser-fixture.mjs`를 사용했다. 실제 Kakao 인증이 아니다. 공급자 18090, 실제 백엔드 18091, 웹 5174로 분리하고 H2는 `jdbc:h2:mem:b7b_fictional_e2e;DB_CLOSE_DELAY=-1`로 실행했다. 사용자 파일 DB는 열지 않았다.
- JDK 21 `backend/.\gradlew.bat bootJar --offline --no-daemon` BUILD SUCCESSFUL, 4개 작업 UP-TO-DATE. 샌드박스의 Gradle 캐시 잠금·소켓 제한은 권한 확장으로 해결했다. 앱/서버 운영 코드는 바꾸지 않았다.
- 실제 API: D 유형 생성·시간/환산 수정·영향 미리보기, 팀별 2칸과 선임 1칸·선임 미충원, 초안 시각/ETag/재검증 갱신, 오래된 ETag 412, 반차 등록·수정·삭제와 VY 등록, 일반→N 전담 저장·재조회, 새 Store 재진입 복원, 성공한 생성 응답 유실 후 중복 재전송 차단, 유형 비활성화·재활성화·기존 칸 유지, 자정 경과 Night 540분, 간호사의 관리자 API 403을 확인했다.
- 브라우저: 가상 관리자 로그인→내 병동의 근무 설정→D 월 환산 420분 변경 영향 확인·저장→간호사 D/E 전담 저장→11월 4일 A팀 D 3명·필요 자격 전체 교체 확인·저장→11월 5일 09:00~13:00 승인 반차 등록·재조회→페이지 reload 후 동일 일정 유지 확인. revision 12와 병동 시간대 표시를 확인했다.
- [브라우저 확인 화면](B7b-browser-verification.jpg)은 가상 데이터만 포함한다. 저장 후 간호사 선택 유지 보완은 DOM 회귀 테스트로 별도 확인했다. 해당 작은 보완 뒤 전체 브라우저 흐름은 반복하지 않았다.

## 재현 환경

`B3b-verification.md`의 가상 공급자·backend·웹 실행 절차에서 DB를 `b7b_fictional_e2e`, 쿠키 이름을 `nf_b7b_fixture_refresh`·`nf_b7b_fixture_login`으로 바꾼다. 실제 인증 서버·사용자 DB와 연결하지 않는다. 새 메모리 DB에서 `web/` PowerShell:

```powershell
$env:NURSEFLOW_B7B_E2E='1'
npm test -- --run src/schedule/ScheduleSettingsStore.server.test.ts
Remove-Item Env:NURSEFLOW_B7B_E2E
```

검사마다 새 가상 병동을 만든다. 관리자 생성 상한에 도달하면 검증용 공급자와 메모리 백엔드를 함께 재시작한다. 토큰·쿠키·서명 키는 출력하거나 파일로 저장하지 않는다.

## 제한·배포·롤백

- 실제 Kakao 재로그인·실제 두 관리자 브라우저의 동시 편집·모든 화면 크기·물리 기기·운영 환경·독립 리뷰·GitHub CI는 미검증이다. 서버/Android 코드는 변경하지 않아 전체 backend·Android 회귀는 생략했다.
- 근무 형태는 서버 계약상 마지막 저장 우선이다. 수요는 날짜별 항목 입력이며 일괄 요일 복사·미저장 입력 보존은 없다. 서버 필수 규칙은 그대로 적용한다.
- 웹 정적 빌드에만 영향이 있다. API/DB/설정·의존성 변경, 데이터 이전과 수동 배포는 없다. 롤백은 B7b 웹 변경 revert와 이전 웹 빌드 복원이다. 커밋·푸시·PR 게시·병합 없이 같은 task branch의 변경으로 인계한다.

## PR #59 리뷰 수정 검증 (2026-11-26)

- P2: 저장/preview의 HTTP 400 거절 뒤 폼 입력을 유지한다. 409/412와 저장 결과 불명·권한 상실 처리는 유지한다.
- P2: 필요 자격을 제출 직전 trim하고 빈 항목을 제거한 뒤 검증/전송한다.
- P3: 기본 대상 월을 브라우저 로컬 연월로 선택한다.
- `TZ=Asia/Seoul` 관련 테스트 49개 통과, 전체 `npm test -- --run` 461개 통과·실서버 opt-in 4개 건너뜀·실패 0. typecheck·lint·api:check·build·diff 공백 검사 성공.
- 수정 diff 자체 재검토. 독립 재리뷰·브라우저 수동·실서버·실제 Kakao·backend/Android 전체 검사는 이번 변경에서 미실행. API/DB/환경 변경 없음, 웹 정적 빌드 영향. 롤백은 수정 커밋 revert 및 이전 빌드 복원.
