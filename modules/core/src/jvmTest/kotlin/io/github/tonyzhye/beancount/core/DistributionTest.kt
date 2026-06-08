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

package io.github.tonyzhye.beancount.core

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for Distribution functionality.
 */
class DistributionTest {

    @Test
    fun `should accumulate counts correctly`() {
        val dist = Distribution()
        dist.add(2)
        dist.add(2)
        dist.add(5)

        assertEquals(2, dist.count(2))
        assertEquals(1, dist.count(5))
        assertEquals(0, dist.count(10))
        assertEquals(3, dist.total())
    }

    @Test
    fun `should compute mode correctly`() {
        val dist = Distribution()
        dist.add(1)
        dist.add(2)
        dist.add(2)
        dist.add(3)

        assertEquals(2, dist.mode())
    }

    @Test
    fun `should compute min and max`() {
        val dist = Distribution()
        dist.add(5)
        dist.add(1)
        dist.add(10)

        assertEquals(1, dist.min())
        assertEquals(10, dist.max())
    }

    @Test
    fun `should compute frequency correctly`() {
        val dist = Distribution()
        dist.add(2)
        dist.add(2)
        dist.add(5)

        assertEquals(2.0 / 3.0, dist.frequency(2), 0.001)
        assertEquals(1.0 / 3.0, dist.frequency(5), 0.001)
    }

    @Test
    fun `should return null for empty distribution`() {
        val dist = Distribution()
        assertNull(dist.mode())
        assertNull(dist.min())
        assertNull(dist.max())
        assertTrue(dist.isEmpty())
    }

    @Test
    fun `should update from another distribution`() {
        val dist1 = Distribution()
        dist1.add(1)
        dist1.add(2)

        val dist2 = Distribution()
        dist2.add(2)
        dist2.add(3)

        dist1.updateFrom(dist2)

        assertEquals(1, dist1.count(1))
        assertEquals(2, dist1.count(2))
        assertEquals(1, dist1.count(3))
        assertEquals(4, dist1.total())
    }

    @Test
    fun `should compute mean correctly`() {
        val dist = Distribution()
        dist.add(2)
        dist.add(4)
        dist.add(6)

        assertEquals(4.0, dist.mean(), 0.001)
    }

    @Test
    fun `addAll should add multiple values`() {
        val dist = Distribution()
        dist.addAll(listOf(1, 2, 2, 3))

        assertEquals(1, dist.count(1))
        assertEquals(2, dist.count(2))
        assertEquals(1, dist.count(3))
    }

    @Test
    fun `buildFractionalDistribution should work`() {
        val numbers = listOf(
            Decimal("100.00"),
            Decimal("50.0"),
            Decimal("25")
        )
        val dist = buildFractionalDistribution(numbers)

        assertEquals(1, dist.count(2))
        assertEquals(1, dist.count(1))
        assertEquals(1, dist.count(0))
    }

    @Test
    fun `buildPostingCountDistribution should work`() {
        val entries = listOf(
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test",
                postings = listOf(
                    Posting(account = "A", units = Amount(Decimal("1"), "USD")),
                    Posting(account = "B", units = Amount(Decimal("-1"), "USD"))
                )
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2024, 1, 2),
                flag = "*",
                narration = "Test 2",
                postings = listOf(
                    Posting(account = "A", units = Amount(Decimal("1"), "USD")),
                    Posting(account = "B", units = Amount(Decimal("-1"), "USD")),
                    Posting(account = "C", units = Amount(Decimal("1"), "USD"))
                )
            )
        )

        val dist = buildPostingCountDistribution(entries)
        assertEquals(1, dist.count(2))
        assertEquals(1, dist.count(3))
    }

    @Test
    fun `buildMonthlyFrequencyDistribution should work`() {
        val entries = listOf(
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 1),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test",
                postings = listOf(Posting(account = "A", units = Amount(Decimal("1"), "USD")))
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 2),
                date = LocalDate(2024, 1, 15),
                flag = "*",
                narration = "Test 2",
                postings = listOf(Posting(account = "A", units = Amount(Decimal("1"), "USD")))
            ),
            Transaction(
                meta = mapOf("filename" to "test.beancount", "lineno" to 3),
                date = LocalDate(2024, 2, 1),
                flag = "*",
                narration = "Test 3",
                postings = listOf(Posting(account = "A", units = Amount(Decimal("1"), "USD")))
            )
        )

        val dist = buildMonthlyFrequencyDistribution(entries)
        assertEquals(1, dist.count(2)) // January has 2 transactions
        assertEquals(1, dist.count(1)) // February has 1 transaction
    }
}
