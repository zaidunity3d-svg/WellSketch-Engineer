// Extracts named top-level declarations from the HTML prototype script (passed as argv[2]).
const fs = require('fs');
const src = fs.readFileSync(process.argv[2], 'utf8');
function block(startIdx) {
  let i = src.indexOf('{', startIdx), depth = 0, q = null;
  for (; i < src.length; i++) {
    const c = src[i];
    if (q) { if (c === '\\') { i++; continue; } if (c === q) q = null; else if (q === '`' && c === '$' && src[i+1] === '{') { /* nested expr: count braces normally */ } continue; }
    if (c === '"' || c === "'" ) { q = c; continue; }
    if (c === '{') depth++;
    else if (c === '}') { depth--; if (depth === 0) return src.slice(startIdx, i + 1); }
  }
  throw Error('unbalanced at ' + startIdx);
}
const fns = process.argv[3].split(',');
let out = '';
for (const name of fns) {
  const m = src.match(new RegExp('\\nfunction ' + name + '\\('));
  if (!m) throw Error('missing ' + name);
  out += block(m.index + 1) + '\n';
}
for (const name of (process.argv[4] || '').split(',').filter(Boolean)) {
  const m = src.match(new RegExp('\\n(?:const|let) ' + name + '=[^\\n]*'));
  if (!m) throw Error('missing const ' + name);
  out += m[0].trim().replace(/^let /, 'var ').replace(/^const /, 'var ') + '\n';
}
process.stdout.write(out);
