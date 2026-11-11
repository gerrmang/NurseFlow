package com.nurseflow.app.core.network

import org.junit.Assert.*
import org.junit.Test

class MembershipDtoTest {
    @Test fun keepsHospitalNamesForSameNamedWardsAndAcceptsLegacyNullOrMissingNames() {
        val wards = ApiJson.json.decodeFromString<Items<MembershipDto>>(
            """{"items":[
              {"wardId":"a","wardName":"가상 병동","hospitalName":"가상 병원 가","roles":["WARD_MEMBER"],"canViewWardCalendar":true},
              {"wardId":"b","wardName":"가상 병동","hospitalName":"가상 병원 나","roles":["WARD_ADMIN"],"canViewWardCalendar":true},
              {"wardId":"c","wardName":"기존 병동","hospitalName":null,"roles":["WARD_MEMBER"],"canViewWardCalendar":true},
              {"wardId":"d","wardName":"이전 병동","roles":["WARD_MEMBER"],"canViewWardCalendar":true}
            ]}""",
        ).items
        assertEquals(listOf("가상 병원 가", "가상 병원 나", null, null), wards.map { it.hospitalName })
        assertEquals(wards[0].wardName, wards[1].wardName)
        assertFalse(wards[0].isAdmin)
        assertTrue(wards[1].isAdmin)
    }
}
