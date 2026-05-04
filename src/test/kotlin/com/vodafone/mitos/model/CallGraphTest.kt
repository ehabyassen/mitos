package com.vodafone.mitos.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallGraphTest {

    private fun node(id: String, path: String = "/p/A.java") = CallNode(
        id = id, displayName = id, qualifiedName = id, kind = NodeKind.METHOD,
        language = Language.JAVA, filePath = path, offset = 0, lineNumber = 1,
    )

    @Test fun `builder dedupes nodes`() {
        val root = node("root")
        val b = CallGraph.Builder(root)
        b.addNode(node("a")).addNode(node("a"))
        val g = b.build(0)
        assertEquals(2, g.nodeCount, "root + a only")
    }

    @Test fun `builder merges weights for repeated edges`() {
        val root = node("root")
        val b = CallGraph.Builder(root)
        b.addEdge(CallEdge("root", "a", EdgeKind.DIRECT_CALL))
        b.addEdge(CallEdge("root", "a", EdgeKind.DIRECT_CALL))
        b.addEdge(CallEdge("root", "a", EdgeKind.DIRECT_CALL))
        val g = b.build(0)
        assertEquals(1, g.edges.size)
        assertEquals(3, g.edges[0].weight)
    }

    @Test fun `builder keeps separate edges for different kinds`() {
        val root = node("root")
        val b = CallGraph.Builder(root)
        b.addEdge(CallEdge("root", "a", EdgeKind.DIRECT_CALL))
        b.addEdge(CallEdge("root", "a", EdgeKind.AJAX_REQUEST))
        val g = b.build(0)
        assertEquals(2, g.edges.size)
    }

    @Test fun `touches matches any node file path`() {
        val root = node("root", "/p/A.java")
        val b = CallGraph.Builder(root).addNode(node("b", "/p/B.java"))
        val g = b.build(0)
        assertTrue(g.touches("/p/A.java"))
        assertTrue(g.touches("/p/B.java"))
        assertEquals(false, g.touches("/p/Other.java"))
    }
}
