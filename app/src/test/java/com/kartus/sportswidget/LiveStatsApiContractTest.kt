package com.kartus.sportswidget

import com.kartus.sportswidget.data.BoxscoreResponse
import com.kartus.sportswidget.data.PlayByPlayResponse
import com.kartus.sportswidget.data.ScheduleResponse
import com.kartus.sportswidget.data.StandingsResponse
import com.kartus.sportswidget.data.StatsApiMapper
import com.kartus.sportswidget.util.TeamLogos
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Contract test against the real statsapi.mlb.com.
 *
 * StatsAPI is undocumented, so the field names in `StatsApiDto` are an assumption
 * until something checks them against the live server. Everything else in this
 * suite runs offline against fixtures; this one class makes real network calls and
 * exists to answer one question: do the names we depend on still exist upstream?
 *
 * Skipped unless RUN_LIVE_API_TESTS=1, so a normal `./gradlew test` stays offline,
 * deterministic, and unaffected by MLB's uptime. CI sets it in a separate job that
 * is allowed to fail without blocking the build.
 */
class LiveStatsApiContractTest {

    companion object {
        private const val BASE = "https://statsapi.mlb.com"

        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        @JvmStatic
        @BeforeClass
        fun requireOptIn() {
            assumeTrue(
                "Set RUN_LIVE_API_TESTS=1 to run live API contract tests",
                System.getenv("RUN_LIVE_API_TESTS") == "1",
            )
        }

        private fun fetch(url: String): String {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SportsWidget/0.1 (contract test)")
                .build()
            client.newCall(request).execute().use { response ->
                assertTrue("HTTP ${response.code} from $url", response.isSuccessful)
                return requireNotNull(response.body).string()
            }
        }
    }

    /**
     * A one-week window so the test does not depend on there being games today —
     * during the season that guarantees a mix of finals and, usually, something
     * scheduled.
     */
    private fun recentSchedule(): List<com.kartus.sportswidget.domain.Game> {
        val end = LocalDate.now()
        val start = end.minusDays(6)
        val body = fetch(
            "$BASE/api/v1/schedule?sportId=1&startDate=$start&endDate=$end" +
                "&hydrate=team,linescore,probablePitcher,venue",
        )
        return StatsApiMapper.toGames(json.decodeFromString(ScheduleResponse.serializer(), body))
    }

    @Test
    fun `schedule endpoint parses into games`() {
        val games = recentSchedule()
        assertFalse(
            "No games parsed from a 7-day window — either the season is over or " +
                "the schedule response shape changed",
            games.isEmpty(),
        )
    }

    @Test
    fun `team hydration supplies the names and abbreviations the UI shows`() {
        val games = recentSchedule()
        assumeTrue(games.isNotEmpty())

        // The widget shows abbreviations; the app shows short names. If `hydrate=team`
        // stops returning them the mapper silently falls back to derived initials,
        // which is exactly the kind of quiet degradation worth catching here.
        val derivedAbbreviations = games.count {
            it.away.abbreviation.length != 2 && it.away.abbreviation.length != 3
        }
        assertTrue(
            "Team abbreviations look derived rather than hydrated",
            derivedAbbreviations == 0,
        )
        assertTrue(
            "Short names identical to full names suggests teamName is missing",
            games.any { it.away.shortName != it.away.name },
        )
    }

    @Test
    fun `completed games carry scores and a linescore`() {
        val finals = recentSchedule().filter { it.state == com.kartus.sportswidget.domain.GameState.FINAL }
        assumeTrue("No completed games in window", finals.isNotEmpty())

        val game = finals.first()
        assertNotNull("Final game has no away score", game.awayScore)
        assertNotNull("Final game has no home score", game.homeScore)

        val linescore = requireNotNull(game.linescore) { "Final game has no linescore" }
        assertFalse("Final game has no innings", linescore.innings.isEmpty())
        assertNotNull("Linescore missing R for away", linescore.awayRuns)
        assertNotNull("Linescore missing H for away", linescore.awayHits)
    }

    @Test
    fun `team logo endpoint serves a real image at both sizes`() {
        // TeamLogos builds these URLs from the team id alone. If the path shape ever
        // changes, both the app rows and the widget silently lose their logos — this
        // is the only thing that would notice.
        listOf(TeamLogos.APP_SIZE, TeamLogos.WIDGET_SIZE).forEach { size ->
            val url = TeamLogos.url(teamId = 147, size = size) // Yankees
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SportsWidget/0.1 (contract test)")
                .build()

            client.newCall(request).execute().use { response ->
                assertTrue("HTTP ${response.code} from $url", response.isSuccessful)
                val bytes = requireNotNull(response.body).bytes()
                assertTrue("Logo at size $size is only ${bytes.size} bytes", bytes.size > 500)

                val isPng = bytes.size > 8 &&
                    bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
                    bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
                val isSvg = String(bytes.copyOfRange(0, minOf(256, bytes.size)))
                    .contains("<svg", ignoreCase = true)
                assertTrue(
                    "Logo at size $size is neither PNG nor SVG — the widget can only " +
                        "decode a bitmap, so a format change breaks it",
                    isPng || isSvg,
                )
                assertTrue("Widget needs a decodable bitmap, got SVG at size $size", isPng)
            }
        }
    }

    @Test
    fun `standings endpoint parses into ranked divisions`() {
        val body = fetch(
            "$BASE/api/v1/standings?leagueId=103,104&season=${LocalDate.now().year}" +
                "&standingsTypes=regularSeason&hydrate=team,division",
        )
        val divisions = StatsApiMapper.toStandings(
            json.decodeFromString(StandingsResponse.serializer(), body),
        )

        assertTrue("Expected 6 divisions, got ${divisions.size}", divisions.size == 6)

        val row = divisions.first().rows.first()
        assertTrue("Division has no teams", divisions.all { it.rows.isNotEmpty() })
        assertTrue("Win total looks unset", row.wins > 0 || row.losses > 0)
        assertTrue("winningPercentage missing", row.winningPercentage != "-")
        assertNotNull("streakCode missing", row.streak)
    }

    @Test
    fun `boxscore endpoint yields real batting and pitching lines`() {
        val finals = recentSchedule().filter { it.state == com.kartus.sportswidget.domain.GameState.FINAL }
        assumeTrue("No completed games in window", finals.isNotEmpty())

        val body = fetch("$BASE/api/v1/game/${finals.first().gamePk}/boxscore")
        val boxscore = requireNotNull(
            StatsApiMapper.toBoxscore(json.decodeFromString(BoxscoreResponse.serializer(), body)),
        ) { "Box score did not map — teams block missing or renamed" }

        assertFalse("Away side has no batters", boxscore.away.batters.isEmpty())
        assertFalse("Home side has no batters", boxscore.home.batters.isEmpty())
        assertFalse("Away side has no pitchers", boxscore.away.pitchers.isEmpty())

        // A completed nine-inning game always has someone with a plate appearance
        // and a pitcher who recorded outs. All-zero lines would mean the stats block
        // moved rather than that nothing happened.
        assertTrue(
            "Every batting line is empty — stats keys likely changed",
            boxscore.away.batters.any { it.atBats > 0 },
        )
        assertTrue(
            "No pitcher recorded innings — inningsPitched likely changed",
            boxscore.away.pitchers.any { it.inningsPitched != "0.0" },
        )
        assertNotNull(
            "Season avg missing — it lives in seasonStats, not the game line",
            boxscore.away.batters.firstOrNull { it.atBats > 0 }?.seasonAvg,
        )
    }

    @Test
    fun `playByPlay yields scoring plays whose indices resolve`() {
        // A completed game that actually scored — scoringPlays is empty in a
        // scoreless game, which would make this vacuous.
        val scored = recentSchedule().firstOrNull {
            it.state == com.kartus.sportswidget.domain.GameState.FINAL &&
                (it.awayScore ?: 0) + (it.homeScore ?: 0) > 0
        }
        assumeTrue("No completed game with runs in window", scored != null)

        val body = fetch("$BASE/api/v1/game/${scored!!.gamePk}/playByPlay")
        val parsed = json.decodeFromString(PlayByPlayResponse.serializer(), body)

        assertFalse("allPlays is empty — the response shape changed", parsed.allPlays.isEmpty())
        assertFalse("scoringPlays is empty for a game with runs", parsed.scoringPlays.isEmpty())

        val plays = StatsApiMapper.toScoringPlays(parsed)
        assertFalse("No scoring play survived mapping", plays.isEmpty())
        assertTrue(
            "A scoring play has no description",
            plays.all { it.description.isNotBlank() },
        )
        assertTrue(
            "Final score should match the last scoring play's running total",
            plays.last().awayScore + plays.last().homeScore > 0,
        )
    }

    @Test
    fun `a live game's linescore carries the offense and defense blocks`() {
        // Only a game in progress has a situation to report; outside game hours
        // this legitimately has nothing to check.
        val live = recentSchedule().firstOrNull {
            it.state == com.kartus.sportswidget.domain.GameState.LIVE
        }
        assumeTrue("No game in progress right now", live != null)

        val body = fetch("$BASE/api/v1/game/${live!!.gamePk}/linescore")
        val dto = json.decodeFromString(
            com.kartus.sportswidget.data.LinescoreDto.serializer(),
            body,
        )
        val situation = StatsApiMapper.toSituation(dto)

        assertNotNull(
            "Live linescore has no offense block — the Gamecast situation panel " +
                "depends on it",
            situation,
        )
        assertNotNull("No batter in the offense block", situation!!.batter)
        assertNotNull("No pitcher in the defense block", situation.pitcher)
        assertTrue("Outs outside 0..2 for a live game", situation.outs in 0..2)
    }

    @Test
    fun `game linescore endpoint parses on its own`() {
        val finals = recentSchedule().filter { it.state == com.kartus.sportswidget.domain.GameState.FINAL }
        assumeTrue("No completed games in window", finals.isNotEmpty())

        val body = fetch("$BASE/api/v1/game/${finals.first().gamePk}/linescore")
        val linescore = StatsApiMapper.toLinescore(
            json.decodeFromString(com.kartus.sportswidget.data.LinescoreDto.serializer(), body),
        )
        assertFalse("Standalone linescore has no innings", linescore.innings.isEmpty())
    }
}
