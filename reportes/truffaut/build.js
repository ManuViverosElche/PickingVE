// Regenera data.json: fusiona _lines.json con los pedidos y calcula paletización 100%.
// Modelo de capacidad (uds/palet EUR 120x80) — ver docs/SPECS.md D-88.
const fs = require('fs');
const path = require('path');
const dir = __dirname;

const CAP = {
  '':1, '5L':30, '7':30, 'T20':30, 'TER':16,
  '10L':24, '15L':12, '16+':12, '20':12, '25L':12,
  '30L':8, '35L':6, '40+':4, '45L':4,
  '56':2, '60':2, '65L':1, '67':2, '70L':2,
  '89':1, '100':1, '113':1, '130':1, '170':1, '180':1, '230':1, '285':1,
  '<2M':2, 'SIM':2, 'MAL':2, 'T55':4, 'T72':2
};
const capOf = l => CAP[l] !== undefined ? CAP[l] : 1;

const data = JSON.parse(fs.readFileSync(path.join(dir, 'data.json'), 'utf8'));
const raw = JSON.parse(fs.readFileSync(path.join(dir, '_lines.json'), 'utf8'));

const byOrder = {};
for (const row of raw) byOrder[row.n] = JSON.parse(row.js);

let missing = 0;
for (const o of data.orders) {
  const ls = byOrder[o.n];
  if (!ls) { missing++; o.lin = []; o.pal100 = o.pal; continue; }
  o.lin = ls.map(L => {
    const cap = capOf(L.l);
    const np = L.u > 0 ? Math.max(1, Math.ceil(L.u / cap)) : 0;
    return {...L, cap, np};
  });
  o.pal100 = o.lin.reduce((s, L) => s + L.np, 0);
}

data.model = {
  rule: '100% paletizado — nada al suelo (D-88)',
  cap: CAP
};

data.generated = new Date().toISOString().slice(0, 10);
fs.writeFileSync(path.join(dir, 'data.json'), JSON.stringify(data, null, 2));
console.log('OK orders=' + data.orders.length + ' missing=' + missing);
for (const o of data.orders) {
  console.log(o.n, 'pal=' + o.pal, 'pal100=' + o.pal100, 'lin=' + o.lin.length);
}
