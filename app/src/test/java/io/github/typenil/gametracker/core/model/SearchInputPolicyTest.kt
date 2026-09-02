package io.github.typenil.gametracker.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies the client search input policy against the shared BFF contract fixture
 * `config/search-contract/search-contract-cases.json`. The same fixture drives
 * `SearchRequestContractTest` on the backend; both sides must agree on the verdicts.
 */
class SearchInputPolicyTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ContractCases(
        val normalization: List<NormalizationCase> = emptyList(),
        val accepted: List<AcceptedCase> = emptyList(),
        val rejected: List<RejectedCase> = emptyList(),
    )

    @Serializable
    private data class NormalizationCase(val id: String, val input: String, val expected: String)

    @Serializable
    private data class AcceptedCase(val id: String, val input: String)

    @Serializable
    private data class RejectedCase(val id: String, val input: String)

    private val cases: ContractCases by lazy {
        val fixture = File("../config/search-contract/search-contract-cases.json")
        require(fixture.exists()) { "Shared contract fixture is missing at ${fixture.absolutePath}" }
        json.decodeFromString<ContractCases>(fixture.readText())
    }

    @Test
    fun `normalization cases produce the same canonical form as the BFF`() {
        for (case in cases.normalization) {
            assertEquals(
                "Canonical mismatch for '${case.id}'",
                case.expected,
                SearchInputPolicy.canonicalize(case.input),
            )
        }
    }

    @Test
    fun `accepted cases validate as valid`() {
        for (case in cases.accepted) {
            assertTrue(
                "Expected valid input '${case.id}'",
                SearchInputPolicy.validate(case.input) is SearchInputValidation.Valid,
            )
        }
    }

    @Test
    fun `rejected cases validate as invalid`() {
        for (case in cases.rejected) {
            assertTrue(
                "Expected rejection for '${case.id}'",
                SearchInputPolicy.validate(case.input) is SearchInputValidation.Invalid,
            )
        }
    }

    @Test
    fun `variation-selector-only input reports invisible format before too long`() {
        val input = "️".repeat(101)

        assertEquals(
            SearchInputValidation.Invalid(SearchInputViolation.INVISIBLE_FORMAT),
            SearchInputPolicy.validate(input),
        )
    }
}
