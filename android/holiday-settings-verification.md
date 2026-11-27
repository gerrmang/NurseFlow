# Android 휴일·목표시간 설정 검증

기준: 최신 dev `d6f69dec`, 작업 브랜치 `codex/holiday-target-settings` (2026-11-27).

## 범위

A13 관리자 휴일 달력·목표시간 API/DTO, 세션 소유 ViewModel, Compose 화면, 병동 목록·루트 진입을 구현했다. 서버·OpenAPI·DB·배포 환경·의존성 변경 없음. 초안 편집·개인별 시간 진단은 B10 후속이다.

## 검증

- JDK 21, 기존 Android SDK와 Gradle 캐시를 사용했다. 초기 샌드박스 실행은 사용자 Gradle 캐시 잠금 접근 거부로 시작하지 못해 권한 확장 후 실행했다.
- `NURSEFLOW_HOLIDAY_ANDROID_E2E=1`, `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`: 단위 XML 293개 중 290개 통과·기존 실서버 opt-in 3개 건너뜀·실패/오류 0. 새 API 3개·ViewModel 13개·실서버 1개 포함. Lint 오류 0·기존 경고 27.
- 실제 Spring/H2 인수 1개: 기존 B5a 가상 OIDC 신원으로 실제 SessionManager·Retrofit·ViewModel을 연결했다. 새 메모리 DB `holiday_android_e2e`, 서버 18091·공급자 18090, 병원 검색·병동 생성, 휴무일 저장 후 20일/160시간, 기준 420분 저장 후 140시간, 전담 목표 0→빈 값(null), 정책 ETag 증가·412, 키 미설정 503, 재진입, 초대 가입 일반 구성원의 달력·정책 조회 403을 확인했다. 실제 Kakao 인증이 아니다.
- ViewModel 검사는 400/412 입력 보존, 공휴일 가져오기 실패 시 편집 유지, 권한 상실 데이터 폐기, 불확실 저장 재조회·확인 전 재전송 차단, 진행 중 중복 요청·재활성화, 늦은 응답 무시, 저장 직후 다른 revision에 미저장 입력을 합치지 않는 경합 경로를 포함한다.
- 읽기 전용 Pixel_10(Android 17) 최종 기기 검사 **4/4 통과·실패/오류/건너뜀 0, 22.233초**. 날짜 편집→저장 확인과 출처 구분, 전담 목표 0분, 키 미설정 갱신 차단·월 이동 취소, 전용 debug Activity의 실제 재생성 후 420분 입력·대상 월 유지와 추가 조회 없음·이탈 취소를 확인했다. 가상 API 화면 검사이며 기기 실서버·실제 Kakao 인수와 구분한다.
- 최종 debug 테스트 호스트 추가 후 `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:connectedDebugAndroidTest`(위 두 기기 클래스 지정) **BUILD SUCCESSFUL(1분 31초)**. Lint 오류 0·기존 경고 27. 이 마지막 실행에는 단위 검사 재실행이 없으며, 위의 실제 서버 포함 단위 290개 통과 결과와 구분한다. 검증용 프로세스와 읽기 전용 AVD는 종료했고 원본 AVD·프록시 설정은 바꾸지 않았다.
- 초기 테스트 프록시가 suspend 예외를 래핑해 상태 검사 2개가 실패했으며 Continuation으로 예외를 전달하도록 수정한 뒤 통과했다. 새 실서버 검사 문법 오류와 검증 서버 audience 설정 누락도 수정 후 실제 인수를 통과했다. 제품의 인증 설정은 변경하지 않았다.
- MainActivity+가상 HTTP의 초기 기기 회전 검사는 테스트 준비 중 메인 스레드 네트워크 오류, 이어서 기존 AVD의 전역 프록시 `10.0.2.2:18080`에 의한 세션 복원 연결 실패로 완료하지 못했다. 자동 승인 검토가 에뮬레이터 프록시 예외 변경을 거부해 실행하지 않았다. 대체 검사는 네트워크를 쓰지 않는 전용 debug Activity와 실제 화면·ViewModel을 사용한다. 호스트는 `exported=false`이며 release 소스에는 포함되지 않는다. MainActivity의 실제 HTTP 진입·기기 실서버 인수와 구분한다.

## 재현

`B5a-verification.md`의 가상 OIDC·서버 실행 절차를 따른다. `NURSEFLOW_KAKAO_AUDIENCES=b5a-fixture`, issuer `http://127.0.0.1:18090`, JWKS `/jwks`, 서버 18091과 새 메모리 H2를 사용한다. 실제 공휴일 키는 설정하지 않는다.

```powershell
$env:NURSEFLOW_HOLIDAY_ANDROID_E2E='1'
.\gradlew.bat :app:testDebugUnitTest --tests '*HolidaySettingsRealServerTest' --offline --no-daemon
```

기기 검사는 읽기 전용 `Pixel_10` AVD와 `IsolatedSessionTestRunner`로 새 두 클래스만 지정한다.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.nurseflow.app.feature.holiday.HolidaySettingsScreenUiTest,com.nurseflow.app.feature.holiday.HolidaySettingsRotationTest' --offline --no-daemon
```

## 제한·배포·되돌리기

- 실제 공휴일 성공 갱신·실제 Kakao·물리 기기·운영·전체 기기 회귀·독립 리뷰·원격 CI는 미검증. 공휴일 성공 응답과 실패는 가상 API 및 실제 키 미설정 경로를 구분한다.
- API에 병동 시간대가 없어 갱신 시각을 UTC로 명시한다. ViewModel은 Activity 재생성을 견디지만 프로세스 종료 뒤 입력 복원은 제공하지 않는다.
- Android 빌드에 영향. 수동 배포·PR 게시·병합 없음. 되돌리기는 이 기능을 되돌리고 이전 Android 빌드를 복원한다. 서버/DB 롤백은 없다.

## 최신 dev 통합 후 PR 검증 (2026-11-27)

- 브랜치는 `feat/holiday-target-settings`, 통합 기준은 최신 dev `4a4cd3d6`. Android 구현·검사는 통합 전 `ff58691b`와 동일함을 git diff로 확인했다.
- JDK 21 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon` BUILD SUCCESSFUL(37초). XML 293개 중 289개 통과·실서버 opt-in 4개 건너뜀·실패/오류 0. Lint 오류 0·기존 경고 27. 단위 검사는 실제 실행됐고 APK/Lint는 UP-TO-DATE 결과를 포함한다.
- 이번에는 기기/회전·Android 실서버·실제 Kakao·물리 기기를 재실행하지 않았다. 이전의 Android 실서버 1개·가상 API 기기 4개 통과와 혼합하지 않는다. 최신 dev 서버의 웹 holiday 실연동 1개는 재실행 통과했고 서버 계약과 Android DTO도 재대조했다.
- 재검토에서 조치 가능한 확정 결함 없음. 서버·API·DB·운영 환경·의존성 변경 없음. debug 테스트 호스트는 release 소스에 포함되지 않는다.
