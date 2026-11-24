package com.wellsketch.engineer.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
@Serializable
data object HomeRoute

@Serializable
data class WellDesignRoute(val projectId: String)

@Serializable
data class WellCalculationsRoute(val projectId: String)

@Serializable
data class CtCalculationsRoute(val projectId: String)

@Serializable
data object EquipmentLibraryRoute
