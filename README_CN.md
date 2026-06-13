# Beancount JVM

[![CI](https://github.com/tonyzhye/beancount-kmp/actions/workflows/ci.yml/badge.svg)](https://github.com/tonyzhye/beancount-kmp/actions/workflows/ci.yml)
[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/gpl-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![JDK](https://img.shields.io/badge/JDK-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![klibs.io](https://img.shields.io/badge/klibs.io-Beancount%20JVM-blue?logo=kotlin)](https://klibs.io/projects/tonyzhye/beancount-kmp)

> [Beancount](https://github.com/beancount/beancount) v3.2.3 的 Kotlin 多平台实现，兼容 Java 和 Kotlin。

## 概述

Beancount JVM 是使用 Kotlin Multiplatform（KMP）将 Python Beancount 会计系统完整迁移到 JVM 生态系统的项目。它在保持与 Python Beancount 3.2.3 语义兼容的同时，在大型账簿上实现了 **高达 2.2 倍的性能提升**。

### 主要特性

- **完全兼容**：解析和处理所有有效的 Beancount v3.2.3 文件
- **高性能**：在大型账簿上比 Python 快达 2.2 倍（参见[基准测试](PERFORMANCE_REPORT_CN.md)）
- **零运行时依赖**：核心库仅需 kotlinx-datetime
- **双语 API**：Kotlin 优先，通过 `@JvmStatic` 提供 Java 兼容的静态方法
- **完整 CLI**：所有 Python beancount 命令（bean-check、bean-doctor、bean-format 等）
- **查询引擎**：完整的 BQL（Beancount 查询语言）实现

## 快速开始

### Maven 坐标

已发布到 **Maven Central**，并可在 **[klibs.io](https://klibs.io/projects/tonyzhye/beancount-kmp)** 上发现。

**KMP 项目（commonMain）：**
```kotlin
// build.gradle.kts（KMP 项目）
commonMain.dependencies {
    implementation("io.github.tonyzhye.beancount:core:3.2.3")
}
```

**纯 JVM 项目：**
```kotlin
// build.gradle.kts（JVM 项目）
dependencies {
    implementation("io.github.tonyzhye.beancount:core-jvm:3.2.3")
}
```

**Maven（纯 JVM）：**
```xml
<dependency>
    <groupId>io.github.tonyzhye.beancount</groupId>
    <artifactId>core-jvm</artifactId>
    <version>3.2.3</version>
</dependency>
```

### Kotlin 用法

```kotlin
import io.github.tonyzhye.beancount.api.Beancount as bn

// 加载 beancount 文件
val result = bn.loadFile("ledger.beancount")

// 检查错误
if (result.errors.isNotEmpty()) {
    println(bn.formatErrors(result.errors))
    return
}

// 查询所有交易
val transactions = bn.getTransactions(result.entries)

// 获取所有账户
val accounts = bn.getAccounts(result.entries)

// 构建价格映射
val priceMap = bn.buildPriceMap(result.entries)

// 将条目格式化为 beancount 语法
println(bn.formatEntries(result.entries))
```

### Java 用法

```java
import io.github.tonyzhye.beancount.api.Beancount;
import io.github.tonyzhye.beancount.core.LoadResult;

// 加载 beancount 文件
LoadResult result = Beancount.loadFile("ledger.beancount");

// 检查错误
if (!result.getErrors().isEmpty()) {
    System.out.println(Beancount.formatErrors(result.getErrors()));
    return;
}

// 获取所有账户
var accounts = Beancount.getAccounts(result.getEntries());
System.out.println("Accounts: " + accounts);
```

### CLI 用法

```bash
# 检查账簿文件
beancount check ledger.beancount

# 详细输出
beancount check ledger.beancount --verbose

# 格式化/美化文件
beancount format ledger.beancount -o formatted.beancount

# 运行 BQL 查询
beancount query ledger.beancount "SELECT date, narration, position WHERE account ~ 'Expenses'"

# 显示文件依赖
beancount deps ledger.beancount

# 获取任意命令的帮助
beancount check --help
```

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│                         CLI 层                               │
│   bean-check | bean-doctor | bean-format | beanquery        │
├─────────────────────────────────────────────────────────────┤
│                      API 层                                  │
│              Beancount 单例（60+ 方法）                      │
├─────────────────────────────────────────────────────────────┤
│                      查询引擎                                │
│   BQL 解析器 → 编译器 → 执行器（60+ 函数）                  │
├─────────────────────────────────────────────────────────────┤
│                      加载器层                                │
│   解析 → 排序 → 记账 → 转换 → 验证                          │
├──────────────┬──────────────┬───────────────────────────────┤
│    解析器    │   插件       │         核心                  │
│   词法分析器 │  18 个内置   │    12 种指令类型             │
│   解析器     │   插件       │    金额 / 成本 / 分录        │
│   记账       │              │    库存 / 验证               │
└──────────────┴──────────────┴───────────────────────────────┘
```

### 模块依赖

```
core（基础）
  ↑
parser（依赖 core）
  ↑
loader（依赖 parser、core）
  ↑
api（依赖 loader、parser、query、core、plugin-api）
  ↑
cli（依赖 api、loader、query）
```

## 功能完整性

### 核心功能（100%）
- [x] 全部 12 种指令类型（Open、Transaction、Balance 等）
- [x] 完整记账方法（FIFO、LIFO、HIFO、STRICT 等）
- [x] 完整验证套件（7 个验证器）
- [x] 库存管理与成本基础追踪
- [x] 显示上下文与数字格式化

### 解析器（95%）
- [x] 递归下降解析器，支持 20+ 种词法单元
- [x] 支持所有指令语法
- [x] 元数据与标签/链接处理
- [x] Include 文件解析
- [x] 插件指令解析
- [ ] C 扩展性能（不需要——Kotlin 更快）

### 插件（100%）
- [x] 18/18 个内置插件已实现
  - auto_accounts、implicit_prices、currency_accounts
  - leafonly、unique_prices、coherent_cost
  - check_closing、check_commodity、noduplicates
  - nounused、onecommodity、sellgains
  - check_drained、close_tree、pedantic
  - auto、check_average_cost、commodity_attr

### 查询引擎（90%）
- [x] BQL 解析器与编译器
- [x] 60+ 内置函数
- [x] JOIN、GROUP BY、ORDER BY、PIVOT BY
- [x] 时间切片（YEAR、QUARTER、MONTH、WEEK、DAY）
- [x] 多种输出格式（TEXT、CSV、HTML、BEANCOUNT）

### CLI 工具（90%）
- [x] bean-check - 验证账簿文件
- [x] bean-doctor - 诊断工具
- [x] bean-example - 生成示例账簿
- [x] bean-format - 格式化/美化文件
- [x] beanquery - BQL 查询接口
- [x] treeify - 树形可视化
- [x] bean-deps - 依赖分析
- [ ] bean-report（按设计排除）
- [ ] bean-price（按设计排除）

所有 18 个内置插件和完整的 BQL 查询引擎均已实现。

## Python 兼容性

与 Python Beancount 3.2.3 对比：

- **98 个测试类** —— 全部通过
- **9 个端到端文件**（starter、basic、example、50KB–10MB）—— Python 和 Kotlin 均零错误
- **全部 18 个内置插件** 产生相同结果

*完整对比报告：[CONSISTENCY_REPORT.md](CONSISTENCY_REPORT.md)*

## 性能

与 Python Beancount 3.2.3 对比（加载 + 记账 + 验证）：

| 文件大小 | Python (ms) | Kotlin (ms) | 加速比 |
|----------|-------------|-------------|--------|
| Example (340 KB) | 87 | 77 | **1.1x** |
| 1 MB | 161 | 154 | **1.0x** |
| 5 MB | 1,467 | 781 | **1.9x** |
| 10 MB | 3,423 | 1,543 | **2.2x** |

Kotlin 随文件大小增长扩展性更好——从小文件的基本持平到 **10 MB 账簿快 2.2 倍**。

*完整基准测试报告：[PERFORMANCE_REPORT_CN.md](PERFORMANCE_REPORT_CN.md)*

## 从源码构建

### 要求
- JDK 21+
- Gradle 9.5+（已包含 wrapper）

### 构建
```bash
# 构建所有模块
./gradlew build

# 运行测试
./gradlew test

# 生成覆盖率报告
./gradlew koverHtmlReport

# 构建 CLI 分发包
./gradlew :cli:distZip

# 发布到本地 Maven
./gradlew publishToMavenLocal
```

### IDE 设置
推荐：安装 Kotlin 插件的 IntelliJ IDEA

1. 打开项目根目录（`settings.gradle.kts` 所在目录）
2. Gradle 将自动同步并下载依赖
3. 通过 IDE 或 `./gradlew test` 运行测试

## 测试

```bash
# 全部测试
./gradlew test

# 指定模块
./gradlew :core:jvmTest
./gradlew :parser:jvmTest
./gradlew :loader:jvmTest
./gradlew :query:jvmTest

# 覆盖率验证
./gradlew koverVerify

# 兼容性测试（需要 Python + beancount 3.2.3）
./gradlew :loader:jvmTest --tests "*CompatTest"
```

**覆盖率状态：**
| 模块 | 覆盖率 |
|------|--------|
| core | 81.3% |
| parser | 81.6% |
| loader | 80.1% |
| query | 81.6% |
| api | 94.3% |
| plugin-api | 96.2% |

## 文档

- [Architecture Overview](AGENTS.md) - 项目结构与约定
- [CI/CD Guide](.github/CI_CD_GUIDE.md) - GitHub Actions 配置
- [Performance Report](PERFORMANCE_REPORT_CN.md) - 详细基准测试
- [Consistency Report](CONSISTENCY_REPORT.md) - Python 一致性验证
- [Changelog](CHANGELOG.md) - 版本历史

## 贡献

欢迎贡献！请阅读我们的 [Contributing Guide](CONTRIBUTING.md)（如有），并确保你的更改：

1. 通过所有测试（`./gradlew test`）
2. 保持 80%+ 代码覆盖率
3. 遵循现有代码风格
4. 包含适当的测试

## 许可证

本项目采用 **GNU General Public License v2.0 only (GPL-2.0-only)** 许可证。

Copyright (C) 2026 Tony Ye

基于 [Beancount](https://github.com/beancount/beancount)（Martin Blais 创作，GPL-2.0-only 许可）。

## 致谢

- [Martin Blais](https://github.com/blais) - Beancount 创始人
- [Beancount Community](https://beancount.github.io/docs/) - 文档与生态系统
- [Kotlin Team](https://kotlinlang.org/) - 出色的多平台支持

## 相关项目

- [beancount](https://github.com/beancount/beancount) - 原始 Python 实现
- [fava](https://github.com/beancount/fava) - Beancount 的 Web 界面
- [beancount-language-server](https://github.com/polarmutex/beancount-language-server) - LSP 实现

---

*本项目为独立项目，与官方 Beancount 项目无关联。*
