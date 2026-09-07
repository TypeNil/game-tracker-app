package io.github.typenil.gametracker.core.network

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun String.toDebugBffOriginOrNull(): String? {
    if (isBlank()) return null
    val url = trim().toHttpUrlOrNull() ?: return null
    if (url.scheme != "http" && url.scheme != "https") return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    if (url.query != null || url.fragment != null) return null
    if (url.encodedPath != "/") return null
    return url.newBuilder().query(null).fragment(null).build().toString()
}

internal fun HttpUrl.toDebugBffOriginOrNull(): HttpUrl? {
    if (scheme != "http" && scheme != "https") return null
    if (username.isNotEmpty() || password.isNotEmpty()) return null
    if (query != null || fragment != null) return null
    if (encodedPath != "/") return null
    return newBuilder().query(null).fragment(null).build()
}

@Singleton
class DebugBffUrlStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    companion object {
        const val PREFS_NAME: String = "debug_bff"
        const val KEY_URL: String = "debug_bff_url"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val urlRef: AtomicReference<HttpUrl?>

    init {
        val persisted = prefs.getString(KEY_URL, null)
        val initialUrl = persisted?.toDebugBffOriginOrNull()?.toHttpUrlOrNull()
        urlRef = AtomicReference(initialUrl)
    }

    fun currentUrl(): HttpUrl? = urlRef.get()

    fun setUrl(url: HttpUrl?) {
        val validUrl = url?.toDebugBffOriginOrNull()
        urlRef.set(validUrl)
        if (validUrl != null) {
            prefs.edit().putString(KEY_URL, validUrl.toString()).apply()
        } else {
            prefs.edit().remove(KEY_URL).apply()
        }
    }

    fun setUrl(urlString: String?) {
        if (urlString.isNullOrBlank()) {
            setUrl(null as HttpUrl?)
        } else {
            val origin = urlString.toDebugBffOriginOrNull()
            setUrl(origin?.toHttpUrlOrNull())
        }
    }
}
