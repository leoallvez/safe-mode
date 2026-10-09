package com.poc.safemode.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.poc.safemode.ui.areas.AreasScreen
import com.poc.safemode.ui.editor.AreaEditorScreen

internal object Routes {
    const val AREAS = "areas"

    const val ARG_AREA_ID = "areaId"
    const val EDITOR = "editor?$ARG_AREA_ID={$ARG_AREA_ID}"

    fun editor(areaId: String?): String =
        if (areaId == null) "editor" else "editor?$ARG_AREA_ID=$areaId"
}

@Composable
fun SafeModeApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.AREAS,
        modifier = modifier
    ) {
        composable(Routes.AREAS) {
            AreasScreen(
                onNewAreaClick = { navController.navigate(Routes.editor(null)) },
                onAreaClick = { areaId -> navController.navigate(Routes.editor(areaId)) }
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument(Routes.ARG_AREA_ID) {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            AreaEditorScreen(
                onClose = { navController.popBackStack(Routes.AREAS, inclusive = false) }
            )
        }
    }
}
