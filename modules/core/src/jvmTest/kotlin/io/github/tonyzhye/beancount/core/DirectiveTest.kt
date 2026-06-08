package io.github.tonyzhye.beancount.core

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Tests for Directive.kt.
 */
class DirectiveTest {

    @Test
    fun `ALL_DIRECTIVES should contain all directive types`() {
        assertTrue(ALL_DIRECTIVES.contains(Open::class))
        assertTrue(ALL_DIRECTIVES.contains(Close::class))
        assertTrue(ALL_DIRECTIVES.contains(Commodity::class))
        assertTrue(ALL_DIRECTIVES.contains(Pad::class))
        assertTrue(ALL_DIRECTIVES.contains(Balance::class))
        assertTrue(ALL_DIRECTIVES.contains(Transaction::class))
        assertTrue(ALL_DIRECTIVES.contains(Note::class))
        assertTrue(ALL_DIRECTIVES.contains(Event::class))
        assertTrue(ALL_DIRECTIVES.contains(Query::class))
        assertTrue(ALL_DIRECTIVES.contains(Price::class))
        assertTrue(ALL_DIRECTIVES.contains(Document::class))
        assertTrue(ALL_DIRECTIVES.contains(Custom::class))
        assertTrue(ALL_DIRECTIVES.contains(PushTag::class))
        assertTrue(ALL_DIRECTIVES.contains(PopTag::class))
        assertTrue(ALL_DIRECTIVES.contains(PushMeta::class))
        assertTrue(ALL_DIRECTIVES.contains(PopMeta::class))
    }
}
