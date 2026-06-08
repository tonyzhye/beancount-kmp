package io.github.tonyzhye.beancount.core

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for AccountTypesUtil.
 */
class AccountTypesUtilTest {

    @Test
    fun `isIncomeStatementAccount should identify income and expenses`() {
        val util = AccountTypesUtil()
        assertTrue(util.isIncomeStatementAccount("Income:Salary"))
        assertTrue(util.isIncomeStatementAccount("Expenses:Food"))
        assertFalse(util.isIncomeStatementAccount("Assets:Bank"))
        assertFalse(util.isIncomeStatementAccount("Liabilities:Credit"))
    }
}
