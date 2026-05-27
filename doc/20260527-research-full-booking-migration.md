# Beancount 完整 Booking 系统迁移研究报告

**研究日期**: 2026-05-27  
**基于**: beancount master (3.2.3)  
**目标**: 评估从 Python 完整 Booking 系统迁移到 Kotlin/JVM 的步骤和工作量

---

## 1. Python Booking 系统架构

### 1.1 整体流程

```
booking.py (入口)
    ├── 收集每个 account 的 booking method
    ├── 调用 booking_full.book() (核心算法)
    └── 验证所有 MISSING 元素已消除

booking_full.py (核心)
    ├── 维护 balances: Map<Account, Inventory>
    ├── 遍历所有 entries
    │   └── 如果是 Transaction:
    │       ├── categorize_by_currency() - 按 currency 分组
    │       ├── infer_tolerances() - 推断容差
    │       ├── book_reductions() - 匹配减少 posting 到现有 lot
    │       ├── interpolate_group() - 补全缺失数字
    │       └── 更新 balances
    └── 返回 (entries, errors, balances)

booking_method.py (5种方法)
    ├── STRICT - 严格匹配，不允许模糊
    ├── STRICT_WITH_SIZE - 严格+按大小自动区分
    ├── NONE - 不 booking，允许负持仓
    ├── FIFO - 先进先出 (按 date 排序)
    ├── LIFO - 后进先出 (按 date 逆序)
    ├── HIFO - 高成本先出 (按 cost 逆序)
    └── AVERAGE - 平均成本 (特殊处理)

interpolate.py (插值)
    ├── compute_residual() - 计算残差
    ├── infer_tolerances() - 推断精度容差
    └── interpolate_group() - 补全缺失 posting
```

### 1.2 核心算法详解

#### 第一阶段: Booking Reductions

对于每个 transaction 中的每个 posting:

1. **判断是否是减少**: 检查该 account 的当前 Inventory 是否会被此 posting 减少
2. **匹配 lots**: 在 Inventory 中查找匹配此 posting CostSpec 的现有 lots
3. **应用 booking method**: 
   - STRICT: 要求唯一匹配，否则报错
   - FIFO: 按日期排序，从最早的 lot 开始减少
   - HIFO: 按成本排序，从最高成本的 lot 开始减少
4. **替换 posting**: 将 reducing posting 的 CostSpec 替换为匹配到的具体 Cost
5. **可能拆分**: 一个 reducing posting 可能匹配多个 lots，产生多个 postings

#### 第二阶段: Interpolation

对于每个 currency group:

1. **计算残差**: 所有 postings 的 weight（含 cost 转换后的值）之和应为零
2. **补全缺失**: 如果只有一个 posting 缺少 units，从残差推断
3. **容差检查**: 允许极小的残差（由精度推断）

#### 第三阶段: CostSpec → Cost 转换

将 augmenting postings 上剩余的 CostSpec 转换为 Cost:
- 如果有 `number_total`，计算 `number_per = number_total / abs(units)`
- 如果没有 `number_per`，报错
- 继承 transaction date 作为默认 cost date

---

## 2. Python 核心代码量统计

| 文件 | 行数 | 说明 |
|------|------|------|
| `booking.py` | ~170 | 入口 + validate + CostSpec转换 |
| `booking_full.py` | ~520 | 核心 booking 算法 |
| `booking_method.py` | ~320 | 5种 booking 方法实现 |
| `interpolate.py` | ~380 | 插值补全 + 容差推断 |
| **总计** | **~1,390** | **Python 实现** |

---

## 3. 当前 Kotlin 实现 vs Python 实现

### 3.1 当前 Kotlin 实现（简化版）

**文件**: `modules/parser/src/commonMain/kotlin/.../Booking.kt`
**代码量**: ~107 行
**功能**: 
- 仅支持"补全单个缺失 posting"
- 按 currency 简单汇总
- 没有 Inventory 余额跟踪
- 没有 CostSpec 处理
- 不支持投资场景（多 lot）

### 3.2 缺失的关键组件

| 组件 | Python | Kotlin | 差距 |
|------|--------|--------|------|
| **Inventory 余额跟踪** | ✅ 每个 account | ❌ 无 | 大 |
| **CostSpec → Cost 转换** | ✅ 完整转换逻辑 | ❌ 无 | 中 |
| **按 currency 分组** | ✅ categorize_by_currency | ❌ 无 | 中 |
| **容差推断** | ✅ infer_tolerances | ❌ 无 | 中 |
| **STRICT 方法** | ✅ 完整实现 | ❌ 无 | 大 |
| **FIFO/LIFO/HIFO** | ✅ _booking_method_xifo | ❌ 无 | 大 |
| **AVERAGE 方法** | ✅ 特殊处理 | ❌ 无 | 大 |
| **插值补全** | ✅ interpolate_group | ❌ 无 | 大 |
| **自减少检测** | ✅ has_self_reduction | ❌ 无 | 小 |

---

## 4. 迁移步骤评估

### 步骤 1: CostSpec 到 Cost 的解析转换（工作量: 中，~2-3天）

**目标**: 在解析阶段正确处理 `{...}` 语法

当前问题:
- `BeancountParser.parseCost()` 已返回 `Cost`（要求完整字段）
- 实际上解析时应该返回 `CostSpec`（允许部分缺失）
- 例如: `{100.00 USD}` 完整，`{USD}` 只有 currency，`{# 1000 USD}` 有 total

需要修改:
```kotlin
// 解析阶段返回 CostSpec
data class Posting(
    val account: Account,
    val units: Amount? = null,
    val cost: CostSpec? = null,  // 解析时用 CostSpec
    val price: Amount? = null,
    ...
)

// Booking 后转换为 Cost
fun convertSpecToCost(units: Amount, spec: CostSpec): Cost? { ... }
```

### 步骤 2: Inventory 余额跟踪（工作量: 中，~2-3天）

**目标**: 在 booking 过程中维护每个 account 的 Inventory

已具备基础:
- ✅ `Inventory` 类已实现（刚添加）
- ✅ `Position`, `Cost`, `CostSpec` 已实现
- ✅ `addAmount()`, `addPosition()` 已实现

需要添加:
- `isReducedBy(units: Amount): Boolean` - 判断是否减少
- `getMatchingPositions(costSpec: CostSpec): List<Position>` - 查找匹配 lots
- 维护 `balances: Map<Account, Inventory>`

### 步骤 3: Booking Methods 实现（工作量: 大，~5-7天）

**目标**: 实现 5 种 booking 方法

核心逻辑:
```kotlin
interface BookingMethod {
    fun book(
        entry: Transaction,
        posting: Posting,
        matches: List<Position>
    ): Triple<List<Posting>, List<Position>, List<Error>>
}

// STRICT: 要求唯一匹配
object StrictBooking : BookingMethod { ... }

// FIFO/LIFO/HIFO: 通用 _xifo 实现
fun bookingMethodXifo(
    entry: Transaction,
    posting: Posting,
    matches: List<Position>,
    sortAttr: String,      // "date" or "number"
    reverseOrder: Boolean  // true for LIFO/HIFO
): Triple<List<Posting>, List<Position>, List<Error>> { ... }

// AVERAGE: 特殊处理（合并所有 lots）
object AverageBooking : BookingMethod { ... }

// NONE: 允许负持仓
object NoneBooking : BookingMethod { ... }
```

### 步骤 4: Interpolation 实现（工作量: 大，~4-5天）

**目标**: 补全 transaction 中缺失的数字

需要实现:
- `computeResidual(postings): Inventory` - 计算残差
- `inferTolerances(postings, options): Map<Currency, Decimal>` - 推断精度
- `interpolateGroup(postings, balances, currency, tolerances): List<Posting>` - 插值
- `getWeight(posting): Amount` - 计算 posting 的 weight（含 cost 转换）

关键难点:
- Weight 计算需要处理 cost basis:
  ```
  10 AAPL {100 USD} → weight = 1000 USD
  -5 AAPL {100 USD} → weight = -500 USD
  ```
- 容差推断需要从所有 amounts 的精度推断

### 步骤 5: Full Booking 主流程（工作量: 大，~3-4天）

**目标**: 整合所有组件为完整流程

```kotlin
fun book(
    entries: List<Directive>,
    options: Options,
    methods: Map<Account, BookingMethod>
): Pair<List<Directive>, List<Error>> {
    val balances = mutableMapOf<Account, Inventory>()
    val errors = mutableListOf<Error>()
    val result = mutableListOf<Directive>()
    
    for (entry in entries) {
        when (entry) {
            is Transaction -> {
                // 1. 按 currency 分组
                val groups = categorizeByCurrency(entry)
                
                // 2. 推断容差
                val tolerances = inferTolerances(entry.postings, options)
                
                // 3. Booking reductions
                val (bookedPostings, bookingErrors) = bookReductions(
                    entry, groups, balances, methods
                )
                errors.addAll(bookingErrors)
                
                // 4. Interpolation
                val (interPostings, interpErrors) = interpolateGroup(
                    bookedPostings, balances, tolerances
                )
                errors.addAll(interpErrors)
                
                // 5. 更新 balances
                updateBalances(entry, interPostings, balances)
                
                result.add(entry.copy(postings = interPostings))
            }
            else -> result.add(entry)
        }
    }
    
    return result to errors
}
```

### 步骤 6: Loader 集成（工作量: 小，~1天）

修改 `Loader.kt`:
```kotlin
fun loadFile(...) {
    // ...
    // 3. Full Booking
    val methods = extractBookingMethods(entries)
    val (bookedEntries, bookingErrors) = FullBooking.book(entries, options, methods)
    // ...
}
```

---

## 5. 工作量总评估

| 步骤 | 工作量 | 预估时间 | 依赖 |
|------|--------|----------|------|
| 1. CostSpec 解析转换 | 中 | 2-3 天 | 无 |
| 2. Inventory 余额跟踪 | 中 | 2-3 天 | 步骤1 |
| 3. Booking Methods | 大 | 5-7 天 | 步骤2 |
| 4. Interpolation | 大 | 4-5 天 | 步骤2 |
| 5. Full Booking 流程 | 大 | 3-4 天 | 步骤3,4 |
| 6. Loader 集成 | 小 | 1 天 | 步骤5 |
| **总计** | **~17-23 天** | | |

**代码量预估**: ~800-1,000 行 Kotlin + ~300 行测试

---

## 6. 风险和建议

### 6.1 主要风险

1. **复杂度**: Python 实现有 1,390 行，涉及大量边界情况和特殊处理
2. **精度问题**: Decimal 运算和容差处理容易出错
3. **测试覆盖**: 需要大量测试用例覆盖 5 种方法和各种边界情况
4. **与现有代码兼容性**: 当前简化 Booking 被多处使用，需要平滑过渡

### 6.2 实施建议

**方案 A: 完整迁移（推荐）**
- 按上述 6 个步骤逐步实现
- 每个步骤完成后运行完整测试
- 最终替换现有简化 Booking
- **时间**: 3-4 周

**方案 B: 渐进式增强**
- 第 1 阶段: 仅实现 STRICT + NONE（覆盖 80% 场景）
- 第 2 阶段: 添加 FIFO/LIFO（投资场景）
- 第 3 阶段: 添加 HIFO/AVERAGE（高级场景）
- **时间**: 每阶段 1-2 周

**方案 C: 最小可行实现**
- 仅实现 STRICT 方法
- 支持 CostSpec → Cost 转换
- 处理简单的多 lot 场景
- **时间**: 1-2 周

### 6.3 测试策略

参考 Python 测试:
- `booking_test.py` (~300 行)
- `booking_full_test.py` (~600 行)
- `booking_method_test.py` (~400 行)
- `interpolate_test.py` (~350 行)

**总计 ~1,650 行 Python 测试**

建议至少实现核心测试:
- 每种 booking 方法的基本场景
- 多 lot 匹配和拆分
- 容差边界
- 错误处理

---

## 7. 结论

完整 Booking 系统是一个**大型工程**（~1,000 行代码，3-4 周），但这是处理投资场景和复杂 ledger 的**必要基础**。

**建议采用方案 B（渐进式）**:
1. **第 1 阶段**（1-2 周）: STRICT + NONE + 基本 interpolation
   - 可处理大多数个人账本
   - example.beancount 应能正常解析
   
2. **第 2 阶段**（1-2 周）: FIFO/LIFO
   - 支持投资组合跟踪
   
3. **第 3 阶段**（1 周）: HIFO/AVERAGE
   - 高级投资场景

每阶段都可独立发布，逐步增强功能。

---

*研究文件*:
- `beancount/parser/booking.py` (~170 行)
- `beancount/parser/booking_full.py` (~520 行)
- `beancount/parser/booking_method.py` (~320 行)
- `beancount/core/interpolate.py` (~380 行)

*当前 Kotlin 实现*:
- `modules/parser/src/commonMain/kotlin/.../Booking.kt` (~107 行)
