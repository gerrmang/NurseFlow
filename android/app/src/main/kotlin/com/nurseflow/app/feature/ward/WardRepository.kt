package com.nurseflow.app.feature.ward

import com.nurseflow.app.core.network.CurrentUserDto
import com.nurseflow.app.core.network.NurseFlowApi
import com.nurseflow.app.core.network.apiCall

class WardRepository(private val api: NurseFlowApi) {
    /** Signed-in user and the wards the server says they belong to. */
    suspend fun me(): CurrentUserDto = apiCall { api.me() }
}
