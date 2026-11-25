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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class StarlakePreparedStatement extends StarlakeStatement implements PreparedStatement {
  private final PreparedStatement wrapped;

  /** The branch the statement was prepared on, null when it was prepared outside a branch. */
  private final StarlakeSession session;

  /** The statement as prepared on the branch, null when it was prepared outside a branch. */
  private final BranchSql branchSql;

  /** The values bound to the parameters, by parameter index. */
  private final Map<Integer, Object> parameters = new HashMap<>();

  /** A parameter bound to SQL NULL. */
  static final Object NULL = new Object();

  /** A parameter bound to a value that can not be written as a SQL literal, such as a stream. */
  static final Object UNSUPPORTED = new Object();

  public StarlakePreparedStatement(StarlakeConnection connection, PreparedStatement wrapped) {
    this(connection, wrapped, null, null);
  }

  StarlakePreparedStatement(StarlakeConnection connection, PreparedStatement wrapped,
      StarlakeSession session, BranchSql branchSql) {
    super(connection, wrapped);

    this.connection = connection;
    this.wrapped = wrapped;
    this.session = session;
    this.branchSql = branchSql;
  }

  @FunctionalInterface
  private interface Execution<T> {
    T run() throws SQLException;
  }

  /**
   * The statement APPLY replays for the current parameters, with each parameter written as a SQL
   * literal: null when the statement is not replayed. The statement must run on the branch it was
   * prepared on: one prepared before a branch started reads and writes production, and one prepared
   * on a branch that is no longer in progress reads and writes its tables.
   */
  private String replayedSql() throws SQLException {
    final StarlakeSession active = connection.activeSession();
    if (active != session) {
      throw new SQLException("This statement was prepared "
          + (session == null ? "before branch " + active.getSessionId() + " started"
              : "on branch " + session.getSessionId() + ", which is no longer in progress")
          + ": prepare it again.", "0A000");
    }
    if (session == null || !branchSql.replayed) {
      return null;
    }
    return bindLiterals(branchSql.sql, parameters, connection.type);
  }

  /** Run the statement, then record it for APPLY when it ran on a branch. */
  private <T> T execute(Execution<T> execution) throws SQLException {
    final String replayedSql = replayedSql();
    final T result = execution.run();
    if (replayedSql != null) {
      session.recordStatement(branchSql.parsed, replayedSql);
    }
    if (session != null && branchSql.parsed != null) {
      updateMetadataIfNeeded(branchSql.parsed);
    }
    return result;
  }

  private void bind(int parameterIndex, Object value) {
    parameters.put(parameterIndex, value == null ? NULL : value);
  }

  /**
   * Write the parameters of a statement into it as SQL literals. The question marks in string
   * literals, quoted identifiers, comments and dollar-quoted strings are not parameters.
   */
  static String bindLiterals(String sql, Map<Integer, Object> parameters,
      StarlakeConnection.DatabaseType type) throws SQLException {
    final boolean backslashEscapes = backslashEscapes(type);
    final StringBuilder out = new StringBuilder(sql.length() + 16 * parameters.size());
    final int n = sql.length();
    int index = 0;
    int i = 0;
    while (i < n) {
      final char c = sql.charAt(i);
      int end;
      if (c == '\'' || c == '"' || c == '`') {
        end = endOfQuoted(sql, i, c, backslashEscapes && c == '\'');
      } else if (sql.startsWith("--", i)) {
        end = sql.indexOf('\n', i);
        end = end < 0 ? n : end + 1;
      } else if (sql.startsWith("/*", i)) {
        end = sql.indexOf("*/", i + 2);
        end = end < 0 ? n : end + 2;
      } else if (c == '$' && (i == 0 || !Character.isLetterOrDigit(sql.charAt(i - 1)))
          && (end = endOfDollarQuoted(sql, i)) > i) {
        // end is set by the condition
      } else if (c == '?') {
        out.append(literal(parameters.get(++index), index, type));
        i++;
        continue;
      } else {
        out.append(c);
        i++;
        continue;
      }
      out.append(sql, i, end);
      i = end;
    }
    return out.toString();
  }

  /** The index after a quoted string or identifier starting at start. */
  private static int endOfQuoted(String sql, int start, char quote, boolean backslashEscapes) {
    int j = start + 1;
    while (j < sql.length()) {
      final char ch = sql.charAt(j);
      if (backslashEscapes && ch == '\\') {
        j += 2;
      } else if (ch == quote) {
        if (j + 1 < sql.length() && sql.charAt(j + 1) == quote) {
          j += 2;
        } else {
          return j + 1;
        }
      } else {
        j++;
      }
    }
    return sql.length();
  }

  /**
   * The index after a dollar-quoted string ($$...$$ or $tag$...$tag$) starting at start, or start
   * when there is none.
   */
  private static int endOfDollarQuoted(String sql, int start) {
    int j = start + 1;
    while (j < sql.length() && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) {
      j++;
    }
    if (j >= sql.length() || sql.charAt(j) != '$'
        || j > start + 1 && Character.isDigit(sql.charAt(start + 1))) {
      return start;
    }
    final String tag = sql.substring(start, j + 1);
    final int close = sql.indexOf(tag, j + 1);
    return close < 0 ? start : close + tag.length();
  }

  /** Whether a backslash escapes the next character in a string literal. */
  private static boolean backslashEscapes(StarlakeConnection.DatabaseType type) {
    return type == StarlakeConnection.DatabaseType.SNOWFLAKE
        || type == StarlakeConnection.DatabaseType.BIGQUERY
        || type == StarlakeConnection.DatabaseType.DATABRICKS
        || type == StarlakeConnection.DatabaseType.MYSQL
        || type == StarlakeConnection.DatabaseType.MARIADB;
  }

  static String quote(String value, StarlakeConnection.DatabaseType type) {
    String escaped = value;
    if (backslashEscapes(type)) {
      escaped = escaped.replace("\\", "\\\\");
    }
    if (type == StarlakeConnection.DatabaseType.BIGQUERY
        || type == StarlakeConnection.DatabaseType.DATABRICKS) {
      escaped = escaped.replace("'", "\\'");
    } else {
      escaped = escaped.replace("'", "''");
    }
    return "'" + escaped + "'";
  }

  /** A parameter value written as a SQL literal. */
  static String literal(Object value, int index, StarlakeConnection.DatabaseType type)
      throws SQLException {
    if (value == null) {
      throw new SQLException("Parameter " + index + " is not set.", "07001");
    } else if (value == NULL) {
      return "NULL";
    } else if (value instanceof Boolean) {
      return (Boolean) value ? "TRUE" : "FALSE";
    } else if (value instanceof Double && ((Double) value).isInfinite()
        || value instanceof Double && ((Double) value).isNaN()
        || value instanceof Float && ((Float) value).isInfinite()
        || value instanceof Float && ((Float) value).isNaN()) {
      throw unsupported(index);
    } else if (value instanceof BigDecimal) {
      return ((BigDecimal) value).toPlainString();
    } else if (value instanceof Number) {
      return value.toString();
    } else if (value instanceof String || value instanceof Character) {
      return quote(value.toString(), type);
    } else if (value instanceof Timestamp) {
      return "TIMESTAMP " + quote(value.toString(), type);
    } else if (value instanceof Date || value instanceof LocalDate) {
      return "DATE " + quote(value.toString(), type);
    } else if (value instanceof Time || value instanceof LocalTime) {
      return "TIME " + quote(value.toString(), type);
    } else if (value instanceof LocalDateTime) {
      return "TIMESTAMP " + quote(value.toString().replace('T', ' '), type);
    } else {
      throw unsupported(index);
    }
  }

  private static SQLException unsupported(int index) {
    return new SQLException("Parameter " + index
        + " is bound to a value the branch can not record for BRANCH APPLY (a stream, a LOB, an"
        + " array, bytes, a URL, a value with a Calendar, or an infinite or NaN number). Bind a"
        + " string, a number, a boolean, a date, a time or a timestamp.", "0A000");
  }

  @Override
  public ResultSet executeQuery() throws SQLException {
    return execute(() -> new StarlakeResultSet(this, wrapped.executeQuery()));
  }

  @Override
  public int executeUpdate() throws SQLException {
    return execute(wrapped::executeUpdate);
  }

  @Override
  public void setNull(int parameterIndex, int sqlType) throws SQLException {
    bind(parameterIndex, NULL);
    wrapped.setNull(parameterIndex, sqlType);
  }

  @Override
  public void setBoolean(int parameterIndex, boolean x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setBoolean(parameterIndex, x);
  }

  @Override
  public void setByte(int parameterIndex, byte x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setByte(parameterIndex, x);
  }

  @Override
  public void setShort(int parameterIndex, short x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setShort(parameterIndex, x);
  }

  @Override
  public void setInt(int parameterIndex, int x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setInt(parameterIndex, x);
  }

  @Override
  public void setLong(int parameterIndex, long x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setLong(parameterIndex, x);
  }

  @Override
  public void setFloat(int parameterIndex, float x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setFloat(parameterIndex, x);
  }

  @Override
  public void setDouble(int parameterIndex, double x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setDouble(parameterIndex, x);
  }

  @Override
  public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setBigDecimal(parameterIndex, x);
  }

  @Override
  public void setString(int parameterIndex, String x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setString(parameterIndex, x);
  }

  @Override
  public void setBytes(int parameterIndex, byte[] x) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setBytes(parameterIndex, x);
  }

  @Override
  public void setDate(int parameterIndex, Date x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setDate(parameterIndex, x);
  }

  @Override
  public void setTime(int parameterIndex, Time x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setTime(parameterIndex, x);
  }

  @Override
  public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setTimestamp(parameterIndex, x);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setAsciiStream(parameterIndex, x, length);
  }

  @Deprecated(since = "1.2")
  @Override
  public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setUnicodeStream(parameterIndex, x, length);
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setBinaryStream(parameterIndex, x, length);
  }

  @Override
  public void clearParameters() throws SQLException {
    parameters.clear();
    wrapped.clearParameters();
  }

  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setObject(parameterIndex, x, targetSqlType);
  }

  @Override
  public void setObject(int parameterIndex, Object x) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setObject(parameterIndex, x);
  }

  @Override
  public boolean execute() throws SQLException {
    return execute(wrapped::execute);
  }

  @Override
  public void addBatch() throws SQLException {
    final String replayedSql = replayedSql();
    wrapped.addBatch();
    addPending(session, branchSql == null ? null : branchSql.parsed, replayedSql);
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader, int length)
      throws SQLException {
    bind(parameterIndex, reader == null ? NULL : UNSUPPORTED);
    wrapped.setCharacterStream(parameterIndex, reader, length);
  }

  @Override
  public void setRef(int parameterIndex, Ref x) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setRef(parameterIndex, x);
  }

  @Override
  public void setBlob(int parameterIndex, Blob x) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setBlob(parameterIndex, x);
  }

  @Override
  public void setClob(int parameterIndex, Clob x) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setClob(parameterIndex, x);
  }

  @Override
  public void setArray(int parameterIndex, Array x) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setArray(parameterIndex, x);
  }

  @Override
  public ResultSetMetaData getMetaData() throws SQLException {
    ResultSetMetaData meta = wrapped.getMetaData();
    return meta != null ? new StarlakeResultSetMetaData(meta) : null;
  }

  @Override
  public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setDate(parameterIndex, x, cal);
  }

  @Override
  public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setTime(parameterIndex, x, cal);
  }

  @Override
  public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setTimestamp(parameterIndex, x, cal);
  }

  @Override
  public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
    bind(parameterIndex, NULL);
    wrapped.setNull(parameterIndex, sqlType, typeName);
  }

  @Override
  public void setURL(int parameterIndex, URL x) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setURL(parameterIndex, x);
  }

  @Override
  public ParameterMetaData getParameterMetaData() throws SQLException {
    return wrapped.getParameterMetaData();
  }

  @Override
  public void setRowId(int parameterIndex, RowId x) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setRowId(parameterIndex, x);
  }

  @Override
  public void setNString(int parameterIndex, String value) throws SQLException {
    bind(parameterIndex, value);
    wrapped.setNString(parameterIndex, value);
  }

  @Override
  public void setNCharacterStream(int parameterIndex, Reader value, long length)
      throws SQLException {
    bind(parameterIndex, value == null ? NULL : UNSUPPORTED);
    wrapped.setNCharacterStream(parameterIndex, value, length);
  }

  @Override
  public void setNClob(int parameterIndex, NClob value) throws SQLException {
    bind(parameterIndex, value == null ? NULL : UNSUPPORTED);
    wrapped.setNClob(parameterIndex, value);
  }

  @Override
  public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
    bind(parameterIndex, reader == null ? NULL : UNSUPPORTED);
    wrapped.setClob(parameterIndex, reader, length);
  }

  @Override
  public void setBlob(int parameterIndex, InputStream inputStream, long length)
      throws SQLException {
    bind(parameterIndex, inputStream == null ? NULL : UNSUPPORTED);
    wrapped.setBlob(parameterIndex, inputStream, length);
  }

  @Override
  public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
    bind(parameterIndex, reader == null ? NULL : UNSUPPORTED);
    wrapped.setNClob(parameterIndex, reader, length);
  }

  @Override
  public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
    bind(parameterIndex, xmlObject == null ? NULL : UNSUPPORTED);
    wrapped.setSQLXML(parameterIndex, xmlObject);
  }

  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength)
      throws SQLException {
    bind(parameterIndex, x);
    wrapped.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setAsciiStream(parameterIndex, x, length);
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setBinaryStream(parameterIndex, x, length);
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader, long length)
      throws SQLException {
    bind(parameterIndex, reader == null ? NULL : UNSUPPORTED);
    wrapped.setCharacterStream(parameterIndex, reader, length);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setAsciiStream(parameterIndex, x);
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
    bind(parameterIndex, x == null ? NULL : UNSUPPORTED);
    wrapped.setBinaryStream(parameterIndex, x);
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
    bind(parameterIndex, reader == null ? NULL : UNSUPPORTED);
    wrapped.setCharacterStream(parameterIndex, reader);
  }

  @Override
  public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
    bind(parameterIndex, value == null ? NULL : UNSUPPORTED);
    wrapped.setNCharacterStream(parameterIndex, value);
  }

  @Override
  public void setClob(int parameterIndex, Reader reader) throws SQLException {
    bind(parameterIndex, reader == null ? NULL : UNSUPPORTED);
    wrapped.setClob(parameterIndex, reader);
  }

  @Override
  public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
    bind(parameterIndex, inputStream == null ? NULL : UNSUPPORTED);
    wrapped.setBlob(parameterIndex, inputStream);
  }

  @Override
  public void setNClob(int parameterIndex, Reader reader) throws SQLException {
    bind(parameterIndex, reader == null ? NULL : UNSUPPORTED);
    wrapped.setNClob(parameterIndex, reader);
  }

  @Override
  public void setObject(int parameterIndex, Object x, SQLType targetSqlType, int scaleOrLength)
      throws SQLException {
    bind(parameterIndex, x);
    wrapped.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
  }

  @Override
  public void setObject(int parameterIndex, Object x, SQLType targetSqlType) throws SQLException {
    bind(parameterIndex, x);
    wrapped.setObject(parameterIndex, x, targetSqlType);
  }

  @Override
  public long executeLargeUpdate() throws SQLException {
    return execute(wrapped::executeLargeUpdate);
  }
}
