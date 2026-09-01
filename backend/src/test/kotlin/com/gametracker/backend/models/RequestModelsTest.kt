package com.gametracker.backend.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertTrue(request.cacheKey.startsWith("search:v3|q=25:the witcher 3: wild hunt!"))
        val apicalypse = request.toApicalypseQuery()
        assertTrue(apicalypse.contains("search \"the witcher 3: wild hunt!\";"))
        assertTrue(apicalypse.contains("cover.image_id"))
        assertTrue(apicalypse.contains("limit 15;"))
        assertTrue(apicalypse.contains("offset 10;"))
    }

    @Test
    fun `SearchRequest throws on blank query without filters`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            SearchRequest("   ")
        }
        assertEquals("Search query 'q' parameter cannot be blank", ex.message)
    }

    @Test
    fun `SearchRequest throws on null query without filters`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            SearchRequest(null)
        }
        assertEquals("Search requires 'q' or at least one filter", ex.message)
    }

    @Test
    fun `SearchRequest rejects control-only query even when filters are present`() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchRequest(
                rawQuery = "\n",
                genresParam = "Action",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearchRequest(
                rawQuery = "\t",
                platformsParam = "PC (Microsoft Windows)",
            )
        }
    }

    @Test
    fun `SearchRequest collapses whitespace runs before canonicalization`() {
        val request = SearchRequest("Grand \u00A0\u2009 Theft")
        assertEquals("grand theft", request.canonicalQuery)
    }

    @Test
    fun `SearchRequest accepts filters with null or blank query and applies sort`() {
        val request = SearchRequest(
            rawQuery = null,
            genresParam = "Role-playing (RPG), Adventure",
            platformsParam = "PC (Microsoft Windows)",
            minRatingParam = 85,
            minYearParam = 2023,
            maxYearParam = 2024,
            sortParam = "rating_desc",
            limitParam = 25,
            offsetParam = 0,
        )

        assertEquals(null, request.canonicalQuery)
        assertTrue(request.hasFilters)
        assertEquals(listOf("Role-playing (RPG)", "Adventure"), request.genres)
        assertEquals(listOf("PC (Microsoft Windows)"), request.platforms)
        assertEquals(85, request.minRating)
        assertEquals(2023, request.minYear)
        assertEquals(2024, request.maxYear)
        assertEquals(SearchSortField.RATING, request.sort)

        val apicalypse = request.toApicalypseQuery()
        assertFalse(apicalypse.contains("search \""))
        assertTrue(apicalypse.contains("genres = (12) & genres = (31)"))
        assertFalse(apicalypse.contains("genres.name ="))
        assertTrue(apicalypse.contains("platforms.name = (\"PC (Microsoft Windows)\")"))
        assertTrue(apicalypse.contains("rating >= 85"))
        assertTrue(apicalypse.contains("first_release_date >="))
        assertTrue(apicalypse.contains("first_release_date <="))
        assertTrue(apicalypse.contains("sort rating desc;"))
    }

    @Test
    fun `SearchRequest with query and filters does not add sort clause to apicalypse`() {
        val request = SearchRequest(
            rawQuery = "Zelda",
            genresParam = "Adventure",
            sortParam = "rating",
        )

        val apicalypse = request.toApicalypseQuery()
        assertTrue(apicalypse.contains("search \"zelda\";"))
        assertTrue(apicalypse.contains("genres = (31)"))
        assertFalse(apicalypse.contains("genres.name ="))
        assertFalse(apicalypse.contains("sort rating"))
    }
    @Test
    fun `SearchRequest with RPG genre and Action theme maps to genre 12 and theme 1`() {
        val request = SearchRequest(
            rawQuery = null,
            genresParam = "Role-playing (RPG), Action",
            sortParam = "rating_desc",
        )
        val apicalypse = request.toApicalypseQuery()
        assertTrue(apicalypse.contains("genres = (12) & themes = (1)"))
        assertTrue(apicalypse.contains("sort rating desc;"))
    }


    @Test
    fun `SearchRequest with text query produces identical cacheKey regardless of sortParam`() {
        val req1 = SearchRequest(rawQuery = "Zelda", sortParam = "rating")
        val req2 = SearchRequest(rawQuery = "Zelda", sortParam = "name")
        val req3 = SearchRequest(rawQuery = "Zelda", sortParam = "first_release_date_asc")
        val reqDefault = SearchRequest(rawQuery = "Zelda")

        assertEquals(reqDefault.cacheKey, req1.cacheKey)
        assertEquals(reqDefault.cacheKey, req2.cacheKey)
        assertEquals(reqDefault.cacheKey, req3.cacheKey)
    }

    @Test
    fun `SearchRequest without text query produces distinct cacheKey for different sortParams`() {
        val reqRating = SearchRequest(rawQuery = null, genresParam = "RPG", sortParam = "rating")
        val reqDate = SearchRequest(rawQuery = null, genresParam = "RPG", sortParam = "first_release_date_desc")

        assertNotEquals(reqRating.cacheKey, reqDate.cacheKey)
        assertEquals(SearchSortField.RATING, reqRating.effectiveSort)
        assertEquals(SearchSortField.FIRST_RELEASE_DATE_DESC, reqDate.effectiveSort)
    }

    @Test
    fun `SearchRequest rejects invalid sort parameter`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            SearchRequest(
                rawQuery = "Elden",
                sortParam = "unsupported_sort",
            )
        }
        assertTrue(ex.message?.contains("unsupported value") == true)
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
    fun `SearchRequest accepts common punctuation, emoji and unicode product names`() {
        val cases = listOf(
            "DOOM (2016)" to "doom (2016)",
            "Pokémon Sword/Shield" to "pokémon sword/shield",
            "NieR™" to "nier™",
            "Game [Demo]" to "game [demo]",
            "Zelda ⚔️" to "zelda ⚔️",
            "Metal Gear | Solid" to "metal gear | solid",
            "Family 👨‍👩‍👧" to "family 👨👩👧",
        )
        for ((input, expected) in cases) {
            val request = SearchRequest(input)
            assertEquals(expected, request.canonicalQuery)
            assertTrue(request.toApicalypseQuery().contains("search \"${expected}\";"))
        }
    }

    @Test
    fun `SearchRequest normalizes NBSP to a regular space`() {
        val request = SearchRequest("Grand\u00A0Theft Auto")
        assertEquals("grand theft auto", request.canonicalQuery)
        assertTrue(request.toApicalypseQuery().contains("search \"grand theft auto\";"))
    }

    @Test
    fun `SearchRequest rejects invisible and bidi format characters`() {
        val invisible = listOf(
            "zero\u200Bwidth",
            "non\u200Cjoiner",
            "lrm\u200Emark",
            "rlm\u200Fmark",
            "bidi\u202Eembed",
            "isolate\u2066char",
            "bom\uFEFFinside",
        )
        for (input in invisible) {
            assertThrows("Expected rejection for '$input'", IllegalArgumentException::class.java) {
                SearchRequest(input)
            }
        }
    }

    @Test
    fun `SearchRequest accepts ZWJ in input but strips it from the canonical form`() {
        val request = SearchRequest("Woman 👩\u200D💻 Coding")
        assertEquals("woman 👩💻 coding", request.canonicalQuery)
        // Variation selectors (U+FE0F) are combining marks: they survive the canonical form.
        val withVariation = SearchRequest("Zelda ⚔️")
        assertEquals("zelda ⚔️", withVariation.canonicalQuery)
    }

    @Test
    fun `SearchRequest accepts retro range starting at 1950`() {
        val request = SearchRequest(rawQuery = null, minYearParam = 1950)
        assertEquals(1950, request.minYear)
        assertThrows(IllegalArgumentException::class.java) {
            SearchRequest(rawQuery = null, minYearParam = 1949)
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
    fun `search cache key cannot collide across tag fields`() {
        val embeddedDelimiter = SearchRequest(
            rawQuery = null,
            genresParam = "RPG|PC",
        )
        val separateFields = SearchRequest(
            rawQuery = null,
            genresParam = "RPG",
            platformsParam = "PC",
        )

        assertNotEquals(embeddedDelimiter.cacheKey, separateFields.cacheKey)
    }

    @Test
    fun `SearchRequest supports full catalog of 22 genres without error`() {
        val all22Genres = listOf(
            "Role-playing (RPG)", "Action", "Adventure", "Shooter", "Strategy",
            "Turn-based strategy (TBS)", "Real-time strategy (RTS)", "Platform",
            "Puzzle", "Indie", "Simulator", "Sport", "Racing", "Fighting",
            "Hack and slash/Beat 'em up", "Music", "Arcade", "Visual Novel",
            "Point-and-click", "Tactical", "MOBA", "Card & Board Game"
        ).joinToString(",")

        val request = SearchRequest(rawQuery = null, genresParam = all22Genres)
        assertEquals(22, request.genres.size)
        assertTrue(request.cacheKey.startsWith("search:v3|q=0:"))
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
    fun `TrendingRequest uses visits popularity type and isolated cache key`() {
        val request = TrendingRequest(limitParam = 10, offsetParam = 5)
        assertEquals(10, request.limit)
        assertEquals(5, request.offset)
        assertEquals("trending_10_5", request.cacheKey)
        val primitives = request.toPrimitivesApicalypseQuery()
        assertTrue(primitives.contains("popularity_type = 1"))
        assertTrue(primitives.contains("sort value desc"))
        assertTrue(primitives.contains("limit 15;"))
        assertFalse(primitives.contains("rating >= 80"))
        val hydrate = request.toHydrateApicalypseQuery(listOf(72L, 14593L))
        assertTrue(hydrate.contains("limit 2;"))
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
        assertTrue(query.contains("artworks.image_id"))
        assertTrue(
            query.contains(
                "similar_games.id, similar_games.name, similar_games.cover.image_id, " +
                    "similar_games.total_rating, similar_games.rating, " +
                    "similar_games.genres.name, similar_games.platforms.name, " +
                    "similar_games.platforms.abbreviation"
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
    @Test
    fun `PopularityRailRequest maps names and limits primitive window`() {
        val request = PopularityRailRequest("playing", 20, 40)

        assertEquals(PopularityRailRequest.PLAYING_TYPE, request.popularityType)
        assertEquals(20, request.limit)
        assertEquals(40, request.offset)
        assertEquals(60, request.primitiveFetchLimit)
    }

    @Test
    fun `PopularityRailRequest rejects unknown rail and offset beyond primitive cap`() {
        assertThrows(IllegalArgumentException::class.java) {
            PopularityRailRequest("unknown", 20, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PopularityRailRequest("visits", 20, 500)
        }
    }

    @Test
    fun `RecommendationCandidatesRequest paginates tag query from page zero`() {
        val request = RecommendationCandidatesRequest(
            genresParam = "RPG",
            offsetParam = 40,
            limitParam = 20,
        )

        val query = request.toTagApicalypseQuery()
        assertTrue(query.contains("limit 60;"))
        assertTrue(query.contains("offset 0;"))
    }
 }
