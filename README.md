# Beancount JVM

Kotlin Multiplatform implementation of [Beancount](https://github.com/beancount/beancount/tree/master) (based on master branch), compatible with both Java and Kotlin.

## Project Overview

Beancount JVM is a project that migrates the Python Beancount accounting system to the JVM ecosystem. It uses the Kotlin Multiplatform (KMP) technology stack, with core logic written in `commonMain` for sharing, and the JVM platform providing production-grade implementation.

**Design Goals:**
- Semantic compatibility with Python Beancount (master branch, version 3.2.3)
- Zero runtime dependencies (core library)
- Kotlin/Java bilingual API compatibility
- High-performance parsing for large ledger files (MB-level)

## Tech Stack

- **Language**: Kotlin (primary) + Java (compatibility layer)
- **Build Tool**: Gradle + Kotlin Multiplatform Plugin
- **JDK Version**: 21
- **Test Framework**: JUnit 5 (JVM) / kotlin-test (Common)
- **Date Library**: kotlinx-datetime (only third-party dependency)
- **CLI Framework**: Clikt

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         CLI Layer                            │
│                    (bean-check command)                      │
├─────────────────────────────────────────────────────────────┤
│                      Loader Layer                            │
│         loadFile() / loadString()                            │
│  Parse → Sort → Booking → Transformations → Validation       │
├──────────────┬──────────────┬───────────────────────────────┤
│    Parser    │   Plugins    │         Core                  │
│   Lexer      │   PadPlugin  │    Decimal (expect/actual)    │
│   Parser     │ BalancePlugin│    Directive (12 types)       │
│              │DocumentsPlug.│    Amount / Cost / Posting    │
│              │              │    Validation (7 functions)     │
│              │              │    Printer (format/output)     │
├──────────────┴──────────────┴───────────────────────────────┤
│                    Kotlin Multiplatform                       │
│              commonMain / jvmMain / jvmTest                  │
└─────────────────────────────────────────────────────────────┘
```

### Loading Pipeline

```
Beancount File
      │
      ▼
┌─────────────┐
│   Parser    │  ← Lexer (20+ token types) + Recursive Descent
│  (parse)    │
└──────┬──────┘
       │ List<Directive>
       ▼
┌─────────────┐
│    Sort     │  ← By date → type → line number
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Booking   │  ← Complete single missing posting per transaction
│  (balance)  │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│    Plugin   │  ← PLUGINS_PRE → user → PLUGINS_POST
│  Pipeline   │     (documents → pad → balance)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Validation  │  ← BASIC + HARDCORE (bean-check)
│  (7 checks) │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  LoadResult │  ← entries + errors + options
└─────────────┘
```

## Module Structure

```
beancount-jvm/
├── modules/
│   ├── core/          # Core models and utilities
│   │   ├── Decimal (expect/actual, JVM uses BigDecimal)
│   │   ├── 12 Directive types (Open, Transaction, Balance, ...)
│   │   ├── Amount / Cost / Posting / TxnPosting
│   │   ├── Validation (7 validation functions)
│   │   └── Printer (formatting output, Python-compatible)
│   │
│   ├── parser/        # Beancount syntax parser
│   │   ├── Lexer (20+ token types)
│   │   ├── BeancountParser (recursive descent)
│   │   └── Booking (complete incomplete postings)
│   │
│   ├── loader/        # File loading and plugin pipeline
│   │   ├── Loader (integrates Parse → Booking → Validation)
│   │   ├── Transformations (plugin dispatcher)
│   │   └── plugins/   # Built-in plugins
│   │       ├── PadPlugin
│   │       ├── BalancePlugin
│   │       └── DocumentsPlugin
│   │
│   ├── cli/           # Command-line tool (JVM-only)
│   │   └── BeanCheckCommand (bean-check command)
│   │
│   ├── plugin-api/    # Plugin API (reserved)
│   ├── query/         # Query engine (reserved)
│   └── plugin-api/    # Plugin interface definitions (reserved)
│
├── doc/               # Architecture documents and research notes
└── examples/
    └── test.bean      # Sample ledger file
```

## Implemented Features

### Core Models
- [x] `Decimal` - expect/actual design, JVM uses `BigDecimal`
- [x] 12 Directive types: Open, Close, Commodity, Pad, Balance, Transaction, Note, Event, Query, Price, Document, Custom
- [x] `Amount`, `Cost`, `Posting`, `TxnPosting`
- [x] `Options` (strongly typed, not Map<String, Any>)
- [x] `Meta` metadata mapping

### Parser
- [x] Hand-written recursive descent parser (zero dependencies)
- [x] Lexer with 20+ token types
- [x] Supports all directive types
- [x] `plugin` directive parsing
- [x] Booking logic to complete single missing postings

### Validation (7 checks)
- [x] `validateOpenClose` - Check open/close constraints
- [x] `validateActiveAccounts` - Check account reference validity
- [x] `validateCurrencyConstraints` - Check currency constraints
- [x] `validateDuplicateBalances` - Check duplicate balance assertions
- [x] `validateDuplicateCommodities` - Check duplicate commodity declarations
- [x] `validateCheckTransactionBalances` - Check transaction balance
- [x] `validateDataTypes` - Strict data type checking (HARDCORE)

### Plugin Pipeline
- [x] `runTransformations()` dispatcher
- [x] `PadPlugin` - Generate pad transactions for balance assertions
- [x] `BalancePlugin` - Validate balance assertions (with tolerance support)
- [x] `DocumentsPlugin` - Validate document paths and accounts
- [x] PluginProcessingMode: DEFAULT / RAW

### Formatting Output
- [x] `formatEntry()` - Format directive to beancount syntax
- [x] `formatError()` - Format error message (with source location and associated entry)
- [x] `formatEntries()` / `formatErrors()` - Batch formatting
- [x] Support formatting for all 12 directive types
- [x] Posting alignment (minimum 47 character width)

### CLI
- [x] `bean-check` command
- [x] `--verbose` verbose output
- [x] `--no-cache` / `--cache-filename` (reserved)
- [x] `--auto` auto-plugins (reserved)
- [x] Exit code 0/1 compatible with Python

## Build and Run

### Build all modules
```bash
./gradlew build
```

### Run tests
```bash
./gradlew test
```

### Run CLI
```bash
# Via Gradle
./gradlew :cli:run --args="ledger.bean"

# Or using built jar
./gradlew :cli:shadowJar
java -jar modules/cli/build/libs/cli-0.1.0-SNAPSHOT-all.jar ledger.bean
```

### Check ledger
```bash
./gradlew :cli:run --args="examples/example.beancount --verbose"
```

## Usage Examples

### Kotlin API
```kotlin
import io.github.tonyzhye.beancount.loader.loadFile
import io.github.tonyzhye.beancount.core.formatErrors

val result = loadFile("ledger.bean")

if (result.errors.isNotEmpty()) {
    println(formatErrors(result.errors))
} else {
    println("Loaded ${result.entries.size} entries")
}
```

### Java API
```java
import io.github.tonyzhye.beancount.loader.LoaderKt;
import io.github.tonyzhye.beancount.core.LoadResult;

LoadResult result = LoaderKt.loadFile("ledger.bean");
if (!result.getErrors().isEmpty()) {
    // handle errors
}
```

## Compatibility with Python Beancount

| Feature | Python Beancount | Beancount JVM |
|---|---|---|
| Parser | Python/C | Kotlin (recursive descent) |
| Decimal | Python `decimal.Decimal` | `expect/actual` → JVM `BigDecimal` |
| Plugin System | Python module dynamic import | Kotlin function type + built-in plugins |
| Booking | Full cost basis support | Single missing posting completion |
| Cache | pickle serialization | Reserved (Phase 2) |
| Query | SQL-like bql | Reserved (Phase 2) |

**Performance** (vs Python Beancount 3.2.3):

| Metric | Small Files | Large Files (1-10MB) |
|--------|------------|---------------------|
| Speed | 2-10x faster | 3-3.5x faster |
| Memory | Higher base overhead | 1.8-2.8x less memory |
| Throughput | 14,000-26,000 KB/s | 12,000-13,000 KB/s |

See [Performance Benchmark Report](doc/20260526-performance-benchmark-report.md) for detailed analysis.

**Test Coverage:**
- Integration tests comparing entry/error counts with Python beancount 3.2.3
- Valid/invalid ledger file validation
- Empty ledger handling

## Testing

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :core:jvmTest
./gradlew :parser:jvmTest
./gradlew :loader:jvmTest

# Run performance comparison tests
./gradlew :loader:jvmTest --tests "PerformanceComparisonTest"

# Run CLI manual test
./gradlew :cli:run --args="examples/example.beancount"
```

**Test Statistics:**
- Core: 38 tests (Validation + Printer)
- Parser: 40 tests (Lexer + Parser + Booking)
- Loader: 15 tests (Integration + Plugin + Python compatibility)

## License

GNU General Public License v2.0 only (GPL-2.0-only)

Based on Martin Blais's [Beancount](https://github.com/beancount/beancount) project.
