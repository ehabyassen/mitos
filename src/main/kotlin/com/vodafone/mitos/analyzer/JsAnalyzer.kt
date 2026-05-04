package com.vodafone.mitos.analyzer

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.vodafone.mitos.analyzer.AnalysisResult.EdgeWithNode
import com.vodafone.mitos.model.CallEdge
import com.vodafone.mitos.model.CallNode
import com.vodafone.mitos.model.EdgeKind
import com.vodafone.mitos.model.Language
import com.vodafone.mitos.model.NodeKind

/** JavaScript analyzer (Ultimate-only PSI). */
class JsAnalyzer : LanguageAnalyzer {
    override fun supports(element: PsiElement): Boolean = element is JSFunction

    override fun toNode(element: PsiElement): CallNode? {
        if (element !is JSFunction) return null
        val file = PsiNodes.pathOf(element.containingFile)
        val name = element.name ?: "anonymous"
        return PsiNodes.build(
            displayName = "$name()",
            qualifiedName = "${element.containingFile?.name ?: "?"}::$name",
            kind = NodeKind.JS_FUNCTION,
            language = Language.JAVASCRIPT,
            filePath = file,
            offset = element.textOffset,
            lineNumber = PsiNodes.lineOf(element),
        )
    }

    override fun resolve(project: Project, node: CallNode): PsiElement? {
        val vf = LocalFileSystem.getInstance().findFileByPath(node.filePath) ?: return null
        val psi: PsiFile = PsiManager.getInstance(project).findFile(vf) ?: return null
        val at = psi.findElementAt(node.offset) ?: return null
        return PsiTreeUtil.getParentOfType(at, JSFunction::class.java)
    }

    override fun outgoing(project: Project, node: CallNode, ctx: AnalyzerContext): AnalysisResult {
        if (!ctx.settings.jsAnalyzerEnabled) return AnalysisResult.EMPTY
        val element = resolve(project, node) as? JSFunction ?: return AnalysisResult.EMPTY
        val pairs = mutableListOf<EdgeWithNode>()

        PsiTreeUtil.findChildrenOfType(element, JSCallExpression::class.java).forEach { call ->
            val ref = call.methodExpression as? JSReferenceExpression ?: return@forEach
            val resolved = ref.resolve() as? JSFunction ?: return@forEach
            val to = toNode(resolved) ?: return@forEach
            pairs += EdgeWithNode(
                edge = CallEdge(
                    fromId = node.id,
                    toId = to.id,
                    kind = EdgeKind.JS_INVOCATION,
                    callSiteSnippet = call.text.take(120),
                    callSiteFile = node.filePath,
                    callSiteLine = PsiNodes.lineOf(call),
                ),
                other = to,
            )
        }

        // FR-14: AJAX → Java best-effort
        pairs += ctx.resolver.jsAjaxToJava(element, node).edges

        return AnalysisResult(pairs)
    }

    override fun incoming(project: Project, node: CallNode, ctx: AnalyzerContext): AnalysisResult {
        if (!ctx.settings.jsAnalyzerEnabled) return AnalysisResult.EMPTY
        val element = resolve(project, node) as? JSFunction ?: return AnalysisResult.EMPTY
        val scope = GlobalSearchScope.projectScope(project)
        val pairs = mutableListOf<EdgeWithNode>()
        ReferencesSearch.search(element, scope, true).forEach { ref ->
            val site = ref.element
            val container = PsiTreeUtil.getParentOfType(site, JSFunction::class.java)
            if (container != null) {
                val from = toNode(container) ?: return@forEach
                pairs += EdgeWithNode(
                    edge = CallEdge(from.id, node.id, EdgeKind.JS_INVOCATION,
                        site.text.take(120), from.filePath, PsiNodes.lineOf(site)),
                    other = from,
                )
            } else {
                ctx.resolver.foreignJsCallerEdge(site, node)?.let { pairs += it }
            }
        }
        return AnalysisResult(pairs)
    }
}
