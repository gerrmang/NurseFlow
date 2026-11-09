package com.nurseflow.app.test

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.nurseflow.app.AppContainer
import com.nurseflow.app.NurseFlowApplication
import com.nurseflow.app.core.session.TokenStore

/** All instrumentation starts without reading or rotating a developer's saved login. */
class IsolatedSessionTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, IsolatedSessionTestApplication::class.java.name, context)
}

class IsolatedSessionTestApplication : NurseFlowApplication() {
    override fun createContainer() = AppContainer(this, tokenStore = TestTokenStore())
    fun useContainer(container: AppContainer) { this.container = container }
}

class TestTokenStore : TokenStore {
    @Volatile var token: String? = null
        private set
    override suspend fun read() = token
    override suspend fun write(refreshToken: String) { token = refreshToken }
    override suspend fun clear() { token = null }
}
