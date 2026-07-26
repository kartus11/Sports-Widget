package com.kartus.sportswidget.widget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.kartus.sportswidget.domain.Scoreboard
import kotlinx.serialization.json.Json

/**
 * The widget's own copy of the scoreboard.
 *
 * A widget process can be recreated by the launcher at any time, with no chance to
 * make a network call first, so whatever it should display has to already be on
 * disk. The refresh worker writes a serialized [Scoreboard] into each widget's
 * Glance state; the widget only ever reads it. That keeps rendering synchronous and
 * means a widget that fails to refresh shows the last known scores (labelled with
 * their age) instead of a spinner or an empty box.
 */
object WidgetState {

    val SCOREBOARD_KEY = stringPreferencesKey("scoreboard_json")

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decode(raw: String?): Scoreboard? = raw
        ?.takeIf { it.isNotBlank() }
        ?.let {
            runCatching { json.decodeFromString(Scoreboard.serializer(), it) }.getOrNull()
        }

    /** Push [scoreboard] into every placed instance of the widget and redraw them. */
    suspend fun publish(context: Context, scoreboard: Scoreboard) {
        val encoded = json.encodeToString(Scoreboard.serializer(), scoreboard)
        val manager = GlanceAppWidgetManager(context)

        manager.getGlanceIds(ScoreboardWidget::class.java).forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[SCOREBOARD_KEY] = encoded
            }
        }

        ScoreboardWidget().updateAll(context)
    }
}
