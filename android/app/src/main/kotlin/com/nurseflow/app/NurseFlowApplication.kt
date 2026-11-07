package com.nurseflow.app

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class NurseFlowApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
        container = AppContainer(this)
    }
}
