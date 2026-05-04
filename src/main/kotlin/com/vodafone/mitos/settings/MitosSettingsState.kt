package com.vodafone.mitos.settings

/**
 * Plain settings record persisted by [MitosSettings]. Kept as a `data class`
 * with all-default-able fields so XML deserialization can default missing
 * entries when settings format evolves.
 */
data class MitosSettingsState(
    var viewResolverPrefix: String = "/WEB-INF/views/",
    var viewResolverSuffix: String = ".jsp",
    var packageIncludeRegex: String = "",
    var packageExcludeRegex: String = "",
    var jspAnalyzerEnabled: Boolean = true,
    var jsAnalyzerEnabled: Boolean = true,
    var maxNodes: Int = 200,
    var defaultDepthIn: Int = 2,
    var defaultDepthOut: Int = 2,
    var defaultLayout: String = "dagre",
    var animationsEnabled: Boolean = true,
) {
    /** Stable hash used as part of the cache key (FR-28). */
    fun signature(): Int = listOf(
        viewResolverPrefix, viewResolverSuffix, packageIncludeRegex, packageExcludeRegex,
        jspAnalyzerEnabled, jsAnalyzerEnabled, maxNodes,
    ).hashCode()

    companion object {
        const val MAX_NODES_HARD_CEILING: Int = 1000
        const val MAX_DEPTH: Int = 5
    }
}
