# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [3.2.3] - 2026-06-13

### Added
- **Core**: Full JVM implementation of Beancount core models (Amount, Position, Inventory, all directive types)
- **Parser**: Complete Beancount syntax parser with recursive-descent lexer
- **Loader**: File loading pipeline with includes, plugins, validation, and JSON cache
- **Booking**: Full-cost booking methods including FIFO, LIFO, HIFO, STRICT, STRICT_WITH_SIZE, and NONE
- **Plugins**: 18 built-in plugins ported from Python Beancount 3.2.3
- **Query Engine**: BQL (Beancount Query Language) parser, compiler, and executor
- **API**: Unified `Beancount` entry point exposing parser, loader, price, and query APIs
- **CLI**: Complete command-line tools — `bean-check`, `bean-doctor`, `bean-example`, `bean-format`, and `beanquery`
- **Compatibility**: End-to-end validation against Python Beancount 3.2.3 for parser output, balances, and BQL results
- **Performance**: Benchmark suite showing multi-fold speedup over Python Beancount on large ledgers
- **Native Image**: GraalVM Native Image build support for `beancount` and `beanquery` CLI binaries
- **CI/CD**: GitHub Actions workflows for testing, coverage, compatibility verification, native-image builds, Maven Central publishing, and automated GitHub Releases

[Unreleased]: https://github.com/tonyzhye/beancount-kmp/compare/v3.2.3...HEAD
[3.2.3]: https://github.com/tonyzhye/beancount-kmp/releases/tag/v3.2.3
