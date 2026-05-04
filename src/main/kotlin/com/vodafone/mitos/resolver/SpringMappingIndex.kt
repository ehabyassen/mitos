package com.vodafone.mitos.resolver

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

/**
 * Lightweight in-memory index of `@RequestMapping` URL → handler-method.
 * Used by [CrossLanguageResolver.matchAjaxUrl] to bridge JS AJAX calls back
 * to their Spring controller (FR-14).
 *
 * Built lazily on demand, scoped to a single graph computation. We do NOT
 * keep this around — the SRS forbids eager indexing on project open (NFR-3).
 */
internal class SpringMappingIndex private constructor(private val entries: List<Entry>) {

    data class Entry(val url: String, val method: PsiMethod, val classUrl: String, val methodUrl: String)

    /** Match a JS-side URL string against the indexed mappings. Returns the best match or null. */
    fun match(rawUrl: String): Entry? {
        if (rawUrl.isBlank()) return null
        val u = normalize(rawUrl)
        // exact match first, then prefix, then path-variable-aware
        return entries.firstOrNull { normalize(it.url) == u }
            ?: entries.firstOrNull { u.startsWith(normalize(it.url).removeSuffix("/")) }
            ?: entries.firstOrNull { templatesMatch(normalize(it.url), u) }
    }

    private fun normalize(s: String): String = if (s.startsWith("/")) s else "/$s"

    private fun templatesMatch(template: String, url: String): Boolean {
        val templateParts = template.trim('/').split("/")
        val urlParts = url.trim('/').split("/")
        if (templateParts.size != urlParts.size) return false
        return templateParts.zip(urlParts).all { (t, p) ->
            (t.startsWith("{") && t.endsWith("}")) || t == p
        }
    }

    companion object {
        private val MAPPING_ANNOTATIONS = listOf(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
        )

        fun build(project: Project): SpringMappingIndex {
            val scope = GlobalSearchScope.projectScope(project)
            val facade = JavaPsiFacade.getInstance(project)
            val entries = mutableListOf<Entry>()

            for (qn in MAPPING_ANNOTATIONS) {
                val annotationClass: PsiClass = facade.findClass(qn, GlobalSearchScope.allScope(project)) ?: continue
                AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope).forEach { method ->
                    val classUrl = method.containingClass?.let { readMappingValue(it) } ?: ""
                    val methodUrl = method.modifierList.annotations
                        .firstOrNull { it.qualifiedName == qn }
                        ?.let { readMappingValue(it) } ?: ""
                    val combined = combine(classUrl, methodUrl)
                    if (combined.isNotEmpty()) entries += Entry(combined, method, classUrl, methodUrl)
                }
            }
            return SpringMappingIndex(entries)
        }

        private fun readMappingValue(cls: PsiClass): String? {
            val ann = cls.modifierList?.annotations
                ?.firstOrNull { it.qualifiedName in MAPPING_ANNOTATIONS } ?: return null
            return readMappingValue(ann)
        }

        private fun readMappingValue(ann: PsiAnnotation): String {
            val attr = ann.findAttributeValue("value") ?: ann.findAttributeValue("path")
            return (attr as? PsiLiteralExpression)?.value as? String ?: ""
        }

        private fun combine(classUrl: String, methodUrl: String): String {
            val c = classUrl.trim('/')
            val m = methodUrl.trim('/')
            return when {
                c.isEmpty() && m.isEmpty() -> ""
                c.isEmpty() -> "/$m"
                m.isEmpty() -> "/$c"
                else -> "/$c/$m"
            }
        }
    }
}
