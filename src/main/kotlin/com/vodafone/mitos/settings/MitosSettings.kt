package com.vodafone.mitos.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Project-level settings store (FR-27). State is persisted through the
 * standard `PersistentStateComponent` machinery into the project's
 * `mitos.xml`.
 */
@Service(Service.Level.PROJECT)
@State(name = "MitosSettings", storages = [Storage("mitos.xml")])
class MitosSettings : PersistentStateComponent<MitosSettingsState> {
    private var state: MitosSettingsState = MitosSettingsState()

    override fun getState(): MitosSettingsState = state

    override fun loadState(state: MitosSettingsState) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    fun update(block: (MitosSettingsState) -> Unit) {
        block(state)
    }

    companion object {
        fun get(project: Project): MitosSettingsState = project.service<MitosSettings>().state
        fun service(project: Project): MitosSettings = project.service()
    }
}
