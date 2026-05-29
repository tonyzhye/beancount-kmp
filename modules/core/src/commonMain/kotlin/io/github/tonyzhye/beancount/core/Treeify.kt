package io.github.tonyzhye.beancount.core

/**
 * Treeify - Convert hierarchical text columns into ASCII tree structures.
 *
 * Based on beancount.tools.treeify
 *
 * This tool scans text for columns containing hierarchical identifiers
 * (like "Assets:Bank:Checking") and replaces them with tree structures:
 *
 * Before:
 *   Assets:Bank:Checking      100.00 USD
 *   Assets:Bank:Savings        50.00 USD
 *   Assets:Cash                20.00 USD
 *
 * After:
 *   Assets
 *   |-- Bank
 *   |   |-- Checking          100.00 USD
 *   |   |-- Savings            50.00 USD
 *   |-- Cash                   20.00 USD
 */

/**
 * Tree node for structured output.
 *
 * Can be used by UI components (Android, Web, Desktop) to render
 * hierarchical data with their own tree widgets.
 */
data class TreeifyNode @JvmOverloads constructor(
    val name: String,
    val depth: Int,
    val lineNumbers: List<Int>,
    val originalTexts: List<String>,
    val isLast: Boolean,
    val children: List<TreeifyNode> = emptyList()
) {
    val isLeaf: Boolean get() = children.isEmpty()
    val hasData: Boolean get() = lineNumbers.isNotEmpty()
}

/**
 * Structured result of treeify operation.
 */
data class TreeifyResult(
    val roots: List<TreeifyNode>,
    val left: Int,
    val right: Int
)

object Treeify {

    /**
     * Default pattern for beancount account names.
     */
    const val DEFAULT_PATTERN = """(Assets|Liabilities|Equity|Income|Expenses)(:[A-Z0-9][A-Za-z0-9-_']*)*"""
    
    /**
     * Default delimiter between columns.
     */
    const val DEFAULT_DELIMITER = """[ \t]+"""
    
    /**
     * Default component splitter.
     */
    const val DEFAULT_SPLITTER = ":"
    
    /**
     * Pattern for loose account names.
     */
    const val LOOSE_PATTERN = """\b([A-Za-z0-9-_']+)(:[A-Za-z0-9-_']+)+\b"""
    
    /**
     * Pattern for filenames.
     */
    const val FILENAME_PATTERN = """([^ \t]*)(/[^ \t]*)+"""
    
    // Tree rendering prefixes
    private const val PREFIX_CHILD_1 = "|-- "
    private const val PREFIX_CHILD_C = "|   "
    private const val PREFIX_LEAF_1 = "`-- "
    private const val PREFIX_LEAF_C = "    "

    /**
     * Treeify input text.
     * 
     * @param input Input text lines
     * @param pattern Regex pattern for hierarchical identifiers
     * @param delimiter Regex for column delimiter detection
     * @param splitter String or regex for splitting components
     * @param filler Filler string for new lines
     * @return Treeified output text
     */
    fun treeify(
        input: String,
        pattern: String = DEFAULT_PATTERN,
        delimiter: String = DEFAULT_DELIMITER,
        splitter: String = DEFAULT_SPLITTER,
        filler: String = " "
    ): String {
        val lines = input.lines()
        
        // Find the hierarchical column
        val result = findColumn(lines, pattern, delimiter) ?: return input
        val (columnMatches, left, right) = result
        
        // Build tree from matches
        val root = createTree(columnMatches, splitter)
        
        // Render tree
        val (treeLines, newColumnWidth) = renderTree(root)
        
        // Combine with original text
        return combine(lines, treeLines, left, right, newColumnWidth, filler)
    }

    /**
     * Treeify input text and return structured result.
     *
     * This method returns the hierarchical tree structure as a list of [TreeifyNode]
     * objects, suitable for rendering by UI components (Android RecyclerView,
     * Web tree widgets, Desktop tree views, etc.).
     *
     * @param input Input text lines
     * @param pattern Regex pattern for hierarchical identifiers
     * @param delimiter Regex for column delimiter detection
     * @param splitter String or regex for splitting components
     * @return [TreeifyResult] containing tree roots and column bounds, or null if no column found
     */
    @JvmStatic
    @JvmOverloads
    fun treeifyStructured(
        input: String,
        pattern: String = DEFAULT_PATTERN,
        delimiter: String = DEFAULT_DELIMITER,
        splitter: String = DEFAULT_SPLITTER
    ): TreeifyResult? {
        val lines = input.lines()

        val result = findColumn(lines, pattern, delimiter) ?: return null
        val (columnMatches, left, right) = result

        val root = createTree(columnMatches, splitter)
        val roots = convertToPublicNodes(root.children)

        return TreeifyResult(roots, left, right)
    }

    /**
     * Convert internal Node tree to public TreeifyNode structure.
     */
    private fun convertToPublicNodes(
        nodes: List<Node>,
        depth: Int = 0
    ): List<TreeifyNode> {
        return nodes.mapIndexed { index, node ->
            TreeifyNode(
                name = node.name,
                depth = depth,
                lineNumbers = node.lineNumbers.toList(),
                originalTexts = node.originalTexts.toList(),
                isLast = index == nodes.size - 1,
                children = convertToPublicNodes(node.children, depth + 1)
            )
        }
    }

    /**
     * Find a column containing hierarchical data.
     */
    private fun findColumn(
        lines: List<String>,
        pattern: String,
        delimiter: String
    ): Triple<List<Pair<Int, String>>, Int, Int>? {
        val regex = Regex(pattern)
        val delimRegex = Regex(delimiter)
        
        // Group matches by start position
        val beginnings = mutableMapOf<Int, MutableList<ColumnMatch>>()
        
        lines.forEachIndexed { no, line ->
            regex.findAll(line).forEach { match: kotlin.text.MatchResult ->
                val matchEnd = match.value.length + match.range.first
                val afterMatch = line.substring(matchEnd)
                val hasDelimiter = delimRegex.find(afterMatch) != null || afterMatch.isEmpty()
                if (hasDelimiter) {
                    beginnings.getOrPut(match.range.first) { mutableListOf() }
                        .add(ColumnMatch(no, line, match, matchEnd))
                }
            }
        }
        
        // Find a valid column (rightmost text doesn't overlap with following content)
        for ((leftmostColumn, columnMatches) in beginnings.toSortedMap()) {
            val rightmostColumn = columnMatches.maxOf { it.matchEnd }
            
            val followingColumn = columnMatches.minOf { colMatch ->
                val afterMatch = colMatch.line.substring(colMatch.matchEnd)
                val delimMatch = Regex(delimiter).find(afterMatch)
                if (delimMatch != null) {
                    colMatch.matchEnd + delimMatch.value.length
                } else {
                    10000
                }
            }
            
            if (rightmostColumn < followingColumn) {
                val matches = columnMatches.map { colMatch ->
                    colMatch.lineNo to colMatch.match.value
                }
                return Triple(matches, leftmostColumn, rightmostColumn)
            }
        }
        
        return null
    }
    
    private data class ColumnMatch(
        val lineNo: Int,
        val line: String,
        val match: kotlin.text.MatchResult,
        val matchEnd: Int
    )

    /**
     * Tree node.
     */
    private class Node(val name: String) {
        val children = mutableListOf<Node>()
        val lineNumbers = mutableListOf<Int>()
        val originalTexts = mutableListOf<String>()
        
        override fun toString(): String = "<Node $name ${children.map { it.name }}>"
    }

    /**
     * Build tree from matches.
     */
    private fun createTree(
        columnMatches: List<Pair<Int, String>>,
        splitter: String
    ): Node {
        val root = Node("")
        
        for ((no, name) in columnMatches) {
            val parts = name.split(splitter)
            var node = root
            
            for (part in parts) {
                val lastNode = node.children.lastOrNull()
                val childNode = if (lastNode?.name == part) {
                    lastNode
                } else {
                    val newNode = Node(part)
                    node.children.add(newNode)
                    newNode
                }
                node = childNode
            }
            
            node.lineNumbers.add(no)
            node.originalTexts.add(name)
        }
        
        return root
    }

    /**
     * Render tree as ASCII.
     */
    private fun renderTree(root: Node): Pair<List<TreeLine>, Int> {
        val lines = mutableListOf<TreeLine>()
        val stack = mutableListOf<StackItem>()
        
        // Push children of root
        root.children.reversed().forEachIndexed { index, child ->
            stack.add(StackItem("", child.name, child, index == 0))
        }
        
        while (stack.isNotEmpty()) {
            val (prefix, name, node, isLast) = stack.removeAt(stack.size - 1)
            
            val (first, cont) = if (isLast) {
                prefix + PREFIX_LEAF_1 to prefix + PREFIX_LEAF_C
            } else {
                prefix + PREFIX_CHILD_1 to prefix + PREFIX_CHILD_C
            }
            
            val contName = if (node.children.isNotEmpty()) PREFIX_CHILD_C else PREFIX_LEAF_C
            
            lines.add(TreeLine(first + name, cont + contName, node))
            
            // Push children
            node.children.reversed().forEachIndexed { index, child ->
                stack.add(StackItem(cont, child.name, child, index == 0))
            }
        }
        
        if (lines.isEmpty()) {
            return lines to 0
        }
        
        val maxWidth = lines.maxOf { it.firstLine.length }
        
        return lines.map { line ->
            TreeLine(
                firstLine = line.firstLine.padEnd(maxWidth),
                contLine = line.contLine.padEnd(maxWidth),
                node = line.node
            )
        } to maxWidth
    }

    private data class TreeLine(
        val firstLine: String,
        val contLine: String,
        val node: Node
    )
    
    private data class StackItem(
        val prefix: String,
        val name: String,
        val node: Node,
        val isLast: Boolean
    )

    /**
     * Group tree lines by input line number.
     */
    private fun enumTreeByLineNum(treeLines: List<TreeLine>): List<Pair<Int, List<Pair<String, Node>>>> {
        val result = mutableListOf<Pair<Int, List<Pair<String, Node>>>>()
        var pending = mutableListOf<Pair<String, Node>>()
        
        for ((firstLine, contLine, node) in treeLines) {
            if (node.lineNumbers.isEmpty()) {
                pending.add(firstLine to node)
            } else {
                var line = firstLine
                for (no in node.lineNumbers) {
                    pending.add(line to node)
                    line = contLine
                    result.add(no to pending.toList())
                    pending = mutableListOf()
                }
            }
        }
        
        if (pending.isNotEmpty()) {
            result.add(-1 to pending.toList())
        }
        
        return result
    }

    /**
     * Combine tree with original text.
     */
    private fun combine(
        lines: List<String>,
        treeLines: List<TreeLine>,
        left: Int,
        right: Int,
        newColumnWidth: Int,
        filler: String
    ): String {
        val enumLines = enumTreeByLineNum(treeLines)
        var enumIndex = 0
        
        val result = StringBuilder()
        
        for ((inputNo, inputLine) in lines.withIndex()) {
            if (enumIndex >= enumLines.size) {
                // No more tree lines, just copy input
                result.appendLine(inputLine)
                continue
            }
            
            val (no, treeItems) = enumLines[enumIndex]
            
            if (inputNo < no) {
                // Before the next tree line
                result.appendLine(inputLine)
            } else {
                // At or after the tree line
                for ((line, node) in treeItems) {
                    if (node.lineNumbers.isEmpty()) {
                        // New inserted line
                        val prefix = filler.repeat((left / filler.length) + 1)
                        result.append(prefix.substring(0, left.coerceAtMost(prefix.length)))
                        result.appendLine(line.trimEnd())
                    } else {
                        // Replace original line
                        val prefix = inputLine.substring(0, left.coerceAtMost(inputLine.length))
                        val suffix = if (right < inputLine.length) {
                            inputLine.substring(right)
                        } else ""
                        val formattedLine = line.padEnd((right - left).coerceAtLeast(newColumnWidth))
                        result.append(prefix)
                        result.append(formattedLine)
                        result.appendLine(suffix)
                    }
                }
                enumIndex++
            }
        }
        
        // Add remaining tree lines
        while (enumIndex < enumLines.size) {
            val (_, treeItems) = enumLines[enumIndex]
            for ((line, node) in treeItems) {
                if (node.lineNumbers.isEmpty()) {
                    val prefix = filler.repeat((left / filler.length) + 1)
                    result.append(prefix.substring(0, left.coerceAtMost(prefix.length)))
                    result.appendLine(line.trimEnd())
                }
            }
            enumIndex++
        }
        
        return result.toString().trimEnd()
    }
}
