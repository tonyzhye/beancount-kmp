# Beancount JVM

[![CI](https://github.com/tonyzhye/beancount-kmp/actions/workflows/ci.yml/badge.svg)](https://github.com/tonyzhye/beancount-kmp/actions/workflows/ci.yml)
[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/gpl-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![JDK](https://img.shields.io/badge/JDK-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![klibs.io](https://img.shields.io/badge/klibs.io-Beancount%20JVM-blue?logo=kotlin)](https://klibs.io/projects/tonyzhye/beancount-kmp)

> Kotlin Multiplatform implementation of [Beancount](https://github.com/beancount/beancount) v3.2.3, compatible with both Java and Kotlin.

## Overview

Beancount JVM is a complete migration of Python Beancount accounting system to the JVM ecosystem using Kotlin Multiplatform (KMP). It maintains semantic compatibility with Python Beancount 3.2.3 while delivering **up to 2.2x performance improvement on large ledgers**.

### Key Features

- **Full Compatibility**: Parses and processes all valid Beancount v3.2.3 files
- **High Performance**: Up to 2.2x faster than Python on large ledgers (see [benchmarks](PERFORMANCE_REPORT.md))
- **Zero Runtime Dependencies**: Core library only needs kotlinx-datetime
- **Bilingual API**: Kotlin-first with Java-compatible static methods via `@JvmStatic`
- **Complete CLI**: All Python beancount commands (bean-check, bean-doctor, bean-format, etc.)
- **Query Engine**: Full BQL (Beancount Query Language) implementation

## Quick Start

### Maven Coordinates

Published to **Maven Central** and discoverable on **[klibs.io](https://klibs.io/projects/tonyzhye/beancount-kmp)**.

**For KMP projects (commonMain):**
```kotlin
// build.gradle.kts (KMP project)
commonMain.dependencies {
    implementation("io.github.tonyzhye.beancount:core:3.2.3")
}
```

**For JVM-only projects:**
```kotlin
// build.gradle.kts (JVM project)
dependencies {
    implementation("io.github.tonyzhye.beancount:core-jvm:3.2.3")
}
```

**For Maven (JVM-only):**
```xml
<dependency>
    <groupId>io.github.tonyzhye.beancount</groupId>
    <artifactId>core-jvm</artifactId>
    <version>3.2.3</version>
</dependency>
```

### Kotlin Usage

```kotlin
import io.github.tonyzhye.beancount.api.Beancount as bn

// Load a beancount file
val result = bn.loadFile("ledger.beancount")

// Check for errors
if (result.errors.isNotEmpty()) {
    println(bn.formatErrors(result.errors))
    return
}

// Query all transactions
val transactions = bn.getTransactions(result.entries)

// Get all accounts
val accounts = bn.getAccounts(result.entries)

// Build price map
val priceMap = bn.buildPriceMap(result.entries)

// Format entries back to beancount syntax
println(bn.formatEntries(result.entries))
```

### Java Usage

```java
import io.github.tonyzhye.beancount.api.Beancount;
import io.github.tonyzhye.beancount.core.LoadResult;

// Load a beancount file
LoadResult result = Beancount.loadFile("ledger.beancount");

// Check for errors
if (!result.getErrors().isEmpty()) {
    System.out.println(Beancount.formatErrors(result.getErrors()));
    return;
}

// Get all accounts
var accounts = Beancount.getAccounts(result.getEntries());
System.out.println("Accounts: " + accounts);
```

### CLI Usage

```bash
# Check a ledger file
beancount check ledger.beancount

# With verbose output
beancount check ledger.beancount --verbose

# Format/beautify a file
beancount format ledger.beancount -o formatted.beancount

# Run BQL queries
beancount query ledger.beancount "SELECT date, narration, position WHERE account ~ 'Expenses'"

# Show file dependencies
beancount deps ledger.beancount

# Get help for any command
beancount check --help
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         CLI Layer                            │
│   bean-check | bean-doctor | bean-format | beanquery        │
├─────────────────────────────────────────────────────────────┤
│                      API Layer                               │
│              Beancount singleton (60+ methods)              │
├─────────────────────────────────────────────────────────────┤
│                      Query Engine                            │
│   BQL Parser → Compiler → Executor (60+ functions)          │
├─────────────────────────────────────────────────────────────┤
│                      Loader Layer                            │
│   Parse → Sort → Booking → Transformations → Validation     │
├──────────────┬──────────────┬───────────────────────────────┤
│    Parser    │   Plugins    │         Core                  │
│   Lexer      │  18 Built-in │    12 Directive Types        │
│   Parser     │   Plugins    │    Amount / Cost / Posting   │
│   Booking    │              │    Inventory / Validation    │
└──────────────┴──────────────┴───────────────────────────────┘
```

### Module Dependencies

```
core (foundation)
  ↑
parser (depends on core)
  ↑
loader (depends on parser, core)
  ↑
api (depends on loader, parser, query, core, plugin-api)
  ↑
cli (depends on api, loader, query)
```

## Feature Completeness

### Core Features (100%)
- [x] All 12 directive types (Open, Transaction, Balance, etc.)
- [x] Full booking methods (FIFO, LIFO, HIFO, STRICT, etc.)
- [x] Complete validation suite (7 validators)
- [x] Inventory management with cost basis tracking
- [x] Display context and number formatting

### Parser (95%)
- [x] Recursive descent parser with 20+ token types
- [x] All directive syntax support
- [x] Metadata and tags/links handling
- [x] Include file resolution
- [x] Plugin directive parsing
- [ ] C-extension performance (not needed - Kotlin is faster)

### Plugins (100%)
- [x] 18/18 built-in plugins implemented
  - auto_accounts, implicit_prices, currency_accounts
  - leafonly, unique_prices, coherent_cost
  - check_closing, check_commodity, noduplicates
  - nounused, onecommodity, sellgains
  - check_drained, close_tree, pedantic
  - auto, check_average_cost, commodity_attr

### Query Engine (90%)
- [x] BQL parser and compiler
- [x] 60+ built-in functions
- [x] JOIN, GROUP BY, ORDER BY, PIVOT BY
- [x] Time-slicing (YEAR, QUARTER, MONTH, WEEK, DAY)
- [x] Multiple output formats (TEXT, CSV, HTML, BEANCOUNT)

### CLI Tools (90%)
- [x] bean-check - Validate ledger files
- [x] bean-doctor - Diagnostic tools
- [x] bean-example - Generate example ledgers
- [x] bean-format - Format/beautify files
- [x] beanquery - BQL query interface
- [x] treeify - Tree visualization
- [x] bean-deps - Dependency analysis
- [ ] bean-report (excluded per design)
- [ ] bean-price (excluded per design)

All 18 built-in plugins and the complete BQL query engine are implemented.

## Python Compatibility

Compared against Python Beancount 3.2.3:

- **98 test classes** — all PASS
- **9 end-to-end files** (starter, basic, example, 50KB–10MB) — zero errors on both Python and Kotlin
- **All 18 built-in plugins** produce identical results

*Full comparison report: [CONSISTENCY_REPORT.md](CONSISTENCY_REPORT.md)*

## Performance

Compared to Python Beancount 3.2.3 (load + book + validate):

| File Size | Python (ms) | Kotlin (ms) | Speedup |
|-----------|-------------|-------------|---------|
| Example (340 KB) | 87 | 77 | **1.1x** |
| 1 MB | 161 | 154 | **1.0x** |
| 5 MB | 1,467 | 781 | **1.9x** |
| 10 MB | 3,423 | 1,543 | **2.2x** |

Kotlin scales better as file size grows — from roughly equivalent on small files to **2.2x faster on 10 MB ledgers**.

*Full benchmark report: [PERFORMANCE_REPORT.md](PERFORMANCE_REPORT.md)*

## Building from Source

### Requirements
- JDK 21+
- Gradle 9.5+ (wrapper included)

### Build
```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Generate coverage report
./gradlew koverHtmlReport

# Build CLI distribution
./gradlew :cli:distZip

# Publish to local Maven
./gradlew publishToMavenLocal
```

### IDE Setup
Recommended: IntelliJ IDEA with Kotlin plugin

1. Open project root (where `settings.gradle.kts` is)
2. Gradle will auto-sync and download dependencies
3. Run tests via IDE or `./gradlew test`

## Testing

```bash
# All tests
./gradlew test

# Specific module
./gradlew :core:jvmTest
./gradlew :parser:jvmTest
./gradlew :loader:jvmTest
./gradlew :query:jvmTest

# Coverage verification
./gradlew koverVerify

# Compatibility tests (requires Python + beancount 3.2.3)
./gradlew :loader:jvmTest --tests "*CompatTest"
```

**Coverage Status:**
| Module | Coverage |
|--------|----------|
| core | 81.3% |
| parser | 81.6% |
| loader | 80.1% |
| query | 81.6% |
| api | 94.3% |
| plugin-api | 96.2% |

## Documentation

- [Architecture Overview](AGENTS.md) - Project structure and conventions
- [CI/CD Guide](.github/CI_CD_GUIDE.md) - GitHub Actions setup
- [Performance Report](PERFORMANCE_REPORT.md) - Detailed benchmarks
- [Consistency Report](CONSISTENCY_REPORT.md) - Python parity verification
- [Changelog](CHANGELOG.md) - Version history

## Contributing

Contributions are welcome! Please read our [Contributing Guide](CONTRIBUTING.md) (if available) and ensure your changes:

1. Pass all tests (`./gradlew test`)
2. Maintain 80%+ code coverage
3. Follow existing code style
4. Include appropriate tests

## License

This project is licensed under the **GNU General Public License v2.0 only (GPL-2.0-only)**.

Copyright (C) 2026 Beancount JVM Contributors

Based on [Beancount](https://github.com/beancount/beancount) by Martin Blais, licensed under GPL-2.0-only.

## Acknowledgments

- [Martin Blais](https://github.com/blais) - Creator of Beancount
- [Beancount Community](https://beancount.github.io/docs/) - Documentation and ecosystem
- [Kotlin Team](https://kotlinlang.org/) - Excellent multiplatform support

## Related Projects

- [beancount](https://github.com/beancount/beancount) - Original Python implementation
- [fava](https://github.com/beancount/fava) - Web interface for Beancount
- [beancount-language-server](https://github.com/polarmutex/beancount-language-server) - LSP implementation

---

*This is an independent project, not affiliated with the official Beancount project.*
