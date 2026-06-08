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

package io.github.tonyzhye.beancount.parser

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for ParserContext.
 */
class ParserContextTest {

    @Test
    fun `should create context from content`() {
        val content = """
            2024-01-01 open Assets:Bank USD
            2024-01-15 * "Paycheck"
              Assets:Bank  100.00 USD
              Income:Salary
        """.trimIndent()

        val ctx = ParserContext.fromContent("test.beancount", content)
        assertEquals("test.beancount", ctx.filename)
        assertEquals(1, ctx.line)
        assertEquals(1, ctx.column)
        assertEquals(4, ctx.sourceLines.size)
    }

    @Test
    fun `getCurrentLine should return correct line`() {
        val content = """
            line1
            line2
            line3
        """.trimIndent()

        val ctx = ParserContext.fromContent("test.beancount", content)
            .withPosition(2, 1)

        assertEquals("line2", ctx.getCurrentLine())
    }

    @Test
    fun `getContextLines should return surrounding lines`() {
        val content = """
            line1
            line2
            line3
            line4
            line5
        """.trimIndent()

        val ctx = ParserContext.fromContent("test.beancount", content)
            .withPosition(3, 1)

        val lines = ctx.getContextLines(1)
        assertEquals(3, lines.size)
        assertEquals(2 to "line2", lines[0])
        assertEquals(3 to "line3", lines[1])
        assertEquals(4 to "line4", lines[2])
    }

    @Test
    fun `locationString should format location`() {
        val ctx = ParserContext("test.beancount", 10, 5)
        assertEquals("test.beancount:10:5", ctx.locationString())
    }

    @Test
    fun `formatError should include source context`() {
        val content = """
            2024-01-01 open Assets:Bank USD
            2024-01-15 * "Paycheck"
              Assets:Bank  100.00 USD
        """.trimIndent()

        val ctx = ParserContext.fromContent("test.beancount", content)
            .withPosition(2, 1)

        val errorMsg = ctx.formatError("Syntax error")
        assertTrue(errorMsg.contains("test.beancount:2:1"))
        assertTrue(errorMsg.contains("Syntax error"))
        assertTrue(errorMsg.contains("2024-01-15"))
    }

    @Test
    fun `ParseException should include context`() {
        val ctx = ParserContext("test.beancount", 5, 10)
        val ex = ParseException(ctx, "Unexpected token")

        assertTrue(ex.message?.contains("test.beancount:5:10") == true)
        assertTrue(ex.message?.contains("Unexpected token") == true)
    }

    @Test
    fun `ParseException detailedMessage should include context lines`() {
        val content = """
            line1
            line2
            line3
        """.trimIndent()

        val ctx = ParserContext.fromContent("test.beancount", content)
            .withPosition(2, 3)

        val ex = ParseException(ctx, "Error here")
        val detailed = ex.detailedMessage(1)
        assertTrue(detailed.contains("line1"))
        assertTrue(detailed.contains("line2"))
        assertTrue(detailed.contains("line3"))
    }
}
