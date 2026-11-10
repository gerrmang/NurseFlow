# B9a 공개 근무표 검증과 PR 초안

## 변경 목적과 내용

Android 병동 선택에서 공개 근무표의 본인/전체 조회, 월 이동, 공개 없음·본인 배정 없음·권한 오류·연결 오류 표시를 제공한다. 병동 재진입 시 현재 월·모드를 유지하면서 새 공개본을 조회한다. 시연 데이터는 별도 진입으로만 제공한다.

관련 이슈: 없음. 기준은 `docs/feature-breakdown.md` B9a다. 같은 `feat/android-published-roster` 브랜치에서 구현과 후속 검증을 이어갔다.

## 검증 구조

- `PublishedRosterDeviceTest`: 실제 Android Activity·Compose·ViewModel·HTTP·세션을 사용한다. `IsolatedSessionTestRunner`와 메모리 `TestTokenStore`를 사용해 개발자의 저장 세션을 읽지 않는다.
- `AndroidRosterE2eServerTest`: 실제 Spring 애플리케이션을 loopback `18083`에서 실행한다. 외부 Kakao만 `FakeOidcProvider`로 대체하며, 공개 조회·권한과 확정은 실제 서버 코드다. H2 메모리 DB에 가상 사용자·멤버십·간호사·근무 유형을 준비한다. 테스트 제어 서버 `18084`에서 실제 `RosterService`로 초안 생성·수요 입력·배정·확정·재공개한다.
- 제어 서버는 opt-in 테스트 클래스에만 있으며 배포물에 포함되지 않는다. 운영 API·DB 스키마·규칙은 변경하지 않았다. 사용자 로컬 DB는 사용하지 않는다.
- 시나리오: 공개 전 오류 코드/안내 → 공개 후 재진입 → 본인/전체 전환 → 다음 달 공개 없음 → 원래 월 복귀 → 재공개 후 같은 월·모드 재진입 → 본인 배정 없음 → 로그아웃·권한 제한 계정 전환 → 전체 조회 403 및 탭 숨김·본인 조회 성공.

## 실행 결과 (2026-11-09, Codex)

- Android `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: `BUILD SUCCESSFUL`. JVM 작업은 기존 성공 결과를 재사용(`UP-TO-DATE`), XML 보고서 39개·실패/오류/건너뜀 0을 확인했다. 앱·기기 테스트 APK 빌드 성공. Lint 오류 0·기존 경고 33개.
- 수정 후 `:app:connectedAndroidTest`: 별도로 시작한 읽기 전용 Pixel_10(Android 17, emulator-5556)에서 `BUILD SUCCESSFUL`. XML 기준 23개 중 18개 통과·5개 건너뜀·실패/오류 0. Gradle 콘솔은 건너뜀 중복 집계로 28개로 표시하므로 수치는 XML을 따른다. 건너뜀은 프로세스 재시작 단계 2개, B1 실서버 세션 검사, OAuth 거절 복귀, B9a opt-in 검사다.
- B9a opt-in 검사는 이후 별도로 실제 실행: `PublishedRosterDeviceTest` **`OK (1 test)`, 11.480초**, 테스트 status 0. 대응 Spring `AndroidRosterE2eServerTest`도 **`BUILD SUCCESSFUL`**, 1개 통과. 위 시나리오 전체가 가상 계정과 실제 로컬 서버에서 성공했다. 실제 외부 Kakao 인증을 실행한 것은 아니다.
- 백엔드 `test --no-daemon`: 91개 중 89개 통과·2개 건너뜀(opt-in 기기 fixture 2개)·실패/오류 0. 이 전체 검사 결과는 별도 opt-in 실행 전에 확인했다(뒤의 필터 실행은 Gradle 결과 폴더를 덮어쓴다).
- 초기 fixture 컴파일에서 `replaceRequirements`의 반환 타입(`RosterSummary`)을 `RosterView`에 대입한 오류를 수정했다. 첫 E2E 실행은 뒤로가기 직후 아직 남아 있는 병동 제목을 카드로 오인해 클릭에 실패했다. 클릭 가능한 노드를 기다리도록 테스트만 수정하고, 새 메모리 DB의 fixture로 재실행해 통과했다. 앱 런타임 변경은 추가하지 않았다.
- `git diff --check`: 통과(줄바꿈 변환 경고만). GitHub CI는 실행·확인하지 않았다.

## 재현 명령

Android SDK의 `adb.exe`를 PATH에 설정한다. 별도 임시/읽기 전용 에뮬레이터를 사용하고 실제 사용자의 기기에 테스트 APK를 설치하지 않는다.

첫 터미널에서 저장소의 `backend/`로 이동해 실행한다. 기기 검사의 `/finish` 호출까지 최대 10분 기다린다.

```powershell
$env:NURSEFLOW_ROSTER_E2E = 'true'
.\gradlew.bat test --tests com.nurseflow.support.AndroidRosterE2eServerTest --rerun --no-daemon
```

두 번째 터미널에서 `android/`로 이동해 실행한다. loopback 제어 포트가 준비된 뒤 시작한다. 아래 기기 번호는 검증용 에뮬레이터의 번호로 맞춘다. Gradle의 전체 기기 검사는 종료 시 APK를 제거할 수 있어 별도 설치가 필요하다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
$env:ANDROID_SERIAL = 'emulator-5556'
.\gradlew.bat :app:connectedAndroidTest
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 reverse tcp:18083 tcp:18083
adb -s emulator-5556 reverse tcp:18084 tcp:18084
adb -s emulator-5556 shell am instrument -w -r -e rosterE2e true -e class com.nurseflow.app.feature.roster.PublishedRosterDeviceTest com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner
```

`am instrument`의 프로세스 종료 코드만으로 성공을 판정하지 않는다. `OK (1 test)` 및 성공 status와 서버 Gradle의 `BUILD SUCCESSFUL`을 모두 확인한다. 실패 후 재실행은 새로운 fixture 서버로 시작한다. 끝나면 추가한 reverse 포트를 제거한다.

## PR 및 최신 dev 통합 검증 (2026-11-09)

- [PR #34](https://github.com/gerrmang/NurseFlow/pull/34), 대상 `dev`, **Squash and merge**. PR 생성은 사용자 지시에 따라 수행했으며 병합하지 않았다.
- A2가 병합된 `dev` `fa47a5a`로 rebase해 공유 기록만 충돌 해결했다. 서버/Android 구현 충돌은 없었다. 신규 화면 파일의 EOF 공백을 정리하고 신규 파일을 포함한 diff 검사를 통과했다.
- 통합 후 백엔드 `test --no-daemon`: 109개 중 107개 통과·2개 opt-in 건너뜀·실패/오류 0.
- 통합 후 B9a 실서버 기기 검사를 재실행해 **`OK (1 test)`, 13.549초**, 서버 fixture **`BUILD SUCCESSFUL`** 확인.
- 구현 head `f4cd1ed64a19097973be1cbff0dcf7066391daad`에서 GitHub [Verify run 42613579824](https://github.com/gerrmang/NurseFlow/actions/runs/42613579824) 성공: Backend tests·Android debug build 모두 통과. Vercel 및 Preview Comments도 성공했다. 이 기록의 후속 문서 커밋 CI는 별도로 확인한다.

## API·데이터·설정 변경

- 운영 API·DB 스키마·환경변수 변경 없음. Android가 기존 공개 조회 계약을 소비한다.
- 테스트 전용 opt-in `NURSEFLOW_ROSTER_E2E`와 `rosterE2e` 인자를 추가했다.
- 병동 전체는 응답의 근무 시간대로 표시한다. 전체 응답에 코드가 없으므로 D/E/N을 추측하지 않는다.

## 배포 영향과 되돌리기

Android 앱 업데이트만 필요하다. 서버 배포·데이터 이전은 없다. 문제 발생 시 B9a 변경을 되돌린 앱을 배포한다. 테스트 fixture는 배포되지 않는다.

## 남은 위험과 후속 작업

외부 Kakao 인증 성공 및 운영 서버·물리 기기 검증은 이번 로컬 실서버 검사에 포함하지 않는다. GitHub CI 성공은 위 실행에서 확인했으며 브랜치 보호·독립 승인 여부는 확인하지 않았다. 전체 근무 코드 표시에는 후속 서버 계약이 필요하다.

## PR 리뷰 P3 수정 검증 (2026-11-10)

- `209de0d`의 COMMENT 리뷰에서 지적한 간호사 미연결 관리자 안내를 수정했다. `NURSE_IDENTITY_REQUIRED`를 일반 403과 구분하고 "본인 근무표에 연결된 간호사 정보가 없습니다."를 표시한다. 전체 조회 권한이 있는 경우에만 "병동 전체 보기" 버튼으로 이동한다. 서버 권한·API 계약은 변경하지 않는다.
- JVM 회귀 2개를 추가해 오류 구분·전체 전환과 전체 권한 없는 경우 API 미호출을 확인했다. 단위 테스트·앱/계측 APK 빌드·Lint 성공: 41개 통과·실패/오류/건너뜀 0, Lint 오류 0·기존 경고 33개.
- 일반 기기 검사 재실행 성공: XML 23개 중 18개 통과·5개 opt-in 건너뜀·실패/오류 0. B9a opt-in 실서버 검사도 `OK (1 test)`, 11.790초와 서버 `BUILD SUCCESSFUL` 확인. 기존 공개·재공개·월 이동·계정 격리 시나리오에 관리자 로그인 → 실제 `NURSE_IDENTITY_REQUIRED` → 올바른 안내 → 버튼으로 전체 공개본 조회 성공을 추가했다.
- 재실행 시 환경변수는 Gradle 테스트 캐시 입력이 아니므로, 위 재현 명령에 `--rerun`을 추가해 서버 테스트의 실제 실행을 보장한다.
- 백엔드 전체 검사는 서버 코드가 바뀌지 않아 생략했다. GitHub CI는 PR에 수정 커밋을 푸시한 뒤 확인한다. 변경된 앱 배포로 반영하며 서버 배포·DB 이전은 없다. 되돌리기는 이 P3 수정 커밋을 되돌린 앱 배포다.

## 최종 확인

- [x] 일반 작업의 PR 대상은 `dev`이며 현재 작업은 `feat/android-published-roster`에 있다.
- [x] 근무 필수 규칙과 공개·권한 판단은 서버에 유지했다.
- [x] 실제 개인정보·부재 사유·초안 데이터를 테스트에 사용하지 않았다.
- [x] 검증 결과·생략한 검사·재현 절차를 기록했다.
- [x] GitHub PR 생성 및 구현 head CI 성공 확인
- [ ] 최종 병합 검토 및 스쿼시 병합
