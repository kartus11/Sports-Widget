package com.kartus.sportswidget.domain

import kotlinx.serialization.Serializable

/**
 * Domain models. These are what the UI and the widget consume; nothing above the
 * data layer should ever touch a raw StatsAPI DTO.
 *
 * They are [Serializable] because the widget persists a snapshot of the current
 * scoreboard into its Glance state, which is a string-keyed preference store.
 */

enum class GameState {
    /** Scheduled but not started. */
    PREVIEW,

    /** Underway, including delays and warmups. */
    LIVE,

    /** Completed. */
    FINAL,

    /** Postponed, suspended, cancelled — anything that will not produce a result today. */
    OFF;

    val isLive: Boolean get() = this == LIVE
}

@Serializable
data class Team(
    val id: Int,
    /** Full name, e.g. "New York Yankees". */
    val name: String,
    /** Short name, e.g. "Yankees". Falls back to [name] when the API omits it. */
    val shortName: String,
    /** Three-letter code, e.g. "NYY". Falls back to derived initials. */
    val abbreviation: String,
    val wins: Int? = null,
    val losses: Int? = null,
) {
    val record: String? get() = if (wins != null && losses != null) "$wins-$losses" else null
}

@Serializable
data class InningLine(
    val number: Int,
    val ordinal: String,
    val awayRuns: Int?,
    val homeRuns: Int?,
)

@Serializable
data class Linescore(
    val innings: List<InningLine> = emptyList(),
    val scheduledInnings: Int = 9,
    val awayRuns: Int? = null,
    val awayHits: Int? = null,
    val awayErrors: Int? = null,
    val homeRuns: Int? = null,
    val homeHits: Int? = null,
    val homeErrors: Int? = null,
    val currentInning: Int? = null,
    val currentInningOrdinal: String? = null,
    /** "Top", "Middle", "Bottom", "End". */
    val inningState: String? = null,
    val balls: Int? = null,
    val strikes: Int? = null,
    val outs: Int? = null,
)

@Serializable
data class Game(
    val gamePk: Long,
    /** Epoch millis of first pitch, UTC. */
    val startTimeUtcMillis: Long?,
    val state: GameState,
    /** Human-readable status straight from the API, e.g. "In Progress", "Postponed". */
    val detailedState: String,
    val away: Team,
    val home: Team,
    val awayScore: Int?,
    val homeScore: Int?,
    val venue: String? = null,
    val linescore: Linescore? = null,
    val awayProbablePitcher: String? = null,
    val homeProbablePitcher: String? = null,
    val seriesDescription: String? = null,
) {
    /**
     * One-line status for compact surfaces (widget rows, list items):
     * "7:10 PM" before the game, "Top 5th" during, "Final" / "Final/10" after.
     */
    fun compactStatus(formatClock: (Long) -> String): String = when (state) {
        GameState.PREVIEW -> startTimeUtcMillis?.let(formatClock) ?: detailedState
        GameState.LIVE -> {
            val half = linescore?.inningState?.take(3)
            val ord = linescore?.currentInningOrdinal
            if (half != null && ord != null) "$half $ord" else detailedState
        }
        GameState.FINAL -> {
            val extra = linescore?.currentInning
            if (extra != null && extra > (linescore.scheduledInnings)) "Final/$extra" else "Final"
        }
        GameState.OFF -> detailedState
    }
}

@Serializable
data class StandingsRow(
    val team: Team,
    val wins: Int,
    val losses: Int,
    val winningPercentage: String,
    val gamesBack: String,
    val streak: String?,
    val divisionRank: String?,
    val runsScored: Int? = null,
    val runsAllowed: Int? = null,
)

@Serializable
data class DivisionStandings(
    val divisionId: Int,
    val divisionName: String,
    val rows: List<StandingsRow>,
)

/** A scoreboard for a single calendar day, plus when it was fetched. */
@Serializable
data class Scoreboard(
    /** ISO date, yyyy-MM-dd, in the user's local zone. */
    val date: String,
    val games: List<Game>,
    val fetchedAtMillis: Long,
) {
    val hasLiveGame: Boolean get() = games.any { it.state.isLive }
}
