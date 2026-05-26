# Beancount JVM - bean-check 迁移实施计划

## 一、决策摘要

| 决策项 | 选择 | 理由 |
|--------|------|------|
| CLI 框架 | **Clikt** | KMP 兼容、API 类似 Python click、Kotlin 惯用 |
| 缓存 | **第一阶段跳过** | 非核心功能，后续迭代添加 |
| Parser | **手写递归下降** | 零依赖、完全可控、Beancount 语法简单 |
| Options | **强类型 `Options` 类** | 类型安全、IDE 友好、KMP 原生支持 |
| Decimal | **稍后讨论** | 需要权衡精度和性能方案 |

---

## 二、模块实现顺序

### Phase 1: 核心数据与解析（Week 1）

```
Day 1-2: core 模块 — 基础数据类型
  ├── Amount, Cost, Posting（Decimal 占位）
  └── Meta 类型别名

Day 2-3: core 模块 — Directive 数据类
  ├── Open, Close, Commodity
  ├── Transaction（最复杂）
  ├── Balance, Pad, Note
  ├── Event, Price, Document, Query, Custom
  └── 排序逻辑 (entrySortKey)

Day 3-4: core 模块 — Options 和验证
  ├── Options 强类型类
  ├── AccountTypesConfig
  ├── ValidationError 类型
  └── 基本验证函数接口

Day 4-5: parser 模块 — Lexer
  ├── Token 定义
  ├── 字符分类（数字、字母、符号）
  ├── 字符串/日期/数字字面量
  └── 错误位置和消息

Day 5-7: parser 模块 — 递归下降解析器
  ├── 顶层：file → directive*
  ├── Open/Close/Commodity 指令
  ├── Transaction 指令（最复杂）
  ├── Balance/Pad/Note/Event
  ├── Price/Document/Query/Custom
  └── Options 解析
```

### Phase 2: 加载与验证（Week 2 前半）

```
Day 8-9: parser 模块 — Booking
  ├── 交易平衡计算
  ├── 缺失 units 推断
  └── cost basis 处理

Day 9-10: core 模块 — Validation
  ├── validateOpenClose
  ├── validateActiveAccounts
  ├── validateCurrencyConstraints
  ├── validateDuplicateBalances
  ├── validateDuplicateCommodities
  ├── validateCheckTransactionBalances
  └── validateDataTypes (HARDCORE)

Day 10-11: loader 模块
  ├── loadFile() 主流程
  ├── 文件读取和编码处理
  ├── include 递归处理
  └── 错误收集和返回
```

### Phase 3: CLI 与测试（Week 2 后半）

```
Day 12: cli 模块
  ├── bean-check 命令定义
  ├── 参数解析 (--verbose, --no-cache 等)
  └── 退出码处理

Day 13-14: 测试
  ├── jvmTest: 单元测试
  ├── 集成测试：真实 ledger 文件
  └── 与 Python bean-check 输出对比
```

---

## 三、核心设计

### 3.1 模块依赖图

```
cli (JVM-only)
  ├── loader
  │   ├── parser
  │   │   └── core
  │   └── core
  ├── query
  │   └── core
  └── plugin-api
      └── core
```

**关键约束**: `core` 不依赖任何业务模块。

### 3.2 包命名规范

```
io.github.tonyzhye.beancount.core      ← core 模块
io.github.tonyzhye.beancount.parser    ← parser 模块
io.github.tonyzhye.beancount.loader    ← loader 模块
io.github.tonyzhye.beancount.query     ← query 模块
io.github.tonyzhye.beancount.plugin    ← plugin-api 模块
io.github.tonyzhye.beancount.cli       ← cli 模块
```

### 3.3 核心数据类设计

#### Amount（Decimal 待确定）

```kotlin
data class Amount(
    val number: Decimal,  // TODO: Decimal 类型待确定
    val currency: Currency
)
```

#### Posting

```kotlin
data class Posting(
    val account: Account,
    val units: Amount? = null,
    val cost: Cost? = null,
    val price: Amount? = null,
    val flag: Flag? = null,
    val meta: Meta? = null
)
```

#### Transaction（最复杂的 Directive）

```kotlin
data class Transaction(
    override val meta: Meta,
    override val date: LocalDate,
    val flag: Flag,
    val payee: String? = null,
    val narration: String? = null,
    val tags: Set<String> = emptySet(),
    val links: Set<String> = emptySet(),
    val postings: List<Posting> = emptyList()
) : Directive()
```

#### Options（强类型）

```kotlin
data class Options(
    val title: String = "",
    val accountTypes: AccountTypesConfig = AccountTypesConfig(),
    val operatingCurrencies: List<Currency> = emptyList(),
    val documents: List<String> = emptyList(),
    val include: List<String> = emptyList(),
    val plugin: List<PluginSpec> = emptyList(),
    val pluginProcessingMode: PluginProcessingMode = PluginProcessingMode.DEFAULT,
    val dcontext: DisplayContext = DisplayContext(),
    val filename: String = "",
    val line: Int = 0,
    val toleranceMap: Map<Currency, Decimal> = emptyMap(),
    val inferToleranceFromCost: Boolean = false,
    val allowDeprecatedNoneForTagsAndLinks: Boolean = false,
    val insertPythonpath: Boolean = false
)

data class AccountTypesConfig(
    val assets: String = "Assets",
    val liabilities: String = "Liabilities",
    val equity: String = "Equity",
    val income: String = "Income",
    val expenses: String = "Expenses"
)

data class PluginSpec(
    val moduleName: String,
    val config: String? = null
)

enum class PluginProcessingMode {
    RAW, DEFAULT
}
```

### 3.4 Parser 语法规则

```
file            := directive* EOF

directive       := date (
                    open | close | commodity | transaction |
                    balance | pad | note | event | price |
                    document | query | custom | option
                  )

open            := "open" account [currencies] [booking]
close           := "close" account
commodity       := "commodity" currency
transaction     := [flag] [payee] narration tags links posting+
balance         := "balance" account amount [tolerance]
pad             := "pad" account account
note            := "note" account string
event           := "event" string string
price           := "price" currency amount
document        := "document" account string
custom          := "custom" string value*
option          := "option" string string

posting         := account [amount] [cost] [price]
amount          := number currency
cost            := "{" [number currency] [date] [string] "}"
price           := "@" amount | "@@" amount

account         := NAME (":" NAME)+
currency        := NAME
number          := [+-]? DIGIT+ ("." DIGIT+)?
string          := '"' CHAR* '"'
date            := DIGIT DIGIT DIGIT DIGIT "-" DIGIT DIGIT "-" DIGIT DIGIT
flag            := "*" | "!" | LETTER
```

### 3.5 验证函数接口

```kotlin
typealias Validation = (List<Directive>, Options) -> List<ValidationError>

data class ValidationError(
    val source: Meta,
    val message: String,
    val entry: Directive? = null
)

// 基本验证（始终运行）
val BASIC_VALIDATIONS: List<Validation> = listOf(
    ::validateOpenClose,
    ::validateActiveAccounts,
    ::validateCurrencyConstraints,
    ::validateDuplicateBalances,
    ::validateDuplicateCommodities,
    ::validateCheckTransactionBalances
)

// 严格验证（bean-check 额外启用）
val HARDCORE_VALIDATIONS: List<Validation> = listOf(
    ::validateDataTypes
)
```

### 3.6 Loader 流程

```kotlin
fun loadFile(
    filename: String,
    extraValidations: List<Validation> = emptyList()
): LoadResult {
    // 1. 解析
    val (entries, parseErrors, options) = parser.parseFile(filename)
    
    // 2. 排序
    val sortedEntries = entries.sortedBy { it.sortKey }
    
    // 3. Booking
    val (bookedEntries, bookingErrors) = booking.book(sortedEntries, options)
    
    // 4. 验证
    val allValidations = BASIC_VALIDATIONS + extraValidations
    val validationErrors = allValidations.flatMap { 
        it(bookedEntries, options) 
    }
    
    // 5. 返回
    return LoadResult(
        entries = bookedEntries,
        errors = parseErrors + bookingErrors + validationErrors,
        options = options
    )
}
```

### 3.7 CLI 设计

```kotlin
class BeanCheckCommand : CliktCommand(
    name = "bean-check",
    help = "Parse, check and realize a beancount ledger."
) {
    private val filename by argument(
        name = "FILENAME",
        help = "Beancount input file"
    ).file(mustExist = true, canBeDir = false)
    
    private val verbose by option("-v", "--verbose")
        .flag(default = false)
        .help("Print timings")
    
    private val noCache by option("-C", "--no-cache")
        .flag(default = false)
        .help("Disable the cache")
    
    override fun run() {
        // 第一阶段：忽略 noCache（无缓存实现）
        val result = loadFile(
            filename = filename.path,
            extraValidations = HARDCORE_VALIDATIONS
        )
        
        if (verbose) {
            // TODO: 打印计时信息
        }
        
        if (result.errors.isNotEmpty()) {
            result.errors.forEach { echo(it, err = true) }
            throw ProgramResult(1)
        }
    }
}
```

---

## 四、依赖清单

### commonMain 第三方依赖

| 库名 | 版本 | 用途 | 模块 |
|------|------|------|------|
| kotlinx-datetime | 0.6.0 | 日期类型 | core |

### jvmMain 第三方依赖

| 库名 | 版本 | 用途 | 模块 |
|------|------|------|------|
| (暂无) | - | - | - |

### cli 第三方依赖（JVM-only）

| 库名 | 版本 | 用途 |
|------|------|------|
| clikt | 4.4.0 | CLI 框架 |

### 测试依赖

| 库名 | 版本 | 用途 | 位置 |
|------|------|------|------|
| kotlin-test | (随 Kotlin) | 通用测试 | commonTest |
| junit-jupiter | 5.10.0 | JVM 测试 | jvmTest |
| junit-jupiter-params | 5.10.0 | 参数化测试 | jvmTest |

---

## 五、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Decimal 精度差异 | 高 | 编写与 Python 对比的测试用例 |
| 解析器性能不足 | 中 | 先用简单实现，后续优化（预编译正则、流式解析） |
| 语法边界情况 | 中 | 参考 Python 测试用例，逐条覆盖 |
| 验证逻辑遗漏 | 中 | 对照 Python validation.py 逐项检查 |

---

## 六、验收标准

1. **功能正确性**
   - 能正确解析有效的 Beancount 文件
   - 能检测并报告语法错误
   - 能执行所有基本验证
   - 退出码：0（无错误）或 1（有错误）

2. **与 Python 版本对比**
   - 对同一文件，kotlin bean-check 与 python bean-check 输出相同的错误列表
   - 至少通过 10 个真实 ledger 文件的对比测试

3. **性能基准**
   - 解析 1MB ledger 文件 < 2 秒（JVM 冷启动）
   - 内存占用 < 200MB

---

## 七、参考资源

- Python 源码：
  - `beancount/scripts/check.py`
  - `beancount/loader.py`
  - `beancount/core/data.py`
  - `beancount/ops/validation.py`
  - `beancount/parser/booking.py`
- 已有研究文档：
  - `doc/20260523-research-beancount-bean-check.md`

---

## 八、Decimal 待讨论事项

### 8.1 可选方案

| 方案 | KMP 支持 | 优点 | 缺点 |
|------|----------|------|------|
| **expect/actual + BigDecimal** | ⚠️ JVM only | 精度完美、性能优秀 | 需要其他平台实际实现 |
| **String 存储 + 运算方法** | ✅ | 纯 KMP、简单 | 性能差、代码冗余 |
| **寻找 KMP Decimal 库** | ✅ | 开箱即用 | 可能增加依赖、维护风险 |
| **手写 KMP Decimal** | ✅ | 完全可控 | 工作量大、测试复杂 |

### 8.2 建议决策流程

1. 先用 `expect/actual` 占位，JVM 使用 `BigDecimal`
2. 编写单元测试对比 Python Decimal 行为
3. 根据测试结果决定是否需要寻找/开发 KMP Decimal 方案
4. 如果仅支持 JVM 足够，保持现状；如果需要 Native/JS，再评估替代方案

---

*文档生成时间: 2026-05-25*
*计划版本: Phase 1*
*状态: 实施中*
