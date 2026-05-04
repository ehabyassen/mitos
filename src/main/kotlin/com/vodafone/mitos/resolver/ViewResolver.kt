package com.vodafone.mitos.resolver

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.vodafone.mitos.settings.MitosSettingsState

/**
 * Maps a Spring controller's returned view name (e.g. `"home"`) to a JSP file
 * (`/WEB-INF/views/home.jsp`) using the prefix/suffix configured in the
 * settings page (FR-12, FR-26).
 */
internal class ViewResolver(private val project: Project, private val settings: MitosSettingsState) {

    fun resolve(viewName: String): VirtualFile? {
        if (viewName.isBlank()) return null
        val candidate = settings.viewResolverPrefix.removeSuffix("/") + "/" +
            viewName.trimStart('/') + settings.viewResolverSuffix

        // Try absolute project paths first.
        val byUrl = VirtualFileManager.getInstance().findFileByUrl("file://$candidate")
        if (byUrl != null && byUrl.exists()) return byUrl

        // Fall back to a relative search across content roots.
        val target = candidate.removePrefix("/")
        var found: VirtualFile? = null
        ProjectFileIndex.getInstance(project).iterateContent { file ->
            if (!file.isDirectory && file.path.endsWith(target)) {
                found = file
                return@iterateContent false
            }
            true
        }
        return found
    }
}
