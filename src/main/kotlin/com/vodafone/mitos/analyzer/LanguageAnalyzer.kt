package com.vodafone.mitos.analyzer

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.vodafone.mitos.model.CallNode

/**
 * One per supported language. The service drives traversal; analyzers report
 * edges plus the node on the other end so the BFS frontier can advance.
 *
 * Implementations MUST run only inside a `ReadAction` and MUST avoid storing
 * `PsiElement` references between calls (see SRS §2.5). Use `(filePath, offset)`.
 */
interface LanguageAnalyzer {
    /** Whether this analyzer recognises [element] as a candidate root or graph node. */
    fun supports(element: PsiElement): Boolean

    /** Construct the [CallNode] representing [element], or null if unsupported. */
    fun toNode(element: PsiElement): CallNode?

    /** Re-resolve a previously emitted [CallNode] back to a navigable [PsiElement]. */
    fun resolve(project: Project, node: CallNode): PsiElement?

    /** Edges leaving [node] (callees), each paired with its target node. */
    fun outgoing(project: Project, node: CallNode, ctx: AnalyzerContext): AnalysisResult

    /** Edges arriving at [node] (callers), each paired with its source node. */
    fun incoming(project: Project, node: CallNode, ctx: AnalyzerContext): AnalysisResult
}
