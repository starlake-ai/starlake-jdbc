@echo off
REM Build script for Starlake ODBC Driver on Windows
REM Requires: GraalVM, Visual Studio, CMake

echo ========================================
echo Starlake ODBC Driver Build Script
echo ========================================
echo.

REM Check for GraalVM
where native-image >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: native-image not found. Please install GraalVM and run: gu install native-image
    exit /b 1
)

REM Check for CMake
where cmake >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: cmake not found. Please install CMake.
    exit /b 1
)

echo Step 1: Building GraalVM Native Image...
echo ========================================
cd ..
call gradlew.bat nativeCompile
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Native image build failed
    exit /b 1
)
echo.

echo Step 2: Building ODBC Driver...
echo ========================================
cd starlake-odbc

if not exist build mkdir build
cd build

REM Configure
cmake .. ^
    -G "Visual Studio 17 2022" ^
    -A x64 ^
    -DGRAALVM_INCLUDE_DIR=../../build/native/nativeCompile ^
    -DGRAALVM_LIBRARY=../../build/native/nativeCompile/starlake-jdbc-native.lib

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: CMake configuration failed
    exit /b 1
)

REM Build
cmake --build . --config Release
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: ODBC driver build failed
    exit /b 1
)

echo.
echo ========================================
echo Build completed successfully!
echo ========================================
echo.
echo Output files:
echo   - Release\starlake_odbc.dll
echo   - ..\..\build\native\nativeCompile\starlake-jdbc-native.dll
echo.
echo Next steps:
echo   1. Run install.bat as Administrator to install the driver
echo   2. Configure DSN in ODBC Data Source Administrator
echo.

cd ..

