package com.kartus.sportswidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Handles the widget's ⟳ tap.
 *
 * Delegates to WorkManager rather than fetching inline: this callback runs on a
 * short-lived broadcast-style scope, and a network call started here can be killed
 * mid-flight. Enqueuing survives that.
 */
class RefreshWidgetAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WidgetRefreshWorker.refreshNow(context)
    }
}
