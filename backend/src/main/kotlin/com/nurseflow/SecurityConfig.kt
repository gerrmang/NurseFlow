package com.nurseflow

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
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
        response.writer.write(
            "{\"type\":\"about:blank\",\"title\":\"$title\",\"status\":$status," +
                "\"code\":\"$code\",\"detail\":\"$detail\"}",
        )
    }
}
