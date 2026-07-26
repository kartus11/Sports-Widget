package com.kartus.sportswidget.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for MLB's public StatsAPI (https://statsapi.mlb.com).
 *
 * IMPORTANT: every field here is nullable with a default. StatsAPI is undocumented
 * and unversioned in practice — fields appear and disappear between hydrations and
 * between game states (a Preview game has no linescore; a Final game has no count).
 * Combined with `ignoreUnknownKeys`, that means a schema change degrades to a
 * missing value rather than a parse exception that blanks the whole screen.
 *
 * If a field ever comes back empty in the app, this is the only file to correct:
 * hit the endpoint in a browser, compare names, fix here. Nothing above the mapper
 * in [StatsApiMapper] depends on these shapes.
 */

@Serializable
data class ScheduleResponse(
    val dates: List<ScheduleDate> = emptyList(),
)

@Serializable
data class ScheduleDate(
    val date: String? = null,
    val games: List<GameDto> = emptyList(),
)

@Serializable
data class GameDto(
    val gamePk: Long? = null,
    /** ISO-8601 UTC, e.g. "2026-07-26T17:10:00Z". */
    val gameDate: String? = null,
    val status: StatusDto? = null,
    val teams: MatchupDto? = null,
    val venue: NamedDto? = null,
    val linescore: LinescoreDto? = null,
    val seriesDescription: String? = null,
    val doubleHeader: String? = null,
    val gameNumber: Int? = null,
)

@Serializable
data class StatusDto(
    /** "Preview" | "Live" | "Final" | "Other". */
    val abstractGameState: String? = null,
    /** e.g. "Scheduled", "In Progress", "Final", "Postponed". */
    val detailedState: String? = null,
    val statusCode: String? = null,
)

@Serializable
data class MatchupDto(
    val away: SideDto? = null,
    val home: SideDto? = null,
)

@Serializable
data class SideDto(
    val score: Int? = null,
    val team: TeamDto? = null,
    val isWinner: Boolean? = null,
    val leagueRecord: LeagueRecordDto? = null,
    val probablePitcher: PersonDto? = null,
)

@Serializable
data class TeamDto(
    val id: Int? = null,
    /** Full club name, "New York Yankees". */
    val name: String? = null,
    /** Nickname only, "Yankees". Present when hydrated with `team`. */
    val teamName: String? = null,
    /** "NYY". Present when hydrated with `team`. */
    val abbreviation: String? = null,
    val clubName: String? = null,
    val shortName: String? = null,
)

@Serializable
data class LeagueRecordDto(
    val wins: Int? = null,
    val losses: Int? = null,
    val pct: String? = null,
)

@Serializable
data class PersonDto(
    val id: Int? = null,
    val fullName: String? = null,
)

@Serializable
data class NamedDto(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
data class LinescoreDto(
    val currentInning: Int? = null,
    val currentInningOrdinal: String? = null,
    /** "Top" | "Middle" | "Bottom" | "End". */
    val inningState: String? = null,
    val scheduledInnings: Int? = null,
    val innings: List<InningDto> = emptyList(),
    val teams: LinescoreTeamsDto? = null,
    val balls: Int? = null,
    val strikes: Int? = null,
    val outs: Int? = null,
)

@Serializable
data class InningDto(
    val num: Int? = null,
    val ordinalNum: String? = null,
    val home: InningSideDto? = null,
    val away: InningSideDto? = null,
)

@Serializable
data class InningSideDto(
    val runs: Int? = null,
    val hits: Int? = null,
    val errors: Int? = null,
)

@Serializable
data class LinescoreTeamsDto(
    val home: InningSideDto? = null,
    val away: InningSideDto? = null,
)

// ---- Standings ----

@Serializable
data class StandingsResponse(
    val records: List<DivisionRecordDto> = emptyList(),
)

@Serializable
data class DivisionRecordDto(
    val division: NamedDto? = null,
    val league: NamedDto? = null,
    val teamRecords: List<TeamRecordDto> = emptyList(),
)

@Serializable
data class TeamRecordDto(
    val team: TeamDto? = null,
    val wins: Int? = null,
    val losses: Int? = null,
    val winningPercentage: String? = null,
    val gamesBack: String? = null,
    val divisionRank: String? = null,
    val leagueRank: String? = null,
    val runsScored: Int? = null,
    val runsAllowed: Int? = null,
    val streak: StreakDto? = null,
)

@Serializable
data class StreakDto(
    @SerialName("streakCode") val code: String? = null,
)
