package com.vodafone.mitos.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.vodafone.mitos.model.CallNode
import com.vodafone.mitos.service.CallGraphService

/**
 * Opens the source location for a clicked graph node (FR-20). Runs on the
 * EDT — the JBCefJSQuery callback marshals the click off the renderer thread.
 */
internal class NavigationHandler(private val project: Project) {

    fun navigate(nodeId: String, fallbackPath: String, fallbackOffset: Int) {
        ApplicationManager.getApplication().invokeLater {
            val (path, offset) = locate(nodeId, fallbackPath, fallbackOffset) ?: return@invokeLater
            val vf = LocalFileSystem.getInstance().findFileByPath(path) ?: return@invokeLater
            val descriptor = OpenFileDescriptor(project, vf, offset)
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        }
    }

    private fun locate(nodeId: String, fallbackPath: String, fallbackOffset: Int): Pair<String, Int>? {
        // Try the structured id format first: KIND@/abs/path#offset
        val at = nodeId.indexOf('@')
        val hash = nodeId.lastIndexOf('#')
        if (at > 0 && hash > at) {
            val path = nodeId.substring(at + 1, hash)
            val offset = nodeId.substring(hash + 1).toIntOrNull() ?: 0
            if (path.isNotEmpty()) return path to offset
        }
        return if (fallbackPath.isNotEmpty()) fallbackPath to fallbackOffset else null
    }
}
