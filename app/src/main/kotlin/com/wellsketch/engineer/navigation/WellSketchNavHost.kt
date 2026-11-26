package com.wellsketch.engineer.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.wellsketch.engineer.AppContainer
import com.wellsketch.engineer.feature.ctcalc.CtCalculationsScreen
import com.wellsketch.engineer.feature.ctcalc.CtCalculationsViewModel
import com.wellsketch.engineer.feature.equipment.EquipmentLibraryScreen
import com.wellsketch.engineer.feature.equipment.EquipmentLibraryViewModel
import com.wellsketch.engineer.feature.wellcalc.WellCalculationsScreen
import com.wellsketch.engineer.feature.wellcalc.WellCalculationsViewModel
import com.wellsketch.engineer.feature.welldesign.WellDesignScreen
import com.wellsketch.engineer.feature.welldesign.WellDesignViewModel
import com.wellsketch.engineer.home.HomeScreen
import com.wellsketch.engineer.home.HomeViewModel
import com.wellsketch.engineer.intro.IntroScreen

@Composable
fun WellSketchNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val unitSystem by container.unitSystem.collectAsStateWithLifecycle()

    // Screen changes are instant: this engineering app avoids animated transitions entirely.
    NavHost(
        navController = navController,
        startDestination = IntroRoute,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        composable<IntroRoute> {
            IntroScreen(
                onFinished = {
                    // Timer and tap can both fire: leave the intro only once.
                    if (navController.currentDestination?.hasRoute<IntroRoute>() == true) {
                        navController.navigate(HomeRoute) { popUpTo<IntroRoute> { inclusive = true } }
                    }
                },
            )
        }
        composable<HomeRoute> {
            val vm = viewModel { HomeViewModel(container.wellProjects) }
            HomeScreen(
                viewModel = vm,
                unitSystem = unitSystem,
                onUnitSystemChange = container::setUnitSystem,
                onOpenWellDesign = { navController.navigate(WellDesignRoute(it)) },
                onOpenWellCalculations = { navController.navigate(WellCalculationsRoute(it)) },
                onOpenCtCalculations = { navController.navigate(CtCalculationsRoute(it)) },
                onOpenEquipmentLibrary = { navController.navigate(EquipmentLibraryRoute) },
            )
        }
        composable<WellDesignRoute> { entry ->
            val route = entry.toRoute<WellDesignRoute>()
            val vm = viewModel { WellDesignViewModel(route.projectId, container.wellProjects, container.autosaver) }
            WellDesignScreen(vm, unitSystem, onBack = { navController.popBackStack() })
        }
        composable<WellCalculationsRoute> { entry ->
            val route = entry.toRoute<WellCalculationsRoute>()
            val vm = viewModel { WellCalculationsViewModel(route.projectId, container.wellProjects) }
            WellCalculationsScreen(vm, unitSystem, onBack = { navController.popBackStack() })
        }
        composable<CtCalculationsRoute> { entry ->
            val route = entry.toRoute<CtCalculationsRoute>()
            val vm = viewModel { CtCalculationsViewModel(route.projectId, container.wellProjects, container.autosaver) }
            CtCalculationsScreen(vm, unitSystem, onBack = { navController.popBackStack() })
        }
        composable<EquipmentLibraryRoute> {
            val vm = viewModel { EquipmentLibraryViewModel(container.equipmentCatalog) }
            EquipmentLibraryScreen(vm, unitSystem, onBack = { navController.popBackStack() })
        }
    }
}
