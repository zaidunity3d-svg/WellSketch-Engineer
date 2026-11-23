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

See `CLAUDE.md` for coding-agent instructions (to be added).
