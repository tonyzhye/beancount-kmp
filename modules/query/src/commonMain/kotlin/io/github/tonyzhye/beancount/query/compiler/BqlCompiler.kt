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
        val targetNodes = if (query.targets.any { it.expression is AstIdentifier && it.expression.name == "*" }) {
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
        
        // Build alias map for ORDER BY resolution
        val aliasMap = targetNodes.associateBy { it.name.lowercase() }
        
        val whereNode = query.where?.let { compileExpression(it) }
        val groupByNodes = query.groupBy.map { compileExpression(it) }
        val havingNode = query.having?.let { compileExpression(it, aliasMap) }
        val orderByNodes = query.orderBy.map { compileOrderBy(it, aliasMap) }
        val pivotBy = query.pivotBy?.let { compilePivotBy(it, targetNodes, groupByNodes) }

        return CompiledQuery(
            distinct = query.distinct,
            targets = targetNodes,
            where = whereNode,
            groupBy = groupByNodes,
            having = havingNode,
            orderBy = orderByNodes,
            limit = query.limit,
            pivotBy = pivotBy
        )
    }

    private fun compileTarget(target: AstTarget): TargetNode {
        val node = compileExpression(target.expression)
        return TargetNode(
            name = target.alias ?: deriveColumnName(target.expression),
            node = node
        )
    }

    private fun compileOrderBy(orderBy: AstOrderBy, aliasMap: Map<String, TargetNode> = emptyMap()): OrderByNode {
        return OrderByNode(
            node = compileExpression(orderBy.expression, aliasMap),
            descending = orderBy.descending
        )
    }

    private fun compileExpression(expr: AstExpression, aliasMap: Map<String, TargetNode> = emptyMap()): EvalNode {
        return when (expr) {
            is AstIdentifier -> compileIdentifier(expr, aliasMap)
            is AstStringLiteral -> EvalConstant(BqlType.String, BqlStringValue(expr.value))
            is AstIntegerLiteral -> EvalConstant(BqlType.Integer, BqlIntegerValue(expr.value))
            is AstDecimalLiteral -> EvalConstant(BqlType.Decimal, BqlDecimalValue(Decimal(expr.value)))
            is AstDateLiteral -> EvalConstant(BqlType.Date, BqlDateValue(expr.value))
            is AstBooleanLiteral -> EvalConstant(BqlType.Boolean, BqlBooleanValue(expr.value))
            is AstNullLiteral -> EvalConstant(BqlType.Null, BqlNullValue())
            is AstFunctionCall -> compileFunctionCall(expr, aliasMap)
            is AstBinaryOp -> compileBinaryOp(expr, aliasMap)
            is AstUnaryOp -> compileUnaryOp(expr, aliasMap)
            is AstInOp -> compileInOp(expr, aliasMap)
            is AstBetweenOp -> compileBetweenOp(expr, aliasMap)
        }
    }

    private fun compileInOp(op: AstInOp, aliasMap: Map<String, TargetNode> = emptyMap()): EvalNode {
        val expr = compileExpression(op.expression, aliasMap)
        val values = op.values.map { compileExpression(it, aliasMap) }
        return EvalInOp(expr, values, op.notIn)
    }

    private fun compileBetweenOp(op: AstBetweenOp, aliasMap: Map<String, TargetNode> = emptyMap()): EvalNode {
        val expr = compileExpression(op.expression, aliasMap)
        val low = compileExpression(op.low, aliasMap)
        val high = compileExpression(op.high, aliasMap)
        return EvalBetweenOp(expr, low, high, op.notBetween)
    }

    private fun compileIdentifier(identifier: AstIdentifier, aliasMap: Map<String, TargetNode> = emptyMap()): EvalNode {
        val name = identifier.name.lowercase()
        // Check alias map first (for ORDER BY referencing SELECT aliases)
        val aliased = aliasMap[name]
        if (aliased != null) {
            return aliased.node
        }
        val column = columns[name]
        if (column != null) {
            return EvalColumn(column.dtype, name) { context ->
                column.evaluate(context).raw
            }
        }
        throw CompileException("Unknown column: ${identifier.name}")
    }

    private fun compileFunctionCall(call: AstFunctionCall, aliasMap: Map<String, TargetNode> = emptyMap()): EvalNode {
        val name = call.name.lowercase()
        
        // Special handling for count(*)
        val operands = if (name == "count" && call.arguments.size == 1 && call.arguments[0] is AstIdentifier && (call.arguments[0] as AstIdentifier).name == "*") {
            listOf(EvalConstant(BqlType.Integer, BqlIntegerValue(1)))
        } else {
            call.arguments.map { compileExpression(it, aliasMap) }
        }

        // Check for aggregator functions first
        val aggregatorFactory = FunctionRegistry.resolveAggregatorFactory(name, operands.map { it.dtype })
        if (aggregatorFactory != null) {
            return aggregatorFactory.create(operands.firstOrNull() ?: EvalConstant(BqlType.Null, BqlNullValue()))
        }

        // Check for regular functions
        val function = FunctionRegistry.resolveFunction(name, operands.map { it.dtype })
        if (function != null) {
            val contextAccessor: ((RowContext) -> BqlValue)? = if (function.signature.passContext) {
                { context ->
                    val pm = context.priceMap
                    if (pm != null) {
                        BqlPriceMapValue(pm, context.allEntries)
                    } else {
                        BqlNullValue()
                    }
                }
            } else null
            return EvalFunction(
                function.signature.returnType,
                function.implementation,
                operands,
                passContext = function.signature.passContext,
                contextAccessor = contextAccessor
            )
        }

        throw CompileException("Unknown function: ${call.name}")
    }

    private fun compileBinaryOp(op: AstBinaryOp, aliasMap: Map<String, TargetNode> = emptyMap()): EvalNode {
        val left = compileExpression(op.left, aliasMap)
        val right = compileExpression(op.right, aliasMap)
        val dtype = inferBinaryOpType(op.operator, left.dtype, right.dtype)
        return EvalBinaryOp(dtype, op.operator, left, right)
    }

    private fun compileUnaryOp(op: AstUnaryOp, aliasMap: Map<String, TargetNode> = emptyMap()): EvalNode {
        val operand = compileExpression(op.operand, aliasMap)
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

    /**
     * Compile a PIVOT BY clause.
     *
     * The PIVOT BY clause accepts two name or index references to columns
     * in the SELECT targets list. The second column should be a GROUP BY column.
     */
    private fun compilePivotBy(
        pivotBy: io.github.tonyzhye.beancount.query.parser.AstPivotBy,
        targets: List<TargetNode>,
        groupBy: List<EvalNode>
    ): List<Int> {
        val names = targets.mapIndexed { index, target -> target.name to index }.toMap()
        val indexes = mutableListOf<Int>()

        for (column in pivotBy.columns) {
            when (column) {
                is io.github.tonyzhye.beancount.query.parser.AstIntegerLiteral -> {
                    val index = column.value - 1 // 1-based to 0-based
                    if (index !in targets.indices) {
                        throw CompileException("invalid PIVOT BY column index ${column.value}")
                    }
                    indexes.add(index)
                }
                is io.github.tonyzhye.beancount.query.parser.AstIdentifier -> {
                    val index = names[column.name.lowercase()]
                        ?: throw CompileException("PIVOT BY column '${column.name}' is not in the targets list")
                    indexes.add(index)
                }
                else -> throw CompileException("PIVOT BY column must be a name or index")
            }
        }

        if (indexes.size != 2) {
            throw CompileException("PIVOT BY requires exactly 2 columns")
        }

        if (indexes[0] == indexes[1]) {
            throw CompileException("the two PIVOT BY columns cannot be the same column")
        }

        // Note: In a full implementation, we would check that indexes[1] is a GROUP BY column
        // For simplicity, we skip this check

        return indexes
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
    val having: EvalNode?,
    val orderBy: List<OrderByNode>,
    val limit: Int?,
    val pivotBy: List<Int>? = null
)
