package io.github.tonyzhye.beancount.query.parser

/**
 * BQL AST nodes.
 * Based on beanquery AST.
 */
sealed interface AstNode

/**
 * SELECT target: expression [AS name]
 */
data class AstTarget(
    val expression: AstExpression,
    val alias: String? = null
) : AstNode

/**
 * FROM clause
 */
data class AstFrom(
    val tableName: String,
    val openDate: kotlinx.datetime.LocalDate? = null,
    val closeDate: kotlinx.datetime.LocalDate? = null,
    val closeAll: Boolean = false,
    val clear: Boolean = false
) : AstNode

/**
 * ORDER BY target
 */
data class AstOrderBy(
    val expression: AstExpression,
    val descending: Boolean = false
) : AstNode

/**
 * Complete query
 */
data class AstQuery(
    val distinct: Boolean = false,
    val targets: List<AstTarget> = emptyList(),
    val from: AstFrom? = null,
    val where: AstExpression? = null,
    val groupBy: List<AstExpression> = emptyList(),
    val orderBy: List<AstOrderBy> = emptyList(),
    val limit: Int? = null
) : AstNode

/**
 * Expression nodes
 */
sealed interface AstExpression : AstNode

data class AstIdentifier(
    val name: String
) : AstExpression

data class AstStringLiteral(
    val value: String
) : AstExpression

data class AstIntegerLiteral(
    val value: Int
) : AstExpression

data class AstDecimalLiteral(
    val value: String
) : AstExpression

data class AstDateLiteral(
    val value: kotlinx.datetime.LocalDate
) : AstExpression

data class AstBooleanLiteral(
    val value: Boolean
) : AstExpression

data class AstNullLiteral(
    val dummy: Unit = Unit
) : AstExpression

data class AstFunctionCall(
    val name: String,
    val arguments: List<AstExpression> = emptyList()
) : AstExpression

data class AstBinaryOp(
    val operator: String,
    val left: AstExpression,
    val right: AstExpression
) : AstExpression

data class AstUnaryOp(
    val operator: String,
    val operand: AstExpression
) : AstExpression
