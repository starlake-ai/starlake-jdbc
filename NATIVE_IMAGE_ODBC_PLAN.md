# Starlake JDBC to ODBC via GraalVM Native Image

## Architecture Overview

```
Windows Application (Excel, PowerBI, etc.)
    ↓
ODBC Driver Manager (Windows)
    ↓
Starlake ODBC Driver (C/C++ DLL)
    ↓
GraalVM Native Image (Compiled Java → Native Code)
    ↓
Underlying JDBC Drivers (also compiled to native)
    ↓
Database
```

## Phase 1: GraalVM Native Image Compilation

### 1.1 Challenges Identified

**Reflection Usage** (CRITICAL):
- `StarlakeDriver.java` lines 121-122, 168-169: Dynamic driver loading via `Class.forName()`
- `DriverManager.getDrivers()`: Enumerates registered drivers
- JSQLParser: Uses reflection internally for parsing

**Solutions**:
1. Pre-register known drivers at build time
2. Create reflection configuration for all JDBC drivers
3. Use GraalVM's `--initialize-at-build-time` for static initialization

### 1.2 Dependencies to Check
- ✅ JSQLParser: Pure Java, should work with proper reflection config
- ✅ JSQLTranspiler: Pure Java, should work with proper reflection config
- ⚠️ H2, DuckDB, Snowflake drivers: Need individual testing

### 1.3 Required Configuration Files

**reflect-config.json**: Register all JDBC driver classes
**resource-config.json**: Include META-INF/services files
**jni-config.json**: For ODBC C wrapper integration
**native-image.properties**: Build-time configuration

## Phase 2: Native Image Build Setup

### 2.1 Add GraalVM Native Image Plugin

Add to `build.gradle`:
```gradle
plugins {
    id 'org.graalvm.buildtools.native' version '0.10.3'
}

graalvmNative {
    binaries {
        main {
            imageName = 'starlake-jdbc-native'
            mainClass = 'ai.starlake.jdbc.NativeImageEntryPoint'
            buildArgs.add('--no-fallback')
            buildArgs.add('--initialize-at-build-time=ai.starlake.jdbc')
            buildArgs.add('-H:+ReportExceptionStackTraces')
        }
    }
}
```

### 2.2 Create Entry Point for Native Image

Need a main class that:
- Registers all known JDBC drivers
- Exposes C-callable functions via GraalVM's C API
- Handles connection pooling and lifecycle

## Phase 3: C ODBC Wrapper Design

### 3.1 ODBC Functions to Implement (Minimum)

**Core Functions** (~20 required):
- SQLAllocHandle / SQLFreeHandle
- SQLConnect / SQLDisconnect
- SQLDriverConnect
- SQLPrepare / SQLExecute / SQLExecDirect
- SQLFetch / SQLGetData
- SQLNumResultCols / SQLDescribeCol
- SQLRowCount
- SQLGetDiagRec (error handling)

### 3.2 C Wrapper Architecture

```c
// starlake_odbc.c
#include <sql.h>
#include <sqlext.h>
#include <graalvm/llvm/polyglot.h>

// Call into native image
SQLRETURN SQLConnect(SQLHDBC hdbc, ...) {
    // Marshal ODBC parameters
    // Call Java native image function
    void* result = polyglot_invoke(
        native_image_handle,
        "starlake_connect",
        connection_string
    );
    // Marshal result back to ODBC
    return SQL_SUCCESS;
}
```

### 3.3 Build System for ODBC DLL

Use CMake or Visual Studio:
```cmake
# CMakeLists.txt
add_library(starlake_odbc SHARED
    starlake_odbc.c
    starlake_odbc.def  # Export definitions
)
target_link_libraries(starlake_odbc
    starlake-jdbc-native.lib  # Native image library
    odbc32.lib
)
```

## Phase 4: Implementation Steps

### Step 1: Prepare Java Code for Native Image
- [ ] Remove dynamic Class.forName() - use static registration
- [ ] Create driver registry with all supported drivers
- [ ] Add GraalVM substitutions for problematic code
- [ ] Create C-callable API layer

### Step 2: Generate Native Image Configs
- [ ] Run with tracing agent: `java -agentlib:native-image-agent=config-output-dir=...`
- [ ] Test with H2, DuckDB, Snowflake
- [ ] Refine reflection configs

### Step 3: Build Native Image
- [ ] Install GraalVM (Windows version)
- [ ] Build native executable/library
- [ ] Test basic JDBC operations

### Step 4: Create ODBC Wrapper
- [ ] Implement core ODBC functions in C
- [ ] Link with native image library
- [ ] Build Windows DLL

### Step 5: Register ODBC Driver
- [ ] Create .reg file for Windows registry
- [ ] Install driver in ODBC Data Source Administrator
- [ ] Test with Excel/PowerBI

## Estimated Effort

- **Phase 1-2** (Native Image): 2-3 weeks
- **Phase 3** (ODBC Wrapper): 3-4 weeks
- **Phase 4** (Testing & Polish): 2-3 weeks
- **Total**: 7-10 weeks for experienced developer

## Implementation Status

### ✅ Completed

1. **GraalVM Native Image Configuration**
   - Created reflection config for JDBC drivers
   - Created resource config for META-INF/services
   - Created JNI config for C interop
   - Added GraalVM plugin to build.gradle

2. **Native Image Bridge**
   - Created `NativeImageBridge.java` with C-callable entry points
   - Implemented connection management
   - Implemented query execution
   - Implemented result set handling
   - Added error handling

3. **C ODBC Wrapper**
   - Created `starlake_odbc.c` with core ODBC functions
   - Implemented SQLAllocHandle/SQLFreeHandle
   - Implemented SQLDriverConnect/SQLDisconnect
   - Implemented SQLExecDirect
   - Implemented SQLFetch/SQLGetData
   - Implemented SQLNumResultCols

4. **Build System**
   - Created CMakeLists.txt for Windows
   - Created build.bat automation script
   - Created install.bat for driver registration
   - Created Windows registry file

### 🚧 Remaining Work

1. **Complete ODBC API** (40+ more functions)
   - SQLPrepare/SQLExecute (prepared statements)
   - SQLBindParameter (parameter binding)
   - SQLDescribeCol (column metadata)
   - SQLGetDiagRec (error diagnostics)
   - SQLSetEnvAttr/SQLSetConnectAttr (attributes)
   - SQLTables/SQLColumns (catalog functions)
   - Transaction functions (SQLEndTran)

2. **Data Type Support**
   - Numeric types (INTEGER, FLOAT, DECIMAL)
   - Date/Time types (DATE, TIME, TIMESTAMP)
   - Binary types (BINARY, VARBINARY)
   - LOB types (CLOB, BLOB)

3. **Testing & Validation**
   - Unit tests for each ODBC function
   - Integration tests with Excel/PowerBI
   - Performance benchmarking
   - Memory leak detection

4. **Documentation**
   - User guide
   - API reference
   - Troubleshooting guide
   - Performance tuning guide

## Quick Start

### Prerequisites
- Windows 10/11 (64-bit)
- GraalVM 23.1.1+ with Native Image
- Visual Studio 2019+ with C++ tools
- CMake 3.20+

### Build & Install

```bash
# 1. Build everything
cd starlake-odbc
build.bat

# 2. Install (as Administrator)
install.bat

# 3. Configure DSN in ODBC Administrator
# 4. Test with your application
```

## Next Steps

1. **Test the proof-of-concept**
   - Build native image with H2 driver
   - Build ODBC wrapper
   - Test basic connectivity

2. **Expand ODBC API coverage**
   - Implement prepared statements
   - Add metadata functions
   - Support more data types

3. **Production hardening**
   - Error handling
   - Memory management
   - Thread safety
   - Performance optimization

4. **Multi-database support**
   - Test with DuckDB, Snowflake, PostgreSQL
   - Handle driver-specific quirks
   - Optimize for each database

