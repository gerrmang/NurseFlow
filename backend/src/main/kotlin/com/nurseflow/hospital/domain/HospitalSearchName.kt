package com.nurseflow.hospital.domain

/** Matching key for hospital names: lower case, letters and digits only, so "(VOM) 봄 안과" matches "봄안과". */
object HospitalSearchName {
    fun of(name: String): String = buildString {
        name.lowercase().forEach { if (it.isLetterOrDigit()) append(it) }
    }

    /**
     * 0 for hospital-level institutions where ward rosters are typical, 1 for clinics and public health posts.
     * HIRA 종별코드: 01 상급종합, 11 종합병원, 21 병원, 28 요양병원, 29 정신병원, 41 치과병원, 92 한방병원.
     */
    fun rank(categoryCode: String): Int = if (categoryCode.trim() in HOSPITAL_LEVEL) 0 else 1

    private val HOSPITAL_LEVEL = setOf("01", "11", "21", "28", "29", "41", "92")
}
