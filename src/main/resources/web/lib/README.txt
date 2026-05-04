This directory is populated at build time by the `downloadWebDeps` Gradle task.

Pinned versions are declared in `gradle.properties`. Files placed here:

  cytoscape.min.js                — Cytoscape.js core
  dagre.min.js                    — dagre layout engine
  cytoscape-dagre.js              — Cytoscape adapter for dagre
  cytoscape-cose-bilkent.js       — Force-directed layout
  popper.min.js                   — Popper.js for tooltips
  cytoscape-popper.js             — Cytoscape adapter for popper

These files are bundled into the plugin JAR during `processResources` and
loaded by `web/index.html` from the plugin classloader at runtime — no
network calls happen after install (NFR-3, NFR-12).
