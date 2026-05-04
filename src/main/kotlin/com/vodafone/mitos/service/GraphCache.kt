package com.vodafone.mitos.service

import com.vodafone.mitos.model.CallGraph

/**
 * LRU cache of computed graphs (FR-28..30). Keys collapse the root node id,
 * the requested depths, and the settings signature so that flipping a
 * filter regenerates the graph.
 */
internal class GraphCache(private val capacity: Int = DEFAULT_CAPACITY) {
    data class Key(val rootId: String, val depthIn: Int, val depthOut: Int, val settingsHash: Int)

    private val map: LinkedHashMap<Key, CallGraph> =
        object : LinkedHashMap<Key, CallGraph>(16, 0.75f, /* accessOrder = */ true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, CallGraph>?): Boolean =
                size > capacity
        }

    @Synchronized fun get(key: Key): CallGraph? = map[key]
    @Synchronized fun put(key: Key, value: CallGraph) { map[key] = value }
    @Synchronized fun invalidate(predicate: (CallGraph) -> Boolean) {
        val it = map.entries.iterator()
        while (it.hasNext()) if (predicate(it.next().value)) it.remove()
    }
    @Synchronized fun clear() { map.clear() }

    companion object { const val DEFAULT_CAPACITY = 16 }
}
