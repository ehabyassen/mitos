package com.vodafone.mitos.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.vodafone.mitos.MitosBundle
import com.vodafone.mitos.service.CallGraphService
import com.vodafone.mitos.ui.MitosToolWindow

/**
 * Editor entry point (FR-1, F1). Picks the element under the caret, opens the
 * Mitos tool window, and asks it to render the graph.
 */
class ShowCallGraphAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val element = elementAtCaret(e)
        e.presentation.isEnabled = element != null &&
            e.project?.let { CallGraphService.get(it).supports(element) } == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val element = elementAtCaret(e)
        val service = CallGraphService.get(project)
        val root = element?.let { service.rootFor(it) }
        if (root == null) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Mitos.Notifications")
                .createNotification(MitosBundle.message("notification.no.element"), NotificationType.INFORMATION)
                .notify(project)
            return
        }
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Mitos") ?: return
        toolWindow.show {
            val mitos = MitosToolWindow.from(toolWindow)
            mitos?.showGraphFor(root)
        }
    }

    private fun elementAtCaret(e: AnActionEvent): PsiElement? {
        val editor: Editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val file: PsiFile = e.getData(LangDataKeys.PSI_FILE) ?: return null
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        return PsiTreeUtil.getParentOfType(
            element,
            com.intellij.psi.PsiMethod::class.java,
            com.intellij.psi.PsiField::class.java,
            com.intellij.lang.javascript.psi.JSFunction::class.java,
        ) ?: file
    }
}
