package com.kartus.sportswidget.util

import android.content.Context

/**
 * Team marks.
 *
 * These ship with the APK as assets rather than being downloaded. There are thirty
 * of them, they are a few kilobytes each, and they change roughly never — so paying
 * a network round trip for them buys nothing and costs a visibly empty widget on
 * first run. `.github/workflows/build.yml` populates `assets/team-logos` from MLB's
 * endpoint and commits the result, so the fetch happens once, in CI, forever.
 *
 * [url] survives for two callers: that bootstrap step, and the runtime fallback for
 * a team id with no bundled asset (an All-Star roster, or an expansion club added
 * between releases).
 */
object TeamLogos {

    private const val ASSET_DIR = "team-logos"

    fun assetPath(teamId: Int): String = "$ASSET_DIR/$teamId.png"

    /** Coil understands this scheme directly. */
    fun assetUri(teamId: Int): String = "file:///android_asset/${assetPath(teamId)}"

    /**
     * [size] is a pixel edge length; the endpoint serves a fixed set of them.
     * Only used to bootstrap the assets and to backfill an unbundled team.
     */
    fun url(teamId: Int, size: Int = APP_SIZE): String =
        "https://midfield.mlbstatic.com/v1/team/$teamId/spots/$size"

    const val APP_SIZE = 96
    const val WIDGET_SIZE = 48

    /**
     * Which ids actually shipped. Read once and cached: `assets.list` touches the
     * APK's zip directory, which is cheap but not free, and list rows ask often.
     */
    @Volatile
    private var bundledIds: Set<Int>? = null

    fun isBundled(context: Context, teamId: Int): Boolean = bundled(context).contains(teamId)

    private fun bundled(context: Context): Set<Int> =
        bundledIds ?: synchronized(this) {
            bundledIds ?: runCatching {
                context.assets.list(ASSET_DIR)
                    .orEmpty()
                    .mapNotNull { it.removeSuffix(".png").toIntOrNull() }
                    .toSet()
            }.getOrDefault(emptySet()).also { bundledIds = it }
        }

    /** Asset when we have one, network otherwise. Both are valid Coil models. */
    fun model(context: Context, teamId: Int): String =
        if (isBundled(context, teamId)) assetUri(teamId) else url(teamId)
}
