package io.github.typenil.gametracker.core.data.recommendations

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * [DemoSeedMarker] over the app's single `DataStore<Preferences>`.
 *
 * A second `PreferenceDataStoreFactory` pointed at the same file would throw `IllegalStateException`,
 * and widening the production `UserPreferences` model with a demo-only field would be worse, so the
 * marker shares the existing store under its own key.
 */
class DataStoreDemoSeedMarker @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DemoSeedMarker {

    override suspend fun isResolved(): Boolean = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .first()[KEY_RESOLVED] ?: false

    override suspend fun markResolved() {
        try {
            dataStore.edit { it[KEY_RESOLVED] = true }
        } catch (_: IOException) {
            // Durability of a demo convenience flag is not worth failing the caller over: the next
            // launch simply re-evaluates, and the seed it may repeat is idempotent.
        }
    }

    private companion object {
        val KEY_RESOLVED = booleanPreferencesKey("demo_library_seeded")
    }
}
