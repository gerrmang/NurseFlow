package com.nurseflow.common.security

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Resolves the verified JWT subject at the HTTP boundary. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentSubject

@Configuration
class CurrentSubjectConfiguration : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers += object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter): Boolean =
                parameter.hasParameterAnnotation(CurrentSubject::class.java) && parameter.parameterType == String::class.java

            override fun resolveArgument(
                parameter: MethodParameter,
                mavContainer: ModelAndViewContainer?,
                webRequest: NativeWebRequest,
                binderFactory: WebDataBinderFactory?,
            ): Any {
                val jwt = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
                return jwt?.token?.subject?.takeIf { it.isNotBlank() }
                    ?: throw BusinessException(ErrorCode.SUBJECT_REQUIRED)
            }
        }
    }
}
