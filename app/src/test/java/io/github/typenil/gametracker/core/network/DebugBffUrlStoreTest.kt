package io.github.typenil.gametracker.core.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugBffUrlStoreTest {

    private fun createContext(prefs: SharedPreferences): Context = mockk {
        every { getSharedPreferences(DebugBffUrlStore.PREFS_NAME, Context.MODE_PRIVATE) } returns prefs
    }

    @Test
    fun `toDebugBffOriginOrNull accepts valid origin urls`() {
        assertEquals("http://localhost:8080/", "http://localhost:8080".toDebugBffOriginOrNull())
        assertEquals("http://localhost:8080/", "http://localhost:8080/".toDebugBffOriginOrNull())
        assertEquals("https://api.example.com/", "https://api.example.com".toDebugBffOriginOrNull())
        assertEquals("https://api.example.com/", "https://api.example.com/".toDebugBffOriginOrNull())
        assertEquals("http://10.0.2.2:3000/", "http://10.0.2.2:3000".toDebugBffOriginOrNull())
        assertEquals("http://127.0.0.1:8080/", "http://127.0.0.1:8080/".toDebugBffOriginOrNull())
    }

    @Test
    fun `toDebugBffOriginOrNull rejects invalid origins`() {
        assertNull("http://localhost:8080/api".toDebugBffOriginOrNull())
        assertNull("http://localhost:8080/api/".toDebugBffOriginOrNull())
        assertNull("http://localhost:8080?key=value".toDebugBffOriginOrNull())
        assertNull("http://localhost:8080#fragment".toDebugBffOriginOrNull())
        assertNull("http://user:pass@localhost:8080".toDebugBffOriginOrNull())
        assertNull("ftp://localhost:8080".toDebugBffOriginOrNull())
        assertNull("not-a-valid-url".toDebugBffOriginOrNull())
        assertNull("".toDebugBffOriginOrNull())
        assertNull("   ".toDebugBffOriginOrNull())
    }

    @Test
    fun `invalidPersistedUrl seeds store synchronously with null`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(DebugBffUrlStore.KEY_URL, "http://localhost:8080/subpath").apply()

        val store = DebugBffUrlStore(createContext(prefs))
        assertNull("Store must seed null when persisted URL is not a root origin", store.currentUrl())

        prefs.edit().putString(DebugBffUrlStore.KEY_URL, "corrupt-data").apply()
        val corruptStore = DebugBffUrlStore(createContext(prefs))
        assertNull("Store must seed null when persisted URL is corrupt", corruptStore.currentUrl())
    }

    @Test
    fun `store round-trip persists and clears debug bff url`() {
        val prefs = FakeSharedPreferences()
        val context = createContext(prefs)

        val store = DebugBffUrlStore(context)
        assertNull(store.currentUrl())

        val targetUrlString = "http://192.168.1.100:8080"
        store.setUrl(targetUrlString)

        val expected = "http://192.168.1.100:8080/".toHttpUrl()
        assertEquals(expected, store.currentUrl())
        assertEquals("http://192.168.1.100:8080/", prefs.getString(DebugBffUrlStore.KEY_URL, null))

        // Create new store instance from same prefs to test persistence restoration
        val reloadedStore = DebugBffUrlStore(context)
        assertEquals(expected, reloadedStore.currentUrl())

        // Clear via setUrl(null)
        store.setUrl(null as HttpUrl?)
        assertNull(store.currentUrl())
        assertNull(prefs.getString(DebugBffUrlStore.KEY_URL, null))

        // Ensure cleared state persists
        val clearedStore = DebugBffUrlStore(context)
        assertNull(clearedStore.currentUrl())
    }

    @Suppress("TooManyFunctions")
    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? =
            map[key] as? String ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null

        override fun getInt(key: String?, defValue: Int): Int =
            map[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            map[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            map[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            map[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) temp[key] = value
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) toRemove.add(key)
            }

            override fun clear(): SharedPreferences.Editor = apply { clear = true }

            override fun commit(): Boolean {
                if (clear) map.clear()
                for (key in toRemove) map.remove(key)
                map.putAll(temp)
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
