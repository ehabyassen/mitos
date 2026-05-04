package com.vodafone.mitos.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.vodafone.mitos.MitosBundle
import com.vodafone.mitos.model.CallGraph
import com.vodafone.mitos.ui.theme.ThemeBridge
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Embeds Cytoscape.js inside a [JBCefBrowser]. Owns the JS↔Kotlin bridge:
 *   - Kotlin → JS: `executeJavaScript("mitos.render(${json})")`.
 *   - JS → Kotlin: registers two [JBCefJSQuery]s that JS calls on node click and on
 *     theme/layout requests.
 *
 * Falls back to an error label when JCEF is unavailable on the host JVM
 * (NFR-5).
 */
internal class GraphPanel(private val project: Project, parentDisposable: Disposable) : Disposable {

    private val log = logger<GraphPanel>()
    private val nav = NavigationHandler(project)

    val component: JComponent

    private val browser: JBCefBrowser?
    private val navigateQuery: JBCefJSQuery?
    private val exportQuery: JBCefJSQuery?
    private var pendingGraph: CallGraph? = null
    private var pageReady: Boolean = false

    init {
        Disposer.register(parentDisposable, this)
        if (!JBCefApp.isSupported()) {
            browser = null
            navigateQuery = null
            exportQuery = null
            component = JPanel(BorderLayout()).apply {
                add(JLabel("JCEF is not available — Mitos requires a JCEF-enabled IDE build."), BorderLayout.CENTER)
            }
            log.warn("JCEF not available; Mitos cannot render graphs")
        } else {
            val b = JBCefBrowser()
            browser = b
            navigateQuery = JBCefJSQuery.create(b as JBCefBrowser)
            exportQuery = JBCefJSQuery.create(b as JBCefBrowser)
            navigateQuery.addHandler { msg ->
                handleNavigate(msg)
                JBCefJSQuery.Response("ok")
            }
            exportQuery.addHandler { msg ->
                handleExport(msg)
                JBCefJSQuery.Response("ok")
            }

            // Wait until the page is loaded before pushing graphs.
            b.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    pageReady = true
                    installBridge()
                    pushTheme()
                    pendingGraph?.let { renderInternal(it) }
                }
            }, b.cefBrowser)

            // React to IntelliJ theme changes (FR-21d).
            ApplicationManager.getApplication().messageBus.connect(this)
                .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { _: EditorColorsScheme? -> pushTheme() })

            b.loadHTML(loadIndex())
            component = b.component
        }
    }

    fun render(graph: CallGraph) {
        pendingGraph = graph
        if (pageReady) renderInternal(graph)
    }

    fun showEmptyState() {
        if (browser == null) return
        if (!pageReady) return
        browser.cefBrowser.executeJavaScript("window.mitos && mitos.showEmpty(${quoteJs(MitosBundle.message("toolwindow.empty"))});", "", 0)
    }

    /**
     * Forward a toolbar action to the renderer. [name] is a JS function name
     * exposed by `mitos`, [jsArg] is a literal JS expression (already escaped
     * by the caller). Empty string means "no argument".
     */
    fun sendCommand(name: String, jsArg: String) {
        if (browser == null || !pageReady) return
        val js = if (jsArg.isBlank()) "window.mitos && mitos.$name();"
                 else "window.mitos && mitos.$name($jsArg);"
        browser.cefBrowser.executeJavaScript(js, "", 0)
    }

    private fun renderInternal(graph: CallGraph) {
        if (browser == null) return
        val json = GraphSerializer.toJson(graph)
        val js = "window.mitos && mitos.render($json);"
        browser.cefBrowser.executeJavaScript(js, "", 0)
    }

    private fun pushTheme() {
        if (browser == null || !pageReady) return
        val p = ThemeBridge.current()
        val js = """
            window.mitos && mitos.applyTheme({
              isDark: ${p.isDark},
              background: '${p.background}',
              foreground: '${p.foreground}',
              grid: '${p.grid}',
              tooltipBackground: '${p.tooltipBackground}',
              tooltipForeground: '${p.tooltipForeground}',
              rootGlow: '${p.rootGlow}'
            });
        """.trimIndent()
        browser.cefBrowser.executeJavaScript(js, "", 0)
    }

    /** Wires the JS callbacks via window-level functions (FR-20, FR-24/25). */
    private fun installBridge() {
        if (browser == null || navigateQuery == null || exportQuery == null) return
        val navJs = navigateQuery.inject("payload",
            "function(response){}", "function(error_code, error_message){}")
        val exportJs = exportQuery.inject("payload",
            "function(response){}", "function(error_code, error_message){}")
        val script = """
            window.__mitosNavigate = function(payload) { $navJs };
            window.__mitosExport = function(payload) { $exportJs };
        """.trimIndent()
        browser.cefBrowser.executeJavaScript(script, "", 0)
    }

    private fun handleNavigate(message: String) {
        // payload is JSON: {"id":"…","filePath":"…","offset":N}
        val id = message.extractField("id") ?: return
        val path = message.extractField("filePath") ?: ""
        val off = message.extractField("offset")?.toIntOrNull() ?: 0
        nav.navigate(id, path, off)
    }

    private fun handleExport(message: String) {
        // payload format: {"format":"png|svg|mermaid","content":"…"}
        val format = message.extractField("format") ?: return
        val content = message.extractField("content") ?: return
        ApplicationManager.getApplication().invokeLater {
            ExportDialog.save(project, format, content)
        }
    }

    /**
     * Builds a self-contained HTML page by inlining mitos.css and every
     * bundled JS into the index template. JBCefBrowser.loadHTML provides no
     * base URL, so relative <script src> / <link href> tags can't resolve
     * back into the plugin JAR — inlining is the simplest reliable path.
     */
    private fun loadIndex(): String {
        val cssPath = "web/mitos.css"
        val css = readResource(cssPath) ?: ""
        val libOrder = listOf(
            "web/lib/cytoscape.min.js",
            "web/lib/dagre.min.js",
            "web/lib/cytoscape-dagre.js",
            "web/lib/cytoscape-cose-bilkent.js",
            "web/lib/popper.min.js",
            "web/lib/cytoscape-popper.js",
            "web/lib/cytoscape-svg.js",
        )
        val libs = libOrder.joinToString("\n") { path ->
            val body = readResource(path)
            if (body == null) "<!-- missing $path -->" else "<script>\n$body\n</script>"
        }
        val app = readResource("web/mitos.js") ?: ""
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<title>Mitos</title>
<style>
$css
</style>
</head>
<body>
<div id="app">
    <div id="legend" class="overlay collapsed">
        <button class="legend-toggle" aria-label="Toggle legend">Legend ▾</button>
        <div class="legend-body" id="legend-body"></div>
    </div>
    <div id="banner" class="overlay banner hidden"></div>
    <div id="empty-state" class="overlay center hidden"></div>
    <div id="cy"></div>
    <div id="minimap" class="overlay"></div>
</div>
$libs
<script>
$app
</script>
</body>
</html>
""".trimIndent()
    }

    private fun readResource(path: String): String? =
        javaClass.classLoader.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }

    override fun dispose() {
        navigateQuery?.let { Disposer.dispose(it) }
        exportQuery?.let { Disposer.dispose(it) }
        browser?.let { Disposer.dispose(it) }
    }

    companion object {
        private fun quoteJs(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        /**
         * Tiny field extractor for the small JSON payloads we exchange with JS.
         * Matches `"key": "string"` or `"key": number`.
         */
        private fun String.extractField(key: String): String? {
            val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|(-?\\d+))")
            val m = pattern.find(this) ?: return null
            return m.groupValues[2].ifEmpty { m.groupValues[3] }
        }
    }
}
