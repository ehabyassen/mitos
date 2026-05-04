package com.vodafone.mitos.model

/**
 * A vertex in the call graph. Identity ([id]) is stable across PSI reloads and
 * derives from `(virtualFilePath, offset, kind)` per FR-5 — the only fields
 * that survive PSI invalidation. Everything else is presentational.
 *
 * Holding a [PsiElement] across read actions is illegal; navigation goes
 * through `(filePath, offset)` and re-resolves on demand.
 */
data class CallNode(
    val id: String,
    val displayName: String,
    val qualifiedName: String,
    val kind: NodeKind,
    val language: Language,
    val filePath: String,
    val offset: Int,
    val lineNumber: Int,
    val tooltip: String = qualifiedName,
) {
    companion object {
        fun makeId(filePath: String, offset: Int, kind: NodeKind): String =
            "${kind.name}@$filePath#$offset"
    }
}
