package com.nurseflow.hospital.application

import com.nurseflow.hospital.application.service.HospitalDatasetStartupImport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * A server started without a dataset path has the official list from the copy shipped in the server, and a
 * restart does not import it again. Uses its own database because other tests clear the hospital tables.
 */
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:hospital_bundled_startup;DB_CLOSE_DELAY=-1",
    "nurseflow.hospital.dataset.path=",
    "nurseflow.hospital.dataset.bundled=true",
])
class HospitalBundledStartupTest @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val startup: HospitalDatasetStartupImport,
) {
    @Test
    fun `startup without a dataset path imports the bundled list once`() {
        val imports = jdbc.queryForList("SELECT dataset_version FROM hospital_dataset_imports", String::class.java)
        assertEquals(1, imports.size, "imported at startup")
        assertTrue(Regex("""\d{4}-\d{2}""").matches(imports[0].orEmpty()), imports[0])
        val active = jdbc.queryForObject("SELECT COUNT(*) FROM hospitals WHERE active=TRUE", Int::class.java)!!
        assertTrue(active >= 10_000, "$active hospitals")

        startup.run(DefaultApplicationArguments())
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM hospital_dataset_imports", Int::class.java))
    }
}
