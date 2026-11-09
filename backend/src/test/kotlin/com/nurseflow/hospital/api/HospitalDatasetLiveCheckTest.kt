package com.nurseflow.hospital.api

import com.nurseflow.hospital.application.service.HospitalDatasetImporter
import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.net.URLEncoder
import java.nio.file.Path

/**
 * Manual check with the real downloaded 「전국 병의원 및 약국 현황」 zip. Skipped unless
 * `NURSEFLOW_HOSPITAL_DATASET_PATH` is set; the file is never committed. Prints timings and public names only.
 */
class HospitalDatasetLiveCheckTest : ApiTestBase() {
    @Autowired lateinit var importer: HospitalDatasetImporter

    @Test
    fun `real dataset imports and searches quickly`() {
        val path = System.getenv("NURSEFLOW_HOSPITAL_DATASET_PATH").orEmpty()
        assumeTrue(path.isNotBlank(), "Set NURSEFLOW_HOSPITAL_DATASET_PATH to run the dataset check")

        val started = System.nanoTime()
        val result = importer.importIfChanged(Path.of(path))
        println("Dataset ${result.version}: rows=${result.importedCount} skipped=${result.skippedCount} " +
            "import=${(System.nanoTime() - started) / 1_000_000}ms")

        val token = userToken().second
        listOf("서울대학교병원", "삼성서울병원", "세브란스", "아산", "가톨릭", "보훈").forEach { query ->
            val t = System.nanoTime()
            val response = http.get("/api/v1/hospitals/search?size=5&query=" + URLEncoder.encode(query, Charsets.UTF_8), token)
            val ms = (System.nanoTime() - t) / 1_000_000
            val body = response.json!!
            println("search '$query': ${ms}ms total=${body.get("totalCount").asInt()}")
            body.get("items").toList().forEach { println("   ${it.get("name").asString()} | ${it.get("category").asString()} | ${it.get("address").asString()}") }
            assertTrue(response.status == 200)
        }
    }
}
