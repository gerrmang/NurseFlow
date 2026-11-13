package com.nurseflow.common.idempotency

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.UUID

/**
 * Stored responses for `Idempotency-Key` mutations. A key is global: it replays only for the same
 * operation and request hash and is a conflict otherwise. Shared by every feature that takes the header.
 */
@Repository
class MutationReplayRepository(private val jdbc: JdbcTemplate, private val mapper: JsonMapper) {
    fun <T> replay(key: UUID, operation: String, requestHash: String, type: Class<T>): ReplayLookup<T> {
        val saved = jdbc.query(
            "SELECT operation,request_hash,response_json FROM mutation_replays WHERE idempotency_key=?",
            { rs, _ -> Triple(rs.getString("operation"), rs.getString("request_hash"), rs.getString("response_json")) },
            key,
        ).firstOrNull() ?: return ReplayLookup.Missing
        if (saved.first != operation || saved.second != requestHash) {
            return ReplayLookup.Conflict
        }
        return ReplayLookup.Found(mapper.readValue(saved.third, type))
    }

    /**
     * Must run in the mutation's transaction; a concurrent duplicate fails on the primary key with
     * [IdempotencyKeyRaceException]. Mutations that lock their scope re-read the replay after the lock, so a
     * duplicate reaching here used the key for another scope or request (another ward or roster): the service
     * answers it with IDEMPOTENCY_KEY_REUSED unless it recovers a same-request replay itself.
     */
    fun recordReplay(key: UUID, operation: String, requestHash: String, response: Any) {
        try {
            jdbc.update(
                "INSERT INTO mutation_replays(idempotency_key,operation,request_hash,response_json) VALUES(?,?,?,?)",
                key, operation, requestHash, mapper.writeValueAsString(response),
            )
        } catch (ex: DuplicateKeyException) {
            throw IdempotencyKeyRaceException(ex)
        }
    }

    /** SHA-256 of the JSON form of [payload]. */
    fun requestHash(payload: Any): String = MessageDigest.getInstance("SHA-256")
        .digest(mapper.writeValueAsBytes(payload))
        .joinToString("") { "%02x".format(it) }
}

/** Another transaction recorded the same key first. Still a [DuplicateKeyException] for callers that recover. */
class IdempotencyKeyRaceException(cause: DuplicateKeyException) :
    DuplicateKeyException("idempotency key recorded concurrently", cause)
