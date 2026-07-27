package com.kartus.sportswidget

import com.kartus.sportswidget.data.LinescoreDto
import com.kartus.sportswidget.data.PlayByPlayResponse
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
 * Shaped after /api/v1/game/{gamePk}/linescore during a live at-bat.
 *
 * The trap this fixture exists to catch: `offense` and `defense` both carry
 * `first`, `second` and `third`. On offense they are runners; on defense they are
 * the fielders at those positions. Here the defense has all three filled — as it
 * always does — while only first and second are actually occupied.
 */
private const val LIVE_LINESCORE = """
{
  "currentInning": 5,
  "currentInningOrdinal": "5th",
  "inningState": "Bottom",
  "scheduledInnings": 9,
  "balls": 2,
  "strikes": 1,
  "outs": 1,
  "innings": [
    { "num": 1, "ordinalNum": "1st", "home": { "runs": 1 }, "away": { "runs": 0 } },
    { "num": 5, "ordinalNum": "5th", "home": { "runs": 2 }, "away": { "runs": 0 } }
  ],
  "teams": {
    "home": { "runs": 6, "hits": 9, "errors": 0 },
    "away": { "runs": 0, "hits": 4, "errors": 1 }
  },
  "offense": {
    "batter":  { "id": 547180, "fullName": "Bryce Harper" },
    "onDeck":  { "id": 592206, "fullName": "Nick Castellanos" },
    "inHole":  { "id": 592663, "fullName": "J.T. Realmuto" },
    "first":   { "id": 656941, "fullName": "Kyle Schwarber" },
    "second":  { "id": 607208, "fullName": "Trea Turner" }
  },
  "defense": {
    "pitcher": { "id": 571578, "fullName": "Will Warren" },
    "catcher": { "id": 596142, "fullName": "Austin Wells" },
    "first":   { "id": 502671, "fullName": "Paul Goldschmidt" },
    "second":  { "id": 665487, "fullName": "Jose Caballero" },
    "third":   { "id": 642708, "fullName": "Amed Rosario" }
  }
}
"""

private const val PLAY_BY_PLAY = """
{
  "allPlays": [
    { "result": { "event": "Strikeout", "description": "Trea Turner strikes out swinging.",
                  "rbi": 0, "awayScore": 0, "homeScore": 0 },
      "about": { "inning": 1, "isTopInning": false, "halfInning": "bottom", "isScoringPlay": false } },
    { "result": { "event": "Home Run", "description": "Kyle Schwarber homers (32) on a fly ball to right center field.",
                  "rbi": 1, "awayScore": 0, "homeScore": 1 },
      "about": { "inning": 1, "isTopInning": false, "halfInning": "bottom", "isScoringPlay": true } },
    { "result": { "event": "Flyout", "description": "Aaron Judge flies out to center.",
                  "rbi": 0, "awayScore": 0, "homeScore": 1 },
      "about": { "inning": 2, "isTopInning": true, "halfInning": "top", "isScoringPlay": false } },
    { "result": { "event": "Double", "description": "Trea Turner doubles (18) on a line drive to left field. Bryce Harper scores.",
                  "rbi": 1, "awayScore": 0, "homeScore": 3 },
      "about": { "inning": 3, "isTopInning": false, "halfInning": "bottom", "isScoringPlay": true } }
  ],
  "scoringPlays": [1, 3]
}
"""

class GamecastMapperTest {

    private val linescore = json.decodeFromString(LinescoreDto.serializer(), LIVE_LINESCORE)

    @Test
    fun `situation reads the count and outs`() {
        val situation = requireNotNull(StatsApiMapper.toSituation(linescore))
        assertEquals(2, situation.balls)
        assertEquals(1, situation.strikes)
        assertEquals(1, situation.outs)
    }

    @Test
    fun `situation names the batter, on-deck hitter and pitcher`() {
        val situation = requireNotNull(StatsApiMapper.toSituation(linescore))
        assertEquals("Bryce Harper", situation.batter?.name)
        assertEquals("Nick Castellanos", situation.onDeck?.name)
        // The pitcher is on the defensive side, not the offensive one.
        assertEquals("Will Warren", situation.pitcher?.name)
    }

    @Test
    fun `runners come from offense, never from the defensive fielders`() {
        val situation = requireNotNull(StatsApiMapper.toSituation(linescore))
        assertTrue("Schwarber is on first", situation.runnerOnFirst)
        assertTrue("Turner is on second", situation.runnerOnSecond)
        // defense.third is Amed Rosario playing third base — not a runner. Reading
        // bases off the wrong side would light up all three every game.
        assertFalse("Third is empty; defense.third is a fielder", situation.runnerOnThird)
    }

    @Test
    fun `a linescore without an offense block has no situation`() {
        // What the schedule endpoint hydrates, and what a finished game returns.
        val noOffense = json.decodeFromString(
            LinescoreDto.serializer(),
            """{"currentInning":9,"balls":0,"strikes":0,"outs":3}""",
        )
        assertNull(StatsApiMapper.toSituation(noOffense))
    }

    @Test
    fun `only the indices listed in scoringPlays become scoring plays`() {
        val plays = StatsApiMapper.toScoringPlays(
            json.decodeFromString(PlayByPlayResponse.serializer(), PLAY_BY_PLAY),
        )
        assertEquals(2, plays.size)
        assertTrue(plays[0].description.startsWith("Kyle Schwarber homers"))
        assertTrue(plays[1].description.startsWith("Trea Turner doubles"))
    }

    @Test
    fun `a scoring play carries its half-inning and the score after it`() {
        val plays = StatsApiMapper.toScoringPlays(
            json.decodeFromString(PlayByPlayResponse.serializer(), PLAY_BY_PLAY),
        )
        val double = plays[1]
        assertEquals(3, double.inning)
        assertFalse(double.isTopInning)
        assertEquals("Bot 3", double.halfInningLabel)
        assertEquals(0, double.awayScore)
        assertEquals(3, double.homeScore)
    }

    @Test
    fun `an index past the end of allPlays is skipped, not fatal`() {
        // A truncated or stale response should cost one row, not the whole screen.
        val ragged = """{"allPlays":[
            {"result":{"description":"Only play.","awayScore":1,"homeScore":0},
             "about":{"inning":1,"isTopInning":true}}
        ],"scoringPlays":[0, 7, 99]}"""
        val plays = StatsApiMapper.toScoringPlays(
            json.decodeFromString(PlayByPlayResponse.serializer(), ragged),
        )
        assertEquals(1, plays.size)
        assertEquals("Only play.", plays.single().description)
    }

    @Test
    fun `half-inning falls back to halfInning when isTopInning is absent`() {
        val noFlag = """{"allPlays":[
            {"result":{"description":"Someone scores.","awayScore":0,"homeScore":2},
             "about":{"inning":4,"halfInning":"bottom"}}
        ],"scoringPlays":[0]}"""
        val play = StatsApiMapper.toScoringPlays(
            json.decodeFromString(PlayByPlayResponse.serializer(), noFlag),
        ).single()
        assertFalse(play.isTopInning)
        assertEquals("Bot 4", play.halfInningLabel)
    }

    @Test
    fun `a play with no description is dropped rather than shown blank`() {
        val blank = """{"allPlays":[
            {"result":{"awayScore":1,"homeScore":0},"about":{"inning":2,"isTopInning":true}}
        ],"scoringPlays":[0]}"""
        val plays = StatsApiMapper.toScoringPlays(
            json.decodeFromString(PlayByPlayResponse.serializer(), blank),
        )
        assertTrue(plays.isEmpty())
    }
}
