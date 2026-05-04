# Build Prompt — Mitos (IntelliJ Plugin)

> **Mitos** (Greek μίτος, "thread") — Ariadne's thread through the Labyrinth.
> *The thread through your codebase.*

## Role
You are an experienced JetBrains plugin developer with a strong eye for data visualization. Build a production-quality IntelliJ IDEA plugin, **Mitos**, that visualizes the bidirectional call graph (callers ⇄ callees) for the symbol under the cursor in the currently open editor, **across language boundaries** that IntelliJ's built-in Call Hierarchy does not handle well: Java ⇄ JSP ⇄ JavaScript. The visualization must be **rich, vibrant, and tactile** — colorful, draggable nodes with smooth animations and clear visual hierarchy.

## Context
- **Target users**: Java developers maintaining a legacy-style web application (Java + JSP + JS + HTML) in IntelliJ IDEA Ultimate.
- **Pain point**: IntelliJ's built-in Call Hierarchy (`Ctrl+Alt+H`) is Java-only and tree-shaped. Following a request from a JSP through a controller into services and back out to client-side JS requires juggling multiple Find Usages windows. Engineers lose context.
- **Differentiator**: A single interactive graph, focused on the symbol under the caret, that follows references across JSP scriptlets/EL/taglibs, Java method calls, Spring MVC view resolution, and JS function calls — rendered with the polish of a modern data-vis tool, not a stock tree view.

## Deliverable
A buildable, installable IntelliJ plugin distributed as a `.zip` via Gradle's `buildPlugin` task, with a documented dev workflow (`./gradlew runIde`).

## Tech Stack (REQUIRED)
- **Language**: Kotlin (JVM 17).
- **Build**: Gradle Kotlin DSL with the **IntelliJ Platform Gradle Plugin 2.x** (`org.jetbrains.intellij.platform`). Do **not** use the legacy `org.jetbrains.intellij` plugin.
- **Target IDE**: IntelliJ IDEA Ultimate 2024.2+ (JSP support requires Ultimate).
- **Plugin SDK APIs**: PSI, `ToolWindow`, `AnAction`, `LangDataKeys`, `ReadAction`, `ProgressManager`, `JBCefBrowser`, `EditorColorsManager` (for theme-aware palettes).
- **Visualization**: Embed Chromium via `JBCefBrowser` and render the graph with **Cytoscape.js** plus the **`cytoscape-dagre`**, **`cytoscape-cose-bilkent`** (force-directed alternative), **`cytoscape-popper`** (tooltips), and **`cytoscape-edgehandles`** extensions, all bundled as static resources under `src/main/resources/web/`. Do not pull JS from a CDN at runtime.
- **Testing**: JUnit 5 + IntelliJ's `BasePlatformTestCase` for PSI-level tests; `LightJavaCodeInsightFixtureTestCase` for Java-side tests.

## Architecture
Organize the code into these packages under `com.vodafone.mitos`:

```
com.vodafone.mitos
 ├── action          # AnAction entry points (editor menu, tool window button)
 ├── analyzer        # Pluggable per-language analyzers (JavaAnalyzer, JspAnalyzer, JsAnalyzer)
 ├── model           # CallNode, CallEdge, CallGraph data classes
 ├── resolver        # CrossLanguageResolver — bridges JSP↔Java↔JS
 ├── service         # CallGraphService (project-level), caching, indexing hooks
 ├── ui              # ToolWindowFactory, GraphPanel (JBCefBrowser), navigation handlers
 │   └── theme       # Theme bridge: maps IntelliJ LAF colors into the web view
 └── settings        # Configurable for depth, included file types, package filters, layout choice
```

### Key abstractions
- `CallNode(id, displayName, kind, language, virtualFile, offset)` where `kind ∈ {METHOD, FIELD, JSP_PAGE, JSP_INCLUDE, JS_FUNCTION, CONTROLLER_MAPPING, SERVICE, REPOSITORY}`.
- `CallEdge(from, to, kind)` where `kind ∈ {DIRECT_CALL, JSP_INCLUDE, FORWARD, EL_REFERENCE, SCRIPTLET_CALL, JS_INVOCATION, AJAX_REQUEST}`.
- `LanguageAnalyzer` interface with `outgoing(node, depth): Set<CallEdge>` and `incoming(node, depth): Set<CallEdge>`.
- `CallGraphService.compute(rootNode, depthIn, depthOut): CallGraph` — runs in a background `ReadAction`, deduplicates, respects user filters.

### Cross-language resolution rules (minimum viable)
1. **JSP → Java**: resolve `<jsp:useBean class="…">`, JSTL taglib URIs to handler classes, `${bean.method()}` EL expressions to bean methods, scriptlet `<% … %>` Java code via PSI.
2. **JSP → JSP**: `<jsp:include page="…">`, `<%@ include file="…" %>`.
3. **JSP → JS**: `<script src="…">` and inline `onclick`/`onsubmit` handler attributes.
4. **Java → JSP**: detect Spring `@RequestMapping` / `@GetMapping` methods returning a `String` view name; resolve against configured view-resolver prefix/suffix (default `/WEB-INF/views/`, `.jsp`). Also detect `RequestDispatcher.forward("…")`.
5. **Java → Java**: delegate to PSI's `MethodReferencesSearch` and `ReferencesSearch`.
6. **JS → JS**: function declarations and call expressions via the bundled JavaScript PSI (Ultimate).
7. **JS → Java** (best-effort): detect `fetch("/api/…")`, `$.ajax({url: "/api/…"})`, `XMLHttpRequest.open(…, "/api/…")` and match against Spring `@RequestMapping` URL patterns.

When a reference cannot be resolved with confidence, surface it as a **dashed, animated edge** with a tooltip explaining the heuristic — do not silently drop it.

## Visualization Requirements (this is the headline feature)

The graph is the product. It must feel **alive, colorful, and tactile** — not a static SVG dump.

### Node visuals
- **Distinct shape per `kind`**, so language and role are readable at a glance:
  - `METHOD` → rounded rectangle
  - `CONTROLLER_MAPPING` → hexagon (signals "entry point")
  - `JSP_PAGE` → trapezoid (page-like)
  - `JS_FUNCTION` → ellipse
  - `FIELD` → small circle
- **Vibrant per-language palette** (light & dark variants, picked at render time from the active IntelliJ theme):
  - Java        → orange  `#E76F00` (light) / `#FFB454` (dark)
  - Spring/MVC  → green   `#6DB33F` (light) / `#9CCC65` (dark)
  - JSP         → blue    `#1565C0` (light) / `#64B5F6` (dark)
  - JavaScript  → yellow  `#F7DF1E` (light) / `#FFEE58` (dark)
  - Unresolved  → gray    `#9E9E9E` with a `?` glyph
- **Gradient fill** (top-light → bottom-dark of the language hue) and a **soft drop shadow** for depth.
- **Root node**: thicker border (3px), subtle pulsing glow animation, and a small "★" badge.
- **Hover**: 1.08× scale-up, brighter outline, neighbor nodes/edges highlighted while the rest fade to ~25% opacity (focus mode).
- **Selected**: persistent halo until the user clicks empty canvas.
- **Badges**: small icons in the top-right of each node — a clock if the node is in a hot path, a lock for `private` members, a globe for `@RequestMapping` (URL shown in tooltip).

### Edge visuals
- **Solid edges** for confident resolutions; **dashed, animated marching-ants edges** for heuristic ones (e.g., AJAX URL match).
- **Color** by edge `kind`, distinguishable from node colors (e.g., `EL_REFERENCE` violet, `AJAX_REQUEST` teal, `FORWARD` amber).
- **Bezier curves** with arrowheads sized in proportion to zoom level. Bidirectional pairs render as a single curve with arrowheads on both ends.
- **Hover an edge** → highlight + tooltip showing the source code snippet of the call site.
- **Edge weight** (thickness) scales with call frequency when reference-search returns multiple usages between the same pair.

### Interaction
- **Drag any node**: smooth physics, the rest of the graph relaxes around it (the `cose-bilkent` layout reflows incrementally; dagre re-snaps on demand). Dragged positions persist for the lifetime of the tool window.
- **Pan**: drag the empty canvas. **Zoom**: mouse wheel, with smooth easing; `Ctrl+0` resets to fit, `Ctrl++` / `Ctrl+-` step zoom.
- **Double-click a node**: navigate to its `PsiElement` in the editor.
- **Right-click a node**: context menu with `Go to Source`, `Find Usages`, `Hide Node`, `Expand Callers`, `Expand Callees`, `Pin Position`.
- **Layout switcher** in the toolbar: `Hierarchical (dagre)`, `Force-directed (cose-bilkent)`, `Concentric (root in center)`, `Grid`. Switching animates between layouts (~600 ms tween).
- **Mini-map** in the bottom-right corner showing the full graph with a viewport rectangle; clicking the mini-map jumps the viewport.
- **Legend** in the top-left, collapsible, showing color/shape mappings for the currently visible node and edge kinds.
- **Search box** in the toolbar: filter nodes by display name; non-matching nodes fade to ~15% opacity.
- **Depth sliders** (in/out, 0–5): live recomputation on release; the graph cross-fades between states rather than hard-cutting.
- **Theme-aware**: graph background, text, and grid colors derive from `EditorColorsManager.getGlobalScheme()`. The plugin SHALL re-render when the user switches themes.
- **Keyboard navigation**: arrow keys move the selection between adjacent nodes; `Enter` opens the source.

### Polish
- All transitions (hover, select, layout switch, depth change, theme change) use 200–600 ms eased animations. No flicker, no reflow jank.
- No emoji in UI text. Icons come from `AllIcons` where possible; SVG bundled otherwise.
- Empty state: friendly illustration + "Place caret on a method, JSP page, or JS function and press Ctrl+Alt+Shift+G."
- Error state: red banner with the exception class and a "Copy details" button — never a blank canvas.

## Functional Requirements (must implement)
- **F1**. Right-click in editor → "Mitos: Show Call Graph" action; also reachable via `Ctrl+Alt+Shift+G`.
- **F2**. Tool window titled "Mitos" docks right; opens automatically when the action is invoked.
- **F3**. Graph shows the root node centered, callers above, callees below, color-coded and shape-coded as specified in "Visualization Requirements".
- **F4**. Click (or double-click) a node → IntelliJ navigates to the corresponding `PsiElement` in the editor.
- **F5**. Toolbar in tool window: depth-in slider (0–5), depth-out slider (0–5), layout switcher, search box, "refresh", "export PNG", "export SVG", "export Mermaid", "settings".
- **F6**. Settings page (`Configurable`) to set: view-resolver prefix/suffix, package include/exclude regex, file type toggles (JSP/JS on/off), max nodes (default 200, hard cap 1000), default layout, animation enabled (bool).
- **F7**. Background computation with a cancellable progress indicator; UI never blocks the EDT.
- **F8**. Results cached per `(rootNode, depthIn, depthOut, settingsHash)`; invalidated on PSI change for any file in the cached graph.
- **F9**. Drag positions, hidden nodes, and pinned nodes persist for the lifetime of the tool window; cleared on "Refresh".

## Non-Functional Requirements
- **N1**. Cold-path graph computation for a typical service method (≤100 nodes) completes in **< 2 s** on a mid-range developer machine.
- **N2**. Plugin startup overhead **< 150 ms** (no eager indexing on project open).
- **N3**. No telemetry, no network calls. All assets bundled.
- **N4**. Compatible with IntelliJ IDEA Ultimate 2024.2 through the latest stable.
- **N5**. Code style: ktlint default; all public APIs documented with KDoc.
- **N6**. Rendering SHALL maintain **≥ 50 fps** during pan/zoom/drag for graphs up to 200 nodes on a mid-range machine.

## Acceptance Criteria
1. `./gradlew buildPlugin` produces a `.zip` that installs cleanly via "Install Plugin from Disk".
2. `./gradlew runIde` launches a sandbox IDE with the plugin loaded.
3. Opening the bundled fixture project (provide a small Spring MVC + JSP + JS sample under `src/test/testData/sampleProject/`), placing the caret on a `@GetMapping` method, and invoking the action produces a graph that shows: the calling JSP form → the controller method → the service it delegates to → the JSP view it returns → the JS file included by that view. Each node renders in its language color and shape; the root node pulses; edges between Java and JSP are solid; the AJAX edge from JS to Java is dashed.
4. Each cross-language edge listed in "Cross-language resolution rules" has at least one passing integration test under `src/test/kotlin/.../resolver/`.
5. Clicking any node in the rendered graph focuses the corresponding source location in the editor.
6. Dragging a node moves it smoothly; releasing it leaves it at the new position; "Refresh" resets layout.
7. Switching IntelliJ between Light and Darcula themes re-renders the graph with the matching palette without requiring a tool window reopen.
8. README documents installation, usage, settings, visual legend, and a troubleshooting section for "no edges shown" cases.

## Out of Scope (do not build)
- Reflection-based call resolution.
- Spring `@Autowired` graph (separate concern).
- Database/SQL call tracking.
- Real-time graph updates as the user types.
- Support for IntelliJ Community edition (no JSP PSI available).
- 3D graph layouts.
- Persistent saved graph snapshots across IDE restarts (deferred to v1.2).

## Working Style
- Read `docs/SRS.md` in this repo before writing code; treat it as the source of truth for scope and behavior.
- Build incrementally: scaffolding → Java-only graph → JSP analyzer → JS analyzer → cross-language resolver → **visualization polish (this is not optional — it ships with v1.0)**.
- After each milestone, commit and run the full test suite. Do not move to the next milestone with failing tests.
- When a JetBrains API decision is non-obvious, document the choice in a `docs/decisions/NNN-title.md` ADR (one paragraph, "context / decision / consequence").
- Ask for clarification if a requirement conflicts with a JetBrains platform constraint — do not silently work around it.
