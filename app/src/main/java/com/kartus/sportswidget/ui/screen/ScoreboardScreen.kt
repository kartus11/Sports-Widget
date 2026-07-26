package com.kartus.sportswidget.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kartus.sportswidget.domain.Game
import com.kartus.sportswidget.domain.GameState
import com.kartus.sportswidget.ui.ScoreboardViewModel
import com.kartus.sportswidget.util.TimeFormat
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardScreen(
    state: ScoreboardViewModel.UiState,
    onRefresh: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onStartPolling: () -> Unit,
    onStopPolling: () -> Unit,
    onGameClick: (Long) -> Unit,
    onStandingsClick: () -> Unit,
) {
    // Live polling runs only while this screen is actually in front of the user.
    LifecycleResumeEffect(Unit) {
        onStartPolling()
        onPauseOrDispose { onStopPolling() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MLB") },
                actions = {
                    IconButton(onClick = onStandingsClick) {
                        Icon(Icons.Default.Leaderboard, contentDescription = "Standings")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            DateBar(
                date = state.date,
                onPrevious = onPreviousDay,
                onNext = onNextDay,
                onToday = onToday,
            )

            when {
                state.loading && state.games.isEmpty() -> CenteredMessage { CircularProgressIndicator() }

                state.games.isEmpty() && state.errorMessage != null -> CenteredMessage {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = onRefresh) { Text("Retry") }
                    }
                }

                state.games.isEmpty() -> CenteredMessage {
                    Text("No games scheduled", style = MaterialTheme.typography.bodyLarge)
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.games, key = { it.gamePk }) { game ->
                        GameCard(game = game, onClick = { onGameClick(game.gamePk) })
                    }

                    item {
                        FooterStatus(state = state)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateBar(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TextButton(onClick = onToday) {
                Text(
                    TimeFormat.dateHeader(date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
        }
    }
}

@Composable
private fun GameCard(game: Game, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TeamLine(
                    name = game.away.shortName,
                    record = game.away.record,
                    score = game.awayScore,
                    // Bold the leader so a glance reads the result without parsing numbers.
                    leading = game.awayScore != null && game.homeScore != null &&
                        game.awayScore > game.homeScore,
                    showScore = game.state != GameState.PREVIEW,
                )
                TeamLine(
                    name = game.home.shortName,
                    record = game.home.record,
                    score = game.homeScore,
                    leading = game.awayScore != null && game.homeScore != null &&
                        game.homeScore > game.awayScore,
                    showScore = game.state != GameState.PREVIEW,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                if (game.state.isLive) {
                    LiveDot()
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = game.compactStatus { TimeFormat.clock(it) },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (game.state.isLive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                game.linescore?.let { ls ->
                    if (game.state.isLive && ls.outs != null) {
                        Text(
                            "${ls.outs} out",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamLine(
    name: String,
    record: String?,
    score: Int?,
    leading: Boolean,
    showScore: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (leading) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (record != null) {
            Text(
                text = record,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (showScore) {
            Text(
                text = score?.toString() ?: "-",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (leading) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun LiveDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
    )
}

@Composable
private fun FooterStatus(state: ScoreboardViewModel.UiState) {
    val text = buildString {
        state.lastUpdatedMillis?.let { append("Updated ${TimeFormat.relativeAge(it)}") }
        if (state.hasLiveGame && state.isToday) {
            if (isNotEmpty()) append(" · ")
            append("auto-refreshing every ${ScoreboardViewModel.POLL_INTERVAL_MILLIS / 1000}s")
        }
        // A stale-but-visible board is better than an empty one; say so rather than
        // letting the user trust numbers that failed to refresh.
        state.errorMessage?.let {
            if (isNotEmpty()) append(" · ")
            append("last refresh failed")
        }
    }

    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
