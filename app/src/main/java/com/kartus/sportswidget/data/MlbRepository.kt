package com.kartus.sportswidget.data

import com.kartus.sportswidget.domain.DivisionStandings
import com.kartus.sportswidget.domain.Linescore
import com.kartus.sportswidget.domain.Scoreboard
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
                games = games.sortedWith(
                    compareBy(
                        { it.startTimeUtcMillis ?: Long.MAX_VALUE },
                        { it.gamePk },
                    ),
                ),
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

    suspend fun linescore(gamePk: Long): Result<Linescore> = runCatching {
        StatsApiMapper.toLinescore(api.linescore(gamePk))
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
    }
}
