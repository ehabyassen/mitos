package com.vodafone.mitos.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class MitosSettingsStateTest {

    @Test fun `signature stable across runs for same content`() {
        val a = MitosSettingsState()
        val b = MitosSettingsState()
        assertEquals(a.signature(), b.signature())
    }

    @Test fun `signature changes when scope filters change`() {
        val a = MitosSettingsState()
        val b = MitosSettingsState().also { it.packageIncludeRegex = "com\\.vodafone\\..*" }
        assertNotEquals(a.signature(), b.signature())
    }

    @Test fun `signature changes when toggles flip`() {
        val a = MitosSettingsState()
        val b = MitosSettingsState().also { it.jspAnalyzerEnabled = false }
        assertNotEquals(a.signature(), b.signature())
    }

    @Test fun `defaults match SRS section 4_4`() {
        val s = MitosSettingsState()
        assertEquals("/WEB-INF/views/", s.viewResolverPrefix)
        assertEquals(".jsp", s.viewResolverSuffix)
        assertEquals(200, s.maxNodes)
        assertEquals(2, s.defaultDepthIn)
        assertEquals(2, s.defaultDepthOut)
        assertEquals(true, s.jspAnalyzerEnabled)
        assertEquals(true, s.jsAnalyzerEnabled)
    }
}
