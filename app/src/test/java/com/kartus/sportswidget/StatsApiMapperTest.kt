package com.kartus.sportswidget

import com.kartus.sportswidget.data.ScheduleResponse
import com.kartus.sportswidget.data.StandingsResponse
import com.kartus.sportswidget.data.StatsApiMapper
import com.kartus.sportswidget.domain.GameState
import com.kartus.sportswidget.util.TimeFormat
import kotlinx.serialization.json.Json
import java.time.ZoneId
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Fixture shaped after a real StatsAPI /api/v1/schedule response with
 * hydrate=team,linescore,probablePitcher,venue. Covers the four game states that
 * behave differently: live, scheduled, final-in-extras, and postponed.
 */
private const val SCHEDULE_JSON = """
{
  "copyright": "Copyright 2026 MLB Advanced Media",
  "totalItems": 4,
  "dates": [
    {
      "date": "2026-07-26",
      "totalGames": 4,
      "games": [
        {
          "gamePk": 745801,
          "gameDate": "2026-07-26T23:10:00Z",
          "status": { "abstractGameState": "Live", "detailedState": "In Progress", "statusCode": "I" },
          "teams": {
            "away": {
              "leagueRecord": { "wins": 58, "losses": 44, "pct": ".569" },
              "score": 3,
              "team": { "id": 147, "name": "New York Yankees", "teamName": "Yankees", "abbreviation": "NYY", "clubName": "Yankees" }
            },
            "home": {
              "leagueRecord": { "wins": 61, "losses": 41, "pct": ".598" },
              "score": 5,
              "team": { "id": 111, "name": "Boston Red Sox", "teamName": "Red Sox", "abbreviation": "BOS", "clubName": "Red Sox" }
            }
          },
          "venue": { "id": 3, "name": "Fenway Park" },
          "linescore": {
            "currentInning": 7,
            "currentInningOrdinal": "7th",
            "inningState": "Top",
            "scheduledInnings": 9,
            "innings": [
              { "num": 1, "ordinalNum": "1st", "home": { "runs": 2 }, "away": { "runs": 0 } },
              { "num": 2, "ordinalNum": "2nd", "home": { "runs": 0 }, "away": { "runs": 1 } },
              { "num": 7, "ordinalNum": "7th", "away": { "runs": 0 } }
            ],
            "teams": {
              "home": { "runs": 5, "hits": 9, "errors": 0 },
              "away": { "runs": 3, "hits": 7, "errors": 1 }
            },
            "balls": 2, "strikes": 1, "outs": 1
          },
          "seriesDescription": "Regular Season"
        },
        {
          "gamePk": 745802,
          "gameDate": "2026-07-27T00:40:00Z",
          "status": { "abstractGameState": "Preview", "detailedState": "Scheduled", "statusCode": "S" },
          "teams": {
            "away": {
              "leagueRecord": { "wins": 50, "losses": 52 },
              "team": { "id": 119, "name": "Los Angeles Dodgers", "teamName": "Dodgers", "abbreviation": "LAD" },
              "probablePitcher": { "id": 477132, "fullName": "Clayton Kershaw" }
            },
            "home": {
              "leagueRecord": { "wins": 47, "losses": 55 },
              "team": { "id": 137, "name": "San Francisco Giants", "teamName": "Giants", "abbreviation": "SF" },
              "probablePitcher": { "id": 592332, "fullName": "Logan Webb" }
            }
          },
          "venue": { "id": 2395, "name": "Oracle Park" }
        },
        {
          "gamePk": 745803,
          "gameDate": "2026-07-26T17:05:00Z",
          "status": { "abstractGameState": "Final", "detailedState": "Final", "statusCode": "F" },
          "teams": {
            "away": { "score": 6, "isWinner": true, "team": { "id": 112, "name": "Chicago Cubs", "teamName": "Cubs", "abbreviation": "CHC" } },
            "home": { "score": 4, "isWinner": false, "team": { "id": 158, "name": "Milwaukee Brewers", "teamName": "Brewers", "abbreviation": "MIL" } }
          },
          "linescore": {
            "currentInning": 10,
            "scheduledInnings": 9,
            "teams": { "home": { "runs": 4, "hits": 8, "errors": 1 }, "away": { "runs": 6, "hits": 11, "errors": 0 } }
          }
        },
        {
          "gamePk": 745804,
          "gameDate": "2026-07-26T22:20:00Z",
          "status": { "abstractGameState": "Preview", "detailedState": "Postponed", "statusCode": "DR" },
          "teams": {
            "away": { "team": { "id": 143, "name": "Philadelphia Phillies", "teamName": "Phillies", "abbreviation": "PHI" } },
            "home": { "team": { "id": 121, "name": "New York Mets", "teamName": "Mets", "abbreviation": "NYM" } }
          }
        }
      ]
    }
  ]
}
"""

private const val STANDINGS_JSON = """
{
  "records": [
    {
      "standingsType": "regularSeason",
      "league": { "id": 103 },
      "division": { "id": 201, "name": "American League East" },
      "teamRecords": [
        {
          "team": { "id": 111, "name": "Boston Red Sox", "teamName": "Red Sox", "abbreviation": "BOS" },
          "wins": 61, "losses": 41, "winningPercentage": ".598", "gamesBack": "-",
          "divisionRank": "1", "runsScored": 500, "runsAllowed": 420,
          "streak": { "streakCode": "W3" }
        },
        {
          "team": { "id": 147, "name": "New York Yankees", "teamName": "Yankees", "abbreviation": "NYY" },
          "wins": 58, "losses": 44, "winningPercentage": ".569", "gamesBack": "3.0",
          "divisionRank": "2",
          "streak": { "streakCode": "L1" }
        }
      ]
    }
  ]
}
"""

class StatsApiMapperTest {

    private val games = StatsApiMapper.toGames(
        json.decodeFromString(ScheduleResponse.serializer(), SCHEDULE_JSON),
    )

    @Test
    fun `parses every game in the payload`() {
        assertEquals(4, games.size)
    }

    @Test
    fun `maps live game with score, linescore and count`() {
        val g = games.single { it.gamePk == 745801L }
        assertEquals(GameState.LIVE, g.state)
        assertEquals("Yankees", g.away.shortName)
        assertEquals("NYY", g.away.abbreviation)
        assertEquals(3, g.awayScore)
        assertEquals(5, g.homeScore)
        assertEquals("Fenway Park", g.venue)
        assertEquals(58, g.away.wins)
        assertEquals("58-44", g.away.record)

        val ls = requireNotNull(g.linescore)
        assertEquals(7, ls.currentInning)
        assertEquals("Top", ls.inningState)
        assertEquals(1, ls.outs)
        assertEquals(9, ls.homeHits)
        assertEquals(1, ls.awayErrors)
        assertEquals(3, ls.innings.size)
        // The 7th is in progress: away has batted, home has not.
        val seventh = ls.innings.single { it.number == 7 }
        assertEquals(0, seventh.awayRuns)
        assertNull(seventh.homeRuns)
    }

    @Test
    fun `live game shows half-inning as its compact status`() {
        val g = games.single { it.gamePk == 745801L }
        assertEquals("Top 7th", g.compactStatus { "unused" })
    }

    @Test
    fun `scheduled game has no score but keeps probable pitchers`() {
        val g = games.single { it.gamePk == 745802L }
        assertEquals(GameState.PREVIEW, g.state)
        assertNull(g.awayScore)
        assertEquals("Clayton Kershaw", g.awayProbablePitcher)
        assertEquals("Logan Webb", g.homeProbablePitcher)
        assertEquals("first pitch", g.compactStatus { "first pitch" })
    }

    @Test
    fun `extra-inning final is labelled with the inning it ended in`() {
        val g = games.single { it.gamePk == 745803L }
        assertEquals(GameState.FINAL, g.state)
        assertEquals("Final/10", g.compactStatus { "unused" })
    }

    @Test
    fun `nine-inning final is labelled plain Final`() {
        val g = games.single { it.gamePk == 745801L }.copy(
            state = GameState.FINAL,
            linescore = games.single { it.gamePk == 745801L }.linescore?.copy(currentInning = 9),
        )
        assertEquals("Final", g.compactStatus { "unused" })
    }

    @Test
    fun `postponed game is not treated as upcoming`() {
        val g = games.single { it.gamePk == 745804L }
        assertEquals(GameState.OFF, g.state)
        assertEquals("Postponed", g.compactStatus { "unused" })
    }

    @Test
    fun `game time is parsed as UTC and rendered in the local zone`() {
        val g = games.single { it.gamePk == 745801L }
        val millis = requireNotNull(g.startTimeUtcMillis)
        assertEquals("7:10 PM", TimeFormat.clock(millis, ZoneId.of("America/New_York")))
        assertEquals("4:10 PM", TimeFormat.clock(millis, ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun `unknown fields and missing objects do not break parsing`() {
        val sparse = """{"dates":[{"games":[
            {"gamePk":1,"someBrandNewField":{"a":1},
             "status":{"abstractGameState":"Final","detailedState":"Final"},
             "teams":{"away":{"team":{"id":1,"name":"A Team"}},"home":{"team":{"id":2,"name":"B Team"}}}}
        ]}]}"""
        val parsed = StatsApiMapper.toGames(
            json.decodeFromString(ScheduleResponse.serializer(), sparse),
        )
        assertEquals(1, parsed.size)
        assertNull(parsed[0].linescore)
        assertNull(parsed[0].awayScore)
        // No abbreviation in the payload: derived rather than crashing.
        assertEquals("ATE", parsed[0].away.abbreviation)
    }

    @Test
    fun `a game missing its team block is dropped rather than faked`() {
        val broken = """{"dates":[{"games":[{"gamePk":9,"status":{"abstractGameState":"Final"}}]}]}"""
        val parsed = StatsApiMapper.toGames(
            json.decodeFromString(ScheduleResponse.serializer(), broken),
        )
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `standings map into ranked divisions`() {
        val divisions = StatsApiMapper.toStandings(
            json.decodeFromString(StandingsResponse.serializer(), STANDINGS_JSON),
        )
        assertEquals(1, divisions.size)
        val al = divisions.single()
        assertEquals("American League East", al.divisionName)
        assertEquals(listOf("Red Sox", "Yankees"), al.rows.map { it.team.shortName })
        assertEquals("W3", al.rows[0].streak)
        assertEquals("3.0", al.rows[1].gamesBack)
        assertEquals(61, al.rows[0].wins)
    }

    @Test
    fun `division name falls back to the id table when hydration is absent`() {
        val noName = """{"records":[{"division":{"id":205},"teamRecords":[
            {"team":{"id":112,"name":"Chicago Cubs","teamName":"Cubs"},"wins":1,"losses":2}
        ]}]}"""
        val divisions = StatsApiMapper.toStandings(
            json.decodeFromString(StandingsResponse.serializer(), noName),
        )
        assertEquals("NL Central", divisions.single().divisionName)
    }
}
