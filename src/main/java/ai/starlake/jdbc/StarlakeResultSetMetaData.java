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

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

public class StarlakeResultSetMetaData implements ResultSetMetaData {
  private final ResultSetMetaData wrapped;

  public StarlakeResultSetMetaData(ResultSetMetaData wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public int getColumnCount() throws SQLException {
    return wrapped.getColumnCount();
  }

  @Override
  public boolean isAutoIncrement(int column) throws SQLException {
    return wrapped.isAutoIncrement(column);
  }

  @Override
  public boolean isCaseSensitive(int column) throws SQLException {
    return wrapped.isCaseSensitive(column);
  }

  @Override
  public boolean isSearchable(int column) throws SQLException {
    return wrapped.isSearchable(column);
  }

  @Override
  public boolean isCurrency(int column) throws SQLException {
    return wrapped.isCurrency(column);
  }

  @Override
  public int isNullable(int column) throws SQLException {
    return wrapped.isNullable(column);
  }

  @Override
  public boolean isSigned(int column) throws SQLException {
    return wrapped.isSigned(column);
  }

  @Override
  public int getColumnDisplaySize(int column) throws SQLException {
    return wrapped.getColumnDisplaySize(column);
  }

  @Override
  public String getColumnLabel(int column) throws SQLException {
    return wrapped.getColumnLabel(column);
  }

  @Override
  public String getColumnName(int column) throws SQLException {
    return wrapped.getColumnName(column);
  }

  @Override
  public String getSchemaName(int column) throws SQLException {
    return wrapped.getSchemaName(column);
  }

  @Override
  public int getPrecision(int column) throws SQLException {
    return wrapped.getPrecision(column);
  }

  @Override
  public int getScale(int column) throws SQLException {
    return wrapped.getScale(column);
  }

  @Override
  public String getTableName(int column) throws SQLException {
    return wrapped.getTableName(column);
  }

  @Override
  public String getCatalogName(int column) throws SQLException {
    return wrapped.getCatalogName(column);
  }

  @Override
  public int getColumnType(int column) throws SQLException {
    return wrapped.getColumnType(column);
  }

  @Override
  public String getColumnTypeName(int column) throws SQLException {
    return wrapped.getColumnTypeName(column);
  }

  @Override
  public boolean isReadOnly(int column) throws SQLException {
    return wrapped.isReadOnly(column);
  }

  @Override
  public boolean isWritable(int column) throws SQLException {
    return wrapped.isWritable(column);
  }

  @Override
  public boolean isDefinitelyWritable(int column) throws SQLException {
    return wrapped.isDefinitelyWritable(column);
  }

  @Override
  public String getColumnClassName(int column) throws SQLException {
    return wrapped.getColumnClassName(column);
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return wrapped.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return wrapped.isWrapperFor(iface);
  }
}
