package io.github.tonyzhye.beancount.core

/**
 * Validation function type.
 * Takes entries and options, returns list of validation errors.
 * Based on beancount.ops.validation.
 */
typealias Validation = (List<Directive>, Options) -> List<ValidationError>

/**
 * Basic validations that are always run.
 * Based on beancount.ops.validation.BASIC_VALIDATIONS.
 */
val BASIC_VALIDATIONS: List<Validation> = listOf(
    ::validateOpenClose,
    ::validateActiveAccounts,
    ::validateCurrencyConstraints,
    ::validateDuplicateBalances,
    ::validateDuplicateCommodities,
    ::validateCheckTransactionBalances
)

/**
 * Hardcore validations - only enabled by bean-check.
 * Based on beancount.ops.validation.HARDCORE_VALIDATIONS.
 */
val HARDCORE_VALIDATIONS: List<Validation> = listOf(
    ::validateDataTypes
)

// TODO: Implement validation functions
fun validateOpenClose(entries: List<Directive>, options: Options): List<ValidationError> {
    return emptyList()
}

fun validateActiveAccounts(entries: List<Directive>, options: Options): List<ValidationError> {
    return emptyList()
}

fun validateCurrencyConstraints(entries: List<Directive>, options: Options): List<ValidationError> {
    return emptyList()
}

fun validateDuplicateBalances(entries: List<Directive>, options: Options): List<ValidationError> {
    return emptyList()
}

fun validateDuplicateCommodities(entries: List<Directive>, options: Options): List<ValidationError> {
    return emptyList()
}

fun validateCheckTransactionBalances(entries: List<Directive>, options: Options): List<ValidationError> {
    return emptyList()
}

fun validateDataTypes(entries: List<Directive>, options: Options): List<ValidationError> {
    return emptyList()
}
