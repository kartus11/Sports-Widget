package com.kartus.sportswidget.ui.screen

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kartus.sportswidget.domain.BatterLine
import com.kartus.sportswidget.domain.Boxscore
import com.kartus.sportswidget.domain.PitcherLine
import com.kartus.sportswidget.domain.TeamBoxscore

/**
 * Per-player batting and pitching lines.
 *
 * Stat tables do not fit a phone at any sensible font size, so the name column is
 * fixed and everything to its right scrolls horizontally. Every row shares one
 * scroll state, which keeps the columns aligned with their header while the user
 * drags.
 */
@Composable
fun BoxScoreTab(
    boxscore: Boxscore?,
    loading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val horizontalScroll = rememberScrollState()

    when {
        loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

        boxscore == null || boxscore.isEmpty -> Box(modifier.fillMaxSize(), Alignment.Center) {
            Text(
                text = error ?: "No box score yet — lines appear once the game starts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            listOf(boxscore.away, boxscore.home).forEach { side ->
                item(key = "header-${side.team.id}") { TeamHeader(side) }

                if (side.batters.isNotEmpty()) {
                    item(key = "bat-head-${side.team.id}") {
                        SectionLabel("Batting")
                        StatHeaderRow(BATTING_COLUMNS, horizontalScroll)
                    }
                    items(
                        count = side.batters.size,
                        key = { i -> "bat-${side.team.id}-${side.batters[i].playerId}" },
                    ) { i ->
                        BatterRow(side.batters[i], horizontalScroll)
                    }
                }

                if (side.pitchers.isNotEmpty()) {
                    item(key = "pit-head-${side.team.id}") {
                        SectionLabel("Pitching")
                        StatHeaderRow(PITCHING_COLUMNS, horizontalScroll)
                    }
                    items(
                        count = side.pitchers.size,
                        key = { i -> "pit-${side.team.id}-${side.pitchers[i].playerId}" },
                    ) { i ->
                        PitcherRow(side.pitchers[i], horizontalScroll)
                    }
                }
            }
        }
    }
}

private val BATTING_COLUMNS = listOf("AB", "R", "H", "RBI", "BB", "SO", "HR", "AVG")
private val PITCHING_COLUMNS = listOf("IP", "H", "R", "ER", "BB", "SO", "HR", "ERA")

private val NAME_WIDTH = 132.dp
private val STAT_WIDTH = 34.dp
private val WIDE_STAT_WIDTH = 46.dp

@Composable
private fun TeamHeader(side: TeamBoxscore) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamLogo(teamId = side.team.id, size = 24.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = side.team.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
    HorizontalDivider()
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun StatHeaderRow(
    columns: List<String>,
    scroll: androidx.compose.foundation.ScrollState,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Spacer(Modifier.width(NAME_WIDTH))
        Row(modifier = Modifier.horizontalScroll(scroll)) {
            columns.forEach { StatCellText(it, header = true, wide = it.length > 2) }
        }
    }
}

@Composable
private fun BatterRow(
    batter: BatterLine,
    scroll: androidx.compose.foundation.ScrollState,
) {
    PlayerRow(
        name = batter.name,
        position = batter.position,
        isSubstitute = batter.isSubstitute,
        scroll = scroll,
        values = listOf(
            batter.atBats.toString() to false,
            batter.runs.toString() to false,
            batter.hits.toString() to false,
            batter.rbi.toString() to false,
            batter.walks.toString() to false,
            batter.strikeouts.toString() to false,
            batter.homeRuns.toString() to false,
            (batter.seasonAvg ?: "-") to true,
        ),
    )
}

@Composable
private fun PitcherRow(
    pitcher: PitcherLine,
    scroll: androidx.compose.foundation.ScrollState,
) {
    PlayerRow(
        name = pitcher.name,
        position = null,
        isSubstitute = false,
        scroll = scroll,
        values = listOf(
            pitcher.inningsPitched to false,
            pitcher.hits.toString() to false,
            pitcher.runs.toString() to false,
            pitcher.earnedRuns.toString() to false,
            pitcher.walks.toString() to false,
            pitcher.strikeouts.toString() to false,
            pitcher.homeRuns.toString() to false,
            (pitcher.seasonEra ?: "-") to true,
        ),
    )
}

@Composable
private fun PlayerRow(
    name: String,
    position: String?,
    isSubstitute: Boolean,
    scroll: androidx.compose.foundation.ScrollState,
    values: List<Pair<String, Boolean>>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(NAME_WIDTH),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Substitutes are indented under the starter they replaced, the way a
            // printed box score reads.
            if (isSubstitute) Spacer(Modifier.width(10.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            position?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(modifier = Modifier.horizontalScroll(scroll)) {
            values.forEach { (value, wide) -> StatCellText(value, wide = wide) }
        }
    }
}

@Composable
private fun StatCellText(text: String, header: Boolean = false, wide: Boolean = false) {
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
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(if (wide) WIDE_STAT_WIDTH else STAT_WIDTH),
    )
}
