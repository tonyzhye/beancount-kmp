# Beanquery 查询引擎迁移研究文档

## 1. 研究概述

### 1.1 研究范围

本文档记录了 Python beanquery 库（Beancount 查询语言引擎）的源码分析结果，为 Kotlin 迁移提供技术参考。

### 1.2 版本信息

- **源码来源**: `https://github.com/beancount/beanquery`
- **Python 版本**: Python 3.10+
- **关键依赖**: TatSu（解析器生成器）

### 1.3 文件清单

| 模块 | 文件 | 行数 | 功能 |
|------|------|------|------|
| Parser | `beanquery/parser/parser.py` | ~160 | TatSu 生成的 BQL 语法解析器 |
| Parser EBNF | `beanquery/parser/bql.ebnf` | ~115 | BQL 语法定义 |
| Compiler | `beanquery/query_compile.py` | ~200 | AST → EvalNode 编译器 |
| Executor | `beanquery/query_execute.py` | ~170 | 查询执行引擎 |
| Types | `beanquery/types.py` | ~30 | 类型系统定义 |
| Tables | `beanquery/tables.py` | ~15 | 表抽象基类 |
| Environment | `beanquery/query_env.py` | ~70 | 查询函数和环境 |
| Rendering | `beanquery/query_render.py` | ~30 | 结果渲染器 |
| Hashable | `beanquery/hashable.py` | ~15 | 自定义哈希支持 |
| Data Sources | `beanquery/sources/beancount.py` | ~595 | Beancount 数据源和表定义 |

---

## 2. 核心架构分析

### 2.1 三层架构

beanquery 采用经典的三层架构：

```
SQL Query → [Parser] → AST → [Compiler] → EvalNode Tree → [Executor] → Results
```

1. **Parser 层**: TatSu 生成的解析器，将 BQL 语法转为 AST 节点
2. **Compiler 层**: 将 AST 编译为 EvalNode 执行树，处理类型推断和函数绑定
3. **Executor 层**: 执行 EvalNode 树，遍历数据、过滤、聚合、排序

### 2.2 关键抽象

| 抽象 | Python 类 | 职责 |
|------|-----------|------|
| AST Node | `ast.Node` (TatSu) | 语法树节点 |
| EvalNode | `query_compile.EvalNode` | 执行树节点基类 |
| EvalColumn | `query_compile.EvalColumn` | 列访问节点 |
| EvalConstant | `query_compile.EvalConstant` | 常量节点 |
| EvalFunction | `query_compile.EvalFunction` | 函数调用节点 |
| EvalAggregator | `query_compile.EvalAggregator` | 聚合函数节点 |
| Table | `tables.Table` | 数据源表抽象 |
| QueryContext | `query_execute.QueryContext` | 查询执行上下文 |

---

## 3. Parser 层分析

### 3.1 语法特性

BQL（Beanquery Language）语法与 SQL 高度相似：

```sql
-- 基础查询
SELECT date, account, position FROM postings WHERE account ~ 'Assets';

-- 聚合查询
SELECT account, sum(position) FROM postings GROUP BY account;

-- 带 OPEN/CLOSE 的查询
SELECT * FROM postings OPEN ON 2023-01-01 CLOSE ON 2023-12-31;

-- DISTINCT
SELECT DISTINCT account FROM postings;

-- ORDER BY + LIMIT
SELECT * FROM postings ORDER BY date DESC LIMIT 10;

-- PIVOT（透视表）
SELECT date, sum(position) FROM postings PIVOT currency;

-- DEFINE（宏定义）
DEFINE my_accounts = "account ~ 'Assets'";
```

### 3.2 语法定义（EBNF）

```ebnf
query ::= select? from? where? group_by? order_by? limit? close?

select ::= 'SELECT' ('DISTINCT')? target (',' target)*
target ::= expression ('AS' name)?

from ::= 'FROM' table_name ('OPEN' 'ON'? date)? ('CLOSE' ('ON'? date | 'TRUE'))? ('CLEAR')?

where ::= 'WHERE' expression

group_by ::= 'GROUP' 'BY' expression (',' expression)*

order_by ::= 'ORDER' 'BY' order_target (',' order_target)*
order_target ::= expression ('ASC' | 'DESC')?

limit ::= 'LIMIT' integer

close ::= 'CLOSE'
```

### 3.3 关键差异

1. **关键词不区分大小写**: `ignorecase=True`
2. **支持 PIVOT 子句**: 生成透视表
3. **支持 OPEN/CLOSE/CLEAR**: 时间窗口和结算操作
4. **支持 DEFINE**: 宏定义
5. **支持 JOURNAL/PRINT**: 非查询命令

---

## 4. Compiler 层分析

### 4.1 EvalNode 类体系

```python
class EvalNode:
    """执行树节点基类"""
    def __init__(self, dtype):
        self.dtype = dtype  # 数据类型
    
    def __call__(self, context):
        """在给定上下文中求值"""
        raise NotImplementedError
```

### 4.2 节点类型

| 节点类型 | 职责 | 示例 |
|----------|------|------|
| `EvalColumn` | 访问数据列 | `account`, `date` |
| `EvalConstant` | 常量值 | `42`, `'hello'` |
| `EvalReference` | 列引用（带表名） | `postings.account` |
| `EvalFuncall` | 函数调用 | `sum(position)` |
| `EvalBinaryOp` | 二元运算 | `+`, `-`, `*`, `/` |
| `EvalEqual` | 等值比较（含 NULL） | `=`, `!=` |
| `EvalMatch` | 正则匹配 | `~` |
| `EvalAnd` / `EvalOr` | 逻辑运算 | `AND`, `OR` |
| `EvalCoalesce` | NULL 合并 | `COALESCE(a, b)` |

### 4.3 类型系统

```python
class AnyType:
    """通配符类型"""
    pass

class Structure:
    """嵌套结构类型（如 Amount, Position）"""
    name = None
    columns = {}

# 类型别名
ALIASES = {
    position.Position: Position,
    data.Cost: Cost,
    data.Amount: Amount,
    data.Transaction: Transaction,
}
```

### 4.4 函数注册机制

**全局函数注册**:
```python
FUNCTIONS = {}

def function(intypes, outtype, pass_context=False):
    """函数注册装饰器"""
    def decorator(func):
        func_name = func.__name__.lower()
        FUNCTIONS.setdefault(func_name, []).append(
            (intypes, outtype, pass_context, func)
        )
        return func
    return decorator

# 示例
@function([AnyType], bool)
def bool_(x):
    return bool(x)
```

**表列注册**:
```python
class ColumnsRegistry(dict):
    """列注册表"""
    def register(self, dtype):
        def decorator(func):
            self[func.__name__] = EvalFunction([dtype], dtype, func)
            return func
        return decorator

# 示例
class PostingsTable:
    columns = ColumnsRegistry()
    
    @columns.register(str)
    def account(context):
        return context.posting.account
```

### 4.5 聚合函数

```python
class EvalAggregator(EvalFunction):
    """聚合函数基类"""
    def store(self):
        """返回初始 accumulator"""
        raise NotImplementedError
    
    def store_type(self):
        """accumulator 类型"""
        raise NotImplementedError
    
    def update(self, store, value):
        """更新 accumulator"""
        raise NotImplementedError
    
    def finalize(self, store):
        """计算最终结果"""
        raise NotImplementedError
```

**内置聚合函数**:
- `sum`: 求和（支持 Amount, Inventory, Position）
- `count`: 计数
- `first` / `last`: 首尾值
- `max` / `min`: 最大/最小值

---

## 5. Executor 层分析

### 5.1 执行流程

```python
def execute_query(query, entries, options, errors):
    """执行查询"""
    # 1. 创建查询上下文
    context = QueryContext(entries, options, errors)
    
    # 2. 解析 FROM 子句，获取目标表
    table = query.from_clause.table
    
    # 3. 应用 OPEN/CLOSE/CLEAR
    filtered_entries = apply_time_window(entries, query.from_clause)
    
    # 4. 构建 WHERE 过滤器
    where_evaluator = compile_expression(query.where_clause)
    
    # 5. 迭代表行，应用过滤
    rows = []
    for row in table:
        if where_evaluator(row):
            rows.append(row)
    
    # 6. 计算 targets（列值）
    targets = [compile_expression(t) for t in query.targets]
    
    # 7. GROUP BY 分组
    if query.group_by:
        groups = group_rows(rows, query.group_by)
        
        # 8. 聚合计算
        for group in groups:
            for target in targets:
                if isinstance(target, EvalAggregator):
                    accumulator = target.store()
                    for row in group:
                        target.update(accumulator, target(row))
                    result = target.finalize(accumulator)
    
    # 9. HAVING 过滤
    if query.having:
        rows = filter_rows(rows, query.having)
    
    # 10. ORDER BY 排序
    if query.order_by:
        rows = sort_rows(rows, query.order_by)
    
    # 11. LIMIT 限制
    if query.limit:
        rows = rows[:query.limit]
    
    return rows
```

### 5.2 排序规则

```python
def sortkey(row, order_by):
    """排序键生成"""
    keys = []
    for target, reverse in order_by:
        value = target(row)
        # 空值放最后
        if value is None:
            value = SortKey(None)
        keys.append((reverse, value))
    return keys
```

**排序特性**:
1. 空值（None）始终排在最后
2. 支持 Boolean 类型排序
3. 支持列表/元组比较
4. 支持 Inventory 和 Amount 类型比较

### 5.3 DISTINCT 实现

```python
def distinct_rows(rows):
    """去重"""
    seen = set()
    result = []
    for row in rows:
        key = tuple(hashable_value(v) for v in row)
        if key not in seen:
            seen.add(key)
            result.append(row)
    return result
```

---

## 6. 数据源层分析

### 6.1 表注册机制

```python
_TABLES = []

class Table:
    def __init_subclass__(cls):
        _TABLES.append(cls)

def attach(context, dsn, *, entries=None, errors=None, options=None):
    """加载数据源并注册所有表"""
    if filename:
        entries, errors, options = loader.load_file(filename)
    for table in _TABLES:
        context.tables[table.name] = table(entries, options)
    context.options.update(options)
    context.errors.extend(errors)
```

### 6.2 核心表定义

| 表名 | 数据类型 | 行数 | 说明 |
|------|----------|------|------|
| `postings` | Posting | 最多 | 核心表，每个 posting 一行 |
| `entries` | Directive | 所有 | 所有指令 |
| `transactions` | Transaction | 较少 | 交易指令 |
| `accounts` | Account | 账户数 | 账户 open/close 信息 |
| `commodities` | Commodity | 商品数 | 商品定义 |
| `prices` | Price | 价格数 | 价格历史 |
| `balances` | Balance | balance 数 | balance 指令 |

### 6.3 PostingsTable（核心表）

```python
class PostingsTable(_BeancountTable):
    name = 'postings'
    wildcard_columns = ['date', 'flag', 'payee', 'narration', 'position']
    
    def __iter__(self):
        entries = self.prepare()  # 应用 OPEN/CLOSE/CLEAR
        context = _PostingsTableRow()
        for entry in entries:
            if isinstance(entry, Transaction):
                context.entry = entry
                for posting in entry.postings:
                    context.rowid += 1
                    context.posting = posting
                    yield context
```

**行上下文对象**:
```python
class _PostingsTableRow:
    def __init__(self):
        self.rowid = 0
        self.balance = Inventory()
        self.entry = None
        self.posting = None
    
    def __hash__(self):
        return self.rowid  # 用于缓存
```

### 6.4 列定义（PostingsTable）

| 列名 | 类型 | 说明 |
|------|------|------|
| `date` | date | 交易日期 |
| `year` | int | 年份 |
| `month` | int | 月份 |
| `day` | int | 日期 |
| `flag` | str | 交易 flag |
| `payee` | str | 收款人 |
| `narration` | str | 描述 |
| `description` | str | payee \| narration |
| `tags` | set | 标签集合 |
| `links` | set | 链接集合 |
| `account` | str | 账户 |
| `other_accounts` | set | 交易中的其他账户 |
| `number` | Decimal | 数量 |
| `currency` | str | 货币 |
| `cost_number` | Decimal | 成本数量 |
| `cost_currency` | str | 成本货币 |
| `cost_date` | date | 成本日期 |
| `cost_label` | str | 成本标签 |
| `position` | Position | 头寸（可聚合） |
| `price` | Amount | 价格 |
| `weight` | Amount | 权重 |
| `balance` | Inventory | 累计余额（带缓存） |
| `meta` | dict | 元数据 |
| `entry` | Transaction | 父交易 |
| `accounts` | set | 交易中的所有账户 |

**balance 列的特殊处理**:
```python
@columns.register(inventory.Inventory)
@cache(maxsize=1)
def balance(context):
    """累计余额，使用缓存防止同一行重复更新"""
    context.balance.add_position(context.posting)
    return copy.copy(context.balance)
```

### 6.5 EntriesTable（通用表）

```python
class EntriesTable(_BeancountTable):
    name = 'entries'
    
    @columns.register(str)
    def id(entry):
        return hash_entry(entry)
    
    @columns.register(str)
    def type(entry):
        return type(entry).__name__.lower()
    
    @columns.register(datetime.date)
    def date(entry):
        return entry.date
    
    # ... 其他通用列
```

---

## 7. 函数环境分析

### 7.1 全局函数

| 函数 | 输入类型 | 输出类型 | 说明 |
|------|----------|----------|------|
| `bool` | Any | bool | 布尔转换 |
| `str` | Any | str | 字符串转换 |
| `lower` | str | str | 小写 |
| `upper` | str | str | 大写 |
| `length` | str | int | 长度 |
| `year` | date | int | 年份 |
| `month` | date | int | 月份 |
| `day` | date | int | 日期 |
| `date` | Any | date | 日期转换 |
| `abs` | Decimal | Decimal | 绝对值 |
| `round` | Decimal | Decimal | 四舍五入 |
| `coalesce` | Any... | Any | NULL 合并 |

### 7.2 聚合函数

| 函数 | 输入类型 | 输出类型 | 说明 |
|------|----------|----------|------|
| `sum` | Amount/Inventory/Position/Decimal | 同输入 | 求和 |
| `count` | Any | int | 计数 |
| `first` | Any | 同输入 | 首个非 NULL |
| `last` | Any | 同输入 | 最后非 NULL |
| `max` | Comparable | 同输入 | 最大值 |
| `min` | Comparable | 同输入 | 最小值 |

---

## 8. Kotlin 迁移方案

### 8.1 架构映射

| Python 组件 | Kotlin 对应 | 说明 |
|-------------|-------------|------|
| TatSu Parser | 手写 Parser / ANTLR | TatSu 是 Python 特有，需重写 |
| AST Node | Sealed Class | 语法树节点 |
| EvalNode | Sealed Class + invoke() | 执行树节点 |
| EvalFunction | Function Interface | 函数调用抽象 |
| EvalAggregator | Aggregator Interface | 聚合函数抽象 |
| Table | Interface + 实现 | 数据源表 |
| QueryContext | Data Class | 查询上下文 |
| ColumnsRegistry | Map<String, Column> | 列注册表 |
| FUNCTIONS | Map<String, List<Function>> | 函数注册表 |

### 8.2 关键设计决策

#### 8.2.1 Parser 选择

**方案 A: 手写递归下降解析器**
- 优点：无外部依赖，完全可控
- 缺点：开发工作量大，维护成本高
- 适用：语法相对简单（BQL 语法不大复杂）

**方案 B: ANTLR4**
- 优点：成熟稳定，生态丰富
- 缺点：增加构建复杂度，KMP 支持需验证
- 适用：需要复杂语法特性时

**推荐**: 方案 A（手写解析器）
- BQL 语法相对简单，手写工作量可控
- 避免 ANTLR 的 KMP 兼容性问题
- 更易于实现 Python 兼容的语义

#### 8.2.2 类型系统

```kotlin
// 类型标记
sealed interface BqlType
object BqlString : BqlType
object BqlDecimal : BqlType
object BqlDate : BqlType
object BqlBoolean : BqlType
object BqlSet : BqlType
object BqlInventory : BqlType
object BqlPosition : BqlType
object BqlAmount : BqlType
object BqlAny : BqlType

// 值封装
sealed interface BqlValue
data class BqlStringValue(val value: String) : BqlValue
data class BqlDecimalValue(val value: Decimal) : BqlValue
// ...
```

#### 8.2.3 函数注册

```kotlin
// 函数签名
data class FunctionSignature(
    val parameterTypes: List<BqlType>,
    val returnType: BqlType,
    val passContext: Boolean = false
)

// 函数注册表
object FunctionRegistry {
    private val functions = mutableMapOf<String, MutableList<Pair<FunctionSignature, BqlFunction>>>()
    
    fun register(name: String, signature: FunctionSignature, function: BqlFunction) {
        functions.getOrPut(name.lowercase()) { mutableListOf() }
            .add(signature to function)
    }
    
    fun resolve(name: String, argumentTypes: List<BqlType>): BqlFunction? {
        // 基于参数类型匹配重载
    }
}
```

#### 8.2.4 表定义

```kotlin
// 表接口
interface Table {
    val name: String
    val columns: Map<String, Column>
    val wildcardColumns: List<String>
    
    fun iterator(): Iterator<RowContext>
}

// 列定义
interface Column {
    val name: String
    val type: BqlType
    fun evaluate(context: RowContext): BqlValue
}

// PostingsTable
class PostingsTable(
    private val entries: List<Directive>,
    private val options: Options
) : Table {
    override val name = "postings"
    override val wildcardColumns = listOf("date", "flag", "payee", "narration", "position")
    
    override val columns = buildMap {
        put("date", DateColumn())
        put("account", AccountColumn())
        put("position", PositionColumn())
        put("balance", BalanceColumn())
        // ...
    }
    
    override fun iterator(): Iterator<RowContext> {
        return entries.asSequence()
            .filterIsInstance<Transaction>()
            .flatMap { entry ->
                entry.postings.map { posting ->
                    PostingsRowContext(entry, posting)
                }
            }
            .iterator()
    }
}
```

#### 8.2.5 执行引擎

```kotlin
class QueryExecutor(
    private val entries: List<Directive>,
    private val options: Options
) {
    fun execute(query: BqlQuery): QueryResult {
        // 1. 获取表
        val table = resolveTable(query.fromClause)
        
        // 2. 应用时间窗口
        val filteredEntries = applyTimeWindow(entries, query.fromClause)
        
        // 3. 过滤
        val whereEvaluator = compileExpression(query.whereClause)
        val filteredRows = table.iterator().asSequence()
            .filter { row -> whereEvaluator.evaluate(row).asBoolean() }
        
        // 4. GROUP BY
        val groupedRows = if (query.groupBy != null) {
            groupRows(filteredRows, query.groupBy)
        } else {
            listOf(filteredRows.toList())
        }
        
        // 5. 计算 targets
        val targets = query.targets.map { compileExpression(it) }
        
        // 6. 聚合
        val resultRows = groupedRows.map { group ->
            targets.map { target ->
                when (target) {
                    is Aggregator -> target.aggregate(group)
                    else -> target.evaluate(group.first())
                }
            }
        }
        
        // 7. HAVING
        val havingEvaluator = query.having?.let { compileExpression(it) }
        val havingRows = if (havingEvaluator != null) {
            resultRows.filter { row -> havingEvaluator.evaluate(row).asBoolean() }
        } else resultRows
        
        // 8. ORDER BY
        val sortedRows = if (query.orderBy != null) {
            sortRows(havingRows, query.orderBy)
        } else havingRows
        
        // 9. LIMIT
        val limitedRows = query.limit?.let { sortedRows.take(it) } ?: sortedRows
        
        // 10. DISTINCT
        val distinctRows = if (query.distinct) {
            limitedRows.distinct()
        } else limitedRows
        
        return QueryResult(targets.map { it.name }, distinctRows)
    }
}
```

### 8.3 实现优先级

| 优先级 | 功能 | 工作量 | 依赖 |
|--------|------|--------|------|
| P0 | Parser（基础 SELECT/FROM/WHERE） | 大 | 无 |
| P0 | Compiler（类型推断 + 函数绑定） | 大 | Parser |
| P0 | Executor（基础执行流程） | 中 | Compiler |
| P0 | PostingsTable（核心表） | 中 | Executor |
| P1 | GROUP BY + 聚合函数 | 中 | Executor |
| P1 | ORDER BY + LIMIT | 小 | Executor |
| P1 | 全局函数（bool, str, year 等） | 小 | Compiler |
| P2 | OPEN/CLOSE/CLEAR | 小 | Executor |
| P2 | DISTINCT | 小 | Executor |
| P2 | EntriesTable + 其他表 | 小 | Executor |
| P3 | PIVOT | 中 | Executor |
| P3 | JOURNAL/PRINT | 中 | Executor |
| P3 | 完整函数库 | 中 | 全局函数 |

### 8.4 测试策略

1. **Parser 测试**: 为每个语法规则编写单元测试
2. **Compiler 测试**: 测试类型推断和函数绑定
3. **Executor 测试**: 使用真实账本数据测试查询结果
4. **Python 兼容性测试**: 对比 Python beanquery 输出
5. **性能测试**: 大账本查询性能对比

---

## 9. 风险与挑战

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| TatSu Parser 替换 | 高 | 手写解析器，逐语法规则验证 |
| Python 类型动态性 | 中 | Kotlin 需要明确类型系统，可能需要 AnyType 通配符 |
| 聚合函数复杂度 | 中 | Inventory/Amount 的 sum 需要特殊处理 |
| 性能要求 | 中 | 使用 Sequence 惰性求值，避免中间集合 |
| KMP 兼容性 | 低 | 查询引擎纯 Kotlin，无需平台特定代码 |

---

## 10. 参考资源

- [Python beanquery 源码](https://github.com/beancount/beanquery)
- [TatSu 文档](https://tatsu.readthedocs.io/)
- [Beancount 查询文档](https://beancount.github.io/docs/beancount_query_language.html)
- [Kotlin Sealed Classes](https://kotlinlang.org/docs/sealed-classes.html)
- [Kotlin Sequence](https://kotlinlang.org/docs/sequences.html)

---

## 附录 A：BQL 完整语法（EBNF）

```ebnf
query          ::= select? from? where? group_by? order_by? limit? close?

select         ::= "SELECT" ("DISTINCT")? target ("," target)*
target         ::= expression ("AS" identifier)?

from           ::= "FROM" identifier ("OPEN" ("ON")? date)? 
                   ("CLOSE" (("ON")? date | "TRUE"))? ("CLEAR")?

where          ::= "WHERE" expression

group_by       ::= "GROUP" "BY" expression ("," expression)*

order_by       ::= "ORDER" "BY" order_target ("," order_target)*
order_target   ::= expression ("ASC" | "DESC")?

limit          ::= "LIMIT" integer

expression     ::= or_expr
or_expr        ::= and_expr ("OR" and_expr)*
and_expr       ::= not_expr ("AND" not_expr)*
not_expr       ::= ("NOT")? comparison_expr
comparison_expr ::= add_expr (("=" | "!=" | "<" | ">" | "<=" | ">=" | "~") add_expr)?
add_expr       ::= mul_expr (("+" | "-") mul_expr)*
mul_expr       ::= unary_expr (("*" | "/") unary_expr)*
unary_expr     ::= ("+" | "-")? primary_expr
primary_expr   ::= literal | identifier | function_call | "(" expression ")"

function_call  ::= identifier "(" (expression ("," expression)*)? ")"

literal        ::= string | integer | decimal | date | "TRUE" | "FALSE" | "NULL"

identifier     ::= [a-zA-Z_][a-zA-Z0-9_]*
string         ::= "'" ([^'\\] | "\\" .)* "'"
integer        ::= [0-9]+
decimal        ::= [0-9]+ "." [0-9]* | "." [0-9]+
date           ::= [0-9]{4} "-" [0-9]{2} "-" [0-9]{2}
```

---

## 附录 B：Python 关键代码片段

### B.1 EvalNode 基类

```python
class EvalNode:
    def __init__(self, dtype):
        self.dtype = dtype
    
    def __call__(self, context):
        raise NotImplementedError
```

### B.2 聚合函数实现（sum）

```python
class Sum(EvalAggregator):
    def __init__(self, operands):
        super().__init__(operands)
        self.dtype = operands[0].dtype
    
    def store(self):
        return self.dtype() if hasattr(self.dtype, '__call__') else None
    
    def store_type(self):
        return self.dtype
    
    def update(self, store, value):
        if store is None:
            return value
        return store + value
    
    def finalize(self, store):
        return store
```

### B.3 查询执行主循环

```python
def execute_query(query, entries, options, errors):
    context = QueryContext(entries, options, errors)
    
    # FROM
    table = query.from_clause.table
    if isinstance(table, str):
        table = context.tables[table]
    
    # OPEN/CLOSE/CLEAR
    if hasattr(table, 'prepare'):
        table = table.prepare()
    
    # WHERE
    where_clause = query.where_clause
    if where_clause is not None:
        where_evaluator = compile_expression(where_clause)
    else:
        where_evaluator = lambda row: True
    
    # 迭代和过滤
    rows = []
    for row in table:
        if where_evaluator(row):
            rows.append(row)
    
    # GROUP BY
    if query.group_by:
        groups = {}
        for row in rows:
            key = tuple(evaluate_expression(expr, row) for expr in query.group_by)
            groups.setdefault(key, []).append(row)
        rows = list(groups.values())
    else:
        rows = [rows]
    
    # 计算 targets
    result = []
    for group in rows:
        row_result = []
        for target in query.targets:
            if isinstance(target, EvalAggregator):
                accumulator = target.store()
                for row in group:
                    value = target.operands[0](row)
                    accumulator = target.update(accumulator, value)
                row_result.append(target.finalize(accumulator))
            else:
                row_result.append(target(group[0]))
        result.append(row_result)
    
    # DISTINCT
    if query.distinct:
        result = distinct_rows(result)
    
    # ORDER BY
    if query.order_by:
        result = sort_rows(result, query.order_by)
    
    # LIMIT
    if query.limit:
        result = result[:query.limit]
    
    return result
```

---

*文档版本: 1.0*
*研究日期: 2026-05-28*
*基于 beanquery commit: 最新 master*
