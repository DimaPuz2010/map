package com.example.map.data

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

    var userId: String = ""


    val categories = listOf(
        "Музеи", "Парки", "Кафе", "Рестораны",
        "Видовые точки", "Шоппинг", "Развлечения"
    )
}
