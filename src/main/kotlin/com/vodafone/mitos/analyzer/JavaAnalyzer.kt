package com.vodafone.mitos.analyzer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.vodafone.mitos.analyzer.AnalysisResult.EdgeWithNode
import com.vodafone.mitos.model.CallEdge
import com.vodafone.mitos.model.CallNode
import com.vodafone.mitos.model.EdgeKind
import com.vodafone.mitos.model.Language
import com.vodafone.mitos.model.NodeKind

/** Java analyzer (FR-5, FR-12, FR-13, plus the Java side of FR-7..11/14). */
class JavaAnalyzer : LanguageAnalyzer {
    override fun supports(element: PsiElement): Boolean =
        element is PsiMethod || element is PsiField

    override fun toNode(element: PsiElement): CallNode? = when (element) {
        is PsiMethod -> methodNode(element)
        is PsiField -> fieldNode(element)
        else -> null
    }

    override fun resolve(project: Project, node: CallNode): PsiElement? {
        val vf = LocalFileSystem.getInstance().findFileByPath(node.filePath) ?: return null
        val psi: PsiFile = PsiManager.getInstance(project).findFile(vf) ?: return null
        val at = psi.findElementAt(node.offset) ?: return null
        return PsiTreeUtil.getParentOfType(at, PsiMethod::class.java, PsiField::class.java)
    }

    override fun outgoing(project: Project, node: CallNode, ctx: AnalyzerContext): AnalysisResult {
        val element = resolve(project, node) ?: return AnalysisResult.EMPTY
        if (element !is PsiMethod) return AnalysisResult.EMPTY
        val pairs = mutableListOf<EdgeWithNode>()

        // Direct method calls
        PsiTreeUtil.findChildrenOfType(element, PsiMethodCallExpression::class.java).forEach { call ->
            val target = call.resolveMethod() ?: return@forEach
            val toNode = methodNode(target)
            pairs += EdgeWithNode(
                edge = CallEdge(
                    fromId = node.id,
                    toId = toNode.id,
                    kind = EdgeKind.DIRECT_CALL,
                    callSiteSnippet = call.text.take(120),
                    callSiteFile = node.filePath,
                    callSiteLine = PsiNodes.lineOf(call),
                ),
                other = toNode,
            )
        }

        // FR-12: controller → JSP view
        if (isControllerMapping(element)) {
            pairs += ctx.resolver.controllerToView(element, node).edges
        }

        // FR-13: RequestDispatcher.forward / .include
        pairs += ctx.resolver.dispatcherForwards(element, node).edges

        return AnalysisResult(pairs)
    }

    override fun incoming(project: Project, node: CallNode, ctx: AnalyzerContext): AnalysisResult {
        val element = resolve(project, node) ?: return AnalysisResult.EMPTY
        if (element !is PsiMethod) return AnalysisResult.EMPTY
        val scope = GlobalSearchScope.projectScope(project)
        val pairs = mutableListOf<EdgeWithNode>()

        MethodReferencesSearch.search(element, scope, true).forEach { ref ->
            referenceToEdge(ref, node, ctx)?.let { pairs += it }
        }
        return AnalysisResult(pairs)
    }

    private fun referenceToEdge(ref: PsiReference, node: CallNode, ctx: AnalyzerContext): EdgeWithNode? {
        val callSite = ref.element
        val containingMethod = PsiTreeUtil.getParentOfType(callSite, PsiMethod::class.java)
        if (containingMethod != null) {
            val from = methodNode(containingMethod)
            return EdgeWithNode(
                edge = CallEdge(
                    fromId = from.id,
                    toId = node.id,
                    kind = EdgeKind.DIRECT_CALL,
                    callSiteSnippet = callSite.text.take(120),
                    callSiteFile = from.filePath,
                    callSiteLine = PsiNodes.lineOf(callSite),
                ),
                other = from,
            )
        }
        return ctx.resolver.foreignReferenceToJava(callSite, node)
    }

    private fun methodNode(method: PsiMethod): CallNode {
        val cls = method.containingClass
        val qn = "${cls?.qualifiedName ?: "<anon>"}#${method.name}(${method.parameterList.parametersCount})"
        val display = "${cls?.name ?: "?"}.${method.name}()"
        val kind = if (isControllerMapping(method)) NodeKind.CONTROLLER_MAPPING else NodeKind.METHOD
        val lang = if (kind == NodeKind.CONTROLLER_MAPPING) Language.SPRING else Language.JAVA
        val file = PsiNodes.pathOf(method.containingFile)
        return PsiNodes.build(display, qn, kind, lang, file, method.textOffset, PsiNodes.lineOf(method))
    }

    private fun fieldNode(field: PsiField): CallNode {
        val cls = field.containingClass
        val qn = "${cls?.qualifiedName ?: "<anon>"}.${field.name}"
        val file = PsiNodes.pathOf(field.containingFile)
        return PsiNodes.build(field.name ?: "?", qn, NodeKind.FIELD, Language.JAVA, file, field.textOffset, PsiNodes.lineOf(field))
    }

    private fun isControllerMapping(method: PsiMethod): Boolean =
        method.modifierList.annotations.any { it.qualifiedName?.let(::isMappingAnnotation) == true }

    private fun isMappingAnnotation(qn: String): Boolean = qn in MAPPING_ANNOTATIONS

    companion object {
        private val MAPPING_ANNOTATIONS = setOf(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
        )

        fun mappingUrl(method: PsiMethod): String? {
            val ann: PsiAnnotation = method.modifierList.annotations.firstOrNull {
                it.qualifiedName in MAPPING_ANNOTATIONS
            } ?: return null
            val value = ann.findAttributeValue("value") ?: ann.findAttributeValue("path") ?: return null
            return (value as? PsiLiteralExpression)?.value as? String
        }
    }
}
