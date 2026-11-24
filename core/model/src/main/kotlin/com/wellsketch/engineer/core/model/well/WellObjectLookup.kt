package com.wellsketch.engineer.core.model.well

import com.wellsketch.engineer.core.model.geology.FormationTop

/** A model object of a well, found by id (used by selection/detail views). */
sealed interface WellObject {
    data class Hole(val value: HoleSection) : WellObject
    data class Casing(val value: CasingString) : WellObject
    data class Tubing(val value: TubingString) : WellObject
    data class Component(val value: CompletionComponent) : WellObject
    data class Perforation(val value: PerforationInterval) : WellObject
    data class Formation(val value: FormationTop) : WellObject
}

fun Well.findObject(id: String, completion: Completion?): WellObject? {
    holeSections.firstOrNull { it.id == id }?.let { return WellObject.Hole(it) }
    casingStrings.firstOrNull { it.id == id }?.let { return WellObject.Casing(it) }
    formations.firstOrNull { it.id == id }?.let { return WellObject.Formation(it) }
    completion?.tubingString?.takeIf { it.id == id }?.let { return WellObject.Tubing(it) }
    completion?.components?.firstOrNull { it.id == id }?.let { return WellObject.Component(it) }
    completion?.perforations?.firstOrNull { it.id == id }?.let { return WellObject.Perforation(it) }
    return null
}
