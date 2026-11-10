package com.nurseflow.hospital.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.hospital.application.model.HospitalSearchResult
import com.nurseflow.hospital.config.HospitalProperties
import com.nurseflow.hospital.domain.HospitalSearchName
import com.nurseflow.hospital.persistence.repository.HospitalJdbcRepository
import org.springframework.stereotype.Service

/**
 * Searches the imported official hospital list. Until a list has been imported, falls back to the live
 * HIRA API when a key is configured (it often takes 10+ seconds per search). Results are never stored.
 */
@Service
class HospitalSearchService(
    private val properties: HospitalProperties,
    private val hospitals: HospitalJdbcRepository,
    private val hira: HiraHospitalClient,
) {
    fun search(query: String, page: Int, size: Int): HospitalSearchResult {
        // Same rule on both paths: at least two letters or digits once spaces and symbols are removed.
        val key = HospitalSearchName.of(query)
        if (key.length < 2) throw BusinessException(ErrorCode.INVALID_REQUEST)
        val dataset = hospitals.latestImport(HospitalDatasetImporter.SOURCE)
        if (dataset != null && hospitals.hasActive(HospitalDatasetImporter.SOURCE)) {
            return HospitalSearchResult(
                items = hospitals.search(key, (page - 1) * size, size),
                page = page,
                size = size,
                totalCount = hospitals.count(key),
                attribution = attribution(dataset.version),
            )
        }
        if (!properties.hira.configured) throw BusinessException(ErrorCode.HOSPITAL_SEARCH_NOT_CONFIGURED)
        return hira.search(query.trim(), page, size)
    }

    companion object {
        /** 공공누리 제1유형: the source must be shown with the data. */
        internal fun attribution(version: String): String {
            val period = Regex("(\\d{4})-(\\d{2})").matchEntire(version)
                ?.let { " (${it.groupValues[1]}.${it.groupValues[2].toInt()}. 기준)" }.orEmpty()
            return "건강보험심사평가원 전국 병의원 및 약국 현황$period"
        }
    }
}
