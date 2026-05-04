package com.vodafone.mitos.model

/**
 * A directed edge in the call graph. Identity is `(fromId, toId, kind)` per
 * FR-5 so that two analyzers reporting the same edge collapse into one.
 *
 * [callSiteSnippet] is a short source excerpt from the call site, surfaced in
 * the edge tooltip per FR-19. May be null when the edge is structural (e.g.
 * `JSP_INCLUDE`).
 */
data class CallEdge(
    val fromId: String,
    val toId: String,
    val kind: EdgeKind,
    val callSiteSnippet: String? = null,
    val callSiteFile: String? = null,
    val callSiteLine: Int? = null,
    /** Number of merged occurrences. Drives edge thickness in the renderer. */
    val weight: Int = 1,
) {
    fun mergedWith(other: CallEdge): CallEdge {
        require(fromId == other.fromId && toId == other.toId && kind == other.kind)
        return copy(weight = weight + other.weight)
    }
}
