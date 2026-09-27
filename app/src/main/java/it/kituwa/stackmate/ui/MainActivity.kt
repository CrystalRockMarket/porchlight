package it.kituwa.stackmate.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.kituwa.stackmate.BuildConfig
import it.kituwa.stackmate.ui.theme.StackMateTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            StackMateTheme {
                StackMateNavHost()
            }
        }
    }
}

@Composable
private fun StackMateNavHost(viewModel: StackMateViewModel = viewModel()) {
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
