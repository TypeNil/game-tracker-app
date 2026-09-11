package io.github.typenil.gametracker.devtools

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.typenil.gametracker.MainActivity
import javax.inject.Inject

/**
 * Relaunches the app after a full reset.
 *
 * Clearing the database is not enough on its own: `DiscoverRailLoader` keeps its page offsets and
 * `endReached` flags in memory, the For You feed holds game ids that no longer exist, and
 * `GameDetailsPreviewCache` is process-lifetime. Those holders all describe the pre-reset database,
 * so the rails stay empty and never refetch (`loadMoreRail` returns early on a stale `endReached`)
 * until something rebuilds them. Relaunching the task is the only complete cure, and it is what
 * "everything" promises: the app comes back exactly as after a fresh install.
 *
 * An interface because the decision belongs to the ViewModel: the repository only reports what it
 * reset, and re-launching the UI is observable behaviour worth a JVM test.
 */
interface DevAppRestarter {
    fun restart()
}

class IntentDevAppRestarter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DevAppRestarter {

    override fun restart() {
        // CLEAR_TASK finishes the old UI (this screen included), so no ViewModel that cached the
        // reset data survives; NEW_TASK is required alongside it. The process is deliberately kept
        // alive: a fresh task is enough, and killing the app would take the logs with it.
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }
}
