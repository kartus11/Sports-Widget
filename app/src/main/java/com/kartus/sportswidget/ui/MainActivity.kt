package com.kartus.sportswidget.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kartus.sportswidget.data.ServiceLocator
import com.kartus.sportswidget.ui.screen.GameDetailScreen
import com.kartus.sportswidget.ui.screen.ScoreboardScreen
import com.kartus.sportswidget.ui.screen.StandingsScreen
import com.kartus.sportswidget.ui.theme.SportsWidgetTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SportsWidgetTheme {
                SportsApp()
            }
        }
    }
}

private object Routes {
    const val SCOREBOARD = "scoreboard"
    const val STANDINGS = "standings"
    const val GAME_DETAIL = "game/{gamePk}"

    fun gameDetail(gamePk: Long) = "game/$gamePk"
}

@Composable
private fun SportsApp() {
    val navController = rememberNavController()
    val repository = ServiceLocator.repository(LocalContext.current)

    // One ViewModel for the whole graph: the detail screen reads the game it was
    // opened for out of the already-loaded scoreboard rather than refetching it.
    val viewModel: ScoreboardViewModel = viewModel(
        factory = ScoreboardViewModel.factory(repository),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.SCOREBOARD) {
        composable(Routes.SCOREBOARD) {
            ScoreboardScreen(
                state = state,
                onRefresh = viewModel::refresh,
                onPreviousDay = { viewModel.stepDay(-1) },
                onNextDay = { viewModel.stepDay(1) },
                onToday = viewModel::goToToday,
                onStartPolling = viewModel::startLivePolling,
                onStopPolling = viewModel::stopLivePolling,
                onGameClick = { navController.navigate(Routes.gameDetail(it)) },
                onStandingsClick = { navController.navigate(Routes.STANDINGS) },
            )
        }

        composable(
            route = Routes.GAME_DETAIL,
            arguments = listOf(navArgument("gamePk") { type = NavType.LongType }),
        ) { entry ->
            val gamePk = entry.arguments?.getLong("gamePk") ?: return@composable
            GameDetailScreen(
                game = state.games.firstOrNull { it.gamePk == gamePk },
                boxscore = state.boxscore,
                boxscoreLoading = state.boxscoreLoading,
                boxscoreError = state.boxscoreError,
                onOpenBoxscore = viewModel::openBoxscore,
                onCloseBoxscore = viewModel::closeBoxscore,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.STANDINGS) {
            StandingsScreen(
                standings = state.standings,
                onLoad = viewModel::loadStandings,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
