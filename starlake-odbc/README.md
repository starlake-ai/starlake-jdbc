# Starlake ODBC Driver

Windows ODBC driver for Starlake JDBC, built on GraalVM Native Image.

## Architecture

```
Windows Application (Excel, PowerBI, etc.)
    ↓
ODBC Driver Manager
    ↓
Starlake ODBC Driver (C DLL) ← This component
    ↓
GraalVM Native Image (Compiled Java)
    ↓
Database
```

## Prerequisites

### For Building

1. **GraalVM** (Windows version)
   - Download from: https://www.graalvm.org/downloads/
   - Version: 23.1.1 or later
   - Install Native Image: `gu install native-image`

2. **Visual Studio 2019 or later**
   - With C++ build tools
   - Windows SDK

3. **CMake** 3.20 or later
   - Download from: https://cmake.org/download/

4. **Gradle** (for building Java component)
   - Included via gradlew

## Build Instructions

### Step 1: Build GraalVM Native Image

From the root project directory:

```bash
# Windows Command Prompt or PowerShell
cd /path/to/starlake-jdbc

# Build the native image as a shared library
gradlew nativeCompile
```

This will create:
- `build/native/nativeCompile/starlake-jdbc-native.dll`
- `build/native/nativeCompile/starlake-jdbc-native.lib`
- `build/native/nativeCompile/graal_isolate.h`

### Step 2: Build ODBC Driver

```bash
cd starlake-odbc
mkdir build
cd build

# Configure with CMake
cmake .. ^
    -G "Visual Studio 17 2022" ^
    -A x64 ^
    -DGRAALVM_INCLUDE_DIR=../../build/native/nativeCompile ^
    -DGRAALVM_LIBRARY=../../build/native/nativeCompile/starlake-jdbc-native.lib

# Build
cmake --build . --config Release
```

This will create:
- `build/Release/starlake_odbc.dll`

### Step 3: Install ODBC Driver

1. Copy files to installation directory:
```bash
mkdir C:\Program Files\Starlake\ODBC
copy Release\starlake_odbc.dll "C:\Program Files\Starlake\ODBC\"
copy ..\..\build\native\nativeCompile\starlake-jdbc-native.dll "C:\Program Files\Starlake\ODBC\"
```

2. Register with ODBC Driver Manager:

Create `starlake_odbc.reg`:
```reg
Windows Registry Editor Version 5.00

[HKEY_LOCAL_MACHINE\SOFTWARE\ODBC\ODBCINST.INI\Starlake ODBC Driver]
"Driver"="C:\\Program Files\\Starlake\\ODBC\\starlake_odbc.dll"
"Setup"="C:\\Program Files\\Starlake\\ODBC\\starlake_odbc.dll"
"APILevel"="1"
"ConnectFunctions"="YYN"
"DriverODBCVer"="03.00"
"FileUsage"="0"
"SQLLevel"="1"

[HKEY_LOCAL_MACHINE\SOFTWARE\ODBC\ODBCINST.INI\ODBC Drivers]
"Starlake ODBC Driver"="Installed"
```

Run as Administrator:
```bash
regedit /s starlake_odbc.reg
```

### Step 4: Create DSN

1. Open **ODBC Data Source Administrator** (64-bit)
2. Go to **System DSN** tab
3. Click **Add**
4. Select **Starlake ODBC Driver**
5. Configure:
   - **Data Source Name**: StarlakeDB
   - **Description**: Starlake JDBC Proxy
   - **Connection String**: `jdbc:starlake:h2:mem:`

## Testing

### Test with PowerShell

```powershell
$conn = New-Object System.Data.Odbc.OdbcConnection
$conn.ConnectionString = "DSN=StarlakeDB"
$conn.Open()

$cmd = $conn.CreateCommand()
$cmd.CommandText = "SELECT 1 AS test"
$reader = $cmd.ExecuteReader()

while ($reader.Read()) {
    Write-Host $reader["test"]
}

$reader.Close()
$conn.Close()
```

### Test with Excel

1. Open Excel
2. Data → Get Data → From Other Sources → From ODBC
3. Select **StarlakeDB** DSN
4. Enter SQL query
5. Load data

## Current Limitations

This is a **proof-of-concept** implementation with:

- ✅ Basic connection management
- ✅ Simple query execution
- ✅ Result set fetching
- ✅ String data types
- ⚠️ Limited ODBC API coverage (~10% of full spec)
- ⚠️ No prepared statements
- ⚠️ No transactions
- ⚠️ No metadata queries
- ⚠️ String data only (no numeric, date, binary)

## Next Steps

1. Implement remaining ODBC functions
2. Add proper error handling and diagnostics
3. Support all SQL data types
4. Add connection pooling
5. Performance optimization
6. Comprehensive testing

## Troubleshooting

### "Cannot find starlake-jdbc-native.dll"
- Ensure both DLLs are in the same directory
- Add directory to PATH environment variable

### "Failed to initialize driver"
- Check GraalVM installation
- Verify native image was built correctly
- Check Windows Event Viewer for details

### "Driver not found in ODBC Administrator"
- Run registry file as Administrator
- Check registry entries were created
- Restart ODBC Administrator

## License

Apache License 2.0 - See LICENSE file

