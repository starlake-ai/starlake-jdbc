/**
 * Starlake Native Image Bridge Header
 * 
 * This header defines the C interface to the GraalVM Native Image compiled
 * Starlake JDBC driver. These functions are called by the ODBC wrapper.
 */

#ifndef STARLAKE_NATIVE_H
#define STARLAKE_NATIVE_H

#ifdef _WIN32
    #define STARLAKE_API __declspec(dllimport)
#else
    #define STARLAKE_API
#endif

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initialize the Starlake JDBC driver.
 * Must be called before any other functions.
 * 
 * @return 0 on success, -1 on error
 */
STARLAKE_API int starlake_init(void* thread);

/**
 * Connect to a database.
 * 
 * @param thread GraalVM isolate thread
 * @param url JDBC connection URL (e.g., "jdbc:starlake:h2:mem:")
 * @param user Username (can be NULL)
 * @param password Password (can be NULL)
 * @return Connection handle (positive) or error code (negative)
 */
STARLAKE_API long starlake_connect(void* thread, const char* url, 
                                   const char* user, const char* password);

/**
 * Disconnect from database.
 * 
 * @param thread GraalVM isolate thread
 * @param conn_handle Connection handle
 * @return 0 on success, -1 on error
 */
STARLAKE_API int starlake_disconnect(void* thread, long conn_handle);

/**
 * Execute a SQL query.
 * 
 * @param thread GraalVM isolate thread
 * @param conn_handle Connection handle
 * @param sql SQL query string
 * @return ResultSet handle (positive) or error code (negative)
 */
STARLAKE_API long starlake_execute_query(void* thread, long conn_handle, const char* sql);

/**
 * Fetch next row from result set.
 * 
 * @param thread GraalVM isolate thread
 * @param rs_handle ResultSet handle
 * @return 1 if row available, 0 if no more rows, -1 on error
 */
STARLAKE_API int starlake_fetch(void* thread, long rs_handle);

/**
 * Get column count from result set.
 * 
 * @param thread GraalVM isolate thread
 * @param rs_handle ResultSet handle
 * @return Column count (positive) or error code (negative)
 */
STARLAKE_API int starlake_get_column_count(void* thread, long rs_handle);

/**
 * Get column name.
 * 
 * @param thread GraalVM isolate thread
 * @param rs_handle ResultSet handle
 * @param column_index Column index (1-based)
 * @param buffer Output buffer for column name
 * @param buffer_size Buffer size
 * @return 0 on success, -1 on error
 */
STARLAKE_API int starlake_get_column_name(void* thread, long rs_handle, int column_index,
                                          char* buffer, int buffer_size);

/**
 * Get column value as string.
 * 
 * @param thread GraalVM isolate thread
 * @param rs_handle ResultSet handle
 * @param column_index Column index (1-based)
 * @param buffer Output buffer for value
 * @param buffer_size Buffer size
 * @return 0 on success, -1 on error
 */
STARLAKE_API int starlake_get_string(void* thread, long rs_handle, int column_index,
                                     char* buffer, int buffer_size);

/**
 * Close result set.
 * 
 * @param thread GraalVM isolate thread
 * @param rs_handle ResultSet handle
 * @return 0 on success, -1 on error
 */
STARLAKE_API int starlake_close_result_set(void* thread, long rs_handle);

/**
 * Get last error message.
 *
 * @param thread GraalVM isolate thread
 * @param buffer Output buffer for error message
 * @param buffer_size Buffer size
 * @return 0 on success, -1 if no error
 */
STARLAKE_API int starlake_get_last_error(void* thread, char* buffer, int buffer_size);

// ========== PREPARED STATEMENT SUPPORT ==========

/**
 * Prepare a SQL statement.
 */
STARLAKE_API long starlake_prepare(void* thread, long conn_handle, const char* sql);

/**
 * Execute a prepared statement.
 */
STARLAKE_API long starlake_execute_prepared(void* thread, long stmt_handle);

/**
 * Set string parameter for prepared statement.
 */
STARLAKE_API int starlake_set_string(void* thread, long stmt_handle, int param_index, const char* value);

/**
 * Set integer parameter for prepared statement.
 */
STARLAKE_API int starlake_set_int(void* thread, long stmt_handle, int param_index, int value);

/**
 * Set long parameter for prepared statement.
 */
STARLAKE_API int starlake_set_long(void* thread, long stmt_handle, int param_index, long value);

/**
 * Set double parameter for prepared statement.
 */
STARLAKE_API int starlake_set_double(void* thread, long stmt_handle, int param_index, double value);

// ========== RESULT SET DATA RETRIEVAL ==========

/**
 * Get integer value from result set.
 */
STARLAKE_API int starlake_get_int(void* thread, long rs_handle, int column_index);

/**
 * Get long value from result set.
 */
STARLAKE_API long starlake_get_long(void* thread, long rs_handle, int column_index);

/**
 * Get double value from result set.
 */
STARLAKE_API double starlake_get_double(void* thread, long rs_handle, int column_index);

/**
 * Check if last value was NULL.
 */
STARLAKE_API int starlake_was_null(void* thread, long rs_handle);

/**
 * Get column type from result set metadata.
 */
STARLAKE_API int starlake_get_column_type(void* thread, long rs_handle, int column_index);

/**
 * Get column type name from result set metadata.
 */
STARLAKE_API int starlake_get_column_type_name(void* thread, long rs_handle, int column_index,
                                                char* buffer, int buffer_size);

/**
 * Get column precision (for numeric types, this is the total number of digits).
 *
 * @param thread GraalVM isolate thread
 * @param rs_handle ResultSet handle
 * @param column_index Column index (1-based)
 * @return Column precision or -1 on error
 */
STARLAKE_API int starlake_get_column_precision(void* thread, long rs_handle, int column_index);

/**
 * Get column scale (for numeric types, this is the number of digits after decimal point).
 *
 * @param thread GraalVM isolate thread
 * @param rs_handle ResultSet handle
 * @param column_index Column index (1-based)
 * @return Column scale or -1 on error
 */
STARLAKE_API int starlake_get_column_scale(void* thread, long rs_handle, int column_index);

/**
 * Get column nullable status.
 *
 * @param thread GraalVM isolate thread
 * @param rs_handle ResultSet handle
 * @param column_index Column index (1-based)
 * @return 0 = not nullable, 1 = nullable, 2 = unknown, -1 on error
 */
STARLAKE_API int starlake_get_column_nullable(void* thread, long rs_handle, int column_index);

/**
 * Get column display size (maximum number of characters needed to display the data).
 *
 * @param thread GraalVM isolate thread
 * @param rs_handle ResultSet handle
 * @param column_index Column index (1-based)
 * @return Column display size or -1 on error
 */
STARLAKE_API int starlake_get_column_display_size(void* thread, long rs_handle, int column_index);

// ========== TRANSACTION MANAGEMENT ==========

/**
 * Commit current transaction.
 */
STARLAKE_API int starlake_commit(void* thread, long conn_handle);

/**
 * Rollback current transaction.
 */
STARLAKE_API int starlake_rollback(void* thread, long conn_handle);

/**
 * Set auto-commit mode.
 */
STARLAKE_API int starlake_set_auto_commit(void* thread, long conn_handle, int auto_commit);

/**
 * Get auto-commit mode.
 */
STARLAKE_API int starlake_get_auto_commit(void* thread, long conn_handle);

// ========== METADATA AND CATALOG FUNCTIONS ==========

/**
 * Get list of tables.
 */
STARLAKE_API long starlake_get_tables(void* thread, long conn_handle,
                                      const char* catalog, const char* schema,
                                      const char* table, const char* types);

/**
 * Get list of columns.
 */
STARLAKE_API long starlake_get_columns(void* thread, long conn_handle,
                                       const char* catalog, const char* schema,
                                       const char* table, const char* column);

/**
 * Get list of primary keys.
 */
STARLAKE_API long starlake_get_primary_keys(void* thread, long conn_handle,
                                            const char* catalog, const char* schema,
                                            const char* table);

/**
 * Get type info for a specific SQL type or all types.
 *
 * @param thread GraalVM isolate thread
 * @param conn_handle Connection handle
 * @param data_type SQL data type (SQL_ALL_TYPES for all types)
 * @return ResultSet handle (positive) or error code (negative)
 */
STARLAKE_API long starlake_get_type_info(void* thread, long conn_handle, int data_type);

/**
 * Get database product name.
 */
STARLAKE_API int starlake_get_database_product_name(void* thread, long conn_handle,
                                                     char* buffer, int buffer_size);

/**
 * Get database product version.
 */
STARLAKE_API int starlake_get_database_product_version(void* thread, long conn_handle,
                                                        char* buffer, int buffer_size);

// ========== EXECUTE UPDATE (INSERT/UPDATE/DELETE) ==========

/**
 * Execute an UPDATE/INSERT/DELETE statement.
 */
STARLAKE_API int starlake_execute_update(void* thread, long conn_handle, const char* sql);

/**
 * Execute prepared UPDATE/INSERT/DELETE statement.
 */
STARLAKE_API int starlake_execute_prepared_update(void* thread, long stmt_handle);

/**
 * Close a statement.
 */
STARLAKE_API int starlake_close_statement(void* thread, long stmt_handle);

// ========== DATE/TIME SUPPORT ==========

/**
 * Get date value (as epoch days).
 */
STARLAKE_API long starlake_get_date(void* thread, long rs_handle, int column_index);

/**
 * Get time value (as milliseconds since midnight).
 */
STARLAKE_API long starlake_get_time(void* thread, long rs_handle, int column_index);

/**
 * Get timestamp value (as milliseconds since epoch).
 */
STARLAKE_API long starlake_get_timestamp(void* thread, long rs_handle, int column_index);

/**
 * Set date parameter (from epoch days).
 */
STARLAKE_API int starlake_set_date(void* thread, long stmt_handle, int param_index, long epoch_days);

/**
 * Set time parameter (from milliseconds since midnight).
 */
STARLAKE_API int starlake_set_time(void* thread, long stmt_handle, int param_index, long millis_since_midnight);

/**
 * Set timestamp parameter (from milliseconds since epoch).
 */
STARLAKE_API int starlake_set_timestamp(void* thread, long stmt_handle, int param_index, long millis_since_epoch);

// ========== BINARY DATA SUPPORT ==========

/**
 * Get binary data from result set.
 */
STARLAKE_API int starlake_get_bytes(void* thread, long rs_handle, int column_index, char* buffer, int buffer_size);

/**
 * Set binary parameter.
 */
STARLAKE_API int starlake_set_bytes(void* thread, long stmt_handle, int param_index, const char* data, int data_length);

// ========== BOOLEAN SUPPORT ==========

/**
 * Get boolean value (1 for true, 0 for false).
 */
STARLAKE_API int starlake_get_boolean(void* thread, long rs_handle, int column_index);

/**
 * Set boolean parameter.
 */
STARLAKE_API int starlake_set_boolean(void* thread, long stmt_handle, int param_index, int value);

// ========== FLOAT SUPPORT ==========

/**
 * Get float value.
 */
STARLAKE_API float starlake_get_float(void* thread, long rs_handle, int column_index);

/**
 * Set float parameter.
 */
STARLAKE_API int starlake_set_float(void* thread, long stmt_handle, int param_index, float value);

#ifdef __cplusplus
}
#endif

#endif /* STARLAKE_NATIVE_H */

