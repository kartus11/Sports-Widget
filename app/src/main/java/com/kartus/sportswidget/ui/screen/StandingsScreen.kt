package com.kartus.sportswidget.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kartus.sportswidget.domain.DivisionStandings
import com.kartus.sportswidget.domain.StandingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsScreen(
    standings: List<DivisionStandings>,
    onLoad: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Standings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (standings.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            standings.forEach { division ->
                item(key = division.divisionId) {
                    DivisionCard(division)
                }
            }
        }
    }
}

@Composable
private fun DivisionCard(division: DivisionStandings) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = division.divisionName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            HeaderRow()
            division.rows.forEach { TeamRow(it) }
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // Keeps the header cells over their columns now that rows start with a logo.
        Spacer(Modifier.width(28.dp))
        Text("", modifier = Modifier.weight(1f))
        StatCell("W", header = true)
        StatCell("L", header = true)
        StatCell("PCT", header = true, width = 48)
        StatCell("GB", header = true)
        StatCell("STRK", header = true, width = 44)
    }
}

@Composable
private fun TeamRow(row: StandingsRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamLogo(teamId = row.team.id, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = row.team.shortName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        StatCell(row.wins.toString())
        StatCell(row.losses.toString())
        StatCell(row.winningPercentage, width = 48)
        StatCell(row.gamesBack)
        StatCell(row.streak ?: "-", width = 44)
    }
}

@Composable
private fun StatCell(text: String, header: Boolean = false, width: Int = 32) {
    Text(
        text = text,
        style = if (header) {
            MaterialTheme.typography.labelSmall
        } else {
            MaterialTheme.typography.bodyMedium
        },
        color = if (header) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        textAlign = TextAlign.End,
        modifier = Modifier.width(width.dp),
    )
}
