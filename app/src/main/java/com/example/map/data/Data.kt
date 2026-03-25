package com.example.map.data

/**
 * In-memory app-wide state.
 *
 * Note: `Api` methods expect `Authorization: Bearer <token>`.
 * This property normalizes both raw tokens and full headers.
 */
object Data {
    var userAuth: String = ""
        set(value) {
            field = value
                .trim()
                .let { v ->
                    when {
                        v.isBlank() -> ""
                        v.startsWith("Bearer ", ignoreCase = true) -> v
                        else -> "Bearer $v"
                    }
                }
        }
}
