package com.nurseflow.hospital.persistence.repository

import com.nurseflow.hospital.application.model.HospitalCandidate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/** One row of the official list, ready to upsert. */
data class HospitalRow(
    val externalId: String,
    val name: String,
    val searchName: String,
    val searchRank: Int,
    val categoryName: String?,
    val address: String,
)

data class HospitalDatasetInfo(val version: String, val fileSha256: String)

@Repository
class HospitalJdbcRepository(private val jdbc: JdbcTemplate) {
    fun hasActive(source: String): Boolean = countActive(source) > 0

    /**
     * Contains-match on the normalized name. Order: exact name, then hospital-level institutions before clinics
     * (a nurse looking for "세브란스" wants the hospitals before clinics whose names merely start with it),
     * then prefix before contains, then name.
     */
    fun search(searchName: String, offset: Int, limit: Int): List<HospitalCandidate> = jdbc.query(
        """SELECT source, external_id, name, address, category_name FROM hospitals
           WHERE active=TRUE AND search_name LIKE ?
           ORDER BY CASE WHEN search_name=? THEN 0 ELSE 1 END, search_rank,
                    CASE WHEN search_name LIKE ? THEN 0 ELSE 1 END, name, external_id
           OFFSET ? ROWS FETCH NEXT ? ROWS ONLY""",
        { rs, _ ->
            HospitalCandidate(
                source = rs.getString("source"),
                externalId = rs.getString("external_id"),
                name = rs.getString("name"),
                address = rs.getString("address"),
                category = rs.getString("category_name"),
            )
        },
        "%$searchName%", searchName, "$searchName%", offset, limit,
    )

    fun findActive(source: String, externalId: String): HospitalCandidate? = jdbc.query(
        """SELECT source, external_id, name, address, category_name FROM hospitals
           WHERE source=? AND external_id=? AND active=TRUE""",
        { rs, _ ->
            HospitalCandidate(
                source = rs.getString("source"),
                externalId = rs.getString("external_id"),
                name = rs.getString("name"),
                address = rs.getString("address"),
                category = rs.getString("category_name"),
            )
        },
        source, externalId,
    ).firstOrNull()

    fun count(searchName: String): Int = jdbc.queryForObject(
        "SELECT COUNT(*) FROM hospitals WHERE active=TRUE AND search_name LIKE ?", Int::class.java, "%$searchName%",
    )!!

    fun latestImport(source: String): HospitalDatasetInfo? = jdbc.query(
        "SELECT dataset_version, file_sha256 FROM hospital_dataset_imports WHERE source=? ORDER BY imported_at DESC FETCH FIRST 1 ROWS ONLY",
        { rs, _ -> HospitalDatasetInfo(rs.getString("dataset_version"), rs.getString("file_sha256")) },
        source,
    ).firstOrNull()

    /** Upserts and reactivates the rows, marking them as seen by [importId]. */
    fun upsert(source: String, version: String, importId: UUID, rows: List<HospitalRow>) {
        jdbc.batchUpdate(
            """MERGE INTO hospitals h
               USING (VALUES (CAST(? AS VARCHAR(255)), CAST(? AS VARCHAR(20)), CAST(? AS VARCHAR(200)),
                              CAST(? AS VARCHAR(200)), CAST(? AS INT), CAST(? AS VARCHAR(50)),
                              CAST(? AS VARCHAR(500)), CAST(? AS VARCHAR(20)), CAST(? AS UUID)))
                     AS v(external_id, source, name, search_name, search_rank, category_name, address, dataset_version,
                          last_import_id)
               ON h.external_id = v.external_id
               WHEN MATCHED THEN UPDATE SET source=v.source, name=v.name, search_name=v.search_name,
                    search_rank=v.search_rank, category_name=v.category_name, address=v.address,
                    active=TRUE, dataset_version=v.dataset_version, last_import_id=v.last_import_id
               WHEN NOT MATCHED THEN INSERT (external_id, source, name, search_name, search_rank, category_name,
                    address, active, dataset_version, last_import_id)
                    VALUES (v.external_id, v.source, v.name, v.search_name, v.search_rank, v.category_name,
                    v.address, TRUE, v.dataset_version, v.last_import_id)""",
            rows.map {
                arrayOf<Any?>(it.externalId, source, it.name, it.searchName, it.searchRank, it.categoryName, it.address, version, importId)
            },
        )
    }

    fun countActive(source: String): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM hospitals WHERE source=? AND active=TRUE", Int::class.java, source)!!

    /** Deactivates active rows the import [importId] did not contain; returns how many. */
    fun deactivateUnseen(source: String, importId: UUID): Int = jdbc.update(
        "UPDATE hospitals SET active=FALSE WHERE source=? AND active=TRUE AND last_import_id<>?", source, importId,
    )

    fun recordImport(
        importId: UUID,
        source: String,
        version: String,
        sha256: String,
        imported: Int,
        skipped: Int,
        deactivated: Int,
        now: Instant,
    ) {
        jdbc.update(
            """INSERT INTO hospital_dataset_imports
               (import_id,source,dataset_version,file_sha256,imported_count,skipped_count,deactivated_count,imported_at)
               VALUES(?,?,?,?,?,?,?,?)""",
            importId, source, version, sha256, imported, skipped, deactivated, now.toString(),
        )
    }
}
