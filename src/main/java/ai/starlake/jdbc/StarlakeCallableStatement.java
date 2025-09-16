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
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

public class StarlakeCallableStatement extends StarlakeStatement implements CallableStatement {
  private final CallableStatement wrapped;

  public StarlakeCallableStatement(StarlakeConnection connection, CallableStatement wrapped) {
    super(connection, wrapped);
    this.connection = connection;
    this.wrapped = wrapped;
  }

  @Override
  public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
    wrapped.registerOutParameter(parameterIndex, sqlType);
  }

  @Override
  public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
    wrapped.registerOutParameter(parameterIndex, sqlType, scale);
  }

  @Override
  public boolean wasNull() throws SQLException {
    return wrapped.wasNull();
  }

  @Override
  public String getString(int parameterIndex) throws SQLException {
    return wrapped.getString(parameterIndex);
  }

  @Override
  public boolean getBoolean(int parameterIndex) throws SQLException {
    return wrapped.getBoolean(parameterIndex);
  }

  @Override
  public byte getByte(int parameterIndex) throws SQLException {
    return wrapped.getByte(parameterIndex);
  }

  @Override
  public short getShort(int parameterIndex) throws SQLException {
    return wrapped.getShort(parameterIndex);
  }

  @Override
  public int getInt(int parameterIndex) throws SQLException {
    return wrapped.getInt(parameterIndex);
  }

  @Override
  public long getLong(int parameterIndex) throws SQLException {
    return wrapped.getLong(parameterIndex);
  }

  @Override
  public float getFloat(int parameterIndex) throws SQLException {
    return wrapped.getFloat(parameterIndex);
  }

  @Override
  public double getDouble(int parameterIndex) throws SQLException {
    return wrapped.getDouble(parameterIndex);
  }

  @Deprecated(since = "1.2")
  @Override
  public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
    return wrapped.getBigDecimal(parameterIndex, scale);
  }

  @Override
  public byte[] getBytes(int parameterIndex) throws SQLException {
    return wrapped.getBytes(parameterIndex);
  }

  @Override
  public Date getDate(int parameterIndex) throws SQLException {
    return wrapped.getDate(parameterIndex);
  }

  @Override
  public Time getTime(int parameterIndex) throws SQLException {
    return wrapped.getTime(parameterIndex);
  }

  @Override
  public Timestamp getTimestamp(int parameterIndex) throws SQLException {
    return wrapped.getTimestamp(parameterIndex);
  }

  @Override
  public Object getObject(int parameterIndex) throws SQLException {
    return wrapped.getObject(parameterIndex);
  }

  @Override
  public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
    return wrapped.getBigDecimal(parameterIndex);
  }

  @Override
  public Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException {
    return wrapped.getObject(parameterIndex, map);
  }

  @Override
  public Ref getRef(int parameterIndex) throws SQLException {
    return wrapped.getRef(parameterIndex);
  }

  @Override
  public Blob getBlob(int parameterIndex) throws SQLException {
    return wrapped.getBlob(parameterIndex);
  }

  @Override
  public Clob getClob(int parameterIndex) throws SQLException {
    return wrapped.getClob(parameterIndex);
  }

  @Override
  public Array getArray(int parameterIndex) throws SQLException {
    return wrapped.getArray(parameterIndex);
  }

  @Override
  public Date getDate(int parameterIndex, Calendar cal) throws SQLException {
    return wrapped.getDate(parameterIndex, cal);
  }

  @Override
  public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
    return wrapped.getTime(parameterIndex, cal);
  }

  @Override
  public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
    return wrapped.getTimestamp(parameterIndex, cal);
  }

  @Override
  public void registerOutParameter(int parameterIndex, int sqlType, String typeName)
      throws SQLException {
    wrapped.registerOutParameter(parameterIndex, sqlType, typeName);
  }

  @Override
  public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
    wrapped.registerOutParameter(parameterName, sqlType);
  }

  @Override
  public void registerOutParameter(String parameterName, int sqlType, int scale)
      throws SQLException {
    wrapped.registerOutParameter(parameterName, sqlType, scale);
  }

  @Override
  public void registerOutParameter(String parameterName, int sqlType, String typeName)
      throws SQLException {
    wrapped.registerOutParameter(parameterName, sqlType, typeName);
  }

  @Override
  public URL getURL(int parameterIndex) throws SQLException {
    return wrapped.getURL(parameterIndex);
  }

  @Override
  public void setURL(String parameterName, URL val) throws SQLException {
    wrapped.setURL(parameterName, val);
  }

  @Override
  public void setNull(String parameterName, int sqlType) throws SQLException {
    wrapped.setNull(parameterName, sqlType);
  }

  @Override
  public void setBoolean(String parameterName, boolean x) throws SQLException {
    wrapped.setBoolean(parameterName, x);
  }

  @Override
  public void setByte(String parameterName, byte x) throws SQLException {
    wrapped.setByte(parameterName, x);
  }

  @Override
  public void setShort(String parameterName, short x) throws SQLException {
    wrapped.setShort(parameterName, x);
  }

  @Override
  public void setInt(String parameterName, int x) throws SQLException {
    wrapped.setInt(parameterName, x);
  }

  @Override
  public void setLong(String parameterName, long x) throws SQLException {
    wrapped.setLong(parameterName, x);
  }

  @Override
  public void setFloat(String parameterName, float x) throws SQLException {
    wrapped.setFloat(parameterName, x);
  }

  @Override
  public void setDouble(String parameterName, double x) throws SQLException {
    wrapped.setDouble(parameterName, x);
  }

  @Override
  public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException {
    wrapped.setBigDecimal(parameterName, x);
  }

  @Override
  public void setString(String parameterName, String x) throws SQLException {
    wrapped.setString(parameterName, x);
  }

  @Override
  public void setBytes(String parameterName, byte[] x) throws SQLException {
    wrapped.setBytes(parameterName, x);
  }

  @Override
  public void setDate(String parameterName, Date x) throws SQLException {
    wrapped.setDate(parameterName, x);
  }

  @Override
  public void setTime(String parameterName, Time x) throws SQLException {
    wrapped.setTime(parameterName, x);
  }

  @Override
  public void setTimestamp(String parameterName, Timestamp x) throws SQLException {
    wrapped.setTimestamp(parameterName, x);
  }

  @Override
  public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException {
    wrapped.setAsciiStream(parameterName, x, length);
  }

  @Override
  public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException {
    wrapped.setBinaryStream(parameterName, x, length);
  }

  @Override
  public void setObject(String parameterName, Object x, int targetSqlType, int scale)
      throws SQLException {
    wrapped.setObject(parameterName, x, targetSqlType, scale);
  }

  @Override
  public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {
    wrapped.setObject(parameterName, x, targetSqlType);
  }

  @Override
  public void setObject(String parameterName, Object x) throws SQLException {
    wrapped.setObject(parameterName, x);
  }

  @Override
  public void setCharacterStream(String parameterName, Reader reader, int length)
      throws SQLException {
    wrapped.setCharacterStream(parameterName, reader, length);
  }

  @Override
  public void setDate(String parameterName, Date x, Calendar cal) throws SQLException {
    wrapped.setDate(parameterName, x, cal);
  }

  @Override
  public void setTime(String parameterName, Time x, Calendar cal) throws SQLException {
    wrapped.setTime(parameterName, x, cal);
  }

  @Override
  public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException {
    wrapped.setTimestamp(parameterName, x, cal);
  }

  @Override
  public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {
    wrapped.setNull(parameterName, sqlType, typeName);
  }

  @Override
  public String getString(String parameterName) throws SQLException {
    return wrapped.getString(parameterName);
  }

  @Override
  public boolean getBoolean(String parameterName) throws SQLException {
    return wrapped.getBoolean(parameterName);
  }

  @Override
  public byte getByte(String parameterName) throws SQLException {
    return wrapped.getByte(parameterName);
  }

  @Override
  public short getShort(String parameterName) throws SQLException {
    return wrapped.getShort(parameterName);
  }

  @Override
  public int getInt(String parameterName) throws SQLException {
    return wrapped.getInt(parameterName);
  }

  @Override
  public long getLong(String parameterName) throws SQLException {
    return wrapped.getLong(parameterName);
  }

  @Override
  public float getFloat(String parameterName) throws SQLException {
    return wrapped.getFloat(parameterName);
  }

  @Override
  public double getDouble(String parameterName) throws SQLException {
    return wrapped.getDouble(parameterName);
  }

  @Override
  public byte[] getBytes(String parameterName) throws SQLException {
    return wrapped.getBytes(parameterName);
  }

  @Override
  public Date getDate(String parameterName) throws SQLException {
    return wrapped.getDate(parameterName);
  }

  @Override
  public Time getTime(String parameterName) throws SQLException {
    return wrapped.getTime(parameterName);
  }

  @Override
  public Timestamp getTimestamp(String parameterName) throws SQLException {
    return wrapped.getTimestamp(parameterName);
  }

  @Override
  public Object getObject(String parameterName) throws SQLException {
    return wrapped.getObject(parameterName);
  }

  @Override
  public BigDecimal getBigDecimal(String parameterName) throws SQLException {
    return wrapped.getBigDecimal(parameterName);
  }

  @Override
  public Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException {
    return wrapped.getObject(parameterName, map);
  }

  @Override
  public Ref getRef(String parameterName) throws SQLException {
    return wrapped.getRef(parameterName);
  }

  @Override
  public Blob getBlob(String parameterName) throws SQLException {
    return wrapped.getBlob(parameterName);
  }

  @Override
  public Clob getClob(String parameterName) throws SQLException {
    return wrapped.getClob(parameterName);
  }

  @Override
  public Array getArray(String parameterName) throws SQLException {
    return wrapped.getArray(parameterName);
  }

  @Override
  public Date getDate(String parameterName, Calendar cal) throws SQLException {
    return wrapped.getDate(parameterName, cal);
  }

  @Override
  public Time getTime(String parameterName, Calendar cal) throws SQLException {
    return wrapped.getTime(parameterName, cal);
  }

  @Override
  public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException {
    return wrapped.getTimestamp(parameterName, cal);
  }

  @Override
  public URL getURL(String parameterName) throws SQLException {
    return wrapped.getURL(parameterName);
  }

  @Override
  public RowId getRowId(int parameterIndex) throws SQLException {
    return wrapped.getRowId(parameterIndex);
  }

  @Override
  public RowId getRowId(String parameterName) throws SQLException {
    return wrapped.getRowId(parameterName);
  }

  @Override
  public void setRowId(String parameterName, RowId x) throws SQLException {
    wrapped.setRowId(parameterName, x);
  }

  @Override
  public void setNString(String parameterName, String value) throws SQLException {
    wrapped.setNString(parameterName, value);
  }

  @Override
  public void setNCharacterStream(String parameterName, Reader value, long length)
      throws SQLException {
    wrapped.setNCharacterStream(parameterName, value, length);
  }

  @Override
  public void setNClob(String parameterName, NClob value) throws SQLException {
    wrapped.setNClob(parameterName, value);
  }

  @Override
  public void setClob(String parameterName, Reader reader, long length) throws SQLException {
    wrapped.setClob(parameterName, reader, length);
  }

  @Override
  public void setBlob(String parameterName, InputStream inputStream, long length)
      throws SQLException {
    wrapped.setBlob(parameterName, inputStream, length);
  }

  @Override
  public void setNClob(String parameterName, Reader reader, long length) throws SQLException {
    wrapped.setNClob(parameterName, reader, length);
  }

  @Override
  public NClob getNClob(int parameterIndex) throws SQLException {
    return wrapped.getNClob(parameterIndex);
  }

  @Override
  public NClob getNClob(String parameterName) throws SQLException {
    return wrapped.getNClob(parameterName);
  }

  @Override
  public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {
    wrapped.setSQLXML(parameterName, xmlObject);
  }

  @Override
  public SQLXML getSQLXML(int parameterIndex) throws SQLException {
    return wrapped.getSQLXML(parameterIndex);
  }

  @Override
  public SQLXML getSQLXML(String parameterName) throws SQLException {
    return wrapped.getSQLXML(parameterName);
  }

  @Override
  public String getNString(int parameterIndex) throws SQLException {
    return wrapped.getNString(parameterIndex);
  }

  @Override
  public String getNString(String parameterName) throws SQLException {
    return wrapped.getNString(parameterName);
  }

  @Override
  public Reader getNCharacterStream(int parameterIndex) throws SQLException {
    return wrapped.getNCharacterStream(parameterIndex);
  }

  @Override
  public Reader getNCharacterStream(String parameterName) throws SQLException {
    return wrapped.getNCharacterStream(parameterName);
  }

  @Override
  public Reader getCharacterStream(int parameterIndex) throws SQLException {
    return wrapped.getCharacterStream(parameterIndex);
  }

  @Override
  public Reader getCharacterStream(String parameterName) throws SQLException {
    return wrapped.getCharacterStream(parameterName);
  }

  @Override
  public void setBlob(String parameterName, Blob x) throws SQLException {
    wrapped.setBlob(parameterName, x);
  }

  @Override
  public void setClob(String parameterName, Clob x) throws SQLException {
    wrapped.setClob(parameterName, x);
  }

  @Override
  public void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException {
    wrapped.setAsciiStream(parameterName, x, length);
  }

  @Override
  public void setBinaryStream(String parameterName, InputStream x, long length)
      throws SQLException {
    wrapped.setBinaryStream(parameterName, x, length);
  }

  @Override
  public void setCharacterStream(String parameterName, Reader reader, long length)
      throws SQLException {
    wrapped.setCharacterStream(parameterName, reader, length);
  }

  @Override
  public void setAsciiStream(String parameterName, InputStream x) throws SQLException {
    wrapped.setAsciiStream(parameterName, x);
  }

  @Override
  public void setBinaryStream(String parameterName, InputStream x) throws SQLException {
    wrapped.setBinaryStream(parameterName, x);
  }

  @Override
  public void setCharacterStream(String parameterName, Reader reader) throws SQLException {
    wrapped.setCharacterStream(parameterName, reader);
  }

  @Override
  public void setNCharacterStream(String parameterName, Reader value) throws SQLException {
    wrapped.setNCharacterStream(parameterName, value);
  }

  @Override
  public void setClob(String parameterName, Reader reader) throws SQLException {
    wrapped.setClob(parameterName, reader);
  }

  @Override
  public void setBlob(String parameterName, InputStream inputStream) throws SQLException {
    wrapped.setBlob(parameterName, inputStream);
  }

  @Override
  public void setNClob(String parameterName, Reader reader) throws SQLException {
    wrapped.setNClob(parameterName, reader);
  }

  @Override
  public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
    return wrapped.getObject(parameterIndex, type);
  }

  @Override
  public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
    return wrapped.getObject(parameterName, type);
  }

  @Override
  public void setObject(String parameterName, Object x, SQLType targetSqlType, int scaleOrLength)
      throws SQLException {
    wrapped.setObject(parameterName, x, targetSqlType, scaleOrLength);
  }

  @Override
  public void setObject(String parameterName, Object x, SQLType targetSqlType) throws SQLException {
    wrapped.setObject(parameterName, x, targetSqlType);
  }

  @Override
  public void registerOutParameter(int parameterIndex, SQLType sqlType) throws SQLException {
    wrapped.registerOutParameter(parameterIndex, sqlType);
  }

  @Override
  public void registerOutParameter(int parameterIndex, SQLType sqlType, int scale)
      throws SQLException {
    wrapped.registerOutParameter(parameterIndex, sqlType, scale);
  }

  @Override
  public void registerOutParameter(int parameterIndex, SQLType sqlType, String typeName)
      throws SQLException {
    wrapped.registerOutParameter(parameterIndex, sqlType, typeName);
  }

  @Override
  public void registerOutParameter(String parameterName, SQLType sqlType) throws SQLException {
    wrapped.registerOutParameter(parameterName, sqlType);
  }

  @Override
  public void registerOutParameter(String parameterName, SQLType sqlType, int scale)
      throws SQLException {
    wrapped.registerOutParameter(parameterName, sqlType, scale);
  }

  @Override
  public void registerOutParameter(String parameterName, SQLType sqlType, String typeName)
      throws SQLException {
    wrapped.registerOutParameter(parameterName, sqlType, typeName);
  }

  @Override
  public ResultSet executeQuery() throws SQLException {
    return wrapped.executeQuery();
  }

  @Override
  public int executeUpdate() throws SQLException {
    return wrapped.executeUpdate();
  }

  @Override
  public void setNull(int parameterIndex, int sqlType) throws SQLException {
    wrapped.setNull(parameterIndex, sqlType);
  }

  @Override
  public void setBoolean(int parameterIndex, boolean x) throws SQLException {
    wrapped.setBoolean(parameterIndex, x);
  }

  @Override
  public void setByte(int parameterIndex, byte x) throws SQLException {
    wrapped.setByte(parameterIndex, x);
  }

  @Override
  public void setShort(int parameterIndex, short x) throws SQLException {
    wrapped.setShort(parameterIndex, x);
  }

  @Override
  public void setInt(int parameterIndex, int x) throws SQLException {
    wrapped.setInt(parameterIndex, x);
  }

  @Override
  public void setLong(int parameterIndex, long x) throws SQLException {
    wrapped.setLong(parameterIndex, x);
  }

  @Override
  public void setFloat(int parameterIndex, float x) throws SQLException {
    wrapped.setFloat(parameterIndex, x);
  }

  @Override
  public void setDouble(int parameterIndex, double x) throws SQLException {
    wrapped.setDouble(parameterIndex, x);
  }

  @Override
  public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
    wrapped.setBigDecimal(parameterIndex, x);
  }

  @Override
  public void setString(int parameterIndex, String x) throws SQLException {
    wrapped.setString(parameterIndex, x);
  }

  @Override
  public void setBytes(int parameterIndex, byte[] x) throws SQLException {
    wrapped.setBytes(parameterIndex, x);
  }

  @Override
  public void setDate(int parameterIndex, Date x) throws SQLException {
    wrapped.setDate(parameterIndex, x);
  }

  @Override
  public void setTime(int parameterIndex, Time x) throws SQLException {
    wrapped.setTime(parameterIndex, x);
  }

  @Override
  public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
    wrapped.setTimestamp(parameterIndex, x);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
    wrapped.setAsciiStream(parameterIndex, x, length);
  }

  @Deprecated(since = "1.2")
  @Override
  public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
    wrapped.setUnicodeStream(parameterIndex, x, length);
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
    wrapped.setBinaryStream(parameterIndex, x, length);
  }

  @Override
  public void clearParameters() throws SQLException {
    wrapped.clearParameters();
  }

  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
    wrapped.setObject(parameterIndex, x, targetSqlType);
  }

  @Override
  public void setObject(int parameterIndex, Object x) throws SQLException {
    wrapped.setObject(parameterIndex, x);
  }

  @Override
  public boolean execute() throws SQLException {
    return wrapped.execute();
  }

  @Override
  public void addBatch() throws SQLException {
    wrapped.addBatch();
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader, int length)
      throws SQLException {
    wrapped.setCharacterStream(parameterIndex, reader, length);
  }

  @Override
  public void setRef(int parameterIndex, Ref x) throws SQLException {
    wrapped.setRef(parameterIndex, x);
  }

  @Override
  public void setBlob(int parameterIndex, Blob x) throws SQLException {
    wrapped.setBlob(parameterIndex, x);
  }

  @Override
  public void setClob(int parameterIndex, Clob x) throws SQLException {
    wrapped.setClob(parameterIndex, x);
  }

  @Override
  public void setArray(int parameterIndex, Array x) throws SQLException {
    wrapped.setArray(parameterIndex, x);
  }

  @Override
  public ResultSetMetaData getMetaData() throws SQLException {
    return wrapped.getMetaData();
  }

  @Override
  public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
    wrapped.setDate(parameterIndex, x, cal);
  }

  @Override
  public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
    wrapped.setTime(parameterIndex, x, cal);
  }

  @Override
  public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
    wrapped.setTimestamp(parameterIndex, x, cal);
  }

  @Override
  public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
    wrapped.setNull(parameterIndex, sqlType, typeName);
  }

  @Override
  public void setURL(int parameterIndex, URL x) throws SQLException {
    wrapped.setURL(parameterIndex, x);
  }

  @Override
  public ParameterMetaData getParameterMetaData() throws SQLException {
    return wrapped.getParameterMetaData();
  }

  @Override
  public void setRowId(int parameterIndex, RowId x) throws SQLException {
    wrapped.setRowId(parameterIndex, x);
  }

  @Override
  public void setNString(int parameterIndex, String value) throws SQLException {
    wrapped.setNString(parameterIndex, value);
  }

  @Override
  public void setNCharacterStream(int parameterIndex, Reader value, long length)
      throws SQLException {
    wrapped.setNCharacterStream(parameterIndex, value, length);
  }

  @Override
  public void setNClob(int parameterIndex, NClob value) throws SQLException {
    wrapped.setNClob(parameterIndex, value);
  }

  @Override
  public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
    wrapped.setClob(parameterIndex, reader, length);
  }

  @Override
  public void setBlob(int parameterIndex, InputStream inputStream, long length)
      throws SQLException {
    wrapped.setBlob(parameterIndex, inputStream, length);
  }

  @Override
  public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
    wrapped.setNClob(parameterIndex, reader, length);
  }

  @Override
  public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
    wrapped.setSQLXML(parameterIndex, xmlObject);
  }

  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength)
      throws SQLException {
    wrapped.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
    wrapped.setAsciiStream(parameterIndex, x, length);
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
    wrapped.setBinaryStream(parameterIndex, x, length);
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader, long length)
      throws SQLException {
    wrapped.setCharacterStream(parameterIndex, reader, length);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
    wrapped.setAsciiStream(parameterIndex, x);
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
    wrapped.setBinaryStream(parameterIndex, x);
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
    wrapped.setCharacterStream(parameterIndex, reader);
  }

  @Override
  public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
    wrapped.setNCharacterStream(parameterIndex, value);
  }

  @Override
  public void setClob(int parameterIndex, Reader reader) throws SQLException {
    wrapped.setClob(parameterIndex, reader);
  }

  @Override
  public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
    wrapped.setBlob(parameterIndex, inputStream);
  }

  @Override
  public void setNClob(int parameterIndex, Reader reader) throws SQLException {
    wrapped.setNClob(parameterIndex, reader);
  }

  @Override
  public void setObject(int parameterIndex, Object x, SQLType targetSqlType, int scaleOrLength)
      throws SQLException {
    wrapped.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
  }

  @Override
  public void setObject(int parameterIndex, Object x, SQLType targetSqlType) throws SQLException {
    wrapped.setObject(parameterIndex, x, targetSqlType);
  }

  @Override
  public long executeLargeUpdate() throws SQLException {
    return wrapped.executeLargeUpdate();
  }
}
