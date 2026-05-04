package com.vodafone.mitos.service

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.vodafone.mitos.MitosBundle
import com.vodafone.mitos.analyzer.AnalysisResult
import com.vodafone.mitos.analyzer.AnalyzerContext
import com.vodafone.mitos.analyzer.JavaAnalyzer
import com.vodafone.mitos.analyzer.JsAnalyzer
import com.vodafone.mitos.analyzer.JspAnalyzer
import com.vodafone.mitos.analyzer.LanguageAnalyzer
import com.vodafone.mitos.analyzer.PsiNodes
import com.vodafone.mitos.model.CallGraph
import com.vodafone.mitos.model.CallNode
import com.vodafone.mitos.resolver.CrossLanguageResolver
import com.vodafone.mitos.settings.MitosSettings
import java.util.ArrayDeque
import java.util.function.Consumer

/**
 * Project-level orchestrator (FR-3..6, FR-28..30). Owns analyzers, the
 * cross-language resolver, and the LRU cache. Public entry point [computeAsync]
 * runs traversal in a background `ReadAction` (NFR-3, NFR-4).
 */
@Service(Service.Level.PROJECT)
class CallGraphService(private val project: Project) {

    private val log = logger<CallGraphService>()
    private val cache = GraphCache()
    private val analyzers: List<LanguageAnalyzer> = listOf(JavaAnalyzer(), JspAnalyzer(), JsAnalyzer())

    /** True when the given element can be a graph root. */
    fun supports(element: PsiElement): Boolean = analyzers.any { it.supports(element) }

    /** Build a [CallNode] for [element] using whichever analyzer recognises it. */
    fun rootFor(element: PsiElement): CallNode? =
        analyzers.firstOrNull { it.supports(element) }?.toNode(element)

    /** Resolve [node] back to a navigable PSI element (FR-20). */
    fun resolveNode(node: CallNode): PsiElement? =
        analyzers.firstNotNullOfOrNull { it.resolve(project, node) }

    /** Drop any cached graph touching [path] (FR-29). */
    fun invalidateFile(path: String) {
        cache.invalidate { it.touches(path) }
    }

    /** Synchronous BFS used by [computeAsync] and tests. */
    fun traverse(root: CallNode, depthIn: Int, depthOut: Int): CallGraph {
        return traverse(root, depthIn, depthOut, indicator = null, started = System.currentTimeMillis())
    }

    /**
     * Compute the graph rooted at [root] asynchronously. [onResult] is
     * invoked on the EDT once the graph is ready. [onError] receives any
     * non-cancellation throwable.
     */
    fun computeAsync(
        root: CallNode,
        depthIn: Int,
        depthOut: Int,
        onResult: Consumer<CallGraph>,
        onError: Consumer<Throwable> = Consumer { log.warn(it) },
    ) {
        val settings = MitosSettings.get(project)
        val key = GraphCache.Key(root.id, depthIn, depthOut, settings.signature())
        cache.get(key)?.let {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater { onResult.accept(it) }
            return
        }

        val task = object : Task.Backgroundable(project, MitosBundle.message("notification.computing"), true) {
            override fun run(indicator: ProgressIndicator) {
                val started = System.currentTimeMillis()
                val graph: CallGraph = try {
                    ReadAction.nonBlocking<CallGraph> {
                        traverse(root, depthIn, depthOut, indicator, started)
                    }
                        .inSmartMode(project)
                        .executeSynchronously()
                } catch (t: com.intellij.openapi.progress.ProcessCanceledException) {
                    throw t
                } catch (t: Throwable) {
                    log.warn("Mitos: graph computation failed", t)
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        onError.accept(t)
                    }
                    return
                }
                cache.put(key, graph)
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    onResult.accept(graph)
                }
            }
        }
        ProgressManager.getInstance().run(task)
    }

    private fun traverse(
        root: CallNode,
        depthIn: Int,
        depthOut: Int,
        indicator: ProgressIndicator?,
        started: Long,
    ): CallGraph {
        val settings = MitosSettings.get(project)
        val resolver = CrossLanguageResolver(project, settings)
        val builder = CallGraph.Builder(root)

        val ctx = AnalyzerContext(settings, resolver) { name, qn, kind, lang, path, off, line ->
            PsiNodes.build(name, qn, kind, lang, path, off, line)
        }

        // ---- Outgoing BFS (callees) ------------------------------------------------
        run {
            val queue = ArrayDeque<Pair<CallNode, Int>>().apply { add(root to 0) }
            val visited = HashSet<String>().apply { add(root.id) }
            while (queue.isNotEmpty()) {
                indicator?.checkCanceled()
                val (current, d) = queue.poll()
                if (d >= depthOut) continue
                val edges = analyzers.flatMap { safe(builder) { it.outgoing(project, current, ctx).edges } }
                for (pair in edges) {
                    if (builder.size() >= settings.maxNodes) { builder.markTruncated(); break }
                    builder.addNode(pair.other).addEdge(pair.edge)
                    if (visited.add(pair.other.id)) queue.add(pair.other to d + 1)
                }
                if (builder.truncated()) break
            }
        }

        // ---- Incoming BFS (callers) ------------------------------------------------
        run {
            val queue = ArrayDeque<Pair<CallNode, Int>>().apply { add(root to 0) }
            val visited = HashSet<String>().apply { add(root.id) }
            while (queue.isNotEmpty()) {
                indicator?.checkCanceled()
                val (current, d) = queue.poll()
                if (d >= depthIn) continue
                val edges = analyzers.flatMap { safe(builder) { it.incoming(project, current, ctx).edges } }
                for (pair in edges) {
                    if (builder.size() >= settings.maxNodes) { builder.markTruncated(); break }
                    builder.addNode(pair.other).addEdge(pair.edge)
                    if (visited.add(pair.other.id)) queue.add(pair.other to d + 1)
                }
                if (builder.truncated()) break
            }
        }

        val graph = builder.build(System.currentTimeMillis() - started)
        log.info("Mitos: graph built — ${graph.nodeCount} nodes / ${graph.edgeCount} edges in ${graph.computeMillis} ms")
        return graph
    }

    private fun <T> safe(builder: CallGraph.Builder, op: () -> List<T>): List<T> = try {
        op()
    } catch (t: com.intellij.openapi.progress.ProcessCanceledException) {
        throw t
    } catch (t: Throwable) {
        log.info("Mitos: analyzer failure (continuing with partial graph)", t)
        builder.warn(t.javaClass.simpleName + ": " + (t.message ?: ""))
        emptyList()
    }

    companion object {
        fun get(project: Project): CallGraphService = project.getService(CallGraphService::class.java)
    }
}
