# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Full Phase 1-3 feature migration from Python Beancount 3.2.3
- KMP (Kotlin Multiplatform) support with JVM target
- Complete parser with recursive descent lexer
- 18 built-in plugins (auto_accounts, implicit_prices, etc.)
- BQL query engine with 60+ functions
- CLI tools: bean-check, bean-doctor, bean-example, bean-format, beanquery
- Performance benchmarks showing 10x+ speedup over Python
- End-to-end compatibility tests with Python Beancount
- GitHub Actions CI/CD pipeline

## [3.2.3] - 2026-06-05

### Added
- **Core Models**: All 12 directive types, Amount, Cost, Position, Inventory
- **Parser**: Lexer + recursive descent parser supporting full Beancount syntax
- **Booking**: FIFO, LIFO, HIFO, STRICT, STRICT_WITH_SIZE, NONE methods
- **Loader**: Full loading pipeline with includes, plugins, validation
- **Plugins**: 18/18 built-in plugins from Python Beancount v3
- **Query Engine**: BQL parser, compiler, and executor
- **CLI**: Complete CLI suite matching Python beancount commands
- **Tests**: 500+ tests across all modules with 80%+ coverage
- **API**: Unified `Beancount` singleton object with 60+ static methods
- **Cache**: JsonFileCache for loading optimization
- **Compatibility**: Cross-validation with Python Beancount 3.2.3

### Performance
- 10.67x average speedup compared to Python Beancount 3.2.3
- Lower memory usage for large files (>1MB)
- Throughput: ~13,000 KB/s for large ledgers

### Documentation
- Comprehensive architecture documents in `doc/`
- Performance benchmark reports (English & Chinese)
- Missing features analysis report
- CI/CD configuration guide

## [0.1.0] - Initial Release (Pre-alpha)

### Added
- Basic project structure with KMP modules
- Core data types (Decimal, Amount, Directive hierarchy)
- Simple lexer and parser
- Basic CLI skeleton

[Unreleased]: https://github.com/tonyzhye/beancount-kmp/compare/v3.2.3...HEAD
[3.2.3]: https://github.com/tonyzhye/beancount-kmp/compare/v0.1.0...v3.2.3
[0.1.0]: https://github.com/tonyzhye/beancount-kmp/releases/tag/v0.1.0
