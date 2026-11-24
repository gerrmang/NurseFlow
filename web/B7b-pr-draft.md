제목: `feat(web): 관리자가 근무 유형과 팀 수요·승인 부재·근무 형태를 설정`

## 변경 목적과 내용

- 실서버 연동(최신 `dev` `db5e412` 통합 기준). 웹 관리자에게 B7b 근무 설정 네 메뉴를 제공한다. 내 병동·팀 관리에서 진입한다. A11·B6·A12·A14를 통합했고, 서버·OpenAPI·생성 타입은 `dev`와 동일하다. 팀 간 지원 허용 관리 화면은 이번 B7b에 추가하지 않는다.
- D/E/N 시간·휴게·인정·환산 설정, 변경 영향 미리보기·비활성화·재활성화, 월별 초안 생성·팀 수요 전체 교체, 승인 VY·반차 등록·수정·삭제, 간호사 근무 형태 조회·저장을 연결한다.
- ETag 충돌·응답 유실·권한 변경을 처리하고 저장 뒤 서버 값을 다시 읽는다. 필요 자격은 조회 API에 없어 기존 수요 자동 복원을 하지 않으며 전체 교체를 명시적으로 확인한다. 부재 사유는 수집하지 않는다.

## 관련 이슈

- 없음 (breakdown B7b)

## 검증

- 최신 dev 통합 후 `web/`: `npm run api:check`·`npm run typecheck`·`npm run lint`·`npm run build` exit 0. `npm test -- --run`: 447개 통과·opt-in 4개 건너뜀·실패 0. JS gzip 114.84 kB, `git diff --cached --check` 통과.
- 독립 리뷰 P3(기존 승인 부재의 시간 미변경 저장에서 정밀도 손실/DST 거절)를 수정했다. 수정 전 새 회귀 6건 실패 확인, 수정 후 초/소수초·오프셋·DST 양 회차·나노초 구간·한쪽 끝만 수정하는 DOM/API 회귀 통과. 별도 에이전트 수정 재리뷰에서 원 P3 해결·새 P1~P5 발견 없음, 수정 회귀 독립 실행 10개 통과(필터 15개 선택 제외). GitHub 승인 리뷰와 구분한다.
- A14 포함 최신 dev JAR로 `NURSEFLOW_B7B_E2E=1`의 실제 서버 인수 검사 1개 통과·실패/건너뜀 0. JDK 21 backend `test bootJar --offline --no-daemon` BUILD SUCCESSFUL(1분 54초), XML 총 294개 중 289개 통과·opt-in 5개 건너뜀·실패/오류 0. 상세는 `web/B7b-verification.md` 참조.
- 이전 dev 통합 최종 head `9e72855`의 [Verify 45406922044](https://github.com/gerrmang/NurseFlow/actions/runs/45406922044)에서 Backend tests·Android debug build·Web checks·Demo seed checks 모두 success. 이번 A14 통합 head의 CI는 푸시 후 별도로 확인한다.
- 가상 OIDC + 실제 Spring + 별도 메모리 H2 인수 검사와 브라우저 저장·재조회·reload 확인. 실제 Kakao 인증과 구분한다.
- 미실행: 실제 Kakao·실제 두 관리자 브라우저 경합·전체 화면 크기·물리 기기·운영. Android 자체 추가 변경이 없어 로컬 전체 검사는 생략하며 PR CI로 확인한다. 최초 독립 리뷰와 P3 수정 부분 재리뷰 완료. 브라우저 검사는 최초 구현 시점이며 최신 dev 통합 후 실제 서버·DOM/API 회귀를 다시 확인했다. 최종 원격 CI는 PR 게시 후 확인한다.

## API·데이터·설정 변경

- [ ] API 계약 변경
- [ ] DB 스키마 또는 마이그레이션 변경
- [ ] 환경변수 또는 설정 변경
- [x] 해당 없음

## 배포 영향

- 웹 정적 빌드에 영향. 데이터 이전·서버 배포 없음. 수동 배포하지 않았다.

## 되돌리기

- B7b 웹 변경 revert 및 이전 웹 빌드 복원. 서버·DB 롤백 없음.

## 남은 위험과 후속 작업

- 근무 형태는 마지막 저장 우선이며 수요는 전체 교체다. 기존 배치가 있는 초안은 B10b 배치 편집에서 먼저 해제해야 한다.
- 이전 CI는 기존 Android `modeHttpErrorsRetainAccessChecksWithResponseWrapper` assertion 1건이 실패했으나 동일 Android 소스의 최신 실행은 통과했다. 간헐적 실패 관찰로 남기며 원인 미확정·이 PR에서 Android 추가 변경 없음.
- 희망 오프·휴일/목표시간 정책·Night 순환·자동배치/확정 화면은 후속이다. 실서버 검사와 브라우저 확인의 상세 범위는 검증 기록 참조.
- 문서 충돌은 B7b와 dev의 기록을 모두 보존해 해결했다. 공유 브랜치에 커밋·푸시하고 [PR #59](https://github.com/gerrmang/NurseFlow/pull/59)를 dev 대상 일반 PR로 게시했다. 최종 원격 CI와 게시 결과는 공유 현황에 기록한다. 병합·자동 병합·수동 배포는 하지 않았다.

## 최종 확인

- [x] 대상 브랜치가 맞음: 일반 작업은 `dev`, 릴리스·긴급 수정은 `main` (게시 시 대상 `dev`)
- [x] 근무 필수 규칙은 서버에서 검증함
- [x] 비공개 부재 정보·건강정보가 노출되지 않음 (가상 일정만 검증 자료 사용)
- [x] 필요한 문서와 API 계약을 갱신함 (계약 변경 없음)
- [x] 검증 결과와 생략한 검사를 적음
