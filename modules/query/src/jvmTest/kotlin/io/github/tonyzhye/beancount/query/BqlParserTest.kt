package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.query.parser.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for BQL parser.
 */
class BqlParserTest {

    @Test
    fun `should parse basic SELECT`() {
        val parser = BqlParser("SELECT date, account FROM postings")
        val ast = parser.parseQuery()
        assertEquals(QueryType.SELECT, ast.queryType)
        assertEquals(2, ast.targets.size)
        assertEquals("postings", ast.from?.tableName)
    }

    @Test
    fun `should parse SELECT with wildcard`() {
        val parser = BqlParser("SELECT * FROM postings")
        val ast = parser.parseQuery()
        assertEquals(1, ast.targets.size)
        assertTrue(ast.targets[0].expression is AstIdentifier)
        assertEquals("*", (ast.targets[0].expression as AstIdentifier).name)
    }

    @Test
    fun `should parse SELECT with alias`() {
        val parser = BqlParser("SELECT date AS d FROM postings")
        val ast = parser.parseQuery()
        assertEquals("d", ast.targets[0].alias)
    }

    @Test
    fun `should parse SELECT DISTINCT`() {
        val parser = BqlParser("SELECT DISTINCT account FROM postings")
        val ast = parser.parseQuery()
        assertTrue(ast.distinct)
    }

    @Test
    fun `should parse WHERE with comparison`() {
        val parser = BqlParser("SELECT account FROM postings WHERE account = 'Assets:Bank'")
        val ast = parser.parseQuery()
        assertNotNull(ast.where)
        assertTrue(ast.where is AstBinaryOp)
    }

    @Test
    fun `should parse WHERE with AND`() {
        val parser = BqlParser("SELECT account FROM postings WHERE account = 'A' AND number > 0")
        val ast = parser.parseQuery()
        val where = ast.where as AstBinaryOp
        assertEquals("AND", where.operator)
    }

    @Test
    fun `should parse WHERE with OR`() {
        val parser = BqlParser("SELECT account FROM postings WHERE account = 'A' OR account = 'B'")
        val ast = parser.parseQuery()
        val where = ast.where as AstBinaryOp
        assertEquals("OR", where.operator)
    }

    @Test
    fun `should parse WHERE with NOT`() {
        val parser = BqlParser("SELECT account FROM postings WHERE NOT account = 'A'")
        val ast = parser.parseQuery()
        val where = ast.where as AstUnaryOp
        assertEquals("NOT", where.operator)
    }

    @Test
    fun `should parse WHERE with regex`() {
        val parser = BqlParser("SELECT account FROM postings WHERE account ~ 'Assets'")
        val ast = parser.parseQuery()
        val where = ast.where as AstBinaryOp
        assertEquals("~", where.operator)
    }

    @Test
    fun `should parse WHERE with BETWEEN`() {
        val parser = BqlParser("SELECT account FROM postings WHERE number BETWEEN 10 AND 20")
        val ast = parser.parseQuery()
        assertTrue(ast.where is AstBetweenOp)
    }

    @Test
    fun `should parse WHERE with unary NOT`() {
        val parser = BqlParser("SELECT account FROM postings WHERE NOT account = 'A'")
        val ast = parser.parseQuery()
        assertTrue(ast.where is AstUnaryOp)
        val notOp = ast.where as AstUnaryOp
        assertEquals("NOT", notOp.operator)
    }

    @Test
    fun `should parse GROUP BY`() {
        val parser = BqlParser("SELECT account, sum(number) FROM postings GROUP BY account")
        val ast = parser.parseQuery()
        assertEquals(1, ast.groupBy.size)
        assertEquals("account", (ast.groupBy[0] as AstIdentifier).name)
    }

    @Test
    fun `should parse HAVING`() {
        val parser = BqlParser("SELECT account, count(*) FROM postings GROUP BY account HAVING count(*) > 1")
        val ast = parser.parseQuery()
        assertNotNull(ast.having)
    }

    @Test
    fun `should parse ORDER BY`() {
        val parser = BqlParser("SELECT account FROM postings ORDER BY account DESC")
        val ast = parser.parseQuery()
        assertEquals(1, ast.orderBy.size)
        assertEquals("account", (ast.orderBy[0].expression as AstIdentifier).name)
        assertTrue(ast.orderBy[0].descending)
    }

    @Test
    fun `should parse LIMIT`() {
        val parser = BqlParser("SELECT account FROM postings LIMIT 10")
        val ast = parser.parseQuery()
        assertEquals(10, ast.limit)
    }

    @Test
    fun `should parse PIVOT BY`() {
        val parser = BqlParser("SELECT account, month FROM postings GROUP BY account, month PIVOT BY account, month")
        val ast = parser.parseQuery()
        assertEquals(2, ast.pivotBy?.columns?.size)
    }

    @Test
    fun `should parse JOURNAL`() {
        val parser = BqlParser("JOURNAL 'Assets:Bank'")
        val ast = parser.parseQuery()
        assertEquals(QueryType.JOURNAL, ast.queryType)
        assertEquals(7, ast.targets.size)
        val where = ast.where as AstBinaryOp
        assertEquals("Assets:Bank", (where.right as AstStringLiteral).value)
    }

    @Test
    fun `should parse JOURNAL with FROM`() {
        val parser = BqlParser("JOURNAL 'Assets:Bank' FROM postings")
        val ast = parser.parseQuery()
        assertEquals("postings", ast.from?.tableName)
    }

    @Test
    fun `should parse function call`() {
        val parser = BqlParser("SELECT year(date) FROM postings")
        val ast = parser.parseQuery()
        val expr = ast.targets[0].expression as AstFunctionCall
        assertEquals("year", expr.name)
        assertEquals(1, expr.arguments.size)
    }

    @Test
    fun `should parse nested function call`() {
        val parser = BqlParser("SELECT parent(root(account)) FROM postings")
        val ast = parser.parseQuery()
        val expr = ast.targets[0].expression as AstFunctionCall
        assertEquals("parent", expr.name)
        assertTrue(expr.arguments[0] is AstFunctionCall)
    }

    @Test
    fun `should parse arithmetic expression`() {
        val parser = BqlParser("SELECT number + 10 FROM postings")
        val ast = parser.parseQuery()
        val expr = ast.targets[0].expression as AstBinaryOp
        assertEquals("+", expr.operator)
    }

    @Test
    fun `should parse date literal`() {
        val parser = BqlParser("SELECT date FROM postings WHERE date = 2024-01-15")
        val ast = parser.parseQuery()
        val where = ast.where as AstBinaryOp
        val right = where.right as AstDateLiteral
        assertEquals(LocalDate(2024, 1, 15), right.value)
    }

    @Test
    fun `should parse decimal literal`() {
        val parser = BqlParser("SELECT number FROM postings WHERE number = 3.14")
        val ast = parser.parseQuery()
        val where = ast.where as AstBinaryOp
        assertTrue(where.right is AstDecimalLiteral)
    }

    @Test
    fun `should parse integer literal`() {
        val parser = BqlParser("SELECT number FROM postings WHERE number = 42")
        val ast = parser.parseQuery()
        val where = ast.where as AstBinaryOp
        assertTrue(where.right is AstIntegerLiteral)
    }

    @Test
    fun `should parse boolean literals`() {
        val parser = BqlParser("SELECT flag FROM postings WHERE flag = TRUE")
        val ast = parser.parseQuery()
        val where = ast.where as AstBinaryOp
        assertTrue(where.right is AstBooleanLiteral)
        assertTrue((where.right as AstBooleanLiteral).value)
    }

    @Test
    fun `should parse NULL literal`() {
        val parser = BqlParser("SELECT account FROM postings WHERE account = NULL")
        val ast = parser.parseQuery()
        val where = ast.where as AstBinaryOp
        assertTrue(where.right is AstNullLiteral)
    }

    @Test
    fun `should parse string with single quotes`() {
        val parser = BqlParser("SELECT account FROM postings WHERE account = 'Assets:Bank'")
        val ast = parser.parseQuery()
        val where = ast.where as AstBinaryOp
        val right = where.right as AstStringLiteral
        assertEquals("Assets:Bank", right.value)
    }

    @Test
    fun `should parse string with double quotes`() {
        val parser = BqlParser("SELECT account FROM postings WHERE account = \"Assets:Bank\"")
        val ast = parser.parseQuery()
        val where = ast.where as AstBinaryOp
        val right = where.right as AstStringLiteral
        assertEquals("Assets:Bank", right.value)
    }

    @Test
    fun `should parse escaped string`() {
        val parser = BqlParser("SELECT account FROM postings WHERE account = 'line1\\nline2'")
        val ast = parser.parseQuery()
        val where = ast.where as AstBinaryOp
        val right = where.right as AstStringLiteral
        assertEquals("line1\nline2", right.value)
    }

    @Test
    fun `should parse all comparison operators`() {
        val ops = listOf("=", "!=", "<", ">", "<=", ">=")
        for (op in ops) {
            val parser = BqlParser("SELECT account FROM postings WHERE number $op 10")
            val ast = parser.parseQuery()
            val where = ast.where as AstBinaryOp
            assertEquals(op, where.operator, "Failed for operator $op")
        }
    }

    @Test
    fun `should parse unary minus`() {
        val parser = BqlParser("SELECT -number FROM postings")
        val ast = parser.parseQuery()
        val expr = ast.targets[0].expression as AstUnaryOp
        assertEquals("-", expr.operator)
    }

    @Test
    fun `should parse unary plus`() {
        val parser = BqlParser("SELECT +number FROM postings")
        val ast = parser.parseQuery()
        val expr = ast.targets[0].expression as AstUnaryOp
        assertEquals("+", expr.operator)
    }

    @Test
    fun `should parse DEFINE statement`() {
        val parser = BqlParser("DEFINE start_date = 2024-01-01; SELECT date FROM postings")
        val ast = parser.parseQuery()
        assertEquals(QueryType.SELECT, ast.queryType)
    }

    @Test
    fun `should parse multiple DEFINE statements`() {
        val parser = BqlParser("DEFINE a = 1; DEFINE b = 2; SELECT account FROM postings")
        val ast = parser.parseQuery()
        assertEquals(QueryType.SELECT, ast.queryType)
    }

    @Test
    fun `should throw on invalid query`() {
        assertThrows(ParseException::class.java) {
            BqlParser("INVALID query").parseQuery()
        }
    }

    @Test
    fun `should throw on missing FROM table`() {
        assertThrows(ParseException::class.java) {
            BqlParser("SELECT account FROM").parseQuery()
        }
    }

    @Test
    fun `should parse empty WHERE`() {
        // WHERE without condition should fail
        assertThrows(ParseException::class.java) {
            BqlParser("SELECT account FROM postings WHERE").parseQuery()
        }
    }

    @Test
    fun `should parse query without FROM`() {
        val parser = BqlParser("SELECT 1 + 2")
        val ast = parser.parseQuery()
        assertNull(ast.from)
    }

    @Test
    fun `should parse query with multiple ORDER BY columns`() {
        val parser = BqlParser("SELECT account FROM postings ORDER BY date ASC, number DESC")
        val ast = parser.parseQuery()
        assertEquals(2, ast.orderBy.size)
        assertFalse(ast.orderBy[0].descending)
        assertTrue(ast.orderBy[1].descending)
    }

    @Test
    fun `should parse query with multiple GROUP BY columns`() {
        val parser = BqlParser("SELECT account, month FROM postings GROUP BY account, month")
        val ast = parser.parseQuery()
        assertEquals(2, ast.groupBy.size)
    }

    @Test
    fun `should parse complex expression with parentheses`() {
        val parser = BqlParser("SELECT account FROM postings WHERE (account = 'A' OR account = 'B') AND number > 0")
        val ast = parser.parseQuery()
        assertNotNull(ast.where)
    }

    @Test
    fun `should parse ORDER BY with expression`() {
        val parser = BqlParser("SELECT account FROM postings ORDER BY year(date) DESC")
        val ast = parser.parseQuery()
        assertTrue(ast.orderBy[0].expression is AstFunctionCall)
    }

    @Test
    fun `should parse HAVING with aggregate`() {
        val parser = BqlParser("SELECT account, sum(number) FROM postings GROUP BY account HAVING sum(number) > 100")
        val ast = parser.parseQuery()
        assertTrue(ast.having is AstBinaryOp)
    }

    @Test
    fun `should parse PIVOT BY with column references`() {
        val parser = BqlParser("SELECT account, month, sum(number) FROM postings GROUP BY account, month PIVOT BY 1, 2")
        val ast = parser.parseQuery()
        val pivot = ast.pivotBy
        assertNotNull(pivot)
        assertEquals(2, pivot!!.columns.size)
    }
}
