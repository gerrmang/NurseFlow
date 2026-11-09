package com.nurseflow.hospital.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.hospital.application.model.HospitalSearchResult
import com.nurseflow.hospital.config.HospitalProperties
import org.springframework.stereotype.Service

/** Search only: results are returned to the caller and never stored. Any signed-in user may search. */
@Service
class HospitalSearchService(private val properties: HospitalProperties, private val hira: HiraHospitalClient) {
    fun search(query: String, page: Int, size: Int): HospitalSearchResult {
        if (!properties.hira.configured) throw BusinessException(ErrorCode.HOSPITAL_SEARCH_NOT_CONFIGURED)
        return hira.search(query.trim(), page, size)
    }
}
