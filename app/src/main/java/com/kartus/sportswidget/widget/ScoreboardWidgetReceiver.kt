package com.kartus.sportswidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Binds the widget to the launcher and ties the refresh schedule to its lifetime:
 * periodic work starts when the first instance is placed and is cancelled when the
 * last one is removed, so an uninstalled widget leaves nothing running.
 */
class ScoreboardWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ScoreboardWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedulePeriodic(context)
        // First placement has no stored scoreboard yet; fill it immediately rather
        // than showing an empty widget until the first periodic run lands.
        WidgetRefreshWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.cancelPeriodic(context)
    }
}
