package com.nurseflow.hospital.application.service

import com.nurseflow.hospital.config.HospitalProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.nio.file.Path

/**
 * Imports the configured hospital dataset at startup when the file changed. A failed import is logged and
 * the server keeps serving the previously imported list, so a bad file never takes the API down.
 */
@Component
class HospitalDatasetStartupImport(
    private val properties: HospitalProperties,
    private val importer: HospitalDatasetImporter,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val path = properties.dataset.path.trim().takeIf { it.isNotEmpty() } ?: return
        try {
            val result = importer.importIfChanged(Path.of(path))
            if (!result.imported) log.info("Hospital dataset {} unchanged; import skipped", result.version)
        } catch (ex: Exception) {
            log.error("Hospital dataset import failed; keeping the previous list: {}", ex.message)
        }
    }
}
