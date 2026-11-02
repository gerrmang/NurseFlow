package com.nurseflow.app.feature.ward

import com.nurseflow.app.core.network.CreateWardBody
import com.nurseflow.app.core.network.CurrentUserDto
import com.nurseflow.app.core.network.MembershipDto
import com.nurseflow.app.core.network.NurseFlowApi
import com.nurseflow.app.core.network.apiCall
import java.time.YearMonth

class WardRepository(private val api: NurseFlowApi) {
    /** Signed-in user and the wards the server says they belong to. */
    suspend fun me(): CurrentUserDto = apiCall { api.me() }

    /** The caller becomes the ward's first administrator. */
    suspend fun createWard(name: String, careerStartedOn: YearMonth, rank: String): MembershipDto = apiCall {
        api.createWard(CreateWardBody(name = name.trim(), careerStartedOn = careerStartedOn.toString(), rank = rank))
    }

    companion object {
        /** A new ward starts with the server's default rank list (policy can be changed afterwards). */
        val DEFAULT_RANKS = listOf("팀장", "과장", "신규")
    }
}
