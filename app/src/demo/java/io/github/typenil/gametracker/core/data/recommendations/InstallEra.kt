package io.github.typenil.gametracker.core.data.recommendations

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Tells a first-ever install apart from an in-place update.
 *
 * The demo flavor seeds a starter library on first run. Without this distinction "marker absent and
 * library empty" is ambiguous: it is also the state of an installation that predates the marker and
 * whose user deliberately emptied the library. Re-seeding that library would undo the user's choice,
 * so an updated install is never seeded, even when its library is empty.
 *
 * Known edge: installing without ever launching, then reinstalling before the first launch, also
 * counts as an update, so that install starts with an empty library. It is a development-only
 * sequence and the developer tools can seed the library on demand.
 */
interface InstallEra {
    /** True only when this package was installed fresh, never updated in place. */
    fun isFreshInstall(): Boolean
}

class PackageManagerInstallEra @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InstallEra {

    override fun isFreshInstall(): Boolean {
        // An unresolvable own package is impossible; treating it as "not fresh" keeps the failure
        // mode on the safe side (never repopulate a library the user emptied).
        val info = packageInfoOrNull() ?: return false
        return info.lastUpdateTime <= info.firstInstallTime
    }

    @Suppress("DEPRECATION")
    private fun packageInfoOrNull(): PackageInfo? = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
