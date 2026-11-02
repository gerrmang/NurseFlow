# 근무표 DTO 경계 정리 계획

1. 현재 요청·응답 JSON 필드와 멱등성 재생 데이터 구조를 기준으로 고정한다. 기존 통합 테스트를 보존한다.
2. `roster.application.model`에 HTTP와 무관한 Command/Result 모델을 정의한다. 서비스와 생성 조정기는 이 모델만 사용한다.
3. `roster.api.controller`에서 Request→Command, Result→Response 변환을 맡는다. API 패키지의 공개 DTO와 경로는 유지한다.
4. 백엔드 `./gradlew check`와 diff 검사를 실행하고, 기존 응답·멱등성 계약의 위험을 검토한다.

범위 밖: DTO 필드 자체 변경, 새 의존성, DB 스키마 변경, 추가 근무 정책 구현.
