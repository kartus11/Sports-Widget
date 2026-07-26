package com.kartus.sportswidget.util

/**
 * MLB serves team marks keyed by the same team id the schedule endpoint returns,
 * so no name-to-asset mapping table is needed and a new/relocated franchise works
 * without a code change.
 *
 * Bundling 30 logos as drawables was the alternative; fetching keeps the APK small
 * and, more importantly, keeps them correct when a club rebrands.
 */
object TeamLogos {

    /**
     * [size] is a pixel edge length. The endpoint only serves a fixed set of sizes;
     * 96 covers list rows at any density, 48 is plenty for the widget.
     */
    fun url(teamId: Int, size: Int = 96): String =
        "https://midfield.mlbstatic.com/v1/team/$teamId/spots/$size"

    const val APP_SIZE = 96
    const val WIDGET_SIZE = 48
}
