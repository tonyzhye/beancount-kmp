# Beancount JVM 性能测试报告

**测试日期**: 2026-05-26  
**测试环境**: Windows, JDK 21, Python 3.14.5, Beancount 3.2.3

## 测试概述

本报告对比 Kotlin/JVM 实现与 Python Beancount 在不同文件大小下的加载性能。

### 测试文件

| 文件 | 大小 | 交易数 | 说明 |
|------|------|--------|------|
| test_50kb.bean | 50 KB | ~670 | 简单交易 |
| test_100kb.bean | 100 KB | ~1,330 | 简单交易 |
| test_500kb.bean | 500 KB | ~6,700 | 简单交易 |
| test_1mb.bean | 1 MB | ~13,400 | 简单交易 |
| test_5mb.bean | 5 MB | ~67,000 | 简单交易 |
| test_10mb.bean | 10 MB | ~134,000 | 简单交易 |

所有测试文件均为自包含文件（包含所有账户声明），确保解析不受外部依赖影响。

### 测试方法

- **JVM**: 使用 `loadFile()` 函数，包含完整解析管道（Parse → Sort → Booking → Transformations → Validation）
- **Python**: 使用 `beancount.loader.load_file()`，默认包含相同处理流程
- **计时方式**: 3 次运行取平均值，包含一次 warmup
- **超时设置**: 小文件 60 秒，大文件 300 秒

---

## JVM vs Python 性能对比

### 小文件对比 (50 KB - 1 MB)

| 文件 | 大小 | JVM (ms) | Python (ms) | 优势 |
|------|------|----------|-------------|------|
| test_50kb.bean | 50 KB | 18 | 35 | **1.94x faster** |
| test_100kb.bean | 100 KB | 23 | 70 | **3.04x faster** |
| test_500kb.bean | 500 KB | 55 | 477 | **8.67x faster** |
| test_1mb.bean | 1 MB | 95 | 931 | **9.80x faster** |

### 大文件对比 (5 MB - 10 MB)

| 文件 | 大小 | JVM (ms) | Python (ms) | 优势 |
|------|------|----------|-------------|------|
| test_5mb.bean | 5 MB | 414 | 1,310 | **3.16x faster** |
| test_10mb.bean | 10 MB | 817 | 2,860 | **3.50x faster** |

---

## JVM-Only 基准测试

### 小文件基准

| 文件 | 大小 | 平均时间 | 最小时间 | 吞吐量 |
|------|------|----------|----------|--------|
| test_50kb.bean | 50 KB | 2ms | 2ms | **26,301 KB/s** |
| test_100kb.bean | 100 KB | 5ms | 5ms | **21,052 KB/s** |
| test_500kb.bean | 500 KB | 35ms | 34ms | **15,034 KB/s** |
| test_1mb.bean | 1 MB | 73ms | 69ms | **14,414 KB/s** |

### 大文件基准

| 文件 | 大小 | 平均时间 | 最小时间 | 吞吐量 |
|------|------|----------|----------|--------|
| test_5mb.bean | 5 MB | 411ms | 392ms | **12,801 KB/s** |
| test_10mb.bean | 10 MB | 864ms | 798ms | **12,179 KB/s** |

---

## 关键发现

### 1. JVM 全面优于 Python

在所有测试场景中，JVM 实现均显著快于 Python：
- 小文件（1MB 以内）: **1.94x - 9.80x** 更快
- 大文件（5-10MB）: **3.16x - 3.50x** 更快

### 2. 性能优势随文件大小变化

| 文件大小范围 | 典型优势 | 说明 |
|-------------|---------|------|
| < 100 KB | 2-3x | Python 启动开销占比较大 |
| 100 KB - 1 MB | 8-10x | JVM JIT 优化充分，缓存命中率高 |
| 5-10 MB | 3-3.5x | 两者均进入稳定状态，JVM 保持领先 |

### 3. JVM 吞吐量分析

- **峰值吞吐**: 26,301 KB/s (50 KB 文件)
- **稳定吞吐**: 12,000-15,000 KB/s (大文件)
- **扩展性**: 近乎线性扩展，10MB 文件 864ms 完成

### 4. 与 Python 的差异分析

Python 性能瓶颈可能来自：
- 解释器开销（无 JIT 编译）
- GIL（全局解释器锁）限制并行
- 动态类型检查开销

JVM 优势来自：
- JIT 编译优化
- 内存管理效率
- 静态类型编译时优化

---

## 结论

**Kotlin/JVM 实现成功超越 Python Beancount 性能**：
- 小文件场景快 **2-10 倍**
- 大文件场景快 **3-3.5 倍**
- 吞吐率稳定在 **12,000-26,000 KB/s**

对于大型账本文件（MB 级别），JVM 版本可以在 **1 秒内**完成加载，满足生产环境性能要求。

---

*测试代码*: `modules/loader/src/jvmTest/kotlin/io/github/tonyzhye/beancount/loader/PerformanceComparisonTest.kt`  
*测试文件*: `examples/benchmark/`
