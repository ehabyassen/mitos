/* Mitos — Cytoscape.js renderer.
 *
 * Public API exposed on `window.mitos`:
 *   render(graph)         — draw a {nodes, edges, root, …} payload
 *   showEmpty(text)       — friendly empty state
 *   applyTheme(palette)   — switch to a theme-bridge palette (FR-21d)
 *   setLayout(name)       — Hierarchical/Force-directed/Concentric/Grid
 *   filter(text)          — fade non-matching nodes (FR-21c)
 *   exportPng()           — POST PNG data:URL to Kotlin (FR-24)
 *   exportSvg()           — POST SVG markup to Kotlin (FR-24)
 *   exportMermaid()       — POST Mermaid text to Kotlin (FR-25)
 *
 * Bridge to Kotlin:
 *   __mitosNavigate({id, filePath, offset})
 *   __mitosExport({format, content})
 * Both are installed by GraphPanel.installBridge().
 */
(function () {
    'use strict';

    // -------------------------------------------------------------- Palette
    const LANG_COLOURS = {
        light: {
            JAVA:        { fill: '#E76F00', fillTop: '#FFB454', stroke: '#A04E00' },
            SPRING:      { fill: '#6DB33F', fillTop: '#9CCC65', stroke: '#3F6F22' },
            JSP:         { fill: '#1565C0', fillTop: '#64B5F6', stroke: '#0D3F7B' },
            JAVASCRIPT:  { fill: '#F7DF1E', fillTop: '#FFEE58', stroke: '#7A6F0E' },
            UNRESOLVED:  { fill: '#9E9E9E', fillTop: '#CFCFCF', stroke: '#5F5F5F' }
        },
        dark: {
            JAVA:        { fill: '#FFB454', fillTop: '#FFC880', stroke: '#FF8A1A' },
            SPRING:      { fill: '#9CCC65', fillTop: '#B7DD8A', stroke: '#6DB33F' },
            JSP:         { fill: '#64B5F6', fillTop: '#90CAF9', stroke: '#1565C0' },
            JAVASCRIPT:  { fill: '#FFEE58', fillTop: '#FFF59D', stroke: '#F7DF1E' },
            UNRESOLVED:  { fill: '#9E9E9E', fillTop: '#BDBDBD', stroke: '#616161' }
        }
    };

    const EDGE_COLOURS = {
        DIRECT_CALL:    '#5C6BC0',
        JSP_INCLUDE:    '#1565C0',
        FORWARD:        '#FFA000',
        EL_REFERENCE:   '#7E57C2',
        SCRIPTLET_CALL: '#26A69A',
        JS_INVOCATION:  '#F4A261',
        AJAX_REQUEST:   '#26C6DA'
    };

    const KIND_SHAPES = {
        METHOD:             'roundrectangle',
        CONTROLLER_MAPPING: 'hexagon',
        SERVICE:            'roundrectangle',
        REPOSITORY:         'roundrectangle',
        FIELD:              'ellipse',
        JSP_PAGE:           'cut-rectangle',
        JSP_INCLUDE:        'cut-rectangle',
        JS_FUNCTION:        'ellipse'
    };

    const KIND_LABEL = {
        METHOD: 'Method',
        CONTROLLER_MAPPING: 'Controller mapping',
        SERVICE: 'Service / class',
        REPOSITORY: 'Repository',
        FIELD: 'Field',
        JSP_PAGE: 'JSP page',
        JSP_INCLUDE: 'JSP include',
        JS_FUNCTION: 'JS function'
    };

    // -------------------------------------------------------------- State
    let cy = null;
    let isDark = false;
    let palette = null;
    let currentLayoutName = 'dagre';
    let lastGraph = null;
    let pendingFilter = '';

    // -------------------------------------------------------------- Utilities
    function $(id) { return document.getElementById(id); }

    function setBanner(text) {
        const el = $('banner');
        if (!text) { el.classList.add('hidden'); el.textContent = ''; return; }
        el.textContent = text;
        el.classList.remove('hidden');
    }

    function laneFor(node) {
        if (node.data('isRoot')) return 0;
        // Best-effort lane assignment for the dagre layout: callers above (negative),
        // callees below (positive). The service emits incoming edges as edges whose
        // *target* is the root, and outgoing as edges whose *source* is the root.
        return 0;
    }

    function postNavigate(node) {
        if (!window.__mitosNavigate) return;
        const data = node.data();
        const payload = JSON.stringify({ id: data.id, filePath: data.filePath || '', offset: data.offset || 0 });
        window.__mitosNavigate(payload);
    }

    function postExport(format, content) {
        if (!window.__mitosExport) return;
        window.__mitosExport(JSON.stringify({ format: format, content: content }));
    }

    // -------------------------------------------------------------- Style
    function buildStyle() {
        const palLight = LANG_COLOURS.light;
        const palDark = LANG_COLOURS.dark;
        const styleSheet = [];

        // Generic node
        styleSheet.push({
            selector: 'node',
            style: {
                'label': 'data(label)',
                'text-valign': 'center',
                'text-halign': 'center',
                'font-size': 11,
                'font-weight': 600,
                'color': isDark ? '#E6E6E6' : '#202020',
                'text-outline-width': 2,
                'text-outline-color': isDark ? '#1E1F22' : '#FFFFFF',
                'width': 'label',
                'height': 32,
                'padding': 12,
                'border-width': 1.5,
                'shadow-blur': 8,
                'shadow-color': '#000',
                'shadow-opacity': isDark ? 0.4 : 0.15,
                'shadow-offset-y': 2,
                'transition-property': 'background-color, border-color, opacity, width, height',
                'transition-duration': '180ms'
            }
        });

        // Per-language gradient + colour
        Object.keys(KIND_SHAPES).forEach(function () { /* handled below */ });
        const langs = Object.keys(palLight);
        langs.forEach(function (lang) {
            const c = (isDark ? palDark : palLight)[lang];
            styleSheet.push({
                selector: 'node[language = "' + lang + '"]',
                style: {
                    'background-fill': 'linear-gradient',
                    'background-gradient-stop-colors': c.fillTop + ' ' + c.fill,
                    'background-gradient-stop-positions': '0 100',
                    'background-gradient-direction': 'to-bottom',
                    'border-color': c.stroke,
                    'background-color': c.fill
                }
            });
        });

        // Per-kind shape
        Object.keys(KIND_SHAPES).forEach(function (kind) {
            styleSheet.push({
                selector: 'node[kind = "' + kind + '"]',
                style: { 'shape': KIND_SHAPES[kind] }
            });
        });

        // Root highlight
        styleSheet.push({
            selector: 'node[?isRoot]',
            style: {
                'border-width': 3,
                'border-color': isDark ? '#FFD54F' : '#F57F17',
                'shadow-color': isDark ? '#FFD54F' : '#F57F17',
                'shadow-blur': 18,
                'shadow-opacity': 0.6
            }
        });

        // Hover/focus
        styleSheet.push({
            selector: 'node.hover',
            style: { 'border-width': 3, 'z-index': 99 }
        });
        styleSheet.push({
            selector: 'node.faded, edge.faded',
            style: { 'opacity': 0.18, 'text-opacity': 0.18 }
        });
        styleSheet.push({
            selector: 'node.search-fade',
            style: { 'opacity': 0.12, 'text-opacity': 0.12 }
        });
        styleSheet.push({
            selector: 'node.selected',
            style: {
                'border-width': 4,
                'border-color': isDark ? '#FFEB3B' : '#1976D2'
            }
        });

        // Edges
        styleSheet.push({
            selector: 'edge',
            style: {
                'curve-style': 'bezier',
                'target-arrow-shape': 'triangle',
                'arrow-scale': 1.1,
                'width': 'mapData(weight, 1, 5, 1.5, 4)',
                'line-cap': 'round',
                'opacity': 0.85,
                'transition-property': 'opacity, line-color, width',
                'transition-duration': '180ms'
            }
        });
        Object.keys(EDGE_COLOURS).forEach(function (kind) {
            styleSheet.push({
                selector: 'edge[kind = "' + kind + '"]',
                style: {
                    'line-color': EDGE_COLOURS[kind],
                    'target-arrow-color': EDGE_COLOURS[kind]
                }
            });
        });
        styleSheet.push({
            selector: 'edge[confidence = "HEURISTIC"]',
            style: {
                'line-style': 'dashed',
                'line-dash-pattern': [6, 4],
                'line-dash-offset': 0
            }
        });

        return styleSheet;
    }

    // -------------------------------------------------------------- Layout
    function layoutOptions(name) {
        if (name === 'cose-bilkent') {
            return {
                name: 'cose-bilkent',
                animate: 'end',
                animationDuration: 600,
                idealEdgeLength: 100,
                nodeRepulsion: 4500,
                edgeElasticity: 0.45,
                gravity: 0.25,
                randomize: false,
                fit: true,
                padding: 30
            };
        }
        if (name === 'concentric') {
            return {
                name: 'concentric',
                animate: true,
                animationDuration: 500,
                concentric: function (n) { return n.data('isRoot') ? 10 : 1; },
                levelWidth: function () { return 1; },
                minNodeSpacing: 30,
                fit: true
            };
        }
        if (name === 'grid') {
            return { name: 'grid', animate: true, animationDuration: 400, fit: true, padding: 30 };
        }
        // dagre default
        return {
            name: 'dagre',
            rankDir: 'TB',
            nodeSep: 35,
            rankSep: 70,
            edgeSep: 15,
            animate: true,
            animationDuration: 500,
            fit: true,
            padding: 30
        };
    }

    function runLayout() {
        if (!cy) return;
        const layout = cy.layout(layoutOptions(currentLayoutName));
        layout.run();
    }

    // -------------------------------------------------------------- Tooltip
    let activeTip = null;
    function showTip(target, html) {
        hideTip();
        const tip = document.createElement('div');
        tip.className = 'mitos-tip';
        tip.innerHTML = html;
        document.body.appendChild(tip);
        activeTip = target.popper({
            content: function () { return tip; },
            popper: { placement: 'top' }
        });
        activeTip.tip = tip;
        target.on('position', updateTip);
        cy.on('pan zoom resize', updateTip);
    }
    function updateTip() { if (activeTip) activeTip.update(); }
    function hideTip() {
        if (activeTip) {
            try { activeTip.tip.remove(); } catch (e) { /* ignore */ }
            try { activeTip.destroy(); } catch (e) { /* ignore */ }
            activeTip = null;
        }
    }

    // -------------------------------------------------------------- Hover focus mode
    function applyFocus(node) {
        cy.elements().addClass('faded');
        const neighbours = node.closedNeighborhood();
        neighbours.removeClass('faded');
    }
    function clearFocus() {
        cy.elements().removeClass('faded');
    }

    // -------------------------------------------------------------- Mini-map
    let miniCy = null;
    function rebuildMinimap() {
        if (!cy) return;
        const container = $('minimap');
        container.innerHTML = '';
        const inner = document.createElement('div');
        inner.style.width = '100%';
        inner.style.height = '100%';
        container.appendChild(inner);
        miniCy = cytoscape({
            container: inner,
            elements: cy.elements().jsons(),
            style: cy.style().json(),
            userZoomingEnabled: false,
            userPanningEnabled: false,
            boxSelectionEnabled: false,
            autoungrabify: true,
            layout: { name: 'preset' }
        });
        // Fit and overlay viewport rectangle
        miniCy.fit();
        miniCy.on('tap', function (event) {
            const pos = event.position || event.cyPosition;
            cy.center({ x: pos.x, y: pos.y });
        });
    }

    // -------------------------------------------------------------- Legend
    function rebuildLegend() {
        const body = $('legend-body');
        body.innerHTML = '';
        if (!cy) return;
        const langs = new Set();
        const kinds = new Set();
        const edgeKinds = new Set();
        cy.nodes().forEach(function (n) { langs.add(n.data('language')); kinds.add(n.data('kind')); });
        cy.edges().forEach(function (e) { edgeKinds.add(e.data('kind')); });

        const themePal = (isDark ? LANG_COLOURS.dark : LANG_COLOURS.light);
        langs.forEach(function (lang) {
            const c = themePal[lang];
            const row = document.createElement('div');
            row.className = 'legend-row';
            row.innerHTML = '<span class="legend-swatch" style="background:' + (c ? c.fill : '#999') + '"></span>' +
                '<span>' + lang.charAt(0) + lang.slice(1).toLowerCase() + '</span>';
            body.appendChild(row);
        });
        kinds.forEach(function (kind) {
            const row = document.createElement('div');
            row.className = 'legend-row';
            row.innerHTML = '<span style="font-family:monospace">▢</span><span>' + (KIND_LABEL[kind] || kind) + '</span>';
            body.appendChild(row);
        });
        edgeKinds.forEach(function (kind) {
            const row = document.createElement('div');
            row.className = 'legend-row';
            const c = EDGE_COLOURS[kind] || '#999';
            const heuristic = (kind === 'AJAX_REQUEST') ? 'dashed' : '';
            row.innerHTML = '<span class="legend-edge ' + heuristic + '" style="border-top-color:' + c + '"></span>' +
                '<span>' + kind.replace(/_/g, ' ').toLowerCase() + '</span>';
            body.appendChild(row);
        });
    }

    // -------------------------------------------------------------- Render
    function render(graph) {
        lastGraph = graph;
        $('empty-state').classList.add('hidden');

        if (cy) { cy.destroy(); cy = null; }
        cy = cytoscape({
            container: $('cy'),
            elements: graph.nodes.concat(graph.edges),
            style: buildStyle(),
            wheelSensitivity: 0.25,
            minZoom: 0.2,
            maxZoom: 4,
            layout: layoutOptions(currentLayoutName)
        });

        wireEvents();
        if (graph.truncated) setBanner('Graph truncated at ' + graph.stats.nodes + ' nodes — increase limit in Settings.');
        else setBanner(null);

        // Build mini-map and legend after layout settles
        cy.one('layoutstop', function () {
            rebuildMinimap();
            rebuildLegend();
            if (pendingFilter) applyFilter(pendingFilter);
        });
    }

    function wireEvents() {
        cy.on('mouseover', 'node', function (event) {
            const n = event.target;
            n.addClass('hover');
            applyFocus(n);
            const d = n.data();
            const html =
                '<div><b>' + escapeHtml(d.qualifiedName || d.label || '') + '</b></div>' +
                '<div style="opacity:.75">' + escapeHtml(d.filePath || '') + (d.line ? ':' + d.line : '') + '</div>';
            showTip(n, html);
        });
        cy.on('mouseout', 'node', function (event) {
            event.target.removeClass('hover');
            clearFocus();
            hideTip();
        });

        cy.on('mouseover', 'edge', function (event) {
            const e = event.target;
            const d = e.data();
            if (!d.snippet) return;
            const html =
                '<div><b>' + d.kind.replace(/_/g, ' ').toLowerCase() + '</b></div>' +
                '<div style="opacity:.75">' + escapeHtml(d.callSiteFile || '') + (d.callSiteLine ? ':' + d.callSiteLine : '') + '</div>' +
                '<div style="margin-top:4px"><code>' + escapeHtml(d.snippet) + '</code></div>';
            showTip(e, html);
        });
        cy.on('mouseout', 'edge', hideTip);

        cy.on('tap', 'node', function (event) {
            cy.nodes().removeClass('selected');
            event.target.addClass('selected');
        });

        cy.on('dblclick', 'node', function (event) {
            postNavigate(event.target);
        });

        cy.on('tap', function (event) {
            if (event.target === cy) cy.nodes().removeClass('selected');
        });
    }

    function escapeHtml(s) {
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    // -------------------------------------------------------------- Theme
    function applyTheme(p) {
        palette = p;
        isDark = !!p.isDark;
        document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light');
        document.documentElement.style.setProperty('--mitos-bg', p.background);
        document.documentElement.style.setProperty('--mitos-fg', p.foreground);
        document.documentElement.style.setProperty('--mitos-grid', p.grid);
        document.documentElement.style.setProperty('--mitos-tooltip-bg', p.tooltipBackground);
        document.documentElement.style.setProperty('--mitos-tooltip-fg', p.tooltipForeground);
        if (cy) { cy.style(buildStyle()); rebuildLegend(); }
    }

    // -------------------------------------------------------------- Layout / search / export

    function setLayout(name) {
        currentLayoutName = name;
        runLayout();
    }

    function applyFilter(text) {
        pendingFilter = text || '';
        if (!cy) return;
        if (!pendingFilter) {
            cy.nodes().removeClass('search-fade');
            cy.edges().removeClass('search-fade');
            return;
        }
        const needle = pendingFilter.toLowerCase();
        cy.nodes().forEach(function (n) {
            const matches = (n.data('label') || '').toLowerCase().indexOf(needle) >= 0 ||
                (n.data('qualifiedName') || '').toLowerCase().indexOf(needle) >= 0;
            if (matches) n.removeClass('search-fade');
            else n.addClass('search-fade');
        });
    }

    function exportPng() {
        if (!cy) return;
        const data = cy.png({ full: true, scale: 2, bg: palette ? palette.background : '#FFFFFF' });
        postExport('png', data);
    }
    function exportSvg() {
        if (!cy || typeof cy.svg !== 'function') {
            postExport('svg', simpleSvgFallback());
            return;
        }
        postExport('svg', cy.svg({ full: true }));
    }
    function simpleSvgFallback() {
        if (!lastGraph) return '<svg xmlns="http://www.w3.org/2000/svg"/>';
        return '<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600">' +
            '<text x="20" y="40">Mitos: ' + (lastGraph.stats.nodes || 0) + ' nodes / ' +
            (lastGraph.stats.edges || 0) + ' edges</text></svg>';
    }
    function exportMermaid() {
        if (!lastGraph) return;
        const lines = ['flowchart TB'];
        lastGraph.nodes.forEach(function (n) {
            const id = sanitizeMermaidId(n.data.id);
            lines.push('  ' + id + '["' + (n.data.label || '').replace(/"/g, '\\"') + '"]');
        });
        lastGraph.edges.forEach(function (e) {
            const from = sanitizeMermaidId(e.data.source);
            const to = sanitizeMermaidId(e.data.target);
            const arrow = e.data.confidence === 'HEURISTIC' ? '-.->' : '-->';
            lines.push('  ' + from + ' ' + arrow + '|' + e.data.kind + '| ' + to);
        });
        postExport('mermaid', lines.join('\n'));
    }
    function sanitizeMermaidId(id) {
        return 'n_' + id.replace(/[^a-zA-Z0-9_]/g, '_');
    }

    // -------------------------------------------------------------- Empty state
    function showEmpty(text) {
        const el = $('empty-state');
        el.textContent = text;
        el.classList.remove('hidden');
        if (cy) { cy.elements().remove(); }
    }

    // -------------------------------------------------------------- Legend toggle
    document.addEventListener('click', function (e) {
        if (e.target && e.target.classList && e.target.classList.contains('legend-toggle')) {
            $('legend').classList.toggle('collapsed');
        }
    });

    // -------------------------------------------------------------- Public API
    window.mitos = {
        render: render,
        showEmpty: showEmpty,
        applyTheme: applyTheme,
        setLayout: setLayout,
        filter: applyFilter,
        exportPng: exportPng,
        exportSvg: exportSvg,
        exportMermaid: exportMermaid
    };

    // Register Cytoscape extensions if globals are present.
    if (typeof cytoscape !== 'undefined') {
        if (typeof cytoscapeDagre !== 'undefined') cytoscape.use(cytoscapeDagre);
        if (typeof cytoscapeCoseBilkent !== 'undefined') cytoscape.use(cytoscapeCoseBilkent);
        if (typeof cytoscapePopper !== 'undefined') {
            // cytoscape-popper v2 expects a popper factory built around Popper.createPopper.
            // v1 was register-as-is. Detect both forms.
            try {
                if (typeof Popper !== 'undefined' && Popper && Popper.createPopper) {
                    var factory = function (ref, content, opts) {
                        return Popper.createPopper(ref, content, opts || {});
                    };
                    var registered = cytoscapePopper(factory);
                    cytoscape.use(typeof registered === 'function' ? registered : cytoscapePopper);
                } else {
                    cytoscape.use(cytoscapePopper);
                }
            } catch (e) {
                cytoscape.use(cytoscapePopper);
            }
        }
        if (typeof cytoscapeSvg !== 'undefined') cytoscape.use(cytoscapeSvg);
    }

    // Default empty state until Kotlin pushes a graph.
    showEmpty('Place the caret on a method, JSP page, or JS function and press Ctrl+Alt+Shift+G.');
})();
