package com.vodafone.mitos

import com.intellij.openapi.util.IconLoader

/** Icon registry used from `plugin.xml` and Kotlin code. */
object MitosIcons {
    @JvmField val ToolWindow = IconLoader.getIcon("/icons/mitos_toolwindow.svg", MitosIcons::class.java)
    @JvmField val Plugin = IconLoader.getIcon("/icons/mitos.svg", MitosIcons::class.java)
}
