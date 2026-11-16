package com.macedonga.visor.core.media

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class MobileConfigReportTest {
    @Test fun theSkylerLiteLayoutIncludesItsActualValues() {
        val report = read(
            "mobileconfig/params_map_v4_u0.txt" to "lite-map",
            "mobileconfig/params_names_v4_u0.txt" to "lite-names",
            "mobileconfig/current_mc_values.json" to "actual-values",
            "mobileconfig/current_timestamp.txt" to "timestamp",
        )!!
        assertEquals("lite-map", report.map.decodeToString())
        assertEquals("lite-names", report.names.decodeToString())
        assertEquals("actual-values", report.values!!.decodeToString())
        assertEquals("timestamp", report.timestamp!!.decodeToString())
    }

    @Test fun mergedReportsStillUseTheMergedSchema() {
        val report = read(
            "mobileconfig/maps/current/merged_params_map_v4_u0.txt" to "merged-map",
            "mobileconfig/maps/current/merged_params_names_v4_u0.txt" to "merged-names",
            "mobileconfig/params_map_v4_u0.txt" to "lite-map",
            "mobileconfig/params_names_v4_u0.txt" to "lite-names",
        )!!
        assertEquals("merged-map", report.map.decodeToString())
        assertEquals("merged-names", report.names.decodeToString())
    }

    @Test fun anIncompleteSchemaCannotBeMixedWithAnotherLayoutsNames() {
        assertNull(read(
            "mobileconfig/maps/current/merged_params_map_v4_u0.txt" to "merged-map",
            "mobileconfig/params_names_v4_u0.txt" to "lite-names",
        ))
    }

    @Test fun aMissingValuesFileIsDistinguishableFromAMissingSchema() {
        val report = read(
            "mobileconfig/params_map_v4_u0.txt" to "map",
            "mobileconfig/params_names_v4_u0.txt" to "names",
        )!!
        assertNull(report.values)
        assertNull(read("unrelated.txt" to "no configuration"))
    }

    private fun read(vararg entries: Pair<String, String>): MobileConfigReport? {
        val path = Files.createTempFile("mobileconfig-test", ".zip")
        try {
            ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                for ((name, value) in entries) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
                }
            }
            return ZipFile(path.toFile()).use(::readMobileConfigReport)
        } finally { Files.deleteIfExists(path) }
    }
}
