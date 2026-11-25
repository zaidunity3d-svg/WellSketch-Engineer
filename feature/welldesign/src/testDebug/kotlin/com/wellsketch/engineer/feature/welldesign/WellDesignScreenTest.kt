package com.wellsketch.engineer.feature.welldesign

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsNodeInteraction
import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.data.repository.InMemoryWellProjectRepository
import com.wellsketch.engineer.core.data.repository.ProjectAutosaver
import com.wellsketch.engineer.core.model.units.UnitSystem
import com.wellsketch.engineer.core.schematic.ElementKind
import com.wellsketch.engineer.core.ui.theme.WellSketchTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals

/**
 * UI regression tests: typing a depth digit by digit must update the field, the model and
 * the sketch, exactly as a user does on a phone keyboard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class WellDesignScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val project = SyntheticDemoProjects.vertical()
    private val repo = InMemoryWellProjectRepository(listOf(project))
    private val viewModel = WellDesignViewModel(
        project.id, repo, ProjectAutosaver(CoroutineScope(SupervisorJob() + Dispatchers.IO), repo),
    )

    private fun showScreen() {
        compose.setContent { WellSketchTheme { WellDesignScreen(viewModel, UnitSystem.METRIC, onBack = {}) } }
        compose.waitUntil(10_000) { viewModel.uiState.value.snapshot != null }
    }

    /** Types one character at a time, like a phone keyboard. */
    private fun SemanticsNodeInteraction.typeSlowly(text: String) {
        performTextClearance()
        text.forEach { performTextInput(it.toString()) }
    }

    private fun field(label: String, index: Int): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        return compose.onAllNodes(hasText(label) and hasSetTextAction())[index]
    }

    private fun casingShoe(id: String): Double? =
        viewModel.uiState.value.snapshot?.scene?.elements?.firstOrNull { it.id == "casing:$id" }?.mdBottom

    @Test
    fun typingProductionCasingShoeMovesCasingInSketch() {
        showScreen()
        val productionCasing = project.well.casingStrings.last()
        val shoeField = field("Shoe MD", index = 3)
        shoeField.typeSlowly("2000")
        compose.waitUntil(10_000) { casingShoe(productionCasing.id)?.let { abs(it - 2000.0) < 1e-6 } == true }
        compose.waitForIdle()
        shoeField.assert(hasText("2000"))
        assertEquals(2000.0, casingShoe(productionCasing.id)!!, 1e-6)
    }

    @Test
    fun typingEndOfTubingMovesTubingInSketch() {
        showScreen()
        val eotField = field("End of tubing MD", index = 0)
        eotField.typeSlowly("1800")
        compose.waitUntil(10_000) {
            viewModel.uiState.value.snapshot?.scene?.elements
                ?.firstOrNull { it.kind == ElementKind.TUBING_WALL }?.mdBottom?.let { abs(it - 1800.0) < 1e-6 } == true
        }
        compose.waitForIdle()
        eotField.assert(hasText("1800"))
    }
}
