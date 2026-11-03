package com.nurseflow.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Demo-only first ward/admin creation. A blank code disables the bootstrap endpoint. */
@ConfigurationProperties("nurseflow.bootstrap")
data class BootstrapProperties(val code: String = "")
