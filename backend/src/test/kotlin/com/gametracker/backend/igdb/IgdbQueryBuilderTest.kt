package com.gametracker.backend.igdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgdbQueryBuilderTest {

    @Test
    fun `build with default parameters contains required fields and limits`() {
        val query = IgdbQueryBuilder.build()
        
        assertTrue(query.contains("fields name, rating, cover.url, first_release_date, summary, genres.name, platforms.name;"))
        assertTrue(query.contains("where cover != null;"))
        assertTrue(query.contains("limit 20;"))
        assertTrue(query.contains("offset 0;"))
    }

    @Test
    fun `build coerces limit into safe range 1 to 30`() {
        val querySmall = IgdbQueryBuilder.build(limit = -5)
        assertTrue(querySmall.contains("limit 1;"))

        val queryLarge = IgdbQueryBuilder.build(limit = 100)
        assertTrue(queryLarge.contains("limit 30;"))
    }

    @Test
    fun `build sanitizes search query from quotes`() {
        val query = IgdbQueryBuilder.build(searchQuery = "Grand \"Theft\" Auto; 'drop tables'")
        
        assertTrue(query.contains("search \"Grand Theft Auto; drop tables\";"))
    }

    @Test
    fun `build filters by ids correctly`() {
        val query = IgdbQueryBuilder.build(ids = listOf(1942L, 7346L, 1020L))
        
        assertTrue(query.contains("id = (1942,7346,1020)"))
    }

    @Test
    fun `build with rating and sort includes sort clause`() {
        val query = IgdbQueryBuilder.build(
            minRating = 85,
            sortBy = "rating",
            sortDirection = "desc"
        )
        
        assertTrue(query.contains("rating >= 85"))
        assertTrue(query.contains("sort rating desc;"))
    }

    @Test
    fun `build ignores invalid sort fields for safety`() {
        val query = IgdbQueryBuilder.build(
            sortBy = "malicious_field; delete from games"
        )
        
        assertTrue(!query.contains("sort malicious_field"))
    }
}
