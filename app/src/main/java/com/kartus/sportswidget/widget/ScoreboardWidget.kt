package com.kartus.sportswidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartus.sportswidget.domain.Game
import com.kartus.sportswidget.domain.GameState
import com.kartus.sportswidget.domain.Scoreboard
import com.kartus.sportswidget.ui.MainActivity
import com.kartus.sportswidget.util.TimeFormat

/**
 * Home-screen scoreboard.
 *
 * Renders purely from persisted state — see [WidgetState] for why. Tapping a row
 * opens the app (where live polling is fast); tapping the header forces a refresh.
 */
class ScoreboardWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetBody(currentState<Preferences>())
            }
        }
    }

    @Composable
    private fun WidgetBody(prefs: Preferences) {
        val scoreboard = WidgetState.decode(prefs[WidgetState.SCOREBOARD_KEY])

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp),
        ) {
            Header(scoreboard)
            Spacer(GlanceModifier.height(6.dp))

            when {
                scoreboard == null -> Placeholder("Tap ⟳ to load today's games")
                scoreboard.games.isEmpty() -> Placeholder("No games scheduled")
                else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(scoreboard.games, itemId = { it.gamePk }) { game ->
                        GameRow(game)
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(scoreboard: Scoreboard?) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MLB",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )

            // Freshness matters more on a widget than in the app: the user has no
            // other signal that a 15-minute-old number is 15 minutes old.
            scoreboard?.let {
                Text(
                    text = TimeFormat.relativeAge(it.fetchedAtMillis),
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
                Spacer(GlanceModifier.width(8.dp))
            }

            Text(
                text = "⟳",
                style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.primary),
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()),
            )
        }
    }

    @Composable
    private fun GameRow(game: Game) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                TeamLine(game.away.abbreviation, game.awayScore, game.state, leading(game, home = false))
                TeamLine(game.home.abbreviation, game.homeScore, game.state, leading(game, home = true))
            }

            Text(
                text = game.compactStatus { TimeFormat.clock(it) },
                style = TextStyle(
                    fontSize = 11.sp,
                    color = if (game.state.isLive) LIVE_COLOR else GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = if (game.state.isLive) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        }
    }

    @Composable
    private fun TeamLine(abbreviation: String, score: Int?, state: GameState, leading: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = abbreviation,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = if (leading) FontWeight.Bold else FontWeight.Normal,
                ),
                modifier = GlanceModifier.width(44.dp),
            )
            Text(
                text = if (state == GameState.PREVIEW) "" else score?.toString() ?: "-",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = if (leading) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        }
    }

    @Composable
    private fun Placeholder(message: String) {
        Text(
            text = message,
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.padding(top = 8.dp),
        )
    }

    private fun leading(game: Game, home: Boolean): Boolean {
        val away = game.awayScore ?: return false
        val homeScore = game.homeScore ?: return false
        return if (home) homeScore > away else away > homeScore
    }

    private companion object {
        /** Glance has no error role in its colour set; this matches the app's live accent. */
        val LIVE_COLOR = ColorProvider(day = Color(0xFFD7263D), night = Color(0xFFFF8A9A))
    }
}
