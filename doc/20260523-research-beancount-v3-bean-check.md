# Beancount v3 核心命令 bean-check 研究报告

## 1. 研究概述

本文档研究 Beancount v3 Python 实现中的 `bean-check` 命令，分析其依赖库、运行逻辑和执行步骤，为 Kotlin/JVM 迁移提供参考。

---

## 2. 项目依赖库

### 2.1 运行时依赖

根据 `pyproject.toml` 中的配置：

| 库名 | 版本要求 | 用途 |
|------|----------|------|
| **click** | >=7.0 | CLI 命令行框架，用于定义命令参数和选项 |
| **python-dateutil** | >=2.6.0 | 日期解析和处理 |
| **regex** | >=2022.9.13 | 正则表达式增强（支持 Unicode 属性等） |

### 2.2 构建依赖

| 库名 | 版本要求 | 用途 |
|------|----------|------|
| **meson-python** | >=0.14.0 | 构建后端，替代 setuptools |
| **meson** | >=1.2.1 | 构建系统 |

### 2.3 可选依赖

| 库名 | 版本要求 | 用途 |
|------|----------|------|
| **mypy** | ==1.4.0 | 静态类型检查 |
| **types-regex** | - | regex 的类型存根 |

### 2.4 标准库使用

Beancount v3 大量使用 Python 标准库：

- `logging` - 日志记录（`bean-check -v` 开启 INFO 级别）
- `pickle` - 缓存序列化（`.filename.picklecache`）
- `hashlib` / `struct` / `os.stat` - 输入文件变更检测
- `glob` - `include` 指令的文件匹配
- `importlib` - 动态插件加载
- `traceback` - 插件错误堆栈捕获
- `functools` - 缓存装饰器
- `datetime` / `decimal` - 核心数据类型

---

## 3. bean-check 命令定义

### 3.1 入口点

```
[project.scripts]
bean-check = "beancount.scripts.check:main"
```

### 3.2 命令参数

```python
@click.command()
@click.argument("filename", type=click.Path())
@click.option("--verbose", "-v", is_flag=True, help="Print timings.")
@click.option("--no-cache", "-C", is_flag=True, help="Disable the cache.")
@click.option("--cache-filename", type=click.Path(), help="Override the cache filename.")
@click.option("--auto", "-a", is_flag=True, help="Implicitly enable auto-plugins.")
@click.version_option(message=VERSION)
def main(filename, verbose, no_cache, cache_filename, auto):
    ...
```

### 3.3 文件位置

- **源码**: `beancount/scripts/check.py`
- **总行数**: ~70 行（非常简洁的入口脚本）

---

## 4. 运行基础逻辑

### 4.1 整体流程

```
bean-check filename
    ├── 参数解析 (click)
    ├── 可选: 启用 auto-plugins (--auto)
    ├── 可选: 配置缓存 (--no-cache, --cache-filename)
    └── loader.load_file()              ← 核心调用
            ├── parse                   ← 解析文件
            ├── booking                 ← 补全分录（自动借贷平衡）
            ├── run_transformations     ← 运行插件
            └── validate                ← 验证数据
    └── 退出码: 0(成功) 或 1(有错误)
```

### 4.2 核心调用链

```python
# bean-check 的核心只有这一行调用
entries, errors, _ = loader.load_file(
    filename,
    log_timings=logging.info,
    log_errors=sys.stderr,
    extra_validations=validation.HARDCORE_VALIDATIONS,
)
```

**关键区别**: `bean-check` 与普通加载的区别在于传递了 `extra_validations=validation.HARDCORE_VALIDATIONS`，这会启用额外的严格验证。

---

## 5. 详细运行步骤

### 5.1 步骤 1: 解析阶段 (`_parse_recursive`)

```
_parse_recursive(sources)
    ├── 递归处理 include 文件（广度优先）
    ├── 检测重复文件（防循环引用）
    ├── 解析每个文件/字符串
    │       └── parser.parse_file() / parser.parse_string()
    ├── 合并 entries 和 errors
    ├── 处理 include 指令的 glob 匹配
    ├── 聚合 options_map
    └── 按日期排序 entries
```

**输入文件处理**:
- 支持加密文件（GPG），自动解密后解析
- 支持文件不存在和重复文件的错误报告
- `include` 支持 glob 模式（如 `"*.bean"`）

### 5.2 步骤 2: Booking 阶段 (`booking.book`)

```
booking.book(entries, options_map)
    └── 处理分录的不完整信息
            ├── 自动推断缺失的 units
            ├── 处理 cost basis（成本基准）
            └── 确保交易借贷平衡
```

**作用**: 将用户输入的"不完整"交易（如省略了平衡分录）补全为完整交易。

### 5.3 步骤 3: 转换阶段 (`run_transformations`)

```
run_transformations(entries, errors, options_map)
    ├── 按顺序运行插件:
    │   ├── PLUGINS_PRE（预定义）
    │   │       └── beancount.ops.documents
    │   ├── 用户插件（来自 options）
    │   ├── PLUGINS_AUTO（--auto 时启用）
    │   │       └── beancount.plugins.auto
    │   └── PLUGINS_POST（后处理）
    │           ├── beancount.ops.pad      ← 自动填充账户
    │           └── beancount.ops.balance  ← 处理 balance 断言
    ├── 动态 import 插件模块
    ├── 调用插件的 __plugins__ 列表中的函数
    └── 每次插件后重新排序 entries
```

**插件机制**:
- 每个插件是一个 Python 模块
- 模块必须有 `__plugins__` 属性，列出可调用的函数名
- 函数签名: `(entries, options_map, *config) -> (entries, errors)`
- 插件错误被捕获并转为 `LoadError`，不会中断处理

### 5.4 步骤 4: 验证阶段 (`validation.validate`)

```
validation.validate(entries, options_map, extra_validations)
    ├── BASIC_VALIDATIONS（始终运行）:
    │   ├── validate_open_close         ← 检查账户开闭一致性
    │   ├── validate_active_accounts    ← 检查引用账户是否有效
    │   ├── validate_currency_constraints ← 检查货币约束
    │   ├── validate_duplicate_balances ← 检查重复余额断言
    │   ├── validate_duplicate_commodities ← 检查重复商品声明
    │   ├── validate_documents_paths    ← 检查文档路径绝对性
    │   └── validate_check_transaction_balances ← 检查交易平衡
    └── HARDCORE_VALIDATIONS（bean-check 额外启用）:
            └── validate_data_types     ← 严格检查数据类型
```

**验证错误类型**: 所有验证返回 `ValidationError(source, message, entry)`

---

## 6. 核心数据结构

### 6.1 指令类型（Directives）

| 指令 | 用途 | 关键字段 |
|------|------|----------|
| `Open` | 开户 | account, currencies, booking |
| `Close` | 关户 | account |
| `Commodity` | 商品声明 | currency |
| `Transaction` | 交易 | flag, payee, narration, tags, links, postings |
| `Posting` | 分录 | account, units, cost, price, flag |
| `Balance` | 余额断言 | account, amount, tolerance |
| `Pad` | 自动填充 | account, source_account |
| `Note` | 备注 | account, comment |
| `Event` | 事件 | type, description |
| `Price` | 价格 | currency, amount |
| `Document` | 文档 | account, filename |
| `Query` | 查询 | name, query_string |
| `Custom` | 自定义 | type, values |

### 6.2 所有指令共有字段

- `meta`: Dict[str, Any] - 元数据，必须包含 `filename` 和 `lineno`
- `date`: datetime.date - 指令日期

### 6.3 返回三元组

```python
# loader.load_file() 返回
tuple[
    list[Directive],      # entries: 按日期排序的指令列表
    list[BeancountError], # errors: 解析和验证错误
    dict[str, Any]        # options_map: 选项配置
]
```

---

## 7. 缓存机制

### 7.1 Pickle 缓存

```
文件名: .{filename}.picklecache

缓存逻辑:
    ├── 检查缓存文件是否存在
    ├── 检查输入文件是否变更（MD5 hash + mtime + size）
    ├── 命中 → 直接返回反序列化结果
    └── 未命中 → 重新计算，耗时 > 1秒则写入缓存
```

### 7.2 缓存控制

| 选项 | 行为 |
|------|------|
| 默认 | 启用缓存（除非 `BEANCOUNT_DISABLE_LOAD_CACHE` 环境变量设置） |
| `--no-cache` / `-C` | 禁用缓存，并删除已有缓存文件 |
| `--cache-filename` | 自定义缓存文件名（支持 `{filename}` 占位符） |

---

## 8. 错误处理

### 8.1 错误输出

```python
# 错误被输出到 stderr
_log_errors(errors, sys.stderr)
    └── printer.print_errors(errors, file=sys.stderr)
```

### 8.2 退出码

```python
sys.exit(1 if errors else 0)
```

- `0`: 无错误（验证通过）
- `1`: 存在任何错误（包括解析错误和验证错误）

### 8.3 错误类型层次

```
BeancountError (Protocol)
    ├── source: Meta      ← 来源位置信息
    ├── message: str      ← 错误描述
    └── entry: Directive  ← 关联的指令（可能为 None）

实现类:
    ├── LoadError         ← 加载阶段错误
    └── ValidationError   ← 验证阶段错误
```

---

## 9. 关键模块依赖关系

```
beancount/scripts/check.py
    └── beancount.loader
            ├── beancount.core.data          ← 核心数据结构
            ├── beancount.ops.validation     ← 验证逻辑
            ├── beancount.parser.booking     ← 分录补全
            ├── beancount.parser.options     ← 默认选项
            ├── beancount.parser.parser      ← C/复解析器
            ├── beancount.parser.printer     ← 错误打印
            ├── beancount.utils.encryption   ← GPG 解密
            └── beancount.utils.misc_utils   ← 工具函数
```

---

## 10. 对 Kotlin/JVM 迁移的启示

### 10.1 需要实现的核心组件

| Python 模块 | Kotlin 对应 | 优先级 |
|-------------|-------------|--------|
| `core/data.py` | `core` 模块数据类 | 高 |
| `loader.py` | `loader` 模块 | 高 |
| `ops/validation.py` | `core` 验证逻辑 | 高 |
| `parser/parser.py` | `parser` 模块 | 高 |
| `parser/booking.py` | `parser` booking | 高 |
| `scripts/check.py` | `cli` 模块入口 | 中 |
| 缓存机制 | 可选实现 | 低 |
| 插件系统 | 后期实现 | 低 |

### 10.2 CLI 框架选型建议

Python 使用 `click`，Kotlin/JVM 可考虑：
- **Clikt** (Kotlin Multiplatform) - 最类似 click 的 Kotlin 库
- ** kotlinx.cli** - JetBrains 官方（已废弃）
- **Picocli** (Java) - 功能最全面的 Java CLI 框架

### 10.3 需要替换的标准库功能

| Python | Kotlin/Java |
|--------|-------------|
| `pickle` 缓存 | Kotlin 序列化 + 文件存储 |
| `glob` | `java.nio.file.PathMatcher` |
| `importlib` | 反射/ServiceLoader（插件） |
| `hashlib.md5` | `java.security.MessageDigest` |
| `decimal.Decimal` | `java.math.BigDecimal` |

---

## 11. 参考资源

- **源码仓库**: https://github.com/beancount/beancount/tree/v3
- **核心入口**: `beancount/scripts/check.py`
- **加载逻辑**: `beancount/loader.py`
- **验证逻辑**: `beancount/ops/validation.py`
- **数据结构**: `beancount/core/data.py`
- **构建配置**: `pyproject.toml`

---

*文档生成时间: 2026-05-23*
*基于 beancount v3 分支研究*
