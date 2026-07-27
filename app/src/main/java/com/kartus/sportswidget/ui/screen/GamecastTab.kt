package com.kartus.sportswidget.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kartus.sportswidget.domain.Game
import com.kartus.sportswidget.domain.GameSituation
import com.kartus.sportswidget.domain.Gamecast
import com.kartus.sportswidget.domain.Linescore
import com.kartus.sportswidget.domain.ScoringPlay

/**
 * Live view of one game: inning grid, what is happening right now, and how the
 * runs got there.
 *
 * The situation panel only exists while a game is in progress. Once it is over
 * there is nothing to describe — an empty diamond and a 0-0 count would be worse
 * than the panel's absence — so the screen becomes grid plus scoring plays.
 */
@Composable
fun GamecastTab(
    game: Game,
    gamecast: Gamecast?,
    loading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    if (gamecast == null) {
        Box(modifier.fillMaxSize(), Alignment.Center) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = error ?: "Nothing to show for this game yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "linescore") {
            LinescoreGrid(game = game, linescore = gamecast.linescore)
            HorizontalDivider()
        }

        gamecast.situation?.let { situation ->
            item(key = "situation") {
                SituationPanel(situation)
                HorizontalDivider()
            }
        }

        if (gamecast.scoringPlays.isNotEmpty()) {
            item(key = "plays-label") {
                Text(
                    text = "Scoring plays",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(gamecast.scoringPlays, key = { "${it.inning}-${it.description.hashCode()}" }) {
                ScoringPlayRow(it)
            }
        }
    }
}

// ---------- inning grid ----------

private val INNING_CELL = 26.dp
private val TOTAL_CELL = 30.dp
private val TEAM_CELL = 62.dp

@Composable
private fun LinescoreGrid(game: Game, linescore: Linescore) {
    // Extra innings run past the screen edge, so the grid scrolls on its own
    // rather than squeezing every column narrower.
    val scroll = rememberScrollState()

    // Always draw at least the regulation nine, so a game in the 3rd still shows
    // the shape of what is to come.
    val innings = maxOf(linescore.scheduledInnings, linescore.innings.maxOfOrNull { it.number } ?: 0)
    val played = linescore.innings.associateBy { it.number }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(modifier = Modifier.horizontalScroll(scroll).padding(horizontal = 16.dp)) {
            Column {
                GridCell("", TEAM_CELL, header = true, align = TextAlign.Start)
                GridCell(game.away.abbreviation, TEAM_CELL, bold = true, align = TextAlign.Start)
                GridCell(game.home.abbreviation, TEAM_CELL, bold = true, align = TextAlign.Start)
            }

            (1..innings).forEach { number ->
                val current = linescore.currentInning == number
                Column {
                    GridCell(number.toString(), INNING_CELL, header = true, highlight = current)
                    GridCell(inningText(played[number]?.awayRuns), INNING_CELL, highlight = current)
                    GridCell(inningText(played[number]?.homeRuns), INNING_CELL, highlight = current)
                }
            }

            Spacer(Modifier.width(10.dp))

            listOf(
                "R" to (linescore.awayRuns to linescore.homeRuns),
                "H" to (linescore.awayHits to linescore.homeHits),
                "E" to (linescore.awayErrors to linescore.homeErrors),
            ).forEach { (label, values) ->
                Column {
                    GridCell(label, TOTAL_CELL, header = true)
                    GridCell(values.first?.toString() ?: "-", TOTAL_CELL, bold = true)
                    GridCell(values.second?.toString() ?: "-", TOTAL_CELL, bold = true)
                }
            }
        }
    }
}

/**
 * An inning with no runs recorded shows a dot, not a zero. A zero claims the side
 * batted and failed to score; a dot says nothing has happened there — which is the
 * truth both for innings still to come and for the bottom half a leading home team
 * never has to play.
 */
private fun inningText(runs: Int?): String = runs?.toString() ?: "·"

@Composable
private fun GridCell(
    text: String,
    width: Dp,
    header: Boolean = false,
    bold: Boolean = false,
    highlight: Boolean = false,
    align: TextAlign = TextAlign.Center,
) {
    Box(modifier = Modifier.width(width).height(26.dp), contentAlignment = Alignment.CenterStart) {
        Text(
            text = text,
            style = if (header) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (bold || highlight) FontWeight.Bold else FontWeight.Normal,
            color = when {
                highlight -> MaterialTheme.colorScheme.primary
                header -> MaterialTheme.colorScheme.onSurfaceVariant
                text == "·" -> MaterialTheme.colorScheme.outlineVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = align,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------- live situation ----------

@Composable
private fun SituationPanel(situation: GameSituation) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BaseDiamond(
                onFirst = situation.runnerOnFirst,
                onSecond = situation.runnerOnSecond,
                onThird = situation.runnerOnThird,
                size = 76.dp,
            )
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CountRow("B", situation.balls, of = 3)
                CountRow("S", situation.strikes, of = 2)
                CountRow("O", situation.outs, of = 2, danger = true)
            }
        }

        Column {
            situation.batter?.let {
                MatchupRow(
                    label = "At bat",
                    name = it.name,
                    detail = listOfNotNull(situation.batterSeasonAvg, situation.batterToday)
                        .joinToString(" · ")
                        .ifBlank { null },
                )
            }
            situation.onDeck?.let { MatchupRow(label = "On deck", name = it.name, detail = null) }
            situation.pitcher?.let {
                MatchupRow(
                    label = "Pitching",
                    name = it.name,
                    detail = listOfNotNull(
                        situation.pitchCount?.let { count -> "$count pitches" },
                        situation.pitcherEra,
                    ).joinToString(" · ").ifBlank { null },
                )
            }
        }
    }
}

@Composable
private fun CountRow(label: String, filled: Int, of: Int, danger: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp),
        )
        repeat(of) { index ->
            val on = index < filled
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            on && danger -> MaterialTheme.colorScheme.error
                            on -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

/**
 * Bases as they sit on a field: second at the top, first to the right, third to
 * the left. Occupied bases are filled, so the diamond reads at a glance without
 * a legend.
 */
@Composable
private fun BaseDiamond(onFirst: Boolean, onSecond: Boolean, onThird: Boolean, size: Dp) {
    val filled = MaterialTheme.colorScheme.onSurface
    val empty = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = Modifier.size(size)) {
        val edge = this.size.minDimension
        val half = edge * 0.135f

        fun base(cx: Float, cy: Float, occupied: Boolean) {
            val topLeft = Offset(cx - half, cy - half)
            val square = Size(half * 2, half * 2)
            rotate(degrees = 45f, pivot = Offset(cx, cy)) {
                if (occupied) {
                    drawRect(color = filled, topLeft = topLeft, size = square)
                } else {
                    drawRect(
                        color = empty,
                        topLeft = topLeft,
                        size = square,
                        style = Stroke(width = edge * 0.035f),
                    )
                }
            }
        }

        base(edge * 0.50f, edge * 0.24f, onSecond)
        base(edge * 0.76f, edge * 0.50f, onFirst)
        base(edge * 0.24f, edge * 0.50f, onThird)
    }
}

@Composable
private fun MatchupRow(label: String, name: String, detail: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f, fill = false),
        )
        detail?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = true),
                textAlign = TextAlign.End,
            )
        }
    }
}

// ---------- scoring plays ----------

@Composable
private fun ScoringPlayRow(play: ScoringPlay) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = play.halfInningLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(40.dp),
        )
        Text(
            text = play.description,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${play.awayScore}–${play.homeScore}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
