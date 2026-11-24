package com.nurseflow.hospital.api.controller

import com.nurseflow.hospital.api.dto.response.HospitalCandidateResponse
import com.nurseflow.hospital.api.dto.response.HospitalSearchPageResponse
import com.nurseflow.hospital.application.model.HospitalSearchResult
import com.nurseflow.hospital.application.service.HospitalSearchService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/hospitals")
class HospitalSearchController(private val hospitals: HospitalSearchService) {
    @GetMapping("/search")
    fun search(
        @RequestParam @Pattern(regexp = QUERY_PATTERN) query: String,
        @RequestParam(defaultValue = "1") @Min(1) @Max(100) page: Int,
        @RequestParam(defaultValue = "10") @Min(1) @Max(20) size: Int,
    ): ResponseEntity<HospitalSearchPageResponse> =
        ResponseEntity.ok().header("Cache-Control", "private, no-store")
            .body(hospitals.search(query, page, size).toResponse())

    private fun HospitalSearchResult.toResponse() = HospitalSearchPageResponse(
        items = items.map { HospitalCandidateResponse(it.source, it.externalId, it.name, it.address, it.category) },
        page = page,
        size = size,
        totalCount = totalCount,
        attribution = attribution,
    )

    companion object {
        /** 2–50 characters after trimming surrounding whitespace, no control characters. */
        private const val QUERY_PATTERN = "^\\s*[^\\s\\p{Cntrl}][^\\p{Cntrl}]{0,48}[^\\s\\p{Cntrl}]\\s*$"
    }
}
