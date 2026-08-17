package com.gametracker.backend.igdb

import org.junit.Assert.assertTrue
import org.junit.Test

class IgdbQueryBuilderTest {

    @Test
    fun `buildSearch generates valid Apicalypse query with cover image_id`() {
        val query = IgdbQueryBuilder.buildSearch("Witcher", limit = 10, offset = 5)

        assertTrue(query.contains("fields name, rating, cover.url, cover.image_id"))
        assertTrue(query.contains("search \"witcher\";"))
        assertTrue(query.contains("where cover != null;"))
        assertTrue(query.contains("limit 10;"))
        assertTrue(query.contains("offset 5;"))
    }

    @Test
    fun `buildTopRated generates query sorted by rating`() {
        val query = IgdbQueryBuilder.buildTopRated(limit = 20, offset = 0)

        assertTrue(query.contains("rating >= 80"))
        assertTrue(query.contains("sort rating desc;"))
    }

    @Test
    fun `buildGameDetails generates single game query`() {
        val query = IgdbQueryBuilder.buildGameDetails(1020L)

        assertTrue(query.contains("where id = (1020)"))
        assertTrue(query.contains("limit 1;"))
    }
}
