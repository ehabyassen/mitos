package com.vodafone.mitos.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.vodafone.mitos.MitosBundle
import java.nio.file.Files
import java.util.Base64

/** Save dialog used by the Export PNG / SVG / Mermaid toolbar buttons. */
internal object ExportDialog {
    fun save(project: Project, format: String, content: String) {
        val (title, ext) = when (format.lowercase()) {
            "png" -> "Export PNG" to "png"
            "svg" -> "Export SVG" to "svg"
            "mermaid" -> "Export Mermaid" to "mmd"
            else -> "Export" to format
        }
        val descriptor = FileSaverDescriptor(title, "Save $title", ext)
        val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val target = saver.save("mitos.$ext") ?: return
        try {
            val path = target.file.toPath()
            when (format.lowercase()) {
                "png" -> {
                    // content is "data:image/png;base64,…"
                    val payload = content.substringAfter(",", missingDelimiterValue = content)
                    Files.write(path, Base64.getDecoder().decode(payload))
                }
                "svg", "mermaid" -> Files.writeString(path, content)
                else -> Files.writeString(path, content)
            }
            NotificationGroupManager.getInstance().getNotificationGroup("Mitos.Notifications")
                .createNotification(MitosBundle.message("notification.export.success", path.toString()),
                    NotificationType.INFORMATION)
                .notify(project)
        } catch (t: Throwable) {
            NotificationGroupManager.getInstance().getNotificationGroup("Mitos.Notifications")
                .createNotification(MitosBundle.message("notification.export.failed", t.message ?: t.javaClass.simpleName),
                    NotificationType.ERROR)
                .notify(project)
        }
    }
}
