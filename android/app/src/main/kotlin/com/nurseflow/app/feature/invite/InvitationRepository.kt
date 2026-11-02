package com.nurseflow.app.feature.invite

import com.nurseflow.app.core.network.CreateInvitationBody
import com.nurseflow.app.core.network.CreatedInvitationDto
import com.nurseflow.app.core.network.InvitationDto
import com.nurseflow.app.core.network.InvitationPreviewDto
import com.nurseflow.app.core.network.InvitationTokenBody
import com.nurseflow.app.core.network.MembershipDto
import com.nurseflow.app.core.network.NurseFlowApi
import com.nurseflow.app.core.network.RedeemInvitationBody
import com.nurseflow.app.core.network.apiCall
import java.time.YearMonth

class InvitationRepository(private val api: NurseFlowApi) {
    suspend fun preview(token: String): InvitationPreviewDto = apiCall { api.previewInvitation(InvitationTokenBody(token)) }

    /** Profile is optional; when given both fields are sent and the admin still has to confirm them. */
    suspend fun redeem(token: String, careerStartedOn: YearMonth?, rank: String?): MembershipDto = apiCall {
        api.redeemInvitation(RedeemInvitationBody(token, careerStartedOn?.toString(), rank))
    }

    suspend fun create(wardId: String, role: String, expiresInHours: Int): CreatedInvitationDto =
        apiCall { api.createInvitation(wardId, CreateInvitationBody(role = role, expiresInHours = expiresInHours)) }

    suspend fun list(wardId: String): List<InvitationDto> = apiCall { api.invitations(wardId).items }

    suspend fun revoke(wardId: String, invitationId: String) = apiCall { api.revokeInvitation(wardId, invitationId) }
}
