package com.vodafone.mitos.service

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent

/**
 * Drops cache entries whose graphs touch the modified file (FR-29). One
 * listener per project; registered through `<projectListeners>` in
 * `plugin.xml` and instantiated by the platform.
 */
class PsiInvalidationListener : PsiTreeChangeAdapter() {
    override fun childrenChanged(event: PsiTreeChangeEvent) = invalidateFor(event)
    override fun childAdded(event: PsiTreeChangeEvent) = invalidateFor(event)
    override fun childRemoved(event: PsiTreeChangeEvent) = invalidateFor(event)
    override fun childReplaced(event: PsiTreeChangeEvent) = invalidateFor(event)

    private fun invalidateFor(event: PsiTreeChangeEvent) {
        val path = event.file?.virtualFile?.path ?: return
        for (project in ProjectManager.getInstance().openProjects) {
            project.service<CallGraphService>().invalidateFile(path)
        }
    }
}
