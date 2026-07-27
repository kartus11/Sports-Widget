package com.kartus.sportswidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin client over MLB's public StatsAPI.
 *
 * No API key, no auth, no published quota — but it is someone else's server, so
 * every response is cached on disk and the repository layer throttles callers.
 * Deliberately hand-rolled over OkHttp instead of Retrofit: three endpoints do not
 * justify the dependency, and it keeps the JSON handling in one visible place.
 */
class StatsApiClient(cacheDir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .cache(Cache(File(cacheDir, "statsapi"), MAX_CACHE_BYTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Games for a single calendar day. [date] must be yyyy-MM-dd. */
    suspend fun schedule(date: String): ScheduleResponse = get(
        "$BASE/api/v1/schedule?sportId=1&date=$date" +
            "&hydrate=team,linescore,probablePitcher,venue",
        ScheduleResponse.serializer(),
    )

    /** Full regular-season standings for both leagues. */
    suspend fun standings(season: Int): StandingsResponse = get(
        "$BASE/api/v1/standings?leagueId=103,104&season=$season" +
            "&standingsTypes=regularSeason&hydrate=team,division",
        StandingsResponse.serializer(),
    )

    /** Per-player batting and pitching lines for one game. */
    suspend fun boxscore(gamePk: Long): BoxscoreResponse = get(
        "$BASE/api/v1/game/$gamePk/boxscore",
        BoxscoreResponse.serializer(),
    )

    /** Inning-by-inning detail for one game. */
    suspend fun linescore(gamePk: Long): LinescoreDto = get(
        "$BASE/api/v1/game/$gamePk/linescore",
        LinescoreDto.serializer(),
    )

    private suspend fun <T> get(
        url: String,
        serializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("StatsAPI ${response.code} for $url")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Empty body for $url")
            json.decodeFromString(serializer, body)
        }
    }

    private companion object {
        const val BASE = "https://statsapi.mlb.com"
        const val MAX_CACHE_BYTES = 8L * 1024 * 1024

        /**
         * Identify the app honestly. This is an undocumented endpoint used at a
         * personal scale; a real UA is the polite minimum.
         */
        const val USER_AGENT = "SportsWidget/0.1 (personal use; Android)"
    }
}
