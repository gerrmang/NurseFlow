package com.nurseflow.app.feature.profile

import com.nurseflow.app.core.network.CareerPolicyDto
import com.nurseflow.app.core.network.CareerProfileBody
import com.nurseflow.app.core.network.NurseFlowApi
import com.nurseflow.app.core.network.StaffMemberDto
import com.nurseflow.app.core.network.apiCall
import java.time.YearMonth

class ProfileRepository(private val api: NurseFlowApi) {
    suspend fun myProfile(wardId: String): StaffMemberDto = apiCall { api.myProfile(wardId) }

    suspend fun policy(wardId: String): CareerPolicyDto = apiCall { api.careerPolicy(wardId) }

    suspend fun submit(wardId: String, careerStartedOn: YearMonth, rank: String): StaffMemberDto =
        apiCall { api.submitMyProfile(wardId, CareerProfileBody(careerStartedOn.toString(), rank)) }
}
