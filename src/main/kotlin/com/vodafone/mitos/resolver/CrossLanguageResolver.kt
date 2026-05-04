package com.vodafone.mitos.resolver

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.vodafone.mitos.analyzer.AnalysisResult
import com.vodafone.mitos.analyzer.AnalysisResult.EdgeWithNode
import com.vodafone.mitos.analyzer.PsiNodes
import com.vodafone.mitos.model.CallEdge
import com.vodafone.mitos.model.CallNode
import com.vodafone.mitos.model.EdgeKind
import com.vodafone.mitos.model.Language
import com.vodafone.mitos.model.NodeKind
import com.vodafone.mitos.settings.MitosSettingsState

/**
 * Bridges JSP ↔ Java ↔ JS (FR-7..FR-16).
 *
 * Each public function returns an [AnalysisResult] so the calling analyzer's
 * BFS can keep advancing. Heuristic edges use [EdgeKind.AJAX_REQUEST] (whose
 * confidence is `HEURISTIC`, rendered as a dashed line per FR-15).
 */
class CrossLanguageResolver(
    private val project: Project,
    private val settings: MitosSettingsState,
) {
    private val viewResolver = ViewResolver(project, settings)
    private val mappingIndex: SpringMappingIndex by lazy { SpringMappingIndex.build(project) }

    // ---------------------------------------------------------- Java → JSP

    /** FR-12. Spring-MVC controller methods that return a string view name. */
    fun controllerToView(method: PsiMethod, fromNode: CallNode): AnalysisResult {
        val pairs = mutableListOf<EdgeWithNode>()
        method.body?.let { body ->
            PsiTreeUtil.findChildrenOfType(body, com.intellij.psi.PsiReturnStatement::class.java).forEach { ret ->
                val literal = ret.returnValue as? PsiLiteralExpression ?: return@forEach
                val viewName = literal.value as? String ?: return@forEach
                val vf: VirtualFile = viewResolver.resolve(viewName) ?: return@forEach
                val target = jspNodeFor(vf)
                pairs += EdgeWithNode(
                    edge = CallEdge(
                        fromId = fromNode.id,
                        toId = target.id,
                        kind = EdgeKind.FORWARD,
                        callSiteSnippet = ret.text.take(120),
                        callSiteFile = fromNode.filePath,
                        callSiteLine = PsiNodes.lineOf(ret),
                    ),
                    other = target,
                )
            }
        }
        return AnalysisResult(pairs)
    }

    /** FR-13. `RequestDispatcher.forward(...)` and `.include(...)` calls inside [method]. */
    fun dispatcherForwards(method: PsiMethod, fromNode: CallNode): AnalysisResult {
        val pairs = mutableListOf<EdgeWithNode>()
        PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression::class.java).forEach { call ->
            val refName = call.methodExpression.referenceName
            if (refName != "forward" && refName != "include") return@forEach
            val type = call.methodExpression.qualifierExpression?.type?.canonicalText ?: return@forEach
            if (!type.contains("RequestDispatcher")) return@forEach
            val arg = call.argumentList.expressions.firstOrNull() as? PsiLiteralExpression ?: return@forEach
            val path = arg.value as? String ?: return@forEach
            val vf = viewResolver.resolve(
                path.removeSuffix(settings.viewResolverSuffix).removePrefix(settings.viewResolverPrefix)
            ) ?: return@forEach
            val target = jspNodeFor(vf)
            pairs += EdgeWithNode(
                edge = CallEdge(
                    fromId = fromNode.id,
                    toId = target.id,
                    kind = EdgeKind.FORWARD,
                    callSiteSnippet = call.text.take(120),
                    callSiteFile = fromNode.filePath,
                    callSiteLine = PsiNodes.lineOf(call),
                ),
                other = target,
            )
        }
        return AnalysisResult(pairs)
    }

    // ---------------------------------------------------------- JSP → Java / JS

    /** FR-7..9: useBean class, EL `${bean.method()}`, scriptlet Java code. */
    fun jspToJava(file: PsiFile, fromNode: CallNode): AnalysisResult {
        val pairs = mutableListOf<EdgeWithNode>()

        // <jsp:useBean class="…">
        PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java)
            .filter { it.name == "jsp:useBean" }
            .forEach { tag ->
                val cls = tag.getAttributeValue("class") ?: return@forEach
                val psiClass: PsiClass = com.intellij.psi.JavaPsiFacade.getInstance(project)
                    .findClass(cls, com.intellij.psi.search.GlobalSearchScope.projectScope(project)) ?: return@forEach
                val target = javaClassNode(psiClass)
                pairs += EdgeWithNode(
                    edge = CallEdge(fromNode.id, target.id, EdgeKind.EL_REFERENCE,
                        tag.text.take(120), fromNode.filePath, PsiNodes.lineOf(tag)),
                    other = target,
                )
            }

        // EL `${bean.method()}` and scriptlet bodies — both surface bindings via PSI references.
        file.accept(object : com.intellij.psi.PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                element.references.forEach { ref ->
                    val target = runCatching { ref.resolve() }.getOrNull() ?: return@forEach
                    if (target is PsiMethod) {
                        val node = javaMethodNode(target) ?: return@forEach
                        val edgeKind = if (element.text.startsWith("<%")) EdgeKind.SCRIPTLET_CALL else EdgeKind.EL_REFERENCE
                        pairs += EdgeWithNode(
                            edge = CallEdge(
                                fromId = fromNode.id,
                                toId = node.id,
                                kind = edgeKind,
                                callSiteSnippet = element.text.take(120),
                                callSiteFile = fromNode.filePath,
                                callSiteLine = PsiNodes.lineOf(element),
                            ),
                            other = node,
                        )
                    }
                }
                super.visitElement(element)
            }
        })

        return AnalysisResult(pairs)
    }

    /** FR-11. `<script src="…">` and inline `onclick`/`onsubmit`/`onchange`. */
    fun jspToJs(file: PsiFile, fromNode: CallNode): AnalysisResult {
        val pairs = mutableListOf<EdgeWithNode>()
        PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).forEach { tag ->
            if (tag.name.equals("script", ignoreCase = true)) {
                val src = tag.getAttributeValue("src")
                if (!src.isNullOrBlank()) {
                    val target = resolveRelativeFile(file, src)?.let { jsFileNode(it) } ?: return@forEach
                    pairs += EdgeWithNode(
                        edge = CallEdge(
                            fromId = fromNode.id,
                            toId = target.id,
                            kind = EdgeKind.SCRIPTLET_CALL,
                            callSiteSnippet = tag.text.take(120),
                            callSiteFile = fromNode.filePath,
                            callSiteLine = PsiNodes.lineOf(tag),
                        ),
                        other = target,
                    )
                }
            }
            tag.attributes.forEach { attr ->
                if (attr.name.lowercase() in INLINE_HANDLERS) {
                    pairs += inlineHandlerEdge(attr, fromNode)
                }
            }
        }
        return AnalysisResult(pairs)
    }

    /** FR-10. `<%@ include file="…" %>` directive. */
    fun directiveIncludes(file: PsiFile, fromNode: CallNode): AnalysisResult {
        val pairs = mutableListOf<EdgeWithNode>()
        PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java)
            .filter { it.name == "jsp:directive.include" }
            .forEach { tag ->
                val path = tag.getAttributeValue("file") ?: return@forEach
                val vf = resolveRelativeFile(file, path) ?: return@forEach
                val target = jspNodeFor(vf)
                pairs += EdgeWithNode(
                    edge = CallEdge(fromNode.id, target.id, EdgeKind.JSP_INCLUDE,
                        tag.text.take(120), fromNode.filePath, PsiNodes.lineOf(tag)),
                    other = target,
                )
            }
        return AnalysisResult(pairs)
    }

    /** FR-10 helper for `<jsp:include>` tags inside [JspAnalyzer]. */
    fun resolveJspInclude(host: PsiFile, path: String): CallNode? {
        val vf = resolveRelativeFile(host, path) ?: return null
        return jspNodeFor(vf)
    }

    // ---------------------------------------------------------- JS → Java

    /** FR-14. Detect `fetch`, `$.ajax`, `XMLHttpRequest.open` AJAX URLs. */
    fun jsAjaxToJava(function: PsiElement, fromNode: CallNode): AnalysisResult {
        val pairs = mutableListOf<EdgeWithNode>()
        function.accept(object : com.intellij.psi.PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is com.intellij.lang.javascript.psi.JSCallExpression) {
                    val url = extractAjaxUrl(element)
                    if (url != null) {
                        mappingIndex.match(url)?.let { match ->
                            val target = javaMethodNode(match.method) ?: return@let
                            pairs += EdgeWithNode(
                                edge = CallEdge(
                                    fromId = fromNode.id,
                                    toId = target.id,
                                    kind = EdgeKind.AJAX_REQUEST,
                                    callSiteSnippet = element.text.take(120),
                                    callSiteFile = fromNode.filePath,
                                    callSiteLine = PsiNodes.lineOf(element),
                                ),
                                other = target,
                            )
                        }
                    }
                }
                super.visitElement(element)
            }
        })
        return AnalysisResult(pairs)
    }

    /** Inverse of [jsAjaxToJava]: a Java node may be invoked from a JSP/JS reference. */
    fun foreignReferenceToJava(callSite: PsiElement, javaNode: CallNode): EdgeWithNode? {
        val file = callSite.containingFile ?: return null
        val vf = file.virtualFile ?: return null
        val ext = vf.extension?.lowercase()
        val source = when (ext) {
            "jsp", "jspx" -> jspNodeFor(vf)
            "js" -> jsFileNode(vf)
            else -> return null
        }
        val kind = when (ext) {
            "jsp", "jspx" -> if (callSite.text.startsWith("<%")) EdgeKind.SCRIPTLET_CALL else EdgeKind.EL_REFERENCE
            "js" -> EdgeKind.AJAX_REQUEST
            else -> return null
        }
        return EdgeWithNode(
            edge = CallEdge(
                fromId = source.id,
                toId = javaNode.id,
                kind = kind,
                callSiteSnippet = callSite.text.take(120),
                callSiteFile = vf.path,
                callSiteLine = PsiNodes.lineOf(callSite),
            ),
            other = source,
        )
    }

    /** Caller-side equivalent for the JS analyzer: the call site lives in JSP/HTML. */
    fun foreignJsCallerEdge(callSite: PsiElement, jsNode: CallNode): EdgeWithNode? {
        val file = callSite.containingFile ?: return null
        val vf = file.virtualFile ?: return null
        val ext = vf.extension?.lowercase()
        if (ext != "jsp" && ext != "jspx") return null
        val source = jspNodeFor(vf)
        return EdgeWithNode(
            edge = CallEdge(
                fromId = source.id,
                toId = jsNode.id,
                kind = EdgeKind.JS_INVOCATION,
                callSiteSnippet = callSite.text.take(120),
                callSiteFile = vf.path,
                callSiteLine = PsiNodes.lineOf(callSite),
            ),
            other = source,
        )
    }

    // ---------------------------------------------------------- Internal helpers

    private fun inlineHandlerEdge(attr: XmlAttribute, fromNode: CallNode): EdgeWithNode {
        val host = attr.containingFile.virtualFile!!
        val target = PsiNodes.build(
            displayName = "${attr.name}=…",
            qualifiedName = "${host.path}#${attr.name}@${attr.textOffset}",
            kind = NodeKind.JS_FUNCTION,
            language = Language.JAVASCRIPT,
            filePath = host.path,
            offset = attr.textOffset,
            lineNumber = PsiNodes.lineOf(attr),
            tooltip = "Inline ${attr.name}: ${attr.value?.take(80)}",
        )
        return EdgeWithNode(
            edge = CallEdge(
                fromId = fromNode.id,
                toId = target.id,
                kind = EdgeKind.JS_INVOCATION,
                callSiteSnippet = attr.text.take(120),
                callSiteFile = host.path,
                callSiteLine = PsiNodes.lineOf(attr),
            ),
            other = target,
        )
    }

    private fun jspNodeFor(vf: VirtualFile): CallNode = PsiNodes.build(
        displayName = vf.name,
        qualifiedName = vf.path,
        kind = NodeKind.JSP_PAGE,
        language = Language.JSP,
        filePath = vf.path,
        offset = 0,
        lineNumber = 1,
        tooltip = "JSP page · ${vf.path}",
    )

    private fun jsFileNode(vf: VirtualFile): CallNode = PsiNodes.build(
        displayName = vf.name,
        qualifiedName = vf.path,
        kind = NodeKind.JS_FUNCTION,
        language = Language.JAVASCRIPT,
        filePath = vf.path,
        offset = 0,
        lineNumber = 1,
        tooltip = "JS file · ${vf.path}",
    )

    private fun javaClassNode(cls: PsiClass): CallNode = PsiNodes.build(
        displayName = cls.name ?: "?",
        qualifiedName = cls.qualifiedName ?: "?",
        kind = NodeKind.SERVICE,
        language = Language.JAVA,
        filePath = PsiNodes.pathOf(cls.containingFile),
        offset = cls.textOffset,
        lineNumber = PsiNodes.lineOf(cls),
    )

    private fun javaMethodNode(method: PsiMethod): CallNode? {
        val cls = method.containingClass
        val qn = "${cls?.qualifiedName ?: "<anon>"}#${method.name}(${method.parameterList.parametersCount})"
        val display = "${cls?.name ?: "?"}.${method.name}()"
        val isMapping = method.modifierList.annotations.any {
            it.qualifiedName?.startsWith("org.springframework.web.bind.annotation.") == true
        }
        val kind = if (isMapping) NodeKind.CONTROLLER_MAPPING else NodeKind.METHOD
        val lang = if (isMapping) Language.SPRING else Language.JAVA
        val file = PsiNodes.pathOf(method.containingFile)
        return PsiNodes.build(display, qn, kind, lang, file, method.textOffset, PsiNodes.lineOf(method))
    }

    private fun resolveRelativeFile(host: PsiFile, raw: String): VirtualFile? {
        val hostVf = host.virtualFile ?: return null
        if (raw.startsWith("/")) {
            return com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                .findFileByUrl("file://${hostVf.parent?.path}$raw")
                ?: com.intellij.openapi.roots.ProjectFileIndex.getInstance(project).let { idx ->
                    var hit: VirtualFile? = null
                    idx.iterateContent { f ->
                        if (!f.isDirectory && f.path.endsWith(raw)) { hit = f; return@iterateContent false }
                        true
                    }
                    hit
                }
        }
        return hostVf.parent?.findFileByRelativePath(raw)
    }

    private fun extractAjaxUrl(call: com.intellij.lang.javascript.psi.JSCallExpression): String? {
        val callee = call.methodExpression?.text ?: return null
        return when {
            callee == "fetch" -> call.argumentList?.arguments?.firstOrNull()
                ?.let { (it as? com.intellij.lang.javascript.psi.JSLiteralExpression)?.value as? String }

            callee.endsWith(".ajax") -> {
                val obj = call.argumentList?.arguments?.firstOrNull()
                    as? com.intellij.lang.javascript.psi.JSObjectLiteralExpression
                val urlProp = obj?.properties?.firstOrNull { it.name == "url" }
                (urlProp?.value as? com.intellij.lang.javascript.psi.JSLiteralExpression)?.value as? String
            }

            callee.endsWith(".open") -> {
                val args = call.argumentList?.arguments ?: return null
                if (args.size < 2) null else
                    (args[1] as? com.intellij.lang.javascript.psi.JSLiteralExpression)?.value as? String
            }

            else -> null
        }
    }

    companion object {
        private val INLINE_HANDLERS = setOf("onclick", "onsubmit", "onchange", "onload", "onblur", "onfocus")
    }
}
