package com.nurseflow.hospital.application.service

import com.nurseflow.hospital.config.HospitalProperties
import com.nurseflow.hospital.domain.HospitalSearchName
import com.nurseflow.hospital.persistence.repository.HospitalJdbcRepository
import com.nurseflow.hospital.persistence.repository.HospitalRow
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.BufferedReader
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/** Outcome of one import. `imported` is false when the same file was already imported. */
data class HospitalImportResult(
    val imported: Boolean,
    val version: String,
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
    val deactivatedCount: Int = 0,
)

class HospitalDatasetException(message: String) : RuntimeException(message)

/**
 * Imports the `병원정보서비스` CSV of 「전국 병의원 및 약국 현황」 (CP949, one row per institution) in one
 * transaction: every row is upserted and reactivated, institutions missing from the file are deactivated.
 * A truncated file, a CSV syntax error or an unusually large deactivation rolls the whole import back.
 * The pharmacy and detail CSVs in the same zip are ignored. Searches keep reading the previous list until commit.
 */
@Service
class HospitalDatasetImporter(
    private val properties: HospitalProperties,
    private val hospitals: HospitalJdbcRepository,
    private val transactions: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun importIfChanged(path: Path): HospitalImportResult {
        if (!Files.isRegularFile(path)) throw HospitalDatasetException("hospital dataset file not found")
        val sha256 = sha256(path)
        hospitals.latestImport(SOURCE)?.takeIf { it.fileSha256 == sha256 }?.let {
            return HospitalImportResult(imported = false, version = it.version)
        }
        return if (path.fileName.toString().endsWith(".zip", ignoreCase = true)) {
            ZipFile(path.toFile()).use { zip ->
                val entry = zip.entries().asSequence()
                    .firstOrNull { !it.isDirectory && it.name.contains(HOSPITAL_ENTRY) && it.name.endsWith(".csv", true) }
                    ?: throw HospitalDatasetException("zip has no $HOSPITAL_ENTRY CSV")
                zip.getInputStream(entry).use { importCsv(it, versionOf(entry.name), sha256) }
            }
        } else {
            Files.newInputStream(path).use { importCsv(it, versionOf(path.fileName.toString()), sha256) }
        }
    }

    /**
     * Imports the list bundled with the server (the `.csv.gz` file in resources `hospitals/`, made by
     * `backend/scripts/extract_hospital_list.py`) when it differs from the latest import, like a changed `path` file,
     * so a corrected list of the same period is applied. It is skipped when it is older than the latest import, so
     * a restart without a dataset path never replaces a newer list from `path`, and when either period is unknown,
     * since then neither can be shown to be newer; importing a dated file from `path` resumes bundled updates.
     */
    fun importBundledUnlessOlder(resource: Resource): HospitalImportResult {
        val name = resource.filename ?: throw HospitalDatasetException("bundled hospital list has no file name")
        val version = versionOf(name)
        val bytes = resource.inputStream.use { it.readAllBytes() }
        val sha256 = sha256(bytes)
        hospitals.latestImport(SOURCE)?.takeIf {
            it.fileSha256 == sha256 || version == UNKNOWN_VERSION || it.version == UNKNOWN_VERSION || version < it.version
        }?.let {
            return HospitalImportResult(imported = false, version = it.version)
        }
        return GZIPInputStream(bytes.inputStream()).use { importCsv(it, version, sha256) }
    }

    private fun importCsv(input: InputStream, version: String, sha256: String): HospitalImportResult {
        val reader = input.bufferedReader(CP949)
        val header = HospitalCsv.readRecord(reader)?.map { it.trim().removePrefix("﻿") }
            ?: throw HospitalDatasetException("empty hospital dataset")
        val column = REQUIRED_COLUMNS.associateWith { name ->
            header.indexOf(name).takeIf { it >= 0 } ?: throw HospitalDatasetException("missing column $name")
        }
        val importId = UUID.randomUUID()
        return transactions.execute {
            val previouslyActive = hospitals.countActive(SOURCE)
            var imported = 0
            var skipped = 0
            rows(reader, column).chunked(BATCH).forEach { batch ->
                val valid = batch.filterNotNull()
                skipped += batch.size - valid.size
                hospitals.upsert(SOURCE, version, importId, valid)
                imported += valid.size
            }
            if (imported < properties.dataset.minRows) {
                throw HospitalDatasetException("only $imported rows; expected at least ${properties.dataset.minRows}")
            }
            val deactivated = hospitals.deactivateUnseen(SOURCE, importId)
            if (deactivated > previouslyActive * properties.dataset.maxDeactivationRatio) {
                throw HospitalDatasetException(
                    "would deactivate $deactivated of $previouslyActive institutions; " +
                        "above ${properties.dataset.maxDeactivationRatio}",
                )
            }
            hospitals.recordImport(importId, SOURCE, version, sha256, imported, skipped, deactivated, Instant.now())
            log.info("Hospital dataset {} imported: rows={} skipped={} deactivated={}", version, imported, skipped, deactivated)
            HospitalImportResult(true, version, imported, skipped, deactivated)
        }!!
    }

    /** Rows without an id or a name are counted as skipped (null). */
    private fun rows(reader: BufferedReader, column: Map<String, Int>): Sequence<HospitalRow?> =
        generateSequence { HospitalCsv.readRecord(reader) }.filter { it.any(String::isNotBlank) }.map { record ->
            fun field(name: String) = record.getOrNull(column.getValue(name))?.trim().orEmpty()
            val id = field(ID)
            val name = field(NAME)
            val searchName = HospitalSearchName.of(name)
            if (id.isEmpty() || id.length > 255 || searchName.isEmpty()) {
                null
            } else {
                HospitalRow(
                    externalId = id,
                    name = name.take(200),
                    searchName = searchName.take(200),
                    searchRank = HospitalSearchName.rank(field(CATEGORY_CODE)),
                    categoryName = field(CATEGORY_NAME).takeIf { it.isNotEmpty() }?.take(50),
                    address = field(ADDRESS).take(500),
                )
            }
        }

    companion object {
        const val SOURCE = "HIRA"
        private const val HOSPITAL_ENTRY = "병원정보서비스"
        private const val ID = "암호화요양기호"
        private const val NAME = "요양기관명"
        private const val CATEGORY_CODE = "종별코드"
        private const val CATEGORY_NAME = "종별코드명"
        private const val ADDRESS = "주소"
        private val REQUIRED_COLUMNS = listOf(ID, NAME, CATEGORY_CODE, CATEGORY_NAME, ADDRESS)
        private val CP949: Charset = Charset.forName("MS949")
        private const val BATCH = 1_000
        private const val UNKNOWN_VERSION = "unknown"
        private val VERSION = Regex("\\((\\d{4})\\.(\\d{1,2})\\.?\\)")

        /** "1.병원정보서비스(2026.9.).csv" → "2026-09"; files without a period are versioned "unknown". */
        internal fun versionOf(fileName: String): String =
            VERSION.find(fileName)?.let { "${it.groupValues[1]}-${it.groupValues[2].padStart(2, '0')}" } ?: UNKNOWN_VERSION

        private fun sha256(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Minimal RFC 4180 reader: quoted fields may contain commas, doubled quotes and line breaks. An unterminated
 * quote or an implausibly long field fails the whole import instead of silently merging rows.
 */
internal object HospitalCsv {
    private const val MAX_FIELD = 2_000

    fun readRecord(reader: BufferedReader): List<String>? {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var sawAny = false
        while (true) {
            val c = reader.read()
            if (c < 0) {
                // A missing closing quote would otherwise swallow every following row into one field.
                if (quoted) throw HospitalDatasetException("unterminated quoted field")
                if (!sawAny) return null
                fields += field.toString()
                return fields
            }
            sawAny = true
            val ch = c.toChar()
            // After a closing quote only a delimiter may follow. Anything else means a quote went missing
            // and the reader has been merging the following rows into this field.
            if (closedQuote && ch != ',' && ch != '\n' && ch != '\r') {
                throw HospitalDatasetException("unexpected character after a closing quote")
            }
            closedQuote = false
            when {
                quoted && ch == '"' -> {
                    reader.mark(1)
                    if (reader.read() == '"'.code) {
                        field.append('"')
                    } else {
                        quoted = false
                        closedQuote = true
                        reader.reset()
                    }
                }
                quoted -> field.append(ch)
                ch == '"' && field.isEmpty() -> quoted = true
                ch == ',' -> { fields += field.toString(); field.clear() }
                ch == '\r' && !quoted -> Unit
                ch == '\n' -> { fields += field.toString(); return fields }
                else -> field.append(ch)
            }
            // Any field this long means a broken file; stop before it grows without bound.
            if (field.length > MAX_FIELD) throw HospitalDatasetException("field longer than $MAX_FIELD")
        }
    }
}
