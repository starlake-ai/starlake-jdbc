/**
 * Starlake.AI JDBC Driver is a JDBC Proxy Driver for supporting staging of DDL and DML statements.
 * Copyright (C) 2025 Starlake.AI <hayssam.saleh@starlake.ai>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.starlake.jdbc;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bridge class for exposing Starlake JDBC functionality to native ODBC wrapper.
 * This class provides C-callable entry points via GraalVM Native Image.
 */
public class NativeImageBridge {
  private static final Logger LOGGER = Logger.getLogger(NativeImageBridge.class.getName());
  
  // Handle management
  private static final AtomicLong handleCounter = new AtomicLong(1);
  private static final Map<Long, Connection> connections = new ConcurrentHashMap<>();
  private static final Map<Long, Statement> statements = new ConcurrentHashMap<>();
  private static final Map<Long, ResultSet> resultSets = new ConcurrentHashMap<>();
  
  // Thread-local error storage
  private static final ThreadLocal<String> lastError = new ThreadLocal<>();

  /**
   * Initialize the Starlake JDBC driver.
   * Called once when ODBC driver loads.
   */
  @CEntryPoint(name = "starlake_init")
  public static int init(IsolateThread thread) {
    try {
      // Register the Starlake driver
      Class.forName("ai.starlake.jdbc.StarlakeDriver");
      LOGGER.info("Starlake JDBC Driver initialized for native image");
      return 0; // Success
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Failed to initialize driver", e);
      lastError.set(e.getMessage());
      return -1; // Error
    }
  }

  /**
   * Connect to a database.
   * 
   * @param thread GraalVM isolate thread
   * @param urlPtr Connection URL (C string)
   * @param userPtr Username (C string)
   * @param passwordPtr Password (C string)
   * @return Connection handle (positive) or error code (negative)
   */
  @CEntryPoint(name = "starlake_connect")
  public static long connect(IsolateThread thread, CCharPointer urlPtr, 
                             CCharPointer userPtr, CCharPointer passwordPtr) {
    try {
      String url = CTypeConversion.toJavaString(urlPtr);
      String user = CTypeConversion.toJavaString(userPtr);
      String password = CTypeConversion.toJavaString(passwordPtr);
      
      Properties props = new Properties();
      if (user != null && !user.isEmpty()) {
        props.setProperty("user", user);
      }
      if (password != null && !password.isEmpty()) {
        props.setProperty("password", password);
      }
      
      Connection conn = DriverManager.getConnection(url, props);
      long handle = handleCounter.getAndIncrement();
      connections.put(handle, conn);
      
      LOGGER.info("Connected: handle=" + handle + ", url=" + url);
      return handle;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Connection failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Disconnect from database.
   * 
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_disconnect")
  public static int disconnect(IsolateThread thread, long connHandle) {
    try {
      Connection conn = connections.remove(connHandle);
      if (conn != null) {
        conn.close();
        LOGGER.info("Disconnected: handle=" + connHandle);
        return 0;
      }
      lastError.set("Invalid connection handle");
      return -1;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Disconnect failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Execute a SQL query.
   * 
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @param sqlPtr SQL query (C string)
   * @return ResultSet handle (positive) or error code (negative)
   */
  @CEntryPoint(name = "starlake_execute_query")
  public static long executeQuery(IsolateThread thread, long connHandle, CCharPointer sqlPtr) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }
      
      String sql = CTypeConversion.toJavaString(sqlPtr);
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql);
      
      long stmtHandle = handleCounter.getAndIncrement();
      long rsHandle = handleCounter.getAndIncrement();
      
      statements.put(stmtHandle, stmt);
      resultSets.put(rsHandle, rs);
      
      LOGGER.info("Query executed: " + sql);
      return rsHandle;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Query execution failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Fetch next row from result set.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @return 1 if row available, 0 if no more rows, -1 on error
   */
  @CEntryPoint(name = "starlake_fetch")
  public static int fetch(IsolateThread thread, long rsHandle) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }
      return rs.next() ? 1 : 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Fetch failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get column count from result set.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @return Column count (positive) or error code (negative)
   */
  @CEntryPoint(name = "starlake_get_column_count")
  public static int getColumnCount(IsolateThread thread, long rsHandle) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }
      return rs.getMetaData().getColumnCount();
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get column count failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get column name.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @param bufferPtr Output buffer for column name
   * @param bufferSize Buffer size
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_get_column_name")
  public static int getColumnName(IsolateThread thread, long rsHandle, int columnIndex,
                                  CCharPointer bufferPtr, int bufferSize) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }

      String columnName = rs.getMetaData().getColumnName(columnIndex);
      CTypeConversion.toCString(columnName, bufferPtr, WordFactory.unsigned(bufferSize));
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get column name failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get column value as string.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @param bufferPtr Output buffer for value
   * @param bufferSize Buffer size
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_get_string")
  public static int getString(IsolateThread thread, long rsHandle, int columnIndex,
                              CCharPointer bufferPtr, int bufferSize) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }

      String value = rs.getString(columnIndex);
      if (value == null) {
        value = "";
      }
      CTypeConversion.toCString(value, bufferPtr, WordFactory.unsigned(bufferSize));
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get string failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Close result set.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_close_result_set")
  public static int closeResultSet(IsolateThread thread, long rsHandle) {
    try {
      ResultSet rs = resultSets.remove(rsHandle);
      if (rs != null) {
        Statement stmt = rs.getStatement();
        rs.close();
        if (stmt != null) {
          stmt.close();
        }
        return 0;
      }
      lastError.set("Invalid result set handle");
      return -1;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Close result set failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get last error message.
   *
   * @param thread GraalVM isolate thread
   * @param bufferPtr Output buffer for error message
   * @param bufferSize Buffer size
   * @return 0 on success, -1 if no error
   */
  @CEntryPoint(name = "starlake_get_last_error")
  public static int getLastError(IsolateThread thread, CCharPointer bufferPtr, int bufferSize) {
    String error = lastError.get();
    if (error != null) {
      CTypeConversion.toCString(error, bufferPtr, WordFactory.unsigned(bufferSize));
      return 0;
    }
    return -1;
  }

  // ========== PREPARED STATEMENT SUPPORT ==========

  /**
   * Prepare a SQL statement.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @param sqlPtr SQL statement with ? placeholders
   * @return PreparedStatement handle (positive) or error code (negative)
   */
  @CEntryPoint(name = "starlake_prepare")
  public static long prepare(IsolateThread thread, long connHandle, CCharPointer sqlPtr) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }

      String sql = CTypeConversion.toJavaString(sqlPtr);
      Statement stmt = conn.prepareStatement(sql);

      long stmtHandle = handleCounter.getAndIncrement();
      statements.put(stmtHandle, stmt);

      LOGGER.info("Statement prepared: " + sql);
      return stmtHandle;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Prepare failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Execute a prepared statement.
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @return ResultSet handle (positive) or error code (negative)
   */
  @CEntryPoint(name = "starlake_execute_prepared")
  public static long executePrepared(IsolateThread thread, long stmtHandle) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null) {
        lastError.set("Invalid statement handle");
        return -1;
      }

      if (!(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Not a prepared statement");
        return -1;
      }

      java.sql.PreparedStatement pstmt = (java.sql.PreparedStatement) stmt;
      ResultSet rs = pstmt.executeQuery();

      long rsHandle = handleCounter.getAndIncrement();
      resultSets.put(rsHandle, rs);

      return rsHandle;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Execute prepared failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Set string parameter for prepared statement.
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @param paramIndex Parameter index (1-based)
   * @param valuePtr Parameter value
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_set_string")
  public static int setString(IsolateThread thread, long stmtHandle, int paramIndex,
                              CCharPointer valuePtr) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null || !(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Invalid prepared statement handle");
        return -1;
      }

      String value = CTypeConversion.toJavaString(valuePtr);
      ((java.sql.PreparedStatement) stmt).setString(paramIndex, value);
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Set string parameter failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Set integer parameter for prepared statement.
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @param paramIndex Parameter index (1-based)
   * @param value Parameter value
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_set_int")
  public static int setInt(IsolateThread thread, long stmtHandle, int paramIndex, int value) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null || !(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Invalid prepared statement handle");
        return -1;
      }

      ((java.sql.PreparedStatement) stmt).setInt(paramIndex, value);
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Set int parameter failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Set long parameter for prepared statement.
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @param paramIndex Parameter index (1-based)
   * @param value Parameter value
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_set_long")
  public static int setLong(IsolateThread thread, long stmtHandle, int paramIndex, long value) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null || !(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Invalid prepared statement handle");
        return -1;
      }

      ((java.sql.PreparedStatement) stmt).setLong(paramIndex, value);
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Set long parameter failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Set double parameter for prepared statement.
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @param paramIndex Parameter index (1-based)
   * @param value Parameter value
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_set_double")
  public static int setDouble(IsolateThread thread, long stmtHandle, int paramIndex, double value) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null || !(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Invalid prepared statement handle");
        return -1;
      }

      ((java.sql.PreparedStatement) stmt).setDouble(paramIndex, value);
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Set double parameter failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  // ========== RESULT SET DATA RETRIEVAL ==========

  /**
   * Get integer value from result set.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return Integer value or 0 on error
   */
  @CEntryPoint(name = "starlake_get_int")
  public static int getInt(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return 0;
      }
      return rs.getInt(columnIndex);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get int failed", e);
      lastError.set(e.getMessage());
      return 0;
    }
  }

  /**
   * Get long value from result set.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return Long value or 0 on error
   */
  @CEntryPoint(name = "starlake_get_long")
  public static long getLong(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return 0;
      }
      return rs.getLong(columnIndex);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get long failed", e);
      lastError.set(e.getMessage());
      return 0;
    }
  }

  /**
   * Get double value from result set.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return Double value or 0.0 on error
   */
  @CEntryPoint(name = "starlake_get_double")
  public static double getDouble(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return 0.0;
      }
      return rs.getDouble(columnIndex);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get double failed", e);
      lastError.set(e.getMessage());
      return 0.0;
    }
  }

  /**
   * Check if last value was NULL.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @return 1 if NULL, 0 if not NULL, -1 on error
   */
  @CEntryPoint(name = "starlake_was_null")
  public static int wasNull(IsolateThread thread, long rsHandle) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }
      return rs.wasNull() ? 1 : 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Was null check failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get column type from result set metadata.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return SQL type code or -1 on error
   */
  @CEntryPoint(name = "starlake_get_column_type")
  public static int getColumnType(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }
      return rs.getMetaData().getColumnType(columnIndex);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get column type failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get column type name from result set metadata.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @param bufferPtr Output buffer
   * @param bufferSize Buffer size
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_get_column_type_name")
  public static int getColumnTypeName(IsolateThread thread, long rsHandle, int columnIndex,
                                      CCharPointer bufferPtr, int bufferSize) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }

      String typeName = rs.getMetaData().getColumnTypeName(columnIndex);
      CTypeConversion.toCString(typeName, bufferPtr, WordFactory.unsigned(bufferSize));
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get column type name failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get column precision from result set metadata.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return Column precision or -1 on error
   */
  @CEntryPoint(name = "starlake_get_column_precision")
  public static int getColumnPrecision(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }
      return rs.getMetaData().getPrecision(columnIndex);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get column precision failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get column scale from result set metadata.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return Column scale or -1 on error
   */
  @CEntryPoint(name = "starlake_get_column_scale")
  public static int getColumnScale(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }
      return rs.getMetaData().getScale(columnIndex);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get column scale failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get column nullable status from result set metadata.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return 0 = not nullable, 1 = nullable, 2 = unknown, -1 on error
   */
  @CEntryPoint(name = "starlake_get_column_nullable")
  public static int getColumnNullable(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }
      return rs.getMetaData().isNullable(columnIndex);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get column nullable failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get column display size from result set metadata.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return Column display size or -1 on error
   */
  @CEntryPoint(name = "starlake_get_column_display_size")
  public static int getColumnDisplaySize(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }
      return rs.getMetaData().getColumnDisplaySize(columnIndex);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get column display size failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  // ========== TRANSACTION MANAGEMENT ==========

  /**
   * Commit current transaction.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_commit")
  public static int commit(IsolateThread thread, long connHandle) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }
      conn.commit();
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Commit failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Rollback current transaction.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_rollback")
  public static int rollback(IsolateThread thread, long connHandle) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }
      conn.rollback();
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Rollback failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Set auto-commit mode.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @param autoCommit 1 for true, 0 for false
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_set_auto_commit")
  public static int setAutoCommit(IsolateThread thread, long connHandle, int autoCommit) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }
      conn.setAutoCommit(autoCommit != 0);
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Set auto-commit failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get auto-commit mode.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @return 1 if auto-commit is on, 0 if off, -1 on error
   */
  @CEntryPoint(name = "starlake_get_auto_commit")
  public static int getAutoCommit(IsolateThread thread, long connHandle) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }
      return conn.getAutoCommit() ? 1 : 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get auto-commit failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  // ========== METADATA AND CATALOG FUNCTIONS ==========

  /**
   * Get list of tables.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @param catalogPtr Catalog name (can be NULL)
   * @param schemaPtr Schema pattern (can be NULL)
   * @param tablePtr Table name pattern (can be NULL)
   * @param typesPtr Table types (can be NULL)
   * @return ResultSet handle (positive) or error code (negative)
   */
  @CEntryPoint(name = "starlake_get_tables")
  public static long getTables(IsolateThread thread, long connHandle,
                               CCharPointer catalogPtr, CCharPointer schemaPtr,
                               CCharPointer tablePtr, CCharPointer typesPtr) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }

      String catalog = catalogPtr.isNull() ? null : CTypeConversion.toJavaString(catalogPtr);
      String schema = schemaPtr.isNull() ? null : CTypeConversion.toJavaString(schemaPtr);
      String table = tablePtr.isNull() ? null : CTypeConversion.toJavaString(tablePtr);
      String[] types = null;
      if (!typesPtr.isNull()) {
        String typesStr = CTypeConversion.toJavaString(typesPtr);
        types = typesStr.split(",");
      }

      ResultSet rs = conn.getMetaData().getTables(catalog, schema, table, types);
      long rsHandle = handleCounter.getAndIncrement();
      resultSets.put(rsHandle, rs);

      return rsHandle;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get tables failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get list of columns.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @param catalogPtr Catalog name (can be NULL)
   * @param schemaPtr Schema pattern (can be NULL)
   * @param tablePtr Table name pattern (can be NULL)
   * @param columnPtr Column name pattern (can be NULL)
   * @return ResultSet handle (positive) or error code (negative)
   */
  @CEntryPoint(name = "starlake_get_columns")
  public static long getColumns(IsolateThread thread, long connHandle,
                                CCharPointer catalogPtr, CCharPointer schemaPtr,
                                CCharPointer tablePtr, CCharPointer columnPtr) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }

      String catalog = catalogPtr.isNull() ? null : CTypeConversion.toJavaString(catalogPtr);
      String schema = schemaPtr.isNull() ? null : CTypeConversion.toJavaString(schemaPtr);
      String table = tablePtr.isNull() ? null : CTypeConversion.toJavaString(tablePtr);
      String column = columnPtr.isNull() ? null : CTypeConversion.toJavaString(columnPtr);

      ResultSet rs = conn.getMetaData().getColumns(catalog, schema, table, column);
      long rsHandle = handleCounter.getAndIncrement();
      resultSets.put(rsHandle, rs);

      return rsHandle;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get columns failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get list of primary keys.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @param catalogPtr Catalog name (can be NULL)
   * @param schemaPtr Schema name (can be NULL)
   * @param tablePtr Table name
   * @return ResultSet handle (positive) or error code (negative)
   */
  @CEntryPoint(name = "starlake_get_primary_keys")
  public static long getPrimaryKeys(IsolateThread thread, long connHandle,
                                    CCharPointer catalogPtr, CCharPointer schemaPtr,
                                    CCharPointer tablePtr) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }

      String catalog = catalogPtr.isNull() ? null : CTypeConversion.toJavaString(catalogPtr);
      String schema = schemaPtr.isNull() ? null : CTypeConversion.toJavaString(schemaPtr);
      String table = CTypeConversion.toJavaString(tablePtr);

      ResultSet rs = conn.getMetaData().getPrimaryKeys(catalog, schema, table);
      long rsHandle = handleCounter.getAndIncrement();
      resultSets.put(rsHandle, rs);

      return rsHandle;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get primary keys failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get type info for a specific SQL type or all types.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @param dataType SQL data type (0 for all types)
   * @return ResultSet handle (positive) or error code (negative)
   */
  @CEntryPoint(name = "starlake_get_type_info")
  public static long getTypeInfo(IsolateThread thread, long connHandle, int dataType) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }

      ResultSet rs = conn.getMetaData().getTypeInfo();
      long rsHandle = handleCounter.getAndIncrement();
      resultSets.put(rsHandle, rs);

      return rsHandle;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get type info failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get database product name.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @param bufferPtr Output buffer
   * @param bufferSize Buffer size
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_get_database_product_name")
  public static int getDatabaseProductName(IsolateThread thread, long connHandle,
                                           CCharPointer bufferPtr, int bufferSize) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }

      String productName = conn.getMetaData().getDatabaseProductName();
      CTypeConversion.toCString(productName, bufferPtr, WordFactory.unsigned(bufferSize));
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get database product name failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Get database product version.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @param bufferPtr Output buffer
   * @param bufferSize Buffer size
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_get_database_product_version")
  public static int getDatabaseProductVersion(IsolateThread thread, long connHandle,
                                              CCharPointer bufferPtr, int bufferSize) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }

      String productVersion = conn.getMetaData().getDatabaseProductVersion();
      CTypeConversion.toCString(productVersion, bufferPtr, WordFactory.unsigned(bufferSize));
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get database product version failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  // ========== EXECUTE UPDATE (INSERT/UPDATE/DELETE) ==========

  /**
   * Execute an UPDATE/INSERT/DELETE statement.
   *
   * @param thread GraalVM isolate thread
   * @param connHandle Connection handle
   * @param sqlPtr SQL statement
   * @return Number of rows affected or -1 on error
   */
  @CEntryPoint(name = "starlake_execute_update")
  public static int executeUpdate(IsolateThread thread, long connHandle, CCharPointer sqlPtr) {
    try {
      Connection conn = connections.get(connHandle);
      if (conn == null) {
        lastError.set("Invalid connection handle");
        return -1;
      }

      String sql = CTypeConversion.toJavaString(sqlPtr);
      Statement stmt = conn.createStatement();
      int rowCount = stmt.executeUpdate(sql);
      stmt.close();

      LOGGER.info("Update executed: " + sql + ", rows affected: " + rowCount);
      return rowCount;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Execute update failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Execute prepared UPDATE/INSERT/DELETE statement.
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @return Number of rows affected or -1 on error
   */
  @CEntryPoint(name = "starlake_execute_prepared_update")
  public static int executePreparedUpdate(IsolateThread thread, long stmtHandle) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null || !(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Invalid prepared statement handle");
        return -1;
      }

      java.sql.PreparedStatement pstmt = (java.sql.PreparedStatement) stmt;
      return pstmt.executeUpdate();
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Execute prepared update failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Close a statement.
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle Statement handle
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_close_statement")
  public static int closeStatement(IsolateThread thread, long stmtHandle) {
    try {
      Statement stmt = statements.remove(stmtHandle);
      if (stmt != null) {
        stmt.close();
        return 0;
      }
      lastError.set("Invalid statement handle");
      return -1;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Close statement failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  // ========== DATE/TIME SUPPORT ==========

  /**
   * Get date value from result set (as epoch days).
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return Days since epoch (1970-01-01) or 0 on error
   */
  @CEntryPoint(name = "starlake_get_date")
  public static long getDate(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return 0;
      }

      java.sql.Date date = rs.getDate(columnIndex);
      if (date == null) {
        return 0;
      }

      // Return as days since epoch
      return date.getTime() / (24 * 60 * 60 * 1000);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get date failed", e);
      lastError.set(e.getMessage());
      return 0;
    }
  }

  /**
   * Get time value from result set (as milliseconds since midnight).
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return Milliseconds since midnight or 0 on error
   */
  @CEntryPoint(name = "starlake_get_time")
  public static long getTime(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return 0;
      }

      java.sql.Time time = rs.getTime(columnIndex);
      if (time == null) {
        return 0;
      }

      // Return as milliseconds since midnight
      return time.getTime() % (24 * 60 * 60 * 1000);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get time failed", e);
      lastError.set(e.getMessage());
      return 0;
    }
  }

  /**
   * Get timestamp value from result set (as milliseconds since epoch).
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return Milliseconds since epoch (1970-01-01 00:00:00) or 0 on error
   */
  @CEntryPoint(name = "starlake_get_timestamp")
  public static long getTimestamp(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return 0;
      }

      java.sql.Timestamp timestamp = rs.getTimestamp(columnIndex);
      if (timestamp == null) {
        return 0;
      }

      return timestamp.getTime();
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get timestamp failed", e);
      lastError.set(e.getMessage());
      return 0;
    }
  }

  /**
   * Set date parameter for prepared statement (from epoch days).
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @param paramIndex Parameter index (1-based)
   * @param epochDays Days since epoch (1970-01-01)
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_set_date")
  public static int setDate(IsolateThread thread, long stmtHandle, int paramIndex, long epochDays) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null || !(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Invalid prepared statement handle");
        return -1;
      }

      java.sql.Date date = new java.sql.Date(epochDays * 24 * 60 * 60 * 1000);
      ((java.sql.PreparedStatement) stmt).setDate(paramIndex, date);
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Set date parameter failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Set time parameter for prepared statement (from milliseconds since midnight).
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @param paramIndex Parameter index (1-based)
   * @param millisSinceMidnight Milliseconds since midnight
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_set_time")
  public static int setTime(IsolateThread thread, long stmtHandle, int paramIndex,
                            long millisSinceMidnight) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null || !(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Invalid prepared statement handle");
        return -1;
      }

      java.sql.Time time = new java.sql.Time(millisSinceMidnight);
      ((java.sql.PreparedStatement) stmt).setTime(paramIndex, time);
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Set time parameter failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Set timestamp parameter for prepared statement (from milliseconds since epoch).
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @param paramIndex Parameter index (1-based)
   * @param millisSinceEpoch Milliseconds since epoch (1970-01-01 00:00:00)
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_set_timestamp")
  public static int setTimestamp(IsolateThread thread, long stmtHandle, int paramIndex,
                                 long millisSinceEpoch) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null || !(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Invalid prepared statement handle");
        return -1;
      }

      java.sql.Timestamp timestamp = new java.sql.Timestamp(millisSinceEpoch);
      ((java.sql.PreparedStatement) stmt).setTimestamp(paramIndex, timestamp);
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Set timestamp parameter failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  // ========== BINARY DATA SUPPORT ==========

  /**
   * Get binary data from result set.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @param bufferPtr Output buffer for binary data
   * @param bufferSize Buffer size
   * @return Number of bytes read or -1 on error
   */
  @CEntryPoint(name = "starlake_get_bytes")
  public static int getBytes(IsolateThread thread, long rsHandle, int columnIndex,
                             CCharPointer bufferPtr, int bufferSize) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }

      byte[] bytes = rs.getBytes(columnIndex);
      if (bytes == null) {
        return 0;
      }

      int bytesToCopy = Math.min(bytes.length, bufferSize);
      for (int i = 0; i < bytesToCopy; i++) {
        bufferPtr.write(i, bytes[i]);
      }

      return bytesToCopy;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get bytes failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Set binary parameter for prepared statement.
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @param paramIndex Parameter index (1-based)
   * @param dataPtr Binary data
   * @param dataLength Data length
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_set_bytes")
  public static int setBytes(IsolateThread thread, long stmtHandle, int paramIndex,
                             CCharPointer dataPtr, int dataLength) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null || !(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Invalid prepared statement handle");
        return -1;
      }

      byte[] bytes = new byte[dataLength];
      for (int i = 0; i < dataLength; i++) {
        bytes[i] = dataPtr.read(i);
      }

      ((java.sql.PreparedStatement) stmt).setBytes(paramIndex, bytes);
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Set bytes parameter failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  // ========== BOOLEAN SUPPORT ==========

  /**
   * Get boolean value from result set.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return 1 for true, 0 for false, -1 on error
   */
  @CEntryPoint(name = "starlake_get_boolean")
  public static int getBoolean(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return -1;
      }

      boolean value = rs.getBoolean(columnIndex);
      return value ? 1 : 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get boolean failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  /**
   * Set boolean parameter for prepared statement.
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @param paramIndex Parameter index (1-based)
   * @param value Boolean value (1 for true, 0 for false)
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_set_boolean")
  public static int setBoolean(IsolateThread thread, long stmtHandle, int paramIndex, int value) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null || !(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Invalid prepared statement handle");
        return -1;
      }

      ((java.sql.PreparedStatement) stmt).setBoolean(paramIndex, value != 0);
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Set boolean parameter failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }

  // ========== FLOAT SUPPORT ==========

  /**
   * Get float value from result set.
   *
   * @param thread GraalVM isolate thread
   * @param rsHandle ResultSet handle
   * @param columnIndex Column index (1-based)
   * @return Float value or 0.0 on error
   */
  @CEntryPoint(name = "starlake_get_float")
  public static float getFloat(IsolateThread thread, long rsHandle, int columnIndex) {
    try {
      ResultSet rs = resultSets.get(rsHandle);
      if (rs == null) {
        lastError.set("Invalid result set handle");
        return 0.0f;
      }
      return rs.getFloat(columnIndex);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Get float failed", e);
      lastError.set(e.getMessage());
      return 0.0f;
    }
  }

  /**
   * Set float parameter for prepared statement.
   *
   * @param thread GraalVM isolate thread
   * @param stmtHandle PreparedStatement handle
   * @param paramIndex Parameter index (1-based)
   * @param value Float value
   * @return 0 on success, -1 on error
   */
  @CEntryPoint(name = "starlake_set_float")
  public static int setFloat(IsolateThread thread, long stmtHandle, int paramIndex, float value) {
    try {
      Statement stmt = statements.get(stmtHandle);
      if (stmt == null || !(stmt instanceof java.sql.PreparedStatement)) {
        lastError.set("Invalid prepared statement handle");
        return -1;
      }

      ((java.sql.PreparedStatement) stmt).setFloat(paramIndex, value);
      return 0;
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Set float parameter failed", e);
      lastError.set(e.getMessage());
      return -1;
    }
  }
}

