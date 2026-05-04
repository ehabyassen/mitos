# 0001 — Render the graph with Cytoscape.js inside JBCefBrowser

**Status:** Accepted (2026-05-04)

## Context
The SRS requires a rich, interactive, draggable graph (FR-17..21d) with
shape-per-kind nodes, vibrant theme-aware colours, animated layout switches,
mini-map, and tooltips showing source snippets. We considered:

1. **Swing/Java2D + GraphStream/JGraphT** — full control, but recreating
   smooth physics, gradients, drop shadows, and four polished layouts is
   weeks of UI work, and Swing's repaint pipeline struggles with hundreds
   of animated nodes.
2. **JCEF + a JS graph library (Cytoscape.js, vis-network, sigma.js)** —
   ships rich rendering for free. Cytoscape.js has a permissive MIT licence,
   active maintenance, and battle-tested layout extensions (`dagre`,
   `cose-bilkent`).
3. **JavaFX WebView** — JavaFX is no longer bundled with the JBR; pulling
   it in as an extra runtime contradicts NFR-3 (startup overhead) and the
   "no surprise dependencies" spirit of the SRS.

## Decision
Embed Cytoscape.js inside `JBCefBrowser`. Bundle a pinned Cytoscape.js plus
`dagre`, `cose-bilkent`, and `popper` extensions under
`src/main/resources/web/lib/`, downloaded at *build* time (`downloadWebDeps`
Gradle task) so the runtime is offline (NFR-12, NFR-13). Wire JS↔Kotlin
through two `JBCefJSQuery` callbacks: `__mitosNavigate` for source
navigation (FR-20) and `__mitosExport` for PNG/SVG/Mermaid output (FR-24/25).

## Consequence
- Visualisation polish (gradients, shadows, animations, mini-map) maps
  directly to Cytoscape primitives — the renderer's complexity is in
  *configuration*, not custom drawing.
- The plugin requires a JCEF-enabled IDE build. We detect this at runtime
  (`JBCefApp.isSupported()`) and degrade gracefully with an explanatory
  label (NFR-5) — no crashes.
- The build needs internet access on first build to fetch the JS deps;
  subsequent offline builds work because `processResources` only re-fetches
  missing files.
- Cytoscape.js is ≈ 350 KB; total bundled JS ≈ 700 KB — small relative to
  IntelliJ's footprint.
