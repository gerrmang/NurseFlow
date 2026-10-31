# 백엔드 예외·인증 사용자·DTO 규칙

상태: 2026-10-31 개발 규칙. NurseFlow 백엔드는 **Kotlin + Spring Boot**다. Java `ErrorCode.java`와 `record` 예시는 각각 Kotlin `ErrorCode.kt`와 불변 `data class`로 적용한다. 기존 [OpenAPI 오류 계약](openapi.yaml)의 `application/problem+json`, `code`, `currentRevision`, `ruleViolations`를 유지하면서 단계별 PR로 옮긴다. 이 문서는 현재 구현 완료를 뜻하지 않는다.

## 결정

1. **비즈니스 오류:** `ErrorCode` enum을 단일 출처로 두고 안정적인 코드, HTTP 상태, 외부에 보여도 안전한 기본 메시지를 정의한다. 서비스는 문자열 코드나 `HttpStatus`를 직접 만들지 않고 `throw BusinessException(ErrorCode.X)`를 사용한다. 기존 공개 오류 코드를 바꾸지 않는다.
2. **입력 오류:** JSON 구문, 누락 필드, Bean Validation 실패는 `GlobalExceptionHandler`에서 `INVALID_REQUEST`와 필요한 필드별 오류로 변환한다. 입력 형식 오류를 서비스의 비즈니스 예외로 포장하지 않는다.
3. **인증·인가 오류:** `@RestControllerAdvice`는 MVC에 도달한 예외를 처리한다. JWT 검증·접근 거부처럼 **보안 필터에서 발생한 401/403**은 `AuthenticationEntryPoint`와 `AccessDeniedHandler`가 처리하고 같은 오류 응답 직렬화기를 사용한다. 병동 소속·역할 판정은 서버 서비스에서 계속 확인한다.
4. **예상 못 한 오류:** 프로그래밍 오류, DB 장애, 외부 서비스 실패를 `BusinessException`으로 감싸 정상적인 업무 오류처럼 보이게 하지 않는다. 클라이언트에는 일반적인 500과 추적 ID만 보내고, 서버에는 비밀키·토큰·휴가 사유·건강정보·요청 본문을 기록하지 않는다. 비동기 생성 작업은 HTTP advice 대신 작업의 `FAILED` 상태와 안전한 진단으로 종료한다.
5. **현재 사용자:** Controller/API 경계에서 검증된 JWT `sub`를 `Actor(subject)` 같은 값으로 바꿔 서비스에 **명시적으로 전달**한다. 서비스에서 정적 `SecurityUtil.getCurrentMemberId()`를 호출하지 않는다. 초대 전 로그인 사용자는 병동 `memberId`가 없을 수 있고, 비동기 생성 작업에는 요청 스레드의 SecurityContext가 자동으로 전달되지 않는다. 각 서비스는 전달된 subject와 대상 ward의 membership을 DB에서 다시 확인한다.
6. **DTO:** 기능별 `api/dto/request`와 `api/dto/response`를 분리하고 Kotlin `data class`의 `val`로 작성한다. Controller는 DTO와 애플리케이션 명령/결과를 변환한다. 도메인 객체·DB 행을 응답으로 직접 노출하지 않는다. 서비스가 API DTO를 import하지 않도록 패키지 구조 정리 후 점진적으로 옮긴다.

## 권장 코드 형태

아래는 형태를 설명하는 예시다. 기능별 실제 코드와 오류 메시지는 현재 API 계약에 맞춘다.

```kotlin
enum class ErrorCode(val status: HttpStatus, val safeMessage: String) {
    ROSTER_NOT_FOUND(HttpStatus.NOT_FOUND, "근무표를 찾을 수 없습니다."),
    STALE_ROSTER_REVISION(HttpStatus.PRECONDITION_FAILED, "근무표가 변경되었습니다."),
    RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, "근무 규칙을 확인해 주세요."),
}

class BusinessException(
    val errorCode: ErrorCode,
    val currentRevision: Int? = null,
    val ruleViolations: List<RuleViolation> = emptyList(),
) : RuntimeException(errorCode.safeMessage)

data class CreateRosterRequest(
    @field:Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])")
    val month: String,
)

data class RosterResponse(val rosterId: UUID, val revision: Int)
data class Actor(val subject: String)
```

- 위 `@field:Pattern` 같은 Bean Validation 어노테이션은 검증 제공자가 있을 때만 동작한다. 현재 `backend/build.gradle.kts`에는 Validation starter가 없다. 검증 기능 PR에서 필요한 의존성과 `@Valid` 적용을 명시하고, 누락·빈값·형식 오류 테스트로 실제 동작을 확인한다. Kotlin의 `data class`를 Java `record`와 함께 중복 생성하지 않는다.
- `ErrorCode`의 `HttpStatus`는 단일 Spring Boot 모듈의 단순한 선택이다. 서비스는 이 타입을 직접 사용하지 않는다. 나중에 API/Application을 별도 Gradle 모듈로 분리할 때 HTTP 매핑을 API 계층으로 옮길 수 있다.
- 동적 메시지에 사용자 입력, 간호사 이름, 휴가 사유를 삽입하지 않는다. 프런트엔드는 메시지 문자열 대신 **안정적인 `code`**로 상태를 구분한다. `traceId`와 `instance`는 민감한 식별자를 포함하지 않도록 제한한다.

## 예외 처리 범위

| 발생 위치 | 처리 | 응답/상태 |
| --- | --- | --- |
| Service의 예상 가능한 업무 규칙 위반 | `BusinessException(ErrorCode.X)` → `GlobalExceptionHandler` | 기존 `ApiProblem` 형식과 정확한 4xx |
| Request JSON/필드 검증 실패 | MVC 예외 → `GlobalExceptionHandler` | 400 `INVALID_REQUEST`, 안전한 필드 오류 |
| 인증되지 않았거나 JWT가 유효하지 않음 | `AuthenticationEntryPoint` | 401 `UNAUTHORIZED` |
| 인증됐지만 권한이 없음 | `AccessDeniedHandler` 또는 서비스의 병동 권한 검사 | 403, 또는 타 병동 존재 여부를 숨길 때 기존 404 정책 |
| 비동기 자동배치 작업 실패 | 작업 실행기에서 상태 저장·안전한 진단 | 작업 조회의 `FAILED`; 요청 스레드 예외 처리기에 의존하지 않음 |
| 예기치 않은 서버 오류 | 중앙 500 처리 및 서버 추적 ID | 500 `INTERNAL_ERROR`, 내부 원인 비공개 |

`try-catch`는 외부 API·DB·비동기 작업 실패를 변환하거나 복구할 때 사용한다. 모든 표준 예외를 무조건 `BusinessException`으로 바꾸지 않는다. 개발 결함이 4xx로 숨겨지면 장애를 발견하기 어렵다.

## 현재 코드에서 옮길 범위

- 현재 `RosterApiException`/구조 정리 PR의 `ApiException`은 HTTP 상태·문자열 코드·메시지를 매번 받는다. 기존 오류 코드와 상태를 표로 고정한 뒤 `ErrorCode` + `BusinessException`으로 치환한다.
- 현재 `GlobalExceptionHandler`는 업무 오류와 일부 형식 오류를 처리한다. `MethodArgumentNotValidException`·메서드 검증·예상 못 한 오류의 안전한 응답, 민감정보 없는 필드 메시지, 기존 412/422 부가 필드 보존을 단계별 테스트로 보완한다.
- 현재 `SecurityConfig`의 401/403 응답은 JSON 문자열을 직접 조립한다. advice와 같은 안전한 JSON 직렬화기를 공유해 응답 형태를 맞춘다. 보안 필터 예외를 advice만으로 처리하려고 하지 않는다.
- 현재 Controller는 각 메서드에서 `@AuthenticationPrincipal Jwt`를 받아 `subject`를 전달한다. 공통 API 경계 resolver 또는 인증된 actor 인자를 사용해 반복을 줄이고, 서비스의 명시적 actor 전달과 병동 DB 권한 검사는 유지한다.
- 구조 정리 PR이 병합되기 전에는 그 worktree의 같은 파일을 동시에 수정하지 않는다. 먼저 해당 PR을 검증·병합한 뒤 새 `dev` 브랜치에서 이 규칙을 작은 PR로 구현한다.

## 구현 PR과 검증 순서

1. **오류 계약 PR:** 기존 모든 오류 코드·HTTP 상태·응답 필드를 고정하는 테스트를 만든다. `ErrorCode`/`BusinessException`/`GlobalExceptionHandler`를 도입하되 외부 응답 형식은 유지한다.
2. **보안 응답·actor PR:** 401/403 필터 응답 직렬화기를 통일하고 Controller의 subject 추출을 API 경계에 모은다. 서비스와 비동기 작업에는 actor를 명시적으로 전달한다. 초대 전 사용자·타 병동 접근을 재검증한다.
3. **입력 DTO 검증 PR:** 기능별 Request/Response 파일과 필요한 검증 어노테이션을 정리하고 `@Valid` 및 검증 제공자 적용을 테스트한다. 구조 정리에서 이미 분리된 DTO는 중복 이동하지 않는다.

각 PR은 `dev`에서 분기하고 백엔드 테스트·CI, 변경된 OpenAPI, API/DB 영향, 미검증 사항과 롤백을 기록한다. Android·웹은 동일한 오류 `code`와 HTTP 상태를 사용하고, 안전한 사용자 안내를 보여준다.

## 공식 문서 근거

- [Spring MVC 오류 응답과 Problem Details](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [Spring Security 필터의 인증·접근 거부 처리](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [SecurityContext의 스레드 경계](https://docs.spring.io/spring-security/reference/features/integrations/concurrency.html)
- [Kotlin 어노테이션 use-site target](https://docs.spring.io/spring-framework/reference/languages/kotlin/annotations.html)
