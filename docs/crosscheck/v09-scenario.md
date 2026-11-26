# Cross-check: HTML v0.9 prototype vs Android (synthetic well)

The owner's v0.9 HTML prototype is a **reference prototype, not a validated calculation
system**. Running its own functions on the same well as the Android code gives a second,
independent implementation to compare against. When the two agree, both use the same
geometry. That does not make either one field-approved.

## How to re-run
```
node extract.js /path/to/v09.js \
  capacityM3m,volume,sectionSpecAt,findSpec,casingHostAt,cementVolume,ctSegmentAt,ctGeometry,ctSpec,ctDim,ctStartMD,ctCap,ctRound \
  "CATALOG,inch,F,BBL,CT_DIM" > htmlcalc.js
node scenario.js > html_results.json
```
`v09.js` is the `<script>` content of `WellSketch-Well-Designer-v0.9.html` (owner's Drive). It is
not stored in this repository.

## Synthetic well
- GL MD 11.1 m.
- Holes 26 / 17-1/2 / 12-1/4 / 8-1/2 / 6-1/8 in, to 60 / 600 / 1950 / 2800 / 3300 m MD.
- Casing from GL: 20 in 94 lb/ft, 13-3/8 in 54.5 lb/ft, 9-5/8 in 47 lb/ft and 7 in 26 lb/ft.
- 5-1/2 in 17 lb/ft liner from 2650 to 3300 m.
- 3-1/2 in 9.2 lb/ft tubing to 2600 m.
- Cement jobs (TOC / tail top / float collar / open excess / cased excess):
  - 13-3/8 in: GL / 400 / 575 / 100 % / 0 %
  - 9-5/8 in: 1200 / 1700 / 1925 / 30 % / 0 %
  - 7 in: 1800 / 2500 / 2775 / 20 % / 5 %
  - liner: 2650 / 3000 / 3275 / 25 % / 0 %
- CT: 1-3/4 in × 0.156 in, nozzle at 3000 m.

## Result
All of the following agree to 1e-9 m³ or better:
- inside volumes (tubing, each casing, liner);
- annuli between named strings and to the open hole;
- cement open-hole and cased-hole annulus, lead and tail, shoe track, slurry total and
  displacement to the float collar;
- flow path to 3000 m;
- CT annulus to the nozzle (12.348525 m³ in both).

The tests are in `domain/welldesign/src/test/.../HtmlV09EditorCrossCheckTest.kt`. The well in
those tests is built through the editor functions, so the user's path is covered: catalogue,
then structure edits, then calculators.

## Differences found (conventions, not arithmetic)
| # | Topic | HTML v0.9 | Android | Status |
|---|---|---|---|---|
| 1 | Displacement when no float collar | Not calculated | Taken to the shoe, with a warning | Kept; owner to confirm |
| 2 | CT internal volume for time to nozzle | Deployed CT length (+ surface line) | Whole string on the reel (reel + well) | Kept: fluid must fill the reel. Owner to confirm |
| 3 | CT volumes start | Wellhead flange MD (RKB − flange height) | Tubing hanger / conduit top; tree and stack bore excluded and reported | Owner to confirm |
| 4 | CT annulus | CT OD only | CT OD, plus each BHA component's OD | Android more complete |
| 5 | Tubing annulus | User interval; packer ignored | Hanger to the top of the shallowest packer | Android more complete |
| 6 | Equivalent density | SG × 8.3454 ppg; TVD typed by the user | Exact unit definitions (1 SG = 8.345404 ppg); TVD from the survey, referenced to RKB | Difference < 4e-6 ppg; datum to confirm |
| 7 | Excess limits | Open 0–300 %, cased 0–200 % | Both 0–300 % | Minor |
| 8 | Conductor | Separate object with stick-up; ID required for cased volumes | Ordinary casing string; no stick-up above GL | Stage 3 |
| 9 | Tapered strings | Several size/weight intervals per string | One size per string | Stage 2 (model change) |
| 10 | Excess per job | Per cement job | One open-hole and one cased-hole value for all jobs | Owner to choose |
| 11 | New well surface equipment | Wellhead type pick (tree / suspended / BOP / none) | A new well has no wellhead or tree yet. The CT screen reports CT_NO_TREE and the sketch has no surface drawing | **Gap** – needs a wellhead/tree editor; no ratings invented |

## QA rules taken over from v0.9 `qa()` (now in Android)
- `HOLE_GAP` (error): a gap between hole sections.
- `HOLE_FIRST_TOP` (warning): the first hole section starts below GL.
- `CASING_DRIFT` (warning): the inner string OD fits the outer ID but not the outer drift.
- `TUBULAR_CATALOGUE_KIND` (error): a casing record is used for tubing, or the reverse.
- `TUBULAR_GRADE_CONNECTION` (info): grade or connection not selected.
- `CEMENT_TAIL_OUTSIDE` (warning): the tail top is not inside the cement column.
