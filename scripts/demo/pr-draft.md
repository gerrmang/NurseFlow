## 변경 목적과 내용

한 명령으로 가상 병동과 편성 기초 데이터를 공개 API로 준비한다. dev `00f086c`(A9·A10 포함)의 실제 backend에 별도 메모리 H2·가상 OIDC/병원 목록을 연결하며 B6에서 서버 코드를 변경하지 않는다. Android·웹 관리자 팀/구성원 화면에도 서버 시연 모드·가상 데이터 포함 경고를 표시한다.

**B6 인수 기준:** 새 H2 DB에서 공개 API로 데이터를 생성하고 조회해 검증한다. 가상 관리자·간호사 2계정의 운용·초대 가입 검증과 Android·웹 관리 화면의 시연 모드 표시를 포함한다. 실제 Kakao 웹 로그인은 **1계정으로 별도 검증 완료**했으며 가상 2계정 결과와 구분한다. 계정 수·관리·폐기 절차는 `scripts/demo/accounts.md`, 실제 결과는 `scripts/demo/real-kakao-verification.md` 참조.

- `scripts/demo/run.ps1`: 빌드→전용 서버 시작→시드→재조회 검증, r 재실행·q 종료
- A/B팀, 팀별 1~5단계 간호사 10명, 경력·선임·팀 내 순서, D/E/N 시간·휴게·인정·환산
- 시연 모드 명시 활성화·서버 편성 대상 검증, 생성 응답 유실 복구·ETag·멱등 키
- 가상 데이터만 사용, 토큰 비출력, 기존 포트·파일 DB 보존
- Android·웹 관리 화면: 서버 모드 표시·최신 목록 갱신, 조회 실패·오래된 상태를 일반 모드로 추정하지 않음
- PR 리뷰 P3 반영: 모드 조회만 실패하면 ‘시연 모드 확인 불가’를 표시하고 최신 팀·구성원 목록과 편집을 유지한다. 인증·권한 오류와 기존 목록 조회 실패의 차단은 유지한다. P5 실행 안내도 현재 관리자 화면 지원과 가상 계정의 실제 로그인 불가 범위를 구분한다.
- 2026-11-23 추가 P3 반영: Android 모드 응답의 JSON 디코딩 실패도 미확인 상태로 처리한다. 실제 Retrofit 변환기로 필수 필드 누락·깨진 JSON·잘못된 타입을 검사하며 첫 진입·새로고침·저장 후 재조회·정상 응답 복구를 검증했다. 취소·인증·권한 오류 처리는 유지한다.
- 가상 관리자·간호사 2계정: 초대 전 404→가입·본인 프로필→관리자 API 403, 기본 모드 편성·관리자 기록 제외
- CI Demo seed checks 추가: 기존 서버·Android·웹 검사와 별도로 시드/CLI 11개 및 새 H2 통합 검사

## 관련 이슈

없음 — breakdown B6, 선행 A5·A7·A8 dev 병합 완료.

## 검증

- 2026-11-23 최신 dev 통합: A9/A10 `00f086c` 병합, 작업 현황 문서 충돌에서 양쪽 기록 보존. backend `test bootJar --offline --no-daemon` 성공, 267개 통과·opt-in 5개 건너뜀·실패/오류 0. web api:check·typecheck 성공. 새 backend JAR의 새 H2 시드·가상 2계정·복구 통합 검증 exit 0, Node API/runtime 7개 통과. 서버·OpenAPI·생성 타입은 dev와 동일하다.
- 2026-11-23 추가 리뷰 수정: Android ViewModel 30개 통과. 새 JSON 회귀 2개는 처리 코드를 제거한 변이에서 모두 실패했으며 수정 코드를 복원했다. 최종 JDK 21 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon` BUILD SUCCESSFUL(1분 19초), 단위 194개 중 193개 통과·실서버 opt-in 1개 건너뜀·실패/오류 0, Lint 오류 0·경고 27. 이번 변경은 Android 모드 응답 처리뿐이므로 웹·backend·시드 실서버 통합·기기·실제 Kakao 검사는 재실행하지 않았다.
- 2026-11-22 리뷰 수정: Android 단위 190개 통과·실서버 opt-in 1개 건너뜀·실패/오류 0, 앱/테스트 APK·Lint 성공(오류 0·경고 27). 웹 api:check·lint·typecheck·전체 test·build 성공, 394개 통과·실서버 opt-in 3개 건너뜀. 시드 API/런타임 7개·local.mjs 구문·git diff 공백 검사 통과. 첫 진입·모드 재조회 실패·저장 후 재조회·복구와 인증/권한 차단 회귀를 포함한다. 기기·실서버·실제 Kakao·backend 전체 검사는 클라이언트 장애 처리와 안내 수정 범위라 이번에 재실행하지 않았다. 수정 diff 독립 재리뷰와 최종 head CI는 별도 확인한다.
- JDK 21·Node v24.15.0, `scripts/demo/run.ps1 -Verify -Offline`: bootJar BUILD SUCCESSFUL, 기본 통합 검증 성공.
- 최종 `node scripts/demo/local.mjs --jar backend/build/libs/nurseflow-backend-0.1.0-SNAPSHOT.jar --verify`: exit 0. 생성 응답 유실 4종 복구·재실행 쓰기 0건·전체 데이터 조회·시연 모드 제외/복구·412·비소속 404·기존 근무 설정 보존 확인.
- `node --test scripts/demo/api.test.mjs scripts/demo/runtime.test.mjs`: 7개 통과·실패/건너뜀 0. 전체 mjs 구문·텍스트 공백 및 git diff 공백 검사 성공.
- 실제 프로세스에서 일반 진입점 r 동일 병동 재조회·q 종료·포트 해제, 시작 중 취소·포트 해제 확인.
- 일반 실행의 마지막 시드 검증 결과를 종료 코드에 반영하도록 P3를 수정했다. 실패→EOF/q는 1, r 복구 성공은 0, 최초 성공 뒤 재실패는 1이다. `NURSEFLOW_B6_TEST_JAR` opt-in으로 기존 7개+실제 CLI 회귀 4개 **총 11개 통과·실패/건너뜀 0**. 별도 메모리 서버와 검색 503 주입으로 검증했다.
- 추가 검증: Android 단위 XML 189개 중 **188개 통과·실서버 opt-in 1개 건너뜀**, 실패/오류 0. 앱·테스트 APK·Lint BUILD SUCCESSFUL.
- Pixel_10 Android 17, 두 StaffAdmin 클래스를 지정한 최종 `connectedDebugAndroidTest` **5/5 통과**, 실패/건너뜀 0, BUILD SUCCESSFUL. 시연 모드 경고·정렬·관리자 보호·MainActivity 진입/재생성 확인. 앞선 초기 프로세스 종료(0개 시작)는 같은 코드의 직접 계측 4개와 최종 Gradle 5개로 재확인했다.
- 웹 api:check·lint·typecheck·전체 test·build 성공. **387개 통과·실서버 opt-in 3개 건너뜀**, 실패 0. 켜짐/꺼짐 갱신·조회 실패·비정상 응답 회귀 포함.
- 새 2계정 초대/가입/권한 분리 실제 서버 검증 exit 0. 구현/게시 기록 head `f2b4c16`의 Verify 44738992638에서 Demo seed checks·Backend tests·Android debug build·Web checks 모두 성공을 확인했다. 이 문서 마무리 커밋의 CI는 별도로 확인하며 자체 diff 검토와 독립 승인은 구분한다.
- 실제 Kakao 웹 1계정: 실제 HTTPS IdP·새 메모리 H2·현재 코드에서 로그인→새로고침 복원→로그아웃→실제 Kakao 재로그인 성공. 인증된 병원 검색·가상 병동 생성·관리자 팀/구성원 API 및 일반 모드 표시 확인 후 로그아웃·검증 서버 종료. 비밀번호/토큰을 자동 입력·출력하지 않았고 가상 OIDC를 사용하지 않았다.
- 미실행: 두 실제 Kakao 계정 간 전환/초대 가입·이번 Android SDK 실로그인·운영·물리 기기·전체 화면 크기·macOS/Linux·독립 리뷰. backend 전체 회귀는 서버 코드/계약 미변경이라 로컬에서 생략했고 PR의 Backend tests로 별도 확인한다.
- 중간 실패·수정 내역: work-status B6 절 참조. 단위 검사와 가상 공급자+실제 서버 통합 검증을 구분한다.

## API·데이터·설정 변경

- [ ] API 계약 변경
- [ ] DB 스키마 또는 마이그레이션 변경
- [ ] 환경변수 또는 설정 계약 변경
- [x] 해당 없음 — 기존 계약 사용, 시연 자식 프로세스에만 기존 설정 적용

## 배포 영향

Android·웹 관리 화면의 표시와 CI 작업이 바뀐다. 서버 배포·사용자 DB 이전 없음. Node 추가 패키지 없음. 수동 배포·자동 병합 없음.

## 되돌리기

시연 종료 후 B6 커밋을 revert하고 이전 Android·웹 빌드를 사용한다. 메모리 DB는 종료 시 폐기되며 사용자 DB 롤백 없음.

## 남은 위험과 후속 작업

- 실제 Kakao 웹 1계정은 검증했으며 두 실제 계정 전환/초대 가입·이번 Android SDK 실로그인·운영 환경은 미검증이다. B6 자동화의 가상 2계정 결과와 실제 결과를 구분해 기록한다. 사용자 요청으로 같은 공유 브랜치에 시연 모드 표시 보완을 함께 포함했다.
- 모드 표시는 기존 API 권한에 맞춰 관리자 팀/구성원 화면에 제공한다. 간호사의 관리자 API 권한을 넓히지 않는다. 가상 계정을 앱/웹의 실제 Kakao 로그인에 사용할 수는 없으며 API 검증과 클라이언트 표시 검증은 구분한다.
- 고정 시연값을 채우는 전용 런타임이며 사용자 수정 일부 필드는 r 재실행 시 재적용된다. 같은 병동의 동시 시드는 지원하지 않는다.
- 초안·필요 인원·자동배치·확정·공개·B12 E2E는 범위 밖이다.
- 메모리 DB는 종료 시 없어지므로 실행 중 터미널을 유지한다.

## 최종 확인

- [x] 대상 브랜치 dev, B6 작업 브랜치에서 개발
- [x] 근무 필수 규칙·권한·편성 대상은 서버에서 판정
- [x] 비공개 부재·건강정보·실데이터·비밀값 비노출
- [x] 사용법·작업 현황 갱신, 기존 API 계약 소비
- [x] 검증 결과와 생략 검사 기록
