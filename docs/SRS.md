# Software Requirements Specification
## Mitos — Cross-Language Call Graph Visualizer (IntelliJ IDEA Plugin)

> **Mitos** (Greek μίτος, "thread") — Ariadne's thread through the Labyrinth.
> *The thread through your codebase.*

| Field | Value |
|---|---|
| Product name | Mitos |
| Document version | 1.0 |
| Date | 2026-05-04 |
| Status | Draft for review |
| Author | Ehab Ahmed Yassen |
| Audience | Plugin developer(s), reviewing tech lead, Java team users |

---

## 1. Introduction

### 1.1 Purpose
This document specifies the functional and non-functional requirements for an IntelliJ IDEA plugin, **Mitos** (hereafter "the Plugin"), that visualizes the bidirectional call graph for the source symbol under the editor caret across Java, JSP, and JavaScript files within a single project. It is intended as the contract between the requestor and the implementer, and as the acceptance baseline for QA.

### 1.2 Scope
Mitos extends IntelliJ IDEA Ultimate with a tool window that, on user request, computes and renders a directed graph of incoming (callers) and outgoing (callees) references for the symbol at the current caret position. The graph crosses language boundaries by resolving JSP-to-Java bindings (taglibs, EL expressions, scriptlets, beans), Java-to-JSP bindings (Spring MVC view names, `RequestDispatcher` forwards/includes), and best-effort JS-to-Java bindings (AJAX URLs matched against Spring request mappings).

The Plugin does **not** replace IntelliJ's built-in Call Hierarchy or Find Usages. It complements them by addressing the cross-language navigation gap.

### 1.3 Definitions, Acronyms, Abbreviations
| Term | Meaning |
|---|---|
| PSI | Program Structure Interface — IntelliJ's in-memory AST/symbol model |
| EL | Expression Language — JSP `${…}` syntax |
| JSTL | JavaServer Pages Standard Tag Library |
| JCEF | JetBrains Chromium Embedded Framework — embedded browser in the IDE |
| Root node | The graph node corresponding to the symbol under the caret when the action is invoked |
| Depth-in / depth-out | Maximum number of caller / callee hops to traverse from the root node |
| MVP | Minimum Viable Product — initial release scope (Section 7) |

### 1.4 References
- IntelliJ Platform SDK documentation (jetbrains.com/help/idea/plugin-development.html)
- IntelliJ Platform Gradle Plugin 2.x
- Cytoscape.js graph rendering library
- Spring Framework `@RequestMapping` / `@GetMapping` reference
- JSR 245 (JavaServer Pages 2.1) and JSR 52 (JSTL)

### 1.5 Document Overview
Section 2 describes the product context and assumptions. Section 3 enumerates external interfaces. Section 4 specifies functional requirements. Section 5 specifies non-functional requirements. Section 6 documents constraints. Section 7 defines release scope and out-of-scope items.

---

## 2. Overall Description

### 2.1 Product Perspective
The Plugin is a self-contained IntelliJ IDEA plugin distributed as a `.zip` archive, installable via the IDE's plugin manager. It depends on the bundled Java, JSP, and JavaScript PSI implementations available in IntelliJ IDEA Ultimate. It does **not** require a network connection at runtime, an external service, or any modification to the user's project.

### 2.2 Product Functions (summary)
1. Compute the bidirectional call graph rooted at the symbol under the caret.
2. Resolve references across Java ⇄ JSP ⇄ JavaScript boundaries.
3. Render the graph in an interactive embedded browser inside an IDE tool window.
4. Allow the user to navigate from any graph node to its source location in the editor.
5. Allow the user to tune traversal depth, filter scope, and export the graph.

### 2.3 User Classes and Characteristics
- **Primary**: Java backend developers maintaining a Spring MVC + JSP + JS web application. Comfortable with IntelliJ. Limited tolerance for slow or unstable IDE plugins.
- **Secondary**: Frontend/JS developers occasionally needing to trace a click handler back to its server-side endpoint.
- **Tertiary**: Tech leads performing change-impact assessment during code review.

### 2.4 Operating Environment
- IntelliJ IDEA **Ultimate** 2024.2 or later (Community is not supported because JSP PSI is unavailable).
- JDK 17 or later.
- Operating systems: macOS, Linux, Windows (anywhere IntelliJ runs with JCEF enabled).
- Project must be a recognized IntelliJ project with Java/JSP/JS files indexed.

### 2.5 Design and Implementation Constraints
- Must be implemented in **Kotlin** targeting JVM 17.
- Must use the **IntelliJ Platform Gradle Plugin 2.x** (not the legacy plugin).
- Must not perform network I/O at runtime. All web assets bundled in plugin resources.
- Must not block the Event Dispatch Thread (EDT). All PSI traversal runs inside `ReadAction` on a background thread, with progress reported via `ProgressManager`.
- Must respect IntelliJ's PSI invalidation contract — no caching of `PsiElement` references across read actions; cache by `SmartPsiElementPointer` or `(VirtualFile, offset)` pairs.

### 2.6 Assumptions and Dependencies
- The user's project compiles and indexes successfully in IntelliJ; the Plugin does not attempt to recover from broken PSI.
- Spring MVC view resolution follows the conventional prefix/suffix model (e.g., `/WEB-INF/views/` + `.jsp`); non-conventional resolvers (Tiles, Thymeleaf) are out of scope.
- AJAX endpoints in JS use string literals for URLs, not dynamic concatenation.
- JSP files use either standard JSTL or scriptlets; custom tag libraries with non-trivial handler resolution are best-effort.

---

## 3. External Interface Requirements

### 3.1 User Interfaces
- **Editor context menu entry** "Mitos: Show Call Graph" (icon: graph node).
- **Main menu** entry under `Navigate → Mitos: Show Call Graph`.
- **Keyboard shortcut**: `Ctrl+Alt+Shift+G` (Windows/Linux), `⌘⌥⇧G` (macOS). User-rebindable via Keymap.
- **Tool Window** "Mitos", default docked right, with:
  - Toolbar: depth-in slider (0–5), depth-out slider (0–5), Refresh, Export PNG, Export Mermaid, Settings, Help.
  - Main panel: `JBCefBrowser` rendering the Cytoscape.js graph.
  - Status bar at bottom: `n nodes, m edges • computed in t ms`.
- **Settings page** under `Settings → Tools → Mitos`.

### 3.2 Software Interfaces
- IntelliJ Platform APIs: `PsiManager`, `PsiFile`, `PsiMethod`, `PsiReference`, `MethodReferencesSearch`, `ReferencesSearch`, `JspFile`, `JSElement`, `JBCefBrowser`, `ToolWindowManager`, `AnAction`, `Configurable`, `ProgressManager`, `SmartPsiElementPointer`.
- Bundled JS assets: Cytoscape.js, dagre layout extension, application JS/CSS under `src/main/resources/web/`.

### 3.3 Hardware Interfaces
None beyond what IntelliJ requires.

### 3.4 Communications Interfaces
None. The Plugin makes no outbound network calls.

---

## 4. Functional Requirements

### 4.1 Graph Construction
| ID | Requirement |
|---|---|
| FR-1 | When the user invokes "Mitos: Show Call Graph", the Plugin SHALL identify the `PsiElement` at the editor caret as the root node. |
| FR-2 | If the caret is not on a supported element (Java method/field, JSP page/include, JS function), the Plugin SHALL display a notification "Place caret on a method, JSP page, or JS function" and abort. |
| FR-3 | The Plugin SHALL compute outgoing edges by recursively following calls/references from the root up to the configured depth-out (default 2, max 5). |
| FR-4 | The Plugin SHALL compute incoming edges by recursively following references **to** the root up to the configured depth-in (default 2, max 5). |
| FR-5 | The Plugin SHALL deduplicate nodes by stable identity `(virtualFile.path, offset, kind)` and edges by `(fromId, toId, edgeKind)`. |
| FR-6 | If the resulting graph exceeds the configured max-node cap (default 200, hard ceiling 1000), the Plugin SHALL truncate the traversal breadth-first and display a banner "Graph truncated at N nodes — increase limit in Settings". |

### 4.2 Cross-Language Resolution
| ID | Requirement |
|---|---|
| FR-7 | The Plugin SHALL resolve `<jsp:useBean class="…">` declarations to their Java class. |
| FR-8 | The Plugin SHALL resolve EL expressions of the form `${bean.method()}` and `${bean.property}` to the corresponding Java getter/method. |
| FR-9 | The Plugin SHALL resolve Java code inside JSP scriptlets `<% … %>` and expressions `<%= … %>` via the JSP PSI. |
| FR-10 | The Plugin SHALL follow `<jsp:include page="…">` and `<%@ include file="…" %>` directives as `JSP_INCLUDE` edges. |
| FR-11 | The Plugin SHALL follow `<script src="…">` references and inline event handler attributes (`onclick`, `onsubmit`, `onchange`) as edges from JSP to JS. |
| FR-12 | The Plugin SHALL detect Spring MVC handler methods (`@RequestMapping`, `@GetMapping`, `@PostMapping`, etc.) returning a `String` view name and resolve the view to a JSP file using the configured prefix/suffix. |
| FR-13 | The Plugin SHALL detect `RequestDispatcher.forward(...)` and `.include(...)` calls and resolve their string arguments to JSP files. |
| FR-14 | The Plugin SHALL detect JS calls of the form `fetch("/url")`, `$.ajax({url: "/url"})`, `XMLHttpRequest.open(method, "/url")` and match the URL against Spring `@RequestMapping` patterns in the project, producing best-effort `AJAX_REQUEST` edges. |
| FR-15 | When a reference is resolved heuristically (e.g., AJAX URL match), the Plugin SHALL render its edge as **dashed** with a tooltip explaining the heuristic. Confident resolutions render as **solid** edges. |
| FR-16 | When the Plugin cannot resolve a referenced bean, view, or URL, it SHALL log the unresolved reference at INFO level and SHALL NOT add a phantom node. |

### 4.3 Visualization

The visualization is the primary user-facing surface and must feel rich, vibrant, and interactive — not a static diagram. Requirements FR-17 through FR-21d together define the visual contract.

| ID | Requirement |
|---|---|
| FR-17 | The graph SHALL be rendered in an embedded `JBCefBrowser` using Cytoscape.js. The default layout SHALL be hierarchical (`dagre` extension) with the root node centered, callers above, callees below. The Plugin SHALL bundle and offer at least four layouts: `Hierarchical (dagre)`, `Force-directed (cose-bilkent)`, `Concentric`, and `Grid`, switchable from the toolbar. |
| FR-18 | Nodes SHALL be color-coded by language with theme-aware light and dark variants resolved at render time from `EditorColorsManager.getGlobalScheme()`: Java = orange (`#E76F00` / `#FFB454`), Spring = green (`#6DB33F` / `#9CCC65`), JSP = blue (`#1565C0` / `#64B5F6`), JavaScript = yellow (`#F7DF1E` / `#FFEE58`), Unresolved = gray (`#9E9E9E`). Each node SHALL render with a top-to-bottom gradient fill and a soft drop shadow. |
| FR-18a | Nodes SHALL be shape-coded by `kind`: `METHOD` = rounded rectangle, `CONTROLLER_MAPPING` = hexagon, `JSP_PAGE` = trapezoid, `JS_FUNCTION` = ellipse, `FIELD` = small circle. Shape SHALL remain identifiable across all bundled layouts. |
| FR-18b | The root node SHALL have a 3px border, a continuously pulsing glow animation (1 Hz, ≤ 30% opacity oscillation), and a "★" badge in the top-right corner. |
| FR-18c | Edges SHALL be color-coded by `kind` (e.g., `EL_REFERENCE` violet, `AJAX_REQUEST` teal, `FORWARD` amber) and rendered as bezier curves with arrowheads sized in proportion to current zoom. Confident resolutions SHALL render as solid lines; heuristic resolutions (FR-15) SHALL render as dashed marching-ants animations. |
| FR-19 | Hovering a node SHALL (a) show a tooltip with fully qualified name, file path, and line number; (b) scale the node to 1.08×; (c) enter "focus mode": neighbor nodes and incident edges remain at full opacity while non-neighbors fade to ~25%. Hovering an edge SHALL show a tooltip containing the source code snippet of the call site. |
| FR-20 | Double-clicking a node SHALL trigger IntelliJ navigation to the underlying `PsiElement` (file opens in editor, caret moves to the symbol). Single-click SHALL select; right-click SHALL open a context menu with `Go to Source`, `Find Usages`, `Hide Node`, `Expand Callers`, `Expand Callees`, and `Pin Position`. |
| FR-21 | The graph SHALL support zoom (mouse wheel with eased easing; `Ctrl+0` fit-to-view; `Ctrl++` / `Ctrl+-` step zoom), pan (drag empty canvas), and node drag-to-rearrange with smooth physics. Dragged positions SHALL persist for the lifetime of the tool window and SHALL survive depth-slider recomputation when the underlying node identity is unchanged. They SHALL be cleared by the "Refresh" toolbar action. |
| FR-21a | The tool window SHALL display a collapsible mini-map in the bottom-right corner showing the entire graph and a viewport rectangle; clicking inside the mini-map SHALL move the viewport. |
| FR-21b | The tool window SHALL display a collapsible legend in the top-left corner showing the color and shape mappings for the node and edge kinds present in the current graph. |
| FR-21c | The toolbar SHALL include a search box that filters nodes by display name; non-matching nodes SHALL fade to ~15% opacity rather than being removed. |
| FR-21d | All interactive transitions — hover, selection, layout switch, depth change, theme change — SHALL use eased animations of 200–600 ms. The Plugin SHALL re-render the graph with the matching palette when the user switches IntelliJ themes, without requiring the tool window to be reopened. |

### 4.4 User Controls and Settings
| ID | Requirement |
|---|---|
| FR-22 | The tool window toolbar SHALL expose depth-in and depth-out sliders (range 0–5). Changing a slider SHALL trigger recomputation. |
| FR-23 | A "Refresh" button SHALL recompute the graph against the current caret position and current PSI state. |
| FR-24 | An "Export PNG" button SHALL save the rendered canvas to a user-chosen path. An "Export SVG" button SHALL save a vector copy of the same view. |
| FR-25 | An "Export Mermaid" button SHALL write a `.mmd` file containing the graph in Mermaid `flowchart` syntax. |
| FR-26 | The settings page SHALL expose: view-resolver prefix, view-resolver suffix, package include regex, package exclude regex, JSP analyzer enabled (bool), JS analyzer enabled (bool), max nodes (int 10–1000), default depth-in (int 0–5), default depth-out (int 0–5), default layout (enum: Hierarchical / Force-directed / Concentric / Grid), animations enabled (bool, default true). |
| FR-27 | Settings SHALL be persisted per-project via `PersistentStateComponent`. |

### 4.5 Caching and Invalidation
| ID | Requirement |
|---|---|
| FR-28 | Computed graphs SHALL be cached keyed by `(rootNodeId, depthIn, depthOut, settingsHash)`. |
| FR-29 | A `PsiTreeChangeListener` SHALL invalidate any cache entry whose graph touches a modified file. |
| FR-30 | Cache size SHALL be bounded (default 16 entries, LRU eviction). |

---

## 5. Non-Functional Requirements

### 5.1 Performance
| ID | Requirement |
|---|---|
| NFR-1 | Cold graph computation for ≤100 nodes SHALL complete in **< 2 seconds** on a developer machine with 16 GB RAM and an 8-core CPU. |
| NFR-2 | Cached graph retrieval SHALL render in **< 300 ms**. |
| NFR-3 | Plugin startup overhead SHALL be **< 150 ms**; the Plugin SHALL NOT perform indexing on project open. |
| NFR-4 | The EDT SHALL never be blocked for more than 50 ms by Plugin code. |
| NFR-4a | The rendered graph SHALL maintain **≥ 50 fps** during pan, zoom, and node drag for graphs of up to 200 nodes on a developer machine with 16 GB RAM and an 8-core CPU. |

### 5.2 Reliability
| ID | Requirement |
|---|---|
| NFR-5 | An exception in any single analyzer SHALL NOT abort graph computation; the failure SHALL be logged and the partial graph rendered with a warning banner. |
| NFR-6 | The Plugin SHALL handle PSI invalidation mid-computation by aborting cleanly and reporting "Graph computation cancelled — please retry". |

### 5.3 Usability
| ID | Requirement |
|---|---|
| NFR-7 | All user-facing text SHALL be in English; strings SHALL live in a `messages/MitosBundle.properties` file to enable future i18n. |
| NFR-8 | The first-time user SHALL be able to render a graph without consulting documentation, using only the editor context menu. |

### 5.4 Compatibility
| ID | Requirement |
|---|---|
| NFR-9 | The Plugin SHALL support IntelliJ IDEA Ultimate 2024.2 through the latest stable release at time of build. The `since-build` and `until-build` SHALL be set in `plugin.xml` accordingly. |
| NFR-10 | The Plugin SHALL not declare dependencies on internal/experimental IntelliJ APIs. |

### 5.5 Security and Privacy
| ID | Requirement |
|---|---|
| NFR-11 | The Plugin SHALL collect no telemetry, analytics, or usage data. |
| NFR-12 | The Plugin SHALL make no outbound network connections. |
| NFR-13 | All bundled web assets SHALL be served from `jbcefbrowser://` local URLs; no remote content. |

### 5.6 Maintainability
| ID | Requirement |
|---|---|
| NFR-14 | Code SHALL conform to ktlint default style; CI SHALL fail on violations. |
| NFR-15 | All public APIs SHALL have KDoc. |
| NFR-16 | Test coverage for the `analyzer` and `resolver` packages SHALL be ≥ 80% line coverage. |

---

## 6. Constraints

- **C-1**. Limited to IntelliJ IDEA **Ultimate** because Community lacks JSP PSI support.
- **C-2**. JSP support is being deprecated in some Spring projects; the Plugin's value depends on continued JSP PSI availability in IntelliJ. If JetBrains removes it, the JSP analyzer becomes inoperable.
- **C-3**. Spring view resolution beyond simple prefix/suffix (Tiles, Thymeleaf, ViewControllerRegistry) is **not** supported in v1.
- **C-4**. JS-to-Java AJAX matching is heuristic. False positives are possible; false negatives are expected for dynamically constructed URLs.
- **C-5**. The Plugin operates on a single project at a time. Multi-project graph traversal is not supported.

---

## 7. Release Scope

### 7.1 MVP (v1.0) — must ship
- All FR-1 through FR-30.
- All NFR-1 through NFR-16.
- Bundled sample project under `src/test/testData/sampleProject/` exercising every cross-language edge kind.
- README with installation, usage, settings, and troubleshooting.

### 7.2 Out of Scope for v1.0
- Reflection-based call resolution.
- Spring `@Autowired` dependency graph.
- Database/SQL call tracking (e.g., MyBatis mapper resolution).
- Real-time graph updates as the user types.
- IntelliJ Community edition support.
- Thymeleaf, Tiles, or non-standard view resolvers.
- Sequence-diagram view (consider for v1.1).
- Saving/sharing graph snapshots between users (consider for v1.2).

### 7.3 Acceptance Criteria
1. `./gradlew buildPlugin` produces a `.zip` that installs cleanly via "Install Plugin from Disk" in a fresh IntelliJ IDEA Ultimate.
2. `./gradlew runIde` launches a sandbox IDE with the Plugin loaded and the bundled sample project openable.
3. With caret placed on a `@GetMapping` method in the sample project, invoking the action produces a graph showing: calling JSP form → controller method → service → returned JSP view → JS file included by that view. All five nodes visible, all four edges present, each rendered in the language color and node shape specified in FR-18 / FR-18a, the root node pulsing per FR-18b, the AJAX edge dashed per FR-18c.
4. Each edge kind defined in the resolver (`DIRECT_CALL`, `JSP_INCLUDE`, `FORWARD`, `EL_REFERENCE`, `SCRIPTLET_CALL`, `JS_INVOCATION`, `AJAX_REQUEST`) has at least one passing integration test.
5. Double-clicking any node opens the corresponding source location in the editor with the caret on the symbol.
6. Dragging a node moves it smoothly to the cursor position and leaves it there until "Refresh" is invoked. Switching layouts animates between configurations without flicker.
7. Switching IntelliJ between Light and Darcula themes re-renders the graph with the matching palette without requiring the tool window to be reopened.
8. Settings page round-trips all values across IDE restart.
9. No Plugin-originated exceptions appear in `idea.log` during a 10-minute interactive smoke test.

---

## 8. Open Questions
1. Should the Plugin support custom Spring view-resolver classes via a configurable SPI, or is prefix/suffix sufficient for the foreseeable backlog?
2. For JS analysis, do we need TypeScript support in v1, or is plain JS enough for the current codebase?
3. Are there project-specific custom JSP tags whose handler resolution we should hardcode in v1?
4. Should "Export Mermaid" produce a sequence diagram or stick with `flowchart`?

These should be answered before development begins.
