# WellSketch Engineer

Independent native Android application for oil and gas well design, drilling/completion engineering calculations, and coiled tubing calculations.

## Modules
- **Well Design:** Editable casing/hole/formation/completion schematic based on actual well input data; supports vertical, J-shape, S-shape and horizontal trajectories.
- **Well Calculations:** Tubular geometry, capacities, annular volumes, hydrostatic pressure, displacement and cementing; formulas require verification against engineering references.
- **CT Calculations:** Completed-well quick setup, production tubing by default (or casing/liner where tubing is absent), configurable CT surface pressure-control stack, and depth-based CT/BHA placement without animation.

## Visual design decisions
- Schematic refreshes as inputs change. The polished completed-well infographic is a separate view generated only when sufficient well data is provided.
- Casing is shown as a **full longitudinal half-cut**, with realistic nested strings and cement annuli. General view stays readable; selecting a casing segment opens detailed equipment views.
- Packer, ESP, bridge plugs and other downhole tools appear externally in the main schematic; selecting a tool opens its detailed component view from the equipment reference library.
- Stratigraphy is aligned to ground level (GL=0) at the first formation top; formation depths and casing/trajectory geometry are driven by entered data.
- CT mode uses a Christmas Tree and a configurable CT pressure-control stack, instead of a drilling-rig/BOP arrangement. Support full and compact surface views.
- CT measured-depth reference is chosen per operation. CT and BHA positions jump immediately when depths change; **no animation**.

## Engineering and development rules
- Use real input data and validated engineering calculations; do not treat generated illustrative images as authoritative.
- Preserve distinction between MD, TVD, RKB, GL, and MSL datums, including offsets and survey calculations.
- Android interface language: English only.
- Use Git branches and pull requests; inspect existing code before any changes.
- **Do not build an APK until the owner explicitly requests: «ابني التطبيق».**
- No private operator well reports, credentials, licensed manufacturer manuals, or sensitive engineering records may be committed to this public repository.

See `CLAUDE.md` for coding-agent instructions.

---

## Architecture (milestone 1)

Kotlin + Jetpack Compose, multi-module Gradle build, offline-first, manual dependency
injection. **All engineering models and logic are pure Kotlin/JVM modules** with no
Android dependency, so they are deterministic and unit-tested on any JVM; Android modules
only persist data and render state.

```
app                    Android app shell: MainActivity, type-safe navigation, home screen, AppContainer
core/model       JVM   Engineering data models: units, datums, well, geology, tubulars, completion,
                       surveys, surface stacks, CT string/BHA/PCE, equipment, provenance
core/engineering JVM   Shared calculation utilities: datum conversion, minimum-curvature trajectory,
                       WellGeometryContext (MD↔TVD on any datum), geometry consistency validator
core/schematic   JVM   Renderer-agnostic schematic scene (half-cut bands, markers, formation bands,
                       surface blocks), screen projection, inverse mapping and hit testing
core/data        JVM   Repository contracts, versioned JSON codec, ordered autosaver,
                       SYNTHETIC demo projects and equipment placeholders
core/database    Andr  Room database (offline persistence) implementing the repository contract
core/ui          Andr  Compose theme, shared components, unit formatting, schematic Canvas renderer
domain/welldesign JVM  Pure well edits and live schematic snapshots          ┐
domain/wellcalc  JVM   Calculation specification catalog (no formulas yet)   │ functional
domain/ct        JVM   CT flow path, restrictions, placement, stack checks   │ modules
domain/equipment JVM   Library grouping and provenance readiness rules       ┘
feature/welldesign  Andr  Well Design screen
feature/wellcalc    Andr  Well Calculations screen (placeholder)
feature/ctcalc      Andr  CT Calculations screen
feature/equipment   Andr  Equipment Library screen
build-logic             Gradle convention plugins (shared Android/Kotlin configuration)
```

Dependency direction: `feature → domain → core:schematic → core:engineering → core:model`;
`core:database → core:data → core:model`. Features never depend on each other.

### Key decisions (for review)

| Decision | Reason |
|---|---|
| Engineering logic in pure Kotlin/JVM modules | Deterministic, testable without Android; calculations versioned separately from UI. |
| Strongly typed units (`Length`, `Pressure`, …) stored in SI | A diameter can never be passed as a pressure; oilfield units are a display concern. Conversion factors are exact definitions (international foot, avoirdupois pound, US gallon = 231 in³). |
| `MeasuredDepth` / `TrueVerticalDepth` always carry their `DepthReference` | RKB, GL, MSL and per-operation datums cannot be mixed: comparing depths on different datums throws; MD on MSL is not representable. One `DatumConverter` does all conversions. |
| `OperatingState` sealed type | Drilling (rig + BOP), completed (Christmas Tree) and CT intervention (tree + CT stack) each carry only their own surface equipment, so an invalid rig/BOP/tree combination cannot be represented. |
| `TubingConfiguration.Installed` / `ExplicitlyNone(reason)` | Production tubing is the default; "no tubing" must be explicit, so missing data is never mistaken for a tubingless completion. |
| `DataProvenance` on every spec and catalog item | Synthetic, user-entered, catalog-verified and measured values are always distinguishable; nominal values are never silently treated as measured. |
| Schematic as a pure scene graph + projection | Geometry (casing ends at shoe, cement outer boundary, rathole, liner overlap) is resolved and tested in JVM code; the Compose renderer only maps coordinates. Path uses one isotropic scale; radii use a separate exaggerated scale; the stratigraphic column uses the same TVD-below-GL scale as the well, so boundaries align. |
| Room storing a versioned JSON document per project | The well aggregate is deep and always loaded whole; a document column keeps the schema small and migrations explicit (`WellProjectCodec`). Decoding is strict, newer schema versions are rejected instead of truncated. |
| Manual DI (`AppContainer`) | Avoids adding Hilt/Koin before it is needed; easy to replace later. |
| No animation | Schematic, CT and BHA positions are pure functions of the input; navigation transitions are disabled too. |
| Backup disabled | `allowBackup=false` and data-extraction rules exclude all data, because projects may hold confidential well data. |

### Datum conventions
- All datums resolve to an elevation above MSL. `depth_T = depth_S + (elev(T) − elev(S))`.
- Exact for TVD. For MD it relies on the hole between the two datums being vertical, which holds for datums at/above the wellhead (RKB, GL, CT stack datums). MD relative to MSL is therefore not allowed.
- The schematic's vertical axis is **TVD below GL**; GL = 0 is the top of the stratigraphic column.
- CT depth convention (initial): counter zeroed with the BHA nose at the operation datum, so the CT depth is the BHA nose depth. Changing the depth reference keeps the tool's physical position and converts the displayed number.

### Build policy and toolchain
- Gradle 8.14.3 (wrapper), AGP 8.11.1, Kotlin 2.2.21, KSP 2.2.21-2.0.4, Compose BOM 2025.09.00, Room 2.7.2, Navigation 2.9.5, compileSdk/targetSdk 36, minSdk 26, JDK 17+.
  This is a conservative, mutually compatible set; upgrading to AGP 9.x is a planned follow-up.
- **APK/AAB packaging of `:app` is blocked** by a guard in the root `build.gradle.kts` until the owner approves an APK build. Compilation and unit tests are not affected. After approval the owner can run e.g. `./gradlew :app:assembleDebug -Pwellsketch.ownerApprovedApkBuild=true`.
- CI (`.github/workflows/ci.yml`) runs `./gradlew test` only (compiles all modules and runs all unit tests). It never assembles, signs or uploads an APK.
- Run tests locally: `./gradlew test`.

## Milestone 1 status

**Complete (implemented and unit-tested in pure Kotlin)**
- Multi-module project, convention plugins, version catalog, APK guard, CI workflow.
- Engineering data models listed above, with JSON persistence round-trip tests.
- Datum conversion between RKB, GL, MSL and per-operation datums.
- Survey → trajectory by the minimum curvature method (tested against exact straight-line and circular-arc geometry), MD→TVD interpolation, vertical-section projection, flagged straight projection below the last station, flagged vertical assumption when no survey exists.
- Geometry consistency checks (ordering, nesting, liner overlap, shoe vs hole, cement interval, tubing/components/perforations placement).
- Schematic scene for vertical, J, S and horizontal wells: nested casing walls ending at shoes, cement with correct outer boundary (previous casing ID / rathole / open hole), tubing, completion components, perforations, liner tops, formation bands aligned to GL = 0, Christmas Tree stack; projection and hit testing.
- CT: flow path (tubing by default; casing/liner/open hole when tubingless or below end of tubing), restriction list (drift → nominal ID with warning, component minimum IDs, blocking plugs), geometric passage check, CT/BHA placement from the chosen datum, CT string length check, pressure-control stack checks (connection continuity, bore vs CT/BHA, rating vs entered max wellhead pressure), full and compact surface layouts.

**Implemented, but compiled only in CI (no Android SDK in the authoring environment)**
- Room persistence, synthetic demo seeding, ordered autosave.
- Compose UI: home screen with project selector and unit system, Well Design (live editable sketch, responsive phone/tablet layout, right-side data panel, tap-to-select detail card), CT Calculations (depth reference per operation, depth field/slider/step buttons, full/compact surface view, placement/flow path/restrictions/stack/BHA panels), Well Calculations list, Equipment Library with provenance.

**Placeholders**
- Well Calculations and CT calculations: specification catalog only; no formulas.
- Equipment Library: synthetic placeholder entries only; no real catalog data.
- Component breakdown views: not implemented; will use owner-approved reference images only.
- Detailed final well illustration: not implemented (separate from the live sketch).
- Drilling rig/BOP surface drawing: type exists, drawing not implemented.

**Needs engineering validation by the owner**
- Minimum curvature implementation and the straight projection below the last survey station.
- Provisional CT stack rules: stripper on top, at least one BOP, BHA must pass every component except the stripper, tree cap removed for rig-up.
- CT depth convention (counter zero with BHA nose at the datum).
- Use of nominal bit size as open-hole diameter; perforation symbols are not to scale.
- Lithology colours/patterns of the stratigraphic column.

**Remaining work (next milestones)**
- Specifications and tests for capacities, displacements, hydrostatics, cementing and CT pumping/forces.
- Validated tubular/equipment datasets with catalog provenance and import tooling.
- Full editors (add/remove strings, sections, components, surveys, survey import), tapered strings, caliper data, offshore datums (water depth, mudline).
- Detailed component views, final well illustration, rig/BOP drilling view.
- Add `distributionSha256Sum` to the wrapper properties.
- UI tests (Compose) and screenshot tests.
