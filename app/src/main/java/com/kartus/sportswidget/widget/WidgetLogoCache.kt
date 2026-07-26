package com.kartus.sportswidget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kartus.sportswidget.domain.Scoreboard
import com.kartus.sportswidget.util.TeamLogos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Team logos for the widget.
 *
 * Coil drives the logos in the app, but Glance cannot use it: a widget renders to
 * RemoteViews and needs an actual [Bitmap] at composition time, not a composable
 * that loads one asynchronously. So logos are downloaded once, kept as files, and
 * decoded up front in `provideGlance` before content is provided.
 *
 * Every failure path is silent by design — a logo that will not download simply
 * does not appear, leaving the abbreviation and score untouched.
 */
object WidgetLogoCache {

    private const val DIR = "team-logos"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Downloads anything missing for [scoreboard]'s teams. Safe to call repeatedly. */
    suspend fun prefetch(context: Context, scoreboard: Scoreboard) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }

        teamIds(scoreboard).forEach { teamId ->
            val file = File(dir, "$teamId.png")
            if (file.exists() && file.length() > 0) return@forEach

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
                // A partially written file would decode to garbage on the next read.
                file.delete()
            }
        }
    }

    /** Decodes whatever is already on disk. Missing teams are simply absent. */
    suspend fun load(context: Context, scoreboard: Scoreboard): Map<Int, Bitmap> =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, DIR)
            if (!dir.isDirectory) return@withContext emptyMap()

            teamIds(scoreboard).mapNotNull { teamId ->
                val file = File(dir, "$teamId.png")
                if (!file.exists()) return@mapNotNull null
                BitmapFactory.decodeFile(file.absolutePath)?.let { teamId to it }
            }.toMap()
        }

    private fun teamIds(scoreboard: Scoreboard): Set<Int> =
        scoreboard.games.flatMapTo(mutableSetOf()) { listOf(it.away.id, it.home.id) }
}
