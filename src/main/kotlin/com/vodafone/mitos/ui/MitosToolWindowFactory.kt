package com.vodafone.mitos.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** Registers the "Mitos" tool window (F2). */
class MitosToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val window = MitosToolWindow(project)
        val content = ContentFactory.getInstance().createContent(window.component, "", false)
        content.putUserData(MitosToolWindow.KEY, window)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
