package com.nurseflow.roster.application

import com.nurseflow.roster.api.GenerationJob
import com.nurseflow.roster.api.StartGenerationRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskRejectedException
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import java.util.UUID

@Configuration
class GenerationExecutorConfig {
    @Bean
    fun rosterGenerationExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 1
        maxPoolSize = 1
        queueCapacity = 16
        setThreadNamePrefix("roster-generation-")
        setWaitForTasksToCompleteOnShutdown(true)
        initialize()
    }
}

@Service
class GenerationCoordinator(
    private val service: RosterService,
    private val rosterGenerationExecutor: ThreadPoolTaskExecutor,
) {
    fun start(
        rosterId: UUID,
        ifMatch: String,
        idempotencyKey: UUID,
        request: StartGenerationRequest,
        subject: String,
    ): GenerationJob {
        val job = service.startGeneration(rosterId, ifMatch, idempotencyKey, request, subject)
        if (job.status == "QUEUED") {
            try {
                rosterGenerationExecutor.execute {
                    try {
                        if (service.markGenerationRunning(job.jobId)) service.processGeneration(job.jobId)
                    } catch (_: Exception) {
                        service.failGeneration(job.jobId)
                    }
                }
            } catch (_: TaskRejectedException) {
                service.failGeneration(job.jobId)
            }
        }
        return service.getGenerationJob(job.jobId, subject)
    }
}
