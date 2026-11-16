package com.macedonga.visor.core.media

import java.util.zip.ZipFile

internal data class MobileConfigReport(
    val map: ByteArray,
    val names: ByteArray,
    val values: ByteArray?,
    val session: ByteArray?,
    val timestamp: ByteArray?,
    val boot: ByteArray?,
)

/** Full and lite reports use different paths for the same firmware-owned parameter schema. */
internal fun readMobileConfigReport(zip: ZipFile): MobileConfigReport? {
    fun bytes(path: String) = zip.getEntry(path)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }
    val layouts = listOf(
        "mobileconfig/maps/current/merged_params_map_v4_u0.txt" to "mobileconfig/maps/current/merged_params_names_v4_u0.txt",
        "mobileconfig/params_map_v4_u0.txt" to "mobileconfig/params_names_v4_u0.txt",
    )
    // Never combine a map from one layout with a names file from another.
    val schema = layouts.firstNotNullOfOrNull { (map, names) ->
        bytes(map)?.let { m -> bytes(names)?.let { n -> m to n } }
    } ?: return null
    return MobileConfigReport(
        schema.first, schema.second,
        bytes("mobileconfig/current_mc_values.json"),
        bytes("mobileconfig/session_info.json"),
        bytes("mobileconfig/current_timestamp.txt"),
        bytes("settingsservice/boot_session_id"),
    )
}
