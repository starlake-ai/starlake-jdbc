/**
 * Starlake ODBC Driver Implementation
 * 
 * This is a minimal ODBC driver wrapper that calls into the GraalVM Native Image
 * compiled Starlake JDBC driver.
 */

#include <windows.h>
#include <sql.h>
#include <sqlext.h>
#include <sqltypes.h>
#include <stdio.h>
#include <string.h>
#include "starlake_native.h"

// ODBC handle structures
typedef struct {
    SQLSMALLINT handleType;
    void* graalThread;
} StarlakeEnv;

typedef struct {
    SQLSMALLINT handleType;
    StarlakeEnv* env;
    long nativeHandle;
    char lastError[512];
} StarlakeConnection;

typedef struct {
    SQLSMALLINT handleType;
    StarlakeConnection* conn;
    long nativeHandle;
    int columnCount;
    SQLLEN rowCount;
} StarlakeStatement;

// DLL Entry Point
BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpvReserved) {
    switch (fdwReason) {
        case DLL_PROCESS_ATTACH:
            // Initialize GraalVM isolate here if needed
            break;
        case DLL_PROCESS_DETACH:
            // Cleanup
            break;
    }
    return TRUE;
}

/**
 * SQLAllocHandle - Allocate an environment, connection, or statement handle
 */
SQLRETURN SQL_API SQLAllocHandle(
    SQLSMALLINT HandleType,
    SQLHANDLE InputHandle,
    SQLHANDLE* OutputHandlePtr)
{
    if (OutputHandlePtr == NULL) {
        return SQL_ERROR;
    }

    switch (HandleType) {
        case SQL_HANDLE_ENV: {
            StarlakeEnv* env = (StarlakeEnv*)malloc(sizeof(StarlakeEnv));
            if (env == NULL) return SQL_ERROR;
            
            env->handleType = SQL_HANDLE_ENV;
            env->graalThread = NULL;  // Will be initialized on first connect
            
            // Initialize Starlake driver
            if (starlake_init(env->graalThread) != 0) {
                free(env);
                return SQL_ERROR;
            }
            
            *OutputHandlePtr = (SQLHANDLE)env;
            return SQL_SUCCESS;
        }

        case SQL_HANDLE_DBC: {
            StarlakeEnv* env = (StarlakeEnv*)InputHandle;
            if (env == NULL || env->handleType != SQL_HANDLE_ENV) {
                return SQL_ERROR;
            }

            StarlakeConnection* conn = (StarlakeConnection*)malloc(sizeof(StarlakeConnection));
            if (conn == NULL) return SQL_ERROR;

            conn->handleType = SQL_HANDLE_DBC;
            conn->env = env;
            conn->nativeHandle = -1;
            conn->lastError[0] = '\0';

            *OutputHandlePtr = (SQLHANDLE)conn;
            return SQL_SUCCESS;
        }

        case SQL_HANDLE_STMT: {
            StarlakeConnection* conn = (StarlakeConnection*)InputHandle;
            if (conn == NULL || conn->handleType != SQL_HANDLE_DBC) {
                return SQL_ERROR;
            }

            StarlakeStatement* stmt = (StarlakeStatement*)malloc(sizeof(StarlakeStatement));
            if (stmt == NULL) return SQL_ERROR;

            stmt->handleType = SQL_HANDLE_STMT;
            stmt->conn = conn;
            stmt->nativeHandle = -1;
            stmt->columnCount = 0;
            stmt->rowCount = 0;

            *OutputHandlePtr = (SQLHANDLE)stmt;
            return SQL_SUCCESS;
        }

        default:
            return SQL_ERROR;
    }
}

/**
 * SQLFreeHandle - Free a handle
 */
SQLRETURN SQL_API SQLFreeHandle(SQLSMALLINT HandleType, SQLHANDLE Handle) {
    if (Handle == NULL) {
        return SQL_INVALID_HANDLE;
    }

    switch (HandleType) {
        case SQL_HANDLE_ENV: {
            StarlakeEnv* env = (StarlakeEnv*)Handle;
            free(env);
            return SQL_SUCCESS;
        }

        case SQL_HANDLE_DBC: {
            StarlakeConnection* conn = (StarlakeConnection*)Handle;
            if (conn->nativeHandle >= 0) {
                starlake_disconnect(conn->env->graalThread, conn->nativeHandle);
            }
            free(conn);
            return SQL_SUCCESS;
        }

        case SQL_HANDLE_STMT: {
            StarlakeStatement* stmt = (StarlakeStatement*)Handle;
            if (stmt->nativeHandle >= 0) {
                starlake_close_result_set(stmt->conn->env->graalThread, stmt->nativeHandle);
            }
            free(stmt);
            return SQL_SUCCESS;
        }
    }

    return SQL_ERROR;
}

/**
 * SQLDriverConnect - Connect to a data source
 */
SQLRETURN SQL_API SQLDriverConnect(
    SQLHDBC ConnectionHandle,
    SQLHWND WindowHandle,
    SQLCHAR* InConnectionString,
    SQLSMALLINT StringLength1,
    SQLCHAR* OutConnectionString,
    SQLSMALLINT BufferLength,
    SQLSMALLINT* StringLength2Ptr,
    SQLUSMALLINT DriverCompletion)
{
    StarlakeConnection* conn = (StarlakeConnection*)ConnectionHandle;
    if (conn == NULL || conn->handleType != SQL_HANDLE_DBC) {
        return SQL_INVALID_HANDLE;
    }

    // Parse connection string for DSN, UID, PWD
    char url[512] = "jdbc:starlake:h2:mem:";  // Default
    char user[128] = "";
    char password[128] = "";

    // Simple parsing (production code would be more robust)
    char* connStr = (char*)InConnectionString;
    // TODO: Parse DSN=...; UID=...; PWD=...

    // Connect via native image
    long handle = starlake_connect(conn->env->graalThread, url, user, password);
    if (handle < 0) {
        starlake_get_last_error(conn->env->graalThread, conn->lastError, sizeof(conn->lastError));
        return SQL_ERROR;
    }

    conn->nativeHandle = handle;
    return SQL_SUCCESS;
}

/**
 * SQLDisconnect - Disconnect from a data source
 */
SQLRETURN SQL_API SQLDisconnect(SQLHDBC ConnectionHandle) {
    StarlakeConnection* conn = (StarlakeConnection*)ConnectionHandle;
    if (conn == NULL || conn->handleType != SQL_HANDLE_DBC) {
        return SQL_INVALID_HANDLE;
    }

    if (conn->nativeHandle >= 0) {
        if (starlake_disconnect(conn->env->graalThread, conn->nativeHandle) != 0) {
            return SQL_ERROR;
        }
        conn->nativeHandle = -1;
    }

    return SQL_SUCCESS;
}

/**
 * SQLExecDirect - Execute a SQL statement directly
 */
SQLRETURN SQL_API SQLExecDirect(
    SQLHSTMT StatementHandle,
    SQLCHAR* StatementText,
    SQLINTEGER TextLength)
{
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT) {
        return SQL_INVALID_HANDLE;
    }

    if (stmt->conn->nativeHandle < 0) {
        return SQL_ERROR;  // Not connected
    }

    // Execute query via native image
    long rsHandle = starlake_execute_query(
        stmt->conn->env->graalThread,
        stmt->conn->nativeHandle,
        (const char*)StatementText
    );

    if (rsHandle < 0) {
        starlake_get_last_error(stmt->conn->env->graalThread,
                               stmt->conn->lastError,
                               sizeof(stmt->conn->lastError));
        return SQL_ERROR;
    }

    stmt->nativeHandle = rsHandle;

    // Get column count
    stmt->columnCount = starlake_get_column_count(stmt->conn->env->graalThread, rsHandle);
    if (stmt->columnCount < 0) {
        return SQL_ERROR;
    }

    return SQL_SUCCESS;
}

/**
 * SQLFetch - Fetch next row
 */
SQLRETURN SQL_API SQLFetch(SQLHSTMT StatementHandle) {
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT) {
        return SQL_INVALID_HANDLE;
    }

    if (stmt->nativeHandle < 0) {
        return SQL_ERROR;
    }

    int result = starlake_fetch(stmt->conn->env->graalThread, stmt->nativeHandle);
    if (result == 1) {
        stmt->rowCount++;
        return SQL_SUCCESS;
    } else if (result == 0) {
        return SQL_NO_DATA;
    } else {
        return SQL_ERROR;
    }
}

/**
 * SQLGetData - Retrieve data for a single column
 */
SQLRETURN SQL_API SQLGetData(
    SQLHSTMT StatementHandle,
    SQLUSMALLINT ColumnNumber,
    SQLSMALLINT TargetType,
    SQLPOINTER TargetValuePtr,
    SQLLEN BufferLength,
    SQLLEN* StrLen_or_IndPtr)
{
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT) {
        return SQL_INVALID_HANDLE;
    }

    if (stmt->nativeHandle < 0 || TargetValuePtr == NULL) {
        return SQL_ERROR;
    }

    int result = -1;
    void* graalThread = stmt->conn->env->graalThread;
    long rsHandle = stmt->nativeHandle;

    // Handle different target types
    switch (TargetType) {
        case SQL_C_CHAR:
        case SQL_C_WCHAR: {
            // String data
            result = starlake_get_string(
                graalThread,
                rsHandle,
                ColumnNumber,
                (char*)TargetValuePtr,
                (int)BufferLength
            );

            if (result == 0) {
                // Check if value was NULL
                int wasNull = starlake_was_null(graalThread, rsHandle);
                if (wasNull == 1) {
                    if (StrLen_or_IndPtr != NULL) {
                        *StrLen_or_IndPtr = SQL_NULL_DATA;
                    }
                    return SQL_SUCCESS;
                }

                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = strlen((char*)TargetValuePtr);
                }
                return SQL_SUCCESS;
            }
            break;
        }

        case SQL_C_LONG:
        case SQL_C_SLONG:
        case SQL_C_ULONG: {
            // 32-bit integer
            int value = starlake_get_int(graalThread, rsHandle, ColumnNumber);

            // Check if value was NULL
            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            *(SQLINTEGER*)TargetValuePtr = value;
            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = sizeof(SQLINTEGER);
            }
            return SQL_SUCCESS;
        }

        case SQL_C_SHORT:
        case SQL_C_SSHORT:
        case SQL_C_USHORT: {
            // 16-bit integer
            int value = starlake_get_int(graalThread, rsHandle, ColumnNumber);

            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            *(SQLSMALLINT*)TargetValuePtr = (SQLSMALLINT)value;
            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = sizeof(SQLSMALLINT);
            }
            return SQL_SUCCESS;
        }

        case SQL_C_SBIGINT:
        case SQL_C_UBIGINT: {
            // 64-bit integer
            long long value = starlake_get_long(graalThread, rsHandle, ColumnNumber);

            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            *(SQLBIGINT*)TargetValuePtr = value;
            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = sizeof(SQLBIGINT);
            }
            return SQL_SUCCESS;
        }

        case SQL_C_FLOAT: {
            // Single precision float
            double value = starlake_get_double(graalThread, rsHandle, ColumnNumber);

            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            *(SQLREAL*)TargetValuePtr = (SQLREAL)value;
            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = sizeof(SQLREAL);
            }
            return SQL_SUCCESS;
        }

        case SQL_C_DOUBLE: {
            // Double precision float
            double value = starlake_get_double(graalThread, rsHandle, ColumnNumber);

            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            *(SQLDOUBLE*)TargetValuePtr = value;
            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = sizeof(SQLDOUBLE);
            }
            return SQL_SUCCESS;
        }

        case SQL_C_BIT: {
            // Boolean/bit
            int value = starlake_get_int(graalThread, rsHandle, ColumnNumber);

            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            *(SQLCHAR*)TargetValuePtr = (value != 0) ? 1 : 0;
            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = sizeof(SQLCHAR);
            }
            return SQL_SUCCESS;
        }

        case SQL_C_TINYINT:
        case SQL_C_STINYINT:
        case SQL_C_UTINYINT: {
            // 8-bit integer
            int value = starlake_get_int(graalThread, rsHandle, ColumnNumber);

            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            *(SQLSCHAR*)TargetValuePtr = (SQLSCHAR)value;
            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = sizeof(SQLSCHAR);
            }
            return SQL_SUCCESS;
        }

        case SQL_C_NUMERIC: {
            // Numeric/Decimal - convert from double
            double value = starlake_get_double(graalThread, rsHandle, ColumnNumber);

            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            // Convert double to SQL_NUMERIC_STRUCT
            SQL_NUMERIC_STRUCT* numeric = (SQL_NUMERIC_STRUCT*)TargetValuePtr;
            numeric->precision = 15;
            numeric->scale = 2;
            numeric->sign = (value >= 0) ? 1 : 0;

            // Simple conversion (production code would be more robust)
            long long intValue = (long long)(value * 100);
            if (intValue < 0) intValue = -intValue;

            memset(numeric->val, 0, SQL_MAX_NUMERIC_LEN);
            memcpy(numeric->val, &intValue, sizeof(long long));

            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = sizeof(SQL_NUMERIC_STRUCT);
            }
            return SQL_SUCCESS;
        }

        case SQL_C_TYPE_DATE:
        case SQL_C_DATE: {
            // Date type
            long epochDays = starlake_get_date(graalThread, rsHandle, ColumnNumber);

            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            // Convert epoch days to SQL_DATE_STRUCT
            DATE_STRUCT* dateStruct = (DATE_STRUCT*)TargetValuePtr;

            // Simple conversion (days since 1970-01-01)
            long days = epochDays;
            int year = 1970;
            int month = 1;
            int day = 1;

            // Add days (simplified - production would handle leap years properly)
            day += (int)days;
            while (day > 365) {
                day -= 365;
                year++;
            }
            while (day > 30) {
                day -= 30;
                month++;
            }

            dateStruct->year = year;
            dateStruct->month = month;
            dateStruct->day = day;

            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = sizeof(DATE_STRUCT);
            }
            return SQL_SUCCESS;
        }

        case SQL_C_TYPE_TIME:
        case SQL_C_TIME: {
            // Time type
            long millisSinceMidnight = starlake_get_time(graalThread, rsHandle, ColumnNumber);

            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            // Convert to SQL_TIME_STRUCT
            TIME_STRUCT* timeStruct = (TIME_STRUCT*)TargetValuePtr;

            long totalSeconds = millisSinceMidnight / 1000;
            timeStruct->hour = (SQLUSMALLINT)(totalSeconds / 3600);
            timeStruct->minute = (SQLUSMALLINT)((totalSeconds % 3600) / 60);
            timeStruct->second = (SQLUSMALLINT)(totalSeconds % 60);

            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = sizeof(TIME_STRUCT);
            }
            return SQL_SUCCESS;
        }

        case SQL_C_TYPE_TIMESTAMP:
        case SQL_C_TIMESTAMP: {
            // Timestamp type
            long millisSinceEpoch = starlake_get_timestamp(graalThread, rsHandle, ColumnNumber);

            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            // Convert to SQL_TIMESTAMP_STRUCT
            TIMESTAMP_STRUCT* tsStruct = (TIMESTAMP_STRUCT*)TargetValuePtr;

            // Simple conversion (production would be more accurate)
            long totalSeconds = millisSinceEpoch / 1000;
            long days = totalSeconds / (24 * 60 * 60);
            long timeOfDay = totalSeconds % (24 * 60 * 60);

            // Date part (simplified)
            int year = 1970 + (int)(days / 365);
            int month = 1 + (int)((days % 365) / 30);
            int day = 1 + (int)((days % 365) % 30);

            // Time part
            int hour = (int)(timeOfDay / 3600);
            int minute = (int)((timeOfDay % 3600) / 60);
            int second = (int)(timeOfDay % 60);
            int fraction = (int)((millisSinceEpoch % 1000) * 1000000);

            tsStruct->year = year;
            tsStruct->month = month;
            tsStruct->day = day;
            tsStruct->hour = hour;
            tsStruct->minute = minute;
            tsStruct->second = second;
            tsStruct->fraction = fraction;

            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = sizeof(TIMESTAMP_STRUCT);
            }
            return SQL_SUCCESS;
        }

        case SQL_C_BINARY: {
            // Binary data
            int bytesRead = starlake_get_bytes(
                graalThread,
                rsHandle,
                ColumnNumber,
                (char*)TargetValuePtr,
                (int)BufferLength
            );

            if (bytesRead < 0) {
                return SQL_ERROR;
            }

            int wasNull = starlake_was_null(graalThread, rsHandle);
            if (wasNull == 1) {
                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = SQL_NULL_DATA;
                }
                return SQL_SUCCESS;
            }

            if (StrLen_or_IndPtr != NULL) {
                *StrLen_or_IndPtr = bytesRead;
            }
            return SQL_SUCCESS;
        }

        case SQL_C_DEFAULT: {
            // Default type - get as string
            result = starlake_get_string(
                graalThread,
                rsHandle,
                ColumnNumber,
                (char*)TargetValuePtr,
                (int)BufferLength
            );

            if (result == 0) {
                int wasNull = starlake_was_null(graalThread, rsHandle);
                if (wasNull == 1) {
                    if (StrLen_or_IndPtr != NULL) {
                        *StrLen_or_IndPtr = SQL_NULL_DATA;
                    }
                    return SQL_SUCCESS;
                }

                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = strlen((char*)TargetValuePtr);
                }
                return SQL_SUCCESS;
            }
            break;
        }

        default:
            // Unsupported type - try to get as string
            result = starlake_get_string(
                graalThread,
                rsHandle,
                ColumnNumber,
                (char*)TargetValuePtr,
                (int)BufferLength
            );

            if (result == 0) {
                int wasNull = starlake_was_null(graalThread, rsHandle);
                if (wasNull == 1) {
                    if (StrLen_or_IndPtr != NULL) {
                        *StrLen_or_IndPtr = SQL_NULL_DATA;
                    }
                    return SQL_SUCCESS;
                }

                if (StrLen_or_IndPtr != NULL) {
                    *StrLen_or_IndPtr = strlen((char*)TargetValuePtr);
                }
                return SQL_SUCCESS;
            }
            break;
    }

    return SQL_ERROR;
}

/**
 * SQLNumResultCols - Get number of columns in result set
 */
SQLRETURN SQL_API SQLNumResultCols(SQLHSTMT StatementHandle, SQLSMALLINT* ColumnCountPtr) {
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT || ColumnCountPtr == NULL) {
        return SQL_INVALID_HANDLE;
    }

    *ColumnCountPtr = (SQLSMALLINT)stmt->columnCount;
    return SQL_SUCCESS;
}

/**
 * SQLDescribeCol - Describe a column in result set
 */
SQLRETURN SQL_API SQLDescribeCol(
    SQLHSTMT StatementHandle,
    SQLUSMALLINT ColumnNumber,
    SQLCHAR* ColumnName,
    SQLSMALLINT BufferLength,
    SQLSMALLINT* NameLengthPtr,
    SQLSMALLINT* DataTypePtr,
    SQLULEN* ColumnSizePtr,
    SQLSMALLINT* DecimalDigitsPtr,
    SQLSMALLINT* NullablePtr)
{
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT) {
        return SQL_INVALID_HANDLE;
    }

    if (stmt->nativeHandle < 0) {
        return SQL_ERROR;
    }

    void* graalThread = stmt->conn->env->graalThread;
    long rsHandle = stmt->nativeHandle;

    // Get column name
    if (ColumnName != NULL && BufferLength > 0) {
        int result = starlake_get_column_name(
            graalThread,
            rsHandle,
            ColumnNumber,
            (char*)ColumnName,
            BufferLength
        );

        if (result != 0) {
            return SQL_ERROR;
        }

        if (NameLengthPtr != NULL) {
            *NameLengthPtr = (SQLSMALLINT)strlen((char*)ColumnName);
        }
    }

    // Get column precision and scale from metadata
    int precision = starlake_get_column_precision(graalThread, rsHandle, ColumnNumber);
    int scale = starlake_get_column_scale(graalThread, rsHandle, ColumnNumber);

    // Get column type and map JDBC types to ODBC types
    if (DataTypePtr != NULL || ColumnSizePtr != NULL) {
        int sqlType = starlake_get_column_type(graalThread, rsHandle, ColumnNumber);

        // JDBC type constants (from java.sql.Types)
        // Map to ODBC SQL types
        SQLSMALLINT odbcType = SQL_VARCHAR;
        SQLULEN columnSize = 255;

        switch (sqlType) {
            // ========== INTEGER TYPES ==========
            case -6: // TINYINT
                odbcType = SQL_TINYINT;
                columnSize = 3;
                break;
            case 5:  // SMALLINT
                odbcType = SQL_SMALLINT;
                columnSize = 5;
                break;
            case 4:  // INTEGER
                odbcType = SQL_INTEGER;
                columnSize = 10;
                break;
            case -5: // BIGINT
                odbcType = SQL_BIGINT;
                columnSize = 19;
                break;

            // ========== FLOATING POINT TYPES ==========
            case 6:  // FLOAT
                odbcType = SQL_FLOAT;
                columnSize = 15;
                break;
            case 7:  // REAL
                odbcType = SQL_REAL;
                columnSize = 7;
                break;
            case 8:  // DOUBLE
                odbcType = SQL_DOUBLE;
                columnSize = 15;
                break;

            // ========== EXACT NUMERIC TYPES ==========
            case 2:  // NUMERIC
                odbcType = SQL_NUMERIC;
                columnSize = (precision > 0) ? precision : 38;
                break;
            case 3:  // DECIMAL
                odbcType = SQL_DECIMAL;
                columnSize = (precision > 0) ? precision : 38;
                break;

            // ========== CHARACTER TYPES ==========
            case 1:  // CHAR
                odbcType = SQL_CHAR;
                columnSize = (precision > 0) ? precision : 1;
                break;
            case 12: // VARCHAR
                odbcType = SQL_VARCHAR;
                columnSize = (precision > 0) ? precision : 255;
                break;
            case -1: // LONGVARCHAR
                odbcType = SQL_LONGVARCHAR;
                columnSize = (precision > 0) ? precision : 65535;
                break;
            case -15: // NCHAR
                odbcType = SQL_WCHAR;
                columnSize = (precision > 0) ? precision : 1;
                break;
            case -9:  // NVARCHAR
                odbcType = SQL_WVARCHAR;
                columnSize = (precision > 0) ? precision : 255;
                break;
            case -16: // LONGNVARCHAR
                odbcType = SQL_WLONGVARCHAR;
                columnSize = (precision > 0) ? precision : 65535;
                break;

            // ========== BINARY TYPES ==========
            case -2: // BINARY
                odbcType = SQL_BINARY;
                columnSize = (precision > 0) ? precision : 1;
                break;
            case -3: // VARBINARY
                odbcType = SQL_VARBINARY;
                columnSize = (precision > 0) ? precision : 255;
                break;
            case -4: // LONGVARBINARY
                odbcType = SQL_LONGVARBINARY;
                columnSize = (precision > 0) ? precision : 65535;
                break;
            case 2004: // BLOB
                odbcType = SQL_LONGVARBINARY;
                columnSize = 2147483647;  // Max INT
                break;
            case 2005: // CLOB
                odbcType = SQL_LONGVARCHAR;
                columnSize = 2147483647;
                break;
            case 2011: // NCLOB
                odbcType = SQL_WLONGVARCHAR;
                columnSize = 2147483647;
                break;

            // ========== DATE/TIME TYPES ==========
            case 91: // DATE
                odbcType = SQL_TYPE_DATE;
                columnSize = 10;  // YYYY-MM-DD
                break;
            case 92: // TIME
                odbcType = SQL_TYPE_TIME;
                columnSize = 8;   // HH:MM:SS
                break;
            case 93: // TIMESTAMP
                odbcType = SQL_TYPE_TIMESTAMP;
                columnSize = (precision > 0) ? 20 + precision : 23;  // With fractional seconds
                break;
            case 2013: // TIME_WITH_TIMEZONE
                odbcType = SQL_TYPE_TIME;
                columnSize = 14;  // HH:MM:SS+HH:MM
                break;
            case 2014: // TIMESTAMP_WITH_TIMEZONE
                odbcType = SQL_TYPE_TIMESTAMP;
                columnSize = 29;  // With timezone
                break;

            // ========== BOOLEAN TYPE ==========
            case 16:  // BOOLEAN
                odbcType = SQL_BIT;
                columnSize = 1;
                break;
            case -7:  // BIT
                odbcType = SQL_BIT;
                columnSize = 1;
                break;

            // ========== OTHER TYPES ==========
            case 0:   // NULL
                odbcType = SQL_VARCHAR;
                columnSize = 0;
                break;
            case 1111: // OTHER
            case 2000: // JAVA_OBJECT
            case 2001: // DISTINCT
            case 2002: // STRUCT
            case 2003: // ARRAY
            case 2006: // REF
            case 2009: // SQLXML
            case 70:   // DATALINK
            case -10:  // ROWID
            default:
                // Default to VARCHAR for unknown types
                odbcType = SQL_VARCHAR;
                columnSize = 255;
                break;
        }

        if (DataTypePtr != NULL) {
            *DataTypePtr = odbcType;
        }
        if (ColumnSizePtr != NULL) {
            *ColumnSizePtr = columnSize;
        }
    }

    // Set decimal digits (scale) from metadata
    if (DecimalDigitsPtr != NULL) {
        *DecimalDigitsPtr = (scale >= 0) ? (SQLSMALLINT)scale : 0;
    }

    // Get nullable status from metadata
    if (NullablePtr != NULL) {
        int nullable = starlake_get_column_nullable(graalThread, rsHandle, ColumnNumber);
        switch (nullable) {
            case 0:  // columnNoNulls
                *NullablePtr = SQL_NO_NULLS;
                break;
            case 1:  // columnNullable
                *NullablePtr = SQL_NULLABLE;
                break;
            case 2:  // columnNullableUnknown
            default:
                *NullablePtr = SQL_NULLABLE_UNKNOWN;
                break;
        }
    }

    return SQL_SUCCESS;
}

/**
 * SQLColAttribute - Get column attribute
 * This is a more flexible version of SQLDescribeCol
 */
SQLRETURN SQL_API SQLColAttribute(
    SQLHSTMT StatementHandle,
    SQLUSMALLINT ColumnNumber,
    SQLUSMALLINT FieldIdentifier,
    SQLPOINTER CharacterAttributePtr,
    SQLSMALLINT BufferLength,
    SQLSMALLINT* StringLengthPtr,
    SQLLEN* NumericAttributePtr)
{
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT) {
        return SQL_INVALID_HANDLE;
    }

    if (stmt->nativeHandle < 0) {
        return SQL_ERROR;
    }

    void* graalThread = stmt->conn->env->graalThread;
    long rsHandle = stmt->nativeHandle;

    switch (FieldIdentifier) {
        case SQL_DESC_NAME:
        case SQL_COLUMN_NAME: {
            if (CharacterAttributePtr != NULL && BufferLength > 0) {
                int result = starlake_get_column_name(
                    graalThread, rsHandle, ColumnNumber,
                    (char*)CharacterAttributePtr, BufferLength
                );
                if (result != 0) return SQL_ERROR;
                if (StringLengthPtr != NULL) {
                    *StringLengthPtr = (SQLSMALLINT)strlen((char*)CharacterAttributePtr);
                }
            }
            break;
        }

        case SQL_DESC_TYPE:
        case SQL_COLUMN_TYPE: {
            if (NumericAttributePtr != NULL) {
                int sqlType = starlake_get_column_type(graalThread, rsHandle, ColumnNumber);
                // Map JDBC type to ODBC type (same mapping as SQLDescribeCol)
                SQLSMALLINT odbcType = SQL_VARCHAR;
                switch (sqlType) {
                    case -6: odbcType = SQL_TINYINT; break;
                    case 5:  odbcType = SQL_SMALLINT; break;
                    case 4:  odbcType = SQL_INTEGER; break;
                    case -5: odbcType = SQL_BIGINT; break;
                    case 6:  odbcType = SQL_FLOAT; break;
                    case 7:  odbcType = SQL_REAL; break;
                    case 8:  odbcType = SQL_DOUBLE; break;
                    case 2:  odbcType = SQL_NUMERIC; break;
                    case 3:  odbcType = SQL_DECIMAL; break;
                    case 1:  odbcType = SQL_CHAR; break;
                    case 12: odbcType = SQL_VARCHAR; break;
                    case -1: odbcType = SQL_LONGVARCHAR; break;
                    case -15: odbcType = SQL_WCHAR; break;
                    case -9:  odbcType = SQL_WVARCHAR; break;
                    case -16: odbcType = SQL_WLONGVARCHAR; break;
                    case -2: odbcType = SQL_BINARY; break;
                    case -3: odbcType = SQL_VARBINARY; break;
                    case -4: odbcType = SQL_LONGVARBINARY; break;
                    case 91: odbcType = SQL_TYPE_DATE; break;
                    case 92: odbcType = SQL_TYPE_TIME; break;
                    case 93: odbcType = SQL_TYPE_TIMESTAMP; break;
                    case 16: case -7: odbcType = SQL_BIT; break;
                    default: odbcType = SQL_VARCHAR; break;
                }
                *NumericAttributePtr = odbcType;
            }
            break;
        }

        case SQL_DESC_PRECISION:
        case SQL_COLUMN_PRECISION: {
            if (NumericAttributePtr != NULL) {
                int precision = starlake_get_column_precision(graalThread, rsHandle, ColumnNumber);
                *NumericAttributePtr = (precision >= 0) ? precision : 0;
            }
            break;
        }

        case SQL_DESC_SCALE:
        case SQL_COLUMN_SCALE: {
            if (NumericAttributePtr != NULL) {
                int scale = starlake_get_column_scale(graalThread, rsHandle, ColumnNumber);
                *NumericAttributePtr = (scale >= 0) ? scale : 0;
            }
            break;
        }

        case SQL_DESC_NULLABLE:
        case SQL_COLUMN_NULLABLE: {
            if (NumericAttributePtr != NULL) {
                int nullable = starlake_get_column_nullable(graalThread, rsHandle, ColumnNumber);
                switch (nullable) {
                    case 0: *NumericAttributePtr = SQL_NO_NULLS; break;
                    case 1: *NumericAttributePtr = SQL_NULLABLE; break;
                    default: *NumericAttributePtr = SQL_NULLABLE_UNKNOWN; break;
                }
            }
            break;
        }

        case SQL_DESC_LENGTH:
        case SQL_COLUMN_LENGTH: {
            if (NumericAttributePtr != NULL) {
                int precision = starlake_get_column_precision(graalThread, rsHandle, ColumnNumber);
                *NumericAttributePtr = (precision >= 0) ? precision : 0;
            }
            break;
        }

        case SQL_DESC_DISPLAY_SIZE: {
            if (NumericAttributePtr != NULL) {
                int displaySize = starlake_get_column_display_size(graalThread, rsHandle, ColumnNumber);
                *NumericAttributePtr = (displaySize >= 0) ? displaySize : 0;
            }
            break;
        }

        case SQL_DESC_OCTET_LENGTH: {
            if (NumericAttributePtr != NULL) {
                int precision = starlake_get_column_precision(graalThread, rsHandle, ColumnNumber);
                // For character data, octet length may differ from precision for multi-byte chars
                *NumericAttributePtr = (precision >= 0) ? precision : 0;
            }
            break;
        }

        case SQL_DESC_TYPE_NAME: {
            if (CharacterAttributePtr != NULL && BufferLength > 0) {
                int result = starlake_get_column_type_name(
                    graalThread, rsHandle, ColumnNumber,
                    (char*)CharacterAttributePtr, BufferLength
                );
                if (result != 0) return SQL_ERROR;
                if (StringLengthPtr != NULL) {
                    *StringLengthPtr = (SQLSMALLINT)strlen((char*)CharacterAttributePtr);
                }
            }
            break;
        }

        case SQL_DESC_UNSIGNED: {
            if (NumericAttributePtr != NULL) {
                // Check if numeric type is unsigned
                int sqlType = starlake_get_column_type(graalThread, rsHandle, ColumnNumber);
                // Most JDBC numeric types are signed
                *NumericAttributePtr = SQL_FALSE;
            }
            break;
        }

        case SQL_DESC_AUTO_UNIQUE_VALUE: {
            if (NumericAttributePtr != NULL) {
                // Auto-increment - would need additional metadata function
                *NumericAttributePtr = SQL_FALSE;
            }
            break;
        }

        case SQL_DESC_UPDATABLE: {
            if (NumericAttributePtr != NULL) {
                *NumericAttributePtr = SQL_ATTR_READWRITE_UNKNOWN;
            }
            break;
        }

        case SQL_DESC_SEARCHABLE: {
            if (NumericAttributePtr != NULL) {
                *NumericAttributePtr = SQL_SEARCHABLE;
            }
            break;
        }

        case SQL_DESC_CASE_SENSITIVE: {
            if (NumericAttributePtr != NULL) {
                int sqlType = starlake_get_column_type(graalThread, rsHandle, ColumnNumber);
                // Character types are typically case sensitive
                *NumericAttributePtr = (sqlType == 1 || sqlType == 12 || sqlType == -1 ||
                                        sqlType == -15 || sqlType == -9 || sqlType == -16)
                                       ? SQL_TRUE : SQL_FALSE;
            }
            break;
        }

        case SQL_DESC_FIXED_PREC_SCALE: {
            if (NumericAttributePtr != NULL) {
                int sqlType = starlake_get_column_type(graalThread, rsHandle, ColumnNumber);
                // DECIMAL and NUMERIC have fixed precision and scale
                *NumericAttributePtr = (sqlType == 2 || sqlType == 3) ? SQL_TRUE : SQL_FALSE;
            }
            break;
        }

        case SQL_DESC_COUNT: {
            if (NumericAttributePtr != NULL) {
                *NumericAttributePtr = stmt->columnCount;
            }
            break;
        }

        default:
            // Unknown attribute
            if (NumericAttributePtr != NULL) {
                *NumericAttributePtr = 0;
            }
            break;
    }

    return SQL_SUCCESS;
}

/**
 * SQLPrepare - Prepare a SQL statement
 */
SQLRETURN SQL_API SQLPrepare(
    SQLHSTMT StatementHandle,
    SQLCHAR* StatementText,
    SQLINTEGER TextLength)
{
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT) {
        return SQL_INVALID_HANDLE;
    }

    if (stmt->conn->nativeHandle < 0) {
        return SQL_ERROR;
    }

    // Prepare statement via native image
    long preparedHandle = starlake_prepare(
        stmt->conn->env->graalThread,
        stmt->conn->nativeHandle,
        (const char*)StatementText
    );

    if (preparedHandle < 0) {
        starlake_get_last_error(stmt->conn->env->graalThread,
                               stmt->conn->lastError,
                               sizeof(stmt->conn->lastError));
        return SQL_ERROR;
    }

    stmt->nativeHandle = preparedHandle;
    return SQL_SUCCESS;
}

/**
 * SQLExecute - Execute a prepared statement
 */
SQLRETURN SQL_API SQLExecute(SQLHSTMT StatementHandle) {
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT) {
        return SQL_INVALID_HANDLE;
    }

    if (stmt->nativeHandle < 0) {
        return SQL_ERROR;
    }

    // Execute prepared statement
    long rsHandle = starlake_execute_prepared(stmt->conn->env->graalThread,
                                              stmt->nativeHandle);

    if (rsHandle < 0) {
        starlake_get_last_error(stmt->conn->env->graalThread,
                               stmt->conn->lastError,
                               sizeof(stmt->conn->lastError));
        return SQL_ERROR;
    }

    // Close old result set if exists
    if (stmt->nativeHandle >= 0) {
        starlake_close_result_set(stmt->conn->env->graalThread, stmt->nativeHandle);
    }

    stmt->nativeHandle = rsHandle;
    stmt->columnCount = starlake_get_column_count(stmt->conn->env->graalThread, rsHandle);

    return SQL_SUCCESS;
}

/**
 * SQLBindParameter - Bind a parameter marker
 */
SQLRETURN SQL_API SQLBindParameter(
    SQLHSTMT StatementHandle,
    SQLUSMALLINT ParameterNumber,
    SQLSMALLINT InputOutputType,
    SQLSMALLINT ValueType,
    SQLSMALLINT ParameterType,
    SQLULEN ColumnSize,
    SQLSMALLINT DecimalDigits,
    SQLPOINTER ParameterValuePtr,
    SQLLEN BufferLength,
    SQLLEN* StrLen_or_IndPtr)
{
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT) {
        return SQL_INVALID_HANDLE;
    }

    if (stmt->nativeHandle < 0 || ParameterValuePtr == NULL) {
        return SQL_ERROR;
    }

    int result = -1;
    void* graalThread = stmt->conn->env->graalThread;
    long stmtHandle = stmt->nativeHandle;

    // Handle different value types
    switch (ValueType) {
        case SQL_C_CHAR:
        case SQL_C_WCHAR:
            result = starlake_set_string(
                graalThread,
                stmtHandle,
                ParameterNumber,
                (const char*)ParameterValuePtr
            );
            break;

        case SQL_C_LONG:
        case SQL_C_SLONG:
        case SQL_C_ULONG:
            result = starlake_set_int(
                graalThread,
                stmtHandle,
                ParameterNumber,
                *(int*)ParameterValuePtr
            );
            break;

        case SQL_C_SHORT:
        case SQL_C_SSHORT:
        case SQL_C_USHORT:
            result = starlake_set_int(
                graalThread,
                stmtHandle,
                ParameterNumber,
                (int)(*(short*)ParameterValuePtr)
            );
            break;

        case SQL_C_TINYINT:
        case SQL_C_STINYINT:
        case SQL_C_UTINYINT:
            result = starlake_set_int(
                graalThread,
                stmtHandle,
                ParameterNumber,
                (int)(*(char*)ParameterValuePtr)
            );
            break;

        case SQL_C_SBIGINT:
        case SQL_C_UBIGINT:
            result = starlake_set_long(
                graalThread,
                stmtHandle,
                ParameterNumber,
                *(long long*)ParameterValuePtr
            );
            break;

        case SQL_C_FLOAT:
            result = starlake_set_float(
                graalThread,
                stmtHandle,
                ParameterNumber,
                *(float*)ParameterValuePtr
            );
            break;

        case SQL_C_DOUBLE:
            result = starlake_set_double(
                graalThread,
                stmtHandle,
                ParameterNumber,
                *(double*)ParameterValuePtr
            );
            break;

        case SQL_C_BIT:
            result = starlake_set_boolean(
                graalThread,
                stmtHandle,
                ParameterNumber,
                *(char*)ParameterValuePtr
            );
            break;

        case SQL_C_NUMERIC: {
            // Convert SQL_NUMERIC_STRUCT to double
            SQL_NUMERIC_STRUCT* numeric = (SQL_NUMERIC_STRUCT*)ParameterValuePtr;
            long long intValue;
            memcpy(&intValue, numeric->val, sizeof(long long));

            double value = (double)intValue / 100.0;
            if (numeric->sign == 0) {
                value = -value;
            }

            result = starlake_set_double(
                graalThread,
                stmtHandle,
                ParameterNumber,
                value
            );
            break;
        }

        case SQL_C_TYPE_DATE:
        case SQL_C_DATE: {
            // Convert DATE_STRUCT to epoch days
            DATE_STRUCT* dateStruct = (DATE_STRUCT*)ParameterValuePtr;

            // Simple conversion (production would handle leap years)
            long epochDays = (dateStruct->year - 1970) * 365 +
                            (dateStruct->month - 1) * 30 +
                            (dateStruct->day - 1);

            result = starlake_set_date(
                graalThread,
                stmtHandle,
                ParameterNumber,
                epochDays
            );
            break;
        }

        case SQL_C_TYPE_TIME:
        case SQL_C_TIME: {
            // Convert TIME_STRUCT to milliseconds since midnight
            TIME_STRUCT* timeStruct = (TIME_STRUCT*)ParameterValuePtr;

            long millisSinceMidnight = (timeStruct->hour * 3600 +
                                       timeStruct->minute * 60 +
                                       timeStruct->second) * 1000;

            result = starlake_set_time(
                graalThread,
                stmtHandle,
                ParameterNumber,
                millisSinceMidnight
            );
            break;
        }

        case SQL_C_TYPE_TIMESTAMP:
        case SQL_C_TIMESTAMP: {
            // Convert TIMESTAMP_STRUCT to milliseconds since epoch
            TIMESTAMP_STRUCT* tsStruct = (TIMESTAMP_STRUCT*)ParameterValuePtr;

            // Simple conversion
            long days = (tsStruct->year - 1970) * 365 +
                       (tsStruct->month - 1) * 30 +
                       (tsStruct->day - 1);

            long millisSinceEpoch = days * 24 * 60 * 60 * 1000 +
                                   tsStruct->hour * 3600 * 1000 +
                                   tsStruct->minute * 60 * 1000 +
                                   tsStruct->second * 1000 +
                                   tsStruct->fraction / 1000000;

            result = starlake_set_timestamp(
                graalThread,
                stmtHandle,
                ParameterNumber,
                millisSinceEpoch
            );
            break;
        }

        case SQL_C_BINARY: {
            // Binary data
            int dataLength = (int)BufferLength;
            if (StrLen_or_IndPtr != NULL && *StrLen_or_IndPtr != SQL_NTS) {
                dataLength = (int)*StrLen_or_IndPtr;
            }

            result = starlake_set_bytes(
                graalThread,
                stmtHandle,
                ParameterNumber,
                (const char*)ParameterValuePtr,
                dataLength
            );
            break;
        }

        default:
            return SQL_ERROR;
    }

    return (result == 0) ? SQL_SUCCESS : SQL_ERROR;
}

/**
 * SQLEndTran - Commit or rollback a transaction
 */
SQLRETURN SQL_API SQLEndTran(
    SQLSMALLINT HandleType,
    SQLHANDLE Handle,
    SQLSMALLINT CompletionType)
{
    if (HandleType == SQL_HANDLE_DBC) {
        StarlakeConnection* conn = (StarlakeConnection*)Handle;
        if (conn == NULL || conn->handleType != SQL_HANDLE_DBC) {
            return SQL_INVALID_HANDLE;
        }

        if (conn->nativeHandle < 0) {
            return SQL_ERROR;
        }

        int result;
        if (CompletionType == SQL_COMMIT) {
            result = starlake_commit(conn->env->graalThread, conn->nativeHandle);
        } else if (CompletionType == SQL_ROLLBACK) {
            result = starlake_rollback(conn->env->graalThread, conn->nativeHandle);
        } else {
            return SQL_ERROR;
        }

        return (result == 0) ? SQL_SUCCESS : SQL_ERROR;
    }

    return SQL_ERROR;
}

/**
 * SQLSetConnectAttr - Set connection attribute
 */
SQLRETURN SQL_API SQLSetConnectAttr(
    SQLHDBC ConnectionHandle,
    SQLINTEGER Attribute,
    SQLPOINTER ValuePtr,
    SQLINTEGER StringLength)
{
    StarlakeConnection* conn = (StarlakeConnection*)ConnectionHandle;
    if (conn == NULL || conn->handleType != SQL_HANDLE_DBC) {
        return SQL_INVALID_HANDLE;
    }

    if (conn->nativeHandle < 0) {
        return SQL_ERROR;
    }

    switch (Attribute) {
        case SQL_ATTR_AUTOCOMMIT: {
            SQLUINTEGER autoCommit = (SQLUINTEGER)(SQLULEN)ValuePtr;
            int value = (autoCommit == SQL_AUTOCOMMIT_ON) ? 1 : 0;
            int result = starlake_set_auto_commit(conn->env->graalThread,
                                                  conn->nativeHandle,
                                                  value);
            return (result == 0) ? SQL_SUCCESS : SQL_ERROR;
        }

        default:
            // Attribute not supported
            return SQL_SUCCESS_WITH_INFO;
    }
}

/**
 * SQLGetConnectAttr - Get connection attribute
 */
SQLRETURN SQL_API SQLGetConnectAttr(
    SQLHDBC ConnectionHandle,
    SQLINTEGER Attribute,
    SQLPOINTER ValuePtr,
    SQLINTEGER BufferLength,
    SQLINTEGER* StringLengthPtr)
{
    StarlakeConnection* conn = (StarlakeConnection*)ConnectionHandle;
    if (conn == NULL || conn->handleType != SQL_HANDLE_DBC) {
        return SQL_INVALID_HANDLE;
    }

    if (conn->nativeHandle < 0) {
        return SQL_ERROR;
    }

    switch (Attribute) {
        case SQL_ATTR_AUTOCOMMIT: {
            int result = starlake_get_auto_commit(conn->env->graalThread,
                                                  conn->nativeHandle);
            if (result < 0) {
                return SQL_ERROR;
            }
            *(SQLUINTEGER*)ValuePtr = (result == 1) ? SQL_AUTOCOMMIT_ON : SQL_AUTOCOMMIT_OFF;
            return SQL_SUCCESS;
        }

        default:
            return SQL_ERROR;
    }
}

/**
 * SQLGetTypeInfo - Get information about data types supported by the driver
 */
SQLRETURN SQL_API SQLGetTypeInfo(
    SQLHSTMT StatementHandle,
    SQLSMALLINT DataType)
{
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT) {
        return SQL_INVALID_HANDLE;
    }

    if (stmt->conn->nativeHandle < 0) {
        return SQL_ERROR;
    }

    // Get type info via native image
    long rsHandle = starlake_get_type_info(
        stmt->conn->env->graalThread,
        stmt->conn->nativeHandle,
        DataType
    );

    if (rsHandle < 0) {
        starlake_get_last_error(stmt->conn->env->graalThread,
                               stmt->conn->lastError,
                               sizeof(stmt->conn->lastError));
        return SQL_ERROR;
    }

    stmt->nativeHandle = rsHandle;
    stmt->columnCount = starlake_get_column_count(stmt->conn->env->graalThread, rsHandle);

    return SQL_SUCCESS;
}

/**
 * SQLTables - Get list of tables
 */
SQLRETURN SQL_API SQLTables(
    SQLHSTMT StatementHandle,
    SQLCHAR* CatalogName,
    SQLSMALLINT NameLength1,
    SQLCHAR* SchemaName,
    SQLSMALLINT NameLength2,
    SQLCHAR* TableName,
    SQLSMALLINT NameLength3,
    SQLCHAR* TableType,
    SQLSMALLINT NameLength4)
{
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT) {
        return SQL_INVALID_HANDLE;
    }

    if (stmt->conn->nativeHandle < 0) {
        return SQL_ERROR;
    }

    // Get tables via native image
    long rsHandle = starlake_get_tables(
        stmt->conn->env->graalThread,
        stmt->conn->nativeHandle,
        (const char*)CatalogName,
        (const char*)SchemaName,
        (const char*)TableName,
        (const char*)TableType
    );

    if (rsHandle < 0) {
        starlake_get_last_error(stmt->conn->env->graalThread,
                               stmt->conn->lastError,
                               sizeof(stmt->conn->lastError));
        return SQL_ERROR;
    }

    stmt->nativeHandle = rsHandle;
    stmt->columnCount = starlake_get_column_count(stmt->conn->env->graalThread, rsHandle);

    return SQL_SUCCESS;
}

/**
 * SQLColumns - Get list of columns
 */
SQLRETURN SQL_API SQLColumns(
    SQLHSTMT StatementHandle,
    SQLCHAR* CatalogName,
    SQLSMALLINT NameLength1,
    SQLCHAR* SchemaName,
    SQLSMALLINT NameLength2,
    SQLCHAR* TableName,
    SQLSMALLINT NameLength3,
    SQLCHAR* ColumnName,
    SQLSMALLINT NameLength4)
{
    StarlakeStatement* stmt = (StarlakeStatement*)StatementHandle;
    if (stmt == NULL || stmt->handleType != SQL_HANDLE_STMT) {
        return SQL_INVALID_HANDLE;
    }

    if (stmt->conn->nativeHandle < 0) {
        return SQL_ERROR;
    }

    // Get columns via native image
    long rsHandle = starlake_get_columns(
        stmt->conn->env->graalThread,
        stmt->conn->nativeHandle,
        (const char*)CatalogName,
        (const char*)SchemaName,
        (const char*)TableName,
        (const char*)ColumnName
    );

    if (rsHandle < 0) {
        starlake_get_last_error(stmt->conn->env->graalThread,
                               stmt->conn->lastError,
                               sizeof(stmt->conn->lastError));
        return SQL_ERROR;
    }

    stmt->nativeHandle = rsHandle;
    stmt->columnCount = starlake_get_column_count(stmt->conn->env->graalThread, rsHandle);

    return SQL_SUCCESS;
}

/**
 * SQLGetDiagRec - Get diagnostic record
 */
SQLRETURN SQL_API SQLGetDiagRec(
    SQLSMALLINT HandleType,
    SQLHANDLE Handle,
    SQLSMALLINT RecNumber,
    SQLCHAR* SQLState,
    SQLINTEGER* NativeErrorPtr,
    SQLCHAR* MessageText,
    SQLSMALLINT BufferLength,
    SQLSMALLINT* TextLengthPtr)
{
    if (RecNumber != 1) {
        return SQL_NO_DATA;
    }

    char* errorMsg = NULL;

    switch (HandleType) {
        case SQL_HANDLE_DBC: {
            StarlakeConnection* conn = (StarlakeConnection*)Handle;
            if (conn == NULL) return SQL_INVALID_HANDLE;
            errorMsg = conn->lastError;
            break;
        }
        case SQL_HANDLE_STMT: {
            StarlakeStatement* stmt = (StarlakeStatement*)Handle;
            if (stmt == NULL) return SQL_INVALID_HANDLE;
            errorMsg = stmt->conn->lastError;
            break;
        }
        default:
            return SQL_INVALID_HANDLE;
    }

    if (errorMsg == NULL || errorMsg[0] == '\0') {
        return SQL_NO_DATA;
    }

    // Set SQLSTATE (generic error)
    if (SQLState != NULL) {
        strcpy((char*)SQLState, "HY000");
    }

    // Set native error code
    if (NativeErrorPtr != NULL) {
        *NativeErrorPtr = 1;
    }

    // Copy error message
    if (MessageText != NULL && BufferLength > 0) {
        strncpy((char*)MessageText, errorMsg, BufferLength - 1);
        MessageText[BufferLength - 1] = '\0';

        if (TextLengthPtr != NULL) {
            *TextLengthPtr = (SQLSMALLINT)strlen((char*)MessageText);
        }
    }

    return SQL_SUCCESS;
}

/**
 * SQLSetEnvAttr - Set environment attribute
 */
SQLRETURN SQL_API SQLSetEnvAttr(
    SQLHENV EnvironmentHandle,
    SQLINTEGER Attribute,
    SQLPOINTER ValuePtr,
    SQLINTEGER StringLength)
{
    StarlakeEnv* env = (StarlakeEnv*)EnvironmentHandle;
    if (env == NULL || env->handleType != SQL_HANDLE_ENV) {
        return SQL_INVALID_HANDLE;
    }

    // Accept ODBC version setting
    if (Attribute == SQL_ATTR_ODBC_VERSION) {
        return SQL_SUCCESS;
    }

    return SQL_SUCCESS_WITH_INFO;
}

/**
 * SQLGetEnvAttr - Get environment attribute
 */
SQLRETURN SQL_API SQLGetEnvAttr(
    SQLHENV EnvironmentHandle,
    SQLINTEGER Attribute,
    SQLPOINTER ValuePtr,
    SQLINTEGER BufferLength,
    SQLINTEGER* StringLengthPtr)
{
    StarlakeEnv* env = (StarlakeEnv*)EnvironmentHandle;
    if (env == NULL || env->handleType != SQL_HANDLE_ENV) {
        return SQL_INVALID_HANDLE;
    }

    if (Attribute == SQL_ATTR_ODBC_VERSION) {
        *(SQLINTEGER*)ValuePtr = SQL_OV_ODBC3;
        return SQL_SUCCESS;
    }

    return SQL_ERROR;
}

