package com.vodafone.mitos.ui.theme

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Translates the active IntelliJ LAF / editor scheme into a colour bundle the
 * web view can consume (FR-21d). The renderer reads these on init and on every
 * theme-switch event.
 */
object ThemeBridge {

    /** Snapshot of palette values we ship to the web view. */
    data class Palette(
        val isDark: Boolean,
        val background: String,
        val foreground: String,
        val grid: String,
        val tooltipBackground: String,
        val tooltipForeground: String,
        val rootGlow: String,
    )

    fun current(): Palette {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val isDark = !JBColor.isBright()
        val bg = scheme.defaultBackground ?: if (isDark) Color(0x1E1F22) else Color.WHITE
        val fg = scheme.defaultForeground ?: if (isDark) Color(0xDFE1E5) else Color(0x1F2328)
        val grid = if (isDark) Color(0x2A2C30) else Color(0xEEEFF1)
        val tipBg = if (isDark) Color(0x2B2D31) else Color(0xFFFFFF)
        val tipFg = if (isDark) Color(0xE6E6E6) else Color(0x202020)
        val rootGlow = if (isDark) Color(0x6DB33F) else Color(0x6DB33F)
        return Palette(
            isDark = isDark,
            background = hex(bg),
            foreground = hex(fg),
            grid = hex(grid),
            tooltipBackground = hex(tipBg),
            tooltipForeground = hex(tipFg),
            rootGlow = hex(rootGlow),
        )
    }

    private fun hex(c: Color): String = String.format("#%02x%02x%02x", c.red, c.green, c.blue)
}
