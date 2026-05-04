package com.vodafone.mitos.analyzer

import com.vodafone.mitos.model.CallEdge
import com.vodafone.mitos.model.CallNode

/**
 * Bundle returned by [LanguageAnalyzer.outgoing] / [LanguageAnalyzer.incoming]:
 * each edge plus the node sitting at the *other* end of it. The service uses
 * the nodes to recurse the BFS frontier.
 */
data class AnalysisResult(val edges: List<EdgeWithNode>) {
    data class EdgeWithNode(val edge: CallEdge, val other: CallNode)

    companion object {
        val EMPTY = AnalysisResult(emptyList())
        fun of(pairs: List<EdgeWithNode>) = AnalysisResult(pairs)
    }
}
