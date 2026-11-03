package com.nurseflow.invitation.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * `linkBaseUrl` is the web/app-link page that opens an invitation (for example
 * `https://nurseflow.example/invite`). The token is appended as a URL fragment so it
 * is not sent to web servers or written to access logs. Empty: only the raw token is returned.
 */
@ConfigurationProperties("nurseflow.invitation")
data class InvitationProperties(val linkBaseUrl: String = "")

@Configuration
@EnableConfigurationProperties(InvitationProperties::class)
class InvitationConfiguration
