package com.gametracker.backend.igdb

object IgdbQueryBuilder {
    private const val ALLOWED_FIELDS = "name, rating, cover.url, first_release_date, summary, genres.name, platforms.name"
    private val ALLOWED_SORT_FIELDS = setOf("rating", "first_release_date", "name")
    
    fun build(
        limit: Int = 20,
        offset: Int = 0,
        searchQuery: String? = null,
        ids: List<Long>? = null,
        minRating: Int? = null,
        sortBy: String? = null,
        sortDirection: String = "desc"
    ): String {
        // Enforce quota limits: Page size 20-30 max
        val safeLimit = limit.coerceIn(1, 30)
        val safeOffset = offset.coerceAtLeast(0)
        
        val builder = StringBuilder("fields $ALLOWED_FIELDS;\n")
        
        if (!searchQuery.isNullOrBlank()) {
            // Защита от инъекций в строку поиска
            val safeSearch = searchQuery.replace("\"", "").replace("'", "")
            builder.append("search \"$safeSearch\";\n")
        }
        
        val whereClauses = mutableListOf<String>()
        
        if (!ids.isNullOrEmpty()) {
            // Batch ID requests
            val safeIds = ids.map { it.toString().toLong() } // ensure valid numbers
            whereClauses.add("id = (${safeIds.joinToString(",")})")
        }
        
        if (minRating != null) {
            val safeRating = minRating.coerceIn(0, 100)
            whereClauses.add("rating >= $safeRating")
        }
        
        // Улучшение UI: не показываем игры без обложки
        whereClauses.add("cover != null")
        
        if (whereClauses.isNotEmpty()) {
            builder.append("where ${whereClauses.joinToString(" & ")};\n")
        }
        
        if (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) {
            val direction = if (sortDirection.lowercase() == "asc") "asc" else "desc"
            builder.append("sort $sortBy $direction;\n")
        }
        
        builder.append("limit $safeLimit;\n")
        builder.append("offset $safeOffset;")
        
        return builder.toString()
    }
}
