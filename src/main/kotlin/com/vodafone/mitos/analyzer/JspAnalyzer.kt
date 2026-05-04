package com.vodafone.mitos.analyzer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.vodafone.mitos.analyzer.AnalysisResult.EdgeWithNode
import com.vodafone.mitos.model.CallEdge
import com.vodafone.mitos.model.CallNode
import com.vodafone.mitos.model.EdgeKind
import com.vodafone.mitos.model.Language
import com.vodafone.mitos.model.NodeKind

/**
 * JSP analyzer. Recognises a JSP page as a node and walks `<jsp:include>`,
 * `<%@ include %>`, EL, scriptlets, `<script src=…>`, and inline event-handler
 * attributes. Cross-language bridges are delegated to [com.vodafone.mitos.resolver.CrossLanguageResolver].
 */
class JspAnalyzer : LanguageAnalyzer {
    override fun supports(element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        val ext = file.virtualFile?.extension?.lowercase() ?: return false
        return ext == "jsp" || ext == "jspx"
    }

    override fun toNode(element: PsiElement): CallNode? {
        val file: PsiFile = element.containingFile ?: return null
        val ext = file.virtualFile?.extension?.lowercase() ?: return null
        if (ext != "jsp" && ext != "jspx") return null
        val path = PsiNodes.pathOf(file)
        return PsiNodes.build(
            displayName = file.name,
            qualifiedName = path,
            kind = NodeKind.JSP_PAGE,
            language = Language.JSP,
            filePath = path,
            offset = 0,
            lineNumber = 1,
            tooltip = "JSP page · $path",
        )
    }

    override fun resolve(project: Project, node: CallNode): PsiElement? {
        val vf = LocalFileSystem.getInstance().findFileByPath(node.filePath) ?: return null
        return PsiManager.getInstance(project).findFile(vf)
    }

    override fun outgoing(project: Project, node: CallNode, ctx: AnalyzerContext): AnalysisResult {
        if (!ctx.settings.jspAnalyzerEnabled) return AnalysisResult.EMPTY
        val file = resolve(project, node) as? PsiFile ?: return AnalysisResult.EMPTY
        val pairs = mutableListOf<EdgeWithNode>()

        // FR-10: <jsp:include page="…">
        PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).forEach { tag ->
            val name = tag.name
            if (name == "jsp:include" || name == "jsp:directive.include") {
                val pageAttr: XmlAttribute? = tag.getAttribute("page") ?: tag.getAttribute("file")
                val path = pageAttr?.value ?: return@forEach
                val target = ctx.resolver.resolveJspInclude(file, path) ?: return@forEach
                pairs += EdgeWithNode(
                    edge = CallEdge(node.id, target.id, EdgeKind.JSP_INCLUDE,
                        tag.text.take(120), node.filePath, PsiNodes.lineOf(tag)),
                    other = target,
                )
            }
        }

        // FR-7..9: useBean, EL, scriptlets
        pairs += ctx.resolver.jspToJava(file, node).edges

        // FR-11: <script src="…"> and inline event handlers
        pairs += ctx.resolver.jspToJs(file, node).edges

        // FR-10b: <%@ include file="…" %>
        pairs += ctx.resolver.directiveIncludes(file, node).edges

        return AnalysisResult(pairs)
    }

    override fun incoming(project: Project, node: CallNode, ctx: AnalyzerContext): AnalysisResult {
        // Pages are reached via Spring view resolution (FR-12) and forwards (FR-13).
        // Both are emitted as outgoing edges by JavaAnalyzer; nothing to do here.
        return AnalysisResult.EMPTY
    }
}
