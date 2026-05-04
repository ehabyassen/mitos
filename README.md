# Mitos

> *Mitos* (Greek **μίτος**, "thread") — Ariadne's thread through the Labyrinth.
> The thread through your codebase.

A bidirectional cross-language call-graph visualizer for IntelliJ IDEA Ultimate.
Place the caret on a method, JSP page, or JavaScript function and press
**Ctrl+Alt+Shift+G** — Mitos draws the graph of callers (above) and callees
(below), tracing references across Java ⇄ JSP ⇄ JavaScript boundaries.

## Highlights
- **Cross-language**: JSP scriptlets/EL/taglibs, Spring MVC view resolution,
  `RequestDispatcher.forward`, `<script src>` and inline event handlers,
  best-effort AJAX URL → `@RequestMapping` matching.
- **Rich visualisation**: Cytoscape.js renderer with shape-per-kind nodes,
  vibrant theme-aware palette, draggable nodes with smooth physics,
  pulsing root, focus mode, mini-map, legend, search filter, four
  switchable layouts, animated transitions.
- **No network at runtime**: all assets bundled. No telemetry.

## Requirements
- IntelliJ IDEA **Ultimate** 2024.2 or later (JSP PSI is Ultimate-only).
- JDK 17+ for building.

## Build

First-time bootstrap (only if `gradlew` is missing — needs a system Gradle 8.x or newer once):

```bash
gradle wrapper --gradle-version 8.10.2
```

Then:

```bash
./gradlew buildPlugin
# Result: build/distributions/mitos-1.0.0.zip
```

The `downloadWebDeps` task fetches Cytoscape.js and its extensions (versions
pinned in `gradle.properties`) into `build/web-deps/`. They are bundled into
the plugin JAR by `processResources`.

## Run from sources

```bash
./gradlew runIde
```

Launches a sandbox IDE with Mitos preloaded.

## Install

1. Build the zip (`./gradlew buildPlugin`).
2. In IntelliJ: **Settings → Plugins → ⚙ → Install Plugin from Disk…**, pick
   the zip from `build/distributions/`.
3. Restart the IDE.

## Usage

1. Open a Java method, JSP page, or JS function.
2. Press **Ctrl+Alt+Shift+G** (or **right-click → Mitos: Show Call Graph**).
3. The Mitos tool window opens on the right with the graph.

### Tool-window controls
- **Callers / callees sliders** (0–5): change traversal depth — recomputes live.
- **Layout**: Hierarchical (dagre), Force-directed (cose-bilkent), Concentric, Grid.
- **Search**: fade non-matching nodes.
- **Refresh**: re-resolve from the current caret + reset drag positions.
- **Export PNG / SVG / Mermaid**: save the rendered view.
- **Settings**: open the Mitos preferences pane.

### Graph interaction
- **Drag a node** — physics relaxes the rest; positions persist until Refresh.
- **Pan**: drag empty canvas. **Zoom**: mouse wheel; `Ctrl+0` fit; `Ctrl+±` step.
- **Hover** — focus mode highlights neighbours; tooltip shows file:line.
- **Hover an edge** — tooltip shows the source code snippet of the call site.
- **Double-click a node** — jump to source in the editor.
- **Right-click** — context menu (go to source, find usages, hide, expand).

## Settings

`Settings → Tools → Mitos`:
- **View resolver prefix / suffix** — Spring MVC view name → JSP file (default `/WEB-INF/views/` + `.jsp`).
- **Package include / exclude regex** — scope filters.
- **JSP / JavaScript analyzer** toggles.
- **Max nodes** (10–1000) — cap; over the cap the graph is BFS-truncated and a banner explains.
- **Default callers / callees depth** (0–5).
- **Default layout** and **animations** toggle.

Settings persist per-project via `mitos.xml`.

## Visual legend

| Language | Light | Dark |
|---|---|---|
| Java | orange `#E76F00` | `#FFB454` |
| Spring (`@*Mapping`) | green `#6DB33F` | `#9CCC65` |
| JSP | blue `#1565C0` | `#64B5F6` |
| JavaScript | yellow `#F7DF1E` | `#FFEE58` |
| Unresolved | gray `#9E9E9E` | gray |

| Shape | Kind |
|---|---|
| Rounded rectangle | Method / Service |
| Hexagon | Controller mapping |
| Trapezoid (cut-rectangle) | JSP page |
| Ellipse | JS function / Field |

| Edge style | Meaning |
|---|---|
| Solid | Confident resolution (PSI reference) |
| Dashed marching-ants | Heuristic (e.g., AJAX URL match) |

## Troubleshooting

**"No edges shown"**
- Caret on a non-supported element? Place it on a Java method, JSP page, or JS function.
- For JSP/JS edges, check **Settings → Tools → Mitos** that the relevant analyzer is enabled.
- Spring view name not resolving? Check the prefix/suffix in Settings — defaults expect `/WEB-INF/views/` + `.jsp`.
- AJAX URL not matching a controller? The matcher only sees string literals — dynamic `${baseUrl}` concatenations are out of scope.

**"JCEF is not available"**
- Mitos requires a JCEF-enabled IDE build. Install the
  *JetBrains Runtime with JCEF* (the default JBR ships with it).

**Graph truncated banner**
- The traversal hit the max-node cap. Raise *Max nodes* in Settings or
  reduce the callers/callees depth.

## Architecture (one-pager)

```
src/main/kotlin/com/vodafone/mitos
 ├── action            ShowCallGraphAction (FR-1, F1)
 ├── analyzer          LanguageAnalyzer + JavaAnalyzer / JspAnalyzer / JsAnalyzer
 ├── model             CallNode, CallEdge, CallGraph (FR-5)
 ├── resolver          CrossLanguageResolver, SpringMappingIndex, ViewResolver (FR-7..16)
 ├── service           CallGraphService (BFS, async ReadAction), GraphCache (LRU FR-28..30)
 ├── settings          MitosSettings + Configurable (FR-26, FR-27)
 └── ui                MitosToolWindow + GraphPanel (JBCefBrowser)
                       + GraphSerializer + ThemeBridge

src/main/resources
 ├── META-INF/plugin.xml
 ├── messages/MitosBundle.properties      (NFR-7)
 ├── icons/                               Plugin & tool-window icons
 └── web/                                 index.html / mitos.css / mitos.js
     └── lib/                             Bundled Cytoscape.js (downloaded at build)
```

See [docs/SRS.md](docs/SRS.md) for the full requirements specification and
[docs/PROMPT.md](docs/PROMPT.md) for the build prompt.

## Out of scope (v1.0)
Reflection-based call resolution, Spring `@Autowired` graph, SQL/MyBatis
mapper resolution, real-time graph updates as the user types, IntelliJ
Community edition, Thymeleaf / Tiles view resolvers, sequence-diagram view,
shareable graph snapshots.

## License
Internal / Vodafone — see your repository's LICENSE file.
>>>>>>> 8bb9e67 (init fix)
