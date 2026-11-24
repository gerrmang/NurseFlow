package com.nurseflow.ward.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** `maxCreatedPerUser` caps how many wards one account may create, so a signed-in script cannot mass-create them. */
@ConfigurationProperties("nurseflow.ward")
data class WardProperties(val maxCreatedPerUser: Int = 5)

@Configuration
@EnableConfigurationProperties(WardProperties::class)
class WardConfiguration
