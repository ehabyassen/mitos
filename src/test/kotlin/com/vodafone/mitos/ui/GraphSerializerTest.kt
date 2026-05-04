package com.vodafone.mitos.ui

import com.vodafone.mitos.model.CallEdge
import com.vodafone.mitos.model.CallGraph
import com.vodafone.mitos.model.CallNode
import com.vodafone.mitos.model.EdgeKind
import com.vodafone.mitos.model.Language
import com.vodafone.mitos.model.NodeKind
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphSerializerTest {

    private fun node(id: String) = CallNode(
        id = id, displayName = id, qualifiedName = id, kind = NodeKind.METHOD,
        language = Language.JAVA, filePath = "/p/A.java", offset = 0, lineNumber = 1,
    )

    @Test fun `payload contains required keys`() {
        val root = node("root")
        val builder = CallGraph.Builder(root).addNode(node("a"))
            .addEdge(CallEdge("root", "a", EdgeKind.DIRECT_CALL,
                callSiteSnippet = "a()", callSiteFile = "/p/A.java", callSiteLine = 7))
        val json = GraphSerializer.toJson(builder.build(42))
        assertTrue(json.contains("\"root\": \"root\""))
        assertTrue(json.contains("\"nodes\""))
        assertTrue(json.contains("\"edges\""))
        assertTrue(json.contains("\"computeMillis\": 42"))
        assertTrue(json.contains("\"confidence\": \"CONFIDENT\""))
    }

    @Test fun `quote escapes control characters and quotes`() {
        val root = node("root")
        val tricky = node("a").copy(displayName = "tab\there\nnext\"end")
        val builder = CallGraph.Builder(root).addNode(tricky)
        val json = GraphSerializer.toJson(builder.build(0))
        assertTrue(json.contains("\\t"))
        assertTrue(json.contains("\\n"))
        assertTrue(json.contains("\\\""))
    }

    @Test fun `heuristic edge serializes its confidence`() {
        val root = node("root")
        val builder = CallGraph.Builder(root).addNode(node("a"))
            .addEdge(CallEdge("root", "a", EdgeKind.AJAX_REQUEST))
        val json = GraphSerializer.toJson(builder.build(0))
        assertTrue(json.contains("\"confidence\": \"HEURISTIC\""))
    }
}
