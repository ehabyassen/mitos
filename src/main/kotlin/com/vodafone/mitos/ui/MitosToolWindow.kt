package com.vodafone.mitos.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.vodafone.mitos.MitosBundle
import com.vodafone.mitos.model.CallGraph
import com.vodafone.mitos.model.CallNode
import com.vodafone.mitos.service.CallGraphService
import com.vodafone.mitos.settings.MitosConfigurable
import com.vodafone.mitos.settings.MitosSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider

/** Owner of the tool-window content. Wires toolbar → service → [GraphPanel]. */
internal class MitosToolWindow(private val project: Project) : Disposable {

    private val service = CallGraphService.get(project)
    private val graphPanel = GraphPanel(project, this)
    private val statusLabel = JBLabel(MitosBundle.message("toolwindow.empty"))

    private val depthIn = JSlider(0, 5, MitosSettings.get(project).defaultDepthIn)
    private val depthOut = JSlider(0, 5, MitosSettings.get(project).defaultDepthOut)
    private val layoutBox = JComboBox(arrayOf("dagre", "cose-bilkent", "concentric", "grid"))
    private val search = SearchTextField()
    private val refreshBtn = JButton(MitosBundle.message("toolbar.refresh"))
    private val pngBtn = JButton(MitosBundle.message("toolbar.export.png"))
    private val svgBtn = JButton(MitosBundle.message("toolbar.export.svg"))
    private val mmdBtn = JButton(MitosBundle.message("toolbar.export.mermaid"))
    private val settingsBtn = JButton(MitosBundle.message("toolbar.settings"))

    private var lastRoot: CallNode? = null

    val component: JComponent

    init {
        depthIn.majorTickSpacing = 1
        depthIn.paintTicks = true
        depthIn.snapToTicks = true
        depthOut.majorTickSpacing = 1
        depthOut.paintTicks = true
        depthOut.snapToTicks = true
        layoutBox.selectedItem = MitosSettings.get(project).defaultLayout

        depthIn.addChangeListener { if (!depthIn.valueIsAdjusting) recompute() }
        depthOut.addChangeListener { if (!depthOut.valueIsAdjusting) recompute() }
        layoutBox.addActionListener { sendCommand("setLayout", "\"${layoutBox.selectedItem}\"") }
        search.textEditor.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = update()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = update()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = update()
            private fun update() = sendCommand("filter", "\"${search.text}\"")
        })
        refreshBtn.addActionListener { lastRoot?.let { showGraphFor(it) } }
        pngBtn.addActionListener { sendCommand("exportPng", "") }
        svgBtn.addActionListener { sendCommand("exportSvg", "") }
        mmdBtn.addActionListener { sendCommand("exportMermaid", "") }
        settingsBtn.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, MitosConfigurable::class.java)
        }

        val toolbar = buildToolbar()
        val status = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 8)
            add(statusLabel, BorderLayout.WEST)
        }
        component = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(graphPanel.component, BorderLayout.CENTER)
            add(status, BorderLayout.SOUTH)
        }
        graphPanel.showEmptyState()
    }

    /** Triggered by the editor action and by the Refresh button. */
    fun showGraphFor(root: CallNode) {
        lastRoot = root
        statusLabel.text = MitosBundle.message("notification.computing")
        service.computeAsync(
            root,
            depthIn.value,
            depthOut.value,
            onResult = { graph -> render(graph) },
            onError = { t ->
                NotificationGroupManager.getInstance().getNotificationGroup("Mitos.Notifications")
                    .createNotification(t.message ?: t.javaClass.simpleName, NotificationType.ERROR)
                    .notify(project)
            },
        )
    }

    private fun recompute() {
        lastRoot?.let { showGraphFor(it) }
    }

    private fun render(graph: CallGraph) {
        graphPanel.render(graph)
        statusLabel.text = MitosBundle.message("status.summary",
            graph.nodeCount, graph.edgeCount, graph.computeMillis)
        if (graph.truncated) {
            NotificationGroupManager.getInstance().getNotificationGroup("Mitos.Notifications")
                .createNotification(MitosBundle.message("notification.truncated", graph.nodeCount), NotificationType.WARNING)
                .notify(project)
        }
        if (graph.warnings.isNotEmpty()) {
            NotificationGroupManager.getInstance().getNotificationGroup("Mitos.Notifications")
                .createNotification(
                    MitosBundle.message("notification.partial") + "\n" + graph.warnings.joinToString("\n"),
                    NotificationType.WARNING,
                )
                .notify(project)
        }
    }

    private fun buildToolbar(): JComponent {
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JLabel(MitosBundle.message("toolbar.depth.in")))
            add(depthIn)
            add(JLabel(MitosBundle.message("toolbar.depth.out")))
            add(depthOut)
            add(JLabel(MitosBundle.message("toolbar.layout")))
            add(layoutBox)
        }
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply {
            add(search.apply { textEditor.columns = 14 })
            add(refreshBtn)
            add(pngBtn)
            add(svgBtn)
            add(mmdBtn)
            add(settingsBtn)
        }
        return JPanel(BorderLayout()).apply {
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }
    }

    private fun sendCommand(name: String, jsArg: String) {
        graphPanel.sendCommand(name, jsArg)
    }

    override fun dispose() {
        Disposer.dispose(graphPanel)
    }

    companion object {
        val KEY: Key<MitosToolWindow> = Key.create("Mitos.ToolWindow")
        fun from(window: ToolWindow): MitosToolWindow? =
            window.contentManager.contents.firstOrNull()?.getUserData(KEY)
    }
}
