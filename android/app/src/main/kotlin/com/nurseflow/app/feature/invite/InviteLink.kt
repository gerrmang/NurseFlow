package com.nurseflow.app.feature.invite

/**
 * An invitation token received only through a link fragment. It is sent to the server in a
 * request body and never placed in a path, query, log or saved state; [toString] is masked.
 */
class InviteToken private constructor(val value: String) {
    override fun equals(other: Any?) = other is InviteToken && other.value == value
    override fun hashCode() = value.hashCode()
    override fun toString() = "InviteToken(***)"

    companion object {
        private val pattern = Regex("^[A-Za-z0-9_-]{43}$")
        fun of(value: String): InviteToken? = if (pattern.matches(value)) InviteToken(value) else null
    }
}

/**
 * Development link format (decision §7 #6, 2026-11-10): `nurseflow://invite#token=<43 chars>`.
 * This custom scheme is for development verification only; HTTPS App Links for production are
 * a follow-up once a domain and assetlinks exist. Anything else is rejected, including https
 * links, queries, paths, extra fragment parameters and malformed tokens.
 */
const val INVITE_LINK_PREFIX = "nurseflow://invite#token="

fun parseInviteLink(raw: String): InviteToken? {
    val link = raw.trim()
    if (!link.startsWith(INVITE_LINK_PREFIX)) return null
    return InviteToken.of(link.removePrefix(INVITE_LINK_PREFIX))
}
