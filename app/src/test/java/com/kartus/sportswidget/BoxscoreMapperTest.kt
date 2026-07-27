package com.kartus.sportswidget

import com.kartus.sportswidget.data.BoxscoreResponse
import com.kartus.sportswidget.data.StatsApiMapper
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Fixture shaped after /api/v1/game/{gamePk}/boxscore.
 *
 * The two things worth pinning down: `players` is a map keyed "ID{personId}" rather
 * than a list, and a position player still carries an empty `pitching` object — so
 * participation has to come from the `batters` / `pitchers` id arrays, not from
 * whether a stats block exists.
 */
private const val BOXSCORE_JSON = """
{
  "teams": {
    "away": {
      "team": { "id": 147, "name": "New York Yankees", "teamName": "Yankees", "abbreviation": "NYY" },
      "players": {
        "ID592450": {
          "person": { "id": 592450, "fullName": "Aaron Judge" },
          "jerseyNumber": "99",
          "position": { "abbreviation": "RF" },
          "battingOrder": "200",
          "stats": {
            "batting": { "atBats": 4, "runs": 2, "hits": 3, "rbi": 4, "baseOnBalls": 1, "strikeOuts": 0, "homeRuns": 2 },
            "pitching": {},
            "fielding": { "assists": 0 }
          },
          "seasonStats": {
            "batting": { "avg": ".322", "homeRuns": 38 },
            "pitching": { "era": "-.--" }
          }
        },
        "ID543037": {
          "person": { "id": 543037, "fullName": "Gerrit Cole" },
          "position": { "abbreviation": "P" },
          "stats": {
            "batting": {},
            "pitching": { "inningsPitched": "6.1", "hits": 4, "runs": 2, "earnedRuns": 2, "baseOnBalls": 1, "strikeOuts": 9, "homeRuns": 1 }
          },
          "seasonStats": {
            "batting": { "avg": ".000" },
            "pitching": { "era": "3.21" }
          }
        },
        "ID650402": {
          "person": { "id": 650402, "fullName": "Oswaldo Cabrera" },
          "position": { "abbreviation": "PH" },
          "battingOrder": "201",
          "stats": {
            "batting": { "atBats": 1, "runs": 0, "hits": 0, "rbi": 0, "baseOnBalls": 0, "strikeOuts": 1, "homeRuns": 0 }
          },
          "seasonStats": { "batting": { "avg": ".247" } }
        },
        "ID999999": {
          "person": { "id": 999999, "fullName": "Did Not Play" },
          "position": { "abbreviation": "1B" }
        }
      },
      "batters": [592450, 650402],
      "pitchers": [543037],
      "bench": [999999]
    },
    "home": {
      "team": { "id": 111, "name": "Boston Red Sox", "teamName": "Red Sox", "abbreviation": "BOS" },
      "players": {
        "ID646240": {
          "person": { "id": 646240, "fullName": "Rafael Devers" },
          "position": { "abbreviation": "3B" },
          "battingOrder": "300",
          "stats": { "batting": { "atBats": 4, "runs": 1, "hits": 1, "rbi": 1, "baseOnBalls": 0, "strikeOuts": 2, "homeRuns": 0 } },
          "seasonStats": { "batting": { "avg": ".281" } }
        }
      },
      "batters": [646240],
      "pitchers": []
    }
  }
}
"""

class BoxscoreMapperTest {

    private val boxscore = requireNotNull(
        StatsApiMapper.toBoxscore(
            json.decodeFromString(BoxscoreResponse.serializer(), BOXSCORE_JSON),
        ),
    )

    @Test
    fun `maps both sides with their teams`() {
        assertEquals("Yankees", boxscore.away.team.shortName)
        assertEquals("Red Sox", boxscore.home.team.shortName)
        assertFalse(boxscore.isEmpty)
    }

    @Test
    fun `only players in the batters array become batting lines`() {
        // "Did Not Play" is present in `players` and on the bench, but never batted.
        val names = boxscore.away.batters.map { it.name }
        assertEquals(listOf("Aaron Judge", "Oswaldo Cabrera"), names)
    }

    @Test
    fun `batting line carries the game stats and the season average`() {
        val judge = boxscore.away.batters.first()
        assertEquals("RF", judge.position)
        assertEquals(4, judge.atBats)
        assertEquals(2, judge.runs)
        assertEquals(3, judge.hits)
        assertEquals(4, judge.rbi)
        assertEquals(1, judge.walks)
        assertEquals(0, judge.strikeouts)
        assertEquals(2, judge.homeRuns)
        // avg is only in seasonStats — the game line has no such field.
        assertEquals(".322", judge.seasonAvg)
    }

    @Test
    fun `battingOrder distinguishes a starter from a substitute`() {
        val (judge, cabrera) = boxscore.away.batters
        assertFalse("200 is a starting spot", judge.isSubstitute)
        assertTrue("201 replaced the number-two hitter", cabrera.isSubstitute)
    }

    @Test
    fun `pitching line reads innings as a string and takes era from the season`() {
        val cole = boxscore.away.pitchers.single()
        assertEquals("Gerrit Cole", cole.name)
        // "6.1" is six and one third, not six and a tenth — never parse this as a number.
        assertEquals("6.1", cole.inningsPitched)
        assertEquals(4, cole.hits)
        assertEquals(2, cole.earnedRuns)
        assertEquals(9, cole.strikeouts)
        assertEquals("3.21", cole.seasonEra)
    }

    @Test
    fun `a position player's empty pitching object never becomes a pitching line`() {
        assertTrue(boxscore.away.pitchers.none { it.name == "Aaron Judge" })
        assertTrue(boxscore.home.pitchers.isEmpty())
    }

    @Test
    fun `missing stats degrade to zeroes rather than failing the parse`() {
        val sparse = """{"teams":{
            "away":{"team":{"id":1,"name":"A Team"},"batters":[5],"players":{"ID5":{"person":{"id":5,"fullName":"No Stats"}}}},
            "home":{"team":{"id":2,"name":"B Team"},"batters":[],"players":{}}
        }}"""
        val parsed = requireNotNull(
            StatsApiMapper.toBoxscore(json.decodeFromString(BoxscoreResponse.serializer(), sparse)),
        )
        val line = parsed.away.batters.single()
        assertEquals("No Stats", line.name)
        assertEquals("-", line.position)
        assertEquals(0, line.atBats)
        assertNull(line.seasonAvg)
        assertFalse(line.isSubstitute)
    }

    @Test
    fun `a payload with no teams yields no boxscore`() {
        val empty = StatsApiMapper.toBoxscore(
            json.decodeFromString(BoxscoreResponse.serializer(), """{}"""),
        )
        assertNull(empty)
    }
}
