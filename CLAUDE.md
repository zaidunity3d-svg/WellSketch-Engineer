# CLAUDE.md — WellSketch Engineer

This repository is for **WellSketch Engineer**, a standalone, English-only Android application for drilling, workover/completion, and coiled tubing engineering. It is NOT the Oil Money Unity game.

## Work protocol
1. Inspect the repository, current branch, tests, and project structure before proposing edits.
2. Do not invent real well data, manufacturer dimensions, standards, test results, or equipment ratings. Tag example data clearly. Treat images as visual references, not as engineering authority.
3. Implement on feature branches. Explain changes and validation in pull requests; do not merge to main without user review.
4. Do not build/sign/export an APK until the owner explicitly says **"ابني التطبيق"**.
5. Keep proprietary EOWR, operator reports, full confidential well data, licensed standards, keys, tokens, and customer records out of this public GitHub repository. Use sanitized synthetic fixtures for tests.

## Product modules
- **Well Design:** Editable well schema and dynamic cutaway cross-section, well sections, formations, casing and completion geometry, directional survey-driven Vertical, J, S, Horizontal trajectories.
- **Well Calculations:** Geometry, inside and annular capacities, displacements, hydrostatics, cementing and other verified engineering formulas. Version and unit-test calculation functions separately from UI.
- **CT Calculations:** Quick completed-well setup with production casing/optional liner; production tubing by default, unless the completion explicitly contains no tubing; packer/perforations/tools; configurable CT BHA; CT pumping and capacity calculations.

## Visual requirements
- Main casing depiction: full longitudinal half-cut along well path, showing individual casing strings ending at their actual shoes, relevant cement annuli, holes, tubing, and fluid spaces. Keep global scene legible.
- Selecting a casing/shoe/component opens an engineering detail view with its actual geometry and equipment library info.
- Packer, ESP, bridge plug, CT BHA and other downhole tools use intact 3D-like external appearances in the main scene. On tap, expose component breakdowns from approved user-supplied reference images; don't substitute arbitrary internals.
- Stratigraphic column is on the **left**, integrated into the well sketch. Its first top aligns precisely with GL=0 surface; each formation boundary aligns with the well cross-section at the same elevation.
- Center/main area should prioritize the full well; right-side panels show useful editable well data; English text only. Approved infographic style favors portrait 9:16 for static reference imagery, while UI must be responsive on Android.
- Sketch updates immediately when users change well geometry or completion inputs. The richly detailed final well illustration is distinct from the live editable sketch and must use entered complete data.
- MD, TVD, RKB, GL and MSL are distinct references; never mix them. Use actual surveys for trajectory shapes and calculate/decode depths correctly.

## Coiled tubing surface/geometry
- CT mode shows a Christmas Tree and the CT surface stack, not a drilling-rig configuration.
- Full view: CT reel, guide arch/gooseneck, injector, control cabin, power pack, stripper/packoff, pressure control, tree/wellhead where included.
- Compact view: injector, CT pressure-control stack, Christmas Tree and downhole completion.
- Compose CT pressure-control components individually (BOP stack, stripper, riser/adapters, etc.), validating bore, connection, pressure rating and actual order/configuration.
- Reference datum may be chosen **per operation** with explicit offsets and converted MD.
- CT defaults to running inside existing production tubing. If no tubing in completion, use production casing or liner as appropriate. Validate smallest restriction for CT and BHA.
- CT and BHA depth/position change instantly with input, **without animation**.

## Engineering QA
- Derive tubular ID, wall and weights from validated API/manufacturer datasets with specific catalog provenance and units. Never silently substitute nominal values for actual measured values.
- Distinguish as-designed, as-built, and current completion states. A rig, drilling BOP, and Christmas Tree must not coexist incorrectly for one operating state.
- Keep calculations deterministic and unit-tested, including edge cases, datum conversions, casing overlaps, annular volumes, and pressure checks.
- Favor maintainable native Android architecture; propose implementation choices (e.g., Kotlin/Compose and local persistence) before introducing major dependencies.
