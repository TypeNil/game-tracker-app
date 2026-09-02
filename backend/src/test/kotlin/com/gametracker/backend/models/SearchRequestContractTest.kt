package com.gametracker.backend.models

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
private val contractJson = Json { ignoreUnknownKeys = true }

/**
 * Contract parity tests against the shared fixture `config/search-contract/search-contract-cases.json`.
 * The same fixture is executed against FakeBffDataSource in the Android module; both sides must
 * agree on normalization, out-of-range normalization, tag semantics and the cover policy.
 */
class SearchRequestContractTest {

    private val cases: SearchContractCases by lazy {
        val fixture = File("../config/search-contract/search-contract-cases.json")
        require(fixture.exists()) { "Shared contract fixture is missing at ${fixture.absolutePath}" }
        contractJson.decodeFromString<SearchContractCases>(fixture.readText())
    }

    @Test
    fun `normalization cases produce identical canonical queries`() {
        for (case in cases.normalization) {
            val request = SearchRequest(case.input)
            assertEquals("Canonical mismatch for '${case.id}'", case.expected, request.canonicalQuery)
        }
    }

    @Test
    fun `accepted cases are valid and quoted into apicalypse search literal`() {
        for (case in cases.accepted) {
            val request = SearchRequest(case.input)
            assertNotNull("Expected accepted query '${case.id}'", request.canonicalQuery)
            assertTrue(
                "Query '${case.id}' must be quoted in the search literal",
                request.toApicalypseQuery().contains("search \"${request.canonicalQuery}\";"),
            )
        }
    }

    @Test
    fun `rejected cases throw IllegalArgumentException`() {
        for (case in cases.rejected) {
            assertThrows("Expected rejection for '${case.id}'", IllegalArgumentException::class.java) {
                SearchRequest(case.input)
            }
        }
    }

    @Test
    fun `out of range pagination is normalized according to api contract`() {
        for (case in cases.pagination) {
            val request = SearchRequest(rawQuery = "zelda", limitParam = case.limit, offsetParam = case.offset)
            assertEquals("Limit mismatch for '${case.id}'", case.expectedLimit, request.limit)
            assertEquals("Offset mismatch for '${case.id}'", case.expectedOffset, request.offset)
        }
    }

    @Test
    fun `out of range minRating is normalized according to api contract`() {
        for (case in cases.minRating) {
            val request = SearchRequest(rawQuery = null, minRatingParam = case.value)
            assertEquals("minRating mismatch for '${case.id}'", case.expected, request.minRating)
        }
    }

    @Test
    fun `genres use AND semantics and themes map by canonical names`() {
        val request = SearchRequest(rawQuery = null, genresParam = cases.semantics.genresAnd.joinToString(","))
        val query = request.toApicalypseQuery()
        assertTrue("genres = (12) for Role-playing (RPG)", query.contains("genres = (12)"))
        assertTrue("themes = (1) for Action", query.contains("themes = (1)"))
    }

    @Test
    fun `platforms use OR semantics inside a single list`() {
        val request = SearchRequest(rawQuery = null, platformsParam = cases.semantics.platformsOr.joinToString(","))
        val query = request.toApicalypseQuery()
        assertTrue(query.contains("PC (Microsoft Windows)"))
        assertTrue(query.contains("Nintendo Switch"))
        assertTrue(query.contains("platforms.name = ("))
    }

    @Test
    fun `cover policy keeps cover filter for text search`() {
        val request = SearchRequest("witcher")
        assertTrue(request.toApicalypseQuery().contains("cover != null"))
    }

    @Test
    fun `filter-only queries always carry a deterministic sort clause`() {
        val relevance = SearchRequest(rawQuery = null, genresParam = "Role-Playing (RPG)")
            .toApicalypseQuery()
        assertTrue(
            "Default relevance fallback must pin id order for stable offset paging",
            relevance.contains("\nsort id asc;"),
        )
        val rating = SearchRequest(
            rawQuery = null,
            genresParam = "Role-Playing (RPG)",
            sortParam = "rating",
        ).toApicalypseQuery()
        assertTrue(rating.contains("\nsort rating desc;"))
        assertTrue(!rating.contains("sort id asc;"))
    }

    @Test
    fun `text queries stay relevance-ordered without any sort clause`() {
        val query = SearchRequest("witcher").toApicalypseQuery()
        assertTrue(!query.contains("\nsort "))
        val explicitSortOnText = SearchRequest("witcher", sortParam = "rating").toApicalypseQuery()
        assertTrue(!explicitSortOnText.contains("\nsort "))
    }
}

@Serializable
internal data class SearchContractCases(
    val normalization: List<NormalizationCase> = emptyList(),
    val accepted: List<AcceptedCase> = emptyList(),
    val rejected: List<RejectedCase> = emptyList(),
    val pagination: List<PaginationCase> = emptyList(),
    val minRating: List<MinRatingCase> = emptyList(),
    val semantics: SemanticsCase = SemanticsCase(),
)

@Serializable
internal data class NormalizationCase(val id: String, val input: String, val expected: String)

@Serializable
internal data class AcceptedCase(val id: String, val input: String)

@Serializable
internal data class RejectedCase(val id: String, val input: String)

@Serializable
internal data class PaginationCase(
    val id: String,
    val limit: Int,
    val offset: Int,
    val expectedLimit: Int,
    val expectedOffset: Int,
)

@Serializable
internal data class MinRatingCase(val id: String, val value: Int, val expected: Int)

@Serializable
internal data class SemanticsCase(
    val genresAnd: List<String> = emptyList(),
    val platformsOr: List<String> = emptyList(),
    val coverPolicy: String = "",
)
