package com.vodafone.mitos.resolver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * Exercises the URL-matching logic in [SpringMappingIndex] without spinning up
 * the full IntelliJ test fixture. Uses reflection to hit the private members
 * because [SpringMappingIndex] is constructed via [SpringMappingIndex.build].
 */
class SpringUrlMatchTest {

    private val index: SpringMappingIndex
    private val matchMethod: Method

    init {
        // Reach through the private constructor with reflection to avoid pulling
        // the heavy IntelliJ fixture in this micro-unit test.
        val ctor = SpringMappingIndex::class.java.declaredConstructors[0].apply { isAccessible = true }
        index = ctor.newInstance(emptyList<Any>()) as SpringMappingIndex
        matchMethod = SpringMappingIndex::class.java.getDeclaredMethod("templatesMatch", String::class.java, String::class.java)
            .apply { isAccessible = true }
    }

    @Test fun `templates match swallows path variables`() {
        assertTrue(matchMethod.invoke(index, "/users/{id}", "/users/42") as Boolean)
        assertTrue(matchMethod.invoke(index, "/users/{id}/posts/{pid}", "/users/1/posts/9") as Boolean)
    }

    @Test fun `templates require same segment count`() {
        assertEquals(false, matchMethod.invoke(index, "/users/{id}", "/users/42/extra") as Boolean)
    }
}
