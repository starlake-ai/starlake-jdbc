@echo off
REM Installation script for Starlake ODBC Driver
REM Must be run as Administrator

echo ========================================
echo Starlake ODBC Driver Installation
echo ========================================
echo.

REM Check for admin privileges
net session >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: This script must be run as Administrator
    echo Right-click and select "Run as administrator"
    pause
    exit /b 1
)

REM Check if build exists
if not exist "build\Release\starlake_odbc.dll" (
    echo ERROR: Driver not built. Please run build.bat first.
    pause
    exit /b 1
)

echo Step 1: Creating installation directory...
set INSTALL_DIR=C:\Program Files\Starlake\ODBC
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

echo Step 2: Copying files...
copy /Y "build\Release\starlake_odbc.dll" "%INSTALL_DIR%\"
copy /Y "..\build\native\nativeCompile\starlake-jdbc-native.dll" "%INSTALL_DIR%\"

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to copy files
    pause
    exit /b 1
)

echo Step 3: Registering ODBC driver...
regedit /s install\starlake_odbc.reg

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to register driver
    pause
    exit /b 1
)

echo.
echo ========================================
echo Installation completed successfully!
echo ========================================
echo.
echo Driver installed to: %INSTALL_DIR%
echo.
echo Next steps:
echo   1. Open ODBC Data Source Administrator (64-bit)
echo   2. Go to System DSN tab
echo   3. Click Add and select "Starlake ODBC Driver"
echo   4. Configure your data source
echo.
echo Example connection string:
echo   DSN=StarlakeDB;URL=jdbc:starlake:h2:mem:
echo.

pause

