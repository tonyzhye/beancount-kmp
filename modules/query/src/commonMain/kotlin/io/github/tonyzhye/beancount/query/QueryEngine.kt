package io.github.tonyzhye.beancount.query

import io.github.tonyzhye.beancount.core.Directive
import io.github.tonyzhye.beancount.core.PriceDatabase
import io.github.tonyzhye.beancount.query.compiler.BqlCompiler
import io.github.tonyzhye.beancount.query.executor.QueryExecutor
import io.github.tonyzhye.beancount.query.executor.QueryResult
import io.github.tonyzhye.beancount.query.parser.BqlParser
import io.github.tonyzhye.beancount.query.parser.QueryType
import io.github.tonyzhye.beancount.query.tables.*

/**
 * Query engine entry point.
 * Provides a simple API to execute BQL queries against beancount entries.
 */
class QueryEngine(private val entries: List<Directive>) {

    private val priceMap = PriceDatabase.buildPriceMap(entries)

    private val postingsTable = PostingsTable(entries, priceMap)
    private val entriesTable = EntriesTable(entries, priceMap)
    private val transactionsTable = TransactionsTable(entries, priceMap)
    private val accountsTable = AccountsTable(entries)
    private val commoditiesTable = CommoditiesTable(entries)
    private val pricesTable = PricesTable(entries, priceMap)
    private val balancesTable = BalancesTable(entries, priceMap)
    private val notesTable = NotesTable(entries)
    private val eventsTable = EventsTable(entries)
    private val documentsTable = DocumentsTable(entries)

    private val tables: Map<String, Table> = mapOf(
        "postings" to postingsTable,
        "entries" to entriesTable,
        "transactions" to transactionsTable,
        "accounts" to accountsTable,
        "commodities" to commoditiesTable,
        "prices" to pricesTable,
        "balances" to balancesTable,
        "notes" to notesTable,
        "events" to eventsTable,
        "documents" to documentsTable
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

        // 3. Apply time-slicing if specified
        val filteredEntries = applyTimeSlicing(entries, ast.from)
        val timeSlicedTable = createTableForName(tableName, filteredEntries)

        // 4. Compile
        val compiler = BqlCompiler(timeSlicedTable)
        val compiledQuery = compiler.compileQuery(ast)

        // 5. Execute
        val executor = QueryExecutor(timeSlicedTable)
        return executor.execute(compiledQuery)
    }

    private fun applyTimeSlicing(
        entries: List<Directive>,
        from: io.github.tonyzhye.beancount.query.parser.AstFrom?
    ): List<Directive> {
        if (from == null) return entries

        var result = entries

        // OPEN ON date: include only entries after open date
        if (from.openDate != null) {
            result = result.filter { it.date >= from.openDate }
        }

        // CLOSE ON date: include only entries before close date
        if (from.closeDate != null) {
            result = result.filter { it.date < from.closeDate }
        }

        // CLOSE ON TRUE: include only open accounts
        if (from.closeAll) {
            val closeDates = result
                .filterIsInstance<io.github.tonyzhye.beancount.core.Close>()
                .associate { it.account to it.date }
            result = result.filter { entry ->
                val account = when (entry) {
                    is io.github.tonyzhye.beancount.core.Transaction -> entry.postings.firstOrNull()?.account
                    is io.github.tonyzhye.beancount.core.Open -> entry.account
                    is io.github.tonyzhye.beancount.core.Close -> entry.account
                    is io.github.tonyzhye.beancount.core.Balance -> entry.account
                    is io.github.tonyzhye.beancount.core.Note -> entry.account
                    is io.github.tonyzhye.beancount.core.Document -> entry.account
                    is io.github.tonyzhye.beancount.core.Pad -> entry.account
                    else -> null
                }
                if (account != null && account in closeDates) {
                    entry.date < closeDates.getValue(account)
                } else {
                    true
                }
            }
        }

        return result
    }

    private fun createTableForName(tableName: String, entries: List<Directive>): Table {
        val filteredPriceMap = PriceDatabase.buildPriceMap(entries)
        return when (tableName.lowercase()) {
            "postings" -> PostingsTable(entries, filteredPriceMap)
            "entries" -> EntriesTable(entries, filteredPriceMap)
            "transactions" -> TransactionsTable(entries, filteredPriceMap)
            "accounts" -> AccountsTable(entries)
            "commodities" -> CommoditiesTable(entries)
            "prices" -> PricesTable(entries, filteredPriceMap)
            "balances" -> BalancesTable(entries, filteredPriceMap)
            "notes" -> NotesTable(entries)
            "events" -> EventsTable(entries)
            "documents" -> DocumentsTable(entries)
            else -> throw IllegalArgumentException("Unknown table: $tableName")
        }
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
