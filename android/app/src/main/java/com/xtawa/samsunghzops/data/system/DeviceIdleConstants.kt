package com.xtawa.samsunghzops.data.system

/**
 * Android 15+ device_idle_constants must be treated as an ordered key-value
 * map. Unknown keys and the original order are preserved on every write.
 */
object DeviceIdleConstants {
    val balancedPreset: Map<String, String> = linkedMapOf(
        "inactive_to" to "60000",
        "sensing_to" to "0",
        "locating_to" to "0",
        "motion_inactive_to" to "60000",
    )

    fun parse(raw: String?): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        if (raw.isNullOrBlank()) return result
        raw.split(',').forEach { token ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return@forEach
            val separator = trimmed.indexOf('=')
            if (separator <= 0) {
                result[trimmed] = ""
            } else {
                val key = trimmed.substring(0, separator).trim()
                val value = trimmed.substring(separator + 1).trim()
                if (key.isNotEmpty()) result[key] = value
            }
        }
        return result
    }

    fun merge(existing: String?, updates: Map<String, String>): String {
        val merged = parse(existing)
        updates.forEach { (key, value) -> merged[key] = value }
        return encode(merged)
    }

    fun removeKeys(existing: String?, keys: Set<String>): String? {
        val merged = parse(existing)
        keys.forEach(merged::remove)
        return encode(merged).ifBlank { null }
    }

    fun encode(values: Map<String, String>): String =
        values.entries.joinToString(",") { (key, value) ->
            if (value.isEmpty()) key else "$key=$value"
        }
}
