package com.wellsketch.engineer.core.data.demo

import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.geology.FormationTop
import com.wellsketch.engineer.core.model.geology.Lithology
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.surface.EndConnection
import com.wellsketch.engineer.core.model.surface.StackComponent
import com.wellsketch.engineer.core.model.surface.StackComponentType
import com.wellsketch.engineer.core.model.surface.ValveOperator
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CasingShoe
import com.wellsketch.engineer.core.model.well.CasingString
import com.wellsketch.engineer.core.model.well.CementSheath
import com.wellsketch.engineer.core.model.well.CementStatus
import com.wellsketch.engineer.core.model.well.ComponentMounting
import com.wellsketch.engineer.core.model.well.CompletionComponent
import com.wellsketch.engineer.core.model.well.CompletionComponentType
import com.wellsketch.engineer.core.model.well.HoleSection
import com.wellsketch.engineer.core.model.well.ShoeType
import com.wellsketch.engineer.core.model.well.TubularSpec

/**
 * Small builders for SYNTHETIC demonstration fixtures. Every object created here carries
 * [DataProvenance.syntheticDemo]. Diameters are round illustrative numbers chosen to be
 * geometrically consistent; they are NOT taken from API or manufacturer tables.
 */
internal object Demo {
    val synthetic: DataProvenance = DataProvenance.syntheticDemo()

    fun rkb(meters: Double) = MeasuredDepth.rkb(Length.meters(meters))
    fun tvdGl(meters: Double) = TrueVerticalDepth.gl(Length.meters(meters))
    fun inch(value: Double) = Length.inches(value)

    fun spec(odIn: Double, idIn: Double, catalogItemId: String? = null, label: String? = null) = TubularSpec(
        outerDiameter = inch(odIn),
        innerDiameter = inch(idIn),
        grade = label,
        catalogItemId = catalogItemId,
        provenance = synthetic,
    )

    fun hole(id: String, bitIn: Double, label: String, top: Double, bottom: Double) =
        HoleSection(id, label, inch(bitIn), rkb(top), rkb(bottom), synthetic)

    fun casing(
        id: String,
        name: String,
        role: CasingRole,
        spec: TubularSpec,
        top: Double,
        shoe: Double,
        topOfCement: Double?,
        shoeType: ShoeType = ShoeType.FLOAT_SHOE,
    ) = CasingString(
        id = id,
        name = name,
        role = role,
        spec = spec,
        top = rkb(top),
        shoe = CasingShoe(depth = rkb(shoe), type = shoeType),
        cement = topOfCement?.let {
            CementSheath(rkb(it), rkb(shoe), CementStatus.DESIGNED, "Synthetic cement interval", synthetic)
        },
        provenance = synthetic,
    )

    fun formation(id: String, name: String, topTvdGl: Double, lithology: Lithology) =
        FormationTop(id, name, tvdGl(topTvdGl), lithology, provenance = synthetic)

    fun component(
        id: String,
        type: CompletionComponentType,
        name: String,
        top: Double,
        lengthM: Double,
        mounting: ComponentMounting = ComponentMounting.ON_TUBING,
        odIn: Double? = null,
        minIdIn: Double? = null,
        blocking: Boolean = false,
    ) = CompletionComponent(
        id = id,
        type = type,
        name = name,
        top = rkb(top),
        length = Length.meters(lengthM),
        mounting = mounting,
        outerDiameter = odIn?.let(::inch),
        minimumInnerDiameter = minIdIn?.let(::inch),
        isFullBoreBlocking = blocking,
        provenance = synthetic,
    )

    fun stackComponent(
        id: String,
        type: StackComponentType,
        name: String,
        boreIn: Double,
        ratingPsi: Double,
        top: String,
        bottom: String,
        heightM: Double?,
        operator: ValveOperator? = null,
    ) = StackComponent(
        id = id,
        type = type,
        name = name,
        bore = inch(boreIn),
        ratedWorkingPressure = Pressure.psi(ratingPsi),
        topConnection = EndConnection(top),
        bottomConnection = EndConnection(bottom),
        height = heightM?.let(Length::meters),
        provenance = synthetic,
        operator = operator,
    )
}
