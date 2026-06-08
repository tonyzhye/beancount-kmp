# Test Coverage Report

Generated: 2026-06-03

## Module Coverage Summary

| Module | Line Coverage | Change | Target |
|--------|--------------|--------|--------|
| query | 68.5% | +13.5% | 70% |
| api | 77.3% | +42.1% | 60% |
| plugin-api | 96.2% | +37.7% | 70% |

## Detailed Breakdown

### query (68.5% line coverage)
- **parser**: 91.1% - BQL parser comprehensively tested
- **compiler**: 82.4% - EvalNode operations covered
- **executor**: 78.1% - Query execution paths tested
- **functions**: 62.9% - FunctionRegistry functions tested
- **tables**: 49.0% - Table implementations need more coverage

New test files:
- `FunctionRegistryTest.kt` - 17 tests for all registered functions
- `EvalNodeTest.kt` - 32 tests for binary/unary/IN/BETWEEN operations
- `BqlParserTest.kt` - 43 tests for parser grammar coverage
- `QueryFormatterExtendedTest.kt` - 15 tests for formatting edge cases

### api (77.3% line coverage)
- All 63 public methods covered
- Loading, formatting, conversion, and query APIs tested

New test file:
- `BeancountApiExtendedTest.kt` - 30 tests for facade methods

### plugin-api (96.2% line coverage)
- Base classes, pipeline, registry, and DSL fully tested

New test file:
- `PluginApiExtendedTest.kt` - 40 tests for plugin system

## Notes
- Overall project coverage improved significantly
- query tables package remains the largest gap at 49%
- All builds pass successfully
