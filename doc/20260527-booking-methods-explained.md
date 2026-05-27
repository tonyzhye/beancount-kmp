# Beancount Booking 方法详解

**文档日期**: 2026-05-27  
**基于**: beancount master (3.2.3)  
**来源文件**: `beancount/parser/booking_method.py`, `beancount/parser/booking_full.py`

---

## 概述

Booking（持仓匹配）是 Beancount 处理投资交易的核心机制。当一笔交易减少某个账户的持仓时（例如卖出股票），系统需要决定**从哪个 lot（批次）中扣除**。

### 为什么需要 Booking？

假设你分两次买入同一只股票：
```beancount
2023-01-01 * "Buy AAPL"
  Assets:Invest      10 AAPL {100.00 USD}  ; Lot 1: 10股 @ $100
  Assets:Cash       -1000.00 USD

2023-06-01 * "Buy more AAPL"
  Assets:Invest       5 AAPL {120.00 USD}  ; Lot 2: 5股 @ $120
  Assets:Cash        -600.00 USD

2023-12-01 * "Sell AAPL"
  Assets:Invest      -8 AAPL {}             ; 卖出8股，但应该按哪个成本？
  Assets:Cash         950.00 USD
  Income:Gains       -150.00 USD            ; 收益/亏损取决于选中哪个 lot
```

如果卖出时选中 **Lot 1**（$100成本）：
- 卖出成本 = 8 × $100 = $800
- 收益 = $950 - $800 = $150

如果选中 **Lot 2**（$120成本）：
- 卖出成本 = 8 × $120 = $960
- 亏损 = $950 - $960 = -$10

**Booking 方法决定了系统如何选择 lot。**

### Booking 方法枚举

```kotlin
enum class Booking {
    STRICT,           // 严格匹配，不允许模糊
    STRICT_WITH_SIZE, // 严格+按大小自动区分
    NONE,             // 不 booking，允许负持仓
    AVERAGE,          // 平均成本（当前未实现）
    FIFO,             // 先进先出
    LIFO,             // 后进先出
    HIFO              // 高成本先出
}
```

---

## 1. STRICT 方法

### 原理

**最严格的匹配方式**：要求减少 posting 只能匹配到**唯一的 lot**。

- 如果 Inventory 中只有 1 个匹配的 lot → 直接匹配
- 如果有多个匹配的 lot → **报错**（ambiguous match）
- 如果匹配 lot 的数量不足 → **报错**（insufficient）

### 适用场景

- 简单投资账户（每个股票通常只有一个 lot）
- 需要精确控制成本的用户
- 默认推荐方法

### 示例

```beancount
option "booking_method" "STRICT"

; 场景1: 唯一匹配（成功）
2023-01-01 open Assets:Invest AAPL "STRICT"
2023-01-01 * "Buy"
  Assets:Invest  10 AAPL {100.00 USD}
  Assets:Cash   -1000.00 USD

2023-02-01 * "Sell"
  Assets:Invest  -5 AAPL {100.00 USD}  ; 只有一个 lot，匹配成功
  Assets:Cash     550.00 USD

; 场景2: 模糊匹配（报错）
2023-03-01 * "Buy more"
  Assets:Invest   5 AAPL {120.00 USD}  ; 现在有2个 lots
  Assets:Cash    -600.00 USD

2023-04-01 * "Sell"
  Assets:Invest  -3 AAPL {USD}         ; 模糊！匹配到2个 lots
  Assets:Cash     360.00 USD
  ; ERROR: Ambiguous matches for "-3 AAPL {USD}"
```

### Python 代码逻辑

```python
def booking_method_STRICT(entry, posting, matches):
    """STRICT: 要求唯一匹配，否则报错"""
    booked_reductions = []
    booked_matches = []
    errors = []
    insufficient = False

    if len(matches) > 1:
        # 检查所有匹配的 lots 加起来是否正好等于要减少的数量
        sum_matches = sum(p.units.number for p in matches)
        if sum_matches == -posting.units.number:
            # 特殊情况：如果所有 lots 的总和正好等于要减少的数量
            # 则匹配所有 lots
            booked_reductions.extend(
                posting._replace(units=-match.units, cost=match.cost)
                for match in matches
            )
        else:
            # 报错：模糊匹配
            errors.append(AmbiguousMatchError(
                entry.meta,
                'Ambiguous matches for "{}"'.format(position.to_string(posting)),
                entry
            ))
    else:
        # 只有一个匹配
        match = matches[0]
        sign = -1 if posting.units.number < ZERO else 1
        # 取两者中较小的数量
        number = min(abs(match.units.number), abs(posting.units.number))
        match_units = Amount(number * sign, match.units.currency)
        booked_reductions.append(posting._replace(units=match_units, cost=match.cost))
        booked_matches.append(match)
        # 检查是否数量不足
        insufficient = match_units.number != posting.units.number

    return booked_reductions, booked_matches, errors, insufficient
```

### 关键逻辑

1. **匹配检查**: 如果 `matches.size > 1` 且不满足特殊条件，报错
2. **数量限制**: `min(abs(match.units), abs(posting.units))` 防止超卖
3. **符号处理**: `sign = -1` 表示减少，结果 posting 的 units 符号反转
4. **不足检测**: 如果 `match_units != posting.units`，说明 inventory 中数量不够

---

## 2. STRICT_WITH_SIZE 方法

### 原理

STRICT 的增强版。当 STRICT 遇到模糊匹配时报错，但 **STRICT_WITH_SIZE** 会尝试通过**大小（units number）**进一步区分：

1. 先尝试 STRICT 逻辑
2. 如果 STRICT 失败（模糊匹配）
3. 查找所有匹配的 lots 中，**数量完全等于要减少的数量**的 lot
4. 如果找到，选择**最老的**那个 lot

### 适用场景

- 有多个 lots，但偶尔有 lot 的大小正好等于卖出数量
- 比 STRICT 更灵活，但仍保持严格性

### 示例

```beancount
option "booking_method" "STRICT_WITH_SIZE"

2023-01-01 open Assets:Invest AAPL "STRICT_WITH_SIZE"

2023-01-01 * "Buy"
  Assets:Invest  10 AAPL {100.00 USD}  ; Lot 1
  Assets:Cash   -1000.00 USD

2023-02-01 * "Buy more"
  Assets:Invest   5 AAPL {120.00 USD}  ; Lot 2
  Assets:Cash    -600.00 USD

; 场景: 卖出5股
2023-03-01 * "Sell"
  Assets:Invest  -5 AAPL {USD}         ; 模糊匹配到 Lot 1 和 Lot 2
  Assets:Cash     600.00 USD
  ; 但 Lot 2 的大小正好是 5 股！
  ; 自动选择 Lot 2 (因为大小完全匹配)
```

### Python 代码逻辑

```python
def booking_method_STRICT_WITH_SIZE(entry, posting, matches):
    """STRICT + 按大小自动区分"""
    # 先尝试 STRICT
    result = booking_method_STRICT(entry, posting, matches)
    booked_reductions, booked_matches, errors, insufficient = result

    # 如果 STRICT 失败（有错误且多个匹配）
    if errors and len(matches) > 1:
        number = -posting.units.number  # 要减少的数量
        # 查找大小完全匹配的 lots
        matching_units = [
            match for match in matches
            if number == match.units.number
        ]
        if matching_units:
            # 按日期排序，选最老的
            matching_units.sort(key=lambda match: match.cost.date)
            match = matching_units[0]
            
            # 使用这个 lot
            booked_reductions = [posting._replace(units=-match.units, cost=match.cost)]
            booked_matches = [match]
            insufficient = False
            errors = []  # 清除错误

    return booked_reductions, booked_matches, errors, insufficient
```

### 关键逻辑

1. **降级策略**: 先尝试 STRICT，失败后再尝试大小匹配
2. **大小匹配**: `number == match.units.number` 精确相等
3. **最老优先**: `sort(key=lambda m: m.cost.date)`，日期最早的优先
4. **完全消耗**: 大小匹配时，整个 lot 被完全消耗

---

## 3. NONE 方法

### 原理

**不执行任何 booking**。减少 posting 不试图匹配现有 lot，直接作为新的负持仓添加。

- 允许负持仓（short position）
- 不维护成本基础
- 适用于不需要跟踪成本基础的账户

### 适用场景

- 期权/期货交易（允许做空）
- 不需要跟踪 realized gains 的账户
- 快速录入交易（不关心成本匹配）

### 示例

```beancount
option "booking_method" "NONE"

2023-01-01 open Assets:Invest AAPL "NONE"

2023-01-01 * "Buy"
  Assets:Invest  10 AAPL {100.00 USD}
  Assets:Cash   -1000.00 USD

2023-02-01 * "Sell"
  Assets:Invest  -5 AAPL {}             ; 不尝试匹配 lot
  Assets:Cash     550.00 USD
  ; 结果: Assets:Invest 现在有 5 AAPL (正) 和 -5 AAPL (负，独立 lot)
  ; 相当于允许做空
```

### Python 代码逻辑

```python
def booking_method_NONE(entry, posting, matches):
    """NONE: 不 booking，直接返回原 posting"""
    # 从不匹配现有 lots
    # 直接返回原 posting，不修改 cost
    return [posting], [], False
```

### 关键逻辑

1. **最简单**: 直接返回原 posting，不做任何处理
2. **副作用**: 可能产生混合 inventory（正负 lots 共存）
3. **后续处理**: 插值阶段可能会报告不完整的 CostSpec 错误

---

## 4. FIFO 方法（先进先出）

### 原理

**First-In-First-Out**: 卖出时，优先卖出**最早买入**的 lot。

1. 将所有匹配的 lots 按 **cost.date** 排序（日期升序）
2. 从最早的 lot 开始逐个减少
3. 一个 lot 消耗完后，继续下一个
4. 直到满足减少数量或 lots 耗尽

### 适用场景

- 税务优化（某些司法管辖区默认使用 FIFO）
- 长期投资者（老 lot 先卖出，可能享受长期资本利得税率）
- 最直观的会计方法

### 示例

```beancount
option "booking_method" "FIFO"

2023-01-01 open Assets:Invest AAPL "FIFO"

2023-01-01 * "Buy Lot 1"
  Assets:Invest  10 AAPL {100.00 USD}  ; 最早
  Assets:Cash   -1000.00 USD

2023-03-01 * "Buy Lot 2"
  Assets:Invest   5 AAPL {120.00 USD}  ; 中间
  Assets:Cash    -600.00 USD

2023-06-01 * "Buy Lot 3"
  Assets:Invest   3 AAPL {110.00 USD}  ; 最新
  Assets:Cash    -330.00 USD

; 卖出 12 股
2023-12-01 * "Sell"
  Assets:Invest  -12 AAPL {USD}
  Assets:Cash     1320.00 USD
  ; FIFO 结果:
  ; - 先消耗 Lot 1 (10股 @ $100) = -$1000
  ; - 再消耗 Lot 2 (2股 @ $120) = -$240
  ; - 总成本 = $1240
  ; - 收益 = $1320 - $1240 = $80
```

### Python 代码逻辑

```python
def booking_method_FIFO(entry, posting, matches):
    """FIFO: 先进先出"""
    return _booking_method_xifo(entry, posting, matches, "date", False)
    # sortattr="date", reverse_order=False (升序，老的在前面)

def _booking_method_xifo(entry, posting, matches, sortattr, reverse_order):
    """FIFO/LIFO/HIFO 通用实现"""
    booked_reductions = []
    booked_matches = []
    errors = []
    insufficient = False

    # 确定减少的方向
    sign = -1 if posting.units.number < ZERO else 1
    remaining = abs(posting.units.number)  # 还需要减少的数量

    # 按指定属性排序
    for match in sorted(matches,
                        key=lambda p: p.cost and getattr(p.cost, sortattr),
                        reverse=reverse_order):
        if remaining <= ZERO:
            break

        # 跳过符号不一致的 lot（mixed inventory 情况）
        if match.units.number * sign > ZERO:
            continue

        # 计算从这个 lot 中减少的数量
        size = min(abs(match.units.number), remaining)
        booked_reductions.append(
            posting._replace(
                units=Amount(size * sign, match.units.currency),
                cost=match.cost
            )
        )
        booked_matches.append(match)
        remaining -= size

    # 检查是否还有未满足的减少需求
    insufficient = remaining > ZERO

    return booked_reductions, booked_matches, errors, insufficient
```

### 关键逻辑

1. **排序**: `sorted(matches, key=lambda p: p.cost.date)` 按日期升序
2. **逐个消耗**: 一个 lot 不够就继续下一个
3. **拆分 posting**: 一个 reducing posting 可能拆分为多个 postings
4. **符号一致性**: 跳过符号不一致的 lot（防止 mixed inventory 混乱）

---

## 5. LIFO 方法（后进先出）

### 原理

**Last-In-First-Out**: 卖出时，优先卖出**最晚买入**的 lot。

1. 将所有匹配的 lots 按 **cost.date** 排序（日期降序）
2. 从最新的 lot 开始逐个减少
3. 与 FIFO 逻辑相同，只是排序方向相反

### 适用场景

- 通胀环境（新 lot 成本更高，卖出可减少应税收益）
- 短期交易者（新 lot 先卖出）
- 库存管理（某些行业默认 LIFO）

### 示例

```beancount
option "booking_method" "LIFO"

2023-01-01 open Assets:Invest AAPL "LIFO"

2023-01-01 * "Buy Lot 1"
  Assets:Invest  10 AAPL {100.00 USD}  ; 最老
  Assets:Cash   -1000.00 USD

2023-03-01 * "Buy Lot 2"
  Assets:Invest   5 AAPL {120.00 USD}  ; 中间
  Assets:Cash    -600.00 USD

2023-06-01 * "Buy Lot 3"
  Assets:Invest   3 AAPL {110.00 USD}  ; 最新
  Assets:Cash    -330.00 USD

; 卖出 12 股
2023-12-01 * "Sell"
  Assets:Invest  -12 AAPL {USD}
  Assets:Cash     1320.00 USD
  ; LIFO 结果:
  ; - 先消耗 Lot 3 (3股 @ $110) = -$330
  ; - 再消耗 Lot 2 (5股 @ $120) = -$600
  ; - 最后消耗 Lot 1 (4股 @ $100) = -$400
  ; - 总成本 = $1330
  ; - 亏损 = $1320 - $1330 = -$10
```

### Python 代码逻辑

```python
def booking_method_LIFO(entry, posting, matches):
    """LIFO: 后进先出"""
    return _booking_method_xifo(entry, posting, matches, "date", True)
    # sortattr="date", reverse_order=True (降序，新的在前面)
```

### 与 FIFO 的区别

| 方面 | FIFO | LIFO |
|------|------|------|
| 排序方向 | 升序（老→新） | 降序（新→老） |
| 卖出顺序 | 先卖老 lot | 先卖新 lot |
| 通胀时收益 | 较高（老 lot 成本低） | 较低（新 lot 成本高） |
| 实现代码 | `reverse=False` | `reverse=True` |

---

## 6. HIFO 方法（高成本先出）

### 原理

**Highest-Cost-First-Out**: 卖出时，优先卖出**成本最高**的 lot。

1. 将所有匹配的 lots 按 **cost.number**（单位成本）排序（降序）
2. 从成本最高的 lot 开始逐个减少
3. 目标：最小化资本利得（或最大化资本亏损）

### 适用场景

- 税务优化（卖出高成本 lot 减少应税收益）
- 投资组合优化（控制 realized gains）
- 高级投资策略

### 示例

```beancount
option "booking_method" "HIFO"

2023-01-01 open Assets:Invest AAPL "HIFO"

2023-01-01 * "Buy Lot 1"
  Assets:Invest  10 AAPL {100.00 USD}  ; 低成本
  Assets:Cash   -1000.00 USD

2023-03-01 * "Buy Lot 2"
  Assets:Invest   5 AAPL {120.00 USD}  ; 高成本
  Assets:Cash    -600.00 USD

2023-06-01 * "Buy Lot 3"
  Assets:Invest   3 AAPL {110.00 USD}  ; 中成本
  Assets:Cash    -330.00 USD

; 卖出 12 股
2023-12-01 * "Sell"
  Assets:Invest  -12 AAPL {USD}
  Assets:Cash     1320.00 USD
  ; HIFO 结果:
  ; - 先消耗 Lot 2 (5股 @ $120) = -$600  ; 成本最高
  ; - 再消耗 Lot 3 (3股 @ $110) = -$330  ; 成本次高
  ; - 最后消耗 Lot 1 (4股 @ $100) = -$400 ; 成本最低
  ; - 总成本 = $1330
  ; - 亏损 = $1320 - $1330 = -$10
  ; 与 LIFO 结果相同（因为 Lot 2 既是最新也是成本最高）
```

### Python 代码逻辑

```python
def booking_method_HIFO(entry, posting, matches):
    """HIFO: 高成本先出"""
    return _booking_method_xifo(entry, posting, matches, "number", True)
    # sortattr="number" (cost number), reverse_order=True (降序，高的在前面)
```

### 关键逻辑

1. **按成本排序**: `sorted(matches, key=lambda p: p.cost.number, reverse=True)`
2. **税务优势**: 优先消耗高成本 lot → 减少 realized gains
3. **与 LIFO 差异**: 按成本而非日期排序，可能选择老但高成本的 lot

---

## 7. AVERAGE 方法（平均成本）

### 原理

**Average Cost**: 将所有匹配的 lots 合并为一个平均成本的 lot，然后减少。

1. 将所有匹配的 lots 合并
2. 计算加权平均成本
3. 从合并后的 lot 中减少

**注意**: Beancount 当前版本中 AVERAGE 方法**未完全实现**，直接报错。

### 适用场景

- 某些基金投资（基金公司通常使用平均成本法）
- 简化记账（不需要跟踪每个 lot）
- 特定税务要求

### 示例（理论）

```beancount
option "booking_method" "AVERAGE"

2023-01-01 open Assets:Invest AAPL "AVERAGE"

2023-01-01 * "Buy Lot 1"
  Assets:Invest  10 AAPL {100.00 USD}  ; $1000
  Assets:Cash   -1000.00 USD

2023-03-01 * "Buy Lot 2"
  Assets:Invest   5 AAPL {120.00 USD}  ; $600
  Assets:Cash    -600.00 USD

; 平均成本 = ($1000 + $600) / (10 + 5) = $106.67/股

2023-12-01 * "Sell"
  Assets:Invest  -8 AAPL {}            ; 按平均成本
  Assets:Cash     880.00 USD
  ; 理论结果:
  ; - 卖出成本 = 8 × $106.67 = $853.33
  ; - 收益 = $880 - $853.33 = $26.67
```

### Python 代码逻辑

```python
def booking_method_AVERAGE(entry, posting, matches):
    """AVERAGE: 当前未实现"""
    errors = [AmbiguousMatchError(
        entry.meta,
        "AVERAGE method is not supported",
        entry
    )]
    return [], [], errors, False

# 注释中有未启用的实现代码（FIXME）
# 核心逻辑:
# 1. 合并所有 lots: merged_units = sum(match.units for match in matches)
# 2. 合并成本: merged_cost = sum(get_weight(match) for match in matches)
# 3. 计算平均: avg_cost = merged_cost / merged_units
# 4. 从合并 lot 中减少
```

### 未实现原因

1. **复杂度**: 需要维护合并状态，影响后续交易
2. **不可逆**: 一旦合并，原始 lots 信息丢失
3. **需求低**: 大多数用户使用 FIFO/LIFO
4. **税务限制**: 某些地区不允许平均成本法用于股票

---

## Booking 方法选择指南

| 方法 | 复杂度 | 税务影响 | 适用用户 |
|------|--------|----------|----------|
| **STRICT** | 低 | 可控 | 新手/简单投资 |
| **STRICT_WITH_SIZE** | 中 | 可控 | 偶尔有整 lot 卖出的用户 |
| **FIFO** | 低 | 长期持有优势 | 长期投资者 |
| **LIFO** | 低 | 短期持有优势 | 短期交易者 |
| **HIFO** | 中 | 税务优化 | 高级用户 |
| **NONE** | 低 | 无 | 期货/期权/不跟踪成本 |
| **AVERAGE** | 高 | 视地区 | 未实现 |

### 默认配置建议

```beancount
; 全局默认
option "booking_method" "STRICT"

; 特定账户覆盖
2023-01-01 open Assets:Invest:Stock AAPL "FIFO"
2023-01-01 open Assets:Invest:Options "NONE"
```

---

## 核心算法通用流程

无论哪种 booking 方法，核心流程相同：

```
1. 遍历 Transaction 中的每个 posting
   └── 如果 posting 有 cost 且 units 已指定:
       ├── 检查该 account 的当前 Inventory
       ├── 判断是 augmentation（增加）还是 reduction（减少）
       │   └── reduction: 在 Inventory 中查找匹配的 lots
       ├── 调用对应的 booking method
       │   └── 返回: (booked_postings, booked_matches, errors, insufficient)
       ├── 如果成功:
       │   ├── 替换原 posting 为 booked_postings（可能拆分多个）
       │   ├── 从 Inventory 中扣除 matched lots
       │   └── 更新 account balance
       └── 如果失败:
           └── 记录 error，跳过该 posting
```

---

*文档生成时间*: 2026-05-27  
*参考源码*: `beancount/parser/booking_method.py` (320 行), `beancount/parser/booking_full.py` (520 行)
