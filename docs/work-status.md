# 공유 작업 현황

## B8a PR #62 최신 dev(B7a #60) 통합 (2026-11-26, Claude)

- 사용자 요청으로 `feat/android-off-requests`에 B7a(#60)가 병합된 `origin/dev` `3c777e9`를 병합했다. 충돌 6파일(`AppContainer.kt`, `ApiFactory.kt`, `WardListScreen.kt`, `NurseFlowRoot.kt`, `android/README.md`, 이 문서)은 모두 양쪽을 함께 유지해 해결했다.
- 결과: API 팩토리에 `offRequestApi`·`scheduleSettingsApi`를 모두 두고, 병동 목록에 희망 오프·근무 설정 진입을 모두 노출하며, 루트 화면 분기에 희망 오프와 근무 설정을 각각 둔다. 초대 수락 시 두 화면 상태를 모두 초기화한다. 기능 동작 변경은 없다.
- 이 환경에는 Android SDK가 없어 로컬 빌드·테스트를 실행하지 않았다. 원격 CI(Android debug build 등)로 컴파일을 확인한다. 기기·실서버 재검증은 하지 않았다. PR 병합·배포하지 않았다.

## B8a 리뷰 수정 푸시·스레드 해결 완료 (2026-11-26, Codex)

- 사용자 명시 요청으로 수정 커밋 8beb3ab와 최신 dev 748a0a9 문서 통합을 기존 PR #62 브랜치에 푸시했다. 게시 확인 head 3f693ba877b33c689aac8444e08fa05790c32a40와 원격 일치. 통합 추가 diff는 docs/holiday-calendar-client-handoff.md와 docs/work-status.md뿐이며 충돌 시 양쪽 현황을 모두 보존했다.
- GitHub 연결 도구의 스레드 해결은 integration 권한 FORBIDDEN으로 실패해 기존 인증된 gh GraphQL로 처리했다. 해당 리뷰 스레드 3개 모두 해결, 재조회 미해결 0건. PR 기존 본문을 보존하며 수정·검증·제한을 추가했다. 독립 승인·병합과 구분한다.
- 이 head의 Verify 45761307233 조회 시 Demo/Backend/Android/Web 4개 IN_PROGRESS, Vercel/Preview Comments SUCCESS. PR은 OPEN·MERGEABLE·BLOCKED·autoMergeRequest=null이다. 승인 완료·CI 전체 통과로 표시하지 않는다. 이 게시 기록을 포함하는 최종 문서 head의 CI는 별도다.
- 제품 코드·테스트는 앞선 검증 스냅샷과 동일함을 SHA256으로 확인했다. 검사를 반복하지 않았고 git diff --check 통과. 이 문서만 후속 커밋·푸시하며 API/DB/환경/배포 영향·롤백은 직전 리뷰 수정 기록과 같다. 같은 worktree·브랜치에서 최종 head CI와 독립 재리뷰/승인 확인으로 인계한다. GitHub PR 병합·자동 병합·수동 배포 없음.

## B7a PR #60 최신 dev 통합 (2026-11-26, Claude)

- 사용자 요청으로 `feat/android-schedule-settings`에 최신 `origin/dev` `ae24f27`(#63 문서, #64 웹 디자인)을 병합했다. 충돌은 이 문서뿐이며 양쪽 기록을 모두 보존했다(dev 기록을 위에 둠).
- 병합 기준 이후 dev의 `android/**`·`backend/**` 변경은 없어 B7a 코드 영향은 없다. 로컬 Android 빌드는 이 환경에서 실행하지 않았고 원격 CI 결과로 확인한다.
- 인계: 다음은 #62(B8a)에 이 병합 이후의 dev를 반영한다. #60과 Android 파일 5개가 겹친다. PR 병합·배포는 하지 않았다.

## PR #64 리뷰 수정 (2026-11-26, Codex)

- 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, 기존 `fix/web-figma-ui`, 기준 HEAD `2012e68`에서 [리뷰](https://github.com/gerrmang/NurseFlow/pull/64#pullrequestreview-6069089093)를 반영했다. 사용자 DB 두 파일은 그대로 보존했다.
- P2: `.staff-page`의 1040px 상한을 복원하고, 공통 `.staff-list li`의 세로 배치·여백을 복원했다. 카드형 목록 스타일은 `.staff-directory` 안에만 적용해 구성원 접근 목록과 후속 #59 근무 설정 목록으로 번지지 않게 했다. 본문은 시스템 글꼴로 전환하고 Noto Sans KR 전체 파일·관련 라이선스를 제거했다. 최종 빌드의 폰트 자산은 Inter 350.34kB만 남는다.
- P3: 로그인 버튼의 보이는 문구와 접근 가능한 이름을 `카카오로 로그인`으로 통일하고 화면 크기와 무관하게 h1을 하나만 제공한다. 간호사 행은 `aria-describedby`로 팀·팀 내 순서·편성 단계·활성 상태·선임 자격·계정 연결 여부를 설명한다. 밝은 테마 고정 결정을 `web/README.md`에 기록했다.
- 공개 월간 표는 slot 응답 순서 대신 이름·동명이인 ID순으로 안정 정렬하고 표시 기준을 안내한다. **팀별 정렬은 후속 계약 보완 사항**이다. 공개 Assignment에는 소속 팀·팀 내 순서가 없으며, StaffingSlot의 `teamId`는 지원근무의 배치 팀일 수 있어 소속 팀으로 추정하지 않았다. 관리자 전용 staff API를 간호사 공개 화면에 추가 호출하지 않았다.
- 최종 검증: web `npm run api:check`, `npm run typecheck`, `npm run lint`, `npm run build` 성공. `npm test -- --run`: 397개 통과·기존 opt-in 3개 건너뜀·실패 0. 로그인 접근성·행 설명·응답 역순 및 동명이인 정렬 회귀 검증 포함. `git diff --check` 성공. 이번 수정 diff를 자체 재검토했다. 독립 재리뷰·브라우저 시각 검증·실제 스크린리더·실제 Kakao·실서버·Android·backend 검사는 이번에 실행하지 않았다.
- API·DB·환경 변경 없음. 웹 정적 자산만 영향이 있고 시스템 글꼴에 따라 자폭·줄바꿈이 달라질 수 있다. 롤백은 수정 커밋 revert 후 웹 정적 빌드 재배포다.
- 인계: 같은 브랜치의 PR #64로 이어간다. #59의 `근무 설정` 링크는 추후 통합 시 segmented 탭 nav 밖의 이동 링크로 배치하고, #61의 `.off-page` 규칙을 유지한다. #59~#62의 문서 충돌은 양쪽 진행 기록을 보존한다. 다른 열린 PR을 이번 브랜치에 선행 병합하지 않았다. 원격 CI는 수정 커밋 게시 후 별도로 확인하며 병합·수동 배포하지 않는다.

## 웹 기존 화면 디자인 반영·재검증 (2026-11-26, Codex)

- 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, `fix/web-figma-ui`, 기준 HEAD `3e30752`. 이후 origin/dev의 #63은 문서만 추가하며 웹 코드 차이는 없다. 로그인·병동 선택/생성·초대·구성원·공개 근무표의 기존 기능에 디자인을 반영했다. Android·서버·API·DB·환경 변경 없음.
- Figma 도구로 확보한 W0/W11 원본과 사용자가 허용한 브라우저 원본 확인을 사용했다. 도구 월간 읽기 한도는 여전히 남아 있다. 웹 전용 로그인 분할 배치와 개인 달력/병동 월간 표를 추가하고, 독립 리뷰의 구성원 행 포커스 잘림과 월 선택 입력·비활성 화살표 표시를 수정했다. 보기 전환·새로고침 시 선택 날짜 유지 회귀 테스트 추가. 미구현 홈·초안 편성·알림 등은 추가하지 않았다.
- 최종 로컬 검증: `npm run api:check`, `npm run typecheck`, `npm run lint`, `npm test -- --run`(395개 통과·기존 3개 건너뜀), `npm run build`, `git diff --check` 성공. 읽기 전용 독립 재리뷰에서 추가 조치 사항 없음. 이후 표 렌더링의 간호사/날짜별 집계를 분리했고 루트에서 재검토·재검증했다.
- 실제 앱 컴포넌트와 가상 응답을 사용하는 임시 브라우저 페이지에서 1440·1024·360px 가로 넘침 없음, 로컬 아이콘 로드/크기, 로그인→초대 입력, 근무표 날짜→보기 전환의 날짜 유지, 월 키보드 조작, 구성원 키보드 포커스→상세 이동을 확인했다. 임시 페이지는 마지막 정식 빌드로 제거했다. 실제 Kakao·실서버·기기·원격 CI 검사는 재실행하지 않았다.
- 최신 dev 통합·PR 준비: 디자인을 `c3e84ff`로 커밋하고, 기존 화면 수정 성격에 맞춰 사용자와 브랜치 이름을 `fix/web-figma-ui`로 정리했다. `origin/dev` `748a0a9`를 `f55ff43`으로 병합했으며 작업 기록 문서의 충돌은 양쪽 내용을 모두 유지해 해결했다. 통합 후 웹 API 타입·타입 검사·Lint·테스트(395개 통과·기존 3개 건너뜀)·빌드와 `git diff --check origin/dev...HEAD` 성공. 폰트 라이선스의 행 끝 공백도 정리했다.
- 인계: `fix/web-figma-ui` → `dev` [PR #64](https://github.com/gerrmang/NurseFlow/pull/64)를 게시했다. 같은 공유 worktree와 브랜치에서 후속 검토한다. 사용자 DB 두 파일은 커밋에서 제외해 보존했다. PR 병합·배포하지 않았다. 웹 정적 자산 배포 영향만 있으며 스쿼시 병합 후에는 해당 커밋을 revert한다. 원본과 모든 화면의 픽셀 단위 완전 일치를 보증하는 검증은 아니다.

## 공휴일·휴일 달력 클라이언트 인수인계 문서 (2026-11-26, Claude)

- 브랜치 `claude/project-thread-0nhhfg`(최신 `dev` `3e30752`에서 분기). 문서만 추가: `docs/holiday-calendar-client-handoff.md`.
- 내용: A13 휴일 달력·공휴일·목표시간 API, 달력 표시 우선순위(병동 항목 > 국가 공휴일 > 주말), 관리자 설정 화면 요구, 정책 ETag 공유, 오류 코드별 화면 처리, 알려진 공백(자동배치 미반영·간호사용 표시 API 없음·운영 키 미확인), B 쪽 권장 순서.
- 확인: `origin/dev`와 열린 PR #59~#62의 `web/src`·`android`에 휴일 달력 사용 코드 없음(`schema.gen.ts` 타입만). 문서의 응답 예시 수치는 `TargetHourCalculator.monthTarget` 규칙으로 손계산했다. 빌드·테스트는 문서 변경이라 실행하지 않았다.
- API/DB/환경 변경 없음. 공유 인계: B7a·B7b 병합 후 휴일 달력 화면을 별도 행으로 둘지 B10에 포함할지 `docs/feature-breakdown.md`에서 정한다.

## B8a 리뷰 수정 게시 준비 (2026-11-26, Codex)

- 사용자 요청으로 검증된 수정만 기존 PR #62 브랜치에 커밋·푸시하고 해당 리뷰 스레드를 해결한다. 수정 시점 diff 해시와 현재 상태가 일치해 제품 검사를 반복하지 않으며 git diff --check를 확인한다.
- git fetch origin 후 최신 dev 748a0a9의 추가 변경은 인계 문서·작업 현황뿐이다. 게시 전 동일 작업 브랜치에 통합하고 현황 충돌은 양쪽 기록을 보존해 해결한다. API/DB/환경·제품 코드 추가 변경 없음. PR 병합·자동 병합·수동 배포 없음. 최종 원격 head·스레드·CI는 게시 후 확인한다.


## B8a PR #62 리뷰 수정 (2026-11-26, Codex)

- 수정 diff 자체 검토 기준: 2026-11-26 14:20:59 KST, Codex, HEAD 4971b3b00847292b30129a639e60d4fb96db4e3f, git diff HEAD(상태 문서 제외) 원시 바이트 SHA256 5489896029555fcca5d4fb3f2460c2a6a4d0b09c554eacaaa1796293a2a000d5. 미추적 android/app/src/androidTest/kotlin/com/nurseflow/app/feature/offrequest/OffRequestRotationDeviceTest.kt SHA256 75861fd0ffa910ef1057923213a8da76d7d89f7b236cccebadbbc43fe61a310b. 외부 독립 승인과 구분한다.

- GitHub 리뷰 스레드 3개와 리뷰 본문을 확인해 P2 1건·P3 2건을 수정했다. 기준 head 4971b3b00847292b30129a639e60d4fb96db4e3f는 로컬 feat/android-off-requests와 일치했다. 커밋·푸시·GitHub 답글/스레드 해결·병합·배포는 수행하지 않았다.
- P2: OffRequestScreen의 DisposableEffect 정리에 기존 StaffAdminScreen과 같은 Activity.isChangingConfigurations 가드를 적용했다. 구성 변경은 retained ViewModel의 읽기·쓰기와 입력을 보존하고 실제 화면 이탈은 기존 deactivate로 취소/불확실 결과 보호를 유지한다. 같은 패턴 검색 결과 StaffAdminScreen은 이미 가드가 있으며 이번 수정은 offrequest 경로뿐이다.
- P3: 목록·정책 monthlyLimit 불일치 시 조회만 1회 재시도한다. 계속 바뀌면 정책 변경 안내와 stale 편집 차단을 유지한다. 지연된 재시도 응답도 ticket/월/세션 검사로 폐기한다. POST·PUT 자동 재전송은 추가하지 않았다.
- P3: 403 NURSE_IDENTITY_REQUIRED는 이전 데이터 폐기·관리자 문의 안내·재조회로 분리했다. 다른 403/401/소속·역할 상실은 기존 deny/세션 처리를 유지하며 간호사 기록 미연결을 역할 상실로 표시하지 않는다.
- ViewModel 회귀 5개(조회 재시도 성공·상한·지연 응답 폐기, 초기 미연결과 기존 데이터 조회 후 미연결)를 추가했다. HEAD ViewModel로만 일시 복원한 수정 전 검사에서는 신규 5개 모두 실패(exit 1); finally로 수정본을 복원한 뒤 전체 JVM **245개 중 243개 통과·실서버 opt-in 2개 건너뜀·실패/오류 0**. JDK 21, ANDROID_HOME 기존 SDK, GRADLE_USER_HOME 기존 사용자 캐시, :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon 최종 BUILD SUCCESSFUL(1분 40초). Lint 오류 0·기존 경고 27. git diff --check 통과.
- 기기 회귀 3개 추가: 미연결 안내·복구 Compose 1개, 실제 MainActivity/Compose/root/retained ViewModel/Retrofit/MockWebServer의 신청·확정 POST 응답 지연 중 ActivityScenario.recreate 2개. CountDownLatch로 POST 도착·서버 상태 변경→회전→응답 허용 순서를 강제하고 재조회/중복 POST 없음·원래 성공 응답 반영·불확실 잠금 없음 확인. 새로운 회전 보호를 제거한 기기 변이 검사는 실행하지 않았다.
- 기기 초기 통합: 화면 8개 통과·신청 회전 통과, 관리자 회전은 조회 완료 전 버튼을 찾는 테스트 대기 누락으로 실패(9/10 통과, 153.7초). 계측 테스트만 목록 로딩 대기를 추가해 재빌드한 뒤 회전 클래스 **2/2 통과·실패/건너뜀 0(43.907초)**. 현재 운영 코드의 화면 8개·회전 2개를 검증했으며 최종 통합 10개 재실행은 생략했다. 마지막 :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon BUILD SUCCESSFUL(1분 11초), lint 오류 0·기존 경고 27. 스크립트는 기본 10개, WithServer 11개를 기대하도록 갱신했다. 결과는 android/app/build/b8a-device-results/instrumentation.txt(초기 실패 이력)와 rotation-final.txt(최종 회전 성공)다.
- 기기 환경: 새 build 전용 AVD는 offline으로 종료했다. 이전 B8a 인수의 가상 B8aVerification AVD를 -read-only -no-snapshot -no-window, 18082/18083으로 재사용했다. 부팅 후 콘솔 화면으로 이번 연결의 USB 디버깅만 허용했으며 항상 허용 옵션은 선택하지 않았다. 사용자 Pixel_10·사용자 DB·디자인·B8b 변경은 보존했다. 이번 에뮬레이터를 adb emu kill로 종료하고 임시 ADB TCP 연결도 해제했다. 종료 후 18082/18083 LISTEN 없음 확인.
- 자체 diff 재검토 완료. 수정 후 실서버 opt-in(2개)·두 계정 Spring 기기 인수·실제 Kakao·물리 기기·backend/웹 전체·독립 재리뷰·최종 원격 CI는 미실행. 클라이언트 수정 범위라 서버/웹 전체는 반복하지 않았다. API/DB/환경/의존성/CI·서버 규칙 변경 없음. Android 빌드 영향, 수동 배포 없음; 롤백은 이번 수정 되돌림과 이전 앱 복원이다.
- 공유 인계: 기존 디자인 작업 폴더와 B8b 미커밋 8파일을 보존하기 위해 독립 Android 작업을 관리 도구의 C:\Users\jjang\.codex\worktrees\b8a-review\NurseFlow로 분리하고 기존 feat/android-off-requests 브랜치를 이어갔다(새 작업 브랜치 없음). 수정은 이 폴더의 미커밋 10파일이며 같은 경로에서 재리뷰/커밋/게시를 이어간다. B8b는 C:\Users\jjang\.codex\worktrees\b8b-review\NurseFlow에 그대로 남아 있고 서로의 현황 문서 기록은 후속 통합 때 모두 보존한다. 다음 단계는 수정 diff 재리뷰와 커밋/푸시 후 PR #62 반영·최종 head CI 확인이다.


## B8a PR #62 일반 PR 전환 확인 (2026-11-25, Codex)

- 기기 인수 보완 7파일을 `3b8cd345b547666aa5bc3a7d8193a95a1a10f485`에 커밋·푸시했다. PR #62 본문을 실제 Android **8/8 성공·실패/건너뜀 0**, 두 계정 실서버 인수·Windows 예약 포트 해결·재현 명령으로 갱신하고 **Draft를 해제했다**. GitHub 재조회로 OPEN·isDraft=false·dev 대상·MERGEABLE·REVIEW_REQUIRED/BLOCKED를 확인했다. 병합이나 승인 완료를 뜻하지 않는다.
- 연결 도구의 본문 갱신과 Ready 전환은 integration 권한 403으로 실패해 기존 인증된 `gh pr edit --body-file`·`gh pr ready`로 완료했다. 새 PR·중복 브랜치·worktree를 만들지 않았다.
- 보완 head Verify [45607890815](https://github.com/gerrmang/NurseFlow/actions/runs/45607890815)는 조회 시 IN_PROGRESS, 이전 `81dfa90` Verify [45603850392](https://github.com/gerrmang/NurseFlow/actions/runs/45603850392)는 4개 전체 성공이다. 이 게시 기록 커밋을 포함한 최종 head의 CI도 별도 확인한다. 문서 기록만 추가하므로 기기/앱 검사를 반복하지 않는다.
- B8a의 구현·자체 재검토·JVM/실제 Spring/실제 기기 화면/두 계정 인수·일반 PR 전환을 완료했다. 같은 공유 worktree·`feat/android-off-requests`·PR #62에서 인계한다. 실제 Kakao·물리 기기·운영·독립 승인은 별도 범위다. 사용자 DB/trace는 보존·커밋 제외했고 이번 검증 프로세스/포트는 정리했다. 병합·자동 병합·리뷰 요청/댓글·수동 배포 없음.

## B8a 기기 인수 완료·PR 마무리 (2026-11-25 20:44 KST, Codex)

- 사용자 지적을 반영해 미완료를 Draft로 넘기던 중단 지점에서 계속 작업했다. 같은 공유 worktree·`feat/android-off-requests`·기존 PR #62를 유지했다. **기기 Compose 7개와 두 계정 실서버 1개를 모두 실행해 완료했다.** 아래의 기기 미완료/Draft 대기 기록은 당시 이력이며 이 절이 갱신한다.
- 원인 확인: Windows TCP 예약 범위 `5517~5616` 때문에 5554/5562/5584 바인딩이 실제 권한 오류였다. 빈 18082/18083 바인딩 성공을 확인해 콘솔/ADB를 옮겼다. Windows 예약 설정·기존 Pixel_10·사용자 DB/trace는 변경하지 않았다. 같은 Android 37.2 x86_64 이미지를 쓰는 검증 전용 AVD를 무시되는 `android/app/build/b8a-avd/`에 만들었고 디버깅 인증 후 `emulator-18082`에서 앱·기기 테스트 APK를 설치했다.
- 첫 Compose 실행 **5개 성공·2개 실패**: 화면 밖 LazyColumn 항목을 바로 조회한 검사 오류였다. `performScrollToNode`로 실제 스크롤·표시를 확인하도록 고치고, 같은 패턴을 실서버 검사에서도 조회 결과 대기에 적용했다. 수정 후 Compose 단독 **7/7 성공**.
- 최종 `android/scripts/verify-off-requests.ps1 -Serial emulator-18082 -Adb <SDK 경로> -WithServer`: **OK (8 tests), 37.328초, 실패/오류/건너뜀 0**. 가상 API 화면 7개와 실제 Activity/Compose/Retrofit/Spring/H2 두 계정 1개다. 간호사 신청→Activity 재생성→로그아웃→관리자 로그인·확정→로그아웃→간호사 재로그인·확정 상태/철회 불가 및 서버 재조회 일치를 확인했다. 인증 공급자/신원은 가상 OIDC이며 실제 Kakao 인증과 구분한다.
- 관리자·간호사 확정 화면 PNG와 계측 로그를 `android/app/build/b8a-device-results/`에 수집·직접 열어 확인했다. 가상 간호사의 같은 확정 상태, 관리자 철회 버튼·본인 철회 없음, 고정 안내·달력 정렬을 확인했다. 산출물은 build에만 두고 커밋하지 않는다. 새 재현 스크립트는 adb exit 0만으로 성공 처리하지 않고 JUnit 성공 수·실패·건너뜀을 확인한다.
- JDK 21 `:app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon` **BUILD SUCCESSFUL(39초)**, 57작업 중 7개 실행·50개 UP-TO-DATE. Lint 오류 0·기존 경고 27. 앱/API/ViewModel/서버 운영 코드는 이번에 변경하지 않아 앞서 실행한 JVM **239개 성공·기존 B5a 선택 1개 건너뜀** 결과를 유지한다. `git diff --check` 통과. 계측/스크립트 수정 자체 재확인 완료, 독립 승인이라고 표현하지 않는다.
- 검증 후 이번 전용 AVD·가상 공급자·메모리 서버만 명령행/부모 관계를 확인해 종료했고 18082/18083/18085/18090/18091 listen 없음 확인. 사용자 AVD와 DB는 보존했다.
- `git fetch origin` 후 최신 dev는 여전히 `3e30752`이며 추가 통합 없음. 기존 최종 head `81dfa90`의 Verify `45603850392`는 Android/Web/Demo/Backend 4개 모두 성공을 확인했다. 이번 보완을 푸시하고 PR 본문을 완료된 인수 결과로 갱신해 Draft를 해제하며, 보완 head의 CI도 확인한다.
- 같은 브랜치에 검사 2파일·재현 스크립트·Android README·검증/PR 초안·이 기록만 반영한다. 사용자 DB/trace 보존·제외. API/DB/환경/의존성 변경·수동 배포 없음, Android 검사/문서 영향. 롤백은 이번 인수 보완 커밋 revert(기존 앱/API 동작 유지). 실제 Kakao·물리 기기·운영·독립 승인은 별도 범위로 남긴다. 병합·자동 병합·리뷰 요청/댓글·수동 배포 없음.

## B8a Draft PR #62 게시 확인 (2026-11-25, Codex)

- 사용자 요청으로 B8a 변경 17파일을 `d889ddb84ea29f4ee1e87855a152e2a3984c00cb`에 커밋하고 `origin/feat/android-off-requests`로 푸시했다. 로컬/원격 head 일치, 최신 dev `3e30752` 포함을 확인했다. 사용자 `nurseflow.mv.db`·`nurseflow.trace.db`는 미추적 상태로 보존·커밋 제외했다.
- [PR #62](https://github.com/gerrmang/NurseFlow/pull/62)를 `dev` 대상 **Draft**로 게시했다. GitHub 연결 도구의 생성은 integration 권한 403(`Resource not accessible by integration`)으로 실패해 기존 인증된 `gh pr create --body-file android/B8a-pr-draft.md`를 사용했다. 조회 결과 OPEN·isDraft=true·MERGEABLE·REVIEW_REQUIRED/BLOCKED다. 독립 승인이나 즉시 병합 가능 상태를 뜻하지 않는다.
- 전체 변경과 앞선 P3 수정의 자체 재검토에서 추가 조치 사항 없음. 게시 직전 로컬 Android 전체 단위/실제 Spring/APK/Lint 성공 결과는 아래 기록을 따른다. 기기 Compose 7개·실제 Activity/서버 두 계정 1개는 실행하지 못해 Draft 사유로 PR 본문에 명시했다. 실제 Kakao·물리 기기·운영·독립 리뷰는 미검증이다.
- 초기 원격 확인에서 Vercel·Vercel Preview Comments는 SUCCESS였으나 `gh run list --branch feat/android-off-requests`는 빈 목록을 반환했다. Verify 통과로 표현하지 않는다. 게시 기록 커밋을 포함한 최종 head의 CI는 푸시 뒤 별도 확인한다.
- 검증용 가상 공급자·메모리 서버는 명령행/부모 관계를 확인해 이번 프로세스만 종료했고 18090/18091 listen 없음 확인. 제품 코드는 최종 검증 뒤 변경하지 않았다. 이번 기록은 문서만 추가해 로컬 검사를 반복하지 않고 diff 공백을 확인한다.
- 공유 인계: 같은 공유 worktree·`feat/android-off-requests`와 PR #62. 다음은 연결 가능한 기기의 화면/실서버 인수, 독립 수정 재리뷰·GitHub 승인 및 원격 CI 확인이다. API/DB/환경/의존성 추가 변경·수동 배포 없음, 롤백은 기능 커밋 revert와 이전 Android 빌드 복원. 병합·자동 병합·리뷰 요청/댓글·수동 배포 없음.

## B8a PR 게시 전 최종 재검토 (2026-11-25, Codex)

- 사용자 요청으로 같은 `feat/android-off-requests`의 B8a 17파일과 P3 수정 부분을 자체 재검토했다. 서버 A12·세션 identity/refresh·권한·상태 전이·마감/한도·ETag·응답 유실/취소/늦은 응답·고정 안내/요일 정렬·기기/실서버 검사·fixture를 다시 대조했다. **추가로 조치 가능한 발견 사항 없음**. 구현자 자체 재검토이며 독립 에이전트/승인 리뷰·무결함 보장을 뜻하지 않는다. 검토 직전 17파일 내용의 경로순 SHA256 결합 해시 `2DB7D083993EC1157C09915B9D57BC2FD29D77859BA03B30FA7DBB6F26E0CDCE`(이 기록 추가 전 공유 현황 포함), HEAD `3e30752`.
- `git fetch origin` 후 최신 `origin/dev`는 여전히 `3e30752043ed78bf8ab8b74e123bab3e28f2ea3d`로 현재 HEAD와 같고 추가 통합 없음. 같은 head 브랜치의 기존 PR 0건 확인. B8a만 커밋/푸시하고 `dev` 대상 Draft PR을 게시한다. Draft 사유는 기기 Compose 7개와 실제 Activity/서버 두 계정 1개가 ADB 연결 실패로 실행되지 않은 점이다.
- 최종 게시 전 검증은 B8a opt-in을 켜 새 메모리 Spring/OIDC에서 실행했다. JDK 21 Android 전체 단위/앱·기기 테스트 APK/Lint **BUILD SUCCESSFUL(1분 25초)**, 80작업 중 14개 실행·66개 UP-TO-DATE. XML 240개 중 **239개 통과·기존 B5a 선택 1개 건너뜀·실패/오류 0**, B8a 실제 Spring 인수 1개 통과. Lint 오류 0·기존 경고 27, `git diff --check`·fixture `node --check` 성공. 서버/OpenAPI/웹/CI는 dev와 동일하며 실제 Kakao·물리 기기·운영·독립 리뷰는 미검증이다.
- 사용자 DB/trace를 보존하고 커밋에서 제외한다. API/DB/환경/의존성 변경·수동 배포 없음, Android 빌드 영향. 롤백은 B8a 커밋 revert와 이전 Android 빌드 복원이다. 같은 공유 worktree·브랜치에 인계하며 게시 결과와 원격 head/CI를 뒤이어 확인한다. 병합·자동 병합·리뷰 요청/댓글·수동 배포는 하지 않는다.

## B8a P3 화면 리뷰 수정 (2026-11-25, Codex)

- 사용자 수정 요청에 따라 같은 공유 worktree·`feat/android-off-requests`·HEAD `3e30752`의 기존 미커밋 B8a 변경에서 P3 2건을 수정했다. 사용자 DB/trace와 기존 변경을 보존했다.
- 오류/결과 미확인/재조회·계속 버튼을 목록 바깥의 고정 상태 영역으로 옮겼다. 긴 안내는 해당 영역 안에서 스크롤하고 접근성 live region으로 알린다. 권한 상실 시 병동 복귀 버튼, 월 한도/마감일 필드 옆 오류도 제공한다. 달력 요일은 날짜와 같은 전체 폭·7열 weight Row와 중앙 정렬을 사용한다.
- Compose 회귀 3개(하단 정책 입력 오류 표시, 응답 유실 복구 버튼의 가시성, 날짜/요일 7열 중심 일치)를 추가해 가상 API 화면 검사는 총 7개다. 실서버 기기 1개와 함께 컴파일 성공, 현재 `adb devices -l`에 연결 기기가 없어 실제 실행은 미완료다. 이전 ADB 문제를 해결했다고 주장하지 않는다.
- JDK 21 Android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon` **BUILD SUCCESSFUL(1분 29초)**, 80작업 중 19개 실행·61개 UP-TO-DATE. XML 240개 중 **238개 통과·실서버 opt-in 2개 건너뜀·실패/오류 0**. 초기 구현의 실서버 1개 성공 기록과 구분한다. Lint 오류 0·기존 경고 27, `git diff --check` 통과.
- 수정된 화면/검사 코드를 자체 재확인해 고정 영역과 목록의 분리, 입력 검증·복구 버튼 활성 조건 유지, 요일/날짜 동일 열 폭을 확인했다. 독립 수정 재리뷰·기기 시각 검증은 미완료다. ViewModel/API/서버 규칙은 바꾸지 않았으며 실제 Spring/Kakao·backend/웹 전체 검사는 UI 수정 범위라 재실행하지 않았다. backend/OpenAPI/웹/CI diff 없음.
- 검증 문서·PR 초안을 갱신했다. API/DB/환경/의존성 변경·수동 배포 없음, Android 빌드 영향. 롤백은 P3 화면 수정을 되돌리고 이전 Android 빌드를 복원한다. 같은 브랜치의 미커밋 상태로 인계하며 다음은 기기 Compose 7개·두 계정 실서버 1개 실행과 수정 diff 독립 재리뷰다. 커밋·푸시·PR 게시·병합·배포 없음.

## B8a 코드 리뷰 (2026-11-25 20:12 KST, Codex)

- 같은 공유 worktree·`feat/android-off-requests`, HEAD `3e30752043ed78bf8ab8b74e123bab3e28f2ea3d`의 tracked/미추적 B8a 17파일을 검토했다. 리뷰 직전 파일 경로순 SHA256 목록을 합친 해시 `A45659DAE4268A2579105ABCE2A8936E308B33A69EFF08BEBA0C97870ABC82AD`(이번 리뷰 기록 추가 전 `docs/work-status.md` 포함). 사용자 DB/trace와 build 산출물은 제외했다. 구현한 Codex의 자체 재검토이며 독립 에이전트·GitHub 승인 리뷰가 아니다.
- 서버 A12 Service/Repository·OpenAPI, 공통 Bearer/refresh 세션 identity, 세션 ViewModelStore·병동 진입, API·ViewModel·Compose·단위/기기/실서버 검사 및 fixture를 대조했다. 조치 가능한 P1/P2는 발견하지 못했고 **P3 화면 문제 2건**을 확인했다.
- P3(런타임/화면), `OffRequestScreen.kt:59`: 오류·결과 미확인·복구 버튼을 LazyColumn 첫 item에만 표시한다. 하단 정책에서 한도 32처럼 잘못된 입력을 저장하거나 목록 하단에서 네트워크 오류를 만나면 스크롤 위치를 유지한 채 안내가 화면 밖에 생긴다. 결과 미확인으로 편집이 잠겨도 원인·복구 버튼이 보이지 않는다. 고정 Snackbar/상태 영역 또는 오류·미확인 발생 시 안내 위치로 스크롤하고 필드 오류는 입력 옆에 표시할 필요가 있다.
- P3(런타임/화면), `OffRequestScreen.kt:81`: 요일은 공백으로 간격을 준 단일 Text인 반면 날짜는 fillMaxWidth의 7개 weight 열이다. 두 행이 같은 열 폭·중앙 정렬을 공유하지 않아 보통 휴대폰 너비에서 요일이 날짜 위에 맞지 않으며 날짜의 요일을 오인할 수 있다. 요일도 같은 7열 Row/weight와 중앙 정렬을 사용해야 한다.
- 검증: JDK 21 Android `:app:testDebugUnitTest --tests '*OffRequestApiTest' --tests '*OffRequestViewModelTest' --offline --no-daemon` **BUILD SUCCESSFUL(17초)**, API 11·ViewModel 31개, 총 42개 통과·실패/오류/건너뜀 0. `git diff --check` 통과. 화면 2건은 배치·스크롤 코드에서 확인한 정적 리뷰 결과이며 기기 재현/시각 측정을 수행했다고 주장하지 않는다. 기존 ADB 연결 실패로 남은 기기 두 계정 시연은 미완료이고 실제 Spring·전체 Android·Lint·Kakao는 이번 리뷰에서 재실행하지 않았다.
- 제품·검사 코드는 수정하지 않았으며 이번 리뷰 기록만 갱신했다. 같은 브랜치의 미커밋 변경·사용자 DB/trace를 유지한다. 다음은 P3 2건 수정과 연결 가능한 기기의 화면/실서버 인수 및 수정 diff 재리뷰다. 커밋·푸시·PR 게시·병합·배포 없음.

## B8a Android 희망 오프 구현·검증 (2026-11-25, Codex)

- 사용자 승인 계획에 따라 같은 공유 worktree에서 `git fetch origin` 후 최신 `origin/dev` `3e30752`에서 `feat/android-off-requests`를 만들었다. A12·B3a를 소비하며 B8b·B7 미병합 코드를 쌓지 않았다. 기존 B8b 브랜치와 사용자 `nurseflow.mv.db`·`nurseflow.trace.db`를 보존했다.
- 병동 카드→희망 오프: 간호사 본인 신청·월별 상태/이력·마감 전 미확정 철회, 관리자 취합·간호사/상태/예외 필터·대리 입력·확정·반려·확정분 철회, 월 한도/마감일 정책과 ETag를 구현했다. 최신 결정 15와 A12에 따라 마감 뒤 예외 요청은 제출 가능하지만 본인 철회 및 확정 전 편성 반영은 불가다. 관리자 대리 입력 후보는 서버 편성 대상 조회를 사용한다. 사유·공식 결재·주말 최적화·초안 편집/공개는 범위 밖이다.
- 세션 identity에 HTTP 클라이언트를 묶고 소속/역할을 활성화·전송 전·조회 후 확인한다. 늦은 응답 폐기, 쓰기와 후속 조회 직렬화, 응답 유실/깨진 성공 응답의 자동 재전송 금지, 결과 미확인 시 월 이동/쓰기 차단과 최신 조회·사용자 확인 복구, 취소·소속 상실 시 데이터 폐기를 구현했다. 서버가 권한·한도·상태 전이·배치 금지를 최종 판정한다.
- JDK 21·기존 사용자 Gradle 캐시: Android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`, B8a 실서버 opt-in 포함 **BUILD SUCCESSFUL(1분 34초)**. XML 240개 중 **239개 통과·기존 B5a opt-in 1개 건너뜀·실패/오류 0**. 신규 API 11·ViewModel 31·실제 Spring 1 모두 통과. Lint 오류 0·기존 경고 27. `git diff --check`, fixture `node --check` 성공.
- 실제 Spring 인수: 변경 없는 dev backend의 `bootJar` 성공(4작업 UP-TO-DATE), 새 메모리 H2·가상 OIDC/병원으로 실제 Retrofit/ViewModel/API를 검증했다. 관리자·간호사 초대 가입→신청/한도/중복→철회/재신청→확정/확정분 철회→대리 입력, 정책 저장/412, 간호사 관리 경로 거부·비소속 거부, 마감 후 예외/본인 철회 거부/관리자 반려, 소속 상실 후 데이터 폐기를 확인했다. 실제 Kakao 인증·기기 화면 시연과 구분한다.
- **기기 인수 미완료**: Compose 4개와 실서버 두 계정 Activity 검사 1개는 컴파일 성공. 기존 Pixel_10을 읽기 전용으로 여러 설정에서 시작해 부팅 로그를 확인했지만 ADB 기기 미검출/연결 거부가 반복돼 APK 설치·계측 실행·화면 캡처를 못 했다. 최초 신규 Compose 검사 선택자의 미지원 `last()` 컴파일 오류는 semantics 대화상자 선택자로 수정했다. 실제 Kakao·물리 기기·다중 관리자 물리 경합·운영·독립 리뷰·GitHub PR/CI는 미검증이다.
- 앱/서버 최초 검사는 사용자 Gradle 캐시 잠금 권한으로 시작하지 못해 로컬 검증 권한으로 실행했다. 검증 후 PID·부모 관계·명령행을 확인해 이번 에뮬레이터/가상 공급자/메모리 서버만 종료했고 18090/18091·이번 에뮬레이터 포트의 listen 없음 확인. 사용자 AVD 데이터 초기화·파일 DB 사용 없음. backend·OpenAPI·웹·CI diff는 없고 전체 서버/웹 회귀는 변경 범위가 아니어서 재실행하지 않았다.
- API/DB/환경/의존성·서버 규칙 변경 및 수동 배포 없음. Android 빌드 영향. 롤백은 B8a 변경 revert와 이전 Android 빌드 복원. 마감 API에 병동 시간대/서버 현재시각이 없어 표시 시간대를 명시한다. 미저장 입력은 재조회/이탈 시 초기화될 수 있고 실시간 구독은 없다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-off-requests`, HEAD `3e30752`의 미커밋 B8a 코드·검사·문서를 유지한다. 상세 `android/B8a-verification.md`, PR 초안 `android/B8a-pr-draft.md`. 사용자 DB/trace 커밋 제외. 다음은 연결 가능한 기기에서 Compose 4개·두 계정 실서버 1개 실행과 전체 diff 독립 리뷰다. 이번에 커밋·푸시·PR 게시·병합·배포하지 않았다.

## B7a PR #60 A13 최신 dev 통합 (2026-11-25, Codex)

- 사용자 요청으로 기존 `feat/android-schedule-settings`의 `b2d9028`에 최신 `origin/dev` `3e30752`(A13 #58)을 통합했다. 충돌 없음. Android 동작 코드는 변경하지 않았고 backend·OpenAPI·웹 생성 타입은 dev와 동일하다. 기존 사용자 DB·trace 보존, 새 브랜치/worktree 없음.
- JDK 21 backend `test bootJar --offline --no-daemon` BUILD SUCCESSFUL(1분 58초), XML **304개 통과·opt-in 5개 건너뜀·실패/오류 0**(총 309개). Android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon` BUILD SUCCESSFUL(29초). Android 단위·빌드 작업은 UP-TO-DATE이며 기존 226개 통과·2개 선택 건너뜀 결과를 이번에 새로 실행했다고 기록하지 않는다.
- A13 통합 JAR·가상 OIDC·새 메모리 H2 `b7a_a13_merge_e2e`에서 B7a 실제 서버 API/ViewModel 인수 **1개 통과·실패/건너뜀 0**(22초). Android 기기·실제 Kakao·운영 검사는 반복하지 않았다. 공휴일 자동 갱신은 검증 서버에서 껐다. `git diff --cached --check` 통과.
- B7a의 추가 API/DB/환경·서버 규칙 변경 없음(A13 계약/스키마 통합). Android 빌드 영향·수동 배포 없음. 롤백은 이 통합 커밋을 첫 부모 기준 revert. A13 관리 화면은 추가하지 않는다.
- 공유 인계: 같은 브랜치에 통합·검증·PR 초안을 커밋·푸시해 기존 PR #60을 갱신한 뒤 같은 공유 worktree에서 B7b PR #59를 처리한다. 기존 구현·기기 검증·독립 리뷰 미완료 제한은 유지한다. 병합·자동 병합·수동 배포 없음. 최종 원격 head·최신 dev 포함·CI는 푸시 뒤 확인한다.

## B7a PR #60 게시 확인 (2026-11-25, Codex)

- B7a 변경 20파일만 `b328894885a414f3be80849a4399d413f8089655`에 커밋·`origin/feat/android-schedule-settings`로 푸시하고 로컬·원격 head 일치를 확인했다. [PR #60](https://github.com/gerrmang/NurseFlow/pull/60)을 `dev` 대상 일반 PR로 게시했다. OPEN·isDraft=false·MERGEABLE, REVIEW_REQUIRED/BLOCKED 확인. 독립 승인 완료나 즉시 병합 가능 상태를 뜻하지 않는다.
- GitHub 연결 도구의 생성은 integration 권한 403으로 거부되어 기존 인증된 gh의 `--body-file android/B7a-pr-draft.md`로 게시했다. 최신 원격 dev는 여전히 `db5e412`이며 기능 head에 포함된다. DB·trace는 커밋하지 않았다.
- 구현 head의 [Verify 45523035406](https://github.com/gerrmang/NurseFlow/actions/runs/45523035406)은 조회 시 Backend tests·Web checks·Demo seed checks 진행 중, Android debug build 대기 중이었다. Vercel·Preview Comments는 성공이었다. 이 기록을 Verify 전체 통과로 해석하지 않는다. 최종 게시 기록 커밋의 원격 head·CI도 푸시 후 조회한다.
- 직전 로컬 검증 이후 제품 코드 변경 없음. 이번에는 게시 기록만 변경해 앱·서버·기기 검사를 반복하지 않았고 `git diff --check`로 확인한다. API/DB/환경 추가 변경 없음, 수동 배포 없음. 같은 공유 worktree·B7a 브랜치에서 이 기록을 커밋·푸시한 뒤 독립 리뷰와 최종 CI 확인으로 인계한다. PR 병합·자동 병합·리뷰 요청/댓글·수동 배포는 하지 않았다.

## B7a PR 게시 준비 (2026-11-25, Codex)

- 사용자 요청으로 같은 `feat/android-schedule-settings`의 B7a 변경만 커밋·푸시하고 `dev` 대상 일반 PR로 게시한다. `git fetch origin` 후 최신 dev는 `db5e412`로 시작 기준과 같으며 같은 head의 기존 PR은 0건이다. 추가 dev 통합은 필요하지 않다.
- 직전 최종 검증은 Android 단위 226개 통과·opt-in 2개 건너뜀·실패/오류 0, 앱/테스트 APK 빌드·Lint 성공(오류 0·경고 27), 실제 서버 JVM 1개 및 기기 6개 시나리오 통과 확인이다. 이후 제품 코드 변경이 없어 검사를 반복하지 않고 `git diff --check`와 게시 대상 diff를 확인한다. 검증 이력·중간 검사 실패 정정은 아래 구현 절과 `android/B7a-verification.md`에 보존한다.
- 사용자 DB·trace는 스테이징·커밋 제외한다. 독립 리뷰·최종 원격 CI는 게시 뒤 확인하며 병합·자동 병합·수동 배포는 하지 않는다. 게시 결과·원격 head·CI 조회 상태는 후속 기록으로 남긴다.

## B7a Android 근무 설정 구현·인수 검증 (2026-11-25, Codex)

- 사용자 승인 계획으로 같은 공유 worktree에서 최신 `origin/dev` `db5e4120d04f9cd259bdb4ad509088ebf1598cd6` 기반 `feat/android-schedule-settings`를 만들었다. 시작 tracked 변경 없음, B7b 브랜치와 사용자 DB·trace 보존. B7b 웹 변경을 쌓지 않았고 backend·OpenAPI·web diff 0줄이다.
- Android 관리자 네 메뉴를 실제 API로 연결했다. D/E/N 시간·휴게·인정·월 환산·변경 영향 preview·비활성화/재활성화, 월 초안 명시 생성·팀 수요 전체 교체·선임 칸/미충원, 승인 VY/반차 CRUD, 간호사 근무 형태 조회·저장을 제공한다. 내 병동·팀 관리에서 진입한다. OTHER는 조회·비활성화만 지원하고 기존 수요의 필요 자격을 복원하지 않는다. 기존 배치가 있으면 교체를 막는다.
- 정책/초안 ETag·쓰기 직렬화, 부재 전후 초안 revision 일치, exact input preview, 응답 유실·5xx·파싱/무본문 실패 후 재전송 차단·최신 결과 확인, 세션/권한 경계·늦은 응답 무시를 구현했다. 승인 부재는 병동 시간대를 사용하고 미변경 endpoint의 원본 초·나노초·오프셋·DST 회차를 보존한다. 사유는 수집하지 않으며 필수 규칙의 최종 판정은 서버에 둔다.
- 최종 JDK 21 Android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`: BUILD SUCCESSFUL (1분 36초), XML **226개 통과·opt-in 2개 건너뜀·실패/오류 0**(총 228개). Lint 오류 0·경고 27개. B7a 기본 JVM 회귀 30개가 포함된다. `git diff --check` 통과.
- 실제 Spring·새 메모리 H2 `b7a_fictional_e2e`·가상 OIDC로 B7a JVM API/ViewModel 인수 **1개 통과·실패/건너뜀 0**. 유형 CRUD/preview·Night 자정 경과·정책/초안 412·팀 2칸/선임 1칸·부재 CRUD/나노초·근무 형태 저장/재조회·새 ViewModel/재진입·간호사 변경 403을 확인했다. 간호사 유형 조회를 403으로 기대했던 검사는 현행 계약의 200 조회·403 변경으로 정정한 뒤 통과했다.
- read-only Pixel_10의 가상 API Compose 검사 **4개 통과**, 실제 서버 화면 네 메뉴 저장·재조회 검사 **1개 통과**, 실제 MainActivity 재생성/뒤로가기/재진입 검사 **1개 통과**를 확인했다. 묶음 6개 실행은 5개 통과·재진입 검사 1개 실패였고, HTTP 요청 도착 뒤 화면 렌더링까지 기다리도록 검사 대기 조건을 정정한 뒤 해당 1개 단독 실행 OK를 확인했다. 회전 시 입력 유지·추가 조회 없음, 재진입 시 조회·이전 폼 폐기를 검증했다. 실제 서버 UI 최초 로컬 프록시 연결 실패와 재생성 검사 최초 NetworkOnMainThreadException/기기용 토큰 저장소 컴파일 수정도 검증 문서에 기록했다. 제품 코드는 이 마지막 검사 대기 조건 수정에서 변경하지 않았다. 최종 검사 APK·Lint는 BUILD SUCCESSFUL(39초)이다.
- 구현자 자체 diff·세션/권한·쓰기 유실·ETag·시각 보존 경로 점검 완료. 독립 코드 리뷰·GitHub PR/CI·실제 Kakao·물리 기기·운영·다중 관리자 실제 기기 경합·프로세스 강제 종료·전체 기기 회귀는 미검증이다. backend·웹 전체 회귀는 해당 코드 미변경으로 생략했다.
- API·DB·환경·의존성·서버 규칙 변경 없음, Android 앱 빌드 영향·배포 없음. 롤백은 B7a Android 변경 revert와 이전 APK 복원. 검증/제한/재현 절차는 `android/B7a-verification.md`, PR 초안은 `android/B7a-pr-draft.md`, 사용법은 `android/README.md`에 남겼다.
- 이번 Java·가상 OIDC 공급자는 명령행·포트를 확인한 뒤 종료했고 임시 에뮬레이터도 종료했다. 18090/18091 포트 해제·ADB 기기 목록 비어 있음 확인. 사용자 DB·trace·원본 AVD는 보존했다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`·`feat/android-schedule-settings`, HEAD `db5e412`의 미커밋 구현·검사·문서를 유지한다. 새 worktree 없음. 커밋·푸시·PR 게시·병합·배포 없음. 사용자 DB·trace는 보존·커밋 제외한다. 다음 단계는 이 변경 전체의 독립 리뷰와 이후 커밋·PR/최종 원격 CI 확인이다.

## A14 팀 간 지원 구현 (2026-11-24, Claude)

- 브랜치 `feat/cross-team-support`(최신 dev `548552b`에서 분기). A12 PR #56과 `RosterService`·`schema.sql`·`openapi.yaml`·`ApiTestBase`를 함께 바꾸므로, 규칙 PR은 하나씩 병합한다는 순서에 따라 **#56 병합 뒤 최신 dev에 맞춰 PR을 연다.** 그 전에는 브랜치에만 둔다.
- 사용자 결정(2026-11-24): 허용 범위는 간호사·배치 팀·시작~종료일(포함, 최대 366일)·근무 유형(비우면 전체). 철회는 기록을 남겨 REVOKED로 바꾸고, 그 간호사가 허용 기간·근무 유형 안에서 배치 팀 칸에 배치된 DRAFT를 revision 증가·재검증 표시한다. 공개본은 바꾸지 않는다. `requirements.md`·`feature-breakdown.md`에 반영했다.
- 서버: `cross_team_support_allowances` 테이블, `GET/POST /wards/{wardId}/support-allowances`, `POST .../{allowanceId}/revoke`(관리자 전용, 병동 잠금, 감사 기록). 같은 범위의 활성 기록이 있으면 새로 만들지 않고 200으로 그 기록을 반환한다. 철회는 멱등이다. 허용 생성은 범위만 넓히므로 초안 revision을 바꾸지 않는다. 병동 정책 revision도 올리지 않는다(다른 정책 화면 ETag 영향 회피).
- 규칙: `CrossTeamSupportRule`이 후보·수동·자동(고정 포함)·확정 4경로에서 팀 조건을 판단한다. 기존 `TEAM_MISMATCH` 진단은 `CROSS_TEAM_NOT_ALLOWED`로 대체했다. 허용은 팀 조건만 완화하고 시간 중복·휴가·휴식·연속근무 등은 그대로 적용한다. 날짜는 칸의 근무일(Night는 시작일). 지원 배치는 칸의 팀, 즉 배치 팀 충원에 계산된다. 자동배치는 칸마다 같은 팀 간호사를 먼저 시도하고 아무도 못 맡을 때만 지원을 쓴다(날짜순 탐욕 배치라 앞선 지원 배치가 뒤 칸의 같은 팀 배치 여지를 줄일 수는 있다).
- 테스트: 규칙 단위(경계일·근무 유형·다른 팀·철회), 통합(허용 전 거부→허용 안 통과·범위 밖 날짜/근무 거부→동시간 중복 거부→배치 팀 충원→철회 시 초안 표시·고정 자동배치 FAILED·확정 거부→재허용 후 공개→공개 뒤 철회는 공개본 유지, 자동배치 같은 팀 우선·허용 범위 밖 미충원), HTTP(권한·검증·중복 생성·타 병동·멱등 철회·감사).
- 검증: backend 전체 `LC_ALL=C.UTF-8 ./gradlew test` 284개 중 279개 통과·opt-in 5개 건너뜀·실패 0(리뷰 반영 후 재실행). 로캘 미지정 첫 실행은 병원 CSV 한글 파일명 테스트 3개만 `InvalidPathException`으로 실패했고 UTF-8 로캘에서 통과해 환경 문제로 판단했다. web `npm run api:check`·`npm run typecheck` 통과. Android·웹 화면은 B 트랙 범위라 변경·실행하지 않았다.
- 코드 리뷰(자체, medium): 같은 범위 중복 생성, 철회 시 근무 유형 무시한 과다 표시, created_at 문자열 정렬을 반영했다. 비활성 간호사 대상 허용 생성 허용과 팀 삭제 시 허용 기록 유지는 판정에 영향이 없어(배치 시 규칙이 차단, 비활성 팀 칸 없음) 이번 범위에서 제외했다.
- API/DB: 신규 테이블·엔드포인트·오류 코드 `INVALID_SUPPORT_ALLOWANCE`·`SUPPORT_ALLOWANCE_NOT_FOUND`. 진단 코드 이름 변경(`TEAM_MISMATCH`→`CROSS_TEAM_NOT_ALLOWED`)은 클라이언트에서 참조하지 않음을 검색으로 확인했다. 롤백은 이 브랜치 커밋 revert이며 새 테이블은 남아도 무해하다.
- dev 반영(2026-11-24): A12 #56 병합 후 `origin/dev` `74886b6`을 병합했다. 충돌은 `RosterService`(검증 맥락에 확정 희망 오프·미확정 희망 오프·지원 허용을 함께 둠), `ApiTestBase` 정리 목록, `openapi.yaml`(경로·스키마·진단 설명을 양쪽 모두 유지), `schema.gen.ts`(재생성), 이 파일이다. 자동배치 후보 순서는 같은 팀 → 미확정 희망 오프 없음 → 배치 횟수 순으로 정했다.
- 병합 후 검증: backend 전체 `LC_ALL=C.UTF-8 ./gradlew test` 294개 중 289개 통과·5개 건너뜀·실패 0. web `api:check`·`typecheck`·`lint` 통과, vitest 394개 통과·3개 건너뜀.
- 병합 후 코드 리뷰(자체, medium) 반영: `shiftTypeIds`의 null 원소를 500 대신 400 `INVALID_REQUEST`로 거부, 철회 시 이미 그 팀 소속이 된 간호사의 배치는 재검증 표시에서 제외, 자동배치 주석을 칸 단위 같은 팀 우선이라는 실제 동작에 맞춤. 반영 후 backend 전체 294개 중 289개 통과·5개 건너뜀·실패 0.
- PR #57 리뷰 P2 반영(hyen11, `discussion_r4680974733`): 생성에 `Idempotency-Key`를 필수로 받고 결과를 재생한다. 재생은 기록을 현재 상태로 다시 읽으므로 생성→철회→같은 키 재시도에서 활성 허용이 생기지 않고 REVOKED가 보인다. 다른 요청에 같은 키는 `IDEMPOTENCY_KEY_REUSED`, 새 키는 의도적 재허용이다. HTTP 회귀 추가. backend 전체 294개 중 289개 통과·5개 건너뜀·실패 0, web `api:check`·`typecheck` 통과.
- 공유 인계: 같은 브랜치로 dev 대상 PR을 연다. A13 PR도 공유 파일을 바꾸므로 공유 파일을 건드리는 PR은 하나씩 병합하고 뒤 PR은 최신 dev에 맞춘 뒤 다시 검증한다.

## A12 희망 오프 구현 (2026-11-24, Claude)

- `origin/dev` `b101bb4`(A11 병합)에서 분기했다. 실행 환경의 푸시 제한 때문에 브랜치 이름은 `claude/project-thread-owc2gs`이며, 문서상 이름은 `feat/off-requests`다. 결정 15는 사용자 답변으로 정했다. 한도는 병동별 기본 4일이고 간호사 본인 제출에만 적용한다. 마감은 전달 15일이며 병동별로 1~28일 중에서 바꿀 수 있다. 마감 뒤에는 간호사 예외 요청을 관리자가 접수한다. 마감 시각은 사용자가 정하지 않아 병동 현지 자정으로 가정했다. 주말 목표는 A19로 미뤘다.
- 신규 `offrequest` 패키지와 `off_requests` 테이블을 추가했다. 테이블에는 상태(SUBMITTED·CONFIRMED·REJECTED·WITHDRAWN), 입력 주체 `source`, 제출·판단 시각, `after_deadline`을 저장한다. `wards`에는 `max_off_requests_per_month`(기본 4)와 `off_request_deadline_day`(기본 15) 컬럼을 추가했다. API는 `GET/POST /wards/{wardId}/off-requests`, `POST .../{requestId}/confirm|reject|withdraw`, `GET/PUT .../off-requests/policy`다. 정책 변경은 병동 정책 ETag를 쓴다.
- 확정분은 공통 validator의 `CONFIRMED_OFF_REQUEST` HARD 규칙으로 후보·수동·자동(고정 포함)·확정 4경로에서 막는다. 판정은 근무 시작일 기준이라, 전날 시작한 Night가 그날 끝나는 경우는 허용한다. 확정과 확정분 철회는 해당 월 초안의 revision을 올리고 재검증 대상으로 표시한다. 공개본은 유지한다. 미확정 요청은 선호로 처리한다. 자동배치는 요청한 간호사를 그날 후순위로 두고, 지키지 못한 날짜는 `OFF_REQUEST_NOT_HONORED` SOFT 진단으로 남긴다.
- 확정 희망 오프는 A11의 OF·SOF가 아니므로 연속근무를 끊지 않는다. 대리 입력에는 Idempotency-Key가 없다. 대신 같은 날짜의 중복 제출은 409로 막힌다.
- 검증 결과는 다음과 같다. 백엔드 `./gradlew check`는 288개 중 283개 통과, 5개 건너뜀, 실패 0이었다(`LANG=C.UTF-8`). 기본 로케일에서는 기존 `HospitalDatasetSearchHttpTest` 3개가 한글 파일명 경로 오류로 실패하는데, 이번 변경과 무관한 환경 문제다. 웹은 `api:gen`·`api:check`·`typecheck`·`lint`·`build`가 통과했고, vitest는 384개 통과·3개 건너뜀이었다. `git diff --check`도 통과했다. Android·실기기·실제 Kakao·GitHub CI는 실행하지 않았다.
- 게시 전에 독립 코드 리뷰를 돌렸다(Claude `code-review`, Codex는 이 환경에서 쓸 수 없음). 지적된 사항은 모두 같은 브랜치에서 고쳤다.
  - 중요: 간호사의 마감 뒤 미확정 예외 요청이 자동배치 선호에 반영되던 문제를 고쳤다.
  - 경미: 간호사의 지난 날짜 신청을 400으로 거부한다. 월 한도는 간호사 본인 입력만 세고 관리자 대리 입력은 세지 않는다. 미반영 진단은 생성기가 직접 배정한 칸만 대상으로 하고 날짜별 건수를 표시한다. 기능 현황 문서도 갱신했다.
  - 수정 후 다시 검증했다.
- 롤백하려면 코드·OpenAPI·웹 생성 타입을 함께 되돌린다. 신규 테이블과 컬럼은 이전 코드에서 쓰지 않는다. 다음 단계는 PR 검토이며, B8a·B8b 화면이 이 계약을 사용한다.

## B6 PR #51 무본문 응답 리뷰 수정·dev 확인 (2026-11-24, Codex)

- 같은 공유 worktree·`chore/demo-seed-script`, 시작 HEAD와 원격 PR head `82544f2` 일치, tracked 변경 없음 확인. 미추적 `nurseflow.mv.db`·`nurseflow.trace.db`를 보존했다. `git fetch origin` 후 최신 `origin/dev`는 `00f086c`이며 이미 HEAD의 조상이므로 추가 통합 커밋 없이 최신 dev 반영 상태다. backend·OpenAPI·웹 생성 타입은 dev와 동일하다.
- 미해결 P3 `discussion_r4669526377`: Android 모드 조회를 `Response<AdminEligibility>`로 받아 성공 응답의 null body를 모드 미확인으로 처리한다. 비성공 응답은 HttpException을 기존 apiCall로 전달해 401·403·WARD_NOT_FOUND·USER_NOT_FOUND 처리를 보존한다. SerializationException·통신 오류 분리 및 CancellationException 재전파도 유지한다. 모든 eligibility 호출·대역을 검색해 단위/기기 fixture를 함께 맞췄다. 서버 API 계약·규칙은 바꾸지 않았다.
- 실제 Retrofit+MockWebServer로 204·205·빈 200 첫 진입, 204 새로고침·205 저장 후 재조회·정상 모드 복구를 추가했다. 무본문 회귀 2개는 수정 전 모두 실패(exit 1), 수정 후 통과했다. 실제 HTTP 401·403·404의 목록 폐기 및 503의 목록 유지도 추가 검증했다. ViewModel 33개 통과, 기존 취소·인증 회귀 포함. 수정 diff 자체 재검토 완료, 독립 재리뷰는 별도다.
- JDK 21, `GRADLE_USER_HOME=C:\Users\jjang\.gradle`, Android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`: BUILD SUCCESSFUL(1분 15초). XML 197개 중 196개 통과·실서버 opt-in 1개 건너뜀·실패/오류 0. `git diff --check` 통과. 웹·backend·시드 통합·기기 UI·실제 Kakao는 Android 응답 처리 수정 범위라 로컬 재실행하지 않았으며 최종 PR CI는 푸시 후 확인한다.
- API/DB/환경·의존성 변경 없음. Android 빌드 영향, 수동 배포 없음. 롤백은 이번 수정 커밋 revert와 이전 Android 빌드 복원. GitHub dev rules 조회에서 승인 1건 필요·필수 check 목록은 빈 배열·strict=true를 확인했다. 현재 PR은 MERGEABLE이나 REVIEW_REQUIRED/BLOCKED이며 독립 승인 완료나 즉시 병합 가능으로 표현하지 않는다.
- 공유 인계: 같은 브랜치에 이번 수정·회귀·PR 초안·이 기록만 커밋·푸시하고 PR 설명과 해당 스레드를 갱신한다. 최종 head CI·미해결 대화·dev 포함 여부를 재확인한다. 사용자 DB 제외, dev/main 직접 푸시·PR 병합·자동 병합 설정 없음.
- 게시 확인: 수정 커밋 `6909ea0` 푸시·원격 head 일치 확인. 연결 도구의 PR 수정/스레드 해결은 integration 권한 403으로 실패해 기존 인증된 gh로 PR 설명 반영과 해당 스레드 해결을 완료했다. 최신 dev 재조회도 `00f086c` 포함 상태이며 GitHub는 MERGEABLE·REVIEW_REQUIRED/BLOCKED다. 구현 head Verify `45332824330`이 시작됐으며 이 게시 기록 커밋을 포함한 최종 CI는 별도 확인한다. 다음 공유 인계는 동일 브랜치의 수정 diff 독립 재리뷰·필요 승인 1건이며 병합은 수행하지 않았다.

## B6 PR #51 게시·최신 dev 충돌 정리 (2026-11-23, Codex)

- JSON 파싱 수정 `c40d090`을 기존 PR 브랜치에 푸시했고 원격 SHA 일치를 확인했다. 연결 GitHub 도구는 PR 설명 수정·스레드 해결에 403을 반환해 기존 gh 인증으로 PR 초안 파일을 본문에 반영하고 P3 스레드를 해결 표시했다. 재조회에서 미해결 스레드 0건을 확인했으며 독립 승인과 구분한다.
- 푸시 후 PR이 최신 dev와 CONFLICTING/DIRTY여서 Verify가 시작되지 않음을 확인했다. `origin/dev` `00f086c`(A9 #53·A10 #52)를 같은 B6 브랜치로 통합하고 `docs/work-status.md` 한 곳의 충돌만 양쪽 기록을 모두 보존해 해결했다. backend·OpenAPI·웹 생성 타입은 `origin/dev`와 동일하며 B6 추가 서버 수정 없음.
- 통합 검증: JDK 21 backend `test bootJar --offline --no-daemon` BUILD SUCCESSFUL(1분 22초), XML 272개 중 267개 통과·opt-in 5개 건너뜀·실패/오류 0. web `npm run api:check`·`npm run typecheck` exit 0. 새 JAR의 `node scripts/demo/local.mjs --jar backend/build/libs/nurseflow-backend-0.1.0-SNAPSHOT.jar --verify` exit 0(새 메모리 H2·가상 2계정·초대 가입·권한 분리·기본 모드 편성·응답 유실 복구·시드 재조회). Node API/runtime 검사 7개 통과·실패/건너뜀 0. 검증용 자식 Java·가상 공급자 종료, 사용자 DB 보존. Android 코드는 통합 중 변경되지 않아 직전 전체 193개 통과 결과를 유지하며 중복 실행하지 않았다.
- 공유 인계: 같은 worktree·`chore/demo-seed-script`에서 이 통합·검증 기록을 커밋·푸시하고 PR 설명을 최신 파일로 갱신한다. `nurseflow.mv.db`·`nurseflow.trace.db`는 미추적 상태 그대로다. 최종 head Verify와 독립 재리뷰는 별도 확인 대상이며 dev/main 직접 푸시·PR 병합·자동 병합·수동 배포 없음.

## B6 PR #51 JSON 파싱 실패 리뷰 수정 (2026-11-23, Codex)

- 같은 공유 worktree·`chore/demo-seed-script`, 시작 HEAD와 원격 PR head `e27e919` 일치를 확인했다. 새 미해결 P3 `discussion_r4660963516` 대응으로 Android `/roster-eligibility` 조회에서만 `SerializationException`을 미확인(null)으로 처리한다. 성공한 관리 목록과 편집을 유지하며 기존 ‘시연 모드 확인 불가’ 표시를 사용한다. 취소·401·403·WARD_NOT_FOUND·USER_NOT_FOUND는 기존 처리를 유지한다.
- 실제 `ApiFactory.staffAdminApi`의 Retrofit/kotlinx.serialization 변환기와 MockWebServer로 HTTP 200의 `{}`·깨진 JSON·잘못된 타입을 검증했다. 다른 관리 API는 가상 응답이며 실서버 통합으로 표현하지 않는다. 첫 진입·시연 모드에서 실패한 새로고침·저장 후 재조회·정상 일반 모드 복구를 검사했고 취소 회귀를 추가했다. 기존 인증·권한·병동 없음 회귀도 유지했다. 같은 패턴을 검색해 웹 모드 조회는 디코딩 실패를 이미 분리함을 확인했다.
- 대상 ViewModel 30개 통과. 파싱 예외 처리만 제거한 변이에서 새 JSON 회귀 2개 모두 목록/편집 유지 단언에 실패(exit 1)했으며 finally로 수정 코드를 복원했다. 변이 실행 중 Kotlin daemon 초기 종료 안내가 있었지만 컴파일이 이어져 검사가 실제 실행됐다.
- 최종 JDK 21 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`: BUILD SUCCESSFUL(1분 19초). 단위 XML 194개 중 193개 통과·실서버 opt-in 1개 건너뜀·실패/오류 0. Lint 오류 0·경고 27·Information 4. 최초 샌드박스 실행은 사용자 Gradle 캐시 미설정·배포본 다운로드 연결 권한 오류로 시작하지 못했고, 기존 사용자 캐시와 JDK 21을 지정해 권한 확장 후 검증했다.
- 웹·backend 전체 회귀·시드 실서버 통합·기기 UI·실제 Kakao 검사는 Android 디코딩 예외 처리 수정 범위라 재실행하지 않았다. API/DB/환경·서버 규칙 변경 없음. Android 빌드에 영향, 수동 배포 없음. 롤백은 이번 커밋 revert와 이전 Android 빌드 복원이다. 수정 diff 자체 검토 완료, 독립 재리뷰·최종 head CI는 별도다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`·`chore/demo-seed-script`에 수정·회귀 검사·PR 초안·이 현황만 커밋하고 PR #51에 반영한다. 사용자 `nurseflow.mv.db`·`nurseflow.trace.db`는 보존·커밋 제외한다. PR 병합·자동 병합·수동 배포 없음. 게시 결과는 후속 기록으로 구분한다.

## B6 PR #51 리뷰 수정 (2026-11-22, Codex)

- 같은 공유 worktree·`chore/demo-seed-script`, HEAD `51008be`와 PR 원격 head 일치를 확인하고 리뷰 `6003072000`의 P3·P5를 반영했다. Android·웹에서 `/roster-eligibility`만 일시 실패하면 모드 값을 미확인(null)으로 두고 ‘시연 모드 확인 불가’를 표시한다. 성공한 팀·간호사·구성원·경력 정책 목록과 편집은 유지하며 이전 모드를 최신 값이나 일반 모드로 표시하지 않는다. 인증·권한·취소 오류의 기존 처리는 유지하고 목록 자체 조회 실패는 계속 편집을 차단한다.
- 첫 진입 503·새로고침 실패·저장 후 재조회·모드 복구·권한 상실·인증 오류 회귀를 Android ViewModel과 웹 Store/화면에서 검증했다. 같은 패턴 검색으로 두 클라이언트의 목록/모드 병합 경로와 배너 소비를 함께 수정했다. P5는 실행 안내를 현재 Android·웹 관리자 화면 지원과 로컬 가상 계정의 실제 Kakao 로그인 불가 범위로 나눴다.
- JDK 21 Android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`: BUILD SUCCESSFUL(1분 45초), XML 191개 중 190개 통과·실서버 opt-in 1개 건너뜀·실패/오류 0. Lint 오류 0·경고 27. 최초 샌드박스 실행은 Gradle 캐시 잠금 접근 거부로 시작하지 못해 권한 확장 후 완료했다.
- Windows web `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 성공. 394개 통과·실서버 opt-in 3개 건너뜀·실패 0. 최초 화면 회귀 2개는 빈 팀 이름의 기존 필수 검증을 누락해 실패했고, 이름 입력 후 활성화를 검사하도록 테스트만 정정해 전체 재실행 통과했다. Node `--test scripts/demo/api.test.mjs scripts/demo/runtime.test.mjs` 7개 통과·실패/건너뜀 0, `node --check scripts/demo/local.mjs`·`git diff --check` 통과.
- 기기 UI·새 실서버/시드 통합·실제 Kakao·backend 전체 회귀는 클라이언트 장애 처리와 실행 문구 변경 범위라 재실행하지 않았다. API/DB/환경·서버 근무 규칙 변경 없음. Android·웹 빌드 영향, 수동 배포 없음. 롤백은 이번 수정 커밋 revert와 이전 클라이언트 빌드 복원이다. 수정 diff 자체 검토 완료, 독립 재리뷰·최종 head CI는 별도다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `chore/demo-seed-script`에 이번 수정·회귀 검사·PR 초안·이 현황을 커밋·푸시하고 PR #51 설명을 최신 검증으로 갱신한다. 사용자 `nurseflow.mv.db`·`nurseflow.trace.db`는 보존하며 커밋에서 제외한다. 리뷰 스레드 해결·독립 승인·PR 병합·자동 병합·수동 배포 없음. 다음 단계는 수정 diff 재리뷰와 최종 head CI 확인이다.
- 게시 상태: 로컬 수정 커밋 생성 후 `git push origin chore/demo-seed-script`는 자동 승인 검토가 외부 GitHub 게시에 대한 명시적 사용자 승인 부족으로 거부했다. 우회하지 않았으며 원격 푸시·PR 설명 갱신은 미실행이다. 검증된 변경은 같은 브랜치의 로컬 커밋으로 인계하고 사용자 푸시 승인 후 게시한다. 위 문장의 푸시·PR 갱신은 계획이며 완료 기록이 아니다.
- 후속 게시: 사용자 ‘ㅇㅇ푸시’ 승인으로 수정 커밋 `798f3ce`를 `origin/chore/demo-seed-script`에 푸시했다(원격 `51008be → 798f3ce`). PR 설명 갱신은 별도 외부 변경에 대한 승인 부족으로 자동 검토가 거부해 실행하지 않았으며 우회하지 않았다. PR 초안 파일에는 최신 결과가 포함돼 있다. 제품 코드는 검증 뒤 변경하지 않아 검사를 반복하지 않는다. 게시 기록만 같은 브랜치에 추가하고, 독립 재리뷰·최종 head CI 결과 확인으로 인계한다.

## B6 실제 Kakao 웹 로그인 1계정 검증 (2026-11-20, Codex)

- 사용자의 실제 로그인 검증 요청에 따라 현재 코드 HEAD `4d8590e`의 웹과 backend bootJar를 실행했다. backend는 dev `139b609` 이후 변경 없음. WSL JDK 21·새 `jdbc:h2:mem:b6_real_kakao`·8080, Windows Vite·localhost:5173, 실제 Kakao HTTPS issuer/JWKS/authorize/token 주소로 구성했다. 사용자가 기존에 준비한 Kakao 환경 파일은 source로만 읽고 키·Secret·토큰·인가 코드·쿠키·계정 식별자를 출력·기록하지 않았다. 사용자 파일 DB를 사용하지 않았다.
- ChatGPT in-app browser에서 **실제 Kakao 계정 1개의 로그인→앱 내 병동 복귀→새로고침 세션 복원→앱 로그아웃→실제 Kakao 인가 코드 재로그인→내 병동 복귀**를 직접 확인했다. 기존 Kakao 로그인 상태로 SSO 왕복했으며 비밀번호 자동 입력·가상 공급자·토큰 주입은 사용하지 않았다. 재로그인 중 `/auth/kakao/callback` 뒤 앱 복귀를 관찰했다. 서버 프로세스의 전용 메모리 DB·실제 issuer를 함께 확인했다.
- 같은 실제 계정으로 병원 검색(10개 후보)→검증용 가상 병동 생성→관리자 등록 안내→팀/구성원 화면의 보호 API 조회·서버 일반 모드 표시·새 팀 입력 표시 확인. 증거 화면에는 계정 식별자가 없으며 무시되는 `backend/build/b6-real-kakao-proof.jpg`에만 보존했다. 검증 후 앱 로그아웃·로그인 화면을 확인하고 이번 Java/Vite 서버만 종료해 메모리 DB를 폐기했다. 기존 사용자 DB·로컬 설정·다른 프로세스는 보존했다.
- 한계: **두 실제 계정 간 전환·초대 가입, 이번 Android SDK 실로그인·물리 기기·운영은 미실행**. 기존 가상 2계정 검증·Android 계측 5개와 이번 실제 웹 1계정 결과를 혼합하지 않는다. 상세 환경·관찰·미검증은 `scripts/demo/real-kakao-verification.md` 참조. Windows 네이티브 Whale 제어는 창 소유자 오류가 반복돼 브라우저 전용 도구로 전환했다.
- 제품·API·DB·배포 설정 파일 변경 없음. 검증 결과 문서·README·계정 기록·PR 설명·이 현황만 같은 `chore/demo-seed-script`에 커밋·푸시하고 PR #51에 실제 결과를 반영한다. 독립 승인·PR 병합·자동 병합·수동 배포는 하지 않는다. 아래 ‘실제 로그인 미검증’ 절들은 당시 이력이며 현재 웹 1계정 검증은 이 절을 따른다.

## B6 인수 범위 마무리·일반 PR 전환 (2026-11-20, Codex)

- 사용자의 마무리 요청에 따라 B6 원문 완료 증명인 **새 H2 DB에서 공개 API로 시드 실행 후 조회**를 기준으로 인수 상태를 정리했다. 구현·복구·종료 상태, 가상 관리자/간호사 2계정의 운용·가입·권한, Android/웹 관리자 화면 시연 모드 표시는 구현·검증했다. 실제 Kakao 로그인을 B6의 필수 선행으로 확대해 Draft에 남겼던 판정을 정정한다. 실제 Kakao 로그인 자체는 실행하지 않았으며 B3/B12의 실제 인증·교차 클라이언트 시연 미검증 항목으로 명시한다. 실제 계정이 준비됐거나 로그인 검증을 통과했다고 주장하지 않는다.
- `scripts/demo/accounts.md`에 B6 자동화의 가상 2계정과 메모리 수명·폐기, 실제 시연의 관리자/간호사 2역할·소유자 직접 관리·로그인·검증 전 계정 이용 가능 확인·비밀값 비출력 절차를 구분했다. README·PR 설명에서 Draft 대기 문구를 제거하고 이 인수 기준과 미검증 항목을 함께 명시한다. 공통 기획 문서는 변경하지 않는다.
- 최신 구현/게시 기록 head `f2b4c16`의 [Verify 44738992638](https://github.com/gerrmang/NurseFlow/actions/runs/44738992638)는 completed/success이고 Demo seed checks·Backend tests·Android debug build·Web checks 모두 success 확인. PR #51은 OPEN·MERGEABLE이며 조회 시 리뷰·리뷰 스레드 0건이다. 이는 독립 승인이나 브랜치 보호 설정 확인을 뜻하지 않는다.
- 이번 변경은 문서 마무리뿐이며 앱/서버/테스트 코드는 바꾸지 않았다. 기존 로컬 11개·Android 단위 188개/opt-in 1개·기기 5개·웹 387개/opt-in 3개 결과와 원격 CI를 근거로 하고 테스트를 불필요하게 반복하지 않는다. git diff 공백 검사를 확인하고 문서 커밋을 같은 `chore/demo-seed-script`에 푸시한다. 최종 문서 head의 CI도 별도로 확인한다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `chore/demo-seed-script`, [PR #51](https://github.com/gerrmang/NurseFlow/pull/51)을 Ready for review로 전환하고 PR 설명을 최신 파일로 갱신한다. 사용자 DB·trace·로컬 문서 보존 브랜치는 유지한다. 별도 병합 승인·독립 리뷰는 수행하지 않았으며 dev/main 병합·자동 병합·수동 배포는 하지 않는다. 아래 Draft 절들은 당시 게시 이력이다.

## B6 Draft PR #51 게시 (2026-11-20, Codex)

- B6 보완을 `856ce5f9b9a081e1ddc6bf6239286c770fbc72fd`에 커밋·푸시하고 [PR #51](https://github.com/gerrmang/NurseFlow/pull/51)을 dev 대상 Draft로 게시했다. 연결 도구의 PR 생성 권한 403 후 기존 인증된 gh의 `--body-file scripts/demo/pr-draft.md`로 게시했다. 사용자 요청의 구현·검증·PR 준비 작업을 수행했으며 실제 Kakao 계정 운용 선택·로그인 검증 대기 사유를 PR 첫 부분에 명시했다.
- Android/웹 표시·가상 2계정 API·기기·단위·CLI 검증 결과는 아래 보완 절을 따른다. 게시 후 제품 코드는 바꾸지 않았다. 사용자 DB·trace·로컬 문서 보존 브랜치를 포함하지 않았고 검증용 Java/JWKS·이번 에뮬레이터는 종료했다. 앱/서버 테스트를 게시 기록 때문에 다시 실행하지 않으며 문서 diff 공백만 검사한다.
- 현재 계정 운용 선택 질문은 사용자 답변 대기다. 실제 Kakao 두 계정 로그인 없이 전체 인수를 통과했다고 주장하지 않고 Draft를 유지한다. 최종 head의 GitHub CI·독립 리뷰는 별도 확인하며 병합·자동 병합·수동 배포는 하지 않는다. 같은 공유 worktree·`chore/demo-seed-script`에서 PR 리뷰 대응으로 인계한다.

## B6 Android·웹 시연 모드 표시와 2계정 검증 보완 (2026-11-20, Codex)

- 사용자 요청으로 남은 표시·계정 검증을 같은 `chore/demo-seed-script`에서 보완했다. Android·웹의 관리자 팀/구성원 화면이 기존 A7 `/roster-eligibility`를 조회해 가상 데이터 포함 시연 모드·일반 모드를 표시한다. 조회 중/실패/오래된 상태는 일반 모드로 추정하지 않으며 갱신 실패 중 편집을 차단한다. 권한 상실 시 기존 세션/병동 소유 상태 처리로 데이터를 폐기한다. 서버/계약/권한을 변경하지 않았다. 사용자가 남은 B6 작업 전체를 요청해 이번 PR에 Android·웹의 작은 표시 보완을 함께 포함한다.
- 가상 관리자·간호사 **2개 신원**으로 가입 전 404→초대 수락·본인 입사 연월→관리자 API 403 및 기본 모드의 가입 간호사 편성·관리자 간호사 기록 제외를 실제 Spring+새 메모리 H2로 검증했다. 기존 A/B팀 시드 병동과 별도 가입 검증 병동을 사용한다. `scripts/demo/accounts.md`에 자동 계정 수·메모리 수명·폐기·비출력 정책과 실제 Kakao 2계정 준비안을 작성했다. 실제 Kakao 운용 방식 선택을 사용자에게 요청했으며 답변 전 결정 21을 임의로 해결 처리하지 않는다.
- 검증: Node 테스트 11개 통과·실패/건너뜀 0(42초), 새 `local.mjs --verify` exit 0로 기존 시드 복구/모드/시간 보존과 새 2계정 가입 검증 성공. CI에 Demo seed checks를 추가해 같은 검사를 PR마다 실행하도록 구성했다. 실제 CI 성공 여부는 게시 후 별도 확인한다.
- Android JDK 21 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon` BUILD SUCCESSFUL. 단위 XML 189개 중 188개 통과·실서버 opt-in 1개 건너뜀·실패/오류 0. 신규 상태 회귀 2개, API·Compose 배너 및 기기 fixture 보완. 읽기 전용 headless Pixel_10 Android 17에서 두 StaffAdmin 기기 클래스를 지정한 최종 `connectedDebugAndroidTest` **5/5 통과·실패/건너뜀 0, BUILD SUCCESSFUL(52초)**. 앞선 PowerShell 인수 분리 오류와 초기 프로세스 종료(0개 시작) 후 정확한 러너 직접 계측 4개 및 최종 Gradle 5개로 확인했다. 기존 AVD 데이터는 초기화하지 않았다.
- 웹 api:check·lint·typecheck·전체 test·build 성공. 387개 통과·실서버 opt-in 3개 건너뜀·실패 0. 화면의 켜짐→꺼짐 재조회·조회 실패 시 일반 모드 오표시 차단, 비정상 demoMode 응답 거부 회귀 포함. git diff --check 통과. 실제 Kakao·물리 기기·운영·macOS/Linux·전체 화면 크기 및 독립 리뷰는 미검증이다. 서버 미변경이므로 로컬 backend 전체 회귀는 생략하고 PR CI에서 별도로 확인한다.
- API·DB·배포 환경/의존성 계약 변경 없음. Android·웹 관리 화면과 CI 작업에 영향, 수동 배포 없음. 롤백은 이 보완 커밋 revert와 이전 클라이언트 빌드 복원이다. 현재 코드/가상 계정 검증 완료와 실제 Kakao 2계정 시연 완료를 구분한다. 실제 계정 운용·로그인 확인 전에는 B6 전체 인수 완료로 표시하지 않는다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `chore/demo-seed-script`에 코드·검사·사용법·계정 기록·PR 초안·이 현황을 함께 커밋·푸시하고 dev 대상 Draft PR로 검토 가능하게 게시한다. 기존 DB·trace와 로컬 문서 보존 브랜치는 유지한다. 최종 CI·계정 운용 방식 확인 및 수정 diff 독립 리뷰를 남기며 병합·자동 병합·배포는 하지 않는다.

## B6 P3 종료 코드 수정·인수 상태 정정 (2026-11-20, Codex)

- 사용자 수정 요청으로 같은 `chore/demo-seed-script`에서 일반 실행의 마지막 시드 검증 결과를 `process.exitCode`에 반영했다. 실패 후 q/EOF는 1, r로 복구·검증까지 성공하면 0, 최초 성공 뒤 재실패하면 1이다. 오류를 알린 뒤 메모리 DB를 유지하는 재시도 흐름은 보존했다. 같은 오류 처리 패턴을 검색했으며 최상위 catch는 이미 exitCode=1, API/runtime catch는 호출자에게 실패를 전달한다.
- `scripts/demo/local.test.mjs`에 실제 CLI+Spring+별도 메모리 H2의 검색 503 주입 회귀 4개 추가. JDK 21, `NURSEFLOW_B6_TEST_JAR` opt-in으로 `node --test scripts/demo/api.test.mjs scripts/demo/runtime.test.mjs scripts/demo/local.test.mjs`: **11개 통과·실패/오류/건너뜀 0**, 43초. 새 회귀 단독 실행도 4개 통과했다. 수정 전 q/EOF exit 0 재현은 아래 자체 검토 절에 있다.
- 수정 후 `node scripts/demo/local.mjs --jar backend/build/libs/nurseflow-backend-0.1.0-SNAPSHOT.jar --verify` exit 0: 기존 4종 생성 응답 유실 복구·시드 데이터 쓰기 0건·시연 모드 제외/복구·412·비소속 404·근무 설정 보존 통과. mjs 구문·`git diff --check` 통과. 검증용 Java/JWKS는 모두 종료하고 해당 포트 해제를 확인했다. 서버/클라이언트 전체 회귀·기기·실제 Kakao·운영·macOS/Linux는 스크립트 종료 상태 수정 범위라 재실행하지 않았다.
- README·PR 초안에서 Android/웹 시연 모드 표시를 임의로 범위 밖이라 적었던 표현을 제거하고 결정 9·21의 **미완료 인수 조건**으로 명시했다. 가상 관리자 1명·검증용 비소속 1명은 실제 Kakao 계정 운용 결정이 아니다. B6의 `scripts/demo/**` 경계를 유지하며 클라이언트 UI·실제 계정 시연까지 완료했다고 주장하지 않는다. 해당 구현·운용 결정 및 검증 기록 연결은 아직 남아 있다.
- API·DB·환경변수 계약·의존성 및 배포 영향 없음. 되돌리기는 이번 종료 코드 수정 revert, 서버/DB 롤백 없음. 수정 diff 자체 재검토 완료, 독립 재리뷰·GitHub CI는 미확인이다. 아래 자체 검토의 P3 미수정 판정은 당시 기록이며 이 수정으로 대응했다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `chore/demo-seed-script`에서 수정·회귀 검사·README·PR 초안·자체 검토 및 이 현황 기록을 함께 커밋·푸시한다. 원격 SHA 일치를 확인하고 다음은 수정 diff 독립 재리뷰다. 기존 DB·trace와 로컬 문서 보존 브랜치는 보존하며 PR 게시·병합·배포 없음.

## B6 문서·구현 자체 검토 (2026-11-20 16:50 KST, Codex)

- 검토 기준: 같은 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, `chore/demo-seed-script`, HEAD `977eaecc19779dfcea4df277fbe7813f201ca829`. 검토 전 상태 기록 외 tracked diff 없음(SHA-256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`, 빈 diff). AGENTS·Git 흐름·공유 인계·리뷰 절차, breakdown B6/선행/결정 9·21, 제품 범위·정책·테스트 및 소비 API와 변경 12파일을 대조했다. 현재 브랜치명·dev 시작점·소유 경로·서버 API/권한 사용·가상 데이터 분리는 기준에 맞는다. 최초 잘못된 브랜치명은 푸시 전에 정정됐다.
- **P3(런타임) 1건, 미수정:** `scripts/demo/local.mjs:73-76`의 일반 실행 catch가 실패를 출력만 하고 종료 상태에 반영하지 않는다. 저장소 수정 없이 별도 Node 프로세스의 fetch에 병원 검색 HTTP 503을 주입했다. 생성 성공 문구 없음·HTTP 503 출력인데 q 종료와 stdin EOF 각각 exit 0 재현. 일반 `run.ps1`은 이 종료 코드를 성공으로 판단한다. 실패 후 재조회/복구는 유지하되 마지막 시드 검증 결과를 종료 코드에 반영하고, 실패→종료/실패→r 복구 회귀 검사를 추가해야 한다.
- 완료 판정 정정: API 시드 구현·새 H2 조회 검증은 끝났지만 B6 전체 인수 완료로 단정하지 않는다. breakdown 결정 9와 OpenAPI는 클라이언트 시연 모드 표시를 요구하는데 현재는 터미널 표시만 있고 Android/웹 배너·설정 UI가 없다. 결정 21의 실제 Kakao 시연 계정 수/관리도 미정이다. README에서 후속으로 적은 사실만으로 해당 인수 조건이 충족되거나 범위 변경이 합의된 것은 아니다. 실제 계정·클라이언트 시연 완료와 구분한다.
- 재검증: `node --test scripts/demo/api.test.mjs scripts/demo/runtime.test.mjs` 7개 통과·실패/건너뜀 0. JDK 21 `scripts/demo/run.ps1 -Verify -Offline` bootJar BUILD SUCCESSFUL·최종 확장 통합 검증 exit 0(4종 응답 유실 복구·쓰기 0건·시연 모드 제외/복구·412·비소속 404·근무 설정 보존). 원격 dev `139b609`와 작업 브랜치 `977eaec` 일치 확인. GitHub PR·해당 브랜치 workflow run 조회 결과 각각 0건이며 CI 성공·독립 승인으로 표현하지 않는다.
- 리뷰 절차에 따라 제품 코드 수정·커밋·푸시·PR 게시·병합 없음. 이 검토 기록만 같은 작업 현황에 추가한다. 검증용 서버는 종료됐고 사용자 DB·trace·기존 로컬 문서 보존 브랜치는 보존했다. 다음 인계는 위 P3 수정과 회귀 검사, 시연 모드 UI/실제 계정 인수 범위 정리 후 재리뷰다.

## B6 공개 API 가상 데이터 시드 구현·검증 (2026-11-20, Codex)

- 같은 공유 worktree에서 최신 dev `139b609` 기반 `chore/demo-seed-script`로 작업한다. 최초 브랜치명을 breakdown에 명시된 이름으로 정정했다. 소유 범위 `scripts/demo/**`에 Node.js 시드·가상 JWKS/Java 런타임·PowerShell 한 명령 진입점·CP949 가상 병원 fixture·사용법·검사를 추가했다. backend·OpenAPI·클라이언트·breakdown은 수정하지 않았다.
- `scripts/demo/run.ps1`이 bootJar 빌드 후 루프백·별도 메모리 H2로 기존 Spring 서버를 실행한다. 가상 병원/병동, A/B팀, 팀별 단계 1~5 간호사 총 10명, 가상 경력 확인·단계 조정·선임·팀 내 순서, D/E/N 시간·휴게·인정·환산을 공개 API로 생성하고 `demoMode=true`와 서버 편성 판정을 재조회한다. 서버 규칙·인증 검증 우회 없음. 터미널에 가상 데이터·시연 모드 표시, 완료 후 r 재실행 쓰기 0건, 실패 뒤에도 r로 조회·복구 가능하다.
- 검증: JDK 21·Node v24.15.0, `scripts/demo/run.ps1 -Verify -Offline` bootJar BUILD SUCCESSFUL 및 초기 통합 시나리오 성공. 최종 확장 검증 `node scripts/demo/local.mjs --jar backend/build/libs/nurseflow-backend-0.1.0-SNAPSHOT.jar --verify` exit 0: 병동·팀·간호사·근무 유형 성공 응답 유실 4종의 중복 없는 복구, 팀/경력/선임/순서/근무 시간·편성 대상 전체 조회, 재실행 쓰기 0건, 오래된 ETag 412, 비소속 계정 404, 시연 모드 해제 시 10명 전원 제외·복구, 기존 Day 인정시간 변경값 덮어쓰기 거부 확인.
- `node --test scripts/demo/api.test.mjs scripts/demo/runtime.test.mjs`: 7개 통과·실패/건너뜀 0. API 외부 주소/경로 차단, 결과 유실 비재전송·오류 비밀값 비노출, HTTP 오류·리다이렉트·제한시간·비정상 JSON, 점유 포트 보존·잘못된 실행 인수 검사. 모든 mjs 구문·텍스트 공백 검사 및 `git diff --check` 통과. 최종 일반 진입점의 최초 생성→r 동일 병동 재조회→q exit 0·포트 해제, 시작 중 취소→Java 종료·포트 해제도 실제 프로세스로 별도 확인했다.
- 중간 실패: 샌드박스 Gradle 캐시 잠금 접근 거부 후 권한 확장으로 빌드 완료. 비소속 병동 응답을 403으로 기대하던 검사를 현행 서버 계약 404로 정정했다. 덮어쓰기 방지 검사에 사용한 인정시간 451분은 서버 상한 450분을 넘어 422였으며, 유효한 449분으로 정정 후 최종 통합 검증 통과. 제품 서버 코드는 바꾸지 않았다.
- 미검증/후속: 실제 Kakao·Android/웹 UI·물리 기기·운영·macOS/Linux·전체 backend/클라이언트 회귀·독립 리뷰·GitHub CI는 실행하지 않았다. 이번 범위는 가상 공급자+실제 서버의 API 데이터 준비다. Android/웹 시연 모드 배너·설정 UI, 실제 계정 수/관리(결정 21), 초안/수요/자동배치/공개·통합 E2E는 후속이다.
- API·DB 스키마·환경변수 계약·의존성 추가 변경 및 배포 없음. 설정은 전용 자식 프로세스에만 적용한다. 되돌리기는 시연 종료 후 B6 변경 제거/revert다. 검증용 Java/JWKS 서버는 종료했으며 기존 DB·trace와 로컬 문서 보존 브랜치 `codex/docs-preserve-handoff-records`의 `9a01332`는 그대로 뒀다.
- 공유 인계: `C:\Users\jjang\IdeaProjects\NurseFlow`, `chore/demo-seed-script`에서 사용자 요청으로 B6 코드·사용법·PR 초안과 이 현황 기록을 함께 커밋·푸시한다. 게시 전 staged diff·공백·대상 파일을 확인하고 푸시 후 원격 SHA를 확인한다. 기존 로컬 문서 보존 브랜치와 DB·trace는 포함하지 않는다. PR 게시·병합·배포는 하지 않으며 다음 단계는 B6 diff 리뷰 및 후속 UI 범위 분리 확인이다. 별도 worktree를 만들지 않는다.

## A11 연속근무 제한 구현 (2026-11-23, Codex)

- PR 게시 전 재리뷰에서 소수 상한이 정수로 잘려 HTTP 200이 나오는 동작을 재현했다. JSON 정수만 받고 소수와 문자열은 400으로 거부하도록 수정했다. 잘못된 입력 후 기존 ETag 유지와 백엔드 전체 check 273개 통과·5개 건너뜀·실패 0을 확인했다. 웹 계약·타입·린트·테스트 384개·빌드 통과.

- `dev` `00f086c`에서 `codex/consecutive-work-rule`로 분기했다. 관리자 전용 병동 정책 조회·변경(기본 5일, 설정 1~31일)과 월 초안의 명시 OF·SOF 목록 조회·교체 API를 추가했다. 정책 변경과 휴식일 변경은 초안 revision을 갱신하며 발행된 공개본은 유지한다. 기존 공개본의 휴식일은 같은 달 수정 초안에 복사한다.
- 병동 시간대의 근무 시작일로 간호사별 근무일을 중복 없이 센다. VY·반차·미배정일은 연속근무를 끊지 않는다. 근무표가 있는 연속된 앞뒤 공개월을 포함하며 미공개월에서 데이터 범위를 끊는다. 전날 Night가 그날 끝나더라도 해당 날짜를 OF로 지정할 수 있다. 기존에 명시 휴식일이 없는 병동은 자동배치 전에 필요한 OF·SOF를 입력해야 한다. 휴식일 등록 화면과 SOF 자동 부여는 후속 범위다.
- 후보·수동 저장·자동배치(고정 포함)·확정에서 `CONSECUTIVE_WORK_LIMIT`과 휴식일 충돌을 검사한다. 휴식일과 승인 연차가 겹치는 입력을 차단하고, 새 공개월이 앞뒤 근무 이력을 이어 붙이는 경우도 확정에서 검사한다. A12 희망 오프와 A15 SOF·전담 정책은 별도다.
- 설계 검토, 백엔드 구현·테스트, OpenAPI·웹 타입 작업을 세션별로 분담했다. 백엔드 전체 `check --offline --no-daemon` 273개 통과·5개 건너뜀·실패 0. 웹 계약·타입·린트·빌드 통과, 웹 384개 통과·3개 건너뜀·실패 0. 실제 Kakao·기기·운영 성능·GitHub CI는 이번 검증에서 실행하지 않았다.
- 인계는 같은 작업 폴더의 `codex/consecutive-work-rule`에서 이어간다. DB 변경은 병동 상한 컬럼과 근무표 휴식일 테이블 추가다. 되돌리려면 코드·API 계약을 함께 되돌린다. 공개 데이터는 변경되지 않는다.

## A9 PR #53 최신 dev 반영 (2026-11-23, Codex)

- A10 스쿼시 병합 커밋 `d072565`를 `codex/team-demand-senior`에 반영했다. 서비스·저장소·DB 스키마·통합 테스트·작업 현황의 충돌을 해결했고 후보·수동·자동·확정에서 팀·선임과 근무 형태 제한이 함께 적용된다. OpenAPI 계약을 병합하고 웹 타입을 재생성했다.
- 백엔드 전체 check 267개 통과·5개 건너뜀, 웹 384개 통과·3개 건너뜀, 실패 0. 웹 API 계약·타입·린트·빌드 및 Git 공백 검사 통과. 앱 기기와 운영 환경은 실행하지 않았다.

## A9 팀별 필요 인원과 선임 배치 구현 (2026-11-21, Codex)

- 게시 전 자체 리뷰에서 기존 OTHER 유형을 팀 수요에 등록한 뒤 D/E/N으로 전환할 때 선임 조건이 빠지는 경로를 막았다. 기존 OTHER의 팀 지정 양수 수요를 400으로 거부하는 회귀 검사와 전체 백엔드 check가 통과했다.

- `origin/dev` `139b609` 기반 `codex/team-demand-senior`에서 팀·날짜·근무 유형별 수요와 선임 필수 칸을 추가했다. 활성 팀이 있는 병동의 양수 수요에 팀 ID가 필요하다. 팀 지정 D/E/N 유형당 선임 칸 하나를 만들고, 수요 0건에는 칸을 만들지 않는다. 팀 없는 병동과 기존 팀 미지정 칸을 호환한다.
- 후보·수동·자동(고정 포함)·확정에 같은 팀과 선임 조건을 적용한다. 배치 불가 시 `TEAM_MISMATCH`·`SENIOR_REQUIRED` 진단을 반환하며, 선임 부족은 `seniorUnfilled` 및 `SENIOR_UNFILLED`로 구분한다. 팀 이동과 선임 자격 변경은 팀 배치 초안의 revision을 갱신한다. 초안이 참조하는 팀은 수요 제거 후 삭제한다. 공개본은 기존 값을 유지한다.
- H2의 `roster_slots.team_id`, `senior_required` 컬럼과 OpenAPI·웹 생성 타입을 갱신했다. B7 화면, 팀 간 지원(A14), 경력 균형 최적화(A19)는 각 기능 범위에서 다룬다. 롤백 시 코드와 OpenAPI를 함께 되돌리며 신규 컬럼은 기존 코드에서 사용하지 않는다.
- 백엔드 `check --offline --no-daemon` 264개 통과·5개 건너뜀·실패/오류 0. 웹 API 계약·타입·린트·384개 테스트·빌드 통과(선택 실서버 검사 3개 건너뜀). 기기·운영 검사는 이번 범위에서 실행하지 않았다.
- A10 `codex/work-pattern-rule`의 `d42fa43`도 #50이 반영된 같은 dev에서 재검증했다. 백엔드 265개 통과·5개 건너뜀, 웹 계약·타입·린트 통과. 임시 결합본에서 서비스·DB·테스트·문서 충돌을 해결한 결과 백엔드 267개 통과·5개 건너뜀, 웹 384개 통과·3개 건너뜀, 실패 0과 빌드 성공을 확인했다.
- 인계: 두 기능은 별도 로컬 브랜치에 있다. 한 기능을 dev에 병합한 뒤 다른 브랜치에 최신 dev를 반영할 때 팀·선임과 근무 형태 판정을 모두 유지하고 OpenAPI 웹 타입을 다시 생성한다. GitHub CI는 새 PR 커밋에서 확인한다.

## A10 근무 형태 규칙 구현 (2026-11-19, Codex)

- 2026-11-21 게시 전 자체 리뷰에서 API가 숫자를 근무 형태로 해석하지 않도록 문자열 허용 목록을 적용했다. 숫자 입력은 400이며 기존 설정을 보존한다. #50 이후 dev 기준 최종 백엔드 check에서 265개 통과·5개 건너뜀·실패 0을 확인했다.

- `origin/dev` `4ecedd1`에서 `codex/work-pattern-rule`로 분기해 일반(`GENERAL`), D/E 전담(`DAY_EVENING_ONLY`), N 전담(`NIGHT_ONLY`)을 저장한다. 기존 간호사와 신규 기록은 GENERAL 기본값이다. 관리자 전용 `GET/PUT /wards/{wardId}/staff/{nurseId}/work-pattern`을 추가했다. 기존 staff 요청·응답은 바꾸지 않아 기존 클라이언트가 설정값을 덮어쓰지 않는다. 제한 형태는 기존 OTHER 유형도 거부하며 GENERAL만 OTHER를 허용한다. Night 기간·순환·최소 횟수(A15)는 제외했다.
- `WorkPattern`은 `roster.domain.model`의 값 모델이며 같은 패키지 ShiftKind만 의존한다. 서비스는 병동 관리자 잠금 뒤 변경하고 기존 직원 조정 이력을 남긴다. 변경 시 모든 DRAFT revision·재검증 표시를 갱신한다. 같은 값은 변경하지 않고 공개본은 재확정 전까지 유지한다. 동시 편집은 기존 staff API와 같은 마지막 저장 우선이다.
- 후보·수동·고정 자동배치·자동 후보 선택·확정에 공통 validator의 `SHIFT_KIND_NOT_ALLOWED` HARD 규칙을 적용했다. 형태×근무 종류 전체 조합, 후보/수동/자동(고정 포함)/확정 통합 경로, 관리자/간호사 권한·잘못된 enum·필수값 누락·타 병동·변경/no-op·초안 무효화, 기존 H2 파일 DB의 GENERAL 기본값과 schema 2회 적용을 검사했다.
- 백엔드 `./gradlew check --offline --no-daemon`, 웹 `api:gen`·`api:check`·`typecheck`·`lint`, `git diff --check` 성공. Android·실기기·실제 Kakao·운영·GitHub CI는 미실행. 신규 의존성 없음. DB는 nurses.work_pattern 추가, OpenAPI/웹 생성타입 동기화. 롤백은 이전 코드 복원이며 추가 컬럼은 이전 코드에서 사용하지 않는다. 커밋·푸시·PR·병합 없이 같은 브랜치의 미커밋 변경으로 부모 검토에 인계한다.

## PR #50 B5a 코드 리뷰 (2026-11-19, Codex)

- head `7b9676f`의 변경 18파일과 세션·권한·쓰기 유실·정렬·null/false 직렬화 및 서버 A5/A6 경로를 읽기 전용 검토했다. 새로 확정된 P1~P5 결함은 없었다. GitHub Verify `44303733822`의 Backend tests·Android debug build·Web checks 성공을 직접 확인했다. 로컬 Android·실기기는 이 리뷰에서 재실행하지 않았다. 병합·댓글 없음. 마지막 저장 우선·긴 목록 자동스크롤 미지원은 PR에 기존 제한으로 기록돼 있다.

## B5a PR #50 공통 401 재시도 계정 혼용 회귀 수정 (2026-11-20, Codex)

- 새 P2 `discussion_r4639296654` 대응. 이전 스냅샷 수정 aefb24f가 staff 태그 없는 공통 요청에서 만든 회귀임을 확인했다. 이전 P2 스레드는 리뷰어가 해결 표시했으나 새 P2는 미해결 상태였다. 초대 발급·철회·병동 생성도 공통 BearerInterceptor에서 실제 전송 토큰의 계정·세션 세대를 RequestSessionIdentity 태그로 기록하게 했다. 기존 staff 클라이언트 생성 시 바인딩은 유지한다.
- RefreshAuthenticator는 태그가 없는 응답을 재시도하지 않고, 태그가 있으면 갱신 전후 모두 같은 계정·세션 세대인지 검사한다. 검증한 스냅샷의 토큰만 사용하므로 공통 요청도 다른 계정이나 로그아웃 후 새 로그인으로 재전송하지 않는다. 읽기 API와 모든 비재시도 API가 같은 interceptor/authenticator를 사용하는 경로를 함께 확인했다.
- 회귀 6개 추가: 초대 발급·철회·병동 생성의 갱신 성공 직후 계정 전환, 변경 전 스냅샷의 원래 토큰 유지, 동일 계정 로그아웃/재로그인 세대 차단, 태그 없는 응답의 갱신·재시도 차단. 새 검사 중 최초 5개를 수정 전 코드에서 실행해 4개가 잘못된 재전송(예상 401/실제 200)으로 실패함을 재현했다. 기존 staff 경합 검사 5개와 정상 공통 401 갱신 검사도 유지했다.
- 최종 JDK 21 android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`: BUILD SUCCESSFUL(52초). XML 187개 중 186개 통과·실서버 opt-in 1개 건너뜀·실패/오류 0. Lint 오류 0·기존 경고 28·Information 4. git diff --check 통과. 기기 UI·실서버·실제 Kakao·backend·웹 전체 검사는 공통 Android 인증 경로 수정 범위라 재실행하지 않았다.
- API/DB/환경·서버 근무 규칙 변경 없음. Android 공통 인증·401 재시도 경로에 영향, 배포 없음. 롤백은 이번 수정 커밋 revert와 이전 Android 빌드 복원이다. 자체 수정 diff·관련 경로 검토 완료, 독립 재리뷰·최종 head CI는 별도 확인한다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-staff-team`에서 코드·회귀 검사·이 현황 절만 커밋·푸시하고 원격 head 일치를 확인한다. PR 설명에 최신 검증 결과를 반영하고 사용자 요청의 구현 대응 완료로 새 리뷰 스레드를 해결 표시한다(독립 승인과 구분). 기존 dev 점검·A7 리뷰·DB 복구 미커밋 기록과 DB·trace는 제외해 보존한다. PR 병합·자동 병합 없음. 다음 단계는 수정 diff 재리뷰다.

## B5a PR #50 계정 전환 토큰 경합 수정 (2026-11-20, Codex)

- P2 `discussion_r4639136298` 수정: SessionManager가 계정 ID·세션 세대·액세스 토큰을 불변 SessionCredentials 하나로 구성해 volatile 참조로 발행한다. UI 상태에는 토큰을 넣지 않는다. 로그아웃·복원 실패 시 스냅샷을 제거하고 기존 accessToken 조회도 이 참조를 사용한다.
- 같은 패턴을 최초 BearerInterceptor와 401 RefreshAuthenticator에서 함께 수정했다. 요청의 세션 바인딩을 검증한 스냅샷의 토큰으로 헤더를 만든다. staff API 생성도 같은 스냅샷에서 세션 식별자를 얻는다. 계정 변경을 읽은 요청은 차단하고, 변경 전 스냅샷을 읽은 요청은 원래 계정 토큰만 사용한다.
- 신규 SessionCredentialRaceTest 5개: latch로 최초 요청·401 재시도의 스냅샷 읽기 직전/직후 계정 전환을 강제한 4개와 로그인·갱신·로그아웃 스냅샷 일관성 1개. 토큰을 별도로 재조회하는 변이를 임시 적용한 대상 검사에서 5개 중 2개가 새 계정 토큰 혼용으로 예상대로 실패했다. finally에서 원본을 복원한 뒤 최종 전체 검사를 다시 통과했다.
- 최종 JDK 21 android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`: BUILD SUCCESSFUL(49초). XML 181개 중 180개 통과·실서버 opt-in 1개 건너뜀·실패/오류 0. Lint 오류 0·기존 경고 28·Information 4. git diff --check 통과. 기기 UI·실서버·실제 Kakao·backend·웹 전체 검사는 Android 세션/네트워크 수정 범위라 재실행하지 않았다.
- API/DB/환경·서버 근무 규칙 변경 없음. Android의 공통 인증 요청 경로에 영향, 배포 없음. 롤백은 이번 수정 커밋 revert와 이전 Android 빌드 복원이다. 자체 수정 diff·관련 경로 재검토 완료, 독립 재리뷰·최종 head CI는 별도 확인 대상이며 리뷰 스레드를 독립 검토 완료로 처리하지 않는다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-staff-team`에서 이번 코드·회귀 검사·PR #50 확인/수정 기록만 커밋·푸시한다. 기존 dev 점검·A7 리뷰·DB 복구 미커밋 기록과 DB·trace는 제외해 보존한다. 원격 head 일치를 확인하고 PR 본문에 최신 검증 결과를 반영한다. PR 병합·자동 병합 없음. 다음 단계는 수정 diff 재리뷰다.

## B5a PR #50 추가 코멘트 확인 (2026-11-20, Codex)

- GitHub 연결 도구로 PR 대화와 리뷰 스레드를 조회했다. head `f15631c0b05afb8c83975b8b90d5e1d21930dc15`에 미해결·현재 유효한 인라인 지적 1건(`discussion_r4639136298`, P2)이 있다. 별도 리뷰 본문은 같은 지적의 요약이며 나머지 대화는 Vercel Ready 알림이다.
- 코드를 대조해 지적이 아직 남아 있음을 확인했다. ApiFactory의 BearerInterceptor는 bound.matches(session) 뒤 accessToken을 별도로 읽고, SessionManager.apply는 토큰과 SignedIn을 따로 발행한다. 계정 전환 중 이전 요청이 새 계정 토큰을 선택할 수 있는 실행 순서다. 기존 StaffAdminApiTest는 로그인 완료 뒤의 차단을 검사하며 두 읽기 사이 전환을 강제하지 않는다. 자격 보존 수정 f15631c는 이 경로를 수정하지 않았다.
- 검증은 코멘트 조회·관련 코드/테스트 정적 대조만 수행했다. 경합 실행 재현·로컬 테스트·최종 head CI는 이번 확인에서 실행/조회하지 않았다. 앱·API·DB 변경, GitHub 답글·스레드 해결·커밋·푸시·병합 없음. 같은 공유 worktree·feat/android-staff-team에서 미해결 P2 수정과 순서 강제 회귀 검사로 인계하며 기존 미커밋 기록·DB·trace를 보존한다.

## B5a PR #50 자격 문자열 보존 수정 (2026-11-20, Codex)

- P2 데이터 보존 결함 수정: 기존 자격 표시값을 변경하지 않으면 원래 배열을 그대로 전송한다. 이름·활성 상태만 수정할 때 `자격 A,B`가 두 자격으로 분리되는 것을 방지했다. 화면 입력 검증과 저장이 같은 변환 함수를 사용해 쉼표를 포함한 50개 자격도 잘못 차단하지 않는다. 자격 입력 자체를 변경하거나 새 기록을 등록할 때는 기존 쉼표 구분 방식을 유지한다.
- 회귀 테스트 5개 추가: 이름 변경·비활성화 시 원래 배열 보존, 쉼표 포함 50개 자격 검증, 자격 교체·비우기, 신규 등록. 같은 패턴 검색 결과 Android의 화면 검증·저장 두 경로를 함께 수정했으며 웹은 줄바꿈 구분을 사용한다.
- JDK 21, android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`: BUILD SUCCESSFUL. XML 총 176개 중 175개 통과·실서버 opt-in 1개 건너뜀·실패/오류 0. Lint 오류 0·경고 28·Information 4. 초기 샌드박스 실행은 Gradle 캐시 잠금 접근 거부로 시작하지 못했고, 권한 확장 후 위 검증을 완료했다. git diff --check 통과.
- 기기 UI·실서버·실제 Kakao·backend·웹 전체 회귀는 Android의 미변경 자격 보존 수정 범위라 재실행하지 않았다. 수정 diff 자체 검토 완료, 독립 재리뷰·수정 후 GitHub CI는 미확인이다. API/DB/환경·서버 근무 규칙 변경 없음. Android 빌드 영향, 배포 없음. 되돌리기는 이번 수정 revert와 이전 Android 빌드 복원이다.
- 공유 인계: 사용자 요청으로 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-staff-team`에서 이번 수정·회귀 테스트·이 현황 절만 커밋해 origin에 푸시한다. 기존 현황 문서의 별도 미커밋 변경과 DB·trace는 커밋에서 제외해 보존한다. 제품·테스트 코드는 직전 검증 이후 변경하지 않아 로컬 검사를 반복하지 않는다. 게시 전 staged diff와 공백을 검사하며 푸시 후 원격 SHA 일치를 확인한다. PR 병합·자동 병합·배포 없음. 다음 단계는 수정 diff 재리뷰와 최종 head CI 확인이다.

## B5a PR #50 게시 (2026-11-18, Codex)

- 사용자 요청으로 [PR #50](https://github.com/gerrmang/NurseFlow/pull/50)을 게시했다. GitHub 조회로 OPEN·일반 PR(`draft=false`)·대상 dev·구현 head `037552cbf9d9afca1bd28b56b679cc6f78433c92` 일치를 확인했다. 연결 도구의 생성 권한 403으로 기존 인증된 gh를 사용했다. 병합·자동 병합·배포 없음.
- PR 템플릿에 실제 backend/가상 OIDC 구분, 171개·기기 4개 성공, 미실행 항목, API/DB 변경 없음·Android 배포 영향·되돌리기·Squash and merge를 기록했다. 구현 head의 [Verify 44303614249](https://github.com/gerrmang/NurseFlow/actions/runs/44303614249)는 조회 시 queued·결과 미정이었다. 이 게시 기록 커밋 이후 최종 CI는 별도 확인한다.
- 제품·테스트 코드 변경 없이 게시 기록만 추가하므로 로컬 앱 검사를 반복하지 않는다. 같은 공유 worktree·feat/android-staff-team에서 이 PR 리뷰 대응으로 인계한다. 기존 dev 점검·A7 리뷰·DB 복구 기록과 DB·trace는 이 문서 커밋에서도 제외해 보존한다. 독립 리뷰와 기존 미검증 제한은 유지한다.


## B5a 커밋·푸시 인계 (2026-11-18, Codex)

- 사용자 요청으로 B5a Android 구현·테스트·검증 문서·PR 초안과 이 기능 현황만 커밋해 `origin/feat/android-staff-team`에 푸시한다. 기존 dev 점검·A7 리뷰·DB 복구 미커밋 기록과 로컬 DB·trace는 커밋에서 제외해 공유 작업 폴더에 보존한다.
- 직전 최종 검증은 단위/API/실서버 171개·기기 4개 통과, 앱·테스트 APK·Lint 성공(오류 0·경고 28). 제품·테스트 코드는 그 뒤 변경하지 않아 이번 게시 단계에서는 재실행하지 않는다. 게시 전 staged diff 공백·포함 파일·기존 기록 제외를 확인하고, 푸시 후 원격 branch SHA와 로컬 HEAD 일치를 확인한다.
- 같은 공유 worktree·`feat/android-staff-team`에서 후속 리뷰로 인계한다. 독립 리뷰·GitHub CI는 별도이며 PR 개설·병합·자동 병합·배포는 이번 요청에 포함하지 않는다. API/DB/환경 추가 변경 없음. 되돌리기는 B5a 커밋 revert와 이전 Android 빌드 사용이다.

## B5a Android 팀·간호사·구성원 관리 구현·검증 (2026-11-18, Codex)

- 사용자 승인 계획을 구현했다. `git fetch origin` 후 최신 dev `4ecedd1a2aa5a39a9cf93a22ee1381f4a83da52c` 기반 `feat/android-staff-team` 생성. 같은 공유 worktree 사용, 추가 worktree 없음. 기존 미커밋 dev 점검·A7 리뷰·DB 복구 기록과 DB·trace를 보존했다.
- 관리자 병동 카드·병동 생성 후 안내에서 관리 진입. 팀 추가·이름·표시 순서·삭제, 간호사 기록 등록/이름·자격·활성/팀 배치, 활성 팀원 전체 드래그·버튼 경력순 저장. 병원 입사 연월 조회와 실제 경력·직급 입력/확인 분리, 선임·단계 조정·이력, 구성원 관리자 지정/해제·접근 비활성/재활성. 팀 수는 기존 추가/삭제 API로 조절한다. 근무 형태·근무 설정·경력 정책 편집·시연 모드는 제외했다.
- A5·A6 기존 계약 소비. 세션·병동별 ViewModel, 진입·재조회·쓰기 전 관리자 소속 확인, 직렬 쓰기·서버 재조회, 유실 결과 확인 전 재전송 차단, 재조회 실패 중 편집 차단·이력 재시도, 권한 상실 데이터 폐기. API를 생성 당시 계정·세션 세대에 묶어 대기 요청과 401 갱신 뒤 계정 전환 재전송을 막았다. 명시적 null로 팀/단계 초기화, PATCH는 변경 속성만 전송. 서버의 기본 경력순을 유지하며 수동 저장 후 순서를 반영한다. 일반 편집의 마지막 저장 우선 한계는 안내한다.
- 최종 JDK 21 Android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`, NURSEFLOW_B5A_E2E=1: BUILD SUCCESSFUL. XML **171개 통과·실패/오류/건너뜀 0**(기존 144 + B5a 27). Lint 오류 0·경고 28·Information 4. 기본 실행에서는 실서버 opt-in 1개를 건너뛴다. 변경 없는 backend·웹 전체 회귀는 재실행하지 않았다.
- 실제 backend 18091·별도 메모리 DB와 루프백 가상 OIDC 18090으로 API/DB·인증 서명 검증을 수행했다. 팀·정렬·경력·선임·이력, 마지막 관리자·활성 기록 충돌·비활성 구성원 보호, 접근만 재활성화, 본인 권한 상실 시 데이터 폐기 확인. 사용자 파일 DB는 사용하지 않았다. 공급자·계정만 가상이며 실제 Kakao·운영 검증은 아니다. 재현은 android/B5a-verification.md와 android/scripts/b5a-oidc-fixture.mjs 참조.
- 최종 Pixel_10 Android 17 `:app:connectedDebugAndroidTest`에 B5a 두 클래스를 지정: **4/4 통과·실패/건너뜀 0, BUILD SUCCESSFUL(45초)**. 드래그·버튼 정렬 저장, 마지막 관리자 확인/거절 안내, 실제 MainActivity 진입·재생성 뒤 미저장 팀 입력과 정렬 보존. 직접 instrumentation도 앞서 4개 성공. 실제 물리 회전·실서버 기기 E2E와 구분한다.
- 중간 검증의 가상 API 예외 전달, 기기의 기존 프록시, 테스트 앱 초기 세션 복원과 로그인 순서를 보완했다. 기기 시스템 응답 정지로 이번 검증용 에뮬레이터를 데이터 초기화 없이 콜드 부팅했다. 기기 프록시는 변경하지 않고 테스트 프로세스에서 루프백만 직접 연결·복원했다. 장시간 메모리 서버의 시계 변경 경고 이후 실서버 HTTP 500 1건이 있었고, 새 메모리 서버에서 동일 제품 코드와 최종 코드 모두 재실행 통과했다. 내부 원인·제품 결함으로 단정하지 않는다.
- 자체 전체 diff·관련 경로 검토와 git diff --check 완료. 독립 리뷰·최종 GitHub CI·실제 Kakao·물리 기기·운영·모든 화면 크기·다중 관리자 실기기 동시 편집은 미완료. 앱/서버 계약·DB·환경변수·의존성 추가 변경 없음. Android 빌드에 영향, 배포 없음. 되돌리기는 B5a 변경 revert와 이전 Android 빌드 복원.
- 공유 인계: 같은 C:\Users\jjang\IdeaProjects\NurseFlow, feat/android-staff-team, HEAD 4ecedd1에서 구현·검증 문서와 PR 초안(android/B5a-pr-draft.md)을 미커밋으로 남긴다. 기존 미커밋 기록·DB·trace는 기능 변경과 구분해 보존한다. 커밋·푸시·PR 개설·병합·자동 병합·배포 없음. 다음 단계는 이 변경 전체의 독립 리뷰와 지적 수정, 검증 기록을 포함한 Android 단독 dev 대상 PR 게시다. 검증용 서버·공급자·이번 에뮬레이터는 종료해 인계한다.


## B9b PR #49 최신 dev B4b·B5b 통합 (2026-11-18, Codex)

- 사용자 요청으로 기존 `feat/web-published-roster`의 `2e83302`에 최신 `origin/dev` `5319ec6`(B4b PR #46, B5b PR #47 병합 포함)을 반영했다. 기존 공유 worktree에서 진행했으며 새 브랜치·worktree 없음. 충돌은 `web/src/App.tsx`와 이 문서에서 해결했다. 공개 근무표·병동 생성·팀 관리 경로를 모두 유지하고 전역 생성 경고와 세션별 화면 키도 보존했다. 자동 병합된 목록의 공개 조회·관리 링크와 두 기능 스타일을 검토했다. 루트/웹 README에 통합된 공개 조회 현황과 검증 링크를 반영했다.
- Windows web `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 exit 0. 17파일 384개 통과·실서버 opt-in 3개 기본 건너뜀·실패 0, JS gzip 106.49 kB. 기존 공개 근무표·병동 생성·팀 관리 회귀 검사를 함께 통과했다. 병합 diff 공백 검사는 커밋 생성 전에 실행한다. 변경 diff 자체 검토 완료, 독립 재리뷰·새 head의 GitHub CI는 푸시 뒤 별도 확인한다.
- 실제 Kakao·브라우저·실서버·backend·Android 전체 회귀는 웹 통합 범위라 재실행하지 않았다. API/DB/환경 추가 변경 없음. 웹 정적 빌드 영향, 수동 배포 없음. 롤백은 이번 통합 커밋을 첫 부모 기준으로 revert하고 이전 웹 빌드를 복원한다.
- 공유 인계: 기존 PR #49 브랜치에 커밋·푸시하고 같은 `feat/web-published-roster`에서 후속 리뷰를 이어간다. 기존 미커밋 A7 리뷰·DB 복구 기록 16줄과 로컬 DB·trace는 커밋에서 제외해 보존한다. dev로 PR 병합·자동 병합 없음.


## PR #49 최신 dev 반영 (2026-11-18, Codex)

- 사용자 요청으로 기존 `feat/web-published-roster`에 `origin/dev` `91484c7f2d3e3c87efc76c973d412f648dd6acdf`(A7 PR #48)을 병합했다. 새 브랜치·worktree 없이 같은 공유 작업 폴더에서 진행했다. 충돌은 이 문서뿐이며 기존 기능 기록과 dev 기록을 모두 보존했다. 서버·OpenAPI·생성 타입은 dev 내용을 그대로 반영하고 기능 코드는 변경하지 않았다. 다른 두 웹 PR의 기능은 포함하지 않았다.
- Windows web: `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 exit 0. Test Files  12 passed | 1 skipped (13); Tests  301 passed | 1 skipped (302). 실서버 opt-in 검사는 기본 건너뜀. 병합 diff의 공백 검사는 커밋 생성 전 확인한다.
- backend·Android 전체 회귀와 실제 Kakao·브라우저·운영 검사는 dev 변경을 그대로 통합하는 작업이라 로컬에서 재실행하지 않았다. 갱신 후 GitHub CI는 푸시 뒤 별도 확인한다. 독립 승인으로 표현하지 않는다.
- API/DB 변경은 dev의 A7에 포함된 내용이며 이번 충돌 해결에서 추가 변경 없음. PR 브랜치 푸시만 수행하고 dev 병합·수동 배포·자동 병합은 하지 않는다. 되돌리기는 이 dev 통합 커밋을 첫 부모 기준으로 revert한다. 기존 미커밋 A7 리뷰·DB 복구 기록과 DB·trace는 이번 커밋·푸시에서 제외해 보존한다. 공유 인계는 기존 feat/web-ward-create 작업 브랜치이며 각 PR에서 후속 리뷰를 이어간다.

## B9b PR #49 고정 오프셋 시간대 리뷰 수정 (2026-11-18, Codex)

- P3: 서버가 반환하는 `Z`, `UTC+09:00` 등을 Intl에 직접 전달해 배정 표시 중 RangeError가 발생하는 문제를 수정했다. Java 고정 오프셋(UTC/GMT/UT 접두사, 부호·시·분·초)을 초로 변환해 시작·종료 시각에 적용하고 UTC로 포맷한다. 날짜 비교와 종료일도 같은 변환을 사용한다. 지역 시간대는 기존 Intl 처리로 서머타임을 유지한다. 같은 패턴 검색 결과 웹 Intl 사용은 이 공통 함수의 두 포맷터뿐이며 날짜 상세·월 목록 모두 이 함수를 사용한다.
- 회귀 테스트 20개 추가: Z·UTC+09:00, 접두사·축약형, 음수·초 단위 오프셋, 자정·월·연 경계, 동일 날짜와 지역 서머타임. 수정 전 12개 실패(RangeError)·8개 통과, 수정 후 웹 전체 **301개 통과·실서버 opt-in 1개 건너뜀·실패 0**. `web`에서 `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 exit 0. `git diff --check` 통과.
- 브라우저·실서버·Android·backend 전체 검사는 표시 유틸리티 수정 범위라 재실행하지 않았다. 독립 재리뷰·수정 후 GitHub CI는 미실행이다. API/DB/환경 변경 없음. 웹 정적 빌드에 영향, 배포 없음. 롤백은 이번 유틸리티 수정 revert·이전 웹 빌드 복원.
- 공유 인계: 작업 도중 다른 작업이 `feat/web-ward-create`로 전환해 중단했으나 사용자가 해당 작업 중지·복귀를 지시했다. 전환 충돌 문서와 원래 local(stage 3) 문서를 저장소 밖 `C:\Users\jjang\.codex\visualizations\2026\11\18\01a1ec4d-982b-78b2-b8b5-0aa13e723517\branch-recovery`에 백업한 뒤, 같은 worktree의 `feat/web-published-roster`로 복귀하고 전환 전 미커밋 기록 16줄을 복원했다. DB·trace 보존. 사용자 요청으로 코드·회귀 테스트·이 현황 절만 커밋·푸시한다. 기존 A7 리뷰·DB 복구 기록 16줄은 커밋에서 제외해 작업 폴더에 보존한다. 앱 코드는 직전 검증 이후 변경하지 않아 로컬 검사를 반복하지 않았다. PR 병합·자동 병합·수동 배포 없음. 다음 단계는 수정 diff 재리뷰와 최종 head CI 확인이다.

## B9b PR #49 게시 (2026-11-18, Codex)

- 사용자 요청으로 B9b 구현·검증 기록 14파일을 `783bdd0b92a3198f556651fe51ebdec4114198d3`에 커밋·푸시하고 [PR #49](https://github.com/gerrmang/NurseFlow/pull/49)를 게시했다. GitHub CLI 조회로 OPEN·일반 PR(`isDraft=false`)·대상 dev·head 일치·autoMergeRequest=null 확인. GitHub 연결 도구는 생성 권한 403이어서 기존 인증된 gh로 게시했다. 병합·자동 병합 설정 없음.
- 구현 head의 [Verify 44123469552](https://github.com/gerrmang/NurseFlow/actions/runs/44123469552)는 게시 후 조회 시 Backend tests·Android debug build·Web checks 모두 IN_PROGRESS. Vercel·Vercel Preview Comments는 SUCCESS였지만 프리뷰 동작은 검증하지 않았다. 후속 문서 커밋 CI는 최종 head로 별도 확인한다.
- PR 템플릿에 로컬 281개·실서버 opt-in 1개 통과, 미실행 실제 Kakao·독립 리뷰, API/DB 변경 없음·배포 영향·롤백·Squash and merge를 명시했다. 앱 코드는 최종 로컬 검증 이후 변경하지 않아 검사를 재실행하지 않았다. 게시 단계 `git diff --cached --check` 통과.
- 같은 공유 worktree·`feat/web-published-roster`에서 리뷰 대응으로 인계한다. 사용자 DB·trace와 기존 DB 복구 기록 8줄은 커밋에서 제외해 작업 폴더에 보존했다. B4b·B5b 커밋을 섞지 않았다. 수동 배포·dev/main 직접 푸시 없음.

## B9b 웹 공개 근무표 조회 (2026-11-18, Codex)

- 사용자 승인 계획을 구현했다. 같은 공유 worktree에서 `git fetch origin` 후 최신 dev `9d4799156b13b6214e5e4f116d86b224dd5e1898` 기반 `feat/web-published-roster`로 분기했다. B4b·B5b 미병합 코드·커밋은 포함하지 않았다. 전환 시 현황 문서 충돌을 해결해 dev 내용과 기존 DB 복구 미커밋 기록 8줄을 보존했다. B5b 기록은 원래 브랜치에 남아 있다.
- 병동 카드→공개 근무표, 월 선택·이동·새로고침, 본인/병동 전체 전환, 날짜 상세·월 목록. 관리자 기본 전체 보기·간호사 기본 본인 보기, 전체 조회 권한 확인, 공개 없음·빈 배정·간호사 미연결·권한 오류 구분. 공개 응답 코드·시각·병동 시간대 사용, 야간 종료일 표시, 빈 날짜의 휴가·오프 추측 없음. 조회마다 소속 확인·늦은 응답 폐기·세션/병동 격리, 일정 메모리 보관. backend·OpenAPI·DB·Android·CI 변경 없음.
- 최종 Windows web `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 exit 0. **281개 통과·실서버 opt-in 1개 기본 건너뜀·실패 0**, JS gzip 96.92 kB. `git diff --check` 통과. 새 기본 검사 29개이며 기존 252개를 포함한다.
- 별도 가상 OIDC·실제 backend(JDK 21, 메모리 DB) opt-in **1개 별도 통과**. 공개 전·초안만 존재할 때 404, 확정 후 본인/전체·미충원, 수정 초안 중 기존 공개본 유지·재확정 후 새 배정, 비소속·빈 본인 배정·관리자 기록 미연결·no-store 확인. 초기 검증 환경의 가상 authorize/token 주소 누락 및 HTTP 어댑터 Content-Type 중복을 정정한 뒤 통과했다. 운영 앱·서버 수정은 없었다.
- 실제 브라우저에서 가상 관리자·간호사 로그인과 조회, 월 이동·날짜 상세·관리자 미연결 안내→전체 보기, 390×844 화면·가로 넘침 없음·Enter 조작 확인. 실제 Kakao·운영·물리 기기·다중 브라우저·실제 망 단절·전체 탭 순서는 미검증. Android·backend 전체 회귀는 웹 기능 단위라 미실행. 독립 리뷰·GitHub CI는 별도. 상세는 `web/B9b-verification.md`, PR 설명 초안은 `web/B9b-pr-draft.md`.
- 검증 backend·OIDC·웹 프로세스 3개 종료, 18090/18091/5174 LISTENING 없음 확인. viewport 원복·검증 탭 닫음. 사용자 DB·trace·기존 DB 복구 기록 보존. API/DB/운영 설정 변경·수동 배포 없음. 롤백은 B9b 변경 revert·이전 웹 빌드 복원.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-published-roster`에서 미커밋 구현·검증 문서를 이어간다. 커밋·푸시·PR 개설·병합 없음. 다음 단계는 diff 리뷰 후 지적 수정과 별도 dev 대상 PR 준비다.

## B4b PR #46 최신 dev B5b 통합 (2026-11-18, Codex)

- 사용자 요청으로 기존 `feat/web-ward-create`의 `72c7357`에 최신 `origin/dev` `4d4ee1d`(B5b PR #47)을 병합했다. 같은 공유 worktree에서 이어 작업했으며 새 브랜치·worktree 없음. `docs/work-status.md`, `web/README.md`, `web/src/App.tsx`, `web/src/styles.css` 충돌을 해결했다. 현황 기록·두 기능의 스타일과 문서를 보존하고 라우터에 병동 생성과 팀·구성원 관리를 모두 연결했다. 전역 `WardCreationGuard`와 관리자 역할 검사·동일 키 재시도는 유지했다. 루트 README에도 dev에서 병합된 B5b 현황을 반영했다.
- Windows web `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 exit 0. 13파일 335개 통과·실서버 opt-in 2개 기본 건너뜀·실패 0, JS gzip 104.11 kB. 기존 B4b 생성/응답/목록 왕복 경고와 B5b 관리 화면 회귀 검사를 함께 통과했다. 통합 App·목록 링크·스타일·문서를 자체 검토했고 병합 diff 공백 검사를 커밋 생성 전에 실행한다.
- 실제 Kakao·브라우저·실서버·backend·Android 전체 회귀는 웹 기능 통합 범위라 재실행하지 않았다. 독립 재리뷰·새 head의 GitHub CI는 푸시 뒤 별도 확인한다. 이번 통합의 API/DB/환경 추가 변경 없음. 웹 정적 빌드 영향, 수동 배포 없음. 롤백은 이번 병합 커밋을 첫 부모 기준으로 revert하고 이전 웹 빌드를 복원한다.
- 공유 인계: 기존 PR #46 브랜치에만 커밋·푸시하며 dev로 PR 병합·자동 병합은 하지 않는다. 기존 미커밋 A7 리뷰·DB 복구 기록 16줄과 로컬 DB·trace는 커밋에서 제외해 보존한다. 후속 리뷰도 같은 `feat/web-ward-create`에서 이어간다.


## B4b PR #46 역할 검증·README 리뷰 수정 (2026-11-18, Codex)

- 사용자 요청으로 PR 코멘트 6966701817(P3), 6966348334(P5)를 수정했다. 생성 응답에 `WARD_ADMIN`이 포함돼야 성공으로 인정하며 빈 역할·일반 구성원 역할만 있는 응답은 결과 미확인으로 처리한다. 기존 역할 값 검사도 유지한다. 검증 함수 이름을 생성 응답 전용임이 드러나게 바꿨다. 루트 README의 웹 구성과 현재 상태에 B3b 초대·B4b 병원 검색/병동 생성/설정 안내와 검증 링크를 반영했다.
- 회귀 사례 2개를 먼저 추가해 수정 전 대상 38개 중 2개 실패·36개 통과로 결함을 재현했다. 수정 후 Windows web `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 exit 0. 10파일 302개 통과·실서버 opt-in 1개 건너뜀·실패 0, JS gzip 97.53 kB. 두 응답에서 생성 성공 미전환·입력 잠금·동일 키/본문 재시도·키 1회 발급·정상 응답 복구를 확인했다.
- 같은 패턴 검색 결과 이 검증 함수는 병동 생성 flow에서만 사용한다. 변경 diff 자체 검토 완료, 독립 재리뷰와 푸시 후 CI는 별도 확인한다. 실제 Kakao·브라우저·실서버·backend·Android 전체 회귀는 웹 응답 검증과 문서 수정 범위라 재실행하지 않았다. API/DB/환경 계약 변경 없음. 웹 정적 빌드 영향, 수동 배포 없음. 롤백은 이번 수정 revert와 이전 웹 빌드 복원.
- 공유 인계: 기존 `feat/web-ward-create`, dev `91484c7` 반영 커밋 `2efa480`에서 이어 수정했다. 같은 PR 브랜치에 수정만 커밋·푸시하며 기존 미커밋 A7 리뷰·DB 복구 기록 16줄과 DB·trace는 제외해 보존한다. 새 브랜치·worktree·다른 PR 수정·dev 병합·자동 병합 없음. 다음 단계는 같은 PR의 재리뷰다.

## PR #46 최신 dev 반영 (2026-11-18, Codex)

- 사용자 요청으로 기존 `feat/web-ward-create`에 `origin/dev` `91484c7f2d3e3c87efc76c973d412f648dd6acdf`(A7 PR #48)을 병합했다. 새 브랜치·worktree 없이 같은 공유 작업 폴더에서 진행했다. 충돌은 이 문서뿐이며 기존 기능 기록과 dev 기록을 모두 보존했다. 서버·OpenAPI·생성 타입은 dev 내용을 그대로 반영하고 기능 코드는 변경하지 않았다. 다른 두 웹 PR의 기능은 포함하지 않았다.
- Windows web: `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 exit 0. Test Files  10 passed | 1 skipped (11); Tests  300 passed | 1 skipped (301). 실서버 opt-in 검사는 기본 건너뜀. 병합 diff의 공백 검사는 커밋 생성 전 확인한다.
- backend·Android 전체 회귀와 실제 Kakao·브라우저·운영 검사는 dev 변경을 그대로 통합하는 작업이라 로컬에서 재실행하지 않았다. 갱신 후 GitHub CI는 푸시 뒤 별도 확인한다. 독립 승인으로 표현하지 않는다.
- API/DB 변경은 dev의 A7에 포함된 내용이며 이번 충돌 해결에서 추가 변경 없음. PR 브랜치 푸시만 수행하고 dev 병합·수동 배포·자동 병합은 하지 않는다. 되돌리기는 이 dev 통합 커밋을 첫 부모 기준으로 revert한다. 기존 미커밋 A7 리뷰·DB 복구 기록과 DB·trace는 이번 커밋·푸시에서 제외해 보존한다. 공유 인계는 기존 feat/web-ward-create 작업 브랜치이며 각 PR에서 후속 리뷰를 이어간다.

## B4b PR #46 재시도 정보 보호 리뷰 수정 (2026-11-18, Codex)

- 사용자 요청의 P3 두 건 수정. `WardCreationGuard`를 `AppRoutes`의 라우트 바깥에 두고 세션 epoch별 flow를 구독해 요청 중·결과 미확인 상태의 `beforeunload` 경고를 목록 이동 후에도 유지한다. 성공·명시적 새 요청·계정 전환·로그아웃·컴포넌트 해제에서는 경고를 제거한다.
- 생성 성공 응답의 객체 형태, 비어 있지 않은 wardId/wardName, roles 배열·역할 값, canViewWardCalendar boolean과 선택적 hospitalName 타입을 검사한 뒤에만 pending 키를 지운다. `201 null`, `{}`·필수 필드 누락/잘못된 타입은 `INVALID_RESPONSE`로 처리해 결과 미확인 안내·입력 잠금·동일 키/본문 재시도를 유지한다. null/생략 hospitalName은 기존 계약대로 허용한다. 같은 패턴 검색 결과 생성 응답을 소비하는 운영 flow와 종료 경고 등록은 이 경로뿐이다.
- 회귀 검사 23개 추가. 수정 전 최초 대상 검사 43개 중 17개 실패·26개 통과로 목록 이동 후 경고 유실과 비정상 응답 수락을 재현했고, 수정 후 대상 43개 모두 통과했다. 이후 요청 중 목록 이동·늦은 성공·로그아웃/해제와 선택적 병원명 검사를 추가했다. 최종 Windows `web`: `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 exit 0. **300개 통과·실서버 opt-in 1개 건너뜀·실패 0**, JS gzip 97.53 kB. `git diff --check` 통과.
- jsdom에서 취소 가능한 beforeunload 이벤트로 차단 여부를 검사했다. 실제 브라우저 종료 대화상자·Kakao·실서버·Android·backend 전체 회귀는 웹 상태/응답 검증 수정 범위라 재실행하지 않았다. 수정 diff 자체 검토 완료, 독립 재리뷰·수정 후 GitHub CI는 미확인이다. API/DB/환경 계약 변경 없음. 웹 정적 빌드에 영향, 배포 없음. 롤백은 이번 웹 수정 revert·이전 웹 빌드 복원.
- 공유 인계: 사용자 재개 지시 후 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`에서 기존 `feat/web-ward-create`로 복귀해 수정했다. 전환 전 현황 문서를 저장소 밖 `C:\Users\jjang\.codex\visualizations\2026\11\18\01a1ec4e-15f6-7233-8a52-5426c4c42845\work-status-resume-pr46.md`에 백업하고, 전환 충돌은 B4b 기록과 기존 A7 리뷰·DB 복구 미커밋 기록 16줄을 모두 보존해 해결했다. PR #49 코드·커밋은 포함하지 않았으며 DB·trace 보존. 사용자 푸시 요청으로 이 수정의 코드·회귀 검사·검증 기록과 이 현황 절만 커밋해 origin/feat/web-ward-create에 푸시한다. 기존 A7 리뷰·DB 복구 기록 16줄과 DB·trace는 커밋에서 제외해 작업 폴더에 보존한다. 앱 코드는 최종 검증 이후 변경하지 않아 로컬 검사는 반복하지 않았다. 게시 단계 git diff --cached --check로 확인하며 수정 후 CI·독립 재리뷰는 별도 확인한다. 같은 브랜치에서 리뷰 대응으로 인계하며 PR 병합·자동 병합·수동 배포 없음.

## B4b PR #46 게시 (2026-11-16, Codex)

- 구현을 `c4904dcde33d9706add9f015c36889fe8e0ec5ed`에 커밋하고 `origin/feat/web-ward-create`에 푸시했다. [PR #46](https://github.com/gerrmang/NurseFlow/pull/46) 생성 후 `OPEN`, `isDraft=false`, 대상 `dev`, head 일치를 확인했다. GitHub 연결 도구가 생성 권한 403을 반환해 인증된 로컬 `gh`로 게시했다. 병합·자동 병합 설정 없음.
- 구현 head의 [Verify 43867357456](https://github.com/gerrmang/NurseFlow/actions/runs/43867357456)는 조회 시 Backend tests·Android debug build·Web checks 모두 IN_PROGRESS였다. Vercel과 Vercel Preview Comments는 SUCCESS였으며 프리뷰의 동작은 검증하지 않았다. 수동 배포는 하지 않았다. 이 후속 문서 커밋의 최종 CI는 별도로 확인해야 한다.
- 앱 코드는 앞 검증 이후 변경하지 않았다. 게시 결과와 다음 작업 안내만 문서에 추가하며 `git diff --cached --check`로 확인한다. 관련 없는 DB 복구 기록 8줄은 스테이징에서 제외해 작업 폴더에 그대로 유지했고 DB·trace도 커밋하지 않았다.
- 다음 작업 권장: B5b 팀·간호사·구성원 관리. 선행 A5 `bdc96a6`, A6 `1205f04`, B2 `163e9ff`가 `origin/dev`에 포함됨을 확인했다. B9b 공개 근무표 조회도 B2만 선행하므로 착수 가능하다. 둘 다 B4b 코드에 의존하지 않아 dev 기반 별도 작업 브랜치로 진행할 수 있다. 지금은 기능을 시작하지 않았으며 같은 공유 worktree·B4b 브랜치에서 PR 리뷰 대응 지점으로 인계한다.

## B4b PR 게시 준비 (2026-11-16, Codex)

- 사용자 요청으로 `feat/web-ward-create`를 커밋·푸시하고 `dev` 대상 일반 PR을 게시한다. 병합·자동 병합·배포는 하지 않는다. `git fetch origin` 후 `origin/dev`와 시작 HEAD가 모두 `9d4799156b13b6214e5e4f116d86b224dd5e1898`, 같은 head의 기존 PR 없음 확인.
- 직전 구현 이후 앱 코드 변경 없음. 앞 절의 계약·Lint·타입·277개·빌드 및 실제 backend opt-in 1개 통과 결과를 사용하고 불필요하게 재실행하지 않았다. 게시 전 `git diff --check` 통과. PR CI·독립 리뷰는 게시 뒤 확인하며 사전 통과로 기록하지 않는다.
- 같은 문서의 기존 `로컬 H2 기존 DB 복구` 절과 루트 DB·trace는 관련 없는 사용자 변경이므로 커밋 대상에서 제외하고 작업 폴더에 보존한다. 인계는 같은 브랜치에서 PR 리뷰 대응이며 다음 기능 코드는 이 PR에 섞지 않는다.
- 다음 웹 기능 후보는 B5b(팀·간호사·구성원 관리, 선행 A5·A6·B2)와 B9b(공개 근무표 조회, 선행 B2)다. 선행 커밋은 현재 dev에 포함된다. B4b 병합 없이 dev 기반 별도 기능 브랜치에서 착수할 수 있으나 이번 요청에서는 안내만 한다. B7b 전체는 A9·A10, B6는 A7을 기다린다.

## B4b 웹 병원 검색·병동 생성 (2026-11-16, Codex)

- 사용자 요청으로 기존 `feat/web-ward-create`의 미커밋 B4b 초안을 이어 완성했다. 시작 HEAD `9d47991`은 로컬 `origin/dev`와 동일하다. 같은 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`를 사용했고 새 브랜치·worktree를 만들지 않았다. 아래 기존 로컬 DB 복구 기록과 루트 DB·trace는 보존했다.
- 구현: 내 병동→`/wards/new`, 실행형 병원 검색·주소/출처·페이지·빈 결과/장애 안내, 병원 선택·병동명 1~100자·Asia/Seoul 생성, 설정 안내 골격과 초대 관리 연결. 요청 중 중복 전송 차단, 결과 미확인 입력 잠금과 동일 키/본문 수동 재시도, SPA 목록 왕복 보존, 확인 후 새 요청, 세션 경계 분리. API/DB·서버 설정 계약 변경 없음. 서버가 상한·중복·권한을 판정한다.
- 최종 Windows `web`: `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 성공. 테스트 **10파일 277개 통과**, 별도 실서버 opt-in **1개 기본 건너뜀**, 실패 0. JS gzip 97.34 kB. 초기 타입 검사에서 초안 timeZone 누락과 새 서버 테스트의 Node 타입 참조 누락을 수정했고 최종 전체 명령을 다시 통과했다. `git diff --check` 통과.
- 실제 backend(JDK 21, 18091, 별도 메모리 DB)·동봉 목록 79,899행과 가상 OIDC(18090)·웹(5174)로 브라우저 검증: 로그인·빈 목록, 빈 검색/출처, 365 검색 840곳, 동명 병원 주소 선택, 가상 병동 생성·설정 안내, 재조회 목록의 관리자/병원명, 초대 관리 연결 성공. 스크린샷으로 성공 화면 레이아웃 확인. 최초 샌드박스 소켓 제한은 검증용 프로세스 권한을 분리해 해결했고 보안 설정은 바꾸지 않았다.
- `NURSEFLOW_B4B_E2E=1 npm test -- --run src/ward/WardCreationFlow.server.test.ts`에 해당하는 PowerShell 환경변수 설정 후 실제 연동 검사 **1개 통과**. 실제 flow/API에 전송 전 오류·서버 201 후 응답 폐기를 주입해 동일 키/본문 재시도, 병동 각각 1개·총 2개 증가, 같은 키 재생, 새 키 중복과 다른 본문 키 충돌 409를 확인했다. 실제 물리망 단절·실제 Kakao 검증과 구분한다.
- 미검증: 실제 Kakao, 모바일 크기, 실제 종료 경고, 실서비스 검색 장애/미적재/계정 상한, Android·backend 전체 회귀(웹 기능 단위이므로 미실행), 독립 재리뷰·GitHub CI. 오류 경로 목업 테스트와 구분한다. 원격 최신 상태·PR·보호 설정도 이번에 조회하지 않았다. 상세·재현: `web/B4b-verification.md`.
- 배포 없음. 웹 정적 빌드에만 영향, 되돌리기는 B4b 웹 변경을 이전 버전으로 복원한다. 커밋·푸시·PR·병합 없음. 검증용 세 프로세스는 종료했고 5174/18090/18091 LISTENING 없음 확인. 가상 데이터는 메모리 DB와 함께 소멸했다.
- 인계: 같은 `feat/web-ward-create`, HEAD `9d47991`의 미커밋 구현·테스트·문서에서 이어간다. 서버·OpenAPI diff 없음. 다음 단계는 변경 전체의 독립 리뷰와 PR/CI이며 기존 사용자 DB 복구 기록을 B4b 커밋에 섞지 않는다.

## PR #47 최신 dev 반영 (2026-11-18, Codex)

- 사용자 요청으로 기존 `feat/web-staff-team`에 `origin/dev` `91484c7f2d3e3c87efc76c973d412f648dd6acdf`(A7 PR #48)을 병합했다. 새 브랜치·worktree 없이 같은 공유 작업 폴더에서 진행했다. 충돌은 이 문서뿐이며 기존 기능 기록과 dev 기록을 모두 보존했다. 서버·OpenAPI·생성 타입은 dev 내용을 그대로 반영하고 기능 코드는 변경하지 않았다. 다른 두 웹 PR의 기능은 포함하지 않았다.
- Windows web: `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 exit 0. Test Files  11 passed | 1 skipped (12); Tests  285 passed | 1 skipped (286). 실서버 opt-in 검사는 기본 건너뜀. 병합 diff의 공백 검사는 커밋 생성 전 확인한다.
- backend·Android 전체 회귀와 실제 Kakao·브라우저·운영 검사는 dev 변경을 그대로 통합하는 작업이라 로컬에서 재실행하지 않았다. 갱신 후 GitHub CI는 푸시 뒤 별도 확인한다. 독립 승인으로 표현하지 않는다.
- API/DB 변경은 dev의 A7에 포함된 내용이며 이번 충돌 해결에서 추가 변경 없음. PR 브랜치 푸시만 수행하고 dev 병합·수동 배포·자동 병합은 하지 않는다. 되돌리기는 이 dev 통합 커밋을 첫 부모 기준으로 revert한다. 기존 미커밋 A7 리뷰·DB 복구 기록과 DB·trace는 이번 커밋·푸시에서 제외해 보존한다. 공유 인계는 기존 feat/web-ward-create 작업 브랜치이며 각 PR에서 후속 리뷰를 이어간다.

## B5b PR #47 게시 (2026-11-17, Codex)

- 구현 `4be8ef682a5cfda63b282df3aa50ceaa59502155`을 커밋·푸시하고 [PR #47](https://github.com/gerrmang/NurseFlow/pull/47)을 게시했다. GitHub 조회로 OPEN·일반 PR(`isDraft=false`)·대상 dev·head 일치·autoMergeRequest=null 확인. 생성 권한이 없는 GitHub 연결 도구 대신 로컬 gh를 사용했다. B4b PR #46과 분리됐으며 병합하지 않았다.
- 구현 head의 [Verify 43982046469](https://github.com/gerrmang/NurseFlow/actions/runs/43982046469)는 조회 시 Backend tests·Android debug build·Web checks 모두 IN_PROGRESS. Vercel·Vercel Preview Comments는 SUCCESS였으나 해당 프리뷰 동작은 검증하지 않았다. 수동 배포 없음. 이 후속 문서 커밋의 최종 CI는 별도로 확인한다.
- 최종 앱 검사 후 코드는 변경하지 않고 게시 기록만 추가한다. 사용자 DB·trace와 DB 복구 기록 8줄은 기능 커밋에서 제외해 작업 폴더에 남겼다. 검증용 프로세스 3개를 종료하고 5174/18090/18091 LISTENING 없음 확인. 메모리 DB 검증 데이터는 종료로 소멸했다.
- 같은 공유 worktree·`feat/web-staff-team`에서 PR 리뷰 대응으로 인계한다. 독립 리뷰·최종 CI와 검증 기록의 제한은 남아 있다. main/dev 직접 푸시·PR 병합·자동 병합 설정 없음.

## B5b 웹 팀·간호사·구성원 관리 (2026-11-16~17, Codex)

- 승인된 B5b 계획을 구현했다. 같은 공유 worktree에서 `git fetch origin` 후 dev `9d4799156b13b6214e5e4f116d86b224dd5e1898` 기반 `feat/web-staff-team`으로 분기했다. B4b PR #46은 OPEN·미병합이며 그 커밋을 포함하지 않았다. 전환 중 문서 충돌은 dev 내용과 기존 DB 복구 미커밋 절을 보존해 해결했고 B4b 기록은 원래 PR 브랜치에 남겼다.
- 구현: 관리자 병동 카드→`/wards/:wardId/staff`; 팀 생성/이름·표시 순서/삭제, 간호사 기록 등록/이름·자격·활성/팀 배치, 드래그·위아래 버튼 경력순, 실제 경력·직급·선임·단계·조정 이력, 구성원 관리자 지정/해제·접근 비활성/재활성. 병원 입사 연월 조회와 실제 경력 수정은 분리했다. 경력 정책 편집·근무 형태/규칙 입력은 제외했다.
- 기존 API 계약을 소비하며 `staffAdminApi`와 `StaffAdminStore`를 분리했다. 권한 확인 후 읽기, 세션·병동별 상태, 늦은 응답 폐기, 쓰기 직렬화와 전체 재조회, 결과 유실 뒤 자동 재전송 금지·사용자 확인, 재조회 실패 시 편집 차단, 본인 권한 상실 시 관리 데이터 제거. 일반 편집의 마지막 저장 우선·순서 충돌 감지 한계를 명시했다. backend·OpenAPI·DB·운영 설정 변경 없음.
- 최종 Windows `web`(2026-11-17): `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build` 모두 성공. **11파일 285개 통과, 실서버 opt-in 1개 기본 건너뜀, 실패 0**, JS gzip 101.24 kB. 직전 중단 도구의 결과를 추정하지 않고 전체 명령을 새로 실행해 확인했다. `git diff --check` 통과.
- 실제 backend(JDK 21, 18091, 별도 `jdbc:h2:mem:b5b_fictional_e2e`)·공식 동봉 목록 79,899행, 가상 OIDC(18090)·웹(5174) 사용. opt-in 실제 store/API 검사 **1개 통과**, 최종 상태 보완 후 재실행도 통과. 팀·등록·경력/편성·이력·이동·정렬, 활성 간호사 팀 삭제/오래된 정렬 거절, 마지막 관리자·관리자 전용 역할·활성 기록 충돌, 접근 재활성 시 기록 비활성 유지, 본인 접근 상실 후 데이터 제거, 실제 201 응답 폐기 뒤 중복 생성/재전송 차단을 확인했다.
- 브라우저: 가상 관리자 로그인, 관리 진입, 팀 생성, 간호사 2명 등록·팀 지정, 버튼 정렬/저장·순서 재조회, 실제 경력·직급, 선임·5단계 저장과 이력, 구성원 확인 단계·마지막 관리자 거절 안내 확인. 데스크톱 스크린샷으로 레이아웃 확인. 직접 드래그는 DOM 화면 테스트로만 검사했다.
- 미검증: 실제 Kakao 재로그인, 실제 다중 브라우저 동시 편집·물리망 단절, 휴대폰 브라우저 크기, 브라우저 직접 드래그. Android·backend 전체 회귀는 변경 없는 웹 기능 단위라 미실행. 독립 리뷰·PR CI는 별도. 상세·재현은 `web/B5b-verification.md`.
- 사용자 DB·trace와 기존 DB 복구 기록은 기능 커밋에 포함하지 않고 보존한다. 검증용 세 프로세스 종료, 포트 종료 상태는 게시 직전 확인한다. 수동 배포 없음. 웹 정적 빌드에만 영향, rollback은 B5b 변경 revert와 이전 웹 빌드 복원. 같은 B5b 브랜치에서 커밋·푸시·별도 dev 대상 PR 게시 후 리뷰 대응으로 인계하며 병합하지 않는다.

## 2026-11-16 현재 기준

- 최신 개발 기준은 `origin/dev` `9d47991`이다. PR #43(A8 근무 유형), #45(B3b 웹 초대), #44(B4a Android 병동 생성)가 차례로 병합됐다. 아래 긴 기능 기록 중 이 PR들을 진행 중으로 적은 행은 병합 전 기록으로 읽는다.
- A7 편성 대상 판정을 `feat/roster-eligibility`에서 구현해 PR #48을 열었다. 기본은 활성 `WARD_MEMBER` 연결 간호사만 포함하고, 계정 없는 가상 간호사는 병동별 시연 모드를 켠 경우에만 포함한다. 후보·수동·자동·확정·충원은 같은 서버 판정을 쓴다. 2026-11-18 로컬 백엔드 `test`·`check`, 웹 API 계약·타입·린트·252개 테스트·빌드가 성공했고 PR #48의 Backend tests·Android debug build·Web checks도 통과했다. 독립 코드 리뷰와 사람 승인은 아직 대기 중이다.
- PR #48 독립 리뷰의 P3: 조회 중 시연 모드 변경이 커밋되면 응답의 `demoMode=false`와 `eligible=true`가 섞일 수 있었다. 응답의 포함 여부를 같은 `demoMode` 값과 제외 사유로 계산하도록 수정하고, 읽기 사이 모드 변경을 강제하는 회귀 테스트를 추가했다. 2026-11-18 해당 테스트와 백엔드 전체 `test --offline --no-daemon` 성공. 수정 커밋의 GitHub CI·재리뷰는 대기 중이다.
- 2026-11-18 PR #48 후속 리뷰의 P4·P5 보완: 최신 정책 ETag로 시연 모드 `true → false` 성공 경로와 가상 간호사 제외, 저장된 가상 배정의 확정 차단, 초안 revision·재검증 표시, 기존 공개본 보존을 HTTP·서비스 통합 테스트로 확인했다. 모드 변경 시 모든 DRAFT revision이 올라 기존 초안 ETag가 412가 된다는 계약을 OpenAPI와 생성된 웹 타입 설명에 추가했다. 로컬 대상 테스트, 백엔드 전체 `./gradlew check --offline --no-daemon`, 웹 `npm run api:check`·`npm run typecheck`, `git diff --check` 모두 성공. Android·실기기·운영 환경은 변경 범위 밖이라 재검증하지 않았다. 이 보완의 PR CI와 사람 리뷰는 푸시 후 별도 확인한다.
- 앞으로 설계·구현·검증·리뷰는 Codex가 맡되, 변경은 기능별 브랜치와 `dev` 대상 PR로 남긴다. 사람의 GitHub 승인과 CI는 별도로 확인한다. 기존 Claude 수행 기록은 이력으로 보존한다.

## 갱신 규칙

- Claude와 Codex는 기능 단위, PR, 리뷰를 마친 뒤 같은 작업 브랜치에서 이 문서를 갱신한다.
- 진행 상태와 검증 결과는 확인한 사실만 적고, 미검증 항목과 생략 사유를 구분한다.
- 비밀키, 토큰, 실제 간호사 정보와 부재·건강 정보는 기록하지 않는다.
- 새 작업을 시작하기 전에 [기능 분해표](feature-breakdown.md)의 현재 기능 행과 파일 소유 범위, 이 문서의 진행 현황을 확인한다.

## A·B 공유: 확정 결정 요약 (2026-11-10)

- 번호 결정: 1·2·3(A4), 5(A3), 6(B3a), 9(A7), 10·11(A5), 12, 19(B2) 해결, 4 보류. 세부는 [기능 분해 §7](feature-breakdown.md)과 구현 중 확정 정책 §7.1.
- B가 화면에 반영할 서버 동작: 병원 검색은 검색 실행 시 호출·출처 표시(A3), 병동 생성 409 두 종류(`WARD_CREATION_LIMIT_REACHED`, `WARD_ALREADY_CREATED`)와 503(목록 미적재) 안내(A4), 초대 미리보기 `hospitalName` 표시(A4), 팀 순서 409 `TEAM_ORDER_STALE` 시 다시 불러오기(A5).
- 로컬 실연동에 필요한 서버 환경변수: `NURSEFLOW_KAKAO_REST_API_KEY`, `NURSEFLOW_KAKAO_REDIRECT_URIS=http://localhost:5173/auth/kakao/callback`(웹 로그인), `NURSEFLOW_HOSPITAL_DATASET_PATH`(병원 검색·병동 생성). 키 값은 공유 문서·채팅에 두지 않고 Kakao Developers 멤버 초대나 비밀번호 관리자로 공유한다.

## 기능 단위 현황

| 단위 | 브랜치 | PR | 상태 | 검증 | 미해결 리뷰 지적 |
| --- | --- | --- | --- | --- | --- |
| 기능 분해표 | 해당 없음 | #29 | `dev` 병합 완료 | 병합 확인 | 없음 |
| README | 해당 없음 | #28 | `dev` 병합 완료 | 병합 확인 | 없음 |
| A1 초대·경력 월 | `feat/invitation-24h-career-month` | #27 | `dev` 병합 완료 | 병합 확인 | 없음 |
| B1 Android Kakao 세션 | `feat/android-kakao-session` | #30 | `dev` 병합 완료 (2026-11-09, 일반 병합 커밋 `9239fa6`) | 로컬 단위·기기·실서버 검증은 [`B1 검증 기록`](../android/B1-verification.md)에 기록. GitHub CI·PR 리뷰 여부는 별도 확인 | 기록된 미해결 P1 없음. 병합 방식은 아래 참고 |
| A2 웹 세션 API | `feat/auth-web-session-v2` | #33 | `dev` 병합 완료 (2026-11-09, 스쿼시 병합 커밋 `fa47a5a`) | 2026-11-09 로컬 `./gradlew test --offline`: 108건 통과, 건너뜀 1. 동시 state 테스트(`FOR UPDATE` 제거 변이 3/3 실패)와 client_secret 전달(전달 줄 제거 변이 실패) 검출력 확인. 실제 Kakao E2E 미검증(목업만) | 없음 |
| B2 웹 기반 세션 | `feat/web-foundation-session` (`dev` `e4ca9bf` 반영) | [#39](https://github.com/gerrmang/NurseFlow/pull/39) (Ready) | 구현·주요 실제 Kakao 검증·A 리뷰 수정 완료. B3a 병합 후 최신 dev 통합 | 리뷰 수정 head `d47eb38`: 웹 55개 통과·빌드 성공, CI 3개 작업 성공. 통합 후 검증은 아래 2026-11-11 기록 | 개발 의존성 moderate 경고·기존 브라우저 검증 제한 유지. A 진단 후속은 별도 미반영 |
| B9a Android 공개 근무표 조회 | `feat/android-published-roster` (`dev` `fa47a5a` 통합) | [#34](https://github.com/gerrmang/NurseFlow/pull/34) | `dev` 병합 완료 (2026-11-10, 스쿼시 병합 커밋 `d1b8729`). 구현·기기·실서버 검증과 PR P3 수정 완료, 병합 전 Backend tests·Android debug build 통과 | [B9a 검증 기록](../android/B9a-verification.md), 아래 최종 검증 참조 | 기존 P2 해결, P3 수정 후 재리뷰 대기 |
| B9b 웹 공개 근무표 조회 | `feat/web-published-roster` (dev `9d47991` 기반) | [#49](https://github.com/gerrmang/NurseFlow/pull/49) (Ready) | 본인/전체·월 이동·날짜 상세 구현, 가상 OIDC+실제 backend 및 브라우저 핵심 검증 완료. 일반 PR 게시·리뷰 대기 | 2026-11-18 계약·Lint·타입·281개·빌드 통과, 실서버 opt-in 1개 별도 통과. [B9b 검증 기록](../web/B9b-verification.md) | 독립 리뷰·최종 CI 미확인, 실제 Kakao 등 제한은 검증 기록 참조 |
| B3a Android 초대 관리·가입 | `feat/android-invite-join` | [#38](https://github.com/gerrmang/NurseFlow/pull/38) | `dev` 병합 완료 (2026-11-11, `e4ca9bf`). GitHub merged=true 확인 | 수정 head `691312c`: 단위 76개·빌드·Lint 성공, CI Backend·Android 성공. 기기 실행 제한은 기존 기록 유지 | 기존 알려진 제한은 android/B3a-verification.md 참조 |
| B4a Android 병원 검색·병동 생성 | `feat/android-ward-create` (`405a8e5`에 dev `b352759` 통합) | [#44](https://github.com/gerrmang/NurseFlow/pull/44) (Ready) | 구현·목업·로컬 실서버 핵심 흐름 검증 완료. 기존 Android 17 콜드 부팅 후 실제 Kakao 로그인·검색·생성·목록·초대 관리 연결 확인. Espresso 3.7.0 명시 의존성으로 UI 테스트 오류 수정 | 2026-11-12 단위 140개(실패·오류·건너뜀 0, 재실행), Compose UI 2개 통과(직접 instrumentation), 테스트 APK·Lint 성공(오류 0·경고 35). 실제 서버 응답 유실 (a)·(b) 재시도·키 재생·각 병동 1개 확인. [B4a 검증 기록](../android/B4a-verification.md) | 상한·키 충돌·미적재/외부 장애·유실 뒤 입력 변경 등의 실서버 검사는 미실행. 운영·전체 기기 회귀·CI 미검증 |
| B3b 웹 초대 관리·가입 | `feat/web-invite-join` (`b352759` 기반 구현 `ff63354`) | [#45](https://github.com/gerrmang/NurseFlow/pull/45) (Ready) | 구현·코드 재리뷰 완료. 실제 Kakao 1계정 로그인, 가상 OIDC 3사용자+실제 backend의 발급·재발급·철회·간호사/관리자 가입을 브라우저에서 확인. 실제 Kakao 2계정 완료 조건은 미충족 | 2026-11-12 Windows 계약·Lint·타입·테스트 8파일 241개·빌드 성공. 실제 복사→붙여넣기·URL 정리·390×844 화면 확인. 개발용 재현 도구 추가. [B3b 검증 기록](../web/B3b-verification.md) | 두 번째 실제 Kakao 계정 없음. 실제 2계정 가입·저장소 값/Referer·실제 만료/응답 유실 등과 최종 CI는 미검증 |
| B4b 웹 병원 검색·병동 생성 | `feat/web-ward-create` | [#46](https://github.com/gerrmang/NurseFlow/pull/46) (Ready) | 구현·목업·실제 backend/가상 OIDC 브라우저 핵심 흐름 검증 완료, 일반 PR 게시·리뷰 대기 | 2026-11-16 계약·Lint·타입·277개·빌드 통과, 별도 실제 backend 응답 유실/재시도 1개 통과. [B4b 검증 기록](../web/B4b-verification.md) | 독립 리뷰·최종 CI·실제 Kakao·휴대폰 브라우저 크기 미검증 |
| B5b 웹 팀·간호사·구성원 관리 | `feat/web-staff-team` | [#47](https://github.com/gerrmang/NurseFlow/pull/47) (Ready) | 구현·목업·실제 backend·가상 OIDC 브라우저 핵심 검증 완료, 일반 PR 게시·리뷰 대기 | 2026-11-17 계약·Lint·타입·285개·빌드 통과, 실제 backend opt-in 1개 별도 통과. [B5b 검증 기록](../web/B5b-verification.md) | 독립 리뷰·최종 PR CI 미검증. 일반 편집 동시성 한계·실제 Kakao 등 제한은 검증 기록 참조 |
| 에이전트 지침 정합성 | `docs/agent-guidance-alignment` | #32 | `dev` 병합 완료 (2026-11-09, 일반 병합 커밋 `ab8347d`. 스쿼시 원칙에서 벗어남) | `git diff --check` 통과. GitHub Actions `Verify` PR 실행 성공, `dev` push 실행 성공 | 없음 |
| A3 병원 검색 API | `feat/hospital-search` | #35 | `dev` 병합 완료 (2026-11-10, 스쿼시 병합 커밋 `23af850`) | 병합 전 `./gradlew test --offline` 134건 통과, CI 통과. 실제 2026.9. 파일 79,899곳 적재 0.7초, 검색 15~40ms | 없음 |
| A4 병동 생성 | `feat/ward-self-create` | #36 | `dev` 병합 완료 (2026-11-10, 스쿼시 병합 커밋 `6a2cf29`) | 2026-11-10 `./gradlew test --offline` 138건 통과(건너뜀 4). 같은 키 동시 요청 테스트는 중복 키 처리 제거 변이 3/3 실패로 검출력 확인. 이전 스키마 H2에 schema.sql 두 번 적용 테스트 통과 | 없음 |
| A5 경력·팀 내 순서 | `feat/staff-admin-career-adjust` | [#37](https://github.com/gerrmang/NurseFlow/pull/37) | `dev` 병합 완료. GitHub 메타데이터와 `origin/dev`에서 `bdc96a6` 확인 | 기존 검증 기록은 아래 보존. 이번 문서 갱신에서 A5 검사를 재실행하지 않음 | 없음 |
| B 검증 후속(Kakao 진단·초대 계약) | `fix/kakao-diagnostics-invite-contract` (`/private/tmp/NurseFlow-fix`) | [#40](https://github.com/gerrmang/NurseFlow/pull/40) `dev` 병합 완료(2026-11-11) | B2·B3a 실제 Kakao 검증 기록에 따른 A 후속. 토큰 교환 401 오류 코드 로그(KOE010), 초대 수락 계약 정정(동작 변경 없음), README 초대 설명, 수정·재리뷰 원칙 | 2026-11-10 `./gradlew test --offline` 150건 통과(건너뜀 4). 수정 전 401 본문 유실 재현 후 수정 | 없음 |
| A6 구성원 관리 | `feat/membership-admin` (`/private/tmp/NurseFlow-a6`) | PR #41 dev 병합 완료(`1205f04`) | 구현·Codex 리뷰 완료. 구성원 목록·관리자 지정/해제·비활성화·재활성화, 마지막 활성 관리자 보호(결정 4, 복구 절차 미구현), 비활성 구성원 즉시 접근 차단. dev 반영 후 두 가지 수정: 구성원 비활성화 시 연결 간호사의 팀 순번 정리(`7dae5c0`), 잠금 대기 중 권한 해제된 관리자의 변경 차단 `WardService.lockAsAdmin`과 초대 재발급·수락 잠금 순서 정정(`b1def48`) | 2026-11-11 `./gradlew test --offline` 209건 중 통과 204, 건너뜀 5, 실패 0(최신 dev `#40` 반영 후). 리뷰 P1(비활성 구성원 기록 재활성화로 편성 복귀) 수정 `d81916e`(MEMBER_INACTIVE, 편성 필터에 활성 WARD_MEMBER 요구), 본인 근무표 조회에도 같은 필터 `b674f92`. 외부 리뷰 P1(관리자 승격 시 활성 간호사 기록 → 편성 계산) 수정 `71dd50e`·`0d1c502`(결과 상태 검사, 관리자 역할 기록 재활성화 차단, 편성 조회 방어 필터), 예외 경로 취소·대기 테스트 DB 잠금 확인 `ac3005b`. PR #41 리뷰 P2(실행 중 자동배치 취소가 병동 잠금에 막힘) 수정 `4ca4e0d`, 재리뷰 P2·P3(취소와 결과 반영·조기 실패 경합, 테스트 동기화) 수정 `2b5640d`·이번 커밋. 순번 정리 제거 시 새 테스트 6건 실패, 잠금 뒤 재확인 제거 시 경합 테스트 2건 실패로 검출력 확인  2026-11-12 불변 조건 무작위 테스트 `77e2eae`(보호 코드 7개 중 6개 제거 시 검출). PR #41 재리뷰 P2(구성원 비활성화와 본인 프로필 쓰기 경합) 수정: 본인 쓰기도 `WardService.lockAsMember`로 병동 잠금 뒤 소속 재확인, 순서 강제 회귀 테스트 추가(수정 제거 시 200으로 실패 확인). P5(관리자 지정 충돌 조건 문서) 정정. `./gradlew test --offline` 218건 중 통과 213, 건너뜀 5, 실패 0 | A8 병합 시 근무 유형 쓰기도 `lockAsAdmin` 사용 필요 |
| 병원 목록 동봉(A3 후속) | `feat/hospital-bundled-list` (`/private/tmp/NurseFlow-hospital`) | PR #42 dev 병합 완료(`b352759`) | B 개발 환경에서 경로 설정 없이 병원 검색·병동 생성이 되도록 공식 목록 5개 열(2026.9., 79,899행, gzip 약 2.5MB)을 서버에 동봉. 경로가 없으면 시작 시 같은·더 새 목록이 없을 때만 가져옴. 갱신 스크립트 `backend/scripts/extract_hospital_list.py`. Codex P4 2건 수정 `1a24ff9`. PR 리뷰 P3·P4·P5 수정: 동봉 목록이 마지막 적재와 다르면 가져오고(같은 기간 정정본 반영) 더 이전 기간·기간 미상이면 건너뜀, 스크립트는 1만 행 미만이면 기존 목록을 건드리지 않고 종료, 기간 미상 적재 뒤 복구 방법 문서화 | 2026-11-12 dev `1205f04` 반영 후 `./gradlew test --offline` 223건 중 통과 218, 건너뜀 5, 실패 0(리뷰 수정 후). web `api:check` 통과. 패키징된 jar를 별도 DB로 두 번 실행해 첫 실행 79,899행 가져옴, 두 번째 건너뜀 확인 | 없음 |
| A8 근무 유형 설정 | `feat/shift-type-settings` (`/private/tmp/NurseFlow-a8`) | PR 개설(최신 dev `b352759` 반영) | 구현·Codex 리뷰 완료. `/wards/{id}/shift-types` 조회·생성·수정·비활성화, 휴게·인정·월 환산 시간, 병동 정책 revision(`policyRevision`), 사용 중 유형 변경 시 DRAFT `needsRevalidation`·칸 시각 재계산, OTHER 입력 차단, 생성 Idempotency-Key. dev 반영 후 쓰기를 `lockAsAdmin`으로(`a16d751`), 멱등 재생을 잠금·재확인 뒤로(`6fbaa45`, 같은 패턴인 `cancelGeneration` 같은 키 재시도 500 수정 포함). 미결: 결정 14·23(휴일·목표 시간)은 범위 밖, 비활성 유형 코드는 재사용 불가 | 2026-11-13 `./gradlew test --offline` 262건 중 통과 257, 건너뜀 5, 실패 0(외부 리뷰 P2 생성·no-op 비활성화 If-Match 수정 후). web `api:check`·lint·typecheck·test·build 통과. 경합 테스트 3건은 각 보호 코드 제거 시 실패 확인 | 없음 |
| A7 편성 대상 | `feat/roster-eligibility` | [#48](https://github.com/gerrmang/NurseFlow/pull/48) | 기본 편성 대상, 병동별 시연 모드, 제외 사유 API 구현. 후보·수동·확정 회귀 테스트 추가 | 2026-11-18 백엔드 test/check, 웹 API 계약·타입·린트·252개 테스트·빌드, PR CI 3작업 성공 | 독립 PR 리뷰·사람 승인 대기 |
| A9 이후 | 해당 없음 | 미개설 | A7 병합 뒤 선행 조건 순서로 착수 | 해당 없음 | 없음 |

## B4a 최신 dev 통합 (2026-11-12, Codex)

- 사용자 요청으로 `feat/android-ward-create`의 B4a 커밋 `d7ce955`에 최신 `origin/dev` `b352759`를 병합했다. A6(#41)와 병원 목록 동봉(#42)을 충돌 없이 반영했으며 Android 기능 코드는 변경하지 않았다.
- 병원 목록은 이제 서버에 동봉되어 별도 CSV 전달 없이 실연동을 준비할 수 있다. 위 B4a 행의 과거 '데이터 미적재라 검색 불가' 설명은 이 통합으로 대체한다. 실제 검색·생성·Kakao·기기 확인은 아직 하지 않았다. `android/B4a-verification.md`의 준비 절차를 현행화했다.
- `android`에서 `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon`: **BUILD SUCCESSFUL**, 80개 작업 중 8개 실행·72개 UP-TO-DATE. 단위 테스트 작업은 UP-TO-DATE이므로 새로 140개를 실행했다고 주장하지 않는다. `git diff --cached --check` 통과. 백엔드 테스트·기기 UI·실서버·CI는 실행하지 않았다.
- B3b 미커밋 변경은 작업 파일만 임시 보관해 B4a에 섞지 않았고, 통합 후 B3b 브랜치로 복원한다. 사용자 DB·local.properties는 변경하지 않는다. 이 통합은 로컬 작업 브랜치의 dev 반영이며 dev/main 병합·배포·원격 푸시는 하지 않는다.

## B4a 에뮬레이터 검증 진행 (2026-11-12, Codex)

- 사용자 요청으로 `feat/android-ward-create`(`405a8e5`)에서 연결된 `emulator-5554`(Pixel_10, Android 17/API 37.2) 검증을 시작했다. 최신 debug APK 설치 성공. UI 트리와 화면에서 로그인 및 '로그인하고 병동 만들기' 의도 안내를 확인했다.
- 기존 `WardCreateScreenUiTest` 2건은 실제 실행했으나 **2건 실패**: Espresso 입력 장치 초기화의 `NoSuchMethodException: android.hardware.input.InputManager.getInstance`로 화면 검증 전 실패했다. 첫 명령은 PowerShell 인수 전달 오류로 테스트 시작 전 실패했고, `-P...` 인수를 인용해 바로잡은 실행에서 위 2건 실패를 확인했다. 자동 UI 검증 통과로 기록하지 않는다.
- 로컬 설정의 Kakao audience를 값 출력 없이 적용하고, 임시 세션 비밀값·`jdbc:h2:mem:b4a_device_verification`으로 실제 backend `bootRun`(8080)을 실행했다. 동봉 병원 목록 **79,899건 적재** 로그 확인. 사용자 파일 DB·local.properties는 변경하지 않았다.
- 실제 Kakao 진입 시 기본 브라우저 Chrome이 `android.credentials.CredentialManager`의 NullPointerException으로 종료돼 로그인·검색·생성 실연동까지 진행하지 못했다. 설치된 AVD/시스템 이미지는 Android 17 한 개뿐이며 SDK command-line tools는 설치돼 있지 않다. Android Studio의 Add Device에서 Medium Phone / API 35 Android 15 / Google Play x86_64(다운로드 1.6GB)를 선택해 설치 직전까지 준비했다. Windows UI 소프트웨어 설치 확인 정책에 따라 Finish는 누르지 않고 사용자 확인 대기 중이다. 기존 AVD는 변경하지 않았고, 검증용 backend는 8080에서 실행 중이다. 운영 코드 수정·커밋·푸시는 하지 않았다.

## B4a Android 17 재검증 완료 (2026-11-12, Codex)

- 사용자가 기존 환경 재시도를 요청해 Pixel_10(Android 17/API 37.2)을 데이터 초기화 없이 `-no-snapshot-load`로 부팅했다. Chrome 로그인 화면 진입 성공, 사용자가 직접 Kakao 로그인 완료 후 생성 화면으로 자동 이동했다. 이전 Chrome 충돌은 이번에 재현되지 않았고 원인 확정·Chrome 수정은 하지 않았다. Android 15 설치는 진행하지 않았다. 앞 절의 설치 대기·로그인 차단 상태는 이 결과로 대체한다.
- UI 2건의 `InputManager.getInstance` 실패는 재현됐다. `dependencyInsight`에서 Compose 테스트의 간접 Espresso 3.5.0을 확인하고, 공식 수정이 포함된 3.7.0을 `androidTestImplementation`에 명시했다. 수정된 테스트 APK만 설치해 직접 `am instrument`로 실행한 결과 **OK (2 tests)**, 두 메서드 모두 status code 0. 기존 Gradle connected HTML/XML의 실패 기록과 구분한다.
- JDK 21로 `:app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon` 성공(단위 작업 UP-TO-DATE). 이어 `:app:testDebugUnitTest --rerun-tasks --no-daemon` 성공, 24개 작업 모두 실행·XML **140개**, 실패/오류/건너뜀 **0**. Lint 오류 **0**, 경고 **35**, Information **1**. 첫 JBR 25 재시도는 Gradle `25.0.3` 오류로 기기 실행 전에 실패해 JDK 21로 바로잡았다.
- 실제 backend `bootRun`(8080), 임시 세션 비밀값·메모리 DB, 동봉 병원 **79,899건**으로 검색→가상 병동 생성→관리자 설정 안내→초대 관리→목록의 병원명을 확인했다. 같은 병원·이름 재생성은 실제 **409**와 중복 안내가 나왔다. 가로 회전 후 선택·입력 유지, Enter 검색의 빈 결과와 출처 `2026.9. 기준`도 확인했다.
- 사용자의 명시 허용 뒤 PC 루프백 임시 프록시로 실제 서버의 유실 (a: backend 전달 전 단절)·(b: 201 수신 뒤 앱 전달 전 단절)를 분리했다. 둘 다 자동 재전송 없음·결과 미확인 안내·같은 키 재시도 성공. (b)는 **같은 병동 ID·201 재생**까지 확인했다. 마지막 실제 `GET /api/v1/wards` 응답에서 기본·(a)·(b) 가상 병동이 **각각 1개**였다. DB 직접 조회는 하지 않았다. 토큰·키·응답 본문을 로그/파일에 기록하지 않았다.
- 프록시는 원래 `null`, 회전은 원래 `free`로 원복하고 프록시 프로세스·임시 스크립트를 제거했다. 프록시 없는 재실행에서도 세 병동을 확인했다. `nurseflow.mv.db`·`local.properties` 보존. 서버/API/DB 스키마·운영 코드 변경·배포 영향 없음. 되돌리기는 Espresso 의존성 추가 제거(기존 Android 17 테스트 오류 재발).
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-ward-create`, HEAD `405a8e5`. 테스트 의존성·두 검증/현황 문서만 미커밋 변경. 기존 현황 기록은 보존했다. 커밋·푸시·PR 개설·병합·배포 없음. 에뮬레이터와 검증 backend(8080)는 실행 상태다. 메모리 DB라 backend 종료 시 검증 병동·서버 세션이 사라진다. 상한·키 충돌·유실 뒤 입력 변경·목록 미적재/외부 장애·초대 의도 경합의 실제 검증, 전체 기기·백엔드 테스트·CI는 이번에 실행하지 않았다.

## B4a PR #44 게시 (2026-11-12, Codex)

- 사용자 요청으로 Espresso 수정·실연동 검증 기록을 `b894cfe`에 커밋하고 `origin/feat/android-ward-create`에 푸시했다. `gh`로 [PR #44](https://github.com/gerrmang/NurseFlow/pull/44)를 생성했으며 `OPEN`, `isDraft=false`, 대상 `dev`, 게시 head `b894cfe41616a229b2c98a93b6cde32ef5f1b93c`를 확인했다.
- PR 템플릿에 구현 결과·140개 단위/2개 UI 검사·실서버 응답 유실 검증·미실행 검사·API/DB 변경 없음·배포 영향·되돌리기를 기록했다. 기존 상세 검증을 유지하며 이번 게시 단계에서 로컬 앱·기기 검사는 재실행하지 않았다. `git diff --check` 통과.
- 게시 직후 [Verify 43221647358](https://github.com/gerrmang/NurseFlow/actions/runs/43221647358)의 Backend tests·Android debug build·Web checks는 모두 실행 중이었다. CI 통과·GitHub 승인·브랜치 보호 강제를 주장하지 않는다. 이 게시 기록을 포함한 후속 문서 커밋의 CI는 해당 head로 별도 확인한다.
- 공유 인계: 같은 worktree·`feat/android-ward-create`에서 이 게시 기록만 후속 커밋·푸시한다. 로컬 DB `nurseflow.mv.db`를 보존하고 PR 리뷰 대기로 넘긴다. 병합·자동 병합 설정·수동 배포 없음. 앞 절의 'PR 미개설 유지·커밋/푸시 없음'은 이전 검증 시점 기록이며 이 게시로 대체한다.

## B4a CI 실패 조사 (2026-11-13, Codex)

- 사용자 요청으로 PR #44 head `3a737a5`의 [Verify 43221785489](https://github.com/gerrmang/NurseFlow/actions/runs/43221785489)를 확인했다. Backend·Web은 성공, Android는 단위 140개 중 `WardCreationApiTest`의 응답 유실 관련 5개가 실패했다(타임아웃 3개·상태 assertion 2개). 실패 작업만 재실행한 attempt 2도 같은 5개가 실패했다.
- Windows JDK 21에서 `:app:testDebugUnitTest --tests '*WardCreationApiTest' --rerun-tasks --no-daemon` 성공. 동일한 컴파일 결과·의존성으로 Linux JDK 21에서 직접 JUnit 실행 시 해당 11개와 전체 140개가 통과했다. assertions 활성화·2코어/512MB 조건의 해당 11개도 통과해 OS·CPU·메모리만으로 원인을 확정하지 못했다. Linux 실행은 Gradle 전체 빌드와 동일한 검사라고 주장하지 않는다.
- CI의 짧은 예외 출력으로는 실패 상태와 원인을 판단할 수 없어 단위 테스트 로깅을 `TestExceptionFormat.FULL`로 보강한다. 검사 생략·타임아웃 확대·운영 앱 동작 변경은 없다. 다음 CI에서 상세 예외를 확인하고 원인을 좁힌다. 로컬 서버·에뮬레이터는 재시작하지 않았고 사용자 DB는 보존했다.

## B4a CI 실패 재현·수정 (2026-11-13, Codex)

- 상세 예외 커밋 `8b13420`의 Verify 43276828688에서도 같은 5개가 실패했다. 이후 테스트 JVM 전용 hosts 파일에서 localhost를 IPv4/IPv6 두 주소로 해석하게 하자 로컬 Linux에서도 **동일한 5개 실패**를 재현했다. 일반 Linux·2코어 조건만으로는 통과했던 이유를 분리했으며 시스템 hosts 파일은 건드리지 않았다.
- 단일 주소의 MockWebServer를 이중 주소 hostname으로 호출해, 의도적으로 연결을 끊은 뒤 후속 호출이 서버가 없는 다른 경로로 향하는 fixture 문제다. `WardCreationApiTest.signedIn`에서 서버 바인딩과 클라이언트 URL을 모두 `127.0.0.1`로 고정했다. 무재시도·키/중복 assertion·대기 제한·운영 앱 코드는 유지했다.
- 수정 후 Windows 해당 11개 통과, Linux의 이중 주소·assertions·2코어·512MB 조건에서 전체 **140개 통과**(직접 JUnit, 13.613초). CI와 같은 Windows Gradle 검사·앱/테스트 APK 빌드 결과와 최종 CI는 아래 후속 기록으로 확인한다. 상세 재현 근거는 android/B4a-verification.md에 있다.
- Windows의 CI 동일 명령 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon`도 BUILD SUCCESSFUL(71개 작업 중 48개 실행), 단위 XML **140개**, 실패·오류·건너뜀 0. 최종 CI는 푸시한 수정 head로 별도 확인한다. 이번 변경 후 Lint·기기·실서버 검사는 재실행하지 않았다.
- 공유 인계: 같은 worktree·`feat/android-ward-create`에서 테스트 fixture와 검증 문서만 반영한다. 사용자 DB 보존, 서버·에뮬레이터 재시작 없음. API/DB·배포 영향 없음. 테스트 주소 고정 제거 시 기존 이중 주소 CI 실패가 재발할 수 있다.
- 수정 커밋 `5967145a5178c52155563c18eb78af653c8bf721`을 PR #44에 푸시한 뒤 [Verify 43278296237](https://github.com/gerrmang/NurseFlow/actions/runs/43278296237)의 **Backend tests·Android debug build·Web checks 모두 success**, run completed/success를 GitHub CLI로 확인했다. PR head도 같은 SHA임을 확인했다. 이 결과 기록만 후속 문서 커밋으로 남기며 기능 코드·서버·에뮬레이터는 더 변경하지 않는다.

## B3b 브라우저·실제 backend 검증 및 Draft 준비 (2026-11-12 저녁, Codex)

- 기존 공유 worktree에서 `feat/web-invite-join`(`ff63354`)으로 복귀했다. B4a 코드·커밋은 가져오지 않았으며 사용자 DB를 보존했다. 기존 `b352759`는 현재 조회한 `origin/dev`를 포함한다.
- Windows Node v24.15.0: `npm run api:check`·`npm run lint`·`npm run typecheck`·`npm test -- --run`·`npm run build` 모두 성공. **8파일 241개 통과**, 실패·건너뜀 0, JS gzip **94.25 kB**. 이번에는 npm ci·변이 검사를 재실행하지 않았다. 재현 도구 추가 후 node --check·Lint도 성공.
- 사용자가 알려준 Ubuntu 기존 키 파일을 값 출력 없이 사용해 실제 Kakao용 backend(8081, 별도 메모리 DB)·웹(5173)을 실행했다. 사용자가 직접 1계정 로그인해 병동 목록·소속 없음 화면 진입 성공. 합성한 없는 초대 토큰의 실제 오류 안내와 fragment 제거도 확인했다. 키 파일·실제 계정 정보를 문서에 복사하지 않았다.
- 사용자가 두 번째 Kakao 계정이 없다고 알려, 별도 루프백 OIDC 테스트 공급자(18090)·실제 backend(18091, 별도 메모리 DB)·웹(5174)에서 가상 관리자/간호사/추가 관리자 3사용자 흐름을 검증했다. 실제 Spring 인증·JWT 서명/state/nonce·초대 API·DB를 사용하고 외부 공급자만 가상이다. 실제 계정 서버와 쿠키 이름도 분리했다. 실제 Kakao 2계정 시연 완료로 주장하지 않는다.
- 브라우저 결과: 기록 연결 발급·목록·현재 origin 웹 링크 복사→실제 Ctrl+V·재발급과 원본 철회·로그아웃 후 링크 진입→다른 가상 사용자 로그인→초대 복귀·미래 연월 오류·정상 간호사 가입·관리자 측 가입 완료. 연결 없는 초대 발급/철회 후 패널의 복사 버튼 제거, 관리자 초대의 연월 입력 없음·추가 관리자 가입·초대 관리 권한까지 확인했다. 사용된 링크 재진입의 오류와 닫기, 390×844 화면의 문구/버튼·가로 넘침 없음도 확인하고 viewport를 원복했다.
- 임시 공급자를 `web/scripts/b3b-browser-fixture.mjs`로 보존했다(모듈 import 정리, 검증 로직 동일). 개발 전용·루프백·가상 계정/키이며 앱 번들에 포함되지 않는다. 실제 API로 시드하는 재현 명령과 실제/가상 구분은 `web/B3b-verification.md`에 있다. 신규 앱 결함은 발견하지 않았으며 운영 앱·서버 코드·API/DB 스키마는 수정하지 않았다.
- 남은 조건: 실제 Kakao 2계정 가입은 두 번째 계정 부재로 미실행. 저장소 값 직접 읽기·Referer·전체 히스토리·권한 차단·실제 만료/응답 유실·연결 없는 간호사 가입 등은 이번 브라우저 검증에서 확인하지 않았다. 목업 테스트를 실제 외부 계정 검증으로 대신 기록하지 않는다. Draft PR에 이 조건을 명시한다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-invite-join`에서 개발용 재현 도구·두 검증/현황 문서만 추가 커밋·푸시하고 Draft PR을 게시한다. backend·OpenAPI diff 없음, `git diff --check` 통과. 서버 8081/18091·웹 5173/5174·가상 공급자 18090과 각각의 브라우저 탭은 검증 후 실행 상태다. 메모리 DB라 종료 시 시연 데이터가 사라진다. 사용자 DB·local.properties·Ubuntu 키 파일 보존, 병합·배포 없음.

## B3b Draft PR #45 게시 (2026-11-12, Codex)

- 검증 기록·개발용 가상 OIDC 도구를 `2ee90bb`에 커밋·푸시하고 [PR #45](https://github.com/gerrmang/NurseFlow/pull/45)를 `gh`로 생성했다. `OPEN`, `isDraft=true`, 대상 `dev`, 게시 head `2ee90bbb6af0a7651b3573cf3c6206531d290c45`를 확인했다. 실제 Kakao 2계정 인수 검증 미완료를 Draft 사유로 명시했다.
- 게시 직후 [Verify 43225192104](https://github.com/gerrmang/NurseFlow/actions/runs/43225192104)의 Backend tests·Android debug build·Web checks는 실행 중이었다. 최종 문서 커밋의 CI는 해당 head로 별도 확인한다. CI 통과·독립 승인·병합 준비 완료를 주장하지 않는다.
- 같은 공유 worktree·`feat/web-invite-join`에서 이 게시 기록만 후속 커밋·푸시한다. 기존 검증 후 앱 코드는 바뀌지 않아 자동 검사·브라우저 검사를 재실행하지 않았다. `git diff --check` 통과, 미추적 사용자 DB 보존. 실제 2계정이 준비되면 본문 5절의 외부 계정 인수 검증을 이어가며 병합·배포는 하지 않는다.

## B3b PR #45 일반 PR 전환 (2026-11-12, Codex)

- 사용자 요청으로 `gh pr ready 45`를 실행했다. GitHub API에서 `draft=false`, 대상 `dev`, head `ee6e78a`를 확인했다. PR 본문의 Draft 유지 문구를 갱신하고 실제 Kakao 2계정 가입·나머지 미검증 항목은 그대로 명시했다. 실제 검증 완료나 병합 승인을 의미하지 않는다.
- 코드 구현과 실행 가능한 검사는 앞 기록대로 완료했다. 새로운 기능 수정은 없고 문서만 변경해 로컬 테스트는 재실행하지 않았다. 전환 확인 시 `ee6e78a`의 Verify 43225366631은 Backend·Android·Web 실행 중이었으며 후속 문서 head의 CI는 별도 확인한다.
- 같은 worktree·`feat/web-invite-join`에 전환 기록을 커밋·푸시한다. 미추적 사용자 DB를 보존하고 일반 PR 리뷰 대기로 인계한다. 미검증 외부 2계정 시연을 완료로 표시하거나 PR을 병합·배포하지 않는다.

## 리뷰 상태

- PR #30의 `RealBackendSessionDeviceTest`는 `IsolatedSessionTestRunner`와 테스트 전용 `Application`/`TokenStore`를 사용한다. 앞서 기록된 “개발자 저장 토큰에 접근할 수 있다”는 P1은 이 코드와 맞지 않아 철회했다. 실제 사용자 세션을 사용하지 않는다는 검증 기록은 [`B1 검증 기록`](../android/B1-verification.md)을 참조한다.
- PR #32의 이전 head `5d4f45b`에서 발견한 사항은 수정 head `f10d598`에 반영했다. PR #30 P1 지적은 테스트 전용 runner를 고려하지 않은 오류로 철회했다. `.gitignore`의 광범위한 `.codex/` 제외를 제거하고, Claude·Codex 공통 worktree 규칙과 누락된 Claude 문서 갱신, breakdown의 A1/B1 병합 상태를 추가했다.
- PR #34 (`209de0db506429050885968e9cd2c3d81cc14192`) Codex COMMENT 리뷰를 2026-11-10 게시했다. P3 1건: 기본 MINE 모드는 간호사 기록이 없는 `WARD_ADMIN`에게 서버의 `NURSE_IDENTITY_REQUIRED`를 일반 권한 오류로 보여준다. 전체 조회 권한이 있으면 우회 가능하며 보안 문제는 아니다.

- B1(#30)과 지침 정합성(#32)이 일반 병합 커밋으로 들어갔다. 이후 기능 PR은 GitHub의 "Squash and merge"로 병합한다.

## B9a 인계 (2026-11-09)

- **구현**(`android/**`만): `NurseFlowApi`에 `published-rosters`·`/mine` 조회와 DTO, `feature/roster/PublishedRosterViewModel`·`PublishedRosterScreen`, 병동 카드 선택 → 근무표 화면(`NurseFlowRoot`). 병동 전체는 근무 시간대(병동 `timeZone`)로 표시하며 D/E/N을 추측하지 않는다. `canViewWardCalendar`가 false면 "병동 전체" 탭을 숨긴다. 본인 assignments가 비면 "이번 달 배정된 근무가 없습니다", `PUBLISHED_ROSTER_NOT_FOUND`면 "아직 확정된 근무표가 없습니다"+대상 월로 구분한다. 오류는 `code` 기준(비소속 `WARD_NOT_FOUND` 404는 공개 없음으로 취급하지 않음). 월·모드 요청마다 ticket으로 늦은 응답을 폐기한다. 세션 변경 시 `SessionViewModelStore`가 ViewModel을 비우고 선택 병동도 세션 식별자별로 초기화된다.
- **DemoRoster 분리**: 가상 시연 진입은 로그인 상태(`SignedIn`)가 아닐 때만 노출되고 실서버 조회 실패 시 자동 대체가 없다. (로그인 전 화면 마커로 쓰는 기존 `ActivityRotationDeviceTest`가 복원 중에도 버튼을 기대해 `SignedIn` 외 상태에서 표시하도록 유지.)
- **권한 구분**: `mine`(멤버십만)과 전체 조회(`canViewWardCalendar`)의 권한 차이는 정상적인 권한 구분이며 계약 결함이 아니다.
- **계약 관찰(수정하지 않음)**: 병동 전체 슬롯에는 `shiftTypeId`·시간만 있고 근무 코드가 없으며, `GET /wards/{id}/shift-types`는 OpenAPI에만 있고 backend에는 구현이 없다. 코드명 표시는 후속 계약이 필요하다.
- **실행·결과**(`android/`, 2026-11-09):
  - `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`: `BUILD SUCCESSFUL`. JVM **36개 통과, 실패/오류/건너뜀 0**(신규 `PublishedRosterViewModelTest` 9개 + `ApiIntegrationTest` 1개 추가). 신규 검사: 성공, 본인 빈 배정과 공개 없음 구분, 전체/본인 전환, 권한 없는 전체 탭 차단, 월 이동 중 늦은 응답 폐기, 모드 전환 중 늦은 응답 폐기, 403·네트워크 재시도, 비소속 404, 저장소 정리 후 결과 미반영, 경로·쿼리·오류 코드.
  - 별도 읽기 전용 `Pixel_10` 에뮬레이터(`-read-only`, 사용자 데이터 이미지 미사용, `emulator-5556`)에서 `:app:connectedAndroidTest`: **22개 실행, 건너뜀 3(opt-in), 실패 0**. 기존 `IsolatedSessionTestRunner`로 개발자 저장 세션을 쓰지 않는다. 첫 실행에서 시연 버튼 숨김 때문에 `ActivityRotationDeviceTest` 2개가 실패해 표시 조건을 고친 뒤 통과했다.
  - `git diff --check`: 통과(CRLF 변환 경고만).
- **Codex 리뷰 P2 수정(같은 병동 재진입 시 오래된 상태 유지)**: `NurseFlowRoot`가 병동 선택 때마다 `wardEntry`(세션 식별자별 `rememberSaveable`)를 올리고, `PublishedRosterScreen`이 `LaunchedEffect(entry)`로 `PublishedRosterViewModel.enter(token)`을 호출한다. 첫 진입은 init의 조회가 처리하고, 같은 token(recomposition·Activity 재생성)은 재조회하지 않으며, 새 진입(token 변경)만 현재 월·모드로 재조회한다. 기존 요청 취소·늦은 응답 폐기·세션 격리 로직은 그대로다.
  - 재실행 `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`: `BUILD SUCCESSFUL`. JVM **39개 통과, 실패/오류/건너뜀 0**(`PublishedRosterViewModelTest` 12개). 신규 회귀 3개: 공개 없음 → 서버 공개 → 재진입 시 공개본 표시와 중복 요청 없음, 기존 배정 → 재공개 → 재진입 시 새 배정으로 교체(월·모드 유지), 재진입 전 요청의 늦은 응답 폐기. 이번 수정 후 기기 `connectedAndroidTest`와 `git diff --check`는 재실행하지 않았다(직전 diff 검사는 통과). 실서버 미검증 상태는 유지한다.
- **생략한 검사와 이유**: (1) **실서버 공개 전·후 조회·전체/본인 전환·월 이동**: `backend/**`를 수정할 수 없고 기존 opt-in fixture(`AndroidSessionE2eServerTest`)는 공개 근무표를 시드하지 않아 실행하지 못했다. 위 결과는 모두 목업(MockWebServer·가짜 API)이다. (2) B9a 전용 기기 UI 계측(NotPublished·탭 전환 화면)은 추가하지 않았다. (3) Lint·GitHub CI는 실행하지 않았다.
- **남은 일**: 공개 근무표를 시드한 가상 서버(관리자·간호사 가상 계정)로 공개 전 404 → 공개 후 `mine`/전체 → 월 이동을 실기기에서 확인한 뒤 완료 표시. 같은 worktree·브랜치(`feat/android-published-roster`)에서 이어가며, 필요 시 backend fixture 확장은 별도 단위로 합의한다.

## B9a Codex 로컬 리뷰 (2026-11-09)

- 검토 범위: 현재 B9a tracked diff와 미추적 `feature/roster` 구현·테스트, 세션 ViewModel 소유 구조, 서버 공개 조회 DTO·권한, 기존 opt-in fixture. 애플리케이션 코드는 수정하지 않았다.
- **최초 지적 P2 [데이터/상태], 아래 재검토에서 해결 확인**: `NurseFlowRoot.kt:61`은 세션 저장소에서 병동별 ViewModel을 재사용하지만, 조회는 `PublishedRosterViewModel`의 최초 생성·월/모드 변경·오류 재시도 때만 했다. 병동 목록으로 나갔다가 같은 병동에 재진입해도 서버를 다시 조회하지 않아, 그 사이 공개·재공개한 근무표 대신 이전 배정 또는 `NotPublished` 상태가 유지됐다.
- 검증: `git diff --check` 직접 실행 통과(줄바꿈 변환 경고). 기존 JVM XML 보고서 4개에서 합계 36개, 실패/오류/건너뜀 0 확인. 이번 리뷰에서 Gradle·기기 검사는 재실행하지 않았으며, 앞선 구현자의 실행 기록과 구분한다.
- 실서버 검증은 여전히 미완료다. 기존 `AndroidSessionE2eServerTest`는 계정별 별도 병동과 멤버십만 생성하고 간호사 연결·근무표는 생성하지 않는다. 격리된 가상 데이터로 공개 전·후 및 본인/전체·월 이동 검증을 준비해야 한다. backend fixture 확장은 기존 Android 소유 범위를 넘으므로 별도 범위 합의가 필요하다.
- 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-published-roster`에서 Claude가 위 P2를 수정하고 검증한다. 실서버 확인 전 완료 표시 금지. 커밋·푸시·PR 생성·병합하지 않았다.
- 리뷰 기준: HEAD `ab8347dde6464379c8670cde6f937f408d349eb8`; tracked diff SHA256 `b6cfd8aeeee2a94953bf29143c855f9043dafb47cfb5206ba0b699e4a44e11f7` (`git diff HEAD -- . ':!docs/work-status.md'` 원본 출력). 미추적 파일은 이 diff에 포함되지 않으므로 별도 SHA256도 기록한다: `PublishedRosterScreen.kt` `1b5560d302ad335e5f5148611f256463b26946333ffb809ebe2a200c53cd46fe`, `PublishedRosterViewModel.kt` `68764bd17b7c0d53bb088f829222e3b8e169d9736b39c83a88a032f9ae680558`, `PublishedRosterViewModelTest.kt` `715817372f3aece99706eaa901384642310603459db9af93a0584aaf2492d5e6`. 검토 주체 Codex.

## B9a P2 수정 재검토 (2026-11-09, Codex)

- `wardEntry` 증가 → `LaunchedEffect(entry)` → `enter(token)` 연결과 회귀 테스트 3개를 확인했다. 첫 진입·동일 token에서는 중복 조회하지 않고, 새 진입에서는 현재 월·모드를 유지해 재조회한다. 기존 P2는 해결됐으며 이번 수정 범위에서 추가 조치할 결함은 발견하지 못했다.
- 기존 JVM XML 보고서 합계 39개, 실패/오류/건너뜀 0을 확인했다. Codex가 현재 수정 상태에서 `git diff --check`를 직접 실행해 통과했다(줄바꿈 변환 경고만). Gradle·기기 검사는 이번 재검토에서 재실행하지 않았다. 수정 후 기기 검사 및 실서버 공개 전·후 검증은 여전히 미완료다.
- 같은 worktree·브랜치에서 기기 검사와 격리된 가상 서버의 실연동 검증을 이어간다. 애플리케이션 코드 수정·커밋·푸시·병합 없음.
- HEAD `ab8347dde6464379c8670cde6f937f408d349eb8`; tracked diff SHA256 `66410e40794e40222067c249c692fc76ca704e07ecc39f8383e5f4768eb9e2cf` (앞선 리뷰와 같은 방식). 미추적 파일 SHA256: `PublishedRosterScreen.kt` `e7e70e622e14b1fbd21ac46aaab04b3e08581e67c519b5c30a556105a83ae9e4`, `PublishedRosterViewModel.kt` `56a4af766f076ec972a78ee20ba7129787d411a8bebb9fc1da0f53f52190d9ef`, `PublishedRosterViewModelTest.kt` `42e387e91583fa228eb6c844055d9e067bcd0eb2356c958f5e7acf75203d9e16`.

## B9a 최종 로컬 검증 (2026-11-09, Codex)

- 사용자의 마무리 지시에 따라 같은 공유 worktree·브랜치를 이어받았다. 검증을 위해 `backend/src/test/**`에 별도 opt-in `AndroidRosterE2eServerTest`, `android/app/src/androidTest/**`에 `PublishedRosterDeviceTest`를 추가했다. 서버 운영 코드·API·DB 스키마는 변경하지 않았고, 기존 B1 fixture도 보존했다. 사용자 로컬 DB와 저장 세션은 사용하지 않았다.
- 실제 Spring 서버(loopback 18083), H2 메모리 DB, 가상 OIDC/계정과 실제 Android 화면을 연결했다. 공개 전 404/안내 → 공개 후 재진입 → 본인/전체 전환 → 월 이동 → 재공개 후 같은 월·모드 재진입 → 본인 배정 없음 → 로그아웃/권한 제한 계정 전환 → 전체 403·탭 숨김/본인 조회 성공을 확인했다.
- Android 단위 검사·앱/테스트 APK 빌드·Lint 명령 성공. 단위 검사는 `UP-TO-DATE`이며 XML 39개·실패/오류/건너뜀 0 확인. Lint 오류 0·기존 경고 33개. 수정 후 일반 기기 검사 성공: XML 23개 중 18개 통과·5개 opt-in 건너뜀·실패/오류 0. 이후 B9a opt-in을 별도 실행해 `OK (1 test)`, 11.480초와 서버 fixture 1개 통과를 확인했다.
- 백엔드 전체 검사 `test --no-daemon`: 91개 중 89개 통과·2개 opt-in 건너뜀·실패/오류 0. 이후 별도 opt-in 필터 실행도 `BUILD SUCCESSFUL`이다. 초기 fixture 반환 타입 컴파일 오류와 기기 테스트의 화면 전환 대기 오류를 수정한 뒤 최종 실행이 통과했다. 상세 재현·기존 보고 수치와의 구분은 [B9a 검증 기록](../android/B9a-verification.md)에 있다.
- 앞선 인계의 실서버 미검증 제한은 이번 검증으로 해소됐다. B9a 구현·로컬 인수 검증은 완료했으며 PR/CI/병합은 별도다. 외부 Kakao 인증 성공·운영 서버·물리 기기, GitHub CI·보호 설정은 이번에 검증하지 않았다.
- 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-published-roster`의 미커밋 변경을 이어간다. 커밋·푸시·PR 생성·병합하지 않았다. 테스트 fixture는 앱/서버 배포에 포함되지 않으며 롤백은 B9a 앱 변경을 되돌리면 된다.
- 최종 테스트 대기 보완 후 `:app:lintDebug`도 재실행해 성공했다. 검증 서버 정상 종료와 추가한 reverse 포트 18083/18084 제거, 이번에 시작한 읽기 전용 에뮬레이터 종료를 확인했다. 신규 기기 테스트 SHA256 `d2109000511e3917ed2f2f34e615e3ec51c0fb6d81e1c76d8236dfdea65d7a8f`, 신규 서버 fixture SHA256 `5cefdf9031fdb6e06e009df36c1b3374c4bb0de7c8c8634461e100da5fee8935`; 나머지 구현·단위 테스트의 해시는 위 P2 재검토 기준과 같다.

## B9a PR 개설 (2026-11-09, Codex)

- 사용자 지시에 따라 B9a 파일 12개만 커밋·푸시하고 [PR #34](https://github.com/gerrmang/NurseFlow/pull/34)를 `dev` 대상으로 열었다. 원격 `dev`의 A2 스쿼시 커밋 `fa47a5a`에 rebase했고, 공유 상태 문서 충돌은 A2의 최신 기록과 B9a의 기록을 모두 보존해 해결했다. PR 본문에 **Squash and merge**를 명시했으며 병합·자동 병합 설정은 하지 않았다.
- A2 통합 후 `backend/ .\gradlew.bat test --no-daemon`: `BUILD SUCCESSFUL`, XML 109개 중 107개 통과·2개 opt-in 건너뜀·실패/오류 0. 신규 파일까지 포함한 `git diff origin/dev...HEAD --check`에서 발견한 화면 파일 EOF 공백은 커밋 전에 정리했다.
- PR 구현 head `f4cd1ed64a19097973be1cbff0dcf7066391daad`에서 GitHub Actions [Verify run 42613579824](https://github.com/gerrmang/NurseFlow/actions/runs/42613579824)의 `Backend tests`(1분)·`Android debug build`(1분 14초) 성공을 확인했다. Vercel 및 Vercel Preview Comments도 성공. 강제 보호·승인 규칙은 확인하지 않았다.
- A2 통합 상태의 B9a 실서버 기기 E2E를 재실행해 `OK (1 test)`, 13.549초, 테스트 status 0과 대응 서버 `BUILD SUCCESSFUL`을 확인했다. 최종 문서 갱신 뒤 후속 CI도 확인한다. 병합은 하지 않는다.
- GitHub 연결 도구는 PR 생성에서 403을 반환해, 정상 인증된 로컬 GitHub CLI로 생성했다. 로컬 DB 파일은 미추적 상태로 보존했다. 같은 worktree·브랜치에서 PR #34의 후속 검토를 이어간다.

## B9a PR 리뷰 P3 수정 (2026-11-10, Codex)

- 리뷰 기준: PR #34의 `209de0db506429050885968e9cd2c3d81cc14192`, gerrmang의 COMMENT 리뷰 1건. 간호사 연결 기록이 없는 관리자에게 `NURSE_IDENTITY_REQUIRED` 403을 일반 권한 거부로 안내하던 P3를 확인했다.
- `RosterContent.NurseIdentityRequired`를 추가하고 일반 403보다 먼저 오류 코드로 분기한다. 화면에는 "본인 근무표에 연결된 간호사 정보가 없습니다."를 표시하고, 전체 조회 권한이 있을 때만 "병동 전체 보기" 버튼을 제공한다. 전체 조회 권한 검사와 서버 판정은 유지한다.
- 신규 단위 회귀 2개: 간호사 정보 미연결과 권한 거부 구분 및 전체 전환, 정보 미연결이어도 전체 권한 없는 계정은 전체 API 미호출. 기존 실서버 기기 시나리오에 간호사 기록 없는 관리자 로그인·실제 오류 코드·정확한 안내·전체 보기 성공을 추가했다. 서버 코드·API·DB·환경 설정·의존성 변경은 없다.
- Android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: 성공. XML 41개 통과, 실패/오류/건너뜀 0. Lint 오류 0·기존 경고 33개. `:app:connectedAndroidTest` 성공: XML 23개 중 18개 통과·5개 opt-in 건너뜀·실패/오류 0. 이후 B9a opt-in 실서버 기기 검사 `OK (1 test)`, 11.790초, 테스트 status 0과 대응 서버 `BUILD SUCCESSFUL` 확인. 관리자 정보 미연결 안내와 전체 보기 버튼이 실제 서버 응답으로 동작한다.
- opt-in 서버 검사가 캐시 결과를 재사용하는 것을 확인해, 실제 실행이 필요한 재현 명령에 `test --rerun`을 명시했다. 같은 worktree·브랜치에서 PR #34를 갱신하며, 로컬 DB는 보존하고 스쿼시 병합은 수행하지 않는다.
- `git diff --check` 통과. 백엔드 전체 검사는 서버 코드 변경이 없어 재실행하지 않았고, 실서버 경로는 위 opt-in 서버 검사로 검증했다. 외부 Kakao 인증·운영 서버·물리 기기는 이번에 검증하지 않았다. 수정 후 독립 재리뷰는 아직 받지 않았다.

## B3a 인계 (2026-11-10, Claude)

- **작업 공간:** 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, 브랜치 `feat/android-invite-join`, 분기점 `23af850`(= `origin/dev`). 검증된 B3a 변경을 첫 커밋으로 `origin/feat/android-invite-join`에 푸시했다. 진행 순서(사용자 확정)는 B3a 첫 커밋·푸시 → B2 실제 Kakao·브라우저 검증과 커밋 → 이 브랜치로 돌아와 B3a 실제 검증이다.
- **보존한 미추적 항목:** `nurseflow.mv.db`와 `web/node_modules`(B2 브랜치에서 무시되던 잔여물)는 B3a 범위가 아니다. 커밋에 넣지 않는다.
- **B2 기록:** B2 브랜치(`feat/web-foundation-session` `767abd6`)의 이 파일 인계 기록은 dev에 없으므로 이 브랜치에 가져오지 않았다.
- **결정 (사용자 확정):**
  - §7 #6: 개발용 `nurseflow://invite#token=`과 명시적 붙여넣기를 쓴다. 운영 HTTPS App Links는 후속이다.
  - §7 #7: 역할 기반 고정 대상 문구를 쓰고 메모는 없다.
  - §7 #21: A 소유 테스트 전용 `backend/src/test/.../AndroidInvitationE2eServerTest.kt` 1개를 B3a 범위로 승인받았다.
  - 공통 정책 문서 반영은 별도 결정 반영 문서 PR에서 한다.
- **계획과 달라진 점:** `MainActivity`를 `singleTask` 대신 `singleTop`으로 했다. Kakao 로그인 중인 Activity가 지워질 위험과 기존 `ActivityScenario` 테스트와의 비호환 때문이다. 이유와 검증은 검증 기록에 있다.
- **문서와 코드 차이 (후속 A 또는 문서 PR):**
  - OpenAPI의 WARD_ADMIN `hospitalJoinedOn` 서술
  - redeem 오류 목록의 `INVALID_HOSPITAL_JOIN_MONTH`·`USER_NOT_FOUND` 누락
  - `backend/README.md`의 `expiresInHours` 서술
  - §7 #8 미결 표기 (구현은 재발급 시 원본을 철회함)
- **다음 단계:**
  1. PR 생성은 별도 승인 후. GitHub CI 확인
  2. Codex 리뷰 (P1/P2 수정)
  3. 오류 상태 화면 캡처 (가상 데이터)
- **실제 Kakao 검증 (2026-11-10 저녁):** 사용자 실제 계정 1개, 임시 에뮬레이터·임시 DB, 가상 병동·초대. 결과는 검증 기록의 같은 이름 절. 서버 로그 WARN·ERROR 0, 토큰 0회. logcat의 시드 토큰은 검증용 adb 명령(adbd)에만 있었다.
- **미검증:** 두 실제 계정 전환, KakaoTalk 앱 로그인, 클립보드 복사·가림, 실제 메신저의 사용자 스킴 링크 표시, 물리 기기, 화면에서의 명시적 로그아웃 시 대기 초대 삭제(기기 테스트로만 확인).

## B4a 인계 (2026-11-11, Claude)

- **작업 공간:** 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, 브랜치 `feat/android-ward-create`, HEAD `0ed5b4c`(= `origin/dev`). 커밋·푸시·PR은 하지 않았다.
- **미커밋 상태:** `android/` 안의 수정 6개(`AppContainer`, `ApiFactory`, `LoginScreen`, `WardListScreen`, `WardsViewModel`, `NurseFlowRoot`)와 신규 `WardCreationApi.kt`, `feature/ward/create/`(조회 검증·실패 분류·생성 의도·ViewModel·화면), 테스트 5개 파일, `android/B4a-verification.md`, 이 문서의 변경이 작업 트리에 있다. `backend/**`와 `docs/openapi.yaml`은 변경하지 않았다(`git diff --stat origin/dev` 비어 있음).
- **보존한 미추적 항목:** `nurseflow.mv.db`는 변경·커밋하지 않았다. `local.properties`도 건드리지 않았다.
- **사용자 보완 반영:** 생성 화면 ViewModel은 고정 키 하나와 `reset()`으로 수명을 관리한다. 생성 직후 `WardsViewModel.onCreated`로 새 병동을 즉시 목록에 합쳐 이동 버튼이 목록 로드를 기다리지 않는다. 로그인 후 병동 만들기 의도는 순수 함수로 결정하며 초대가 우선하고 한 번 소비한다. 응답 유실은 (a) 처리 전 끊김과 (b) 생성 후 응답 유실을 분리해 MockWebServer fake로 재현했다.
- **남은 일 (PR 병합 전 필수):** 공식 병원 데이터를 적재한 `dev` 백엔드로 실서버 검색 → 생성 → 목록 갱신 확인, 실제 Kakao 로그인 경로, 기기·회전 확인, 응답 유실 (a)(b)의 실서버 확인. 절차는 [B4a 검증 기록](../android/B4a-verification.md)의 "실서버 연동 절차"에 있다.
- **계약 관찰:** 서버·계약 문제는 발견하지 못했다. 서버 중복 방지는 같은 계정·병원·이름이므로, 키를 잃은 뒤(프로세스 종료·재진입) 같은 내용으로 다시 제출하면 `WARD_ALREADY_CREATED`로 안내된다. 앱은 마지막 초안의 키만 기억한다.

## B4a Codex 로컬 리뷰 (2026-11-11 19:08 KST)

- 검토 범위: B4a 미커밋 Android 구현·신규 테스트·검증 문서. 키 재사용, reset 세대 차단, 생성 응답의 목록 병합, 세션·초대·화면 이동을 대조했다. 애플리케이션 코드는 수정하지 않았다.
- P3(런타임/UI): `WardCreateScreen.kt`의 이름 입력 단계는 스크롤 없는 바깥 Column에 병원 정보·입력·유실 안내·오류·하단 버튼을 모두 놓는다. 작은 화면/가로 모드/키보드 표시/큰 글꼴에서 내용이 가용 높이를 넘으면 생성·재시도·목록 확인 버튼에 스크롤로 접근할 수 없다. 이름 단계에 스크롤과 키보드 인셋을 적용하고 짧은 화면에서 확인해야 한다. 기기에서 재현한 결과는 아니며 레이아웃 구조에 대한 정적 판정이다.
- P3(런타임/UI): 생성 422 `HOSPITAL_NOT_FOUND`는 선택을 지우고 SEARCH로 이동하면서 `createError`에만 오류를 보관한다. SearchStep은 `createError`를 표시하지 않아 재검색 안내가 사라지고 기존 결과가 남는다. 검색 단계에 안내를 표시하고 새 검색/후보 선택 때 해제해야 한다.
- 검증: `android/`에서 `.\gradlew.bat :app:testDebugUnitTest --offline --no-daemon` 성공. 기본 샌드박스의 Gradle 잠금 파일 접근 거절 후 승인된 실행에서 성공했으며, 모든 작업은 UP-TO-DATE로 테스트 본문은 재실행되지 않았다. 기존 XML 15개에서 tests=133, failures=0, errors=0, skipped=0을 직접 집계했다. `git diff --check` 통과(줄바꿈 안내만), backend/OpenAPI tracked diff 없음.
- 검증 한계: 빌드·Lint·기기·실서버·실제 Kakao는 이번 리뷰에서 실행하지 않았다. `recompositionOrRotationKeepsStateBecauseTheSameModelSurvives`는 동일 인스턴스의 상태를 읽는 JVM 검사로 Activity 회전/Compose 연결을 검증하지 않는다. 새로 추가된 422 테스트도 상태만 확인하며 실제 오류 문구 노출은 검사하지 않는다. P1/P2는 검토 범위에서 발견하지 못했다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-ward-create`, HEAD `0ed5b4c2bb7443d719180433c3593ad9c3b579ba`. 기존 변경과 미추적 DB를 보존했다. 위 P3 수정·재리뷰와 공식 데이터 적재 후 실연동을 이어간다. 커밋·푸시·PR 게시·병합·배포 없음.
- 리뷰 기준 해시: B4a 소스·테스트·검증 문서(공유 현황 문서·DB 제외)의 경로별 SHA256 목록을 경로 정렬·LF 연결한 UTF-8 바이트의 SHA256은 `835e066a515053af75a32a848bbbded0a01a4aa6b82339b63c2307fe1a194370`이다. 미추적 파일도 포함한다.

## B4a Codex P3 수정 인계 (2026-11-11, Claude)

- **수정:** (1) `SearchStep`·`NameStep`을 각각 하나의 스크롤 컨테이너로 바꿔 작은 화면·큰 글꼴·유실 안내에서도 생성·재시도·병동 목록 확인 버튼에 닿도록 했다. 검색 결과의 `LazyColumn` 중첩은 없앴다. 생성 중 잠금은 유지했다. (2) 422 `HOSPITAL_NOT_FOUND` 안내를 검색 단계에 표시하고, 거절된 병원은 `invalidHospitals`로 기록해 다시 고르지 못하게 했다. 안내는 새 검색이 시작되거나 다른 병원을 고를 때 해제된다.
- **검증(2026-11-11, `--rerun-tasks`로 80개 작업 모두 실제 실행):** `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` `BUILD SUCCESSFUL`. 단위 140개 통과(실패·오류·건너뜀 0, 신규 7), Lint 오류 0·경고 35개(+2는 테스트 의존성 버전 안내), `git diff --check` 통과, backend·`docs/openapi.yaml` diff 없음.
- **미실행:** `WardCreateScreenUiTest`(Compose UI, 422 안내 노출·짧은 화면 스크롤·클릭)는 작성하고 APK로 컴파일만 했다. 기기가 없어 실행하지 못했으므로 UI에서는 미검증이다. 신규 단위 테스트는 상태만 확인한다. 기존 회전 테스트는 같은 ViewModel의 상태 유지 검사이며 실제 Activity 회전 검증이 아니다.
- **작업 공간:** 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-ward-create`, HEAD `0ed5b4c`(미커밋). `nurseflow.mv.db`·`local.properties`는 건드리지 않았다. 커밋·푸시·PR은 하지 않았다.
- **남은 일:** Codex 재리뷰. PR 병합 전: 기기에서 `WardCreateScreenUiTest` 실행과 키보드·회전 확인, 공식 데이터 적재 후 실서버 검색→생성→목록 갱신, 실제 Kakao, 응답 유실 (a)(b) 실서버 확인. 절차는 [B4a 검증 기록](../android/B4a-verification.md).

## B4a P3 수정 Codex 재리뷰 (2026-11-11 19:35 KST)

- 이전 P3 2건의 수정 범위(`WardCreateScreen`, `WardCreateViewModel`, 신규 상태 테스트 7개·Compose UI 테스트·테스트 의존성·검증 문서)를 정적으로 대조했다. 검색/이름 단계를 각각 높이가 제한된 단일 스크롤 영역으로 바꾸고, 검색 결과의 중첩 LazyColumn을 제거했다. 422 안내가 SEARCH에서 렌더링되고, 거절한 후보는 UI와 ViewModel 모두 재선택을 차단한다. 새 검색 시작/다른 후보 선택 시 안내 해제와 LIMIT_REACHED 보존도 확인했다. 이전 P3 2건은 코드상 해결됐으며 이번 수정 범위에서 추가 조치할 결함은 발견하지 못했다.
- 검증: 기존 XML 직접 집계 tests=140, failures=0, errors=0, skipped=0. `git diff --check` 통과(줄바꿈 안내만). backend/OpenAPI tracked diff 없음. 이번 재리뷰에서 Gradle·빌드·Lint·기기·실서버 검사는 재실행하지 않았다. 140개는 기존 보고서 확인이며 이번 직접 실행 결과가 아니다.
- 새 UI 테스트는 422 안내·다른 후보 선택과 320dp×180dp 영역의 하단 버튼 스크롤/클릭을 검사하도록 작성됐으나 실행 결과는 없다. 실제 IME·큰 글꼴·Activity 회전은 이 테스트만으로 검증되지 않는다. 기기 UI 검사와 공식 데이터 적재 후 실서버 검색→생성→목록 갱신 등 기존 미검증 항목은 그대로 남는다.
- 인계: 같은 공유 worktree와 `feat/android-ward-create`, HEAD `0ed5b4c2bb7443d719180433c3593ad9c3b579ba`의 미커밋 변경을 유지한다. 앱 코드는 변경하지 않고 이 재리뷰 기록만 추가했다. 커밋·푸시·PR 게시·병합·배포 없음. 기준 SHA256: `WardCreateScreen.kt`=`923e99ba27d8ec8197f252696a5522e197aff03858b6580fa457b3ebe6a7c2f8`, `WardCreateViewModel.kt`=`075d6fb2d691ddf06b77f3228dfba9b6def89352ca2eb80d3907304e2ed68b8a`, `WardCreateScreenUiTest.kt`=`5c398e21494aded94d41628b2f3ef6c9e5891c1e08529cf0ba99a1f1a0159011`.

## B3b 인계 (2026-11-12, Claude)

- **작업 공간:** 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, 브랜치 `feat/web-invite-join`, 기준 HEAD `0ed5b4c2bb7443d719180433c3593ad9c3b579ba`(= `origin/dev`). 원격 추적은 해제했다(`dev`로 잘못 푸시되지 않도록). **커밋·푸시·PR은 하지 않았고 모든 변경이 미커밋 상태다.**
- **B4a 보존:** `feat/android-ward-create`(`d7ce955`, origin과 동일)에서 `git switch -c feat/web-invite-join origin/dev`로 분기했다. B4a 변경·커밋은 이 브랜치에 없고 `git log origin/dev..HEAD`는 비어 있다. 미추적 `nurseflow.mv.db`는 건드리지 않았다.
- **변경 범위:** `web/` 안(신규 `src/invite/*`, `src/pages/JoinInvitePage.tsx`, `InvitationAdminPage.tsx`, 테스트·가짜 서버 파일, 수정 `App.tsx`·`main.tsx`·`KakaoCallbackPage.tsx`·`LoginPage.tsx`·`WardsPage.tsx`·`nurseflowApi.ts`·`types.ts`·`styles.css`·기존 테스트 설정·`README.md`·`B3b-verification.md`)과 이 문서뿐이다. `backend/**`·`docs/openapi.yaml`·공통 정책 문서는 수정하지 않았다.
- **사용자 승인 결정(2026-11-11):** (1) 로그인 왕복용 sessionStorage 제한 저장(초대 토큰 1개·`expiresAt`, 로그인 버튼에서만 저장, 읽기→삭제→검증→복원, 10분은 논리 만료, 삭제 실패 시 자동 복원 중단), (2) 현재 웹 origin으로 `/invite#token=` 링크 직접 생성(서버 `inviteUrl` 미사용). 승인 시 보완 4건(URL 읽기·제거 분리와 StrictMode, 로그인 복귀 1회 소비와 목적지 일원화, 삭제 실패 처리, 응답 유실 후 조회 결과의 정확한 표현)을 모두 반영했다. 공통 정책 문서(`feature-breakdown.md` §7)에는 아직 반영하지 않았다(별도 결정 반영 문서 PR).
- **Codex 리뷰 지점**
  1. `src/invite/pendingInvite.ts`: 저장·소비·폐기 경로(`consumeLoginReturn`의 1회 메모, 삭제 실패 분기, `observeSession`의 소유·폐기 규칙), 저장 항목이 토큰·만료 외에 없는지.
  2. `src/pages/JoinInvitePage.tsx`: fragment 읽기(렌더 중 순수 읽기)와 제거·등록(효과)의 분리, 가입 `Uncertain` 흐름(`verifyOutcome`의 `/wards` → 미리보기 재조회 판정), 늦은 응답 폐기(`current()`), 쓰기 이중 제출 가드.
  3. `src/invite/callbackDestination.ts`와 `KakaoCallbackPage.tsx`: 목적지 결정이 한 곳인지, 기존 로그인 성공·취소·실패 동작이 그대로인지.
  4. `src/pages/InvitationAdminPage.tsx`: 쓰기 뒤 재조회 결과별 문구(`composeNotice`), 발급 토큰이 컴포넌트 state에만 있고 닫으면 사라지는지, 연결 후보 필터(`linkableNurses`).
  5. 서버 코드·detail이 화면에 새지 않는지(`invitationMessages.ts`), 토큰이 URL·router state·로그·DOM에 남지 않는지.
- **알려진 제한·관찰:** `SessionManager.beginEpoch`는 쓰기를 포함한 모든 진행 중 API 요청을 취소하므로 로그아웃·계정 전환 중에는 서버가 처리했지만 화면이 결과를 보지 못할 수 있다(화면은 사라지고 이후 조회로 확인). 계약 관찰 4건(비활성 간호사 기록 가입 시 `ALREADY_WARD_MEMBER`, 관리자 초대의 `hospitalJoinedOn` 형식 검사, §7 #8 미결 표기, B3a 문서 차이 중 3건 해결)은 [검증 기록](../web/B3b-verification.md) 2.4절.
- **남은 일:** Codex 리뷰와 수정. 실연동용 가상 병동·가상 테스트 계정 2개 준비는 병원 CSV와 별개로 확인한다(병동 생성은 공식 병원 데이터 적재가 필요). 실서버·Kakao·실제 브라우저 검증 절차는 검증 기록 5절. 그 뒤 PR(`dev` 대상, 스쿼시 병합)을 연다. B9b 웹 공개 근무표는 B3b 완료·리뷰 뒤 별도 작업이다.

## B3b Codex 리뷰 (2026-11-12 13:17 KST)

- **결과:** P3(런타임) 3건. 수정·재리뷰 전이며 병합 준비 완료로 보지 않는다. 애플리케이션 코드는 수정하지 않았고 커밋·푸시·PR도 하지 않았다.
  1. `web/src/pages/InvitationAdminPage.tsx:375–377,236`: 복사 실패로 수동 링크 A가 표시된 상태에서 재발급 B를 받으면 같은 `IssuedLinkPanel`의 `manualText`가 유지되어, 새 링크 제목 아래 철회된 A가 남는다. 토큰 변경 시 복사 상태를 초기화하고 이전 복사 작업의 늦은 응답도 폐기해야 한다.
  2. `web/src/pages/InvitationAdminPage.tsx:168–170,222`: 선택한 간호사가 다른 초대 발급·연결 등으로 후보에서 빠지고 새로고침하면 화면은 '연결하지 않음'이지만 요청은 이전 `nurseId`를 보낸다. 후보를 확인한 실제 표시값으로 본문을 만들거나 선택 상태를 명시적으로 초기화해야 한다.
  3. `web/src/pages/InvitationAdminPage.tsx:153–161,236`: 현재 발급 패널의 초대를 철회해도 패널·복사 버튼·'24시간 동안 사용할 수 있음' 설명이 남아 무효 링크를 공유할 수 있다. 패널에 invitationId를 연결해 철회·재조회 결과에 따라 숨기거나 무효화하고, 재발급 응답 유실 때의 이전 패널도 점검해야 한다.
- **직접 검증:** WSL `~/nf-b3b/web/src`와 공유 worktree의 `web/src`를 `diff -qr`로 대조해 차이 없음 확인. 해당 사본에서 `npm test -- --run`: **8개 파일 228개 통과**. `npm run api:check`, `npm run lint`, `npm run typecheck`, `npm run build` 모두 exit 0. JS gzip 93.94 kB. `npm ci`는 재실행하지 않았다. `git diff --check` 통과.
- **재현:** WSL 사본에만 임시 `codex-review.test.tsx`를 추가해 위 3개 정상 기대값을 검사했고 **3개 모두 해당 결함으로 실패**했다(수동 입력값 A 유지, 요청에 이전 nurseId 포함, 철회 후 패널 유지). 재현 후 임시 파일은 삭제했다. 공유 worktree에는 테스트나 운영 코드 변경을 남기지 않았다.
- **미검증:** 실제 브라우저·클립보드·Kakao·실서버·두 계정 시연·GitHub CI. 목업 검증으로 이 항목의 완료를 주장하지 않는다.
- **인계 기준:** 경로 `C:\Users\jjang\IdeaProjects\NurseFlow`, 브랜치 `feat/web-invite-join`, HEAD `0ed5b4c2bb7443d719180433c3593ad9c3b579ba`. `docs/work-status.md` 제외 tracked diff SHA-256 `d6b6380d96ae3fbf176d507d319819cb356b547c64a1e4142f24563728adbfb4`. 신규 `web/**` 17개 파일을 경로순 정렬한 `SHA256 + 두 공백 + 경로 + LF` manifest의 SHA-256 `3d9252c003c965e472cb3171ca337bb18399df6c2e72b7fd906130c7c8483f17`. 검토 주체 Codex. 기존 미추적 `nurseflow.mv.db` 보존.

## B3b Codex 리뷰 P3 3건 수정 (2026-11-12, Claude)

- **상태:** 수정 완료, **Codex 재리뷰 대기**. 커밋·푸시·PR 없음. `backend/**`·`docs/openapi.yaml` 변경 0줄. 수정 파일은 `web/src/pages/InvitationAdminPage.tsx`, `InvitationAdminPage.test.tsx`(정식 테스트 9개 추가)와 이 문서·`web/B3b-verification.md`뿐이다.
  1. 발급 패널을 `invitationId`로 key를 줘 재발급 시 새로 마운트(복사·수동 복사 상태 초기화). 한 패널 안에서는 마지막 복사 시도만 화면을 바꾸고, 이전 시도의 늦은 성공·실패는 버린다.
  2. `issue()`가 화면과 같은 기준(현재 후보 목록에 있는 선택만 유효)으로 `nurseId`를 만들어, 후보에서 빠진 간호사는 보내지 않는다.
  3. 발급 패널이 `invitationId`와 가용 상태를 갖고, 재조회 성공 때마다 목록의 해당 초대 상태(철회·사용·만료·목록에 없음)를 반영한다. PENDING이 아니면 복사 버튼과 '24시간' 안내를 없애고 사용 불가 안내만 보인다. 다른 초대 철회는 영향 없다. 재조회 실패(상태 불명)는 패널을 그대로 두고 기존 '결과 미확인' 안내를 유지한다. 재발급 응답 유실도 재조회로 이전 패널이 철회됨으로 바뀐다.
- **같은 패턴 점검:** 가입 화면·로그인 복귀 경로는 변경하지 않았다. 확인 대기 중(`confirming`)인 초대가 다른 곳에서 상태가 바뀌어도 확인 상자가 남는 점은 서버가 최종 판정하므로 그대로 뒀다.
- **검증(Windows, Node, 이번에는 test·build도 차단 없이 실행):** `api:check` 일치, `lint`·`typecheck` 성공, `npm test -- --run` **8개 파일 237개 통과**(기준 228 + 신규 9, 실패·건너뜀 0), `build` 성공(JS gzip 93.2 kB), `git diff --check` 통과. 변이 3개(패널 key 제거, 화면과 다른 nurseId 전송, 상태 반영 제거)를 각각 신규 테스트가 검출했고 원복했다. WSL 사본은 이번에 사용하지 않았다(WSL에서 Linux Node가 없어 Windows npm만 잡혀 실행 불가).
- **미검증:** 실제 브라우저·클립보드·Kakao·실서버·두 계정 시연·GitHub CI·기기. 목업 검증이며 완료를 주장하지 않는다.
- **공유 인계:** `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-invite-join`, HEAD `0ed5b4c`, 미커밋 구현을 이어서 사용. 미추적 `nurseflow.mv.db` 보존.

## B3b Codex 재리뷰 (2026-11-12)

- **결과:** 기존 P3 1번(복사 상태)·2번(간호사 선택값) 해결 확인. 3번(철회 패널)은 재조회 성공 경로만 해결됐고 **P3(런타임) 1건이 남음**. B3b 수정·재리뷰를 먼저 마친 뒤 B9b 별도 구현으로 이어간다.
- **남은 P3:** `web/src/pages/InvitationAdminPage.tsx:151–175`에서 DELETE 204를 받았어도 해당 발급 패널을 즉시 무효화하지 않고 목록 재조회에만 의존한다. 발급 → 같은 초대 철회 성공 → 목록 조회 실패이면 이미 철회된 링크를 계속 복사할 수 있고 24시간 사용 가능 안내도 남는다. **철회 성공 응답은 결과가 확인된 상태**이며, 철회 응답 자체를 잃은 상태와 구분해야 한다. `confirmed()`의 대상 invitationId를 쓰기 결과에 연결해 성공 시 해당 패널을 즉시 REVOKED 처리하고, 응답 유실 때는 목록 재조회로 판정해야 한다.
- **재현 근거:** 정식 테스트 `InvitationAdminPage.test.tsx:498`은 `failBefore('list')`만 주입하고 철회 요청은 정상 처리한다. 그런데도 복사 버튼이 남는 것을 정상 기대값으로 검사하므로 현재 237개 통과가 이 경로의 정확성을 증명하지 못한다. 이 테스트는 성공 철회 시 복사 불가를 검사하도록 바꾸고, 철회 응답 유실+목록 조회 실패는 별도 테스트로 분리해야 한다. 무관한 초대 철회 유지 테스트도 보존한다.
- **직접 검증(Windows):** `npm test -- --run` **8개 파일 237개 통과**, `npm run api:check`·`npm run lint`·`npm run typecheck`·`npm run build` 모두 exit 0. 이번 build JS gzip **94.19 kB**. `git diff --check` 통과. WSL·npm ci는 재실행하지 않았다. 실제 브라우저·클립보드·Kakao·실서버·GitHub CI는 미검증이다.
- **인계 기준:** 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, 브랜치 `feat/web-invite-join`, HEAD `0ed5b4c2bb7443d719180433c3593ad9c3b579ba`. 상태 문서 제외 tracked diff SHA-256 `d6b6380d96ae3fbf176d507d319819cb356b547c64a1e4142f24563728adbfb4`, 신규 web 17개 파일 manifest SHA-256 `d7d37224b55bd4b68ccb05b81597c44fc6e182ecddbd0a5fcc882a6374f7c79d`(앞선 리뷰와 같은 계산 방식). 검토 주체 Codex. 코드 수정·커밋·푸시·PR·B9b 구현은 하지 않았다. 기존 미추적 DB 보존.

## B3b 재리뷰 P3 수정: 철회 성공 후 재조회 실패 (2026-11-12, Claude)

- **상태:** 수정 완료, **Codex 재리뷰 대기**. 커밋·푸시·PR·B9b 시작 없음. `backend/**`·`docs/openapi.yaml` 변경 0줄. 수정 파일: `web/src/pages/InvitationAdminPage.tsx`, `InvitationAdminPage.test.tsx`, 이 문서, `web/B3b-verification.md`.
- **수정:** `run()`이 철회 대상 `invitationId`를 쓰기 결과에 연결한다. 철회 요청이 성공(204)하면 현재 발급 패널이 그 초대일 때 즉시 REVOKED로 바꾸고, 이후 목록 조회가 실패해도 복사 버튼·수동 복사란·24시간 안내가 다시 나오지 않는다(재조회 실패 안내는 그대로 표시). 다른 초대의 철회는 패널을 유지한다. 철회 응답 유실·실패는 성공으로 보지 않고 재조회 결과로만 판단하며, 재조회도 실패하면 '결과 미확인' 안내와 기존 패널을 유지한다. 목록 반영 시 이미 사용 불가로 확인된 패널을 PENDING 목록이 되살리지 못하게 막았다(재조회마다 요청 번호로 이전 응답은 버려지므로 이중 방어).
- **테스트:** 기존 '재조회 실패' 테스트의 잘못된 기대(철회 성공인데 복사 버튼 유지)를 바로잡고 분리: 철회 204+조회 실패(복사·수동 복사란 불가, 실패 안내 유지), 응답 유실+조회 성공(조회 상태 반영), 서버가 처리하지 못한 철회+조회 성공(패널 유지), 응답 유실+조회 실패(미확인 안내·패널 유지), 늦은 이전 조회 응답이 철회 상태를 되돌리지 못함. 무관한 초대 철회 유지·복사 상태 초기화·늦은 복사 폐기·간호사 선택값 테스트는 유지.
- **검증(Windows):** `api:check` 일치, `lint`·`typecheck` 성공, `npm test -- --run` **8개 파일 241개 통과**(기존 237 + 순증 4, 실패·건너뜀 0), `build` 성공(JS gzip 약 93.3 kB), `git diff --check` 통과. 철회 대상 연결을 끊는 변이를 신규 테스트가 검출했고 원복했다. 늦은 응답 가드 단독 변이는 요청 번호 방어와 겹쳐 따로 검출되지 않는다.
- **미검증:** 실제 브라우저·클립보드·Kakao·실서버·두 계정 시연·GitHub CI·기기. WSL·`npm ci`는 재실행하지 않았다.
- **공유 인계:** `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-invite-join`, HEAD `0ed5b4c`, 미커밋 구현 이어서 사용. 미추적 `nurseflow.mv.db` 보존.

## B3b Codex 최종 수정 재리뷰 (2026-11-12 13:39 KST)

- **결과:** 남은 P3(철회 성공 후 목록 조회 실패) 해결 확인. 기존 P3 3건과 후속 잔여 경로 모두 해결됐으며, 이번 수정 범위에서 추가 조치할 결함을 발견하지 못했다. 코드 리뷰 완료이며 실연동 완료·병합 완료를 의미하지 않는다.
- **검토:** `confirmed()`가 철회 대상 ID를 전달하고, `run()`이 성공 응답을 받은 경우에만 해당 패널을 즉시 REVOKED로 바꾸는 것을 확인했다. 다른 초대 철회는 현재 패널을 유지한다. 응답 유실은 재조회로 판정하며, 이미 무효인 패널은 PENDING 조회 결과로 되살아나지 않는다. 무효 패널은 복사·수동 복사란·24시간 안내를 렌더하지 않는다. 요청 번호·세션/컴포넌트 수명 검사와 기존 복사 상태·간호사 선택값 수정도 확인했다.
- **직접 검증(Windows):** `npm test -- --run` **8개 파일 241개 통과**, `npm run api:check`·`npm run lint`·`npm run typecheck`·`npm run build` 모두 exit 0. 실제 이번 build JS gzip **94.25 kB**. `git diff --check` 통과. backend·OpenAPI diff 없음. npm ci·WSL·변이 검사는 이번 재리뷰에서 실행하지 않았다. 늦은 조회 테스트는 요청 번호 방어를 검사하며, 가짜 서버가 보류 시점의 PENDING 응답 본문을 고정하지 않으므로 상태 가드 자체의 독립 변이 검출까지 주장하지 않는다.
- **남은 완료 조건:** 실제 브라우저·클립보드 권한·Kakao 로그인 왕복·실서버 관리자/간호사 두 계정 시연과 PR/CI 확인. 코드 리뷰를 마쳤으므로 이 검증 준비와 별개로 B9b를 별도 기능 작업으로 진행할 수 있다.
- **인계 기준:** 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, 브랜치 `feat/web-invite-join`, HEAD `0ed5b4c2bb7443d719180433c3593ad9c3b579ba`. 상태 문서 제외 tracked diff SHA-256 `d6b6380d96ae3fbf176d507d319819cb356b547c64a1e4142f24563728adbfb4`, 신규 web 17개 파일 manifest SHA-256 `a9d576564f313fabb8f010cd20be80a202cf517290e224d2f1a0e492b9590bfe`(앞선 리뷰와 같은 계산 방식). 검토 주체 Codex. 운영 코드 수정·커밋·푸시·PR·B9b 시작 없음. 미추적 DB 보존.

## B3b·B4a 최신 dev 반영 완료 (2026-11-12, Codex)

- 최신 `origin/dev`는 `b352759`(#41 A6 구성원 관리, #42 병원 목록 동봉 포함)이며 두 작업 브랜치 모두 이 커밋을 조상으로 포함한다. B9b는 이번 요청 대상이 아니다.
- **B3b:** `feat/web-invite-join` HEAD를 `0ed5b4c`에서 `b352759`로 fast-forward했다. 기존 B3b 작업 파일만 임시 보관 후 복원했으며 충돌 없음. 보존된 web 28개 파일을 임시 보관본과 LF 정규화하여 대조해 차이 0개 확인(상태 문서는 dev 내용과 3-way 병합). 기존 B3b 구현은 미커밋 상태를 유지한다. 최신 OpenAPI와 생성 타입은 dev에서 함께 반영됐다.
- **B3b 직접 검증:** Windows에서 `npm run api:check`·`npm run lint`·`npm run typecheck`·`npm run build` 모두 exit 0, `npm test -- --run` **8개 파일 241개 통과**, JS gzip 94.25 kB. `git diff --check` 통과. 앞선 최종 재리뷰에서 P3는 모두 해결 확인됐으며, 표의 '재리뷰 대기'는 이 기록으로 대체한다.
- **B4a:** 기존 `d7ce955`와 최신 dev를 충돌 없이 병합한 로컬 통합 커밋 `405a8e5727a1e9de8f5c3e5c72ce521b60c372a2`. `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon` **BUILD SUCCESSFUL**, 80개 작업 중 8개 실행·72개 UP-TO-DATE(단위 테스트는 캐시 재사용). 상세·변경된 실연동 준비 절차는 B4a 브랜치의 상태 기록과 `android/B4a-verification.md`에 남겼다.
- **인계:** 현재 공유 worktree는 `feat/web-invite-join`, HEAD `b352759`, B3b 미커밋 구현이 복원돼 있다. B4a는 로컬 브랜치에 통합 커밋이 있고 원격에는 아직 푸시하지 않았다. B3b 커밋·두 브랜치 푸시·PR·dev/main 병합·배포는 하지 않았다. 백엔드 테스트·실서버·Kakao·브라우저/기기·CI는 이번에 실행하지 않았다. 사용자 DB·local.properties 보존.

## B3b·B4a 커밋·푸시 인계 (2026-11-12, Codex)

- 사용자 요청으로 두 작업 브랜치의 커밋·원격 푸시를 진행한다. B3b는 최신 dev `b352759` 위에 검토 완료한 `web/**`와 이 상태 문서만 커밋하며, B4a는 이미 만든 dev 통합 커밋 `405a8e5`를 푸시한다. PR 개설·dev/main 병합·배포는 범위에 없다.
- 직전 통합 검증 결과: B3b 테스트 8개 파일 241개 통과, api:check·lint·typecheck·build 성공. B4a 단위 테스트·앱/테스트 APK 빌드·Lint 명령 BUILD SUCCESSFUL(단위 테스트는 UP-TO-DATE). 이후 기능 코드 변경이 없어 재실행하지 않았고 `git diff --check`를 다시 확인했다.
- 실브라우저·실서버·Kakao·기기·CI 미검증을 유지한다. B3b 기존 P3는 최종 재리뷰에서 모두 해결 확인됐다. 사용자 DB는 스테이징·커밋·푸시 대상에서 제외한다. 푸시 후 두 원격 브랜치 SHA와 로컬 SHA가 같은지 확인하고 현재 B3b 브랜치에서 인계한다.

## 기타 브랜치

- `feat/android-auth-invite` (`95cf33d`): **병합 대상에서 제외(2026-11-09 사용자 결정).** 현재 A/B 기능 분해 이전(11/2~11/3)의 백엔드+Android 통합 시제품 브랜치다. 같은 기능이 #17~#20, A1(#27), B1(#30)로 따로 병합됐고, `dev`와 합치면 174개 파일이 충돌하며, 병동 직접 생성(`74c9036`)은 #24 이후 A3·A4로 다시 기획됐다. 원격에 참고용으로만 남기고, B3a(첫 화면 선택·초대 가입 화면)·B4a(병동 생성 화면) 작업 때 최신 `dev`에서 새로 구현하며 참고한다. 이 브랜치에서 PR을 열거나 이어서 작업하지 않는다.

## B2 인계 (2026-11-10, Claude)

### 브랜치와 보존한 변경

- 최신 `origin/dev` `23af850`(A3 #35 병합 포함)에서 `git switch --merge -c feat/web-foundation-session origin/dev`로 브랜치를 만들었다.
- 이전 브랜치 `feat/android-published-roster`에 있던 미커밋 Codex 기록("A3 PR #35 B4a/B4b 소비자 계약 검토" 절)은 3-way 병합으로 그대로 옮겼다. 전환 전 diff 사본과 비교해 내용이 바뀌지 않았음을 확인했다.
- 미추적 `nurseflow.mv.db`는 건드리지 않았다.
- 이 브랜치는 원격 추적을 해제했다(`dev`로 잘못 푸시되지 않도록).
- 사용자 지시에 따라 B2 변경만 커밋해 `origin/feat/web-foundation-session`에 푸시했다.
  - Codex 절은 이 커밋에서 빼고 공유 worktree에 미커밋 상태로 보존했다.
  - `nurseflow.mv.db`도 커밋하지 않았다.
  - PR 생성·병합·배포는 하지 않았다.

### 구현 범위 (B 소유 경로만)

- 수정한 경로:
  - `web/**` 신설
  - `.github/workflows/verify.yml`: `Web checks` 작업 추가만 하고 기존 줄은 바꾸지 않음
  - 루트 `README.md`, `CLAUDE.md`, 이 문서
- 수정하지 않은 경로: `backend/**`, `docs/openapi.yaml`, `docs/feature-breakdown.md`.
- §7 #19 결정 사항:
  - "React+Vite 확정"과 호스팅 후보는 [웹 안내](../web/README.md)에 기록했다.
  - breakdown 반영은 별도 결정 반영 문서 PR로 한다.

### 세션 설계

- 쿠키를 바꾸는 refresh, 코드 교환, logout은 공통 락 `nurseflow-web-session` 안에서만 실행한다. Web Locks API를 쓰고, 없으면 탭 안 대기열로 대신한다.
- 이 세 호출은 중간에 취소하지 않는다.
- 로그아웃은 `SigningOut` → 락 대기 → 서버 logout → `SignedOut` 또는 `LogoutFailed` 순서다.
- 콜백 경로에서는 시작 시 복원 refresh를 생략한다.
- epoch로 늦게 도착한 응답을 버린다.
- 서버 `AuthService.logout`은 이미 회전된 토큰으로도 계열을 폐기함을 코드로 확인했다.

### 검증

- **통과**: `api:check`, `lint`, `typecheck` 3종.
- **미실행**: `npm test`, `npm run build`, 로컬 실서버, 실제 Kakao.
- **원인**: 스마트 앱 컨트롤(`VerifiedAndReputablePolicyState = 1`)이 `@rolldown/binding-win32-x64-msvc`를 차단한다. WASI 대체 바인딩도 Windows 경로를 해석하지 못해 실패했다.
- **WSL 재검증 (2026-11-10 16:45~17:05, Claude)**: 사용자가 WSL Ubuntu 26.04.1을 설치했다.
  - **설치한 도구**: 공식 배포본 Node v24.22.2와 Temurin JDK 21.0.13+9를 `~/.local`에 설치했다(체크섬 확인, sudo 없음).
  - **작업 위치**: WSL 홈 사본 `~/nf-b2`에서 실행했다. 공유 worktree, Windows `node_modules`, `nurseflow.mv.db`는 건드리지 않았다.
  - **CI와 같은 6단계**: 모두 통과했다(테스트 49/49, 빌드 JS gzip 86.07 kB).
    - 첫 실행에서는 테스트 6건이 실패했다. 모두 테스트 코드 결함(fetch를 생성 시점에 잡아 둠, 역할 중복 질의)이었고 수정했다.
    - 변이 검사로 가짜 서버가 응답 시점의 쿠키를 읽던 결함을 찾아, 요청 시점의 쿠키를 쓰도록 고쳤다. 이제 9개 중 등가 1개를 뺀 8개를 검출한다.
  - **로컬 백엔드 연동(curl, 프록시 경유)**: dev(Kakao 키 없음) 20/20, preview(가짜 키, Kakao 미호출) 23/23.
  - **보안 점검**: 번들에 Web Storage·`console.log`·테스트 문자열 0회. 서버 로그에 WARN/ERROR와 쿠키·토큰 값 0건.
- **실제 Kakao 검증 (2026-11-10 저녁, Claude 진행·사용자 직접 조작)**
  - **구성**: WSL 사본에서 백엔드·웹을 띄웠다. 매 실행 새 임시 H2 DB를 쓰고 access token 수명은 1분으로 줄였다.
  - **비밀값**: Kakao 키와 Client Secret은 사용자가 만든 `~/.nurseflow-kakao.env`(권한 600)를 `source`로만 불러왔고 값은 출력하지 않았다.
  - **첫 시도 실패**: 원인은 Kakao 앱의 Client Secret 활성화와 서버 설정 누락이었다(토큰 교환 401 `KOE010`). state·쿠키 단계는 정상이었다. 사용자가 secret을 추가한 뒤 해결됐다.
  - **사용자 확인(웨일, 실제 계정 1개)**: 로그인, 소속 없음 화면, F5·새 탭 복원, 1분 뒤 갱신, 두 탭 동시 갱신, 로그아웃 전파, 브라우저 재시작 복원, 취소 후 로그인 화면 복귀가 모두 정상이었다.
  - **서버 확인**
    - DB 개수·시각: 로그인 시도 소비 각 1회, 두 탭 갱신 구간의 순차 회전(계열 폐기 없음), 로그아웃 시 계열 일괄 폐기.
    - 로그: WARN·ERROR 0건이고, 토큰·쿠키·코드·키 값은 0회다.
  - **미검증**: 두 실제 계정 전환, 웨일 외 브라우저, 쿠키 속성·Web Storage·Console·주소창·콜백 재사용의 브라우저 측 확인, 쿠키 차단 안내, GitHub CI.
  - **A 후속**: Kakao 토큰 401일 때 서버 로그의 `kakaoErrorCode`가 비는 진단 공백.
- 상세 결과는 [B2 검증 기록](../web/B2-verification.md) 5절에 있다.

### 다른 단위 상태 관찰

- 위 표의 A3 행은 "PR 열림"으로 남아 있다. 그러나 `origin/dev`에는 A3 #35가 `23af850`으로 병합돼 있다(2026-11-10 15:47 KST).
- A3 행은 B2 범위가 아니어서 고치지 않았다.

## 리뷰 지점 기록

Claude와 Codex가 같은 진행 기록을 사용한다. PR 또는 정기 리뷰를 마칠 때 공유 worktree별 경로, 브랜치, 리뷰한 `HEAD` 커밋 SHA, 미커밋 diff 해시, 시각, 검토 주체를 기록한다. 해시는 재현 가능하도록 `git diff HEAD -- . ':!docs/work-status.md' | shasum -a 256`으로 계산해 상태 기록 파일 자체를 제외한다. 인계받은 에이전트는 먼저 이 기준점을 확인하고 변경된 경우 새 검토 지점을 추가한다. 로컬 도구별 상태 파일을 유일한 기록으로 삼지 않는다.

| 작업 경로 | 브랜치 | 리뷰한 HEAD SHA | 미커밋 diff 해시 | 리뷰 시각 | 검토 주체 | 범위/결과 |
| --- | --- | --- | --- | --- | --- | --- |
| `C:\Users\jjang\IdeaProjects\NurseFlow` | `feat/android-published-roster` | `ab8347dde6464379c8670cde6f937f408d349eb8` | `66410e40794e40222067c249c692fc76ca704e07ecc39f8383e5f4768eb9e2cf` (tracked diff, 신규 파일 해시는 위 기록) | 2026-11-09 | Codex | 기존 P2 해결 확인 후 기기 회귀·실서버 공개/재공개·권한 검증 완료. 같은 미커밋 브랜치에서 PR 준비 가능. 운영 코드 추가 수정 없음 |
| `C:\Users\jjang\IdeaProjects\NurseFlow` | `feat/android-invite-join` | `23af850d2ee7e85a7bf6fa690f9714dc73b3bceb` | `1b59e8e03cbbe34eff816efaedf75177dca7fc381e828dd9a52dba5a49aa1762` (추적 파일 diff. 신규 파일은 미포함) | 2026-11-10 | Claude | B3a 구현 자체 검토. 관리자 권한 거부 후 재진입 시 바로 다시 닫히는 문제를 수정했고, 무재시도 테스트가 실제 결함을 잡지 못하던 것을 변형 검사로 발견해 보강. 독립 리뷰 전 |
| GitHub PR #32 diff (`/private/tmp/NurseFlow-guidance`) | `docs/agent-guidance-alignment` | `5d4f45bcf7e5247d85f9f1a38fd0a59a081c499e` | `bffafc453496740f281aca22552cd1dcaf80516a2150a8bb741aa42efbdca3ab` (`git diff origin/dev...origin/pr-32 | shasum -a 256`) | 2026-11-09 | Codex | PR 메타데이터·diff·리뷰·체크 및 B1 격리 테스트 경로를 재검토. PR은 열림; GitHub 리뷰 없음, 결합 상태에서 Vercel만 성공 확인. 재검토 사항은 위에 기록 |
| GitHub PR #34 diff | `feat/android-published-roster` | `209de0db506429050885968e9cd2c3d81cc14192` | `635976c6eab08175715a8813697306c26a4f43895ca820571c9c4f98cbe244d7` (`git diff fa47a5afc1bf3b92ed2cb7436e711ba2d57a8295..origin/feat/android-published-roster | shasum -a 256`) | 2026-11-10 | Codex | 12개 파일 읽기 전용 검토. P3 1건을 COMMENT 리뷰로 게시; Verify run 42613978040 성공 |
| `/private/tmp/NurseFlow-guidance` | `docs/agent-guidance-alignment` | `f10d598b330de17a47a954558196e8df0be4a4ec` | `e638b1aa27f5c0cccc9f855d1cb5b9c19834bd1b1410a80b260b69a29cbb5fb5` (`git diff HEAD^ HEAD -- . ':!docs/work-status.md' | shasum -a 256`) | 2026-11-09 | Codex | Claude·Codex가 같은 worktree·브랜치·상태 기록·검증 기준을 이어 쓰도록 정리. `git diff --check` 통과. PR #32에 푸시 완료 |
| `/private/tmp/NurseFlow-a2-review` | `feat/auth-web-session-v2` | `9a8ef4d64a5f50579853fada8ce3b823d66ec962` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`(미커밋 변경 없음) | 2026-11-09 | Codex | A2 전체 읽기 전용 리뷰. P1 없음. P2(테스트) 1건: 같은 state 동시 교환 테스트 부재. state 소비·refresh 회전 행 잠금, redirect URI 정확 일치, nonce·aud·iss·exp, 쿠키 속성, CSRF 인터셉터 순서, 오류 매핑, 로그 비노출, Android 경로 무변경, OpenAPI 일치 확인 |
| `/private/tmp/NurseFlow-a2-review` | `feat/auth-web-session-v2` | `0ab0a5449d2e3a5aef5ac3eb2e6c5500ea65b6f0` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | 2026-11-09 | Claude | 위 P2를 동시 콜백 테스트로 수정. Claude가 지적을 코드와 대조해 확인한 뒤 수정했고 Codex 재리뷰는 하지 않음 |
| GitHub PR #33 리뷰 | `feat/auth-web-session-v2` | `f30a59bd971b4cd7304b5bbc9a6478354e439f46` | 해당 없음(PR diff 기준) | 2026-11-09 | hyen11 | P1·P2 없음. P3 기본 포트 Origin 정규화, P4 client_secret 회귀 테스트, P5 OpenAPI cross-site 조건 |
| `/private/tmp/NurseFlow-a2-review` | `feat/auth-web-session-v2` | `00cbffff08cbc17490bd8db2f8194fde3d23b0ad` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`(미커밋 변경 없음) | 2026-11-09 | Claude | 위 P3(`b6d56aa`)·P4(`e371fa5`)·P5(`00cbfff`) 수정. 재리뷰 전 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/hospital-search` | `08180db18edd7512e5033390c6469a76f2b8e75e` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`(미커밋 변경 없음) | 2026-11-10 | Codex | A3 전체 읽기 전용 리뷰. P2(데이터) 1건: 닫히지 않은 따옴표가 뒤 행을 삼켜 최소 행 수만 넘으면 대량 비활성화. 트랜잭션 롤백, 해시 건너뛰기, zip 선택, LIKE 와일드카드 비노출, 키 비로깅, OpenAPI 일치 확인. Gradle 실행은 샌드박스 제한으로 못 함 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/hospital-search` | `56434503bf8c763e837071a10890fd58580c32a4` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | 2026-11-10 | Claude | 위 P2 수정: 닫는 따옴표 뒤 구분자 강제·미종결 따옴표 오류·10% 초과 비활성화 거부·적재 ID 기반 비활성화. Codex 재리뷰 전 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/hospital-search` | `283ffb6fcea6971094f593df9c2f113685a73c67` | 미커밋 변경 없음 | 2026-11-10 | Codex | P2 수정(`5643450`) 재리뷰. P1/P2 없음, 병합 가능 판정. P3(런타임) 1건: 길이 제한이 따옴표 필드에만 적용 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/hospital-search` | `a68b578984da0ea3d8c4f93b7044b1ea8eacf671` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P3 수정(모든 필드 2,000자 제한)과 테스트 보강. 야간 QA 결과는 A3 행 참조 |
| GitHub PR #35 리뷰 | `feat/hospital-search` | `68b59814641f6341e0bfe611946db8b713ef3834` | 해당 없음(PR diff 기준) | 2026-11-10 | hyen11 | B4a/B4b 소비자 관점 계약 검토. P1/P2 없음. 응답 필드·200 빈 결과/503 구분은 화면에 충분. P3 2건: 정규화 후 최소 길이의 계약 명시·공통 검증, 기호 무시·정렬의 보장 범위 명시 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/hospital-search` | `a3755fd3438ac30d3f168cc82e631c4a627ba12b` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P3 2건 수정: 두 경로 공통 최소 길이 검증, 대체 경로는 외부 API 일치·정렬을 따른다고 계약에 명시. hyen11 재확인 전 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `aa91e6915da63f22efa82c89196dc69f10a1d56e` | 미커밋 변경 없음 | 2026-11-10 | Codex | A4 전체 읽기 전용 리뷰. P1/P2 없음. 권한 분리·목록 기반 병원 검증·멱등 트랜잭션 경계 확인. P3(테스트) 업그레이드 검사가 메모리 DB만 사용, P4(문서) breakdown 요청 예시 불일치 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `918e34682b125370fe4f472ddfbfc247c2013eb8` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P3·P4 수정: H2 파일 DB를 닫고 다시 열어 업그레이드 검증, 요청 예시 정정 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `6a284e0fc5078bfdb07d2dc179acbe391e4da1a1` | 미커밋 변경 없음 | 2026-11-10 | Claude(/code-review) | 10건 중 7건 수정: 초대 미리보기 hospitalName, 공백만 다른 재시도 replay, 멱등 저장소 공용화, 병원 확인 조회 순서·존재 확인 비용, 행 매퍼 중복, source 상수, 대체 경로 계약 명시. 미수정 3건(생성 횟수 제한, 잠금 시간 초과 시 500, replay JSON 형식 변경 호환)은 PR 코멘트에 사유와 함께 기록 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `4d3f73d7d4a5a4e7ac49851b430e928561491222` | 미커밋 변경 없음 | 2026-11-10 | Codex | 상한·공용 replay 이후 재리뷰. REQUEST CHANGES: P2 잠금을 기다린 같은 키 요청이 중복 오류로 실패, P3 새 오류 코드 계약 누락. roster 멱등 동작·잠금 순서·USER_NOT_FOUND는 문제없음 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `2ab2b92c9e9e8a436b0dbe04c8e12e1f45d612e7` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P2·P3 수정: 계정 잠금 직후 replay 재확인, 같은 키 동시 테스트 강화(변이 3/3 검출), 계약·README 보강 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `fbbfe031f0f285da4d54357db497aa13a21cbc82` | 미커밋 변경 없음 | 2026-11-10 | Codex | P2 수정 확인: 잠금 뒤 replay 재확인은 READ_COMMITTED에서 타당, 부분 쓰기 없음, 계약·README 일치. 남은 P3(테스트): 동시 테스트가 경합 구간을 매번 강제하지 못함. 서비스에 테스트용 동기화 지점이 필요해 수정하지 않음 |
| GitHub PR #36 리뷰 | `feat/ward-self-create` | `d82fa2da5778601b3ade50367005b143250e649b` | 해당 없음(PR diff 기준) | 2026-11-10 | hyen11 | P1~P3·P5 없음. P4(테스트) 2건: 잠금 뒤 replay 재확인 경로를 강제하지 못함, 다른 계정 같은 키 경합·롤백 미검증 |
| `/Users/sihwan/Documents/ChatGPT/NurseFlow` | `feat/ward-self-create` | `bbd7b550202490ccdbed8f14823f2b03ca5b3da3` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P4 2건 수정: 테스트 전용 하위 클래스로 순서 강제. 두 경로 변이 각 3/3 검출 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `2a4967badad87b4781a4b30825ea04d6d3c48e2b` | 미커밋 변경 없음 | 2026-11-10 | Codex | A5 리뷰. P2 생성·활성 변경이 병동 잠금 밖, P2 비활성 간호사 순번 잔존, P3 동일 경력 재저장 이력, P4 계약 required·테스트·B5 범위. 권한·단계 범위·roster 코드 무영향 확인 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `68c6b412ba1b16f71a347adfae4990b1dbd17e97` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 지적 모두 수정. Codex 재리뷰 전 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `8295380c5f148acd9154e4bd77e55a46fc0f65fe` | 미커밋 변경 없음 | 2026-11-10 | Claude(/code-review) | A5 10건 모두 수정: 이력 seq 정렬, 팀 삭제·이동 잠금, 편성 설정 행 잠금, 활성 변경 시에만 병동 잠금(lockWard 재사용), 본인 화면 MyStaffProfile, stageOverride 필수, 순번 연속, 죽은 검사 제거, 조인 개선 |
| GitHub PR #37 리뷰 | `feat/staff-admin-career-adjust` | `9d16d482a78219d143abefbeaa338996af1b7a47` | 해당 없음(PR diff 기준) | 2026-11-10 | hyen11 | P2 2건: 잠금 여부를 잠금 밖 조회로 정해 활성화·순서 저장과 경합(중복 순번), 경력 입력·확정이 행 잠금 밖에서 이전 값을 읽어 이력 왜곡 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `4fd88149eba66b4428e0bd46b8f77d9aa7deec00` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P2 2건 수정: 간호사 수정 항상 병동 잠금, 경력 경로 행 잠금. 순서 강제 테스트 2건, 변이 각 3/3 검출 |
| GitHub PR #37 리뷰 | `feat/staff-admin-career-adjust` | `53ed3dcfaf68da433dc2ffdb025647c4860e50a2` | 해당 없음(PR diff 기준) | 2026-11-10 | hyen11 | P2: 팀 이동·해제 시 이전 팀 순번에 빈칸(1,3)·선임 2부터 시작 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `fbf71cb5a71a00e1b6097cb9420895d74f24a186` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P2 수정(팀 이동·해제에 순번 정리). 같은 패턴 점검 결과 A6 구성원 비활성화 경로가 남아 A6 dev 반영 때 처리 예정 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `87e1d1f3d1075a77cf0df545bce7db03a9958c78` | 미커밋 변경 없음 | 2026-11-10 | Codex | 팀 이동 순번 수정 재리뷰(`53ed3dc..HEAD`). 지적 없음. 잠금 안 읽기·판단·쓰기, 이력 이전 값, 잠금 순서(권한→병동→대상 행), staff 패키지의 순번 해제 경로 전수 확인 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `931686c9c1f790bd030bc6e5c76bdf31a5ad313e` | 미커밋 변경 없음 | 2026-11-10 | Codex | PR 전체 불변 조건 감사(I1~I8). I1·I2·I3·I5·I6·I7·I8 통과. I4 TEAM_ASSIGNED가 팀 이름을 기록(설계상 화면용 문자열로 판단, 계약 명시), P3 순서 저장의 잠금 전 팀 확인 |
| `/private/tmp/NurseFlow-a5` | `feat/staff-admin-career-adjust` | `16258026d63b7a5921a50743772ebe4a22b4e006` | 미커밋 변경 없음 | 2026-11-10 | Claude | 무작위 불변 조건 테스트 추가, 이 테스트가 찾은 '삭제된 팀의 활성 간호사' 수정, 위 P3 수정, I4 계약 명시 |
| `/private/tmp/NurseFlow-fix` | `fix/kakao-diagnostics-invite-contract` | `b2e8d04aa894c7d916f2927facba53616939cc5a` | 미커밋 변경 없음 | 2026-11-10 | Codex | B 후속 리뷰. Kakao 진단·초대 계약·README는 문제없음. P3(관측성) HIRA 클라이언트에도 401 본문 유실 가능성 |
| `/private/tmp/NurseFlow-fix` | `fix/kakao-diagnostics-invite-contract` | `97f0bdd` | 미커밋 변경 없음 | 2026-11-10 | Claude | HIRA 클라이언트를 같은 HTTP 클라이언트로 맞추고 401·403 로그 고정 테스트 추가. **정정:** 커밋 `97f0bdd`의 메시지는 'HIRA 401 사유 코드가 비었다'고 적었으나 확인 결과 HIRA는 GET이라 원래 읽혔다(실제 결함 아님, 테스트는 이전 클라이언트에서도 통과). 본문 유실은 Kakao 토큰 교환 같은 POST의 401에서만 생긴다 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `73990f9` (구현 커밋. 상태 문서 커밋 전) | 미커밋 변경 없음 | 2026-11-10 | Claude(Sonnet) | A6 구현 전체: 계약 선행 커밋, 구성원 목록·PATCH, 활성 필터(rolesIn·membershipsOf·roster 구성원/조회 권한), 마지막 관리자 보호, 동시 테스트, 스키마 업그레이드 테스트. Codex 리뷰 전 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `4c4762ea12436bd9bcd78be67b90b5ba6c4b076f` | 미커밋 변경 없음 | 2026-11-10 | Codex | A6 리뷰. P1/P2 없음. 모든 접근 경로 active 필터·마지막 관리자 보호·잠금 확인. P3(테스트) 잠금 대기 중 호출자 권한 상실 경로 미고정 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `da361f61d44b0ce4efc6ca0d171aa483ebf267c0` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 P3 수정: 순서 강제 테스트, 변이 3/3 검출 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `7dae5c07d56d03e93db3a3e2ab5d6836f60a8783` | 미커밋 변경 없음 | 2026-11-11 | Codex | dev(A5) 반영 후 순번 정리 수정 재리뷰. REQUEST CHANGES: P2 관리자 쓰기 경로가 병동 잠금 전에만 권한 확인(잠금 대기 중 권한 해제 시 변경 완료). 순번 정리·Bean 의존 순환·활성 필터는 문제없음 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `b1def48214ea374fe51f0d59424b844a1e08a946` | 미커밋 변경 없음 | 2026-11-11 | Codex | 위 P2 수정 재리뷰. APPROVE, 지적 없음. 관리자 쓰기 경로 전수 확인, 초대 수락 잠금 순서·자동생성 실패 처리·경합 테스트 확인 |
| GitHub PR #41 리뷰 | `feat/membership-admin` | `91557be936c4caf08a08527b8cc38d9e642f598b` | 해당 없음(PR diff 기준) | 2026-11-11 | hyen11 | CHANGES_REQUESTED. P2 회귀: `b1def48`에서 자동배치 취소가 병동 잠금을 먼저 잡아, 병동 잠금을 쥔 실행 중 생성을 취소하지 못함 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `4ca4e0daec2906120794f0bc3fb6503c7af8e0e6` | 미커밋 변경 없음 | 2026-11-11 | Codex | 위 P2 수정 재리뷰. P2(dev에 있던 경합): 마지막 취소 확인 뒤 커밋된 취소가 RUNNING으로 응답되고 결과가 반영됨 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `2b5640d97320b689c7d15464cd9718f9accb65be` | 미커밋 변경 없음 | 2026-11-11 | Codex | 결정 직전 작업 행 잠금 재리뷰. P1/P2 없음. P3 조기 실패 경로가 접수된 취소를 FAILED로 덮어씀, P3 테스트의 sleep 동기화 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `e64055bd84898282e11a9c574e82d5d1ae5167ac` | 미커밋 변경 없음 | 2026-11-11 | Codex | 조기 실패 수정 재리뷰. P3 예외 경로 markFailed가 접수된 취소를 FAILED로 덮어씀, P3 cancelArrived가 DB 잠금 시도 전 신호라 대기 증명 불가 |
| 외부 리뷰(사용자 전달) | `feat/membership-admin` | `91557be936c4caf08a08527b8cc38d9e642f598b` | 해당 없음 | 2026-11-11 | 외부 | P1 간호사를 관리자로 지정하면 활성 간호사 기록이 남아 근무 인원으로 계산, P2 취소 대기(이미 `4ca4e0d`로 해결), P4 마지막 관리자 비활성화 안내 문구 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `71dd50e5c88f19937769995e5efb14960a2db632` | 미커밋 변경 없음 | 2026-11-11 | Codex | P1 수정 재리뷰. P1 비활성 상태 지정 후 재활성화·비활성 구성원 간호사 활성화 우회, P1 편성 조회에 관리자 방어 필터 없음, P4 대기 테스트가 취소 세션을 식별하지 않음 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `0d1c50269d06ee016589009739712eb4689076d8` | 미커밋 변경 없음 | 2026-11-11 | Codex | 위 지적 수정 재리뷰. APPROVE, 지적 없음. SESSIONS.EXECUTING_STATEMENT 필터는 Claude가 H2에서 실행 확인(취소 잠금 제거 변이 시 실패) |
| 외부 리뷰(사용자 전달) | `feat/membership-admin` | `56b197dd6b05581effe293005ca7368d656e68c0` | 해당 없음 | 2026-11-11 | 외부 | P1 구성원 비활성화 뒤 직원 API로 기록을 재활성화하면 병동 접근이 막힌 간호사가 다시 편성됨 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `d81916e9c9513dd527640e651756304130996341` | 미커밋 변경 없음 | 2026-11-11 | Codex | 위 P1 수정 재리뷰. P2(Claude 판단 P3) 본인 근무표 조회가 편성 필터를 우회 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `b674f92bbf1b48f651ae88a8d21857783ea353b3` | 미커밋 변경 없음 | 2026-11-11 | Codex | 수정 확인, 지적 없음. 정상 구성원의 본인 근무표 경로 유지 확인 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `77e2eae3a64756ca4292a8d330b27d9e32c2e4be` | 미커밋 변경 없음 | 2026-11-12 | hyen11 | PR #41 재리뷰 CHANGES_REQUESTED: P2 비활성화·본인 프로필 쓰기 경합, P5 관리자 지정 충돌 조건 문서. 다음 커밋에서 수정 |
| `/private/tmp/NurseFlow-a6` | `feat/membership-admin` | `2624a21` | 미커밋 변경 없음 | 2026-11-12 | Codex | APPROVE. 다른 구성원 전용 쓰기 경로 없음, 잠금 순서 유지 확인. 낮은 위험(테스트가 모든 대기 세션을 셈) 지적에 따라 다음 커밋에서 대기 문장을 병동 잠금·간호사 UPDATE로 한정 |
| `/private/tmp/NurseFlow-hospital` | `feat/hospital-bundled-list` | `163e9ff`(dev) 기반, 이 커밋 | 미커밋 변경 없음 | 2026-11-12 | Claude | 병원 목록 동봉(B 개발 환경 요청): 공식 파일에서 서버가 쓰는 5개 열만 gzip(2026.9., 79,899행, 약 2.5MB)으로 `src/main/resources/hospitals/`에 두고, 경로 미지정 시 시작할 때 같은·더 새 목록이 없으면 가져옴. 갱신 스크립트 `backend/scripts/extract_hospital_list.py`. `./gradlew test --offline` 179건 중 통과 174, 건너뜀 5, 실패 0. 더 새 목록 보호 조건 제거 시 테스트 실패 확인. PR #41 병합 뒤 PR 예정 |
| `/private/tmp/NurseFlow-hospital` | `feat/hospital-bundled-list` | `4b5e60c` | 미커밋 변경 없음 | 2026-11-12 | Codex | P1~P3 없음, P4 2건(README 표 사이 문단으로 초대 링크 행이 표 밖에 렌더링, 갱신 스크립트가 기존 파일을 먼저 지움). 다음 커밋에서 수정. bootJar 실행 확인 권고에 따라 패키징된 jar를 별도 DB로 두 번 실행: 첫 실행 79,899행 가져옴, 두 번째 건너뜀 |
| GitHub PR #42 리뷰·외부 리뷰 | `feat/hospital-bundled-list` | `365088a900f53eb9d7eaf318251babbfeb6f82de` | 해당 없음(PR diff 기준) | 2026-11-12 | hyen11·외부 | P3 같은 기간 정정본 동봉 목록이 반영되지 않음(경로 방식과 동작 불일치), P4 추출 스크립트가 행 수 확인 전에 기존 목록 교체, P5 기간 미상 목록이 자동 갱신을 막는 조건 문서 누락. 다음 커밋에서 수정 |
| `/private/tmp/NurseFlow-hospital` | `feat/hospital-bundled-list` | `061d1d9` | 미커밋 변경 없음 | 2026-11-12 | Codex | 리뷰 수정 재리뷰. 규칙·테스트·스크립트 문제 없음. P3(문서) `HospitalDatasetStartupImport` KDoc만 이전 조건으로 남음, 다음 커밋에서 수정. 같은 기간 동봉 목록의 비활성화 비율 가드 전용 테스트는 선택 보강(같은 `importCsv` 경로라 기존 가드 테스트가 적용) |
| 외부 리뷰(사용자 전달) | `feat/hospital-bundled-list` | `98acc41` | 해당 없음 | 2026-11-12 | 외부 | P5 날짜 없는 CSV의 파일명만 바꿔 다시 넣으면 내용이 같아 건너뛰므로 복구되지 않는 조건이 문서에 없음. 다음 커밋에서 README 안내와 테스트 고정 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `d407e55` (구현 HEAD, 이후 문서 커밋 추가) | 미커밋 변경 없음 | 2026-11-10 | Claude(Sonnet) | A8 구현: 근무 유형 CRUD(`policy` 패키지), 휴게·인정·환산 시간, 정책 revision, DRAFT 재검증 표시, 스키마 업그레이드 테스트. 계약 커밋 `9860a57` 선행 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `ed32d2fd67a9e1656dafc077d786b80ca710f114` | 미커밋 변경 없음 | 2026-11-10 | Codex | A8 리뷰. P2 시간 변경 후 초안 칸 시각이 옛 값이라 확정 검사가 옛 시간 기준, P2 kind=OTHER로 휴식 규칙 우회, P3 POST Idempotency-Key 제거 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `67896e789610fba587d9fcfa3944dc3aec556fc5` | 미커밋 변경 없음 | 2026-11-10 | Claude | 위 3건 수정: 초안 칸 시각 재계산·revision 증가, OTHER 입력 차단, Idempotency-Key 복원(잠금 뒤 재확인). Codex 재리뷰 전 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `a16d751` | 미커밋 변경 없음 | 2026-11-12 | Claude | dev(`b352759`, A6·병원 목록) 반영. 근무 유형 쓰기를 `WardService.lockAsAdmin`으로 바꾸고 자체 잠금 제거, 권한 해제 경합 테스트(재확인 제거 시 201로 실패), 웹 타입 재생성 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `a16d751` | 미커밋 변경 없음 | 2026-11-12 | Codex | A8 전체 재리뷰(`67896e7` 수정 확인 포함). P2 근무 유형 생성의 멱등 재생이 잠금·권한 재확인 전에 반환됨. 같은 패턴 점검에서 `cancelGeneration` 재생도 잠금 전이라 같은 키 동시 재시도가 `DuplicateKeyException`(500)으로 실패함을 Claude가 재현. 다음 커밋에서 둘 다 수정 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `6fbaa45` | 미커밋 변경 없음 | 2026-11-12 | Codex | 재생 순서 수정 재리뷰. APPROVE, 지적 없음. 취소 경로에 병동 잠금 추가 없음(교착 없음), 다른 서비스의 재생은 잠금·권한 확인 뒤이거나 관리자 쓰기가 아님(`createWard`) |
| 외부 리뷰(사용자 전달, Codex) | `feat/shift-type-settings` | `f75d79c` | 해당 없음 | 2026-11-12 | 외부 | P2 시각 외 변경(종류·활성·휴게·인정·환산·비활성화)은 재검증 표시만 하고 초안 revision을 올리지 않아, 변경 전 화면의 ETag로 확정 가능. 다음 커밋에서 `markDraftsForRevalidation`이 revision도 올리도록 수정 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `2770d52` | 미커밋 변경 없음 | 2026-11-12 | Codex | 위 P2 수정 재리뷰. APPROVE, 지적 없음. 영향 변경마다 revision 한 번 증가, 이름·코드만 변경 시 유지, 실행 중 자동배치는 STALE로 끝남, 잠금 순서 역전 없음. 후속 참고: 경력 정책·팀·프로필이 확정 규칙에 쓰이게 되면 같은 무효화 대상인지 재검토 |
| GitHub PR #43 리뷰 | `feat/shift-type-settings` | `f75d79c97be97207a60dfc5b66329e49f9f8bf46` | 해당 없음(PR diff 기준) | 2026-11-12 | hyen11 | P1 근무 유형 수정이 공개 근무표 칸의 종류·코드를 소급 변경(휴식 규칙 우회, 개인 근무표 코드 변경), P2 일광절약시간 전환일에 초안 칸 시각 재계산이 종료<시작 구간 저장. 다음 커밋에서 수정 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `34c7132` | 미커밋 변경 없음 | 2026-11-12 | Codex | 위 P1·P2 수정 재리뷰. APPROVE, 지적 없음. 확정 검증·인접 월 휴식 규칙·자동배치·병동/개인 캘린더 모두 `slots()` 스냅샷 경유, 별도 슬롯 삽입 경로 없음, 백필 멱등, DST 거부 시 유형·정책 revision까지 롤백 |
| GitHub PR #43 재리뷰 | `feat/shift-type-settings` | `70c9dbdb0d20f03593d8ae0a80e1d6a6f4321a7c` | 해당 없음(PR diff 기준) | 2026-11-12 | hyen11 | P3 다른 병동에서 같은 Idempotency-Key로 동시 생성 시 500, P4 재시작 후 공개 칸 스냅샷 보존 테스트 누락, P5 `targetCreditMinutes` 상한 계약 누락. 다음 커밋에서 수정 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `70c9dbd` | 해당 없음 | 2026-11-12 | Claude | 설계 문서(requirements·mvp-scope·월간 규칙·정책 설정 명세) 대조. 어긋남: 근무표 응답이 칸 스냅샷 코드·종류를 내보내지 않음(공개본 불변·같은 공개 revision), 근무 유형 동시 편집 revision 검사 없음(mvp-scope 시연 인수 8), 저장 전 변경 영향 표시 없음(정책 설정 명세 9행). 사용자 결정: 미리보기 API 추가, 초안은 새 값 갱신+재검증 표시 유지, 유형 간 시간 겹침 허용 유지. 다음 커밋에서 반영. 후속: A5 직원·팀 수정도 동시 편집 revision 검사 없음, A17 수정 초안은 복사한 칸을 현재 유형 값으로 다시 계산해야 함 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `30bbabc` | 미커밋 변경 없음 | 2026-11-12 | Codex | 설계 규칙 R1~R8 대조 리뷰. REQUEST CHANGES: P2 생성 재시도가 최초 결과에 현재 정책 ETag를 붙여 오래된 본문으로 덮어쓰기 가능, P2 목록·미리보기가 데이터 뒤에 revision을 읽어 옛 데이터에 새 ETag, P3 코드만 바꾸면 초안 칸은 바뀌는데 재검증 표시·revision 그대로. 다음 커밋에서 수정. If-Match 잠금 아래 검사, 재생 경합 409 매핑, 잠금 순서는 문제 없음 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `ca570f1` | 미커밋 변경 없음 | 2026-11-12 | Codex | 위 3건 수정 재리뷰. P1·P3~P5 없음. P2(조건부) 재생 기록 형식이 `ShiftTypeResult`에서 `ShiftTypeWrite`로 바뀌어 배포 전 기록 재시도가 500일 수 있음 → `CREATE_SHIFT_TYPE` 재생은 이 브랜치에만 있고 dev·배포에 없어 해당 없음으로 판단, 코드 변경 없음(이 브랜치를 돌린 로컬 H2만 영향, 로컬 DB 재생성으로 해소) |
| GitHub PR #43 재리뷰 | `feat/shift-type-settings` | `00e1ecbd7597e72a0f2200e0108ec02724672306` | 해당 없음(PR diff 기준) | 2026-11-12 | hyen11 | P3 `SlotView`에 필수 `shiftCode`·`shiftKind`가 추가되어, dev에서 이미 저장된 배정·근무표 생성 재생 응답(`RosterView`)을 업그레이드 후 같은 키로 재시도하면 역직렬화 실패로 500. P4 같은 경로 회귀 테스트 공백. Claude 확인: dev의 `assign`·`createRoster`가 슬롯 포함 `RosterView`를 저장하므로 실제 결함. 다음 커밋에서 재생 시 빠진 두 값을 칸의 저장값(칸이 없으면 유형)으로 채움(`RosterService.replayView`, `RosterJdbcRepository.slotShift`), 구형 재생 JSON 회귀 테스트 추가(채움 제거 시 실패 확인). 같은 패턴 점검: dev 대비 필수 필드가 늘어난 재생 저장 모델은 `SlotView`뿐(`ShiftTypeWrite`는 이 브랜치 전용). `./gradlew test --offline` 261건 중 통과 256, 건너뜀 5, 실패 0 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `7d005da` | 미커밋 변경 없음 | 2026-11-13 | Codex | 위 P3 수정 재리뷰. 승인. 재생 경로는 `createRoster`·`assign` 둘 다 `replayView` 사용, 잠금·권한 재확인 뒤 재생, `slotShift` SQL·폴백 값 적절. P4 슬롯 요소가 객체가 아니거나 `slotId`·`shiftTypeId`가 없으면 예외 가능 → 해당 없음으로 판단: 재생 JSON은 서버가 `RosterView`를 직렬화해서만 저장하며 두 ID는 non-null이라 그런 형태가 생기지 않음 |
| 외부 리뷰(사용자 전달, Claude 데스크톱) | `feat/shift-type-settings` | `630f15c` | 해당 없음 | 2026-11-13 | 외부 | P2 생성(If-Match 없음)과 이미 비활성인 유형의 DELETE가 현재 정책 ETag를 돌려줘, 목록 뒤 다른 관리자가 바꾼 유형을 옛 화면이 412 없이 덮어씀 → 확인, 다음 커밋 `5d993e1`에서 생성 If-Match 필수·no-op DELETE도 비교. P3 DELETE 400 문서 누락·`displayName` 공백 불가 미표기·기존 OTHER 유형 PUT 불가 미표기 → 문서 수정. P3 멱등 키 경합의 409를 전역 핸들러가 정함 → 서비스(`ShiftTypeService`·`RosterService.recordReplay`·`WardService.createWard`)로 이동. 미확인 항목(자동배치 중 근무 유형 쓰기 500) → 임시 테스트로 확인: 병동 잠금 대기 약 2초 뒤 `QueryTimeoutException` 500. 자동배치가 병동 잠금을 실행 내내 잡는 구조라 dev의 모든 병동 잠금 쓰기에 공통, A8 범위 밖 후속으로 기록 |
| `/private/tmp/NurseFlow-a8` | `feat/shift-type-settings` | `5d993e1` | 미커밋 변경 없음 | 2026-11-13 | Codex | 위 수정 재리뷰. P1~P3·P5 없음. P4 생성 재시도 시 `If-Match`도 재생 일치 조건(새 ETag로 같은 키 → 409)이라는 설명 누락 → 다음 커밋에서 OpenAPI·README에 추가. `./gradlew test --offline` 262건 중 통과 257, 건너뜀 5, 실패 0. web `api:check`·lint·typecheck·test 통과. 검사 제거 시 회귀 테스트 3건 실패 확인 |

이 문서는 Claude와 Codex가 각 기능 단위 작업 후 진행 결과를 함께 기록하는 단일 공유 현황이다.

## A3 PR #35 B4a/B4b 소비자 계약 검토 (2026-11-10, Codex)

> 아래는 이전 head에서 수행한 계약 검토의 시점 기록이다. 현재 코드에 지적이 남아 있다는 판정이 아니다.

- 후속 게시: 사용자의 행별 리뷰 요청에 따라 [COMMENT 리뷰 5837141400](https://github.com/gerrmang/NurseFlow/pull/35#pullrequestreview-5837141400)을 head `68b5981`에 게시했다. `docs/openapi.yaml` 368행(입력 검증, discussion_r4521393260), 359~361행(검색 보장 범위, discussion_r4521393265)에 대응하는 diff hunk와 position 29/22를 GitHub API로 확인했다. 연결 도구는 403이어서 인증된 로컬 GitHub CLI를 사용했다. 아래의 'GitHub 리뷰 게시 없음'은 최초 정적 검토 시점 기록이며 이 후속 요청으로 게시 완료했다. 코드 변경·커밋·푸시·병합은 하지 않았다.

- 검토 대상: GitHub PR #35의 최초 OpenAPI 계약 `aa1915c`와 최신 head `68b59814641f6341e0bfe611946db8b713ef3834`. `docs/openapi.yaml`, 검색 Controller/DTO/Service, DB·실시간 검색 경로와 관련 테스트를 정적으로 대조했다. 현재 로컬에는 A3가 없으므로 GitHub 연결 도구로 읽었으며 브랜치를 전환하지 않았다.
- 판정: 검토 범위에서 P1/P2 없음. P3(계약/입력) 1건: OpenAPI 368행은 trim 후 2~50자만 명시하지만, DB 경로는 문자·숫자만 남긴 뒤에도 2자 이상을 요구한다. `(가)`는 계약상 허용되지만 400 `INVALID_REQUEST`이며 `HospitalDatasetSearchHttpTest`에도 거절 기대가 있다. 정규화 후 최소 길이를 계약에 명시하고 DB/실시간 경로의 공통 검증으로 맞춰야 B4 입력 안내가 일치한다.
- P3(계약/검색) 1건: OpenAPI 359~361행의 기호 무시·정확 일치/병원급 우선 정렬은 DB 경로에서만 구현된다. 실시간 경로는 trim한 query를 yadmNm으로 보내고 외부 페이지 순서를 그대로 반환한다. 공통 보장으로 구현하거나, 해당 규칙이 DB 경로에 한정됨을 계약에 명시한다. 외부 API의 실제 검색·정렬 동작은 이번에 호출해 확인하지 않았다.
- 소비자 확인: `source`·`externalId`로 선택 항목을 식별하고 `name`·`address`로 동명 병원을 구분할 수 있다. `category`는 선택적 nullable로 소비한다. 200 빈 목록과 503 두 오류 코드를 별도 상태로 처리하고 `attribution`을 표시한다. page 100 이후는 요청하지 않고 검색어 구체화를 안내해야 한다. 검색 실행 시 호출하고 늦게 도착한 이전 검색 결과는 폐기한다.
- 범위 제한: `POST /wards`는 여전히 BootstrapWardRequest 계약이다. 병원 후보 검색 검토가 A4 병동 생성 계약 합의나 B4 전체 실연동 완료를 뜻하지 않는다. A4에서 hospital 스냅샷·Idempotency-Key·오류 계약을 별도로 검토한다.
- 검증: GitHub 원본 파일·PR diff·기존 테스트 기대값 대조. Gradle·기기·외부 API·실데이터 검사는 실행하지 않았다(원격 A3 정적 계약 리뷰). 최신 head의 Verify run `42672162190`은 조회 시 completed/failure였다. 이번에 CI 실패 원인을 재검증하거나 재실행하지 않았다. 코드/API/DB 변경·배포 영향 없음. GitHub 리뷰 게시·커밋·푸시·병합 없음.
- 공유 인계: 현재 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-published-roster`, HEAD `209de0db506429050885968e9cd2c3d81cc14192`를 보존했다. 기존 Android 수정 4개와 미추적 `nurseflow.mv.db`는 검토·수정하지 않았다. 이 절만 현재 공유 브랜치에 남기며, A3 구현자가 위 두 계약 항목을 반영한 뒤 재검토한다. 기록 시각 2026-11-10 14:00 KST 이후. 기존 tracked diff 해시는 `abca27372b8422cbb7f54dec0e67a5019c213e961a89a5f58cfb9e486247c36b` (`git diff HEAD -- . ':!docs/work-status.md' | Out-String`의 UTF-8 바이트 SHA256; PowerShell 줄바꿈 포함).

## 미커밋 기록 보존·로컬 브랜치 정리 (2026-11-10, Codex)

- 사용자 요청으로 위 A3 계약 검토 미커밋 기록을 현재 B2 브랜치의 별도 문서 커밋에 보존한다. B2 구현 커밋 `55fb61b`와 가상 검증 결과는 변경하지 않았다.
- `git fetch origin --prune` 성공. GitHub PR #33·#34의 `merged=true`·대상 `dev`, 병합 커밋 `fa47a5a`·`d1b8729`의 `origin/dev` 포함, 로컬 A2 head의 원본 PR head 포함을 확인한 뒤 로컬 `feat/auth-web-session-v2`·`feat/android-published-roster`만 삭제했다. 원격 브랜치는 삭제하지 않았다.
- 공유 worktree는 `C:\Users\jjang\IdeaProjects\NurseFlow`, 현재 브랜치는 `feat/web-foundation-session`으로 유지한다. `main`·`dev`·B2 브랜치와 미추적 `nurseflow.mv.db`를 보존했다. 문서 변경만이므로 앱·서버 테스트와 브라우저·Kakao·CI 검증은 재실행하지 않았다.

## B2 Draft PR·CI 기록 정정 (2026-11-10, Codex)

- [PR #39](https://github.com/gerrmang/NurseFlow/pull/39)는 open·Draft, 대상 `dev`다. 구현 head `0f4ddd4344cd80eecd93ea11af471e87c70471fc`의 [Verify 42826773185](https://github.com/gerrmang/NurseFlow/actions/runs/42826773185)에서 Backend tests·Android debug build·Web checks 모두 성공을 GitHub API로 확인했다.
- `git diff cd66358 0f4ddd4 --name-only -- web`는 검증 문서와 `src/api/schema.gen.ts`만 반환했다. 생성 타입은 `import type`으로 소비된다. 번들 크기만으로 런타임 동일성을 단정하던 표현을 이 diff 근거로 정정했다. 서버 변경·실제 Kakao 재검증 여부와 구분한다.
- A5 `bdc96a6` 반영 완료. A 후속 진단·초대 계약 `be6d78c` 미반영에 따른 Draft·KOE010 재확인 대기를 유지한다. 미검증 브라우저·계정·코드 리뷰 항목도 그대로다.
- 검증: GitHub PR·작업별 CI 결과, Git diff와 타입 참조 확인. 문서만 수정해 로컬 앱·서버·기기 검사는 재실행하지 않았다. 후속 문서 커밋의 CI는 푸시 뒤 최종 head 기준으로 확인한다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-foundation-session`에서 문서만 커밋·푸시한다. 사용자 DB를 보존하고 다른 기능 커밋·원격 병합·Draft 해제·배포는 하지 않는다.

## B2 PR #39 A 리뷰 수정 (2026-11-11, Codex)

- 기준 `00724e4`, A 리뷰 `5846276341`·`5852145291`을 코드와 대조했다. 병동 목록 병원명(P3), Web Locks 접속·브라우저 지원 조건(P3), BroadcastChannel 로그아웃 전파(P3), no-referrer(P4), 동시 로그인 재시도 안내(P4)를 반영했다. 서버·API·DB는 수정하지 않았다.
- Windows typecheck·lint 성공. WSL의 분리 사본에서 npm ci·api:check·lint·typecheck·테스트 55/55·build 모두 성공. 즉시 탭 로그아웃과 늦은 refresh 폐기·알림 실패·채널 검증, 동명 병동/nullable 병원명 회귀가 포함된다.
- audit: 개발 체인 moderate 10개 패키지, high/critical 0, omit=dev 0. 제안된 주 버전 하향·force 변경은 적용하지 않았고 호환 수정판 대기를 기록했다. 실제 브라우저·Kakao·실서버 검사는 이번 수정 후 재실행하지 않았다.
- 공유 인계: `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/web-foundation-session`에서 B2만 커밋·푸시한다. DB·Windows node_modules를 보존한다. Draft와 A 후속 미병합 대기를 유지하며, 수정 후 CI·재리뷰를 별도로 확인한다. 리뷰 원문과 수정 상세는 web/B2-verification.md에 있다.

## B3a Draft PR·CI 기록 정정 (2026-11-10, Codex)

- [PR #38](https://github.com/gerrmang/NurseFlow/pull/38)는 open·Draft, 대상 `dev`다. 구현 head `878cc713ac98e6c6eab96d01386f1d08498951aa`의 [Verify 42826307031](https://github.com/gerrmang/NurseFlow/actions/runs/42826307031)에서 Backend tests·Android debug build 모두 성공을 GitHub API로 확인했다. B2 웹 코드는 이 브랜치에 없으므로 Web checks는 없다.
- A5 `bdc96a6` 반영 완료. A 후속 진단·초대 계약 `be6d78c` 미반영에 따른 Draft·계약 재대조 대기를 유지한다. 실제 Kakao 통합 후 재검증·전체 코드 리뷰 등 기존 제한도 유지한다.
- 검증: GitHub PR·작업별 CI 결과 확인과 문서 변경의 `git diff --check`. 문서만 수정해 로컬 앱·서버·기기 검사는 재실행하지 않았다. 후속 문서 커밋의 CI는 푸시 뒤 최종 head 기준으로 확인한다.
- 공유 인계: 같은 `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-invite-join`에서 문서만 커밋·푸시한다. 사용자 DB와 미추적 `web/` 잔여물을 보존했다. B2 커밋을 섞지 않았고 병합·Draft 해제·배포는 하지 않는다. 두 브랜치 기록 정리 후 원래 B2 브랜치로 복귀한다.

## B3a PR #38 A 리뷰 수정 (2026-11-11, Codex)

- 기준 `155ec1c`, A 리뷰 `5846275956`·`5852145036`을 코드와 대조했다. nullable 병원명 DTO·병동 목록 표시(P3), 링크 설정 없는 빌드의 발급·재발급 사전 차단(P3)을 반영했다. 개발 스킴 가로채기·로그인 중 프로세스 종료 시 초대 유실(P4)은 알려진 제한으로 README에 명시했다. API·DB·서버 fixture 변경 없음.
- 단위 검사 76개 통과(실패·오류·건너뜀 0), 앱·기기 테스트 APK 빌드 성공, Lint 오류 0·기존 경고 33개. JSON 테스트 설정 정리 후 단위·Lint 재실행 성공. `git diff --check` 통과.
- 신규 기기 화면 테스트는 컴파일만 확인했다. 읽기 전용 Pixel_10을 두 번 시작했지만 ADB offline으로 기기 검사 실행은 시작하지 못했다. 이번 에뮬레이터 프로세스만 종료했다. 실제 Kakao·실서버 E2E·백엔드 검사는 이번 수정 뒤 재실행하지 않았다.
- 공유 인계: `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-invite-join`에서 B3a만 커밋·푸시한 뒤 원래 B2 브랜치로 복귀한다. DB·Windows node_modules 잔여물을 보존한다. Draft·A 후속 미병합 대기를 유지하고 수정 후 CI·재리뷰는 별도로 확인한다.

## B2 PR #39 최신 dev 통합·병합 준비 (2026-11-11, Codex)

- B3a PR #38의 dev 병합 `e4ca9bf`를 확인하고 현재 B2 브랜치에 병합했다. 공유 현황 문서 충돌만 발생했으며 B2·B3a 검증·리뷰·인계 기록을 모두 보존했다. 기능 코드는 충돌 없이 반영됐다.
- 이전 dev `bdc96a6`과 새 dev `e4ca9bf` 사이에 web·OpenAPI·서버 운영 코드·CI 변경은 없다. B2 A 리뷰 수정 `d47eb38`은 유지한다. PR #39는 조회 시 Ready였으므로 Draft로 되돌리지 않는다. A의 수정 재확인과 기존 APPROVED 리뷰도 확인했으며 새 head의 승인 강제 여부로 해석하지 않는다.
- 통합 후 WSL에서 오프라인 npm ci·api:check·lint·typecheck·테스트 55/55·build 모두 성공. git diff --check 통과. 기기·실제 Kakao·실서버·audit은 이번에 재실행하지 않았고, 개발 서버도 시작하지 않았다. 최종 GitHub CI는 푸시 후 확인한다.
- A 진단·초대 계약 후속 be6d78c는 여전히 미반영이며 별도 후속으로 유지한다. B3a는 이미 병합됐고 B2만 최신 dev 기준으로 갱신한다. 병합·자동 병합·배포는 하지 않는다.
- 공유 인계: C:\Users\jjang\IdeaProjects\NurseFlow, feat/web-foundation-session에서 통합 커밋·푸시한다. 기존 nurseflow.mv.db와 사용자 로컬 설정·잔여물을 보존한다.

## B4a PR #44 리뷰 수정 (2026-11-13, Codex)

- 검토 기준 f7ffc1e의 P2·P3를 수정했다. 닫기·뒤로가기·목록 확인을 공통 leave 경로로 연결해 reset 전에 소속 목록을 재조회한다. 성공한 서버 목록을 그대로 사용해 비활성 소속 잔존을 없앴고, 완전한 목록이 없으면 생성 후 재조회 실패를 표시한다. 병동명 100자 초과는 안내하고 전송하지 않는다.
- 테스트 병원 source를 HIRA로 맞췄다. 자기 자신 상태 비교를 제거하고 고정 키 ViewModelProvider와 유지된 ViewModelStore에서 draft·유실 상태·재시도 키를 검사했다. 이는 실제 Activity 회전 검증과 구분한다. 응답 유실 후 종료·목록 권위·실패·100/101자 회귀를 추가했다.
- JDK 21, android에서 .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon: BUILD SUCCESSFUL. 단위 XML 144개, 실패·오류·건너뜀 0. 최초 실행은 변경 전 기대값이 남은 테스트 1개가 실패해 서버 목록을 기준으로 기대값을 정정한 뒤 통과했다. git diff --check 통과.
- 기기 UI·실서버·백엔드 전체 검사는 이번에 재실행하지 않았다(클라이언트 수정). 독립 재리뷰는 미실행, 병합하지 않는다. 서버의 기존 wardId 제공·INVALID_TIME_ZONE 계약 설명은 리뷰에 명시된 A 후속 범위로 남긴다.
- API/DB/환경 변경 없음. Android 빌드에만 영향, 배포 없음. 롤백은 이번 수정 커밋 revert. 같은 공유 worktree에서 이 PR 브랜치에 커밋·푸시 후 B3b 브랜치로 이동해 #45를 처리한다. 사용자 nurseflow.mv.db는 보존한다.

## B4a 최신 dev 통합 (2026-11-13, Codex)

- 사용자 요청으로 기존 feat/android-ward-create a57fa65에 최신 origin/dev e1ce681(A8 #43)을 병합했다. 충돌 없음. Android 기능 코드는 바꾸지 않았으며 서버·OpenAPI·schema.gen.ts는 dev와 동일하다. 리뷰 수정 커밋을 보존했다.
- JDK 21, android: .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon BUILD SUCCESSFUL, 80개 작업 중 6개 실행·74개 UP-TO-DATE. 단위 작업은 UP-TO-DATE이므로 이번에 144개를 새로 실행했다고 기록하지 않는다.
- web: npm run api:check, lint, typecheck, npm test -- --run, build 모두 성공. 이 브랜치 웹은 B3b를 포함하지 않아 3파일 55개 통과, 실패·건너뜀 0. B3b의 252개 결과와 구분한다.
- 통합된 backend는 B3b와 바이트 기준 동일함을 git diff origin/dev로 확인했다. 같은 dev 통합 서버에서 직전에 실행한 JDK 21 backend test 결과는 262개 중 257개 통과·5개 opt-in 건너뜀·실패/오류 0이며 중복 실행하지 않았다. git diff --cached --check 통과.
- B3b는 ec6e8cd로 dev 통합을 커밋·푸시했다. 두 PR의 GitHub 조회 결과 mergeable=MERGEABLE, reviewDecision=REVIEW_REQUIRED, mergeStateStatus=BLOCKED다. 승인 없이 즉시 병합 가능하다고 주장하지 않는다. 최종 CI는 푸시 후 별도 확인한다.
- API/DB/환경 추가 변경 없음(A8 계약·스키마를 통합). 배포 없음. 되돌리기는 이 병합 커밋을 첫 부모 기준 revert. 기기·실서버·실제 Kakao 검사는 재실행하지 않았다. 기존 미완료 인수 검증·재리뷰 제한은 유지한다.
- 공유 인계: 같은 C:\Users\jjang\IdeaProjects\NurseFlow, feat/android-ward-create에서 통합·검증 기록을 커밋·푸시한다. 사용자 nurseflow.mv.db 보존. dev/main 직접 푸시·PR 병합·자동 병합 설정 없음.

## B3b PR #45 리뷰 수정 (2026-11-13, Codex)

- d808aca 리뷰 P1: refresh로 signedIn userId가 바뀌면 beginEpoch로 진행 중 요청을 중단하고 새 세션을 적용한다. 가입·발급·재발급·철회 전송 전과 401 뒤 갱신을 검사하는 5개 회귀 테스트가 수정 전 모두 실패, 수정 후 통과했다. 최초 restore와 같은 계정 refresh의 기존 검사는 유지했다.
- P4: 가입 대기 중 새 hash 링크는 busy를 route state에 남겨 거절·재진입 안내를 표시하고 기존 요청을 유지한다. 토큰은 주소에서 제거하며 새로 저장하지 않는다.
- P3: 관리자 오류 문구 분리, 가입 403의 계정·초대 안내, 소속/연결 충돌의 보수적 안내, 빈 초대 화면의 새로고침·로그인 뒤 복구 안내, 가입 배너 history state 소비를 반영했다. 미래 입사 연월 판단은 서버 병동 시간대에 맡기고 형식·1950년 하한 검사만 유지한다.
- USER_NOT_FOUND의 401 사용과 소속 상태별 오류 코드 분리는 리뷰에 명시된 A 후속이다. 현행 재인증 처리를 유지한다. 이번에 서버·OpenAPI를 변경하지 않았다.
- web에서 npm run api:check, npm run lint, npm run typecheck, npm test -- --run, npm run build 모두 성공. 8파일 252개 통과, 실패·건너뜀 0, JS gzip 94.51 kB. git diff --check 통과. 중간 실행에서 기존 문구 기대값 3개가 실패해 새 안내에 맞춘 뒤 통과했다.
- 실제 브라우저·Kakao·실서버·backend 전체 검사는 이번 수정에서 재실행하지 않았다. 실제 Kakao 2계정 인수 검증과 독립 재리뷰는 미완료이며 병합하지 않는다. API/DB/환경·추가 저장소 변경 없음. 웹 정적 빌드에 영향, 배포 없음, 롤백은 수정 커밋 revert.
- B4a는 같은 공유 worktree에서 기존 feat/android-ward-create에 a57fa65를 커밋·푸시했다. 단위 144개·앱/테스트 APK·Lint 통과, Verify 43422104112 전체 성공 확인. B3b에는 Android 수정 커밋을 섞지 않았다.
- 인계: 같은 C:\Users\jjang\IdeaProjects\NurseFlow, feat/web-invite-join에서 이 수정만 커밋·푸시한다. nurseflow.mv.db 보존. PR #44/#45의 서버 후속·미실행 인수 검증·독립 재리뷰를 남긴다.

## B3b 최신 dev 통합 (2026-11-13, Codex)

- 사용자 요청으로 기존 feat/web-invite-join의 ccaf77b에 최신 origin/dev e1ce681(A8 PR #43)을 병합했다. 충돌 없음. 서버·OpenAPI·schema.gen.ts는 dev와 동일하며 기존 초대 기능·계정 전환 방어를 보존했다.
- web: npm run api:check, lint, typecheck, npm test -- --run, build 모두 성공. 8파일 252개 통과, 실패·건너뜀 0, JS gzip 94.51 kB. backend: JDK 21 .\gradlew.bat test --no-daemon BUILD SUCCESSFUL, XML 262개 중 257개 통과·5개 opt-in 건너뜀·실패/오류 0. git diff --cached --check 통과.
- API/DB/환경의 추가 변경 없음(A8에 이미 포함된 계약·스키마를 통합). 배포 없음. 롤백은 이 병합 커밋을 첫 부모 기준 revert. 브라우저·실제 Kakao·기기 검사는 재실행하지 않았다. 기존 실제 Kakao 2계정 인수·독립 재리뷰 미완료를 유지한다.
- 같은 공유 worktree에서 이 브랜치에 통합·검증 기록을 커밋·푸시하고 B4a로 이동한다. 사용자 nurseflow.mv.db 보존, dev/main에 직접 푸시·PR 병합·자동 병합 설정 없음. 최종 PR CI와 GitHub 충돌 상태는 푸시 후 별도 확인한다.

## A13 월 목표시간·휴일 달력·시간 진단 구현 (2026-11-24, Claude)

- 브랜치 `feat/target-hours`(최신 `dev` `548552b`에서 분기). 결정 사항 14 사용자 답변 반영: 자정을 넘는 근무는 끝나는 날짜의 월에 전부 귀속(잠정), 시연 기본값 하루 480분(30일·휴식 9일 = 168시간)이며 관리자가 병동별로 변경, 공휴일은 외부 API에서 가져와 DB에 저장해 사용.
- API: `GET/PUT /wards/{wardId}/target-hour-policy`, `GET/PUT /wards/{wardId}/holiday-calendar?month=`, `POST /wards/{wardId}/holiday-calendar/refresh?month=`, `GET /rosters/{rosterId}/hour-diagnostics`. 모두 관리자 전용. 정책·달력 쓰기는 병동 공통 정책 ETag(If-Match)를 쓰고 초안 재검증 표시는 하지 않는다(진단은 조회 때 계산).
- DB: `ward_target_hour_policies`, `ward_calendar_days`, `public_holidays`, `public_holiday_imports`. 오류 코드 `INVALID_TARGET_HOUR_POLICY`, `INVALID_HOLIDAY_CALENDAR`, `HOLIDAY_MONTH_OUT_OF_RANGE`, `HOLIDAY_SOURCE_NOT_CONFIGURED`, `HOLIDAY_SOURCE_UNAVAILABLE`.
- 공휴일: 한국천문연구원 특일 정보(data.go.kr 15012690, `getRestDeInfo`). 환경 변수 `NURSEFLOW_HOLIDAY_API_KEY`(없으면 `NURSEFLOW_HOSPITAL_API_KEY`), 매일 03:30(Asia/Seoul) 이번 달~12개월 뒤 갱신(`NURSEFLOW_HOLIDAY_REFRESH_CRON`, `-`이면 끔), 관리자 즉시 갱신은 작년~내년·10분 재사용 제한. 조회·진단은 DB만 읽고, 실패하면 저장값 유지, 병동 항목이 같은 날짜의 공휴일보다 우선한다.
- 검증: JDK 21 `./gradlew check`(LC_ALL=C.UTF-8) 289개 중 284개 통과·5개 opt-in 건너뜀·실패 0. web `npm run api:check`·`lint`·`typecheck`·`npm test -- --run`(394개 통과·3개 건너뜀)·`build` 성공. 기본 POSIX 로캘에서는 기존 병원 CSV 한글 파일명 테스트가 실패하므로 UTF-8 로캘로 실행했다.
- 코드 리뷰(자체, 2회): 1차 6건(월 경계 연차, 주말 연차 이중 계산, 기본값 저장 시 revision 증가, 반차·SOF 중복, 진단 ETag, 필수값 오류 코드)과 2차 9건(일일 갱신 중단, 동시 갱신 키 충돌, 연차·SOF 중복, 휴일 SOF, 관리자 갱신 남용, 병동 이동 간호사 누락, 공휴일 종류 병합, 스키마 주석, 이 기록 누락)을 모두 반영했다. Codex 독립 리뷰는 아직 없다.
- 미검증·제한: 실제 data.go.kr 호출(키 없음, 가짜 서버로만 검증), 월 중 입사·비활성 간호사의 목표 비례 계산(미지원, 전체 목표로 표시), 근무 칸이 시간을 따로 저장하지 않아 공개본도 근무 유형의 현재 환산시간으로 계산, 전월이 공개되지 않으면 전월 말일 Night를 세지 않음(`previousMonthPublished=false`). Android·웹 화면은 후속(B10).
- 실제 공휴일 API 확인(2026-11-24): 사용자 Mac의 IntelliJ 실행 구성에 있는 `NURSEFLOW_HOLIDAY_API_KEY`로 `getRestDeInfo`를 2026-09·2026-10·2027-01·2027-02 네 번 호출해 모두 HTTP 200·resultCode 00을 받았다. 대체공휴일은 이름에 "대체"가 붙은 별도 항목으로 왔다. 키 값은 출력하지 않았다. 서버를 띄워 저장까지 확인하지는 않았다.
- dev 통합: A12 PR #56 병합본(`74886b6`)을 병합했다. `openapi.yaml`은 dev 쪽에 A13 변경을 다시 적용했고, `ApiTestBase.kt`의 초기화 표 목록은 양쪽 표를 모두 포함하도록 합쳤다. `schema.gen.ts`는 다시 생성했다.
- PR 전 3차 리뷰(dev 통합 후) 9건 중 8건 반영: 자정을 넘는 연차 이중 계산(하루의 절반 이상을 덮은 날짜만 셈), 공휴일 갱신 잠금을 저장 단계로 한정, 관리자 갱신 시도(실패 포함)를 원자적으로 10분 제한, 실제 호출 때만 감사 기록, 갱신 응답을 한 읽기 트랜잭션으로, 달력 `days`의 null 항목 400, 정책 본문 검사를 관리자 확인 뒤로, 고정 Night 전담 목표의 주말 휴가 환산. 남긴 1건: 달력 GET ETag가 월과 무관한 정책 revision이라 If-None-Match를 보내면 다른 달에 304가 날 수 있다(`Cache-Control: no-store`라 브라우저는 보내지 않음, 후속).
- 최종 검증: JDK 21 `./gradlew check`(LC_ALL=C.UTF-8) 300개 중 295개 통과·5개 opt-in 건너뜀·실패 0. web api:check·lint·typecheck·테스트 394개 통과(3개 건너뜀)·build 성공.
- PR #58 리뷰(hyen11, P2 2건) 반영: 달력 응답의 목록과 월 목표를 같은 조회 결과로 계산, 다른 달 공개본의 장기 연차를 저장 월이 아닌 대상 월과의 겹침으로 조회(9월 공개본의 9/30~11/3 연차 회귀 테스트).
- A14 통합(2026-11-24): A14 PR #57 병합본(`db5e412`)을 병합했다. `schema.sql`은 A13 표 뒤에 A14 `cross_team_support_allowances`를 두었고, `ApiTestBase.kt` 초기화 목록은 양쪽 표를 모두 포함한다. `openapi.yaml`은 dev 쪽에 A13 변경(472줄 추가, 삭제 없음)을 다시 적용했고 `schema.gen.ts`는 다시 생성했다. 동작 변경은 없다. 검증: `./gradlew check`(LC_ALL=C.UTF-8) 306개 중 301개 통과·5개 opt-in 건너뜀·실패 0, web api:check·lint·typecheck·테스트 394개 통과(3개 건너뜀)·build 성공.
- PR #58 재검토(hyen11, P2 1건·P3 1건) 반영: 공휴일 응답은 `totalCount`가 0으로 명시된 경우만 빈 달로 저장하고, `body`·`totalCount` 누락이나 항목 수 불일치는 `HOLIDAY_SOURCE_UNAVAILABLE`로 처리해 저장값을 유지한다. 연차 날짜는 대상 월의 날짜만 확인한다(3일 이상 연차는 가운데 날짜를 온전히 덮으므로 시작일 대체 규칙은 2일 이하 연차에만 적용). 테스트 3개 추가. 검증: `./gradlew check`(LC_ALL=C.UTF-8) 309개 중 304개 통과·5개 opt-in 건너뜀·실패 0.
- 인계: A14가 먼저 병합되었으므로 PR #58은 최신 dev 기준으로 병합 가능하다.
