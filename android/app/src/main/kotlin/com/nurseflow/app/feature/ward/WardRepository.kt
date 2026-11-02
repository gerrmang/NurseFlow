package com.nurseflow.app.feature.ward

import com.nurseflow.app.core.network.CreateWardBody
import com.nurseflow.app.core.network.CurrentUserDto
import com.nurseflow.app.core.network.MembershipDto
import com.nurseflow.app.core.network.NurseFlowApi
import com.nurseflow.app.core.network.apiCall

class WardRepository(private val api: NurseFlowApi) {
    /** Signed-in user and the wards the server says they belong to. */
    suspend fun me(): CurrentUserDto = apiCall { api.me() }

    /** The caller becomes the ward's first administrator. */
    suspend fun createWard(name: String, creationCode: String): MembershipDto =
        apiCall { api.createWard(CreateWardBody(name = name.trim(), bootstrapCode = creationCode.trim())) }
}
