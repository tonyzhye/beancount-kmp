# CI/CD 配置说明

本文档说明 Beancount JVM 项目的持续集成/持续部署 (CI/CD) 配置。

## 目录

- [概述](#概述)
- [CI 工作流](#ci-工作流)
- [CD 工作流](#cd-工作流)
- [配置方法](#配置方法)
- [本地验证](#本地验证)
- [故障排除](#故障排除)

## 概述

本项目使用 **GitHub Actions** 作为 CI/CD 平台，配置位于 `.github/workflows/` 目录：

| 文件 | 用途 | 触发条件 |
|------|------|----------|
| `ci.yml` | 持续集成 | Push 到 main/develop、Pull Request |
| `cd.yml` | 持续部署 | 推送 tag (v*)、手动触发 |

### Native Image 构建

除常规 JVM 构建外，CI/CD 还支持 **GraalVM Native Image** 编译，生成无需 JRE 的独立可执行文件：

| 工作流 | Job | 平台 | 产物 |
|--------|-----|------|------|
| `ci.yml` | `native-image` | Ubuntu/Windows/macOS | `native-image-<os>` artifact |
| `cd.yml` | `publish-native-image` | Ubuntu/Windows/macOS | `beancount-native-<os>.zip` (Release 附件) |

## CI 工作流

### 测试矩阵 (`test`)

**跨平台测试**：在 Ubuntu、Windows、macOS 上运行完整测试套件。

```yaml
strategy:
  matrix:
    os: [ubuntu-latest, windows-latest, macos-latest]
    java-version: [21]
```

**执行步骤：**
1. 检出代码
2. 设置 JDK 21 (Temurin)
3. 配置 Gradle (含缓存)
4. 运行所有测试 (`./gradlew test`)
5. 生成 Kover 覆盖率报告
6. 上传覆盖率报告和测试结果作为 artifact

### 构建验证 (`build`)

在 Ubuntu 上执行完整构建，验证所有模块编译通过。

**产物：**
- JAR 文件
- CLI 分发包 (ZIP/TAR)

### 覆盖率检查 (`coverage-check`)

**功能：**
- 运行 `koverVerify` 检查覆盖率阈值（当前要求 80%+）
- 上传覆盖率数据到 Codecov

**阈值配置：** 见各模块 `build.gradle.kts` 中的 `kover { verify { ... } }`

### Python 兼容性测试 (`compatibility-test`)

**特殊要求：** 需要 Python 3.11 + beancount 3.2.3

**执行：**
```bash
pip install beancount==3.2.3
./gradlew :loader:jvmTest --tests "*CompatTest"
```

## CD 工作流

### 发布到 Maven Central (`publish`)

**触发方式：**
- 推送 `v*` 标签（如 `v3.2.3`）
- 手动触发（workflow_dispatch）

**平台选择：** 使用 **macOS** 运行器，因为 KMP 需要同时构建 JVM、JS、Native 目标。

**发布步骤：**
1. 检出代码
2. 设置 JDK 21
3. 解码 GPG 签名密钥
4. 发布到 Maven Central
5. 创建 GitHub Release（自动附带变更日志）

### CLI 分发 (`publish-cli`)

**多平台构建：** 在 Ubuntu、Windows、macOS 上分别构建 CLI 分发包。

**产物：**
- `beancount-cli-<version>.zip`
- `beancount-cli-<version>.tar`

### Native Image 构建 (`native-image` / `publish-native-image`)

**构建平台：** Ubuntu、Windows、macOS（三平台并行）。

**JDK 要求：** 使用 **GraalVM JDK**（`oracle-graalvm` distribution），非 Temurin。

**Windows 特殊要求：** GitHub Actions Windows runner 预装 Visual Studio 2022，通过 `ilammy/msvc-dev-cmd@v1` action 自动激活 MSVC 环境。Native Image 编译需要 `cl.exe` / `link.exe`。

**产物：**
- `beancount` / `beancount.exe` — 主 CLI 二进制文件
- `beanquery` / `beanquery.exe` — BQL 查询工具二进制文件
- 产物通过 `actions/upload-artifact` 上传为 CI artifact

**CD Release 产物：**
- `beancount-native-ubuntu-latest.zip`
- `beancount-native-windows-latest.zip`
- `beancount-native-macos-latest.zip`

## 配置方法

### 1. 添加 GitHub Secrets

在仓库 Settings → Secrets and variables → Actions 中添加：

| Secret 名称 | 说明 | 获取方式 |
|------------|------|----------|
| `MAVEN_CENTRAL_USERNAME` | Maven Central 用户名 | [Sonatype JIRA](https://issues.sonatype.org/) |
| `MAVEN_CENTRAL_PASSWORD` | Maven Central 密码 | 同上 |
| `SIGNING_KEY_ID` | GPG 密钥 ID | `gpg --list-keys --keyid-format short` |
| `SIGNING_PASSWORD` | GPG 密钥密码 | 创建密钥时设置 |
| `SIGNING_KEY` | GPG 私钥 (Base64) | `gpg --export-secret-keys | base64` |

### 2. 生成 GPG 密钥

```bash
# 生成密钥
gpg --full-generate-key

# 查看密钥 ID
gpg --list-keys --keyid-format short

# 导出私钥（Base64 编码）
gpg --export-secret-keys YOUR_KEY_ID | base64 > signing-key.txt
```

### 3. 配置 Gradle 发布

确保 `build.gradle.kts` 已配置 `maven-publish` 和 `signing` 插件：

```kotlin
plugins {
    id("maven-publish")
    id("signing")
}

publishing {
    repositories {
        maven {
            name = "mavenCentral"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = findProperty("mavenCentralUsername") as String? ?: ""
                password = findProperty("mavenCentralPassword") as String? ?: ""
            }
        }
    }
}

signing {
    sign(publishing.publications)
}
```

### 4. 发布新版本

**自动发布（推荐）：**
```bash
# 1. 更新版本号
git tag -a v3.2.4 -m "Release version 3.2.4"
git push origin v3.2.4
```

**手动发布：**
1. 进入 Actions 标签页
2. 选择 "CD - Publish to Maven Central"
3. 点击 "Run workflow"
4. 输入版本号

## 本地验证

### 验证 CI 流程

```bash
# 本地运行所有测试
./gradlew test

# 本地运行覆盖率检查
./gradlew koverVerify

# 本地构建分发包
./gradlew :cli:distZip
```

### 验证 Native Image 构建

```bash
# 安装 GraalVM JDK（通过 SDKMAN 或 scoop）
sdk install java 21.0.5-graalce

# 或在 Windows 上通过 scoop
scoop install graalvm21

# 编译 Native Image（Linux/macOS）
./gradlew :cli:nativeCompile :cli:nativeBeanqueryCompile

# Windows 需要先初始化 MSVC 环境
cmd //c "C:/Program^ Files/Microsoft^ Visual^ Studio/2022/Community/VC/Auxiliary/Build/vcvarsall.bat x64 && gradlew :cli:nativeCompile :cli:nativeBeanqueryCompile"

# 运行编译产物
./modules/cli/build/native/nativeCompile/beancount bean-check examples/example.beancount
./modules/cli/build/native/nativeBeanqueryCompile/beanquery bean-query examples/example.beancount "SELECT date, narration FROM entries"
```

### 验证发布配置

```bash
# 发布到本地 Maven 仓库
./gradlew publishToMavenLocal

# 检查生成的 POM 文件
ls ~/.m2/repository/io/github/tonyzhye/beancount/
```

## 故障排除

### 问题：测试在 Windows 上失败

**原因：** 文件路径分隔符、换行符差异。

**解决：**
- 使用 `File.separator` 而非硬编码 `/`
- 使用 `System.lineSeparator()` 处理换行
- 已在 `EndToEndCompatTest` 中处理 CRLF 差异

### 问题：Maven Central 发布失败

**常见原因：**
1. GPG 签名错误 → 检查 `SIGNING_KEY` 是否正确编码
2. 401 未授权 → 检查 Maven Central 凭据
3. 网络超时 → 重试或检查代理设置

### 问题：覆盖率检查失败

```bash
# 查看详细覆盖率报告
./gradlew koverHtmlReport
open modules/core/build/reports/kover/html/index.html
```

### 问题：Native Image 编译失败（Windows）

**错误：** `native-image.cmd wasn't found` 或 `cl.exe wasn't found`

**原因：** Windows 上 GraalVM Native Image 需要 Visual Studio C++ 工具链。

**解决：**
1. 确保已安装 GraalVM JDK（`java -version` 应显示 GraalVM）
2. 在 Git Bash 中，先运行 MSVC 环境初始化脚本：
   ```bash
   cmd //c "C:/Program^ Files/Microsoft^ Visual^ Studio/2022/Community/VC/Auxiliary/Build/vcvarsall.bat x64"
   ```
3. 或在 PowerShell / CMD 中直接编译（已自动激活 MSVC）

### 问题：Native Image 编译失败（Gradle Configuration Cache）

**错误：** `error writing value of type 'DefaultLegacyConfiguration'`

**原因：** GraalVM Build Tools 插件与 Gradle Configuration Cache 不兼容。

**解决：** 编译时添加 `--no-configuration-cache` 参数：
```bash
./gradlew :cli:nativeCompile --no-configuration-cache
```

CI/CD 工作流中已默认包含此参数。

### 问题：Gradle 缓存导致构建不一致

```bash
# 清理 Gradle 缓存
./gradlew clean
rm -rf ~/.gradle/caches/
```

## 最佳实践

1. **分支保护**：为 `main` 分支启用分支保护规则，要求 CI 通过后才能合并
2. **标签规范**：使用语义化版本号（`vMAJOR.MINOR.PATCH`）
3. **变更日志**：使用 `generate_release_notes: true` 自动生成 Release Notes
4. **缓存策略**：利用 Gradle 构建缓存加速 CI（已配置 `gradle-home-cache-cleanup`）
5. **Artifact 保留**：设置 artifact 保留策略，避免存储费用过高

## 相关文档

- [GitHub Actions 文档](https://docs.github.com/en/actions)
- [Gradle 构建缓存](https://docs.gradle.org/current/userguide/build_cache.html)
- [Maven Central 发布指南](https://central.sonatype.org/publish/publish-guide/)
- [Kotlin Multiplatform 发布](https://kotlinlang.org/docs/multiplatform-publish-lib.html)

---

*最后更新：2026-06-09*
