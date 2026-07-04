package com.reminder.local.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reminder.local.ui.screen.category.CategoryScreen
import com.reminder.local.ui.screen.edit.EditReminderScreen
import com.reminder.local.ui.screen.list.ReminderListScreen
import com.reminder.local.ui.screen.settings.SettingsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startReminderId: Long? = null
) {
    LaunchedEffect(startReminderId) {
        if (startReminderId != null && startReminderId >= 0) {
            navController.navigate(Routes.edit(startReminderId))
        }
    }

    NavHost(navController = navController, startDestination = Routes.LIST) {

        composable(Routes.LIST) {
            ReminderListScreen(
                onAddClick = { navController.navigate(Routes.edit()) },
                onEditClick = { id -> navController.navigate(Routes.edit(id)) },
                onCategoryClick = { navController.navigate(Routes.CATEGORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = Routes.EDIT_PATTERN,
            arguments = listOf(
                navArgument(Routes.EDIT_ARG_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            EditReminderScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Routes.CATEGORY) {
            CategoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
