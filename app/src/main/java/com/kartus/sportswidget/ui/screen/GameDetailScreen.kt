package com.kartus.sportswidget.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kartus.sportswidget.domain.Game
import com.kartus.sportswidget.domain.GameState
import com.kartus.sportswidget.domain.Linescore
import com.kartus.sportswidget.domain.Team
import com.kartus.sportswidget.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(game: Game?, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = game?.let { "${it.away.shortName} @ ${it.home.shortName}" } ?: "Game",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (game == null) {
            // Reachable if the process was restored straight onto this route with an
            // empty scoreboard behind it.
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Game not loaded — go back and refresh.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScoreHeader(game)
            game.linescore?.let { LinescoreTable(game, it) }
            GameInfo(game)
        }
    }
}

@Composable
private fun ScoreHeader(game: Game) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = game.compactStatus { TimeFormat.clock(it) },
                style = MaterialTheme.typography.labelLarge,
                color = if (game.state.isLive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(12.dp))

            SideRow(game.away, game.awayScore, game.state)
            Spacer(Modifier.height(12.dp))
            SideRow(game.home, game.homeScore, game.state)

            if (game.state.isLive) {
                val ls = game.linescore
                val count = listOfNotNull(
                    ls?.balls?.let { b -> ls.strikes?.let { s -> "$b-$s" } },
                    ls?.outs?.let { "$it out" },
                ).joinToString(" · ")
                if (count.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(count, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SideRow(team: Team, score: Int?, state: GameState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(teamId = team.id, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(team.name, style = MaterialTheme.typography.titleMedium)
            team.record?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = if (state == GameState.PREVIEW) "-" else score?.toString() ?: "-",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LinescoreTable(game: Game, linescore: Linescore) {
    // Extra innings make this wider than the screen, so the grid scrolls on its own
    // rather than squeezing the columns.
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                Column {
                    LinescoreCell("", header = true, width = 64)
                    LinescoreCell(game.away.abbreviation, width = 64)
                    LinescoreCell(game.home.abbreviation, width = 64)
                }

                linescore.innings.forEach { inning ->
                    Column {
                        LinescoreCell(inning.number.toString(), header = true)
                        LinescoreCell(inning.awayRuns?.toString() ?: "")
                        LinescoreCell(inning.homeRuns?.toString() ?: "")
                    }
                }

                Spacer(Modifier.width(12.dp))

                listOf(
                    "R" to (linescore.awayRuns to linescore.homeRuns),
                    "H" to (linescore.awayHits to linescore.homeHits),
                    "E" to (linescore.awayErrors to linescore.homeErrors),
                ).forEach { (label, values) ->
                    Column {
                        LinescoreCell(label, header = true)
                        LinescoreCell(values.first?.toString() ?: "-", bold = true)
                        LinescoreCell(values.second?.toString() ?: "-", bold = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinescoreCell(
    text: String,
    header: Boolean = false,
    bold: Boolean = false,
    width: Int = 28,
) {
    Box(
        modifier = Modifier.width(width.dp).height(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (header) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (header) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GameInfo(game: Game) {
    val rows = buildList {
        game.startTimeUtcMillis?.let { add("First pitch" to TimeFormat.clock(it)) }
        game.venue?.let { add("Venue" to it) }
        game.seriesDescription?.let { add("Series" to it) }
        if (game.state == GameState.PREVIEW) {
            game.awayProbablePitcher?.let { add("${game.away.shortName} starter" to it) }
            game.homeProbablePitcher?.let { add("${game.home.shortName} starter" to it) }
        }
    }
    if (rows.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
