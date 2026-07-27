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

    /**
     * Where games in this state belong on a scoreboard. Games in progress are the
     * only ones whose numbers are still changing, so they lead; then what is about
     * to start, then results, then games that will not happen.
     */
    val sortPriority: Int
        get() = when (this) {
            LIVE -> 0
            PREVIEW -> 1
            FINAL -> 2
            OFF -> 3
        }
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

/**
 * Scoreboard ordering: in-progress games first, then by first pitch. Lives here
 * rather than in the repository so the app, the widget and the tests all agree on
 * what "first" means.
 */
val ScoreboardOrder: Comparator<Game> = compareBy(
    { it.state.sortPriority },
    { it.startTimeUtcMillis ?: Long.MAX_VALUE },
    { it.gamePk },
)

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

// ---- Box score ----

@Serializable
data class BatterLine(
    val playerId: Int,
    val name: String,
    /** Fielding position abbreviation, e.g. "RF", "DH". */
    val position: String,
    val atBats: Int,
    val runs: Int,
    val hits: Int,
    val rbi: Int,
    val walks: Int,
    val strikeouts: Int,
    val homeRuns: Int,
    /** Season average, not this game's — the game line does not carry one. */
    val seasonAvg: String?,
    /** True for a substitute, who is conventionally indented under the starter. */
    val isSubstitute: Boolean,
)

@Serializable
data class PitcherLine(
    val playerId: Int,
    val name: String,
    /** StatsAPI reports this as a string ("6.1"), and thirds do not divide. */
    val inningsPitched: String,
    val hits: Int,
    val runs: Int,
    val earnedRuns: Int,
    val walks: Int,
    val strikeouts: Int,
    val homeRuns: Int,
    val seasonEra: String?,
    /** Pitches thrown in this game, which the Gamecast shows for the active arm. */
    val pitchCount: Int? = null,
)

@Serializable
data class TeamBoxscore(
    val team: Team,
    val batters: List<BatterLine>,
    val pitchers: List<PitcherLine>,
) {
    val isEmpty: Boolean get() = batters.isEmpty() && pitchers.isEmpty()
}

@Serializable
data class Boxscore(
    val away: TeamBoxscore,
    val home: TeamBoxscore,
) {
    val isEmpty: Boolean get() = away.isEmpty && home.isEmpty
}

// ---- Gamecast ----

@Serializable
data class PlayerRef(val id: Int, val name: String)

/** What is happening right now. Only meaningful while a game is in progress. */
@Serializable
data class GameSituation(
    val balls: Int,
    val strikes: Int,
    val outs: Int,
    val batter: PlayerRef? = null,
    val onDeck: PlayerRef? = null,
    val pitcher: PlayerRef? = null,
    val runnerOnFirst: Boolean = false,
    val runnerOnSecond: Boolean = false,
    val runnerOnThird: Boolean = false,
    /** The pitcher's total for the game, from the box score. */
    val pitchCount: Int? = null,
    val pitcherEra: String? = null,
    val batterSeasonAvg: String? = null,
    /** Today's line for the batter, e.g. "1-for-2". */
    val batterToday: String? = null,
)

@Serializable
data class ScoringPlay(
    val inning: Int,
    val isTopInning: Boolean,
    val description: String,
    /** Score after the play. */
    val awayScore: Int,
    val homeScore: Int,
) {
    /** "Top 5" / "Bot 5", matching how the scoreboard labels a half-inning. */
    val halfInningLabel: String get() = "${if (isTopInning) "Top" else "Bot"} $inning"
}

@Serializable
data class Gamecast(
    val linescore: Linescore,
    /** Null for a game that has not started, or has finished. */
    val situation: GameSituation? = null,
    val scoringPlays: List<ScoringPlay> = emptyList(),
)
