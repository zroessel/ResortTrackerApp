#!/usr/bin/env node
// Builds data/resorts.json from the OpenSkiMap ski area export.
//
//   1. Download https://tiles.openskimap.org/csv/ski_areas.csv to data/raw/ski_areas.csv
//   2. node scripts/build-resorts.js
//
// Keeps named, operating ski areas that have downhill skiing. Resorts from the
// original hand-curated list (resorts.txt) are matched by name so their
// descriptions and visited/wishlist marks carry over into data/progress.default.json.
//
// Data © OpenStreetMap contributors / OpenSkiMap, licensed under the ODbL.

const fs = require('fs');
const path = require('path');
const parseCSV = require('./csv');

const ROOT = path.join(__dirname, '..');
const RAW = path.join(ROOT, 'data', 'raw', 'ski_areas.csv');
const LEGACY = path.join(ROOT, 'resorts.txt');
const OUT_RESORTS = path.join(ROOT, 'data', 'resorts.json');
const OUT_PROGRESS = path.join(ROOT, 'data', 'progress.default.json');

const CONTINENT = {
  'North America': ['United States', 'Canada', 'Mexico', 'Greenland'],
  'South America': ['Argentina', 'Chile', 'Brazil'],
  'Europe': ['Albania', 'Andorra', 'Austria', 'Belarus', 'Belgium', 'Bosnia and Herzegovina', 'Bulgaria',
    'Croatia', 'Cyprus', 'Czechia', 'Denmark', 'Estonia', 'Finland', 'France', 'Germany', 'Greece', 'Hungary',
    'Iceland', 'Ireland', 'Italy', 'Kosovo', 'Latvia', 'Liechtenstein', 'Lithuania', 'Montenegro', 'Netherlands',
    'North Macedonia', 'Norway', 'Poland', 'Portugal', 'Romania', 'Russia', 'Serbia', 'Slovakia', 'Slovenia',
    'Spain', 'Sweden', 'Switzerland', 'Ukraine', 'United Kingdom'],
  'Asia': ['Armenia', 'Azerbaijan', 'China', 'Georgia', 'India', 'Iran', 'Israel', 'Japan', 'Kazakhstan',
    'Kyrgyzstan', 'Lebanon', 'Mongolia', 'North Korea', 'Pakistan', 'South Korea', 'Taiwan', 'Tajikistan',
    'Turkey', 'Uzbekistan'],
  'Africa': ['Egypt', 'Lesotho', 'Morocco', 'South Africa'],
  'Oceania': ['Australia', 'New Zealand'],
  'Antarctica': ['Antarctica'],
};
const continentOf = Object.fromEntries(Object.entries(CONTINENT).flatMap(([k, cs]) => cs.map(c => [c, k])));

// Country names used in the original curated list → OpenSkiMap names
const LEGACY_COUNTRY = { USA: 'United States' };

const norm = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '');
const isLatin = s => /^[\p{Script=Latin}\p{N}\p{P}\p{Zs}'’&+]+$/u.test(s);
const num = v => (v === '' || v == null ? null : Math.round(Number(v)));
const list = v => (v ? v.split(';').map(s => s.trim()).filter(Boolean) : []);

// OSM names often list alternates: "祇園山スキー場, Gionyama Ski Area". Show the
// first Latin-script one and keep the rest as search aliases.
function pickName(raw) {
  const parts = raw.split(',').map(s => s.trim()).filter(p => p && !/^Q\d+$/.test(p));
  const display = parts.find(isLatin) || parts[0] || raw.trim();
  const aka = [...new Set(parts.filter(p => p !== display))];
  return { display, aka };
}

const rows = parseCSV(fs.readFileSync(RAW, 'utf8'))
  .filter(r => r.has_downhill === 'yes' && r.status === 'operating' && r.name.trim());

const all = rows.map(r => {
  const { display, aka } = pickName(r.name);
  const countries = list(r.countries);
  const resort = {
    id: r.id,
    name: display,
    country: countries[0] || 'Unknown',
    regions: list(r.regions),
    continent: continentOf[countries[0]] || 'Other',
    lat: +Number(r.lat).toFixed(4), lng: +Number(r.lng).toFixed(4),
    lifts: num(r.lift_count),
    runKm: num(r.downhill_distance_km),
    vertical: num(r.vertical_m),
    top: num(r.max_elevation_m),
    web: list(r.websites.replace(/\s+/g, ';'))[0] || null,
  };
  if (countries.length > 1) resort.countries = countries;
  if (aka.length) resort.aka = aka;
  return resort;
});

// ── Merge duplicates ─────────────────────────────────────────────────────────
// OSM sometimes maps the same area twice (e.g. a point and a polygon). Treat two
// entries with the same name, country and region within 5 km as one, keeping
// the more complete record.
const km = (a, b) => {
  const R = 6371, rad = Math.PI / 180;
  const dLat = (b.lat - a.lat) * rad, dLng = (b.lng - a.lng) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
};
const score = r => (r.lifts || 0) * 10 + (r.runKm || 0) + (r.web ? 1 : 0);
const groups = new Map();
for (const r of all) {
  const key = `${norm(r.name) || r.name}|${r.country}|${r.regions.join(';')}`;
  (groups.get(key) || groups.set(key, []).get(key)).push(r);
}
const resorts = [];
let merged = 0;
for (const group of groups.values()) {
  group.sort((a, b) => score(b) - score(a));
  const kept = [];
  for (const r of group) {
    if (kept.some(k => km(k, r) < 5)) { merged++; continue; }
    kept.push(r);
  }
  resorts.push(...kept);
}

// ── Carry over the curated list ──────────────────────────────────────────────
const legacy = fs.existsSync(LEGACY)
  ? fs.readFileSync(LEGACY, 'utf8').split(/\r?\n/).filter(Boolean).map(l => {
      const p = l.split(',');
      return { name: p[0].trim(), country: LEGACY_COUNTRY[p[1].trim()] || p[1].trim(),
               description: p[3].trim(), visited: p[4].trim() === 'true', wishlist: (p[5] || '').trim() === 'true' };
    })
  : [];

const progress = { visited: [], wishlist: [] };
const unmatched = [];
for (const old of legacy) {
  const k = norm(old.name);
  const cands = resorts.filter(r => (r.countries || [r.country]).includes(old.country)).filter(r => {
    const n = norm(r.name);
    return n === k || n.includes(k) || (n.length > 4 && k.includes(n));
  });
  // Prefer an exact name match, then the biggest area (most lifts)
  cands.sort((a, b) => (norm(b.name) === k) - (norm(a.name) === k) || (b.lifts || 0) - (a.lifts || 0));
  const hit = cands[0];
  if (!hit) { unmatched.push(`${old.name} (${old.country})`); continue; }
  if (old.description && !hit.description) hit.description = old.description;
  if (old.visited) progress.visited.push(hit.id);
  if (old.wishlist) progress.wishlist.push(hit.id);
}

// Sort ignoring leading quotes/punctuation so '"Amirsay" Mountain Resort' files under A
const sortKey = s => s.replace(/^[^\p{L}\p{N}]+/u, '');
resorts.sort((a, b) => sortKey(a.name).localeCompare(sortKey(b.name), 'en'));
fs.writeFileSync(OUT_RESORTS, JSON.stringify(resorts));
fs.writeFileSync(OUT_PROGRESS, JSON.stringify(progress, null, 2) + '\n');

const countries = new Set(resorts.map(r => r.country));
console.log(`Merged ${merged} duplicate entries`);
console.log(`Wrote ${resorts.length} resorts in ${countries.size} countries → ${path.relative(ROOT, OUT_RESORTS)}`);
console.log(`Matched ${legacy.length - unmatched.length}/${legacy.length} curated resorts; ` +
            `${progress.visited.length} visited, ${progress.wishlist.length} wishlisted carried over`);
if (unmatched.length) console.log('Not found:', unmatched.join(', '));
const other = [...countries].filter(c => !continentOf[c]);
if (other.length) console.log('No continent for:', other.join(', '));
