package com.nurseflow.common.security

import jakarta.servlet.http.HttpServletResponse
import com.nurseflow.common.error.ApiProblem
import tools.jackson.databind.json.JsonMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(private val mapper: JsonMapper) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/kakao/session", "/api/v1/auth/session/refresh",
                    "/api/v1/auth/logout").permitAll()
                .anyRequest().authenticated()
        }
        .exceptionHandling {
            it.authenticationEntryPoint { _, response, _ -> problem(response, 401, "UNAUTHORIZED", "인증이 필요합니다.") }
                .accessDeniedHandler { _, response, _ -> problem(response, 403, "FORBIDDEN", "접근 권한이 없습니다.") }
        }
        .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
        .build()

    private fun problem(response: HttpServletResponse, status: Int, code: String, detail: String) {
        response.status = status
        response.contentType = "application/problem+json"
        response.characterEncoding = "UTF-8"
        response.setHeader("Cache-Control", "private, no-store")
        val title = if (status == 401) "Unauthorized" else "Forbidden"
        response.writer.write(mapper.writeValueAsString(ApiProblem(
            title = title,
            status = status,
            code = code,
            detail = detail,
        )))
    }
}
