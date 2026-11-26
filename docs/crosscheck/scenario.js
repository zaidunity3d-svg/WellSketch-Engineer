// Synthetic cross-check well (not real data). Depths in m MD below RKB; GL MD = 11.1.
// Usage: node extract.js <path to v0.9 script> <functions> <constants> > htmlcalc.js; node scenario.js
// (see v09-scenario.md). The v0.9 prototype itself is not stored in this repository.
const vm = require('vm'), fs = require('fs');
const ctx = {}; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(__dirname + '/htmlcalc.js', 'utf8'), ctx);
const seg = (top, bottom, id) => ({ id: 'seg-' + id + '-' + top, top, bottom, catalogId: id, grade: 'Not selected', connection: 'Not selected' });
const str = (id, name, kind, catalogId, top, shoe) => {
  const c = ctx.CATALOG.find(x => x.id === catalogId);
  return { id, name, kind, od: c.od, idIn: c.id_in, top, shoe, segments: [seg(top, shoe, catalogId)] };
};
const GL = 11.1;
const w = {
  datum: GL, groundMSL: 20, td: 3300, tvd: 3300,
  conductor: { enabled: false },
  holes: [
    { id: 'h1', role: 'Conductor bore', top: GL, bottom: 60, diameter: 26 },
    { id: 'h2', role: 'Surface', top: 60, bottom: 600, diameter: 17.5 },
    { id: 'h3', role: 'Intermediate', top: 600, bottom: 1950, diameter: 12.25 },
    { id: 'h4', role: 'Production', top: 1950, bottom: 2800, diameter: 8.5 },
    { id: 'h5', role: 'Liner', top: 2800, bottom: 3300, diameter: 6.125 },
  ],
  strings: [
    str('cond', 'Conductor', 'casing', 'c-20-94-api', GL, 60),
    str('surf', 'Surface casing', 'casing', 'c-13p375-54p5-api', GL, 600),
    str('int', 'Intermediate casing', 'casing', 'c-9p625-47-api', GL, 1950),
    str('prod', 'Production casing', 'casing', 'c-7-26-api', GL, 2800),
    str('lin', 'Production liner', 'liner', 'c-5p5-17-api', 2650, 3300),
    str('tbg', 'Production tubing', 'tubing', 't-3p5-9p2-nu', GL, 2600),
  ],
  equipment: [], formations: [],
};
const r = {};
const V = (...a) => ctx.volume(w, ...a).m3;
r.inside_int = V('internal', 'int', null, GL, 1950);
r.inside_tbg = V('internal', 'tbg', null, GL, 2600);
r.inside_prod_to_shoe = V('internal', 'prod', null, GL, 2800);
r.inside_lin = V('internal', 'lin', null, 2650, 3300);
r.ann_tbg_prod = V('annulus', 'tbg', 'prod', GL, 2600);
r.ann_int_surf = V('annulus', 'int', 'surf', GL, 600);
r.ann_int_hole = V('annulus', 'int', 'h3', 600, 1950);
r.ann_prod_int = V('annulus', 'prod', 'int', GL, 1950);
r.ann_prod_hole = V('annulus', 'prod', 'h4', 1950, 2800);
r.ann_lin_prod = V('annulus', 'lin', 'prod', 2650, 2800);
r.ann_lin_hole = V('annulus', 'lin', 'h5', 2800, 3300);
const jobs = {
  int: { stringId: 'int', tocMD: 1200, tailTopMD: 1700, openHoleExcessPct: 30, casedHoleExcessPct: 0, floatCollarMD: 1925 },
  prod: { stringId: 'prod', tocMD: 1800, tailTopMD: 2500, openHoleExcessPct: 20, casedHoleExcessPct: 5, floatCollarMD: 2775 },
  lin: { stringId: 'lin', tocMD: 2650, tailTopMD: 3000, openHoleExcessPct: 25, casedHoleExcessPct: 0, floatCollarMD: 3275 },
  surf: { stringId: 'surf', tocMD: GL, tailTopMD: 400, openHoleExcessPct: 100, casedHoleExcessPct: 0, floatCollarMD: 575 },
};
for (const [k, j] of Object.entries(jobs)) {
  const c = ctx.cementVolume(w, j);
  r['cem_' + k] = { lead: c.lead, tail: c.tail, open: c.openVolume, cased: c.casedVolume, shoeTrack: c.shoeTrack, total: c.total, displacement: c.displacement,
    rows: c.rows.map(x => [x.a, x.b, x.host, x.kind, x.d, +x.vol.toFixed(6)]) };
}
// CT: completed well, production casing 7 in 26 lb to 2800, liner 5-1/2 17 lb 2650-3300, tubing 3-1/2 9.2 lb to 2600.
ctx.ct = { td: 3300, layout: 'liner', caseSpec: 'c-7-26-api', linerSpec: 'c-5p5-17-api', linerTop: 2650, linerBottom: 3300, casingShoe: 2800, tubing: 't-3p5-9p2-nu', tubingEnd: 2600, perfsTop: 3100, perfsBottom: 3150 };
ctx.ctCalc = { ctDim: 3, ctDepth: 3000, ctLength: 3000, targetMD: 3000, rkbHeightGL: GL, wellheadHeightGL: 0 };
vm.runInContext('var ct = this.ct, ctCalc = this.ctCalc;', ctx);
const g = (a, b, m) => { const v = vm.runInContext(`ctGeometry(${a},${b},'${m}')`, ctx); return { m3: v.m3, errors: v.errors, segs: v.segments.map(s => [s.top, s.bot, s.label, s.bore]) }; };
r.ct_active = g(GL, 3000, 'active');
r.ct_annulus = g(GL, 3000, 'ct-annulus');
r.ct_occupied = g(GL, 3000, 'ct-occupied');
r.ct_tubing_annulus = g(GL, 3000, 'tubing-annulus');
r.ct_inner_per_m = vm.runInContext('ctCap(ctDim().id)*0.158987294928', ctx);
console.log(JSON.stringify(r, null, 1));
