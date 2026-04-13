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
import java.sql.*;
import java.util.Calendar;
import java.util.Map;

public class StarlakeVirtualResultSet implements ResultSet {

  private final String[] columnLabels;
  private final Class<?>[] columnClasses;
  private final Object[][] values;

  private int currentRow = -1; // Before first row
  private boolean wasNull = false;
  private boolean closed = false;

  public StarlakeVirtualResultSet(String columnLabel, String value) {
    if (columnLabel == null || value == null) {
      throw new IllegalArgumentException("Parameters cannot be null");
    }
    this.columnLabels = new String[] {columnLabel};
    this.columnClasses = new Class<?>[] {String.class};
    this.values = new Object[][] {{value}};
  }

  public StarlakeVirtualResultSet(String[] columnLabels, Class<?>[] columnClasses,
      Object[][] values) {
    if (columnLabels == null || columnClasses == null || values == null) {
      throw new IllegalArgumentException("Parameters cannot be null");
    }
    if (columnLabels.length != columnClasses.length) {
      throw new IllegalArgumentException("Column labels and classes arrays must have same length");
    }
    if (values.length > 0 && values[0].length != columnLabels.length) {
      throw new IllegalArgumentException("Data array column count must match column labels count");
    }

    this.columnLabels = columnLabels.clone();
    this.columnClasses = columnClasses.clone();
    this.values = new Object[values.length][];
    for (int i = 0; i < values.length; i++) {
      this.values[i] = values[i] != null ? values[i].clone() : null;
    }
  }

  private void checkClosed() throws SQLException {
    if (closed) {
      throw new SQLException("ResultSet is closed");
    }
  }

  private void checkValidRow() throws SQLException {
    checkClosed();
    if (currentRow < 0 || currentRow >= values.length) {
      throw new SQLException("Invalid row position: " + currentRow);
    }
  }

  private int getColumnIndex(String columnLabel) throws SQLException {
    for (int i = 0; i < columnLabels.length; i++) {
      if (columnLabels[i].equalsIgnoreCase(columnLabel)) {
        return i + 1; // JDBC uses 1-based indexing
      }
    }
    throw new SQLException("Column not found: " + columnLabel);
  }

  @Override
  public int findColumn(String columnLabel) throws SQLException {
    checkClosed();
    return getColumnIndex(columnLabel);
  }

  private Object getValue(int columnIndex) throws SQLException {
    checkValidRow();
    if (columnIndex < 1 || columnIndex > columnLabels.length) {
      throw new SQLException("Invalid column index: " + columnIndex);
    }

    Object value = values[currentRow][columnIndex - 1];
    wasNull = value == null;
    return value;
  }

  // Navigation methods
  @Override
  public boolean next() throws SQLException {
    checkClosed();
    if (currentRow < values.length - 1) {
      currentRow++;
      return true;
    }
    if (values.length > 0 && currentRow == values.length - 1) {
      currentRow = values.length;
    }
    return false;
  }

  @Override
  public boolean previous() throws SQLException {
    checkClosed();
    if (currentRow > 0) {
      currentRow--;
      return true;
    }
    return false;
  }

  @Override
  public boolean first() throws SQLException {
    checkClosed();
    if (values.length > 0) {
      currentRow = 0;
      return true;
    }
    return false;
  }

  @Override
  public boolean last() throws SQLException {
    checkClosed();
    if (values.length > 0) {
      currentRow = values.length - 1;
      return true;
    }
    return false;
  }

  @Override
  public void beforeFirst() throws SQLException {
    checkClosed();
    currentRow = -1;
  }

  @Override
  public void afterLast() throws SQLException {
    checkClosed();
    currentRow = values.length;
  }

  @Override
  public boolean isFirst() throws SQLException {
    checkClosed();
    return currentRow == 0 && values.length > 0;
  }

  @Override
  public boolean isLast() throws SQLException {
    checkClosed();
    return currentRow == values.length - 1 && values.length > 0;
  }

  @Override
  public boolean isBeforeFirst() throws SQLException {
    checkClosed();
    return currentRow < 0;
  }

  @Override
  public boolean isAfterLast() throws SQLException {
    checkClosed();
    return currentRow >= values.length;
  }

  @Override
  public int getRow() throws SQLException {
    checkClosed();
    return currentRow >= 0 && currentRow < values.length ? currentRow + 1 : 0;
  }

  @Override
  public boolean absolute(int row) throws SQLException {
    checkClosed();
    if (row == 0) {
      beforeFirst();
      return false;
    } else if (row > 0) {
      if (row <= values.length) {
        currentRow = row - 1;
        return true;
      } else {
        afterLast();
        return false;
      }
    } else {
      int targetRow = values.length + row;
      if (targetRow >= 0) {
        currentRow = targetRow;
        return true;
      } else {
        beforeFirst();
        return false;
      }
    }
  }

  @Override
  public boolean relative(int rows) throws SQLException {
    return absolute(getRow() + rows);
  }

  // Getter methods by column index
  @Override
  public String getString(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    return value != null ? value.toString() : null;
  }

  @Override
  public boolean getBoolean(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue() != 0;
    }
    return Boolean.parseBoolean(value.toString());
  }

  @Override
  public byte getByte(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return 0;
    }
    if (value instanceof Number) {
      return ((Number) value).byteValue();
    }
    return Byte.parseByte(value.toString());
  }

  @Override
  public short getShort(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return 0;
    }
    if (value instanceof Number) {
      return ((Number) value).shortValue();
    }
    return Short.parseShort(value.toString());
  }

  @Override
  public int getInt(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return 0;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return Integer.parseInt(value.toString());
  }

  @Override
  public long getLong(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return 0;
    }
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return Long.parseLong(value.toString());
  }

  @Override
  public float getFloat(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return 0;
    }
    if (value instanceof Number) {
      return ((Number) value).floatValue();
    }
    return Float.parseFloat(value.toString());
  }

  @Override
  public double getDouble(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return 0;
    }
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    return Double.parseDouble(value.toString());
  }

  @Override
  public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal) {
      return (BigDecimal) value;
    }
    return new BigDecimal(value.toString());
  }

  @Override
  public Date getDate(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return null;
    }
    if (value instanceof Date) {
      return (Date) value;
    }
    if (value instanceof java.util.Date) {
      return new Date(((java.util.Date) value).getTime());
    }
    return Date.valueOf(value.toString());
  }

  @Override
  public Time getTime(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return null;
    }
    if (value instanceof Time) {
      return (Time) value;
    }
    if (value instanceof java.util.Date) {
      return new Time(((java.util.Date) value).getTime());
    }
    return Time.valueOf(value.toString());
  }

  @Override
  public Timestamp getTimestamp(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return null;
    }
    if (value instanceof Timestamp) {
      return (Timestamp) value;
    }
    if (value instanceof java.util.Date) {
      return new Timestamp(((java.util.Date) value).getTime());
    }
    return Timestamp.valueOf(value.toString());
  }

  @Override
  public Object getObject(int columnIndex) throws SQLException {
    return getValue(columnIndex);
  }

  // Getter methods by column label
  @Override
  public String getString(String columnLabel) throws SQLException {
    return getString(getColumnIndex(columnLabel));
  }

  @Override
  public boolean getBoolean(String columnLabel) throws SQLException {
    return getBoolean(getColumnIndex(columnLabel));
  }

  @Override
  public byte getByte(String columnLabel) throws SQLException {
    return getByte(getColumnIndex(columnLabel));
  }

  @Override
  public short getShort(String columnLabel) throws SQLException {
    return getShort(getColumnIndex(columnLabel));
  }

  @Override
  public int getInt(String columnLabel) throws SQLException {
    return getInt(getColumnIndex(columnLabel));
  }

  @Override
  public long getLong(String columnLabel) throws SQLException {
    return getLong(getColumnIndex(columnLabel));
  }

  @Override
  public float getFloat(String columnLabel) throws SQLException {
    return getFloat(getColumnIndex(columnLabel));
  }

  @Override
  public double getDouble(String columnLabel) throws SQLException {
    return getDouble(getColumnIndex(columnLabel));
  }

  @Override
  public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
    return getBigDecimal(getColumnIndex(columnLabel));
  }

  @Override
  public Date getDate(String columnLabel) throws SQLException {
    return getDate(getColumnIndex(columnLabel));
  }

  @Override
  public Time getTime(String columnLabel) throws SQLException {
    return getTime(getColumnIndex(columnLabel));
  }

  @Override
  public Timestamp getTimestamp(String columnLabel) throws SQLException {
    return getTimestamp(getColumnIndex(columnLabel));
  }

  @Override
  public Object getObject(String columnLabel) throws SQLException {
    return getObject(getColumnIndex(columnLabel));
  }

  // ResultSetMetaData
  @Override
  public ResultSetMetaData getMetaData() throws SQLException {
    checkClosed();
    return new StarlakeResultSetMetaData();
  }

  private class StarlakeResultSetMetaData implements ResultSetMetaData {
    @Override
    public int getColumnCount() throws SQLException {
      return columnLabels.length;
    }

    @Override
    public String getColumnLabel(int column) throws SQLException {
      if (column < 1 || column > columnLabels.length) {
        throw new SQLException("Invalid column index: " + column);
      }
      return columnLabels[column - 1];
    }

    @Override
    public String getColumnName(int column) throws SQLException {
      return getColumnLabel(column);
    }

    @Override
    public int getColumnType(int column) throws SQLException {
      if (column < 1 || column > columnClasses.length) {
        throw new SQLException("Invalid column index: " + column);
      }

      Class<?> clazz = columnClasses[column - 1];
      if (clazz == String.class) {
        return Types.VARCHAR;
      }
      if (clazz == Integer.class || clazz == int.class) {
        return Types.INTEGER;
      }
      if (clazz == Long.class || clazz == long.class) {
        return Types.BIGINT;
      }
      if (clazz == Double.class || clazz == double.class) {
        return Types.DOUBLE;
      }
      if (clazz == Float.class || clazz == float.class) {
        return Types.FLOAT;
      }
      if (clazz == Boolean.class || clazz == boolean.class) {
        return Types.BOOLEAN;
      }
      if (clazz == Date.class) {
        return Types.DATE;
      }
      if (clazz == Time.class) {
        return Types.TIME;
      }
      if (clazz == Timestamp.class) {
        return Types.TIMESTAMP;
      }
      if (clazz == BigDecimal.class) {
        return Types.DECIMAL;
      }
      return Types.OTHER;
    }

    @Override
    public String getColumnTypeName(int column) throws SQLException {
      if (column < 1 || column > columnClasses.length) {
        throw new SQLException("Invalid column index: " + column);
      }
      return columnClasses[column - 1].getSimpleName();
    }

    @Override
    public String getColumnClassName(int column) throws SQLException {
      if (column < 1 || column > columnClasses.length) {
        throw new SQLException("Invalid column index: " + column);
      }
      return columnClasses[column - 1].getName();
    }

    // Other metadata methods with default implementations
    @Override
    public boolean isAutoIncrement(int column) throws SQLException {
      return false;
    }

    @Override
    public boolean isCaseSensitive(int column) throws SQLException {
      return true;
    }

    @Override
    public boolean isSearchable(int column) throws SQLException {
      return true;
    }

    @Override
    public boolean isCurrency(int column) throws SQLException {
      return false;
    }

    @Override
    public int isNullable(int column) throws SQLException {
      return columnNullable;
    }

    @Override
    public boolean isSigned(int column) throws SQLException {
      return true;
    }

    @Override
    public int getColumnDisplaySize(int column) throws SQLException {
      return 20;
    }

    @Override
    public int getPrecision(int column) throws SQLException {
      return 0;
    }

    @Override
    public int getScale(int column) throws SQLException {
      return 0;
    }

    @Override
    public String getTableName(int column) throws SQLException {
      return "";
    }

    @Override
    public String getCatalogName(int column) throws SQLException {
      return "";
    }

    @Override
    public String getSchemaName(int column) throws SQLException {
      return "";
    }

    @Override
    public boolean isReadOnly(int column) throws SQLException {
      return true;
    }

    @Override
    public boolean isWritable(int column) throws SQLException {
      return false;
    }

    @Override
    public boolean isDefinitelyWritable(int column) throws SQLException {
      return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      throw new SQLException("Not supported");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return false;
    }
  }

  // ResultSet state methods
  @Override
  public boolean wasNull() throws SQLException {
    checkClosed();
    return wasNull;
  }

  @Override
  public void close() throws SQLException {
    closed = true;
  }

  @Override
  public boolean isClosed() throws SQLException {
    return closed;
  }

  // Cursor methods
  @Override
  public String getCursorName() throws SQLException {
    throw new SQLException("Named cursors not supported");
  }

  // Warning methods
  @Override
  public SQLWarning getWarnings() throws SQLException {
    return null;
  }

  @Override
  public void clearWarnings() throws SQLException {
    // No-op
  }

  // Statement reference
  @Override
  public Statement getStatement() throws SQLException {
    return null;
  }

  // ResultSet characteristics
  @Override
  public int getType() throws SQLException {
    return TYPE_SCROLL_INSENSITIVE;
  }

  @Override
  public int getConcurrency() throws SQLException {
    return CONCUR_READ_ONLY;
  }

  @Override
  public int getHoldability() throws SQLException {
    return HOLD_CURSORS_OVER_COMMIT;
  }

  @Override
  public int getFetchDirection() throws SQLException {
    return FETCH_FORWARD;
  }

  @Override
  public void setFetchDirection(int direction) throws SQLException {
    if (direction != FETCH_FORWARD && direction != FETCH_REVERSE && direction != FETCH_UNKNOWN) {
      throw new SQLException("Invalid fetch direction");
    }
  }

  @Override
  public int getFetchSize() throws SQLException {
    return 0;
  }

  @Override
  public void setFetchSize(int rows) throws SQLException {
    if (rows < 0) {
      throw new SQLException("Fetch size must be >= 0");
    }
  }

  // Unsupported operations - throw SQLException
  @Override
  public byte[] getBytes(int columnIndex) throws SQLException {
    throw new SQLException("getBytes not supported");
  }

  @Override
  public byte[] getBytes(String columnLabel) throws SQLException {
    throw new SQLException("getBytes not supported");
  }

  @Override
  public InputStream getAsciiStream(int columnIndex) throws SQLException {
    throw new SQLException("getAsciiStream not supported");
  }

  @Override
  public InputStream getUnicodeStream(int columnIndex) throws SQLException {
    throw new SQLException("getUnicodeStream not supported");
  }

  @Override
  public InputStream getBinaryStream(int columnIndex) throws SQLException {
    throw new SQLException("getBinaryStream not supported");
  }

  @Override
  public InputStream getAsciiStream(String columnLabel) throws SQLException {
    throw new SQLException("getAsciiStream not supported");
  }

  @Override
  public InputStream getUnicodeStream(String columnLabel) throws SQLException {
    throw new SQLException("getUnicodeStream not supported");
  }

  @Override
  public InputStream getBinaryStream(String columnLabel) throws SQLException {
    throw new SQLException("getBinaryStream not supported");
  }

  @Override
  public Reader getCharacterStream(int columnIndex) throws SQLException {
    throw new SQLException("getCharacterStream not supported");
  }

  @Override
  public Reader getCharacterStream(String columnLabel) throws SQLException {
    throw new SQLException("getCharacterStream not supported");
  }

  @Override
  public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
    throw new SQLException("getBigDecimal with scale not supported");
  }

  @Override
  public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
    throw new SQLException("getBigDecimal with scale not supported");
  }

  // Update operations - not supported
  @Override
  public boolean rowUpdated() throws SQLException {
    return false;
  }

  @Override
  public boolean rowInserted() throws SQLException {
    return false;
  }

  @Override
  public boolean rowDeleted() throws SQLException {
    return false;
  }

  // All update methods throw SQLException
  @Override
  public void updateNull(int columnIndex) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBoolean(int columnIndex, boolean x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateByte(int columnIndex, byte x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateShort(int columnIndex, short x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateInt(int columnIndex, int x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateLong(int columnIndex, long x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateFloat(int columnIndex, float x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateDouble(int columnIndex, double x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateString(int columnIndex, String x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBytes(int columnIndex, byte[] x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateDate(int columnIndex, Date x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateTime(int columnIndex, Time x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateObject(int columnIndex, Object x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateNull(String columnLabel) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBoolean(String columnLabel, boolean x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateByte(String columnLabel, byte x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateShort(String columnLabel, short x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateInt(String columnLabel, int x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateLong(String columnLabel, long x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateFloat(String columnLabel, float x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateDouble(String columnLabel, double x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateString(String columnLabel, String x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBytes(String columnLabel, byte[] x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateDate(String columnLabel, Date x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateTime(String columnLabel, Time x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x, int length)
      throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader x, int length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateObject(String columnLabel, Object x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void insertRow() throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateRow() throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void deleteRow() throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void refreshRow() throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void cancelRowUpdates() throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void moveToInsertRow() throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void moveToCurrentRow() throws SQLException {
    throw new SQLException("Updates not supported");
  }

  // Advanced getters - partial support
  @Override
  public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
    return getObject(columnIndex);
  }

  @Override
  public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
    return getObject(columnLabel);
  }

  @Override
  public Ref getRef(int columnIndex) throws SQLException {
    throw new SQLException("Ref not supported");
  }

  @Override
  public Ref getRef(String columnLabel) throws SQLException {
    throw new SQLException("Ref not supported");
  }

  @Override
  public Blob getBlob(int columnIndex) throws SQLException {
    throw new SQLException("Blob not supported");
  }

  @Override
  public Blob getBlob(String columnLabel) throws SQLException {
    throw new SQLException("Blob not supported");
  }

  @Override
  public Clob getClob(int columnIndex) throws SQLException {
    throw new SQLException("Clob not supported");
  }

  @Override
  public Clob getClob(String columnLabel) throws SQLException {
    throw new SQLException("Clob not supported");
  }

  @Override
  public Array getArray(int columnIndex) throws SQLException {
    throw new SQLException("Array not supported");
  }

  @Override
  public Array getArray(String columnLabel) throws SQLException {
    throw new SQLException("Array not supported");
  }

  @Override
  public Date getDate(int columnIndex, Calendar cal) throws SQLException {
    return getDate(columnIndex);
  }

  @Override
  public Date getDate(String columnLabel, Calendar cal) throws SQLException {
    return getDate(columnLabel);
  }

  @Override
  public Time getTime(int columnIndex, Calendar cal) throws SQLException {
    return getTime(columnIndex);
  }

  @Override
  public Time getTime(String columnLabel, Calendar cal) throws SQLException {
    return getTime(columnLabel);
  }

  @Override
  public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
    return getTimestamp(columnIndex);
  }

  @Override
  public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
    return getTimestamp(columnLabel);
  }

  @Override
  public URL getURL(int columnIndex) throws SQLException {
    throw new SQLException("URL not supported");
  }

  @Override
  public URL getURL(String columnLabel) throws SQLException {
    throw new SQLException("URL not supported");
  }

  // More unsupported update methods
  @Override
  public void updateRef(int columnIndex, Ref x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateRef(String columnLabel, Ref x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBlob(int columnIndex, Blob x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBlob(String columnLabel, Blob x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateClob(int columnIndex, Clob x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateClob(String columnLabel, Clob x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateArray(int columnIndex, Array x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateArray(String columnLabel, Array x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  // Additional JDBC 4.0+ methods
  @Override
  public RowId getRowId(int columnIndex) throws SQLException {
    throw new SQLException("RowId not supported");
  }

  @Override
  public RowId getRowId(String columnLabel) throws SQLException {
    throw new SQLException("RowId not supported");
  }

  @Override
  public void updateRowId(int columnIndex, RowId x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateRowId(String columnLabel, RowId x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public NClob getNClob(int columnIndex) throws SQLException {
    throw new SQLException("NClob not supported");
  }

  @Override
  public NClob getNClob(String columnLabel) throws SQLException {
    throw new SQLException("NClob not supported");
  }

  @Override
  public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public String getNString(int columnIndex) throws SQLException {
    return getString(columnIndex);
  }

  @Override
  public String getNString(String columnLabel) throws SQLException {
    return getString(columnLabel);
  }

  @Override
  public Reader getNCharacterStream(int columnIndex) throws SQLException {
    throw new SQLException("getNCharacterStream not supported");
  }

  @Override
  public Reader getNCharacterStream(String columnLabel) throws SQLException {
    throw new SQLException("getNCharacterStream not supported");
  }

  @Override
  public void updateNString(int columnIndex, String nString) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateNString(String columnLabel, String nString) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateNCharacterStream(String columnLabel, Reader reader, long length)
      throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x, long length)
      throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x, long length)
      throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader, long length)
      throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBlob(int columnIndex, InputStream inputStream, long length)
      throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBlob(String columnLabel, InputStream inputStream, long length)
      throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateClob(int columnIndex, Reader reader) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateClob(String columnLabel, Reader reader) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateNClob(int columnIndex, Reader reader) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateNClob(String columnLabel, Reader reader) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  // JDBC 4.1 methods
  @Override
  public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) {
      return null;
    }

    if (type.isInstance(value)) {
      return type.cast(value);
    }

    // Basic type conversions
    if (type == String.class) {
      return type.cast(value.toString());
    }
    if (type == Integer.class && value instanceof Number) {
      return type.cast(((Number) value).intValue());
    }
    if (type == Long.class && value instanceof Number) {
      return type.cast(((Number) value).longValue());
    }
    if (type == Double.class && value instanceof Number) {
      return type.cast(((Number) value).doubleValue());
    }
    if (type == Boolean.class) {
      if (value instanceof Boolean) {
        return type.cast(value);
      }
      if (value instanceof Number) {
        return type.cast(((Number) value).intValue() != 0);
      }
      return type.cast(Boolean.parseBoolean(value.toString()));
    }

    throw new SQLException(
        "Cannot convert " + value.getClass().getName() + " to " + type.getName());
  }

  @Override
  public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
    return getObject(getColumnIndex(columnLabel), type);
  }

  // SQLXML support
  @Override
  public SQLXML getSQLXML(int columnIndex) throws SQLException {
    throw new SQLException("SQLXML not supported");
  }

  @Override
  public SQLXML getSQLXML(String columnLabel) throws SQLException {
    throw new SQLException("SQLXML not supported");
  }

  @Override
  public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  @Override
  public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
    throw new SQLException("Updates not supported");
  }

  // Wrapper interface
  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface.isInstance(this)) {
      return iface.cast(this);
    }
    throw new SQLException("Cannot unwrap to " + iface.getName());
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return iface.isInstance(this);
  }
}
