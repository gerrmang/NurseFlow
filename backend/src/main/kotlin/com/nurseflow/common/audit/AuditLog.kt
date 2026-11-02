package com.nurseflow.common.audit

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Records who changed ward access. Callers pass identifiers only: never tokens,
 * display names, leave reasons or health information.
 */
@Component
class AuditLog(private val jdbc: JdbcTemplate) {
    fun record(wardId: UUID?, actor: String, action: String, targetId: String? = null) {
        jdbc.update(
            "INSERT INTO audit_events(event_id,ward_id,actor,action,target_id,created_at) VALUES(?,?,?,?,?,?)",
            UUID.randomUUID(), wardId, actor, action, targetId, Instant.now().toString(),
        )
    }
}
