package com.bhavesh.geocircle.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bhavesh.geocircle.ui.GeoCircleViewModel
import com.bhavesh.geocircle.ui.screens.CreateJoinGroupScreen
import com.bhavesh.geocircle.ui.screens.GroupDetailsScreen
import com.bhavesh.geocircle.ui.screens.HomeScreen

object Screen {
    const val Home = "home"
    const val CreateJoinGroup = "create_join/{tab}"
    const val GroupDetails = "group_details/{groupId}"

    fun createJoinGroup(tab: Int = 0): String = "create_join/$tab"
    fun groupDetails(groupId: String): String = "group_details/$groupId"
}

@Composable
fun GeoCircleNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    geoCircleViewModel: GeoCircleViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home,
        modifier = modifier
    ) {
        composable(Screen.Home) {
            HomeScreen(
                viewModel = geoCircleViewModel,
                onCreateGroupClick = {
                    navController.navigate(Screen.createJoinGroup(0))
                },
                onJoinGroupClick = {
                    navController.navigate(Screen.createJoinGroup(1))
                },
                onGroupClick = { groupId ->
                    navController.navigate(Screen.groupDetails(groupId))
                }
            )
        }

        composable(
            route = Screen.CreateJoinGroup,
            arguments = listOf(
                navArgument("tab") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getInt("tab") ?: 0
            CreateJoinGroupScreen(
                initialTab = tab,
                viewModel = geoCircleViewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onGroupSelected = { groupId ->
                    navController.navigate(Screen.groupDetails(groupId)) {
                        popUpTo(Screen.Home) {
                            inclusive = false
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.GroupDetails,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupDetailsScreen(
                groupId = groupId,
                viewModel = geoCircleViewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
