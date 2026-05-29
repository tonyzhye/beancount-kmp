package io.github.tonyzhye.beancount.core

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.random.Random

/**
 * Employer information for example generation.
 */
data class Employer(
    val name: String,
    val address: String
)

/**
 * Options for generating an example ledger.
 */
data class ExampleOptions(
    val dateBegin: LocalDate = LocalDate(Clock.System.todayIn(TimeZone.UTC).year - 2, 1, 1),
    val dateEnd: LocalDate = Clock.System.todayIn(TimeZone.UTC),
    val dateBirth: LocalDate = LocalDate(1980, 5, 12),
    val seed: Int? = null,
    val principalCurrency: String = "USD",
    val employer: Employer? = null,
    val annualSalary: Decimal = Decimal("120000"),
    val includeInvestments: Boolean = true,
    val includeTaxes: Boolean = true,
    val reformat: Boolean = true
)

/**
 * Metadata about the generated example ledger.
 */
data class ExampleMetadata(
    val personName: String,
    val employerName: String,
    val annualSalary: Decimal,
    val principalCurrency: String,
    val dateRange: ClosedRange<LocalDate>
)

/**
 * Result of generating an example ledger.
 */
data class ExampleLedger(
    val entries: List<Directive>,
    val commodities: List<Commodity>,
    val accounts: List<String>,
    val metadata: ExampleMetadata
)

/**
 * Example ledger generator based on beancount.scripts.example.
 *
 * Generates realistic financial history for a fictional person,
 * suitable for testing, documentation, and demonstration.
 */
object ExampleGenerator {

    // Constants
    private val ANNUAL_SALARY = Decimal("120000")
    private val ANNUAL_VACATION_DAYS = Decimal("15")
    private val RENT_DIVISOR = Decimal("50")
    private val RENT_INCREMENT = Decimal("25")

    private val EMPLOYERS = listOf(
        Employer("Hooli", "1 Carloston Rd, Mountain Beer, CA"),
        Employer("BayBook", "1501 Billow Rd, Benlo Park, CA"),
        Employer("Babble", "1 Continuous Loop, Bupertina, CA"),
        Employer("Hoogle", "1600 Amphibious Parkway, River View, CA")
    )

    private val RESTAURANT_NAMES = listOf(
        "Rose Flower",
        "Cafe Modagor",
        "Goba Goba",
        "Kin Soy",
        "Uncle Boons",
        "China Garden",
        "Jewel of Morroco",
        "Chichipotle"
    )

    private val RESTAURANT_NARRATIONS = listOf(
        "Eating out with Joe",
        "Eating out with Natasha",
        "Eating out with Bill",
        "Eating out with Julie",
        "Eating out with work buddies",
        "Eating out after work",
        "Eating out alone",
        "Eating out"
    )

    private val GROCERIES_NAMES = listOf(
        "Onion Market",
        "Good Moods Market",
        "Corner Deli",
        "Farmer Fresh"
    )

    private val HOME_NAME = "New Metropolis"

    /**
     * Generate an example ledger with the given options.
     *
     * @param options Configuration options for generation
     * @return ExampleLedger containing all generated directives
     */
    @JvmStatic
    @JvmOverloads
    fun generate(options: ExampleOptions = ExampleOptions()): ExampleLedger {
        val random = options.seed?.let { Random(it) } ?: Random.Default
        val employer = options.employer ?: EMPLOYERS.random(random)

        val entries = mutableListOf<Directive>()
        val accounts = mutableSetOf<String>()

        // Commodities
        val commodityEntries = generateCommodityEntries(options.dateBirth)
        entries.addAll(commodityEntries)

        // Employment income
        val employmentEntries = generateEmploymentIncome(
            employer.name,
            employer.address,
            options.annualSalary,
            "Assets:CC:Bank1:Checking",
            "Assets:CC:Retirement",
            options.dateBegin,
            options.dateEnd,
            random
        )
        entries.addAll(employmentEntries)

        // Banking expenses
        val rentAmount = roundTo(options.annualSalary / RENT_DIVISOR, RENT_INCREMENT)
        val bankingExpenseEntries = generateBankingExpenses(
            options.dateBegin,
            options.dateEnd,
            "Assets:CC:Bank1:Checking",
            rentAmount,
            random
        )
        entries.addAll(bankingExpenseEntries)

        // Credit card expenses
        val creditEntries = generateRegularCreditExpenses(
            options.dateBirth,
            options.dateBegin,
            options.dateEnd,
            "Liabilities:CC:CreditCard1",
            "Assets:CC:Bank1:Checking",
            random
        )
        entries.addAll(creditEntries)

        // Open entries for expense accounts
        val expenseAccounts = generateExpenseAccounts(options.dateBirth)
        entries.addAll(expenseAccounts)

        // Open entries
        val allAccounts = entries.flatMap { extractAccounts(it) }.distinct()
        val openEntries = generateOpenEntries(options.dateBegin, allAccounts, options.principalCurrency)
        entries.addAll(0, openEntries)

        // Balance checks
        val balanceEntries = generateBalanceChecks(
            entries,
            "Assets:CC:Bank1:Checking",
            generateDateIter(options.dateBegin, options.dateEnd, 90, 120, random)
        )
        entries.addAll(balanceEntries)

        // Sort all entries by date
        val sortedEntries = entries.sorted()

        val metadata = ExampleMetadata(
            personName = "John Doe",
            employerName = employer.name,
            annualSalary = options.annualSalary,
            principalCurrency = options.principalCurrency,
            dateRange = options.dateBegin..options.dateEnd
        )

        return ExampleLedger(
            entries = sortedEntries,
            commodities = commodityEntries,
            accounts = allAccounts,
            metadata = metadata
        )
    }

    /**
     * Generate example ledger as beancount text.
     */
    @JvmStatic
    @JvmOverloads
    fun generateString(options: ExampleOptions = ExampleOptions()): String {
        val ledger = generate(options)
        return formatLedger(ledger, options)
    }

    // Internal generators

    private fun generateCommodityEntries(dateBirth: LocalDate): List<Commodity> {
        val commodities = listOf(
            "USD", "EUR", "GBP", "CAD", "JPY",
            "ITOT", "VEA", "VWO", "AGG", "GLD",
            "HOOL", "MFUND1", "MFUND2"
        )
        return commodities.map { currency ->
            Commodity(
                meta = emptyMap(),
                date = dateBirth,
                currency = currency
            )
        }
    }

    private fun generateEmploymentIncome(
        employerName: String,
        employerAddress: String,
        annualSalary: Decimal,
        accountDeposit: String,
        accountRetirement: String,
        dateBegin: LocalDate,
        dateEnd: LocalDate,
        random: Random
    ): List<Directive> {
        val entries = mutableListOf<Directive>()
        val biWeeklySalary = annualSalary / Decimal("26")
        val taxRate = Decimal("0.25")
        val retirementRate = Decimal("0.06")

        var currentDate = dateBegin
        var payPeriod = 1

        while (currentDate <= dateEnd) {
            val taxAmount = roundTo(biWeeklySalary * taxRate, Decimal("0.01"))
            val retirementAmount = roundTo(biWeeklySalary * retirementRate, Decimal("0.01"))
            val netPay = biWeeklySalary - taxAmount - retirementAmount

            val transaction = Transaction(
                meta = mapOf("filename" to "example.beancount", "lineno" to payPeriod),
                date = currentDate,
                flag = "*",
                payee = employerName,
                narration = "Paycheck $payPeriod",
                postings = listOf(
                    Posting(
                        account = accountDeposit,
                        units = Amount(netPay, "USD")
                    ),
                    Posting(
                        account = accountRetirement,
                        units = Amount(retirementAmount, "USD")
                    ),
                    Posting(
                        account = "Expenses:Taxes:Federal",
                        units = Amount(taxAmount, "USD")
                    ),
                    Posting(
                        account = "Income:Employer1:Salary",
                        units = Amount(-biWeeklySalary, "USD")
                    )
                )
            )
            entries.add(transaction)

            currentDate += DatePeriod(days = 14)
            payPeriod++
        }

        return entries
    }

    private fun generateBankingExpenses(
        dateBegin: LocalDate,
        dateEnd: LocalDate,
        account: String,
        rentAmount: Decimal,
        random: Random
    ): List<Directive> {
        val entries = mutableListOf<Directive>()

        // Monthly rent
        var currentDate = dateBegin
        while (currentDate <= dateEnd) {
            val transaction = Transaction(
                meta = emptyMap(),
                date = currentDate,
                flag = "*",
                payee = HOME_NAME,
                narration = "Monthly rent",
                postings = listOf(
                    Posting(
                        account = account,
                        units = Amount(-rentAmount, "USD")
                    ),
                    Posting(
                        account = "Expenses:Home:Rent",
                        units = Amount(rentAmount, "USD")
                    )
                )
            )
            entries.add(transaction)
            currentDate += DatePeriod(months = 1)
        }

        // Utilities (electricity, internet, phone)
        val utilities = listOf(
            Triple("Electric Company", "Expenses:Home:Electricity", Decimal("80")),
            Triple("Internet Provider", "Expenses:Home:Internet", Decimal("60")),
            Triple("Phone Company", "Expenses:Home:Phone", Decimal("50"))
        )

        for ((payee, expenseAccount, baseAmount) in utilities) {
            var utilityDate = dateBegin
            while (utilityDate <= dateEnd) {
                val variation = Decimal((random.nextDouble() * 20 - 10).toString())
                val amount = roundTo(baseAmount + variation, Decimal("0.01"))

                val transaction = Transaction(
                    meta = emptyMap(),
                    date = utilityDate,
                    flag = "*",
                    payee = payee,
                    narration = "Monthly bill",
                    postings = listOf(
                        Posting(
                            account = account,
                            units = Amount(-amount, "USD")
                        ),
                        Posting(
                            account = expenseAccount,
                            units = Amount(amount, "USD")
                        )
                    )
                )
                entries.add(transaction)
                utilityDate += DatePeriod(months = 1)
            }
        }

        return entries
    }

    private fun generateRegularCreditExpenses(
        dateBirth: LocalDate,
        dateBegin: LocalDate,
        dateEnd: LocalDate,
        accountCredit: String,
        accountChecking: String,
        random: Random
    ): List<Directive> {
        val entries = mutableListOf<Directive>()

        // Restaurant expenses
        val restaurantDates = generateDateRandomSeq(dateBegin, dateEnd, 3, 7, random)
        for (date in restaurantDates) {
            val restaurant = RESTAURANT_NAMES.random(random)
            val narration = RESTAURANT_NARRATIONS.random(random)
            val amount = Decimal((20 + random.nextDouble() * 60).toString())
            val roundedAmount = roundTo(amount, Decimal("0.01"))

            val transaction = Transaction(
                meta = emptyMap(),
                date = date,
                flag = "*",
                payee = restaurant,
                narration = narration,
                postings = listOf(
                    Posting(
                        account = accountCredit,
                        units = Amount(-roundedAmount, "USD")
                    ),
                    Posting(
                        account = "Expenses:Food:Restaurant",
                        units = Amount(roundedAmount, "USD")
                    )
                )
            )
            entries.add(transaction)
        }

        // Grocery expenses
        val groceryDates = generateDateRandomSeq(dateBegin, dateEnd, 5, 10, random)
        for (date in groceryDates) {
            val grocery = GROCERIES_NAMES.random(random)
            val amount = Decimal((30 + random.nextDouble() * 120).toString())
            val roundedAmount = roundTo(amount, Decimal("0.01"))

            val transaction = Transaction(
                meta = emptyMap(),
                date = date,
                flag = "*",
                payee = grocery,
                narration = "Weekly groceries",
                postings = listOf(
                    Posting(
                        account = accountCredit,
                        units = Amount(-roundedAmount, "USD")
                    ),
                    Posting(
                        account = "Expenses:Food:Groceries",
                        units = Amount(roundedAmount, "USD")
                    )
                )
            )
            entries.add(transaction)
        }

        // Credit card payments (monthly)
        var paymentDate = dateBegin
        while (paymentDate <= dateEnd) {
            // Calculate balance to pay (simplified)
            val paymentAmount = Decimal((500 + random.nextDouble() * 1000).toString())
            val roundedPayment = roundTo(paymentAmount, Decimal("0.01"))

            val transaction = Transaction(
                meta = emptyMap(),
                date = paymentDate,
                flag = "*",
                payee = "Credit Card Company",
                narration = "Credit card payment",
                postings = listOf(
                    Posting(
                        account = accountChecking,
                        units = Amount(-roundedPayment, "USD")
                    ),
                    Posting(
                        account = accountCredit,
                        units = Amount(roundedPayment, "USD")
                    )
                )
            )
            entries.add(transaction)
            paymentDate += DatePeriod(months = 1)
        }

        return entries
    }

    private fun generateExpenseAccounts(dateBirth: LocalDate): List<Open> {
        val expenseAccounts = listOf(
            "Expenses:Food:Restaurant",
            "Expenses:Food:Groceries",
            "Expenses:Home:Rent",
            "Expenses:Home:Electricity",
            "Expenses:Home:Internet",
            "Expenses:Home:Phone",
            "Expenses:Transport:Tram",
            "Expenses:Health:Insurance",
            "Expenses:Health:Dental",
            "Expenses:Health:Vision",
            "Expenses:Taxes:Federal",
            "Expenses:Taxes:State",
            "Expenses:Taxes:SocialSecurity",
            "Expenses:Taxes:Medicare",
            "Income:Employer1:Salary",
            "Income:Employer1:Match",
            "Income:Employer1:Vacation",
            "Equity:Opening-Balances"
        )

        return expenseAccounts.map { account ->
            Open(
                meta = emptyMap(),
                date = dateBirth,
                account = account,
                currencies = listOf("USD")
            )
        }
    }

    private fun generateOpenEntries(
        date: LocalDate,
        accounts: List<String>,
        currency: String?
    ): List<Open> {
        return accounts.map { account ->
            Open(
                meta = emptyMap(),
                date = date,
                account = account,
                currencies = if (currency != null) listOf(currency) else emptyList()
            )
        }
    }

    private fun generateBalanceChecks(
        entries: List<Directive>,
        account: String,
        dateIter: Sequence<LocalDate>
    ): List<Balance> {
        val balances = mutableListOf<Balance>()
        var runningBalance = Decimal.ZERO

        for (entry in entries.sorted()) {
            if (entry is Transaction) {
                for (posting in entry.postings) {
                    if (posting.account == account && posting.units != null) {
                        runningBalance += posting.units.number
                    }
                }
            }
        }

        for (date in dateIter) {
            balances.add(
                Balance(
                    meta = emptyMap(),
                    date = date,
                    account = account,
                    amount = Amount(runningBalance, "USD")
                )
            )
        }

        return balances
    }

    // Utility functions

    private fun extractAccounts(directive: Directive): List<String> {
        return when (directive) {
            is Transaction -> directive.postings.map { it.account }
            is Open -> listOf(directive.account)
            is Balance -> listOf(directive.account)
            is Pad -> listOf(directive.account, directive.sourceAccount)
            else -> emptyList()
        }
    }

    private fun generateDateIter(
        dateBegin: LocalDate,
        dateEnd: LocalDate,
        daysMin: Int,
        daysMax: Int,
        random: Random
    ): Sequence<LocalDate> {
        return generateDateRandomSeq(dateBegin, dateEnd, daysMin, daysMax, random)
    }

    private fun generateDateRandomSeq(
        dateBegin: LocalDate,
        dateEnd: LocalDate,
        daysMin: Int,
        daysMax: Int,
        random: Random
    ): Sequence<LocalDate> = sequence {
        var current = dateBegin
        while (current <= dateEnd) {
            yield(current)
            val days = daysMin + random.nextInt(daysMax - daysMin + 1)
            current += DatePeriod(days = days)
        }
    }

    private fun roundTo(value: Decimal, increment: Decimal): Decimal {
        val divided = value / increment
        val rounded = Decimal(divided.toDouble().toLong())
        return rounded * increment
    }

    private fun formatLedger(ledger: ExampleLedger, options: ExampleOptions): String {
        val sb = StringBuilder()

        // Options
        sb.appendLine("option \"title\" \"Example Beancount file\"")
        sb.appendLine("option \"operating_currency\" \"${options.principalCurrency}\"")
        sb.appendLine()

        // Commodities
        for (commodity in ledger.commodities) {
            sb.appendLine("${commodity.date} commodity ${commodity.currency}")
        }
        sb.appendLine()

        // Accounts
        val openEntries = ledger.entries.filterIsInstance<Open>().sortedBy { it.date }
        for (open in openEntries) {
            val currencies = if (open.currencies.isNotEmpty()) {
                " " + open.currencies.joinToString(", ")
            } else ""
            sb.appendLine("${open.date} open ${open.account}$currencies")
        }
        sb.appendLine()

        // Transactions and other entries
        val otherEntries = ledger.entries.filter { it !is Open && it !is Commodity }.sorted()
        for (entry in otherEntries) {
            when (entry) {
                is Transaction -> {
                    val payee = entry.payee?.let { " \"$it\"" } ?: ""
                    val narration = entry.narration?.let { " \"$it\"" } ?: ""
                    sb.appendLine("${entry.date} ${entry.flag}$payee$narration")
                    for (posting in entry.postings) {
                        val units = posting.units?.let { "  ${it.number} ${it.currency}" } ?: ""
                        sb.appendLine("  ${posting.account}$units")
                    }
                }
                is Balance -> {
                    sb.appendLine("${entry.date} balance ${entry.account}  ${entry.amount.number} ${entry.amount.currency}")
                }
                else -> {
                    // Skip other types for now
                }
            }
        }

        return sb.toString()
    }
}
