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
    /** Only populated by the standalone linescore endpoint, not by schedule hydration. */
    val offense: LinescoreSideDto? = null,
    val defense: LinescoreSideDto? = null,
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

// ---- Box score ----

@Serializable
data class BoxscoreResponse(
    val teams: BoxscoreTeamsDto? = null,
)

@Serializable
data class BoxscoreTeamsDto(
    val away: BoxscoreTeamDto? = null,
    val home: BoxscoreTeamDto? = null,
)

@Serializable
data class BoxscoreTeamDto(
    val team: TeamDto? = null,
    /**
     * Keyed by "ID{personId}" rather than a plain id, so this stays a String map.
     * [batters] and [pitchers] carry the ids in box-score order and are the
     * authoritative answer to who actually appeared — a position player's entry
     * still contains an empty `pitching` object.
     */
    val players: Map<String, BoxPlayerDto> = emptyMap(),
    val batters: List<Int> = emptyList(),
    val pitchers: List<Int> = emptyList(),
)

@Serializable
data class BoxPlayerDto(
    val person: PersonDto? = null,
    val jerseyNumber: String? = null,
    val position: PositionDto? = null,
    /** This game's line. */
    val stats: BoxStatsDto? = null,
    /** Season totals — where avg and era live; the game line has neither. */
    val seasonStats: BoxStatsDto? = null,
    val battingOrder: String? = null,
)

@Serializable
data class PositionDto(
    val abbreviation: String? = null,
)

@Serializable
data class BoxStatsDto(
    val batting: BattingStatsDto? = null,
    val pitching: PitchingStatsDto? = null,
)

@Serializable
data class BattingStatsDto(
    val atBats: Int? = null,
    val runs: Int? = null,
    val hits: Int? = null,
    val doubles: Int? = null,
    val triples: Int? = null,
    val homeRuns: Int? = null,
    val rbi: Int? = null,
    val baseOnBalls: Int? = null,
    val strikeOuts: Int? = null,
    val avg: String? = null,
)

@Serializable
data class PitchingStatsDto(
    val inningsPitched: String? = null,
    val hits: Int? = null,
    val runs: Int? = null,
    val earnedRuns: Int? = null,
    val homeRuns: Int? = null,
    val baseOnBalls: Int? = null,
    val strikeOuts: Int? = null,
    val era: String? = null,
    val numberOfPitches: Int? = null,
)

// ---- Live situation (linescore offense/defense) ----

/**
 * One side of the current situation.
 *
 * CAREFUL: `first`/`second`/`third` mean different things on each side. Under
 * `offense` they are the runners on those bases — which is what the Gamecast
 * shows. Under `defense` they are the fielders playing those positions. Reading
 * bases off `defense` would report a runner on every base of every game.
 */
@Serializable
data class LinescoreSideDto(
    val batter: PersonDto? = null,
    val onDeck: PersonDto? = null,
    val inHole: PersonDto? = null,
    val pitcher: PersonDto? = null,
    val first: PersonDto? = null,
    val second: PersonDto? = null,
    val third: PersonDto? = null,
)

// ---- Play by play ----

@Serializable
data class PlayByPlayResponse(
    val allPlays: List<PlayDto> = emptyList(),
    /** Indices into [allPlays], not plays themselves. */
    val scoringPlays: List<Int> = emptyList(),
)

@Serializable
data class PlayDto(
    val result: PlayResultDto? = null,
    val about: PlayAboutDto? = null,
)

@Serializable
data class PlayResultDto(
    val event: String? = null,
    val description: String? = null,
    val rbi: Int? = null,
    /** Score *after* the play, which is what a scoring-play list wants to show. */
    val awayScore: Int? = null,
    val homeScore: Int? = null,
)

@Serializable
data class PlayAboutDto(
    val inning: Int? = null,
    val isTopInning: Boolean? = null,
    val halfInning: String? = null,
    val isScoringPlay: Boolean? = null,
)
