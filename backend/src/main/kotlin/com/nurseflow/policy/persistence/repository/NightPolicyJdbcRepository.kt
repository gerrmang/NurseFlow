package com.nurseflow.policy.persistence.repository

import com.nurseflow.policy.domain.NightPolicy
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class NightPolicyJdbcRepository(private val jdbc: JdbcTemplate) {
    fun policy(wardId: UUID): NightPolicy? = jdbc.query(
        "SELECT * FROM ward_night_policies WHERE ward_id=?",
        { rs, _ ->
            NightPolicy(
                rs.getInt("sof_night_threshold"), rs.getInt("sof_days"), rs.getInt("dedicated_headcount"),
                rs.getInt("dedicated_rotation_months"), rs.getInt("dedicated_min_nights"),
            )
        }, wardId,
    ).firstOrNull()

    /** Caller holds the ward lock. */
    fun save(wardId: UUID, policy: NightPolicy, actor: String, now: Instant) {
        jdbc.update("DELETE FROM ward_night_policies WHERE ward_id=?", wardId)
        jdbc.update(
            """INSERT INTO ward_night_policies(ward_id,sof_night_threshold,sof_days,dedicated_headcount,
                dedicated_rotation_months,dedicated_min_nights,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?)""",
            wardId, policy.sofNightThreshold, policy.sofDays, policy.dedicatedHeadcount,
            policy.dedicatedRotationMonths, policy.dedicatedMinNights, actor, now.toString(),
        )
    }
}
