package com.gametracker.backend.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestModelsTest {

    @Test
    fun `SearchRequest validates and normalizes valid query`() {
        val request = SearchRequest("  The Witcher 3: Wild Hunt!  ", limitParam = 15, offsetParam = 10)

        assertEquals("the witcher 3: wild hunt!", request.canonicalQuery)
        assertEquals(15, request.limit)
        assertEquals(10, request.offset)
        assertEquals("search_the witcher 3: wild hunt!_15_10", request.cacheKey)

        val apicalypse = request.toApicalypseQuery()
        assertTrue(apicalypse.contains("search \"the witcher 3: wild hunt!\";"))
        assertTrue(apicalypse.contains("cover.image_id"))
        assertTrue(apicalypse.contains("limit 15;"))
        assertTrue(apicalypse.contains("offset 10;"))
    }

    @Test
    fun `SearchRequest throws on blank query`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            SearchRequest("   ")
        }
        assertEquals("Search query 'q' parameter cannot be blank", ex.message)
    }

    @Test
    fun `SearchRequest throws on null query`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            SearchRequest(null)
        }
        assertEquals("Search query 'q' parameter cannot be blank", ex.message)
    }

    @Test
    fun `SearchRequest throws on query exceeding 100 code points`() {
        val longQuery = "a".repeat(101)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            SearchRequest(longQuery)
        }
        assertTrue(ex.message!!.contains("between 1 and 100 characters"))
    }

    @Test
    fun `SearchRequest rejects quotes and backslashes`() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchRequest("Grand \"Theft\" Auto")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearchRequest("Witcher\\")
        }
    }

    @Test
    fun `SearchRequest rejects control characters`() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchRequest("Witcher\n3")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearchRequest("Cyberpunk\t2077")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearchRequest("Game\u0000Title")
        }
    }

    @Test
    fun `SearchRequest rejects unpermitted characters like emoji`() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchRequest("Zelda ⚔️")
        }
    }

    @Test
    fun `SearchRequest coerces limit and offset safely`() {
        val requestSmall = SearchRequest("Zelda", limitParam = -5, offsetParam = -10)
        assertEquals(1, requestSmall.limit)
        assertEquals(0, requestSmall.offset)

        val requestLarge = SearchRequest("Zelda", limitParam = 500, offsetParam = 2000)
        assertEquals(30, requestLarge.limit)
        assertEquals(1000, requestLarge.offset)
    }

    @Test
    fun `TopRatedRequest clamps parameters and generates apicalypse`() {
        val request = TopRatedRequest(limitParam = 25, offsetParam = 50)
        assertEquals(25, request.limit)
        assertEquals(50, request.offset)
        assertEquals("top_rated_25_50", request.cacheKey)

        val query = request.toApicalypseQuery()
        assertTrue(query.contains("rating >= 80"))
        assertTrue(query.contains("cover.image_id"))
        assertTrue(query.contains("sort rating desc;"))
        assertTrue(query.contains("limit 25;"))
        assertTrue(query.contains("offset 50;"))
    }

    @Test
    fun `GameDetailsRequest rejects non-positive id`() {
        assertThrows(IllegalArgumentException::class.java) {
            GameDetailsRequest(null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameDetailsRequest(0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameDetailsRequest(-100L)
        }
    }

    @Test
    fun `GameDetailsRequest accepts positive id and formats query`() {
        val request = GameDetailsRequest(1020L)
        assertEquals(1020L, request.id)
        assertEquals("game_v2_1020", request.cacheKey)

        val query = request.toApicalypseQuery()
        assertTrue(query.contains("where id = (1020);"))
        assertFalse(query.contains("cover != null"))
        assertTrue(query.contains("total_rating, total_rating_count, url"))
        assertTrue(query.contains("themes.name, game_modes.name"))
        assertTrue(query.contains("release_dates.date, release_dates.y, release_dates.platform.name, release_dates.platform.abbreviation"))
        assertTrue(query.contains("involved_companies.company.name, involved_companies.developer, involved_companies.publisher"))
        assertTrue(query.contains("screenshots.image_id"))
        assertTrue(query.contains("videos.video_id, videos.name"))
        assertTrue(
            query.contains(
                "similar_games.id, similar_games.name, similar_games.cover.image_id, " +
                    "similar_games.total_rating, similar_games.rating"
            )
        )
        assertTrue(query.contains("limit 1;"))
    }

    @Test
    fun `RecommendationCandidatesRequest parses lists and builds cache key`() {
        val req = RecommendationCandidatesRequest(
            genresParam = " RPG, Shooter,RPG ",
            themesParam = "Fantasy",
            platformsParam = "PC",
            excludeParam = "1,2",
            similarToParam = "10",
            limitParam = 30,
        )
        assertEquals(listOf("RPG", "Shooter"), req.genres)
        assertEquals(listOf("Fantasy"), req.themes)
        assertEquals(listOf("PC"), req.platforms)
        assertEquals(listOf(1L, 2L), req.exclude)
        assertEquals(listOf(10L), req.similarTo)
        assertEquals(30, req.limit)
        assertTrue(req.cacheKey.startsWith("rec_"))
    }

    @Test
    fun `RecommendationCandidatesRequest accepts IGDB tag punctuation`() {
        val req = RecommendationCandidatesRequest(
            genresParam = "Role-playing (RPG),Hack and slash/Beat 'em up",
            platformsParam = "Xbox Series X|S",
        )
        assertEquals(listOf("Role-playing (RPG)", "Hack and slash/Beat 'em up"), req.genres)
        assertEquals(listOf("Xbox Series X|S"), req.platforms)
    }

    @Test
    fun `RecommendationCandidatesRequest rejects quote in genre`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationCandidatesRequest(genresParam = "RP\"G")
        }
    }

    @Test
    fun `RecommendationCandidatesRequest rejects non-positive ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationCandidatesRequest(excludeParam = "0")
        }
    }

    @Test
    fun `RecommendationCandidatesRequest tag query omits empty axes and excludes seeds`() {
        val req = RecommendationCandidatesRequest(
            genresParam = "RPG",
            excludeParam = "5",
            similarToParam = "10",
            limitParam = 10,
        )
        val q = req.toTagApicalypseQuery()
        assertTrue(q.contains("fields ") && q.contains("themes.name") && q.contains("rating_count"))
        assertTrue(q.contains("genres.name = (\"RPG\")"))
        assertFalse(q.contains("themes.name ="))
        assertTrue(q.contains("id != (5,10)") || q.contains("id != (10,5)"))
        assertTrue(q.contains("limit 10;"))
    }

    @Test
    fun `RecommendationCandidatesRequest similar seeds query expands similar_games id`() {
        val req = RecommendationCandidatesRequest(similarToParam = "10,20")
        val q = req.toSimilarSeedsApicalypseQuery()
        assertTrue(q.contains("similar_games.id"))
        assertTrue(q.contains("where id = (10,20)") || q.contains("where id = (20,10)"))
    }
}
