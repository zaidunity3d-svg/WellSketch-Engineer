package com.wellsketch.engineer.core.ui.schematic

import androidx.compose.ui.graphics.Color
import com.wellsketch.engineer.core.model.ct.CtBhaComponentType
import com.wellsketch.engineer.core.model.workover.WorkoverToolType
import com.wellsketch.engineer.core.model.geology.Lithology
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CementStatus
import com.wellsketch.engineer.core.model.well.CompletionComponentType
import com.wellsketch.engineer.core.model.well.PerforationStatus
import com.wellsketch.engineer.core.schematic.ElementDetail
import com.wellsketch.engineer.core.schematic.ElementKind
import com.wellsketch.engineer.core.schematic.SchematicElement
import com.wellsketch.engineer.core.schematic.SurfaceBlockKind

/** Drawing colors for the engineering schematic (always drawn on a light "paper" background). */
object SchematicPalette {
    val paper = Color(0xFFF8F7F3)
    val ink = Color(0xFF1E2A33)
    val grid = Color(0x331E2A33)
    val groundLine = Color(0xFF6B4F2A)
    val mslLine = Color(0xFF2A6FB0)
    val selection = Color(0xFFFFB300)
    val holeFluid = Color(0xFFDDE8EF)
    val ctLine = Color(0xFFD08C12)
    val soilTop = Color(0xFFAD8D66)
    val soilBottom = Color(0xFF6E5236)
    val soilLine = Color(0x596E5236)

    fun elementColor(e: SchematicElement): Color = when (val d = e.detail) {
        is ElementDetail.Casing -> if (d.role.isLiner) Color(0xFF5E7489) else when (d.role) {
            CasingRole.CONDUCTOR -> Color(0xFF39434C)
            else -> Color(0xFF46535F)
        }
        is ElementDetail.Cement -> when (d.status) {
            CementStatus.DESIGNED -> Color(0xFFC2BDB0)
            CementStatus.REPORTED_AS_BUILT -> Color(0xFFADA796)
            CementStatus.LOG_EVALUATED -> Color(0xFF948D7A)
        }
        is ElementDetail.Component -> componentColor(d.type)
        is ElementDetail.Perforation -> when (d.status) {
            PerforationStatus.OPEN -> Color(0xFFD9480F)
            PerforationStatus.PLANNED -> Color(0xFFF08C00)
            PerforationStatus.SQUEEZED, PerforationStatus.ISOLATED -> Color(0xFF8E8E8E)
        }
        is ElementDetail.Bha -> bhaColor(d.type)
        is ElementDetail.WorkoverTool -> workoverToolColor(d.type)
        ElementDetail.None -> when (e.kind) {
            ElementKind.OPEN_HOLE -> holeFluid
            ElementKind.TUBING_WALL -> Color(0xFF2E6F9E)
            ElementKind.CT_WALL -> ctLine
            ElementKind.WORK_STRING_WALL -> Color(0xFF6A4C93)
            else -> Color(0xFF607080)
        }
    }

    private fun componentColor(type: CompletionComponentType): Color = when (type) {
        CompletionComponentType.PACKER -> Color(0xFF9A3B2E)
        CompletionComponentType.BRIDGE_PLUG, CompletionComponentType.CEMENT_RETAINER -> Color(0xFF3B3B3B)
        CompletionComponentType.ESP -> Color(0xFF2F7D4F)
        CompletionComponentType.SAFETY_VALVE -> Color(0xFFB0702A)
        CompletionComponentType.LANDING_NIPPLE, CompletionComponentType.SLIDING_SLEEVE -> Color(0xFF6B5B95)
        CompletionComponentType.GAS_LIFT_MANDREL -> Color(0xFF4F7CAC)
        CompletionComponentType.TUBING_HANGER -> Color(0xFF55606B)
        CompletionComponentType.WIRELINE_ENTRY_GUIDE, CompletionComponentType.OTHER -> Color(0xFF6E7B86)
    }

    private fun workoverToolColor(type: WorkoverToolType): Color = when (type) {
        WorkoverToolType.PACKER -> Color(0xFF9A3B2E)
        WorkoverToolType.BRIDGE_PLUG -> Color(0xFF3B3B3B)
        WorkoverToolType.PERFORATING_GUN -> Color(0xFFD9480F)
        WorkoverToolType.ESP_PUMP, WorkoverToolType.ESP_INTAKE, WorkoverToolType.ESP_SEAL,
        WorkoverToolType.ESP_MOTOR, WorkoverToolType.ESP_SENSOR -> Color(0xFF2F7D4F)
        WorkoverToolType.CEMENT_STINGER -> Color(0xFF8E8676)
        else -> Color(0xFF8A5A2B)
    }

    private fun bhaColor(type: CtBhaComponentType): Color = when (type) {
        CtBhaComponentType.JETTING_NOZZLE, CtBhaComponentType.MILL -> Color(0xFF5B3A1A)
        CtBhaComponentType.MOTOR -> Color(0xFF3F5D2A)
        else -> Color(0xFF8A5A2B)
    }

    fun lithologyColor(lithology: Lithology): Color = when (lithology) {
        Lithology.SANDSTONE -> Color(0xFFF2DE8C)
        Lithology.SHALE -> Color(0xFF9DA58F)
        Lithology.CLAYSTONE -> Color(0xFFB5B89A)
        Lithology.SILTSTONE -> Color(0xFFC9C39A)
        Lithology.LIMESTONE -> Color(0xFF9EC6E0)
        Lithology.DOLOMITE -> Color(0xFFB7A6D6)
        Lithology.CHALK -> Color(0xFFE4EEF2)
        Lithology.MARL -> Color(0xFFB4C9B0)
        Lithology.ANHYDRITE -> Color(0xFFE6B8D6)
        Lithology.SALT -> Color(0xFFEDEDED)
        Lithology.COAL -> Color(0xFF3A3A3A)
        Lithology.CONGLOMERATE -> Color(0xFFE5C07B)
        Lithology.IGNEOUS_METAMORPHIC -> Color(0xFFD08770)
        Lithology.UNCONSOLIDATED -> Color(0xFFE9D9B5)
        Lithology.UNKNOWN -> Color(0xFFDADADA)
    }

    fun surfaceColor(kind: SurfaceBlockKind): Color = when (kind) {
        SurfaceBlockKind.WELLHEAD, SurfaceBlockKind.TREE_ADAPTER -> Color(0xFF5B6770)
        SurfaceBlockKind.TREE_CAP, SurfaceBlockKind.TREE_VALVE, SurfaceBlockKind.TREE_TEE -> Color(0xFFC0392B)
        SurfaceBlockKind.STRIPPER -> Color(0xFF8E3B2E)
        SurfaceBlockKind.BOP -> Color(0xFFB23A2A)
        SurfaceBlockKind.RISER, SurfaceBlockKind.CROSSOVER -> Color(0xFF6F7C87)
        SurfaceBlockKind.FLOW_CROSS -> Color(0xFF7D5BA6)
        SurfaceBlockKind.INJECTOR -> Color(0xFFD08C12)
        SurfaceBlockKind.GOOSENECK -> Color(0xFFB07A1A)
        SurfaceBlockKind.REEL -> Color(0xFF365F8C)
        SurfaceBlockKind.CONTROL_CABIN -> Color(0xFF4A6378)
        SurfaceBlockKind.POWER_PACK -> Color(0xFF4F5E3A)
        SurfaceBlockKind.OTHER -> Color(0xFF7A848C)
        SurfaceBlockKind.TREE_WING_VALVE, SurfaceBlockKind.TREE_CHOKE -> Color(0xFFC0392B)
        SurfaceBlockKind.DRILLING_BELL_NIPPLE -> Color(0xFF9AA5AF)
        SurfaceBlockKind.DRILLING_ANNULAR, SurfaceBlockKind.DRILLING_RAM, SurfaceBlockKind.DRILLING_SPOOL -> Color(0xFFC62828)
        SurfaceBlockKind.CELLAR -> Color(0xFFC2BDB1)
        SurfaceBlockKind.SUBSTRUCTURE, SurfaceBlockKind.RIG_FLOOR -> Color(0xFF6E7A85)
    }
}
