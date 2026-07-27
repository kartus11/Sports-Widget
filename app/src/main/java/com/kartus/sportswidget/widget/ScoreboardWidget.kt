package com.kartus.sportswidget.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.kartus.sportswidget.domain.Game
import com.kartus.sportswidget.domain.GameState
import com.kartus.sportswidget.domain.Scoreboard
import com.kartus.sportswidget.domain.Team
import com.kartus.sportswidget.ui.MainActivity
import com.kartus.sportswidget.util.TimeFormat

/**
 * Home-screen scoreboard.
 *
 * Everything it needs is resolved before composition: the scoreboard snapshot is
 * read out of widget state and the logos are decoded from disk, so the composable
 * itself is pure and synchronous. See [WidgetState] and [WidgetLogoCache] for why
 * a widget cannot fetch either at draw time.
 */
class ScoreboardWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val scoreboard = WidgetState.decode(prefs[WidgetState.SCOREBOARD_KEY])
        val error = prefs[WidgetState.ERROR_KEY]
        val logos = scoreboard?.let { WidgetLogoCache.load(context, it) }.orEmpty()

        provideContent {
            GlanceTheme {
                WidgetBody(scoreboard, logos, error)
            }
        }
    }

    @Composable
    private fun WidgetBody(scoreboard: Scoreboard?, logos: Map<Int, Bitmap>, error: String?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Header(scoreboard, error)
            Spacer(GlanceModifier.height(2.dp))

            when {
                // An error with no scoreboard is the only case where the widget has
                // nothing useful to show; with one, the scores stay and the header
                // carries the warning.
                scoreboard == null && error != null -> Placeholder(error)
                scoreboard == null -> Placeholder("Loading today's games…")
                scoreboard.games.isEmpty() -> Placeholder("No games scheduled")
                else -> {
                    // Three games per row. One game per row left most of the width
                    // empty between the score and the status, and fit far fewer
                    // games than the space allowed.
                    val rows = scoreboard.games.chunked(COLUMNS)
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(
                            count = rows.size,
                            itemId = { index -> rows[index].first().gamePk },
                        ) { index ->
                            GameGridRow(rows[index], logos)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(scoreboard: Scoreboard?, error: String?) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MLB",
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
            )

            // Freshness matters more on a widget than in the app: the user has no
            // other signal that a 15-minute-old number is 15 minutes old.
            if (scoreboard != null) {
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = if (error != null) {
                        "${TimeFormat.relativeAge(scoreboard.fetchedAtMillis)} · stale"
                    } else {
                        TimeFormat.relativeAge(scoreboard.fetchedAtMillis)
                    },
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = if (error != null) {
                            GlanceTheme.colors.error
                        } else {
                            GlanceTheme.colors.onSurfaceVariant
                        },
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
            } else {
                Spacer(GlanceModifier.defaultWeight())
            }

            Text(
                text = "⟳",
                style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.primary),
                modifier = GlanceModifier
                    .clickable(actionRunCallback<RefreshWidgetAction>())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }

    /** One row of up to [COLUMNS] games, padded so partial rows stay aligned. */
    @Composable
    private fun GameGridRow(games: List<Game>, logos: Map<Int, Bitmap>) {
        Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
            games.forEachIndexed { index, game ->
                if (index > 0) Spacer(GlanceModifier.width(8.dp))
                GameCell(game, logos, GlanceModifier.defaultWeight())
            }

            // A final row of one or two games must not stretch its cells to fill
            // the width, or the columns stop lining up with the rows above.
            repeat(COLUMNS - games.size) {
                Spacer(GlanceModifier.width(8.dp))
                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun GameCell(game: Game, logos: Map<Int, Bitmap>, modifier: GlanceModifier) {
        val context = LocalContext.current

        Column(modifier = modifier.clickable(actionStartActivity(gameIntent(context, game)))) {
            TeamLine(game.away, game.awayScore, game.state, leading(game, home = false), logos)
            Spacer(GlanceModifier.height(2.dp))
            TeamLine(game.home, game.homeScore, game.state, leading(game, home = true), logos)

            // Status sits under the matchup rather than beside it — at a third of
            // the width there is no room for a column of its own.
            Text(
                text = game.compactStatus { TimeFormat.clock(it) },
                style = TextStyle(
                    fontSize = 10.sp,
                    color = if (game.state.isLive) {
                        GlanceTheme.colors.error
                    } else {
                        GlanceTheme.colors.onSurfaceVariant
                    },
                    fontWeight = if (game.state.isLive) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
                modifier = GlanceModifier.padding(top = 0.dp),
            )
        }
    }

    /** One club: logo, abbreviation, score. Sized for a third of the widget width. */
    @Composable
    private fun TeamLine(
        team: Team,
        score: Int?,
        state: GameState,
        leading: Boolean,
        logos: Map<Int, Bitmap>,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val logo = logos[team.id]
            if (logo != null) {
                Image(
                    provider = ImageProvider(logo),
                    contentDescription = null,
                    modifier = GlanceModifier.size(16.dp),
                )
            } else {
                // Hold the column so rows stay aligned when a logo is missing.
                Spacer(GlanceModifier.width(16.dp))
            }

            Spacer(GlanceModifier.width(6.dp))

            Text(
                text = team.abbreviation,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = if (leading) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )

            Text(
                text = if (state == GameState.PREVIEW) "" else score?.toString() ?: "-",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = if (leading) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
                modifier = GlanceModifier.width(22.dp),
            )
        }
    }

    @Composable
    private fun Placeholder(message: String) {
        Text(
            text = message,
            style = TextStyle(fontSize = 15.sp, color = GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.padding(top = 8.dp),
        )
    }

    /**
     * Intent that opens one specific game.
     *
     * The distinct `data` URI matters: Android compares PendingIntents with
     * `Intent.filterEquals`, which ignores extras. Without it every cell would
     * share one PendingIntent and each of them would open whichever game happened
     * to be registered first.
     */
    private fun gameIntent(context: Context, game: Game): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("sportswidget://game/${'$'}{game.gamePk}")
            putExtra(MainActivity.EXTRA_GAME_PK, game.gamePk)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    private companion object {
        /** Games per row. Three fits comfortably at a phone's widget width. */
        const val COLUMNS = 3
    }

    private fun leading(game: Game, home: Boolean): Boolean {
        val away = game.awayScore ?: return false
        val homeScore = game.homeScore ?: return false
        return if (home) homeScore > away else away > homeScore
    }
}
