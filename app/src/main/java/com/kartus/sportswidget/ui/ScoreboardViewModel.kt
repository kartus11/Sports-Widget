package com.kartus.sportswidget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.kartus.sportswidget.data.MlbRepository
import com.kartus.sportswidget.domain.Boxscore
import com.kartus.sportswidget.domain.DivisionStandings
import com.kartus.sportswidget.domain.Game
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Drives the scoreboard screen.
 *
 * The widget is capped at a 15-minute refresh by WorkManager, so the app is where
 * "live" actually happens: while the screen is resumed and at least one game is in
 * progress, [startLivePolling] refreshes every [POLL_INTERVAL_MILLIS]. Polling is
 * bound to the resumed lifecycle — a backgrounded app stops hitting the network
 * entirely rather than quietly draining battery.
 */
class ScoreboardViewModel(private val repository: MlbRepository) : ViewModel() {

    data class UiState(
        val date: LocalDate = LocalDate.now(),
        val games: List<Game> = emptyList(),
        val standings: List<DivisionStandings> = emptyList(),
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val errorMessage: String? = null,
        val lastUpdatedMillis: Long? = null,
        /** Box score for whichever game the detail screen is showing, if any. */
        val boxscore: Boxscore? = null,
        val boxscoreGamePk: Long? = null,
        val boxscoreLoading: Boolean = false,
        val boxscoreError: String? = null,
    ) {
        val hasLiveGame: Boolean get() = games.any { it.state.isLive }
        val isToday: Boolean get() = date == LocalDate.now()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        load(force = false)
    }

    fun selectDate(date: LocalDate) {
        if (date == _state.value.date) return
        _state.update { it.copy(date = date, games = emptyList(), loading = true, errorMessage = null) }
        load(force = false)
    }

    fun stepDay(days: Long) = selectDate(_state.value.date.plusDays(days))

    fun goToToday() = selectDate(LocalDate.now())

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load(force = true)
    }

    private fun load(force: Boolean) {
        val date = _state.value.date
        viewModelScope.launch {
            repository.scoreboard(date, force = force)
                .onSuccess { board ->
                    // A slow response for a date the user already navigated away from
                    // must not overwrite what is on screen now.
                    if (_state.value.date.toString() != board.date) return@onSuccess
                    _state.update {
                        it.copy(
                            games = board.games,
                            loading = false,
                            refreshing = false,
                            errorMessage = null,
                            lastUpdatedMillis = board.fetchedAtMillis,
                        )
                    }
                }
                .onFailure { error ->
                    if (_state.value.date != date) return@onFailure
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            errorMessage = error.message ?: "Couldn't reach MLB StatsAPI",
                        )
                    }
                }
        }
    }

    /**
     * Called when the detail screen opens. Also marks this game as the one the poll
     * loop should keep fresh, so a box score stays current during a live game
     * instead of freezing at whatever inning it was opened on.
     */
    fun openBoxscore(gamePk: Long) {
        if (_state.value.boxscoreGamePk == gamePk && _state.value.boxscore != null) return
        _state.update {
            it.copy(
                boxscoreGamePk = gamePk,
                boxscore = null,
                boxscoreLoading = true,
                boxscoreError = null,
            )
        }
        fetchBoxscore(gamePk, force = false)
    }

    fun closeBoxscore() {
        _state.update {
            it.copy(boxscoreGamePk = null, boxscore = null, boxscoreLoading = false, boxscoreError = null)
        }
    }

    private fun fetchBoxscore(gamePk: Long, force: Boolean) {
        viewModelScope.launch {
            repository.boxscore(gamePk, force = force)
                .onSuccess { boxscore ->
                    // Ignore a slow response for a game the user already backed out of.
                    if (_state.value.boxscoreGamePk != gamePk) return@onSuccess
                    _state.update {
                        it.copy(boxscore = boxscore, boxscoreLoading = false, boxscoreError = null)
                    }
                }
                .onFailure { error ->
                    if (_state.value.boxscoreGamePk != gamePk) return@onFailure
                    _state.update {
                        it.copy(
                            boxscoreLoading = false,
                            boxscoreError = error.message ?: "Box score unavailable",
                        )
                    }
                }
        }
    }

    fun loadStandings() {
        if (_state.value.standings.isNotEmpty()) return
        viewModelScope.launch {
            repository.standings(LocalDate.now().year)
                .onSuccess { standings -> _state.update { it.copy(standings = standings) } }
                .onFailure { error ->
                    _state.update { it.copy(errorMessage = error.message ?: "Couldn't load standings") }
                }
        }
    }

    /** Called when the scoreboard screen resumes. */
    fun startLivePolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)
                // Only worth a request when something is actually in progress today.
                // The repository's TTL is the real backstop; this just avoids the call.
                val current = _state.value
                if (current.hasLiveGame && current.isToday) {
                    load(force = true)
                }
                // Keep an open box score moving in step with the scoreboard.
                current.boxscoreGamePk?.let { gamePk ->
                    if (current.games.firstOrNull { it.gamePk == gamePk }?.state?.isLive == true) {
                        fetchBoxscore(gamePk, force = true)
                    }
                }
            }
        }
    }

    /** Called when the scoreboard screen pauses. */
    fun stopLivePolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        stopLivePolling()
        super.onCleared()
    }

    companion object {
        /**
         * Fast enough that a score change lands within a pitch or two, slow enough
         * to stay well-mannered against a free public endpoint.
         */
        const val POLL_INTERVAL_MILLIS = 20_000L

        fun factory(repository: MlbRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                ScoreboardViewModel(repository) as T
        }
    }
}
