# Workover module (v1)

This module was built to the owner's description. A completed well has its production casing
to surface, or a liner. The wellhead sits at the top, with a workover BOP above it, then the
rig floor. The job runs through drill pipe or tubing, and the user chooses everything.

## What the user controls
- **Job:** operation type (perforation, balanced cement plug, set packer, ESP change,
  acidizing / stimulation, well kill, circulation / clean-out, other), name. A well can hold
  several jobs.
- **Rig and BOP:**
  - the workover RKB height above GL, which is the datum for every workover depth (it is not the drilling RKB);
  - the rig floor below the RKB;
  - the BOP components (type, bore, height). Nothing is filled in by default: no rating is assumed.
- **Work string:** one or more sections from top to bottom (tapered string).
  - Each section is drill pipe or tubing from the catalogue: OD, then weight, then grade, then connection.
  - Tool-joint or coupling OD: the drill-pipe catalogue tool joint by default; the user can overwrite it.
  - The top section reaches the rig floor, and its length is calculated.
- **Tools below the string:** type, name, length, max OD, through bore (0 = closed), order.
- **Depths:** bottom of the string or tools, and the target interval. Defaults come from the
  current perforations.
- **Fluids and pressures:** well fluid density, pumped fluid density, pump rate, reservoir
  pressure, kill overbalance, fracture gradient.
- **Per operation:**
  - perforation: shot density, gun length per run;
  - cement plug: plug length, excess, spacer ahead, under-displacement;
  - acidizing: volume per length, overflush.

## Picture
The workover state (`OperatingState.Workover`) removes the Christmas Tree. It keeps every
wellhead section on the completed well, draws the workover BOP on the top section, and puts
the rig floor at the workover RKB. The production tubing and the equipment mounted on it are
pulled. Equipment set in the casing, and the perforations, stay. The work string and tools are
drawn at their depth and labelled. They move at once when a value changes (no animation).

## Calculations (WorkoverCalculator 1.0.0) — implemented, pending engineering validation
| Result | Formula / rule | Flag |
|---|---|---|
| Inside string, annulus, below string, bore to target | π/4·ID², π/4·(D² − OD²); D = innermost casing/liner ID, else bit size | Nominal body dimensions; tool joints and couplings are not in the volumes |
| Fill when pulling | Steel (open end) π/4·(OD² − ID²); closed end π/4·OD² | Below the casing top only |
| Times | volume ÷ pump rate | No surface lines |
| Hydrostatic, balance, kill density | ρ·g·TVD, TVD below the workover RKB; kill = (P_res + overbalance)/(g·TVD) | Datum convention to confirm (same open item as for EMW) |
| Balanced cement plug | V_s = L·C_bore·(1 + excess); height with pipe in solves ∫(C_ann + C_pipe) = V_s; spacer behind = spacer ahead·C_pipe/C_ann; displacement = pipe volume to (bottom − H) − spacer behind − under-displacement | No contamination, U-tube or pipe-pulling effects |
| Acid | volume per length × interval; displacement = string + bore to the target top; MASP = FG·TVD − ρ·g·TVD | Friction not included |
| Perforation | shots = density × length; runs = ⌈length ÷ gun length⌉; over/underbalance at mid-interval | Gun data from the user |
| Packer | host casing/liner, packer across a liner top or shoe, packer fluid above, volume below | No tubing-movement or force analysis |
| ESP | intake depth, TVD and inclination; motor below the top of the target is flagged | No pump sizing |
| Clearance | Every item against BOP bores, wellhead bores, casing/liner drift (warning) and ID (error), and open hole | Tool-joint OD used where entered |

The tests compare these results with closed-form values computed independently in Python
(`WorkoverCalculatorTest`).

## Drill-pipe catalogue
- 15 API 5DP pipe-body records, 2-3/8 to 5-1/2 in, from
  https://www.drillingmanual.com/api-drill-pipes-specifications/.
- Status: UNVERIFIED. ID = OD − 2·wall is checked.
- The tool joint stored with each row is the source's first E-75 listing, a reference for
  clearance only. The rig's data sheet governs.

## Not covered yet
- Tubing-movement and force analysis.
- Kill sheets (step-down pressure schedule).
- Swab and surge.
- Pump sizing.
- Fluid-loss allowances.
- Completion equipment editor in Well Design: workover keeps equipment already set in the
  casing, but adding it is a stage 2 item.
