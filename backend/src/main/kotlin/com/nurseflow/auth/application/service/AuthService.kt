package com.nurseflow.auth.application.service

import com.nurseflow.auth.application.model.KakaoLoginCommand
import com.nurseflow.auth.application.model.SessionResult
import com.nurseflow.auth.application.model.SessionUser
import com.nurseflow.auth.application.model.VerifiedIdentity
import com.nurseflow.auth.config.AuthProperties
import com.nurseflow.auth.persistence.entity.DbUser
import com.nurseflow.auth.persistence.repository.AuthJdbcRepository
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * Login only proves who the user is. It never grants ward access: wards and roles come
 * exclusively from ward_memberships created by ward creation or invitation redemption.
 */
@Service
class AuthService(
    private val verifier: KakaoIdTokenVerifier,
    private val tokens: SessionTokenService,
    private val repo: AuthJdbcRepository,
    private val properties: AuthProperties,
    private val transactions: TransactionTemplate,
) {
    fun loginWithKakao(command: KakaoLoginCommand): SessionResult {
        // Verify outside the transaction: it may fetch Kakao's JWKS over the network.
        return loginWithVerifiedIdentity(verifier.verify(command.idToken, command.nonce))
    }

    /** Starts a session for an identity already proven by a verified Kakao ID token. */
    fun loginWithVerifiedIdentity(identity: VerifiedIdentity): SessionResult {
        return try {
            transactions.execute { startSession(identity) }!!
        } catch (_: DuplicateKeyException) {
            // A concurrent first login created the same Kakao user; the retry finds it.
            transactions.execute { startSession(identity) }!!
        }
    }

    private fun startSession(identity: VerifiedIdentity): SessionResult {
        val now = Instant.now()
        val (user, created) = findOrCreateUser(identity, now)
        return issue(SessionUser(user.id, user.displayName, created), UUID.randomUUID(), now)
    }

    /**
     * Rotates a refresh token. Presenting an already-rotated or revoked token is treated as
     * theft: the whole session family is revoked, which also logs out the legitimate holder.
     */
    @Transactional(noRollbackFor = [BusinessException::class])
    fun refresh(refreshToken: String): SessionResult {
        val now = Instant.now()
        val current = repo.lockRefreshTokenByHash(tokens.hash(refreshToken))
            ?: throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        if (current.revokedAt != null || current.replacedAt != null) {
            repo.revokeFamily(current.familyId, now)
            throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
        if (!now.isBefore(current.expiresAt)) {
            repo.revokeFamily(current.familyId, now)
            throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
        val user = repo.findUser(current.userId) ?: throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        repo.markReplaced(current.id, now)
        return issue(SessionUser(user.id, user.displayName, newUser = false), current.familyId, now)
    }

    /** Idempotent: unknown or already revoked tokens also succeed so logout never leaks token state. */
    @Transactional
    fun logout(refreshToken: String) {
        val current = repo.lockRefreshTokenByHash(tokens.hash(refreshToken)) ?: return
        repo.revokeFamily(current.familyId, Instant.now())
    }

    @Transactional(readOnly = true)
    fun requireUser(subject: String): DbUser {
        val id = runCatching { UUID.fromString(subject) }.getOrNull()
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        return repo.findUser(id) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
    }

    private fun findOrCreateUser(identity: VerifiedIdentity, now: Instant): Pair<DbUser, Boolean> {
        repo.findUserByProviderSubject(identity.provider, identity.subject)?.let { existing ->
            repo.touchUser(existing.id, identity.displayName, now)
            return existing.copy(displayName = identity.displayName ?: existing.displayName) to false
        }
        val user = DbUser(UUID.randomUUID(), identity.displayName ?: DEFAULT_DISPLAY_NAME)
        repo.insertUser(user.id, identity.provider, identity.subject, user.displayName, now)
        return user to true
    }

    private fun issue(user: SessionUser, familyId: UUID, now: Instant): SessionResult {
        val refresh = tokens.newOpaqueToken()
        val refreshExpiresAt = now.plus(properties.session.refreshTokenTtl)
        repo.insertRefreshToken(UUID.randomUUID(), familyId, user.userId, tokens.hash(refresh), now, refreshExpiresAt)
        val access = tokens.issueAccessToken(user.userId, familyId, now)
        return SessionResult(access.value, access.expiresAt, refresh, refreshExpiresAt, user)
    }

    companion object {
        const val DEFAULT_DISPLAY_NAME = "카카오 사용자"
    }
}
