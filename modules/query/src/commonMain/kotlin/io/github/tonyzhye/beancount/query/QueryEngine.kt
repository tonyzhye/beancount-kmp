package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.core.Directive
import io.github.tonyzhye.beancount.query.compiler.BqlCompiler
import io.github.tonyzhye.beancount.query.executor.QueryExecutor
import io.github.tonyzhye.beancount.query.executor.QueryResult
import io.github.tonyzhye.beancount.query.parser.BqlParser
import io.github.tonyzhye.beancount.query.tables.*

/**
 * Query engine entry point.
 * Provides a simple API to execute BQL queries against beancount entries.
 */
class QueryEngine(private val entries: List<Directive>) {

    private val postingsTable = PostingsTable(entries)
    private val entriesTable = EntriesTable(entries)
    private val transactionsTable = TransactionsTable(entries)
    private val accountsTable = AccountsTable(entries)
    private val commoditiesTable = CommoditiesTable(entries)
    private val pricesTable = PricesTable(entries)
    private val balancesTable = BalancesTable(entries)

    private val tables: Map<String, Table> = mapOf(
        "postings" to postingsTable,
        "entries" to entriesTable,
        "transactions" to transactionsTable,
        "accounts" to accountsTable,
        "commodities" to commoditiesTable,
        "prices" to pricesTable,
        "balances" to balancesTable
    )

    /**
     * Execute a BQL query string.
     *
     * @param queryString The BQL query to execute
     * @return QueryResult with column names and rows
     * @throws ParseException if the query syntax is invalid
     * @throws CompileException if the query references unknown columns or functions
     */
    fun execute(queryString: String): QueryResult {
        // 1. Parse
        val parser = BqlParser(queryString)
        val ast = parser.parseQuery()

        // 2. Resolve table
        val tableName = ast.from?.tableName ?: "postings"
        val table = tables[tableName.lowercase()]
            ?: throw IllegalArgumentException("Unknown table: $tableName")

        // 3. Compile
        val compiler = BqlCompiler(table)
        val compiledQuery = compiler.compileQuery(ast)

        // 4. Execute
        val executor = QueryExecutor(table)
        return executor.execute(compiledQuery)
    }

    /**
     * Get available table names.
     */
    fun getTableNames(): Set<String> = tables.keys

    /**
     * Get column names for a table.
     */
    fun getColumnNames(tableName: String): List<String> {
        return tables[tableName.lowercase()]?.columns?.keys?.toList() ?: emptyList()
    }
}
