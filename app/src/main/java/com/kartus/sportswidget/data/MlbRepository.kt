package com.kartus.sportswidget.data

import com.kartus.sportswidget.domain.Boxscore
import com.kartus.sportswidget.domain.GameSituation
import com.kartus.sportswidget.domain.Gamecast
import com.kartus.sportswidget.domain.DivisionStandings
import com.kartus.sportswidget.domain.Linescore
import com.kartus.sportswidget.domain.Scoreboard
import com.kartus.sportswidget.domain.ScoreboardOrder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Caching front door for MLB data.
 *
 * Holds two things the layers above should not have to think about:
 *  - a per-date scoreboard cache, so flipping back and forth through dates is free;
 *  - a floor on how often we will actually hit the network, so a UI that polls
 *    aggressively during a live game cannot turn into a request storm if something
 *    upstream starts erroring and the caller retries in a tight loop.
 */
class MlbRepository(private val api: StatsApiClient) {

    private val mutex = Mutex()
    private val scoreboards = mutableMapOf<String, Scoreboard>()
    private var standingsCache: Pair<Long, List<DivisionStandings>>? = null
    private val boxscores = mutableMapOf<Long, Pair<Long, Boxscore>>()

    /**
     * Scoreboard for [date]. Returns the cached copy when it is younger than the
     * applicable TTL unless [force] is set (pull-to-refresh, widget tap).
     */
    suspend fun scoreboard(
        date: LocalDate,
        force: Boolean = false,
    ): Result<Scoreboard> {
        val key = date.toString()

        mutex.withLock { scoreboards[key] }?.let { cached ->
            if (!force && !cached.isStale()) return Result.success(cached)
        }

        return runCatching {
            val games = StatsApiMapper.toGames(api.schedule(key))
            val board = Scoreboard(
                date = key,
                games = games.sortedWith(ScoreboardOrder),
                fetchedAtMillis = System.currentTimeMillis(),
            )
            mutex.withLock { scoreboards[key] = board }
            board
        }.recoverCatching { error ->
            // A failed refresh should not blank an already-good screen. Serve the
            // stale copy if we have one and let the caller surface the staleness.
            mutex.withLock { scoreboards[key] } ?: throw error
        }
    }

    /** Last known scoreboard for [date] without touching the network. */
    suspend fun cachedScoreboard(date: LocalDate): Scoreboard? =
        mutex.withLock { scoreboards[date.toString()] }

    suspend fun standings(season: Int, force: Boolean = false): Result<List<DivisionStandings>> {
        standingsCache?.let { (fetchedAt, value) ->
            if (!force && System.currentTimeMillis() - fetchedAt < STANDINGS_TTL_MILLIS) {
                return Result.success(value)
            }
        }

        return runCatching {
            val standings = StatsApiMapper.toStandings(api.standings(season))
            standingsCache = System.currentTimeMillis() to standings
            standings
        }.recoverCatching { error ->
            standingsCache?.second ?: throw error
        }
    }

    /**
     * Box score for one game. Cached briefly: during a live game these lines change
     * every half inning, but re-opening the same finished game should not re-fetch.
     */
    suspend fun boxscore(gamePk: Long, force: Boolean = false): Result<Boxscore> {
        mutex.withLock { boxscores[gamePk] }?.let { (fetchedAt, cached) ->
            if (!force && System.currentTimeMillis() - fetchedAt < BOXSCORE_TTL_MILLIS) {
                return Result.success(cached)
            }
        }

        return runCatching {
            val boxscore = StatsApiMapper.toBoxscore(api.boxscore(gamePk))
                ?: error("Box score not available for this game")
            mutex.withLock { boxscores[gamePk] = System.currentTimeMillis() to boxscore }
            boxscore
        }.recoverCatching { error ->
            mutex.withLock { boxscores[gamePk] }?.second ?: throw error
        }
    }

    suspend fun linescore(gamePk: Long): Result<Linescore> = runCatching {
        StatsApiMapper.toLinescore(api.linescore(gamePk))
    }

    /**
     * Everything the Gamecast shows, assembled from three small endpoints rather
     * than `feed/live`, which is megabytes per call and unsuited to a 20-second
     * poll on a phone.
     *
     * The box score is only consulted for the pitch count and the batter's line —
     * it is already cached for the Box Score tab, so this usually costs nothing.
     * If it fails, the rest of the Gamecast still renders.
     */
    suspend fun gamecast(gamePk: Long, force: Boolean = false): Result<Gamecast> = runCatching {
        val linescoreDto = api.linescore(gamePk)
        val linescore = StatsApiMapper.toLinescore(linescoreDto)
        val situation = StatsApiMapper.toSituation(linescoreDto)

        val scoringPlays = runCatching {
            StatsApiMapper.toScoringPlays(api.playByPlay(gamePk))
        }.getOrDefault(emptyList())

        Gamecast(
            linescore = linescore,
            situation = situation?.let { withBoxscoreDetail(gamePk, it, force) },
            // Newest first: during a live game the play that just happened is the
            // one worth reading.
            scoringPlays = scoringPlays.reversed(),
        )
    }

    /** Folds the pitch count and the batter's day into the situation, if available. */
    private suspend fun withBoxscoreDetail(
        gamePk: Long,
        situation: GameSituation,
        force: Boolean,
    ): GameSituation {
        val boxscore = boxscore(gamePk, force = force).getOrNull() ?: return situation
        val sides = listOf(boxscore.away, boxscore.home)

        val pitcher = situation.pitcher?.let { ref ->
            sides.flatMap { it.pitchers }.firstOrNull { it.playerId == ref.id }
        }
        val batter = situation.batter?.let { ref ->
            sides.flatMap { it.batters }.firstOrNull { it.playerId == ref.id }
        }

        return situation.copy(
            pitchCount = pitcher?.pitchCount,
            pitcherEra = pitcher?.seasonEra,
            batterSeasonAvg = batter?.seasonAvg,
            batterToday = batter?.let { "${it.hits}-for-${it.atBats}" },
        )
    }

    /**
     * Live days go stale fast; days with nothing in progress barely change at all.
     * This is what keeps a 20-second foreground poll from becoming 20-second traffic
     * once every game has gone final.
     */
    private fun Scoreboard.isStale(): Boolean {
        val age = System.currentTimeMillis() - fetchedAtMillis
        val ttl = if (hasLiveGame) LIVE_TTL_MILLIS else IDLE_TTL_MILLIS
        return age >= ttl
    }

    private companion object {
        const val LIVE_TTL_MILLIS = 15_000L
        const val IDLE_TTL_MILLIS = 5 * 60_000L
        const val STANDINGS_TTL_MILLIS = 30 * 60_000L
        const val BOXSCORE_TTL_MILLIS = 20_000L
    }
}
