package com.nurseflow.hospital.api

import com.nurseflow.hospital.application.service.HospitalDatasetException
import com.nurseflow.hospital.application.service.HospitalDatasetImporter
import com.nurseflow.hospital.config.HospitalProperties
import com.nurseflow.hospital.persistence.repository.HospitalJdbcRepository
import com.nurseflow.support.ApiTestBase
import com.nurseflow.support.FakeHiraServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Search over an imported copy of the official list. All institutions here are fictional. */
class HospitalDatasetSearchHttpTest : ApiTestBase() {
    @Autowired lateinit var importer: HospitalDatasetImporter
    @Autowired lateinit var hospitalRows: HospitalJdbcRepository
    @Autowired lateinit var transactions: TransactionTemplate
    @TempDir lateinit var dir: Path

    private val header = "암호화요양기호,요양기관명,종별코드,종별코드명,시도코드,시도코드명,주소,전화번호"

    private fun row(id: String, name: String, code: String, category: String, address: String) =
        "$id,$name,$code,$category,110000,서울,\"$address\",02-000-0000"

    private val september = listOf(
        row("FAKE-001", "(의)가상의료재단 가상병원", "21", "병원", "서울특별시 가상구 가상로 1, 2층 (가상동)"),
        row("FAKE-002", "가상병원", "11", "종합병원", "부산광역시 가상구 바다로 2"),
        row("FAKE-003", "가상병원의원", "31", "의원", "대구광역시 가상구 3"),
        row("FAKE-004", "새가상병원", "01", "상급종합", "광주광역시 가상구 4"),
        row("FAKE-005", "다른의원", "31", "의원", "대전광역시 가상구 5"),
        row("", "식별자없는가상병원", "21", "병원", "어딘가"),
    )

    /** Same shape as data.go.kr: a CP949 zip with the hospital CSV next to a pharmacy CSV. */
    private fun zip(name: String, rows: List<String>): Path = dir.resolve(name).also { path ->
        ZipOutputStream(Files.newOutputStream(path)).use { out ->
            out.putNextEntry(ZipEntry("1.병원정보서비스(2026.9.).csv"))
            out.write((listOf(header) + rows).joinToString("\r\n", postfix = "\r\n").toByteArray(CP949))
            out.closeEntry()
            out.putNextEntry(ZipEntry("2.약국정보서비스(2026.9.).csv"))
            out.write("$header\r\n${row("FAKE-PH1", "가상병원약국", "81", "약국", "어딘가")}\r\n".toByteArray(CP949))
            out.closeEntry()
        }
    }

    private fun csv(name: String, rows: List<String>): Path = dir.resolve(name).also {
        Files.write(it, (listOf(header) + rows).joinToString("\n").toByteArray(CP949))
    }

    private fun search(query: String, extra: String = "") =
        http.get("/api/v1/hospitals/search?query=" + URLEncoder.encode(query, Charsets.UTF_8) + extra, userToken().second)

    private fun names(query: String, extra: String = ""): List<String> =
        search(query, extra).json!!.get("items").toList().map { item -> item.get("name").asString() }

    @Test
    fun `zip import keeps hospitals only and search uses the local list`() {
        val result = importer.importIfChanged(zip("data.zip", september))

        assertTrue(result.imported)
        assertEquals("2026-09", result.version)
        assertEquals(5, result.importedCount)
        assertEquals(1, result.skippedCount)
        val before = FakeHiraServer.lastQuery.get()
        val response = search("가상병원")
        assertEquals(200, response.status, response.body)
        assertEquals(before, FakeHiraServer.lastQuery.get(), "local search must not call the live API")
        val body = response.json!!
        assertEquals(4, body.get("totalCount").asInt())
        assertEquals("건강보험심사평가원 전국 병의원 및 약국 현황 (2026.9. 기준)", body.get("attribution").asString())
        val first = body.get("items")[0]
        assertEquals("HIRA", first.get("source").asString())
        assertEquals("FAKE-002", first.get("externalId").asString())
        assertEquals("부산광역시 가상구 바다로 2", first.get("address").asString())
        assertEquals("종합병원", first.get("category").asString())
        assertFalse(names("가상병원").any { name -> name.contains("약국") })
    }

    @Test
    fun `exact match, then hospitals before clinics, then prefix, ignoring spaces and punctuation`() {
        importer.importIfChanged(zip("data.zip", september))

        // 정확 일치 → 병원급(종별 21·01) → 의원급. 같은 급에서는 앞부분 일치 → 포함 → 이름순.
        assertEquals(listOf("가상병원", "(의)가상의료재단 가상병원", "새가상병원", "가상병원의원"), names("가상병원"))
        assertEquals(listOf("(의)가상의료재단 가상병원"), names(" 가상 의료재단 "))
        assertEquals(0, search("없는이름").json!!.get("items").size())
    }

    @Test
    fun `pagination returns the requested slice with the full count`() {
        importer.importIfChanged(zip("data.zip", september))

        val page2 = search("가상병원", "&page=2&size=2").json!!
        assertEquals(4, page2.get("totalCount").asInt())
        assertEquals(listOf("새가상병원", "가상병원의원"), page2.get("items").toList().map { it.get("name").asString() })
    }

    @Test
    fun `the same file is not imported twice and a newer file deactivates closed institutions`() {
        val first = zip("data.zip", september)
        importer.importIfChanged(first)
        assertFalse(importer.importIfChanged(first).imported)

        val december = csv("1.병원정보서비스(2026.12.).csv", september.filterNot { it.startsWith("FAKE-004") }
            .map { it.replace("가상구 바다로 2", "가상구 바다로 22") })
        val result = importer.importIfChanged(december)

        assertEquals("2026-12", result.version)
        assertEquals(1, result.deactivatedCount)
        assertFalse(names("가상병원").contains("새가상병원"))
        assertEquals("부산광역시 가상구 바다로 22", search("가상병원").json!!.get("items")[0].get("address").asString())
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM hospitals WHERE external_id='FAKE-004'", Int::class.java))
    }

    @Test
    fun `a broken file leaves the previous list untouched`() {
        importer.importIfChanged(zip("data.zip", september))

        assertThrows<HospitalDatasetException> { importer.importIfChanged(csv("broken.csv", emptyList()).also {
            Files.writeString(it, "이름,주소\n가상,어딘가\n")
        }) }
        assertThrows<HospitalDatasetException> { importer.importIfChanged(dir.resolve("missing.zip")) }

        assertEquals(4, search("가상병원").json!!.get("totalCount").asInt())
    }

    @Test
    fun `quoted fields keep commas and doubled quotes`() {
        importer.importIfChanged(csv("quoted.csv", listOf(
            "FAKE-101,\"가상 \"\"큰\"\" 병원\",21,병원,110000,서울,\"서울특별시 가상구, 1층\",02-000-0000",
        )))

        val item = search("가상큰병원").json!!.get("items")[0]
        assertEquals("가상 \"큰\" 병원", item.get("name").asString())
        assertEquals("서울특별시 가상구, 1층", item.get("address").asString())
    }

    @Test
    fun `an unterminated quote fails the import instead of swallowing the following rows`() {
        importer.importIfChanged(zip("data.zip", september))
        val broken = september.take(2) + "FAKE-201,가상깨진병원,21,병원,110000,서울,\"닫히지 않은 주소,02-000-0000" + september.drop(2)

        assertThrows<HospitalDatasetException> { importer.importIfChanged(csv("broken-quote.csv", broken)) }

        assertEquals(4, search("가상병원").json!!.get("totalCount").asInt())
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM hospital_dataset_imports", Int::class.java))
    }

    @Test
    fun `a file that would deactivate too many institutions is rejected`() {
        importer.importIfChanged(zip("data.zip", september))

        // 5 active, only 2 left: 3 deactivations is above the 0.5 ratio in the test config.
        assertThrows<HospitalDatasetException> { importer.importIfChanged(csv("shrunk.csv", september.take(2))) }

        assertEquals(4, search("가상병원").json!!.get("totalCount").asInt())
    }

    @Test
    fun `a file below the minimum row count rolls back without a history entry`() {
        importer.importIfChanged(zip("data.zip", september))
        val strict = HospitalDatasetImporter(
            HospitalProperties(dataset = HospitalProperties.Dataset(minRows = 100)), hospitalRows, transactions,
        )

        assertThrows<HospitalDatasetException> { strict.importIfChanged(csv("small.csv", september)) }

        assertEquals(4, search("가상병원").json!!.get("totalCount").asInt())
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM hospital_dataset_imports", Int::class.java))
    }

    @Test
    fun `line breaks inside a quoted address stay in one row`() {
        importer.importIfChanged(csv("multiline.csv", listOf(
            "FAKE-301,가상줄바꿈병원,21,병원,110000,서울,\"서울특별시 가상구\r\n가상로 1\",02-000-0000",
            row("FAKE-302", "가상다음병원", "21", "병원", "서울특별시 가상구 2"),
        )))

        assertEquals("서울특별시 가상구\r\n가상로 1", search("가상줄바꿈병원").json!!.get("items")[0].get("address").asString())
        assertEquals(1, search("가상다음병원").json!!.get("totalCount").asInt())
    }

    @Test
    fun `queries that normalize to fewer than two characters are rejected`() {
        importer.importIfChanged(zip("data.zip", september))

        assertEquals("INVALID_REQUEST", search("(가)").code)
    }

    private companion object {
        val CP949: Charset = Charset.forName("MS949")
    }
}
