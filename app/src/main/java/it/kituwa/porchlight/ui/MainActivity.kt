package it.kituwa.porchlight.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.kituwa.porchlight.BuildConfig
import it.kituwa.porchlight.ui.theme.PorchlightTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PorchlightTheme {
                PorchlightNavHost()
            }
        }
    }
}

@Composable
private fun PorchlightNavHost(viewModel: PorchlightViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "overview") {
        composable("overview") {
            OverviewScreen(
                viewModel = viewModel,
                onOpenServer = { navController.navigate("server/${it.id}") },
                onAddServer = { navController.navigate("add") },
                onAbout = { navController.navigate("about") },
            )
        }
        composable(
            route = "server/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ServerDetailScreen(
                serverId = id,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onRemoved = { navController.popBackStack() },
            )
        }
        composable("add") {
            AddServerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable("about") {
            AboutScreen(
                versionName = BuildConfig.VERSION_NAME,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
