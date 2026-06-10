# GraalVM Native Image 可行性研究报告

**报告日期**: 2026-06-09
**评估范围**: beancount-kmp 项目 CLI 模块
**GraalVM 版本参考**: 21+ (JDK 21 兼容)
**Gradle 插件版本参考**: org.graalvm.buildtools.native 0.10.4

---

## 1. 项目现状分析

### 1.1 模块结构

| 模块 | 类型 | 是否需 Native Image |
|------|------|---------------------|
| core | KMP (JVM target) | 否（库模块） |
| parser | KMP (JVM target) | 否（库模块） |
| loader | KMP (JVM target) | 否（库模块） |
| query | KMP (JVM target) | 否（库模块） |
| plugin-api | KMP (JVM target) | 否（库模块） |
| api | KMP (JVM target) | 否（库模块） |
| **cli** | **Kotlin JVM + application** | **是（唯一可执行目标）** |

本项目仅 `cli` 模块是可执行入口，其他均为库模块。因此 Native Image 构建应聚焦于 `cli` 模块。

### 1.2 关键依赖与 Native Image 兼容性

| 依赖 | 版本 | Native Image 支持 | 备注 |
|------|------|-------------------|------|
| Kotlin stdlib | 2.3.20 | ✅ 官方支持 | Kotlin 2.0+ 大幅改善 Native Image 兼容性 |
| kotlinx-datetime | 0.6.0 | ✅ 已提供 reachability metadata | 无需额外配置 |
| kotlinx-serialization-json | 1.7.3 | ✅ 已提供 reachability metadata | 编译时代码生成，反射极少 |
| clikt | 4.4.0 | ✅ 官方支持 | CLI 框架，作者主动维护 Native Image 兼容性 |
| JUnit 5 | 5.10.0 | N/A（测试-only） | 不打包进 Native Image |

所有运行时依赖均已官方支持 GraalVM Native Image，无需替换或升级。

### 1.3 反射与动态特性审计

**✅ 低风险项：**
- **PluginRegistry**：显式注册（`registry.register(plugin)`），无 `ServiceLoader`、无 `Class.forName`
- **序列化**：kotlinx-serialization 使用编译时代码生成（KSP），非运行时反射
- **无动态代理**：代码中未使用 JDK Proxy 或 CGLIB
- **无 JNI**：项目纯 Kotlin/JVM，无 JNI 调用

**⚠️ 中等风险项：**
- **kotlinx-serialization `classDiscriminator`**：`CacheDtos.kt` 使用 `classDiscriminator = "_class"` 序列化 sealed class 层次结构（`DirectiveDto` 的 17 个子类）。虽然 kotlinx-serialization-json 1.7.x 已自带 Native Image 元数据，但仍需在构建时验证多态序列化是否正常工作。
- **AnyValueSerializer**：使用 `when (value)` 运行时类型判断，不涉及反射，安全。
- **System.getProperty / System.getenv**：Native Image 中可用，但部分属性（如 `user.home`）需在运行时初始化。

**❌ 未发现高风险项：**
- 无 `Class.forName`
- 无 `ServiceLoader.load`
- 无 `MethodHandle` / `VarHandle`
- 无运行时字节码生成

---

## 2. GraalVM vs JVM 对比

### 2.1 维度对比

| 维度 | GraalVM Native Image | 传统 JVM (HotSpot) |
|------|----------------------|--------------------|
| **启动时间** | ~10–50 ms（毫秒级） | ~200–800 ms（需类加载+JIT预热） |
| **内存占用** | 低（无 JVM 运行时开销） | 高（元空间+JIT编译器+GC开销） |
| **包大小** | 单个可执行文件（15–50 MB） | 需 JRE + JAR（~100 MB+） |
| **分发** | 无需 JRE，直接运行 | 需目标环境安装 JDK/JRE |
| **峰值性能** | 略低于 JIT（缺少运行时优化） | 更高（HotSpot 自适应优化） |
| **构建时间** | 慢（2–5 分钟静态分析） | 快（秒级编译） |
| **反射/动态特性** | 需显式配置 | 原生支持 |

### 2.2 对本项目的具体价值

- **CLI 工具分发**：`bean-check`、`bean-format` 等命令可直接作为单个二进制文件分发，无需用户安装 JDK 21
- **容器化**：Docker 镜像可从 ~200 MB 缩减到 ~30 MB（基于 distroless 或 scratch）
- **CI/CD**：启动快，适合频繁调用的场景
- **性能场景匹配**：本项目 CLI 以文件批处理为主，非长时间运行服务，Native Image 的启动优势 > 峰值性能劣势

---

## 3. 需要修改的部分

### 3.1 最小修改方案（推荐）

仅对 `cli` 模块添加 Native Image 构建，库模块保持不变。

#### 步骤 1：添加 GraalVM Gradle 插件

在 `build.gradle.kts`（根项目）plugins 块添加：

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.20" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.1" apply false
    id("org.graalvm.buildtools.native") version "0.10.4" apply false
}
```

在 `modules/cli/build.gradle.kts` 应用：

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.3.20"
    application
    id("org.graalvm.buildtools.native")
}
```

#### 步骤 2：配置 Native Image 构建

在 `modules/cli/build.gradle.kts` 底部添加：

```kotlin
graalvmNative {
    binaries {
        named("main") {
            imageName.set("beancount")
            mainClass.set("io.github.tonyzhye.beancount.cli.MainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-O2")
            // 如需进一步压缩体积，可启用：
            // buildArgs.add("--enable-preview")
        }
    }
}
```

#### 步骤 3：处理 KMP 模块依赖

KMP 模块的 JVM artifact 作为普通 JAR 依赖被 Native Image 静态分析，无需特殊处理。但需确保：
- KMP 模块的 `jvmMain` 输出被正确包含在 classpath 中
- Gradle `implementation(project(":core"))` 等依赖传递正常工作

#### 步骤 4：验证/补充反射配置

kotlinx-serialization-json 1.7.3 已自带 reachability metadata，理论上无需手动配置。若构建或运行时出现序列化异常，可在 `modules/cli/src/main/resources/META-INF/native-image/` 下创建 `reflect-config.json`：

```json
[
  {
    "name": "io.github.tonyzhye.beancount.loader.cache.CacheEntryDto",
    "allDeclaredConstructors": true,
    "allDeclaredFields": true
  }
]
```

#### 步骤 5：资源文件处理

Native Image 默认包含 `src/main/resources` 下的文件。本项目 CLI 模块无复杂资源文件，无需额外配置。

#### 步骤 6：I/O 与平台相关代码

- `System.getenv()`、`System.getProperty("user.home")`：Native Image 中可用
- 文件 I/O（`java.io.File`、`java.io.InputStream`）：完全兼容
- `java.nio.file` 高级功能（如 WatchService）：本项目未使用

---

### 3.2 扩展修改方案（可选）

若需极致优化：

- **`--initialize-at-build-time`**：将纯静态的无状态类在构建时初始化，减少运行时开销
- **PGO（Profile-Guided Optimization）**：先运行 JVM 版本收集 profile，再用 profile 指导 Native Image 构建以获得更高性能
- **G1 GC 替代 Serial GC**：GraalVM 21+ 支持 G1 GC 的 Native Image 版本，适合大内存场景

---

## 4. 风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| kotlinx-serialization 多态序列化在 Native Image 中失败 | 低 | 中 | 提供 reflect-config.json；已有自动元数据 |
| KMP Gradle 插件与 GraalVM 插件冲突 | 中 | 中 | 仅对 cli 模块应用 GraalVM 插件，库模块保持 KMP |
| 构建时间显著增加 | 高 | 低 | CI 中异步构建，不影响日常开发 |
| 文件编码/路径问题 | 低 | 低 | Windows 下测试验证 |
| 峰值性能下降 | 中 | 低 | 本项目以批处理为主，启动速度更重要 |

---

## 5. 工作量估算

| 任务 | 工时 |
|------|------|
| 添加 GraalVM Gradle 插件 + 基础配置 | 0.5 h |
| 解决 KMP 依赖传递问题（如有） | 1–2 h |
| 创建/验证 reflect-config.json | 1 h |
| 构建测试 + 运行时验证 | 2 h |
| CI/CD 集成（GitHub Actions 构建 Native Image） | 2 h |
| **总计** | **6.5–7.5 h** |

---

## 6. 结论与建议

**可行性：✅ 高**

本项目非常适合 GraalVM Native Image：
- CLI 是唯一的可执行入口，目标明确
- 无复杂反射/动态特性
- 所有依赖库均已支持 Native Image
- KMP 的 JVM target 输出与普通 Kotlin/JVM 字节码无异

**建议方案：仅对 `cli` 模块启用 Native Image**
- 保持 `core`/`parser`/`loader` 等库模块为 KMP，继续发布 JVM artifact
- `cli` 模块同时提供：
  1. 传统 `java -jar` 可运行 JAR（已有）
  2. GraalVM Native Image 二进制文件（新增）

**预期收益**：
- CLI 启动时间从 ~500 ms 降至 ~30 ms
- 分发包大小从 ~100 MB（含 JRE）降至 ~20–30 MB
- 容器镜像体积减少 80%+
