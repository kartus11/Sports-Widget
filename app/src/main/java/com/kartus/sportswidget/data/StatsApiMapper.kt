package com.kartus.sportswidget.data

import com.kartus.sportswidget.domain.BatterLine
import com.kartus.sportswidget.domain.Boxscore
import com.kartus.sportswidget.domain.DivisionStandings
import com.kartus.sportswidget.domain.Game
import com.kartus.sportswidget.domain.GameState
import com.kartus.sportswidget.domain.InningLine
import com.kartus.sportswidget.domain.Linescore
import com.kartus.sportswidget.domain.PitcherLine
import com.kartus.sportswidget.domain.StandingsRow
import com.kartus.sportswidget.domain.TeamBoxscore
import com.kartus.sportswidget.domain.Team
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * DTO -> domain. The single place where StatsAPI's shape is allowed to matter.
 *
 * Every conversion supplies a fallback, because a game in Preview legitimately has
 * no score and a Postponed game legitimately has no linescore — absent data is the
 * normal case here, not an error.
 */
object StatsApiMapper {

    /** MLB division ids are stable; used when the `division` hydration is missing. */
    private val DIVISION_NAMES = mapOf(
        200 to "AL West",
        201 to "AL East",
        202 to "AL Central",
        203 to "NL West",
        204 to "NL East",
        205 to "NL Central",
    )

    fun toGames(response: ScheduleResponse): List<Game> =
        response.dates.flatMap { it.games }.mapNotNull(::toGame)

    fun toGame(dto: GameDto): Game? {
        val pk = dto.gamePk ?: return null
        val away = toTeam(dto.teams?.away) ?: return null
        val home = toTeam(dto.teams?.home) ?: return null

        return Game(
            gamePk = pk,
            startTimeUtcMillis = parseInstant(dto.gameDate),
            state = toState(dto.status),
            detailedState = dto.status?.detailedState ?: "Scheduled",
            away = away,
            home = home,
            awayScore = dto.teams?.away?.score,
            homeScore = dto.teams?.home?.score,
            venue = dto.venue?.name,
            linescore = dto.linescore?.let(::toLinescore),
            awayProbablePitcher = dto.teams?.away?.probablePitcher?.fullName,
            homeProbablePitcher = dto.teams?.home?.probablePitcher?.fullName,
            seriesDescription = dto.seriesDescription,
        )
    }

    fun toLinescore(dto: LinescoreDto): Linescore = Linescore(
        innings = dto.innings.mapNotNull { inning ->
            val num = inning.num ?: return@mapNotNull null
            InningLine(
                number = num,
                ordinal = inning.ordinalNum ?: ordinalOf(num),
                awayRuns = inning.away?.runs,
                homeRuns = inning.home?.runs,
            )
        },
        scheduledInnings = dto.scheduledInnings ?: 9,
        awayRuns = dto.teams?.away?.runs,
        awayHits = dto.teams?.away?.hits,
        awayErrors = dto.teams?.away?.errors,
        homeRuns = dto.teams?.home?.runs,
        homeHits = dto.teams?.home?.hits,
        homeErrors = dto.teams?.home?.errors,
        currentInning = dto.currentInning,
        currentInningOrdinal = dto.currentInningOrdinal,
        inningState = dto.inningState,
        balls = dto.balls,
        strikes = dto.strikes,
        outs = dto.outs,
    )

    fun toBoxscore(response: BoxscoreResponse): Boxscore? {
        val away = toTeamBoxscore(response.teams?.away) ?: return null
        val home = toTeamBoxscore(response.teams?.home) ?: return null
        return Boxscore(away = away, home = home)
    }

    private fun toTeamBoxscore(dto: BoxscoreTeamDto?): TeamBoxscore? {
        if (dto == null) return null
        val team = toTeam(dto.team) ?: return null

        // `batters` and `pitchers` are already in box-score order, and using them
        // avoids having to infer participation from an empty stats object.
        return TeamBoxscore(
            team = team,
            batters = dto.batters.mapNotNull { id ->
                dto.players["ID$id"]?.let { toBatterLine(id, it) }
            },
            pitchers = dto.pitchers.mapNotNull { id ->
                dto.players["ID$id"]?.let { toPitcherLine(id, it) }
            },
        )
    }

    private fun toBatterLine(playerId: Int, dto: BoxPlayerDto): BatterLine? {
        val name = dto.person?.fullName ?: return null
        val batting = dto.stats?.batting

        return BatterLine(
            playerId = playerId,
            name = name,
            position = dto.position?.abbreviation ?: "-",
            atBats = batting?.atBats ?: 0,
            runs = batting?.runs ?: 0,
            hits = batting?.hits ?: 0,
            rbi = batting?.rbi ?: 0,
            walks = batting?.baseOnBalls ?: 0,
            strikeouts = batting?.strikeOuts ?: 0,
            homeRuns = batting?.homeRuns ?: 0,
            seasonAvg = dto.seasonStats?.batting?.avg,
            // battingOrder is "100" for the leadoff starter, "101" for the first
            // player to replace them, "200" for the number-two hitter, and so on.
            isSubstitute = dto.battingOrder?.let { it.length >= 3 && !it.endsWith("00") } ?: false,
        )
    }

    private fun toPitcherLine(playerId: Int, dto: BoxPlayerDto): PitcherLine? {
        val name = dto.person?.fullName ?: return null
        val pitching = dto.stats?.pitching

        return PitcherLine(
            playerId = playerId,
            name = name,
            inningsPitched = pitching?.inningsPitched ?: "0.0",
            hits = pitching?.hits ?: 0,
            runs = pitching?.runs ?: 0,
            earnedRuns = pitching?.earnedRuns ?: 0,
            walks = pitching?.baseOnBalls ?: 0,
            strikeouts = pitching?.strikeOuts ?: 0,
            homeRuns = pitching?.homeRuns ?: 0,
            seasonEra = dto.seasonStats?.pitching?.era,
        )
    }

    fun toStandings(response: StandingsResponse): List<DivisionStandings> =
        response.records.mapNotNull { record ->
            val divisionId = record.division?.id ?: return@mapNotNull null
            val rows = record.teamRecords.mapNotNull(::toStandingsRow)
            if (rows.isEmpty()) return@mapNotNull null

            DivisionStandings(
                divisionId = divisionId,
                divisionName = record.division.name
                    ?: DIVISION_NAMES[divisionId]
                    ?: "Division $divisionId",
                rows = rows.sortedBy { it.divisionRank?.toIntOrNull() ?: Int.MAX_VALUE },
            )
        }.sortedBy { it.divisionName }

    private fun toStandingsRow(dto: TeamRecordDto): StandingsRow? {
        val team = toTeam(dto.team) ?: return null
        return StandingsRow(
            team = team,
            wins = dto.wins ?: 0,
            losses = dto.losses ?: 0,
            winningPercentage = dto.winningPercentage ?: "-",
            gamesBack = dto.gamesBack ?: "-",
            streak = dto.streak?.code,
            divisionRank = dto.divisionRank,
            runsScored = dto.runsScored,
            runsAllowed = dto.runsAllowed,
        )
    }

    private fun toTeam(side: SideDto?): Team? {
        val team = toTeam(side?.team) ?: return null
        return team.copy(
            wins = side?.leagueRecord?.wins,
            losses = side?.leagueRecord?.losses,
        )
    }

    private fun toTeam(dto: TeamDto?): Team? {
        if (dto == null) return null
        val id = dto.id ?: return null
        val name = dto.name ?: dto.teamName ?: dto.shortName ?: return null
        val short = dto.teamName ?: dto.clubName ?: dto.shortName ?: name
        return Team(
            id = id,
            name = name,
            shortName = short,
            abbreviation = dto.abbreviation ?: abbreviate(short),
        )
    }

    private fun toState(status: StatusDto?): GameState =
        when (status?.abstractGameState) {
            "Live" -> GameState.LIVE
            "Final" -> GameState.FINAL
            "Preview" -> {
                // Postponements stay in the Preview bucket but will never start.
                val detail = status.detailedState.orEmpty()
                if (detail.startsWith("Postponed") ||
                    detail.startsWith("Cancelled") ||
                    detail.startsWith("Suspended")
                ) {
                    GameState.OFF
                } else {
                    GameState.PREVIEW
                }
            }
            else -> GameState.OFF
        }

    private fun parseInstant(iso: String?): Long? = try {
        iso?.let { Instant.parse(it).toEpochMilli() }
    } catch (_: DateTimeParseException) {
        null
    }

    /** "Yankees" -> "NYY" is impossible without a table; fall back to first 3 letters. */
    private fun abbreviate(name: String): String =
        name.filter { it.isLetter() }.take(3).uppercase()

    private fun ordinalOf(n: Int): String {
        val suffix = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }
}
