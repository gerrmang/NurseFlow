package com.nurseflow.app.feature.invite

/**
 * Pulls the 43-character invitation token out of whatever the user pasted: the raw code,
 * an invite link with `#token=…` or `?token=…`, or a share message containing the link.
 */
object InviteCode {
    private val token = Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{43}(?![A-Za-z0-9_-])")
    private val tagged = Regex("token=([A-Za-z0-9_-]{43})(?![A-Za-z0-9_-])")

    fun extract(input: String): String? {
        val text = input.trim()
        tagged.find(text)?.let { return it.groupValues[1] }
        if (token.matches(text)) return text
        return token.findAll(text).singleOrNull()?.value
    }
}
