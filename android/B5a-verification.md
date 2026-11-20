# B5a Android 팀·간호사·구성원 관리 검증

2026-11-18, Codex. 공유 worktree `C:\Users\jjang\IdeaProjects\NurseFlow`, `feat/android-staff-team`, 기반 `origin/dev` `4ecedd1a2aa5a39a9cf93a22ee1381f4a83da52c`.

## 구현 범위

- 관리자 병동 카드·병동 생성 후 설정 안내→관리 화면.
- 팀 추가·이름·표시 순서·삭제, 간호사 기록 등록·이름/자격/활성·팀 배치.
- 활성 팀원 전체의 드래그·위/아래 정렬과 명시적 저장. 팀 안 위쪽이 상대 선임이다.
- 병원 입사 연월 조회와 실제 경력/직급 입력·확인 분리, 선임 자격·단계 조정과 변경 이력.
- 구성원 관리자 지정/해제·접근 비활성/재활성. 확인 대화상자와 서버 오류별 안내.
- 세션·병동별 ViewModel, 진입/재조회/쓰기 전 소속 확인, 쓰기 직렬화, 서버 재조회 실패 중 편집 차단, 유실 결과 확인 후 재개. 권한 상실 시 관리 데이터 폐기.
- 관리 API는 생성 당시 계정·세션 세대에 묶고, 계정 전환 이후 대기 요청과 401 갱신 재전송을 차단한다. 동일 계정의 일반 토큰 갱신은 기존 동작을 유지한다.

서버·OpenAPI·DB·환경변수·의존성 변경 없음. A5·A6 계약을 소비한다. `stageOverride` 초기화와 팀 해제는 명시적 null을 보내고, 구성원 PATCH는 변경할 속성만 보낸다.

## 검사와 재현

JDK 21, `android/`에서 기본 검사:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon
```

- API 검사: 명시적 null·false·PATCH 생략 속성·비활성 목록·전체 순서 배열·유실 응답 재전송 금지·오류 코드·계정 변경 차단.
- ViewModel 검사: 중복 쓰기, 쓰기 전 소속 재확인, 최신 목록 실패, 응답 유실 후 확인, 권한 상실, 마지막 관리자·기록 충돌, 정렬 구성 변경, 오래된 이력 응답 폐기, 화면 이탈 중 쓰기 결과 불확실성.
- Compose: 버튼·길게 누른 드래그 정렬과 저장, 변경 확인과 LAST_WARD_ADMIN 안내.
- Activity: 실제 MainActivity의 병동 카드→관리 진입 및 재생성 뒤 미저장 팀 입력·정렬 보존.

최종 실행 결과는 이 문서 끝의 실행 결과 절을 따른다. 이전 중간 실패를 최종 통과로 대체하지 않고 원인과 수정 범위를 구분한다.

## 실제 backend 검사

기존 backend를 포트 18091, 별도 `jdbc:h2:mem:b5a_fictional_e2e;DB_CLOSE_DELAY=-1`로 실행한다. 사용자 파일 DB는 사용하지 않는다. 인증·API·DB는 실제 구현이며 OIDC 공급자와 계정만 가상이다.

1. 저장소 루트에서 `node android/scripts/b5a-oidc-fixture.mjs` 실행(루프백 18090).
2. 별도 PowerShell에서 다음 설정으로 backend 실행한다. 임시 서명 비밀값은 실행 중에만 생성하며 출력하지 않는다.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot'
$env:SPRING_DATASOURCE_URL='jdbc:h2:mem:b5a_fictional_e2e;DB_CLOSE_DELAY=-1'
$env:SERVER_PORT='18091'
$env:NURSEFLOW_SESSION_SECRET=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
$env:NURSEFLOW_AUTH_KAKAO_ISSUER='http://127.0.0.1:18090'
$env:NURSEFLOW_AUTH_KAKAO_JWK_SET_URI='http://127.0.0.1:18090/jwks'
$env:NURSEFLOW_KAKAO_AUDIENCES='b5a-fixture'
Set-Location backend
.\gradlew.bat bootRun --offline --no-daemon
```

3. `android/`의 별도 PowerShell에서 opt-in 검사:

```powershell
$env:NURSEFLOW_B5A_E2E='1'
.\gradlew.bat :app:testDebugUnitTest --tests '*StaffAdminRealServerTest' --rerun-tasks --offline --no-daemon
Remove-Item Env:NURSEFLOW_B5A_E2E
```

고정 포트는 기존 서비스가 없을 때만 사용한다. 가상 공급자를 운영 설정으로 사용하지 않는다. 테스트가 끝나면 이번 공급자·backend 프로세스를 종료해 메모리 DB와 토큰을 폐기한다.

검증 내용: 공식 목록 병원으로 가상 병동 생성, 팀·간호사 등록/배치, 순서 저장/재조회, 실제 경력·확인·선임·단계와 null 초기화·이력, 팀 이동/해제, 비어 있지 않은 팀 삭제·오래된 정렬 거절, 가입 연월, 마지막 관리자 보호, 관리자/활성 기록 충돌, 비활성 구성원 기록 보호, 접근만 재활성화, 본인 권한 상실 시 ViewModel 데이터 폐기.

## 제한·배포·되돌리기

- 일반 편집은 서버 revision 충돌 검사가 없어 마지막 저장이 반영될 수 있다. TEAM_ORDER_STALE은 팀원 집합 변경을 감지한다.
- 순서는 저장 버튼을 누르기 전에는 서버에 반영하지 않는다. 새로고침·다른 화면 이동 시 미저장 값은 초기화될 수 있다. 긴 목록 드래그의 가장자리 자동 스크롤은 제공하지 않으며 위·아래 버튼을 함께 제공한다.
- 실제 Kakao·물리 기기·운영 서버·모든 화면 크기·실제 다중 관리자 동시 편집은 미검증이다. 실제 서버 API 검사와 기기의 가상 API 화면 검사는 구분한다.
- backend·웹 전체 회귀는 변경 없는 Android 기능 단위라 재실행하지 않았다. GitHub CI·독립 재리뷰는 별도 확인 대상이다.
- Android 빌드 영향. 서버 배포·데이터 이전 없음. 되돌리기는 B5a 변경 revert와 이전 Android 빌드 복원.

## 실행 결과

- 최종 앱 코드의 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon`, `NURSEFLOW_B5A_E2E=1`: **BUILD SUCCESSFUL**. 단위 XML **171개, 실패·오류·건너뜀 0**. 가상 OIDC+실제 backend 검사 1개를 포함한다. 기본 실행에서는 이 실서버 검사만 opt-in으로 건너뛴다.
- B5a 추가 단위/API/실서버 검사 27개(18+8+1), 기존 144개를 함께 실행했다. 기본 전체 검사에서 실서버 opt-in을 항상 켜도록 CI를 변경하지 않았다.
- Lint XML: 오류 0, 경고 28, Information 4. 경고를 이번 작업에서 모두 해결했다고 주장하지 않는다.
- 기존 Pixel_10, Android 17에서 직접 instrumentation 실행 **OK (4 tests), 8.032초**: 드래그/버튼 정렬 저장, 마지막 관리자 확인/거절, MainActivity 진입·재생성 뒤 팀 입력/미저장 정렬 보존. 이는 실제 Kakao·실서버 기기 E2E가 아닌 가상 API 기기 검사다.
- 중간 실행: 테스트 대역이 checked 예외를 Proxy에서 직접 던져 결과 불확실성으로 처리된 1건은 Continuation 실패로 전달하도록 고쳤다. 가상 HTTP 기기 검사는 기존 프록시 `10.0.2.2:18080` 연결 실패가 있어 테스트 프로세스의 루프백만 직접 연결하고 종료 시 원래 ProxySelector를 복원한다. 기기 전체 프록시는 변경하지 않았다. 시스템 응답 정지로 이번 에뮬레이터만 데이터 초기화 없이 콜드 부팅했다.
- 합동 기기 실행에서 Activity 진입 대기 1건이 시간 초과되어, 테스트에서 세션 복원 완료 후 로그인하고 기존 task를 재사용하지 않도록 준비 순서를 명시했다. 수정 후 합동 검사 결과는 공유 현황에도 별도로 기록한다.
- 최종 `:app:connectedDebugAndroidTest`에서 B5a 두 테스트 클래스를 함께 실행: **BUILD SUCCESSFUL, 4/4 통과·실패/건너뜀 0**, 45초. 이 결과는 위 준비 순서 보완 후 최종 제품 코드/테스트 APK 기준이다. 과거 Gradle 실패 보고서와 직접 instrumentation 성공만으로 대신하지 않았다. `git diff --check` 통과.
- 오래 켜 둔 메모리 서버의 시계 변경 경고 이후 실서버 검사 1건이 HTTP 500으로 실패했다. 서버를 새 메모리 DB로 재시작한 뒤 동일 제품 코드로 재실행해 통과했다. 500의 내부 원인을 제품 결함으로 단정하지 않는다. 이후 최종 실행에서도 통과했다.
- 자체 diff 검토로 대기 요청/401의 계정 전환 차단, Activity 재생성 보존, 명시적 null, 최신 목록 실패 시 이력 로딩 종료, 서버 기본 경력순 보존을 확인했다. 독립 리뷰·GitHub CI는 미실행이다.
