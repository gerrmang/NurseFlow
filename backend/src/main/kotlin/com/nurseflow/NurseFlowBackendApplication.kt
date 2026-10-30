package com.nurseflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NurseFlowBackendApplication

fun main(args: Array<String>) {
    runApplication<NurseFlowBackendApplication>(*args)
}
