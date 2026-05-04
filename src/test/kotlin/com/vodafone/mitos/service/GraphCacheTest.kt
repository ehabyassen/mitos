package com.vodafone.mitos.service

import com.vodafone.mitos.model.CallGraph
import com.vodafone.mitos.model.CallNode
import com.vodafone.mitos.model.Language
import com.vodafone.mitos.model.NodeKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GraphCacheTest {

    private fun graph(rootPath: String): CallGraph {
        val root = CallNode("r", "r", "r", NodeKind.METHOD, Language.JAVA, rootPath, 0, 1)
        return CallGraph.Builder(root).build(0)
    }

    @Test fun `LRU evicts oldest beyond capacity`() {
        val cache = GraphCache(capacity = 2)
        val k1 = GraphCache.Key("a", 0, 0, 0)
        val k2 = GraphCache.Key("b", 0, 0, 0)
        val k3 = GraphCache.Key("c", 0, 0, 0)
        cache.put(k1, graph("/a"))
        cache.put(k2, graph("/b"))
        cache.put(k3, graph("/c"))
        assertNull(cache.get(k1), "k1 should have been evicted")
        assertNotNull(cache.get(k2))
        assertNotNull(cache.get(k3))
    }

    @Test fun `invalidate removes entries that touch the path`() {
        val cache = GraphCache(capacity = 4)
        val k = GraphCache.Key("a", 0, 0, 0)
        cache.put(k, graph("/x.java"))
        cache.invalidate { it.touches("/x.java") }
        assertNull(cache.get(k))
    }

    @Test fun `invalidate keeps unrelated entries`() {
        val cache = GraphCache(capacity = 4)
        val k = GraphCache.Key("a", 0, 0, 0)
        cache.put(k, graph("/x.java"))
        cache.invalidate { it.touches("/other.java") }
        assertEquals(graph("/x.java").root.id, cache.get(k)?.root?.id)
    }
}
