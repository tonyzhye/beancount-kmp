# AGENTS.md - Beancount JVM

## Project Goal
Migrate [beancount v3](https://github.com/beancount/beancount/tree/v3) to the JVM, implemented in Kotlin, with interfaces compatible with both Java and Kotlin.

## Build System
- **Build Tool**: Gradle + Kotlin Multiplatform Plugin
- **Language**: Kotlin (primary) + Java (compatibility layer)
- **Test Framework**: JUnit 5
- **JDK Version**: 21

## Common Commands

```bash
# Build all modules
./gradlew build

# Run all tests
./gradlew test

# Run JVM tests only
./gradlew jvmTest

# Run a single test class
./gradlew test --tests "ClassName"

# Publish to local Maven repository (includes KMP metadata + JVM artifacts)
./gradlew publishToMavenLocal

# Format code (if ktlint/spotless configured)
./gradlew spotlessApply

# Clean build
./gradlew clean
```

## Project Structure

```
beancount-jvm/
├── settings.gradle.kts     # Project settings, auto-discovers modules
├── build.gradle.kts        # Root build script, defines plugins and versions
├── gradle.properties
├── doc/                    # Research and architecture documents
├── modules/                # All code modules
│   ├── core/               # Core models and parser (KMP module)
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/kotlin/    # Cross-platform shared code
│   │       ├── jvmMain/kotlin/       # JVM implementation + Java-compatible API
│   │       └── jvmTest/kotlin/
│   ├── parser/             # Beancount syntax parser (KMP module)
│   │   └── src/
│   │       ├── commonMain/kotlin/
│   │       └── jvmMain/kotlin/
│   ├── loader/             # File loading and validation (KMP module)
│   │   └── src/
│   │       ├── commonMain/kotlin/
│   │       └── jvmMain/kotlin/
│   ├── query/              # Query engine (KMP module)
│   │   └── src/
│   │       ├── commonMain/kotlin/
│   │       └── jvmMain/kotlin/
│   ├── plugin-api/         # Plugin API with Java-compatible interfaces
│   │   └── src/
│   │       ├── commonMain/kotlin/
│   │       └── jvmMain/kotlin/
│   └── cli/                # Command-line tool (JVM-only)
│       └── src/
│           └── jvmMain/kotlin/
└── LICENSE                 # GPL-2.0-only license
```

## Kotlin/Java Interoperability Guidelines

- **API Design**: Use Kotlin for public interfaces, but ensure annotations like `@JvmStatic`, `@JvmOverloads` make them Java-friendly
- **Data Classes**: Use Kotlin `data class`, but provide Builder pattern or static factory methods for Java
- **Collections**: Public APIs return `java.util.List/Map` instead of Kotlin collection types
- **Null Safety**: Use `@Nullable`/`@NotNull` (JSR-305) for public API parameters and return values
- **Package Names**: Use your own domain namespace, e.g., `io.github.tonyzhye.beancount.*` (do not use `org.beancount.*` to avoid implying official affiliation)

## Development Conventions

- **Source Locations**: `src/commonMain/kotlin/` (cross-platform), `src/jvmMain/kotlin/` (JVM-specific + Java compatibility code)
- **Test Locations**: `src/jvmTest/kotlin/`
- **Resources**: `src/commonMain/resources/`, `src/jvmMain/resources/`
- **Package Naming**: All lowercase, reverse domain: `io.github.tonyzhye.beancount.parser`
- **Module Dependencies**: Avoid circular dependencies, `core` does not depend on other business modules

## Testing Standards

### Test Framework Strategy

KMP projects use **two test frameworks** depending on the source set:

- **`commonTest/`** — Use `kotlin-test` (`kotlin.test.Test`, `kotlin.test.assertEquals`)
  - Cross-platform, works in all KMP targets
  - Keep tests here simple: pure logic, no JVM-specific APIs
- **`jvmTest/`** — Use **JUnit 5** (`org.junit.jupiter.api.Test`)
  - Full JUnit 5 feature set: `@ParameterizedTest`, `@Nested`, Extension API
  - Java-compatible: Java developers can read and run these tests
  - Use for complex JVM-specific tests (file I/O, performance, large ledger parsing)

### Test Conventions

- Test class naming: `*Test.kt`
- Test method naming: Use backticks for descriptive names: `` `should parse transaction` ``
- Data-driven tests: Use `@ParameterizedTest` (JUnit 5 in `jvmTest/`) or parameterized test support from `kotlin-test`
- Integration tests: Place in `src/jvmTest/kotlin/` (requires JVM runtime)

### JVM Test Dependencies

```kotlin
// In module's build.gradle.kts
jvmTest.dependencies {
    implementation("org.junit.jupiter:junit-jupiter:5.10.0")
    implementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
}
```

## Key Constraints

- **Python Compatibility**: Maintain semantic consistency with the original beancount Python API as much as possible
- **Performance**: Parser needs to handle large ledger files (MB level), pay attention to memory and speed
- **No External Runtime Dependencies**: Core library should minimize third-party dependencies
- **KMP-first for commonMain**: When `commonMain` requires third-party dependencies, prioritize finding KMP-compatible libraries at [https://klibs.io/](https://klibs.io/). JVM-only libraries are only allowed in `jvmMain` source sets.

## License

This project is licensed under the **GNU General Public License v2.0 only (GPL-2.0-only)**.

- **License File**: See `LICENSE` in the project root
- **Original Project**: Based on [Beancount](https://github.com/beancount/beancount) by Martin Blais
- **Original License**: GPL-2.0-only (NOT "GPL-2.0-or-later")

### License Requirements

- All source files MUST include the GPL-2.0-only license header
- The license MUST NOT be changed to MIT, Apache, BSD, or GPL-3.0
- Derivative works MUST be distributed under GPL-2.0-only

### Source File Header Template

```kotlin
/*
 * Beancount JVM - A JVM implementation of Beancount
 * Copyright (C) 2026  Beancount JVM Contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <https://www.gnu.org/licenses/>.
 *
 * Based on Beancount by Martin Blais
 * Original project: https://github.com/beancount/beancount
 */
```

## KMP Publishing Notes

This is a Kotlin Multiplatform (KMP) library. Publishing produces:

```
org.beancount.jvm:core:1.0.0              # KMP metadata (used by KMP projects)
org.beancount.jvm:core-jvm:1.0.0          # JVM artifact (used by plain JVM projects)
```

### For KMP Consumers

```kotlin
// In commonMain (KMP project)
commonMain.dependencies {
    implementation("org.beancount.jvm:core:1.0.0")
}
```

### For Plain JVM Consumers (Gradle)

```kotlin
// JVM-only project
implementation("org.beancount.jvm:core:1.0.0")  // Automatically resolves core-jvm
```

### For Plain JVM Consumers (Maven)

```xml
<!-- Must use the -jvm artifact explicitly -->
<dependency>
    <groupId>org.beancount.jvm</groupId>
    <artifactId>core-jvm</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Reference Resources

- [Beancount Docs](https://beancount.github.io/docs/)
- [Beancount v3 GitHub](https://github.com/beancount/beancount/tree/v3)
- [Kotlin Docs](https://kotlinlang.org/docs/)
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)

## AI Assistant Working Rules

### 1. Think Before Coding
**Don't assume. Don't hide confusion. Surface tradeoffs.**
Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First
**Minimum code that solves the problem. Nothing speculative.**
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.
Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes
**Touch only what you must. Clean up only your own mess.**
When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.
When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.
The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution
**Define success criteria. Loop until verified.**
Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"
For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```
Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

### 5. Git Commit Rules
- **Do not auto-commit** - Never proactively execute `git commit` or `git push`
- **Do not ask about committing** - Do not proactively ask the user if they want to commit code
- **Wait for explicit request** - Only execute commit operations when the user explicitly requests "commit"
- User explicitly said: "I will ask when needed"
