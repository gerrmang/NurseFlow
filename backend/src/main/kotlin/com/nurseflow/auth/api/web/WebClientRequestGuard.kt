package com.nurseflow.auth.api.web

import com.nurseflow.auth.config.AuthProperties
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * CSRF defence for the cookie-based web auth endpoints, applied before body parsing:
 * 1. `X-NurseFlow-Client: web` is required. Cross-site forms cannot set custom headers, and a
 *    cross-origin fetch with it needs a CORS preflight, which this server does not grant.
 * 2. When the browser sends `Origin`, it must be an allowed web origin.
 * 3. `Sec-Fetch-Site: cross-site` is rejected outright.
 * The cookies are also HttpOnly + SameSite=Strict and scoped to [AuthProperties.Web.COOKIE_PATH].
 */
class WebClientRequestGuard(private val properties: AuthProperties) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method == "OPTIONS") return true
        val webClient = request.getHeader(CLIENT_HEADER)?.trim().equals(CLIENT_WEB, ignoreCase = true)
        val origin = request.getHeader("Origin")?.trim()?.trimEnd('/')?.lowercase()
        val originAllowed = origin == null || origin in properties.webAllowedOrigins.map { it.lowercase() }
        val crossSite = request.getHeader("Sec-Fetch-Site").equals("cross-site", ignoreCase = true)
        if (!webClient || !originAllowed || crossSite) throw BusinessException(ErrorCode.WEB_CLIENT_REQUIRED)
        return true
    }

    companion object {
        const val CLIENT_HEADER = "X-NurseFlow-Client"
        const val CLIENT_WEB = "web"
    }
}

@Configuration
class WebAuthMvcConfiguration(private val properties: AuthProperties) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(WebClientRequestGuard(properties)).addPathPatterns("/api/v1/auth/web/**")
    }
}
