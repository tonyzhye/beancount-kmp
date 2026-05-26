# Decimal 类型设计决策

## 概述

本文档记录 Beancount JVM 项目中 Decimal（高精度数字）类型的设计决策，包括跨平台策略和实现细节。

## 决策背景

Beancount 作为会计系统，需要精确的十进制运算，不能使用浮点数。Python 使用 `decimal.Decimal`，Java 使用 `java.math.BigDecimal`。

## 目标平台

- **现阶段**：JVM（主要目标）
- **确定未来目标**：iOS/Native（Swift 桥接）
- **可能目标**：JavaScript

## 方案评估

| 方案 | KMP 支持 | 精度 | 性能 | 构建复杂度 | 维护成本 |
|------|----------|------|------|-----------|----------|
| A. expect/actual + JVM BigDecimal | ✅ JVM 完美 | 完美 | 优秀 | 低 | 低 |
| B. Kotlin-Native-BigDecimal | ✅ 全平台 | 完美 | 中 | **高** | 中 |
| C. Swift NSDecimalNumber 桥接 | ✅ iOS | 良好 | **优秀** | 低 | 低 |
| D. 纯 Kotlin 实现 | ✅ 全平台 | 完美 | 差 | **高** | **高** |

### 方案 A：expect/actual + JVM BigDecimal（当前选择）

**实现方式**：
```kotlin
// commonMain
expect class Decimal : Comparable<Decimal> {
    constructor(value: String)
    operator fun plus(other: Decimal): Decimal
    // ...
}

// jvmMain
actual class Decimal(private val bigDecimal: BigDecimal) { ... }
```

**优点**：
- 零额外依赖
- JVM 使用成熟的 BigDecimal
- 接口统一，未来切换实现不影响业务代码

**缺点**：
- 需要为每个平台写 actual 实现

### 方案 B：Kotlin-Native-BigDecimal

**参考**：https://github.com/Crossoid/Kotlin-Native-BigDecimal

**优点**：
- 真正的 KMP，无需 expect/actual
- API 与 Java BigDecimal 完全一致

**缺点**：
- 需要手动构建 BoringSSL（CMake + Go）
- 增加包体积（C 库）
- 对 JVM 而言是多余依赖
- 社区较小（81 stars）

### 方案 C：Swift NSDecimalNumber 桥接（iOS 未来选择）

**实现方式**：
```kotlin
// iosMain
import platform.Foundation.NSDecimalNumber

actual class Decimal(private val value: NSDecimalNumber) { ... }
```

**优点**：
- 利用 iOS 原生高精度计算
- 性能最优
- 与 Swift 生态无缝

**缺点**：
- 仅适用于 iOS/Apple 平台
- 需要了解 Swift API

## 最终决定

### 阶段 1（当前）：JVM 优先

使用 **expect/actual + java.math.BigDecimal**

- commonMain 定义接口
- jvmMain 使用 BigDecimal 包装
- iOS 占位（可编译但抛 NotImplementedError 或空实现）

### 阶段 2（未来）：iOS 支持

使用 **Swift NSDecimalNumber 桥接**

- iosMain 实现 actual class
- 复用相同的 commonMain 接口
- 业务代码零修改

### 阶段 3（可选）：统一库

如果 Kotlin-Native-BigDecimal 或类似库成熟：
- 考虑替换为统一库
- 但仅在它提供明显优势时

## 代码示例

### commonMain 接口

```kotlin
expect class Decimal : Comparable<Decimal> {
    constructor(value: String)
    constructor(value: Double)
    constructor(value: Long)
    
    operator fun plus(other: Decimal): Decimal
    operator fun minus(other: Decimal): Decimal
    operator fun times(other: Decimal): Decimal
    operator fun div(other: Decimal): Decimal
    operator fun unaryMinus(): Decimal
    
    fun abs(): Decimal
    fun negate(): Decimal
    fun isZero(): Boolean
    fun isPositive(): Boolean
    fun isNegative(): Boolean
    
    fun toPlainString(): String
    fun toDouble(): Double
    
    companion object {
        val ZERO: Decimal
        val ONE: Decimal
    }
}
```

### jvmMain 实现

```kotlin
actual class Decimal {
    private val bigDecimal: BigDecimal
    
    actual constructor(value: String) {
        this.bigDecimal = BigDecimal(value)
    }
    
    actual operator fun plus(other: Decimal): Decimal {
        return Decimal(this.bigDecimal + other.bigDecimal)
    }
    
    // ... 其他操作符和函数
}
```

### iosMain 实现（未来）

```kotlin
import platform.Foundation.NSDecimalNumber

actual class Decimal {
    private val value: NSDecimalNumber
    
    actual constructor(value: String) {
        this.value = NSDecimalNumber(string = value)
    }
    
    // ... 实现相同接口
}
```

## 依赖影响

| 模块 | 依赖变化 |
|------|----------|
| core | 零第三方依赖（JVM 用标准库 BigDecimal） |
| parser | 无变化 |
| loader | 无变化 |

## 验证计划

1. **单元测试**：验证 Decimal 运算精度与 Python decimal 一致
2. **边界测试**：大数、小数、除法精度、舍入模式
3. **跨平台测试**（未来 iOS）：与 JVM 结果对比

## 相关文档

- [AGENTS.md](../AGENTS.md) - 项目约束和规则
- [20260525-plan-bean-check-migration.md](20260525-plan-bean-check-migration.md) - 实施计划
- Kotlin Issue: [KT-61573](https://youtrack.jetbrains.com/issue/KT-61573) - expect/actual classes Beta 状态

---

*文档生成时间: 2026-05-26*
*决策状态: 已实施（JVM 阶段）*
