# Powder — Ski Resort Tracker

Track every ski resort you've visited, out of **4,697 downhill resorts in 74 countries**. Search, filter by country and province/state, sort, build a wishlist, and watch your stats (resorts, countries, continents) update as you check resorts off.

**Live demo:** https://zroessel.github.io/ResortTrackerApp/

![Screenshot](docs/screenshot.png)

## Features

- **Worldwide catalog** — every operating downhill ski area from [OpenSkiMap](https://openskimap.org), with lift count and vertical drop
- **Search** — matches name, alternate/local-language names, country, province and description (e.g. "gionyama" finds 祇園山スキー場)
- **Country → Province / State filters** — the province dropdown lists only the selected country's provinces, with resort counts
- **Sorting** — name A→Z / Z→A, biggest first (by lifts), visited first, unvisited first, or by country (home country first)
- **Wishlist** — star resorts you want to ski, filter to just your wishlist; resorts drop off automatically once visited
- **Home country** — set it on the Profile tab to see home vs. abroad stats; your country is pinned first in the filters and country sort
- **Light & dark mode** — toggle in the top right, remembered between visits
- **Map & Profile tabs** — progress by continent and country, top countries, overall completion
- **Fast with thousands of rows** — the list renders 60 rows at a time and loads more as you scroll; search is debounced and runs against a precomputed index

## Tech stack

| Layer | Tech |
|---|---|
| UI | Vanilla HTML, CSS (custom properties for theming), JavaScript — no framework |
| Desktop shell | Electron (main process + `contextBridge` preload with context isolation) |
| Data pipeline | Node script that turns the OpenSkiMap CSV export into `data/resorts.json` |
| Web build | Same `index.html`, with a fallback data layer using `fetch` + `localStorage` |
| Prototype | Original JavaFX version in [`src/App.java`](src/App.java) |

## Architecture

The UI talks to a small `window.api` interface (`loadCatalog`, `loadProgress`, `saveProgress`), so it doesn't care where data lives. The resort **catalog** (read-only) is kept separate from your **progress** (visited + wishlist resort IDs).

- **Electron:** [`preload.js`](preload.js) exposes `window.api` through `contextBridge`; [`main.js`](main.js) handles the IPC calls, reads the catalog from `data/resorts.json`, and saves progress to the user's app-data folder. The renderer never gets direct Node access.
- **Browser:** if `window.api` isn't present, [`index.html`](index.html) installs a fallback that fetches the catalog over HTTP and stores progress in `localStorage`. This is what powers the GitHub Pages demo.
- **Untrusted data:** resort names come from OpenStreetMap, so they're HTML-escaped before rendering.

### Data pipeline

[`scripts/build-resorts.js`](scripts/build-resorts.js) converts the raw export:

1. Keeps named, operating ski areas with downhill skiing (4,830 of ~12,500 rows)
2. Picks a readable display name from multi-language OSM names and keeps the rest as search aliases
3. Merges duplicates — same name and province within 5 km (133 merged)
4. Maps each country to a continent
5. Matches resorts from the original hand-curated list (`resorts.txt`) by name to carry over descriptions and visited marks

To refresh the data:

```bash
curl -o data/raw/ski_areas.csv https://tiles.openskimap.org/csv/ski_areas.csv
node scripts/build-resorts.js
```

## Running locally

**In the browser**

```bash
npx http-server -p 8080 .
# open http://localhost:8080
```

**As a desktop app**

```bash
npm install
npm start
```

## What I learned

- Separating the UI from storage behind a small interface so one codebase runs as both a desktop app and a static website
- Electron's security model: context isolation and exposing only a minimal IPC surface through a preload script
- Building a repeatable data pipeline: cleaning messy real-world data (duplicates, multilingual names, multi-country resorts)
- Keeping a vanilla-JS UI fast with thousands of records (incremental rendering, debounced search, precomputed search index)
- Porting a JavaFX prototype to web technologies

## Data & license

Resort data © [OpenSkiMap](https://openskimap.org) and [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), available under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/). `data/resorts.json` is a derived database and is likewise shared under the ODbL.
