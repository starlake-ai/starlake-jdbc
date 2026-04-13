# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Starlake JDBC is a JDBC proxy driver that enables **branching and sandboxing** for SQL databases. It wraps any standard JDBC driver and transparently rewrites SQL to redirect operations to isolated branch schemas, allowing DDL/DML changes to be staged before applying to production.

**Maven coordinates**: `ai.starlake.jdbc:starlakejdbc`

## Build Commands

```bash
# Build and run all checks (checkstyle, PMD, SpotBugs, tests, code coverage)
./gradlew check

# Run tests only
./gradlew test

# Run a single test class
./gradlew test --tests "ai.starlake.jdbc.StarlakeSessionTest"

# Run a single test method
./gradlew test --tests "ai.starlake.jdbc.StarlakeSessionTest.testMethodName"

# Generate code coverage report (output: build/reports/jacoco/)
./gradlew jacocoTestReport

# Build shadow/fat JAR
./gradlew shadowJar

# Format code (uses Eclipse formatter, ratcheted against origin/main)
./gradlew spotlessApply
```

## Snowflake Integration Tests

Snowflake tests (`SnowFlakeTest`, `SnowFlakeReconnectTest`) require environment variables:
`SNOWFLAKE_USER`, `SNOWFLAKE_PASSWORD`, `SNOWFLAKE_ACCOUNT`, `SNOWFLAKE_WAREHOUSE`, `SNOWFLAKE_DATABASE`, `SNOWFLAKE_SCHEMA`

These can also be set as Gradle properties.

## Architecture

The driver uses the **Proxy/Decorator pattern** — each core JDBC interface has a `Starlake*` wrapper:

- **StarlakeDriver** → `java.sql.Driver` — entry point, discovers and wraps the underlying JDBC driver
- **StarlakeConnection** → `java.sql.Connection` — manages branch lifecycle (START/APPLY/DROP), DBMS-specific schema cloning (Snowflake zero-copy, Databricks shallow clone, BigQuery snapshot, generic CTAS)
- **StarlakeSession** → core state manager — tracks created/dropped schemas and tables, performs SQL rewriting via JSQLTranspiler, maintains table replacement mappings (original → branch), optional database persistence
- **StarlakeStatement / StarlakePreparedStatement / StarlakeCallableStatement** → intercept SQL execution, parse branch commands (`BRANCH START`, `BRANCH APPLY`, etc.), delegate rewritten SQL to the real driver
- **StarlakeResultSet / StarlakeVirtualResultSet** → result set wrappers; virtual variant provides in-memory results for branch commands
- **StarlakeDatabaseMetaData** → wraps metadata, integrates with JSQLTranspiler's schema model
- **DBObjects** → thread-safe tracker (ConcurrentSkipListSet) for database object states (SCHEMA, TABLE, VIEW, INDEX, SEQUENCE)

All source lives in a single package: `ai.starlake.jdbc`

## Key Dependencies

- **JSQLTranspiler** (`ai.starlake.jsqltranspiler:jsqltranspiler`) — SQL rewriting and schema management
- **JSQLParser** (`com.manticore-projects.jsqlformatter:jsqlparser`) — SQL parsing
- Both use snapshot versions when the project version is a snapshot, release versions otherwise

## Build Details

- **Java**: source/target compatibility 11, toolchain 17
- **Versioning**: derived from git tags (`major.minor.patch`), auto-increments minor for snapshots
- **Code quality**: Checkstyle, PMD, SpotBugs, JaCoCo (minimum 10% coverage, max 2048 missed lines)
- **Formatting**: Spotless with Eclipse Google Style, ratcheted against `origin/main`
- **Publishing**: Maven Central via Sonatype; release with `RELEASE=true ./gradlew publish`
- **JDBC auto-discovery**: registered via `META-INF/services/java.sql.Driver`