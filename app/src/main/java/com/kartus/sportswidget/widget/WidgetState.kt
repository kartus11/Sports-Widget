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
    val ERROR_KEY = stringPreferencesKey("last_error")

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

        forEachWidget(context) { prefs ->
            prefs[SCOREBOARD_KEY] = encoded
            prefs.remove(ERROR_KEY)
        }
        redraw(context)
    }

    /**
     * Record a failed refresh. Any previously published scoreboard is left in place —
     * stale scores plus a note beat no scores at all.
     */
    suspend fun publishError(context: Context, message: String?) {
        forEachWidget(context) { prefs ->
            prefs[ERROR_KEY] = message ?: "Couldn't reach MLB"
        }
        redraw(context)
    }

    /** Recompose every placed widget against whatever state it already holds. */
    suspend fun redraw(context: Context) {
        ScoreboardWidget().updateAll(context)
    }

    private suspend fun forEachWidget(
        context: Context,
        edit: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ) {
        GlanceAppWidgetManager(context)
            .getGlanceIds(ScoreboardWidget::class.java)
            .forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs -> edit(prefs) }
            }
    }
}
