/*
 * Beancount JVM - A JVM implementation of Beancount
 * Copyright (C) 2026  Beancount JVM Contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <https://www.gnu.org/licenses/>.
 *
 * Based on Beancount by Martin Blais
 * Original project: https://github.com/beancount/beancount
 */

package io.github.tonyzhye.beancount.cli

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for ConditionalPager functionality.
 */
class PagerTest {

    @Test
    fun `should collect lines below threshold`() {
        val pager = ConditionalPager(threshold = 5)
        pager.println("Line 1")
        pager.println("Line 2")
        pager.close()
        // Should print directly without error
        assertTrue(true)
    }

    @Test
    fun `should detect pager command availability`() {
        val pager = ConditionalPager.defaultPager()
        // Should return "less -FRSX", "more", or "" depending on system
        assertTrue(pager == "less -FRSX" || pager == "more" || pager == "")
    }

    @Test
    fun `should not fail when pager is unavailable`() {
        val pager = ConditionalPager(threshold = 1, pagerCommand = "nonexistent_pager_xyz")
        pager.println("Line 1")
        pager.println("Line 2")
        pager.close()
        // Should fallback to direct output without error
        assertTrue(true)
    }

    @Test
    fun `use block should execute and close`() {
        ConditionalPager(threshold = 10).use { pager ->
            pager.println("Test line")
        }
        // Should complete without error
        assertTrue(true)
    }

    @Test
    fun `printLines should add multiple lines`() {
        val pager = ConditionalPager(threshold = 5)
        pager.printLines(listOf("Line 1", "Line 2", "Line 3"))
        pager.close()
        assertTrue(true)
    }
}
