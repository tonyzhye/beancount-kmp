package io.github.tonyzhye.beancount.query.compiler

import io.github.tonyzhye.beancount.core.Decimal
import io.github.tonyzhye.beancount.query.*
import io.github.tonyzhye.beancount.query.parser.*
import io.github.tonyzhye.beancount.query.functions.FunctionRegistry

/**
 * BQL Compiler - converts AST to EvalNode execution tree.
 * Based on beanquery.query_compile.
 */
class BqlCompiler(private val table: io.github.tonyzhye.beancount.query.tables.Table) {

    private val columns = table.columns

    /**
     * Compile a complete query.
     */
    fun compileQuery(query: AstQuery): CompiledQuery {
        val targetNodes = if (query.targets.any { it.expression is AstIdentifier && (it.expression as AstIdentifier).name == "*" }) {
            // Expand wildcard to all wildcard columns
            table.wildcardColumns.map { colName ->
                val col = table.columns[colName]!!
                TargetNode(
                    name = colName,
                    node = EvalColumn(col.dtype, colName) { context -> col.evaluate(context).raw }
                )
            }
        } else {
            query.targets.map { compileTarget(it) }
        }
        val whereNode = query.where?.let { compileExpression(it) }
        val groupByNodes = query.groupBy.map { compileExpression(it) }
        val orderByNodes = query.orderBy.map { compileOrderBy(it) }

        return CompiledQuery(
            distinct = query.distinct,
            targets = targetNodes,
            where = whereNode,
            groupBy = groupByNodes,
            orderBy = orderByNodes,
            limit = query.limit
        )
    }

    private fun compileTarget(target: AstTarget): TargetNode {
        val node = compileExpression(target.expression)
        return TargetNode(
            name = target.alias ?: deriveColumnName(target.expression),
            node = node
        )
    }

    private fun compileOrderBy(orderBy: AstOrderBy): OrderByNode {
        return OrderByNode(
            node = compileExpression(orderBy.expression),
            descending = orderBy.descending
        )
    }

    private fun compileExpression(expr: AstExpression): EvalNode {
        return when (expr) {
            is AstIdentifier -> compileIdentifier(expr)
            is AstStringLiteral -> EvalConstant(BqlType.String, BqlStringValue(expr.value))
            is AstIntegerLiteral -> EvalConstant(BqlType.Integer, BqlIntegerValue(expr.value))
            is AstDecimalLiteral -> EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal(expr.value)))
            is AstDateLiteral -> EvalConstant(BqlType.Date, BqlDateValue(expr.value))
            is AstBooleanLiteral -> EvalConstant(BqlType.Boolean, BqlBooleanValue(expr.value))
            is AstNullLiteral -> EvalConstant(BqlType.Null, BqlNullValue())
            is AstFunctionCall -> compileFunctionCall(expr)
            is AstBinaryOp -> compileBinaryOp(expr)
            is AstUnaryOp -> compileUnaryOp(expr)
        }
    }

    private fun compileIdentifier(identifier: AstIdentifier): EvalNode {
        val name = identifier.name.lowercase()
        val column = columns[name]
        if (column != null) {
            return EvalColumn(column.dtype, name) { context ->
                column.evaluate(context).raw
            }
        }
        throw CompileException("Unknown column: ${identifier.name}")
    }

    private fun compileFunctionCall(call: AstFunctionCall): EvalNode {
        val name = call.name.lowercase()
        
        // Special handling for count(*)
        val operands = if (name == "count" && call.arguments.size == 1 && call.arguments[0] is AstIdentifier && (call.arguments[0] as AstIdentifier).name == "*") {
            listOf(EvalConstant(BqlType.Integer, BqlIntegerValue(1)))
        } else {
            call.arguments.map { compileExpression(it) }
        }

        // Check for aggregator functions first
        val aggregatorFactory = FunctionRegistry.resolveAggregatorFactory(name, operands.map { it.dtype })
        if (aggregatorFactory != null) {
            return aggregatorFactory.create(operands.firstOrNull() ?: EvalConstant(BqlType.Null, BqlNullValue()))
        }

        // Check for regular functions
        val function = FunctionRegistry.resolveFunction(name, operands.map { it.dtype })
        if (function != null) {
            return EvalFunction(function.signature.returnType, function.implementation, operands)
        }

        throw CompileException("Unknown function: ${call.name}")
    }

    private fun compileBinaryOp(op: AstBinaryOp): EvalNode {
        val left = compileExpression(op.left)
        val right = compileExpression(op.right)
        val dtype = inferBinaryOpType(op.operator, left.dtype, right.dtype)
        return EvalBinaryOp(dtype, op.operator, left, right)
    }

    private fun compileUnaryOp(op: AstUnaryOp): EvalNode {
        val operand = compileExpression(op.operand)
        val dtype = when (op.operator) {
            "NOT" -> BqlType.Boolean
            "-", "+" -> operand.dtype
            else -> throw CompileException("Unknown unary operator: ${op.operator}")
        }
        return EvalUnaryOp(dtype, op.operator, operand)
    }

    private fun inferBinaryOpType(operator: String, left: BqlType, right: BqlType): BqlType {
        return when (operator) {
            "AND", "OR" -> BqlType.Boolean
            "=", "!=", "<", ">", "<=", ">=", "~" -> BqlType.Boolean
            "+", "-", "*", "/" -> {
                if (left == BqlType.Decimal || right == BqlType.Decimal) BqlType.Decimal
                else if (left == BqlType.Integer && right == BqlType.Integer) BqlType.Integer
                else BqlType.Any
            }
            else -> BqlType.Any
        }
    }

    private fun deriveColumnName(expr: AstExpression): String {
        return when (expr) {
            is AstIdentifier -> expr.name
            is AstFunctionCall -> "${expr.name}(${(expr.arguments.map { deriveColumnName(it) }.joinToString(", "))})"
            else -> "expr"
        }
    }
}

class CompileException(message: String) : RuntimeException(message)

data class TargetNode(
    val name: String,
    val node: EvalNode
)

data class OrderByNode(
    val node: EvalNode,
    val descending: Boolean
)

data class CompiledQuery(
    val distinct: Boolean,
    val targets: List<TargetNode>,
    val where: EvalNode?,
    val groupBy: List<EvalNode>,
    val orderBy: List<OrderByNode>,
    val limit: Int?
)
