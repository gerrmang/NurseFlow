# B7a Android 관리자 근무 설정 검증

검증일: 2026-11-25 (KST). 구현은 전날 계획에서 이어 진행했다.

## 범위와 기준

- 최신 `origin/dev` `db5e412` 기반 `feat/android-schedule-settings`, 같은 공유 worktree에서 구현했다. B7b 브랜치는 보존하고 해당 미병합 웹 변경을 쌓지 않았다.
- A8 근무 유형·영향 preview, A9 팀 수요·선임 칸, A10 근무 형태와 기존 초안·승인 부재 API를 소비한다. 변경 경로는 Android와 공유 작업 현황이다. 서버·OpenAPI·DB·환경변수·의존성 변경 없음.
- 내 병동과 팀 관리에서 관리자 근무 설정에 진입한다. 근무 유형 생성·수정·비활성화·재활성화, 월별 초안 조회·명시 생성, 팀 수요 전체 교체, 승인 VY·반차 CRUD, 간호사 근무 형태 조회·저장을 제공한다.
- 기존 OTHER는 조회·비활성화만 지원한다. 수요 조회에 필요 자격이 없어 기존 칸을 새 입력으로 자동 복원하지 않는다. 기존 수요를 읽기 전용으로 표시하고 새 목록 전체 교체를 체크·재확인한다. 기존 배치가 있으면 교체를 막는다.
- 희망 오프, OF/SOF 입력, 팀 간 지원 허용, 휴일·목표시간 정책, Night 순환, 배치 편집·자동배치·확정은 후속이다. 입력 단계의 기본 검사는 편의를 위한 것이고 필수 규칙은 서버에서 판정한다.

## 오류·세션·시각 처리

- 정책 ETag와 초안 ETag를 별도로 보관하고 서버 헤더를 그대로 If-Match에 사용한다. 초안과 부재를 전후 초안 조회로 묶어 revision이 다르면 편집을 차단한다. 수정 preview는 같은 입력·policyRevision에서만 저장에 사용할 수 있다.
- 쓰기는 직렬화하며 연결 실패 시 자동 재전송하지 않는다. 응답 유실·5xx·성공 응답 파싱 실패·무본문 응답은 결과 미확인으로 처리한다. 서버 결과를 재조회하고 사용자가 확인하기 전까지 새 변경을 막는다. 401 갱신은 기존 세션 바인딩을 유지한다.
- 계정·세션 세대가 바뀌면 요청과 UI 상태를 폐기한다. 관리자 소속을 조회 및 쓰기 전에 확인하고 권한 오류 시 화면을 종료해 병동 목록을 갱신한다. 화면 종료 중 전송된 쓰기는 결과 미확인으로 남기고 재진입 시 조회한다.
- 승인 부재는 병동 시간대로 표시·입력한다. 미변경 endpoint는 원본 시각 문자열을 그대로 보내 초·나노초·오프셋과 DST 회차를 보존한다. 새로 입력한 DST 누락·중복 시각은 차단한다. 사유는 입력·기록하지 않는다.

## 자동 검사

- Android JDK 21, `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`: **BUILD SUCCESSFUL (1분 36초, 80개 작업 중 16개 실행·64개 UP-TO-DATE)**. 최종 XML 총 228개 중 **226개 통과·실서버 opt-in 2개 건너뜀·실패/오류 0**. 앞선 225개 결과 뒤 잘못된 대상 월 안내 검사를 추가했다. Lint 오류 0·경고 27개.
- B7a 기본 JVM 검사 30개: HTTP 경로·헤더·false/0 직렬화·연결 유실 비재전송 6개, ViewModel snapshot·preview·충돌·동시 쓰기·권한/세션 상실·응답 유실·재진입·근무 형태·부재 원본 보존·잘못된 연월 안내 18개, 병동 시간대·DST·초/나노초·날짜 경계 6개.
- 실제 Spring·별도 메모리 H2·가상 OIDC에서 `NURSEFLOW_B7A_E2E=1`, `:app:testDebugUnitTest --tests '*ScheduleSettingsRealServerTest' :app:assembleDebugAndroidTest --offline --no-daemon`: **1개 통과·실패/건너뜀 0**, BUILD SUCCESSFUL.
- 실제 서버 인수: 공식 병원 목록으로 가상 병동 생성, 팀·간호사·선임 등록, 근무 유형 생성·preview·수정·비활성화·재활성화·Night 자정 경과, 오래된 정책/초안 ETag 412, 팀 수요 2칸·선임 1칸, 부재 생성·수정·삭제·나노초 보존, 근무 형태 저장·재조회, 새 ViewModel/재진입 복원, 간호사의 변경 API 403을 확인했다.
- 초기 실서버 검사에서 간호사 근무 유형 GET을 403으로 기대해 1건 실패했다. 현행 OpenAPI는 병동 구성원의 조회를 허용하므로 200 조회·403 변경으로 정정 후 통과했다. 서버 코드는 변경하지 않았다.
- 새 실서버 UI 검사 컴파일 중 JVM 전용 MemoryTokenStore 참조를 기존 기기용 TestTokenStore로 정정했다. 실제 저장된 사용자 세션을 읽지 않는다.

## 기기 인수

- 기존 Pixel_10을 `-read-only -no-window -no-audio -no-snapshot -gpu swiftshader_indirect`로 별도 실행했다. 원본 AVD와 사용자 파일 DB·trace를 보존했다.
- `ScheduleSettingsScreenUiTest`: 직접 instrumentation **OK (4 tests)**, 모든 메서드 status code 0. 영향 preview 전 저장 잠금·preview 후 저장, 빈 수요 전체 교체의 체크·재확인, 부재 구분만 변경할 때 원본 나노초 보존, 근무 형태 조회 실패 시 저장 차단·복구를 확인했다. 이 4개는 가상 API 화면 검사다.
- `ScheduleSettingsRealServerUiTest`: **OK (1 test)**. 에뮬레이터의 실제 화면에서 유형 환산 420분 preview·저장·서버 GET, N 전담 저장·재조회, 초안 생성·팀 수요 2칸/선임 1칸, 승인 반차 저장·새로고침 유지 확인. 가상 OIDC 외 API·서버·DB는 실제 구현이다. 최초 시도는 기기의 종료된 로컬 프록시 연결로 실패했고 read-only 임시 기기의 HTTP 프록시 설정만 해제한 뒤 통과했다.
- Activity 재생성 검사는 최초 목업 주소 조회를 메인 스레드에서 해 NetworkOnMainThreadException이 발생했다. 주소를 테스트 스레드에서 구하도록 검사 코드를 정정했다. 최종 APK의 통합 기기 검사 결과는 아래에 기록한다.
- 최종 제품 APK의 6개 묶음 실행은 가상 UI 4개·실서버 UI 1개 통과, 재생성/재진입 검사 1개 실패였다. 실제 Activity 재생성 후 입력 보존과 추가 조회 없음 검사는 통과했고, 재진입 시 HTTP 요청 도착만 기다린 뒤 화면 렌더링 전에 필드를 찾아 실패했다. 재진입도 필드 렌더링을 기다리도록 검사 대기 조건을 정정했다. 제품 코드는 변경하지 않았다.
- 대기 조건 수정 뒤 `:app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon` **BUILD SUCCESSFUL (39초)**. 수정된 `ScheduleSettingsRotationTest` 직접 instrumentation **OK (1 test)**, status code 0. 실제 MainActivity 재생성에서 입력 유지·추가 조회 없음, 뒤로가기·재진입 시 서버 재조회·이전 폼 폐기를 확인했다. 최종 기기 인수는 묶음의 5개 통과와 수정 후 별도 1개 통과로 **6개 시나리오 모두 통과 확인**이며, 6개 묶음 전체를 다시 실행한 것은 아니다.

## 재현

`B5a-verification.md`의 루프백 OIDC fixture와 서버 실행 절차를 사용하되 DB는 `jdbc:h2:mem:b7a_fictional_e2e;DB_CLOSE_DELAY=-1`로 분리한다. 서버는 18091, 공급자는 18090을 사용하고 실제 계정·사용자 DB와 연결하지 않는다. 서버 JAR는 최신 dev로 `backend/gradlew.bat bootJar --offline --no-daemon` 빌드한다.

Android JVM 실서버 검사는 `NURSEFLOW_B7A_E2E=1`과 `--tests '*ScheduleSettingsRealServerTest'`로 실행한다. 기기 검사는 APK 설치 뒤 다음 명령을 사용한다.

```powershell
adb shell am instrument -w -r -e class com.nurseflow.app.feature.schedule.ScheduleSettingsScreenUiTest com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner
adb shell am instrument -w -r -e b7aRealServer 1 -e class com.nurseflow.app.feature.schedule.ScheduleSettingsRealServerUiTest com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner
adb shell am instrument -w -r -e class com.nurseflow.app.feature.schedule.ScheduleSettingsRotationTest com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner
```

실제 서버 UI 검사는 Android 에뮬레이터의 `10.0.2.2:18090/18091`로 연결한다. 검사 종료 후 이번 서버·공급자·임시 에뮬레이터만 종료해 메모리 DB·가상 세션을 폐기한다.

최종 정리: 명령행·소유 포트를 대조한 이번 Java/가상 공급자와 임시 에뮬레이터를 종료했다. 18090/18091 포트 해제와 ADB 기기 목록이 비었음을 확인했다. 사용자 DB·trace·원본 AVD는 보존했다.

## 제한·배포·되돌리기

- 실제 Kakao·물리 기기·운영·전체 Android 기기 회귀·다중 관리자 실제 기기 경합·프로세스 강제 종료는 이번에 검증하지 않았다. 가상 인증 실서버 검사와 실제 인증 성공을 구분한다.
- 독립 코드 리뷰와 GitHub PR/CI는 아직 수행하지 않았다. 기존 Android 테스트의 간헐적 실패 기록은 B7b 현황을 참고한다.
- 근무 형태는 마지막 저장 우선이다. 기존 배치 해제는 B10a 후속이다. 입력은 회전 중 보존하고, 화면 종료 뒤 다시 들어오면 서버에서 새로 조회한다. 프로세스 재시작은 서버 재조회로 복구한다.
- Android 앱 빌드에 영향. 서버·DB·환경 계약 변경 및 수동 배포 없음. 롤백은 B7a Android 변경 revert와 이전 APK 복원이다.
