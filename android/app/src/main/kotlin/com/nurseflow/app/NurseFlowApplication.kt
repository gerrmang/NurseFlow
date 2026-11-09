package com.nurseflow.app

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

open class NurseFlowApplication : Application() {
    lateinit var container: AppContainer
        protected set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
        container = createContainer()
    }

    protected open fun createContainer(): AppContainer = AppContainer(this)
}
