package com.vodafone.mitos.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.vodafone.mitos.MitosBundle
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SpinnerNumberModel
import javax.swing.JSpinner

/**
 * Settings UI under `Settings → Tools → Mitos` (FR-26). Backed by
 * [MitosSettings].
 */
class MitosConfigurable(private val project: Project) : Configurable {
    private val viewPrefix = JBTextField()
    private val viewSuffix = JBTextField()
    private val pkgInclude = JBTextField()
    private val pkgExclude = JBTextField()
    private val jspEnabled = JBCheckBox(MitosBundle.message("settings.jsp.enabled"))
    private val jsEnabled = JBCheckBox(MitosBundle.message("settings.js.enabled"))
    private val animations = JBCheckBox(MitosBundle.message("settings.animations.enabled"))

    private val maxNodes = JSpinner(SpinnerNumberModel(200, 10, MitosSettingsState.MAX_NODES_HARD_CEILING, 10))
    private val depthIn = JSpinner(SpinnerNumberModel(2, 0, MitosSettingsState.MAX_DEPTH, 1))
    private val depthOut = JSpinner(SpinnerNumberModel(2, 0, MitosSettingsState.MAX_DEPTH, 1))

    private val layout = JComboBox(arrayOf("dagre", "cose-bilkent", "concentric", "grid"))

    override fun getDisplayName(): String = MitosBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        val form: JPanel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>${MitosBundle.message("settings.section.resolution")}</b></html>"))
            .addLabeledComponent(MitosBundle.message("settings.viewresolver.prefix"), viewPrefix)
            .addLabeledComponent(MitosBundle.message("settings.viewresolver.suffix"), viewSuffix)
            .addSeparator()
            .addComponent(JBLabel("<html><b>${MitosBundle.message("settings.section.scope")}</b></html>"))
            .addLabeledComponent(MitosBundle.message("settings.package.include"), pkgInclude)
            .addLabeledComponent(MitosBundle.message("settings.package.exclude"), pkgExclude)
            .addComponent(jspEnabled)
            .addComponent(jsEnabled)
            .addSeparator()
            .addComponent(JBLabel("<html><b>${MitosBundle.message("settings.section.limits")}</b></html>"))
            .addLabeledComponent(MitosBundle.message("settings.max.nodes"), maxNodes)
            .addLabeledComponent(MitosBundle.message("settings.depth.in"), depthIn)
            .addLabeledComponent(MitosBundle.message("settings.depth.out"), depthOut)
            .addSeparator()
            .addComponent(JBLabel("<html><b>${MitosBundle.message("settings.section.visualization")}</b></html>"))
            .addLabeledComponent(MitosBundle.message("settings.layout.default"), layout)
            .addComponent(animations)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return form
    }

    override fun isModified(): Boolean {
        val s = MitosSettings.get(project)
        return viewPrefix.text != s.viewResolverPrefix ||
            viewSuffix.text != s.viewResolverSuffix ||
            pkgInclude.text != s.packageIncludeRegex ||
            pkgExclude.text != s.packageExcludeRegex ||
            jspEnabled.isSelected != s.jspAnalyzerEnabled ||
            jsEnabled.isSelected != s.jsAnalyzerEnabled ||
            (maxNodes.value as Int) != s.maxNodes ||
            (depthIn.value as Int) != s.defaultDepthIn ||
            (depthOut.value as Int) != s.defaultDepthOut ||
            (layout.selectedItem as String) != s.defaultLayout ||
            animations.isSelected != s.animationsEnabled
    }

    override fun apply() {
        MitosSettings.service(project).update { s ->
            s.viewResolverPrefix = viewPrefix.text
            s.viewResolverSuffix = viewSuffix.text
            s.packageIncludeRegex = pkgInclude.text
            s.packageExcludeRegex = pkgExclude.text
            s.jspAnalyzerEnabled = jspEnabled.isSelected
            s.jsAnalyzerEnabled = jsEnabled.isSelected
            s.maxNodes = maxNodes.value as Int
            s.defaultDepthIn = depthIn.value as Int
            s.defaultDepthOut = depthOut.value as Int
            s.defaultLayout = layout.selectedItem as String
            s.animationsEnabled = animations.isSelected
        }
    }

    override fun reset() {
        val s = MitosSettings.get(project)
        viewPrefix.text = s.viewResolverPrefix
        viewSuffix.text = s.viewResolverSuffix
        pkgInclude.text = s.packageIncludeRegex
        pkgExclude.text = s.packageExcludeRegex
        jspEnabled.isSelected = s.jspAnalyzerEnabled
        jsEnabled.isSelected = s.jsAnalyzerEnabled
        maxNodes.value = s.maxNodes
        depthIn.value = s.defaultDepthIn
        depthOut.value = s.defaultDepthOut
        layout.selectedItem = s.defaultLayout
        animations.isSelected = s.animationsEnabled
    }
}
