package com.vodafone.mitos

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.MitosBundle"

/** Localised string lookup. All user-facing text routes through here (NFR-7). */
object MitosBundle : DynamicBundle(BUNDLE) {
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
