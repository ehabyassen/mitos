package com.vodafone.mitos.analyzer

import com.vodafone.mitos.model.CallNode
import com.vodafone.mitos.resolver.CrossLanguageResolver
import com.vodafone.mitos.settings.MitosSettingsState

/**
 * Per-traversal context handed to each analyzer. Carries the user settings,
 * the cross-language resolver bridge, and a sink that lets analyzers register
 * newly-discovered nodes (which the service then traverses next).
 */
class AnalyzerContext(
    val settings: MitosSettingsState,
    val resolver: CrossLanguageResolver,
    val nodeFactory: NodeFactory,
) {
    fun interface NodeFactory {
        fun create(displayName: String, qualifiedName: String, kind: com.vodafone.mitos.model.NodeKind,
                   language: com.vodafone.mitos.model.Language, filePath: String, offset: Int,
                   lineNumber: Int): CallNode
    }
}
