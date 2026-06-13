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
| `cd.yml` | 发布 Release + Maven Central | 推送 tag (v*)、手动触发 |

### Native Image 构建

除常规 JVM 构建外，CI/CD 还支持 **GraalVM Native Image** 编译，生成无需 JRE 的独立可执行文件：

| 工作流 | Job | 平台 | 产物 |
|--------|-----|------|------|
| `ci.yml` | `native-image` | Ubuntu/Windows/macOS | `native-image-<os>` artifact |
| `cd.yml` | `build-cli` | `ubuntu-latest` | `beancount-cli-{version}-jar.zip` (Release 附件) |
| `cd.yml` | `build-native-image` | Ubuntu/Windows/macOS | `beancount-native-{version}-{os}.zip` (Release 附件) |
| `cd.yml` | `publish-maven` | `ubuntu-latest` | 发布到 Maven Central |

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

CD 工作流包含 5 个顺序/并行 job，依赖关系如下：

```
validate ───┬──→ build-cli ───┐
            │                  ├──→ create-release
            ├──→ build-native ─┘
            └──→ publish-maven
```

### 1. 验证 (`validate`)

**前置条件：** 只有测试全部通过后，后续发布 job 才会执行。

**执行内容：**
1. 运行完整测试套件 (`./gradlew test`)
2. 验证覆盖率阈值 (`./gradlew koverVerify`)
3. 运行 Python 兼容性测试

### 2. CLI 分发构建 (`build-cli`)

**平台选择：** `ubuntu-latest`（JVM 应用跨平台，无需按 OS 重复构建）。

**产物：**
- `cli-distribution` artifact（单个 ZIP）
- 最终通过 `create-release` job 附加到 GitHub Release

### 3. Native Image 构建 (`build-native-image`)

**多平台并行构建：** Ubuntu、Windows、macOS 同时构建。Native Image 是平台相关二进制，必须分平台。

**JDK 要求：** 使用 **GraalVM JDK**（`graalvm` distribution）。

**Windows 特殊要求：** 通过 `ilammy/msvc-dev-cmd@v1` action 自动激活 MSVC 环境。

**产物：**
- `native-<os>` artifact
- 最终通过 `create-release` job 附加到 GitHub Release

### 4. Maven Central 发布 (`publish-maven`)

**平台选择：** `ubuntu-latest`（项目当前仅 JVM 目标，无需 macOS）。

**版本号自动提取：**
- Tag 触发：从 `refs/tags/v3.2.4` 提取 `3.2.4`
- 手动触发：使用输入的版本号
- 通过 `-Pversion=X.Y.Z` 传递给 Gradle

**发布步骤：**
1. 检出代码
2. 提取版本号
3. 解码 GPG 签名密钥
4. 发布到 Maven Central（`./gradlew publishToMavenCentral -Pversion=X.Y.Z`）

### 5. GitHub Release 创建 (`create-release`)

**触发条件：** `build-cli`、`build-native-image`、`publish-maven` 全部成功后执行。

**功能：**
1. 下载所有 artifact（1 个 CLI + 3 个 Native Image）
2. 使用 `gh release create` CLI 创建 Release（替代已归档的 `softprops/action-gh-release`）
3. 自动生成 Release Notes（`--generate-notes`）
4. 自动更新 `CHANGELOG.md` 并提交回仓库

**Release 附件：**
- CLI 分发包（1 个跨平台 JVM zip）：`beancount-cli-{version}-jar.zip`
- Native Image 包（3 平台）：`beancount-native-{version}-{os}.zip`

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

### 3. Gradle 发布配置

发布配置已自动注入所有 KMP 子模块（`cli` 模块除外）：

- `maven-publish` 和 `signing` 插件在 `build.gradle.kts` 的 `subprojects` 块中自动应用
- POM 信息（许可证、开发者、SCM）已统一配置
- 版本号支持通过 `-Pversion=X.Y.Z` 动态覆盖

**本地验证发布配置：**
```bash
./gradlew publishToMavenLocal -Pversion=3.2.4
```

### 4. 发布新版本

**版本号管理：**
- `build.gradle.kts` 中默认版本为 `3.2.3-SNAPSHOT`
- 发布时通过 `-Pversion=X.Y.Z` 从 tag 或手动输入覆盖
- **无需手动修改** `build.gradle.kts` 中的版本号

**自动发布（推荐）：**
```bash
# 1. 更新 CHANGELOG.md（在 [Unreleased] 节添加变更内容）
# 2. 提交并推送
git add CHANGELOG.md
git commit -m "docs: prepare release v3.2.4"
git push origin main

# 3. 打标签并推送（版本号从 tag 自动提取）
git tag -a v3.2.4 -m "Release version 3.2.4"
git push origin v3.2.4
```

推送 tag 后，CD 工作流自动执行：
1. 运行验证测试
2. 并行构建 CLI 分发包（三平台）
3. 并行构建 Native Image（三平台）
4. 发布到 Maven Central
5. 创建 GitHub Release（自动附带 Release Notes 和全部产物）
6. 自动更新 `CHANGELOG.md` 并提交回仓库

**手动发布：**
1. 进入 Actions 标签页
2. 选择 "CD - Publish Release"
3. 点击 "Run workflow"
4. 输入版本号（如 `3.2.4`）
5. 工作流执行完整发布流程

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
4. **缓存策略**：利用 Gradle 构建缓存加速 CI（已配置 `setup-gradle@v4`）
5. **Artifact 保留**：设置 artifact 保留策略，避免存储费用过高

## 相关文档

- [GitHub Actions 文档](https://docs.github.com/en/actions)
- [Gradle 构建缓存](https://docs.gradle.org/current/userguide/build_cache.html)
- [Maven Central 发布指南](https://central.sonatype.org/publish/publish-guide/)
- [Kotlin Multiplatform 发布](https://kotlinlang.org/docs/multiplatform-publish-lib.html)

---

*最后更新：2026-06-10*
