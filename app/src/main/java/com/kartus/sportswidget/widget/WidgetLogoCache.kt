package com.kartus.sportswidget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kartus.sportswidget.domain.Scoreboard
import com.kartus.sportswidget.util.TeamLogos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Team logos for the widget, as decoded bitmaps.
 *
 * Glance cannot use Coil: a widget renders to RemoteViews and needs a real [Bitmap]
 * at composition time, so these are resolved in `provideGlance` before content is
 * provided.
 *
 * Almost always this is a straight read out of the APK's assets — no network, no
 * disk cache, nothing that can fail or stall. The download path only covers a team
 * with no bundled asset, and it never blocks the scoreboard from being published:
 * see [WidgetRefreshWorker], which publishes first and backfills after.
 */
object WidgetLogoCache {

    private const val DIR = "team-logos"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Downloads only what did not ship in the APK. Runs its requests concurrently —
     * serially this was thirty round trips, which is what made the first widget
     * refresh appear to hang.
     */
    suspend fun backfill(context: Context, scoreboard: Scoreboard) {
        val missing = teamIds(scoreboard).filterNot { TeamLogos.isBundled(context, it) }
        if (missing.isEmpty()) return

        val dir = File(context.filesDir, DIR).apply { mkdirs() }

        coroutineScope {
            missing.map { teamId ->
                async(Dispatchers.IO) {
                    val file = File(dir, "$teamId.png")
                    if (file.exists() && file.length() > 0) return@async

                    runCatching {
                        val request = Request.Builder()
                            .url(TeamLogos.url(teamId, TeamLogos.WIDGET_SIZE))
                            .header("User-Agent", "SportsWidget/0.1 (personal use; Android)")
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@runCatching
                            response.body?.byteStream()?.use { input ->
                                file.outputStream().use(input::copyTo)
                            }
                        }
                    }.onFailure {
                        // A half-written file would decode to garbage on the next read.
                        file.delete()
                    }
                }
            }.awaitAll()
        }
    }

    /** Assets first, downloaded backfill second, nothing at all if neither exists. */
    suspend fun load(context: Context, scoreboard: Scoreboard): Map<Int, Bitmap> =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, DIR)

            teamIds(scoreboard).mapNotNull { teamId ->
                val bitmap = decodeAsset(context, teamId) ?: decodeFile(dir, teamId)
                bitmap?.let { teamId to it }
            }.toMap()
        }

    private fun decodeAsset(context: Context, teamId: Int): Bitmap? = runCatching {
        context.assets.open(TeamLogos.assetPath(teamId)).use(BitmapFactory::decodeStream)
    }.getOrNull()

    private fun decodeFile(dir: File, teamId: Int): Bitmap? {
        val file = File(dir, "$teamId.png")
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun teamIds(scoreboard: Scoreboard): Set<Int> =
        scoreboard.games.flatMapTo(mutableSetOf()) { listOf(it.away.id, it.home.id) }
}
