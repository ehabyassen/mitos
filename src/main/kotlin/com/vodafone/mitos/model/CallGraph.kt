package com.vodafone.mitos.model

/**
 * Immutable snapshot of a computed call graph. Built incrementally inside
 * `CallGraphService` then frozen for delivery to the UI.
 *
 * @property root the node corresponding to the symbol under the caret
 *   when the graph was computed (FR-1).
 * @property nodes deduplicated by [CallNode.id].
 * @property edges deduplicated and merged by `(fromId, toId, kind)`.
 * @property truncated true when traversal hit the `maxNodes` cap (FR-6).
 * @property warnings collected per-analyzer non-fatal failures (NFR-5).
 * @property computeMillis wall-clock time spent computing the graph;
 *   surfaced in the tool window status bar.
 */
data class CallGraph(
    val root: CallNode,
    val nodes: Map<String, CallNode>,
    val edges: List<CallEdge>,
    val truncated: Boolean = false,
    val warnings: List<String> = emptyList(),
    val computeMillis: Long = 0,
) {
    val nodeCount: Int get() = nodes.size
    val edgeCount: Int get() = edges.size

    fun touches(filePath: String): Boolean = nodes.values.any { it.filePath == filePath }

    class Builder(private val root: CallNode) {
        private val nodes = LinkedHashMap<String, CallNode>().apply { put(root.id, root) }
        private val edgeIndex = LinkedHashMap<Triple<String, String, EdgeKind>, CallEdge>()
        private val warnings = mutableListOf<String>()
        private var truncated = false

        fun addNode(node: CallNode): Builder {
            nodes.putIfAbsent(node.id, node)
            return this
        }

        fun addEdge(edge: CallEdge): Builder {
            val key = Triple(edge.fromId, edge.toId, edge.kind)
            val existing = edgeIndex[key]
            edgeIndex[key] = existing?.mergedWith(edge) ?: edge
            return this
        }

        fun warn(message: String): Builder {
            warnings += message
            return this
        }

        fun markTruncated(): Builder {
            truncated = true
            return this
        }

        fun truncated(): Boolean = truncated

        fun size(): Int = nodes.size

        fun build(computeMillis: Long): CallGraph =
            CallGraph(
                root = root,
                nodes = nodes.toMap(),
                edges = edgeIndex.values.toList(),
                truncated = truncated,
                warnings = warnings.toList(),
                computeMillis = computeMillis,
            )
    }
}
