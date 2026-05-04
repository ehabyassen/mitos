package com.vodafone.mitos.ui

import com.vodafone.mitos.model.CallEdge
import com.vodafone.mitos.model.CallGraph
import com.vodafone.mitos.model.CallNode

/**
 * Serializes a [CallGraph] into the JSON shape consumed by `mitos.js`.
 * Hand-rolled to avoid pulling in a JSON dependency for a tiny payload.
 */
internal object GraphSerializer {

    fun toJson(graph: CallGraph): String {
        val nodes = graph.nodes.values.joinToString(",") { nodeJson(it, isRoot = it.id == graph.root.id) }
        val edges = graph.edges.joinToString(",") { edgeJson(it) }
        val warnings = graph.warnings.joinToString(",") { quote(it) }
        return """
            {
              "root": ${quote(graph.root.id)},
              "nodes": [$nodes],
              "edges": [$edges],
              "truncated": ${graph.truncated},
              "warnings": [$warnings],
              "computeMillis": ${graph.computeMillis},
              "stats": { "nodes": ${graph.nodeCount}, "edges": ${graph.edgeCount} }
            }
        """.trimIndent()
    }

    private fun nodeJson(n: CallNode, isRoot: Boolean): String = """
        {
          "data": {
            "id": ${quote(n.id)},
            "label": ${quote(n.displayName)},
            "qualifiedName": ${quote(n.qualifiedName)},
            "kind": ${quote(n.kind.name)},
            "language": ${quote(n.language.name)},
            "filePath": ${quote(n.filePath)},
            "offset": ${n.offset},
            "line": ${n.lineNumber},
            "tooltip": ${quote(n.tooltip)},
            "isRoot": $isRoot
          }
        }
    """.trimIndent()

    private fun edgeJson(e: CallEdge): String = """
        {
          "data": {
            "id": ${quote("${e.fromId}->${e.toId}@${e.kind}")},
            "source": ${quote(e.fromId)},
            "target": ${quote(e.toId)},
            "kind": ${quote(e.kind.name)},
            "confidence": ${quote(e.kind.confidence.name)},
            "weight": ${e.weight},
            "snippet": ${quote(e.callSiteSnippet ?: "")},
            "callSiteFile": ${quote(e.callSiteFile ?: "")},
            "callSiteLine": ${e.callSiteLine ?: 0}
          }
        }
    """.trimIndent()

    private fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when {
                c == '\\' -> sb.append("\\\\")
                c == '"' -> sb.append("\\\"")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c == '\b' -> sb.append("\\b")
                c == '\u000C' -> sb.append("\\f")
                c.code < 0x20 -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
