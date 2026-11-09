package com.nurseflow.app.core.session
interface TokenStore {
    suspend fun read(): String?
    suspend fun write(refreshToken: String)
    suspend fun clear()
}
