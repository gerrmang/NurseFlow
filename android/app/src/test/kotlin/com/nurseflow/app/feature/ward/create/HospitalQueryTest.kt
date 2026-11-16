package com.nurseflow.app.feature.ward.create

import org.junit.Assert.*
import org.junit.Test

class HospitalQueryTest {
    @Test fun acceptsTwoToFiftyCharactersAfterTrim() {
        assertNull(validateHospitalQuery("  가상  "))
        assertNull(validateHospitalQuery("가나"))
        assertNull(validateHospitalQuery("가".repeat(50)))
        assertNull(validateHospitalQuery(" " + "가".repeat(50) + " ")) // trimmed first
    }

    @Test fun rejectsTooShortAndTooLong() {
        assertNotNull(validateHospitalQuery(""))
        assertNotNull(validateHospitalQuery("가"))
        assertNotNull(validateHospitalQuery("  가  "))
        assertNotNull(validateHospitalQuery("가".repeat(51)))
    }

    @Test fun rejectsControlCharacters() {
        assertNotNull(validateHospitalQuery("가상\n병원"))
        assertNotNull(validateHospitalQuery("가상\u0000병원"))
        assertNotNull(validateHospitalQuery("가상\t병원"))
    }

    @Test fun needsTwoLettersOrDigitsBesideSymbols() {
        assertNotNull(validateHospitalQuery("(가)"))
        assertNotNull(validateHospitalQuery("--"))
        assertNotNull(validateHospitalQuery("가 ()"))
        assertNull(validateHospitalQuery("(가나)"))
        assertNull(validateHospitalQuery("가 나"))
        assertNull(validateHospitalQuery("A1"))
    }
}
