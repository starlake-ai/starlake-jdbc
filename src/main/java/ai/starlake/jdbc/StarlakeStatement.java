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

import ai.starlake.transpiler.JSQLTranspiler;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.SessionStatement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.drop.Drop;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.logging.Level;

public class StarlakeStatement implements Statement {
  private final StarlakeConnection connection;
  private final Statement wrapped;

  public StarlakeStatement(StarlakeConnection connection, Statement wrapped) {
    this.connection = connection;
    this.wrapped = wrapped;
  }

  @Override
  public ResultSet executeQuery(String sql) throws SQLException {
    try {
      // @todo: avoid double parsing and unpipe into Statement instead of String
      String unpipedSql = sql;
      JSQLTranspiler.unpipe(sql, connection.executor, p -> {
      });

      final net.sf.jsqlparser.statement.Statement st =
          CCJSqlParserUtil.parse(unpipedSql, connection.executor, parser -> {
          });

      if (st instanceof SessionStatement) {
        return connection.executeQuery((SessionStatement) st);
      } else {
        if (connection.currentSession != null) {
          String rewrittenSql = connection.currentSession.rewriteSql(st, sql);
          return new StarlakeResultSet(this, wrapped.executeQuery(rewrittenSql));
        } else {
          return new StarlakeResultSet(this, wrapped.executeQuery(unpipedSql));
        }
      }
    } catch (JSQLParserException e) {
      StarlakeConnection.LOGGER.log(Level.WARNING, "Statement can not be parsed:\n" + sql);
      return wrapped.executeQuery(sql);
    }
  }

  @Override
  public int executeUpdate(String sql) throws SQLException {
    try {
      // @todo: avoid double parsing and unpipe into Statement instead of String
      String unpipedSql = JSQLTranspiler.unpipe(sql, connection.executor, p -> {
      });

      final net.sf.jsqlparser.statement.Statement st =
          CCJSqlParserUtil.parse(unpipedSql, connection.executor, p -> {
          });

      if (st instanceof SessionStatement) {
        connection.execute((SessionStatement) st);
        return -1;
      } else {
        if (connection.currentSession != null) {
          String rewrittenSql = connection.currentSession.rewriteSql(st, sql);
          int i = wrapped.executeUpdate(rewrittenSql);

          // update the metadata when tables have been changed
          if (st instanceof CreateTable) {
            Table t = new Table(((CreateTable) st).getTable().getFullyQualifiedName());
            try {
              connection.currentSession.metaData.updateTable(connection.wrapped, t);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          } else if (st instanceof Alter) {
            Table t = new Table(((Alter) st).getTable().getFullyQualifiedName());
            try {
              connection.currentSession.metaData.updateTable(connection.wrapped, t);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          } else if (st instanceof Drop) {
            Drop drop = (Drop) st;
            if ("TABLE".equalsIgnoreCase(drop.getType())) {
              Table t = new Table(drop.getName().getFullyQualifiedName());
              try {
                connection.currentSession.metaData.dropTable(t);
              } catch (SQLException e) {
                throw new RuntimeException(e);
              }
            }
          }
          return i;

        } else {
          return wrapped.executeUpdate(unpipedSql);
        }
      }
    } catch (JSQLParserException e) {
      StarlakeConnection.LOGGER.log(Level.WARNING, "Statement can not be parsed:\n" + sql);
      return wrapped.executeUpdate(sql);
    }
  }

  @Override
  public void close() throws SQLException {
    wrapped.close();
  }

  @Override
  public int getMaxFieldSize() throws SQLException {
    return wrapped.getMaxFieldSize();
  }

  @Override
  public void setMaxFieldSize(int max) throws SQLException {
    wrapped.setMaxFieldSize(max);
  }

  @Override
  public int getMaxRows() throws SQLException {
    return wrapped.getMaxRows();
  }

  @Override
  public void setMaxRows(int max) throws SQLException {
    wrapped.setMaxRows(max);
  }

  @Override
  public void setEscapeProcessing(boolean enable) throws SQLException {
    wrapped.setEscapeProcessing(enable);
  }

  @Override
  public int getQueryTimeout() throws SQLException {
    return wrapped.getQueryTimeout();
  }

  @Override
  public void setQueryTimeout(int seconds) throws SQLException {
    wrapped.setQueryTimeout(seconds);
  }

  @Override
  public void cancel() throws SQLException {
    wrapped.cancel();
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    return wrapped.getWarnings();
  }

  @Override
  public void clearWarnings() throws SQLException {
    wrapped.clearWarnings();
  }

  @Override
  public void setCursorName(String name) throws SQLException {
    wrapped.setCursorName(name);
  }

  @Override
  public boolean execute(String sql) throws SQLException {
    try {
      // @todo: avoid double parsing and unpipe into Statement instead of String
      String unpipedSql = JSQLTranspiler.unpipe(sql, connection.executor, p -> {
      });

      final net.sf.jsqlparser.statement.Statement st =
          CCJSqlParserUtil.parse(unpipedSql, connection.executor, p -> {
          });

      if (st instanceof SessionStatement) {
        return connection.execute((SessionStatement) st);
      } else {
        if (connection.currentSession != null) {
          String rewrittenSql = connection.currentSession.rewriteSql(st, sql);
          return wrapped.execute(rewrittenSql);
        } else {
          return wrapped.execute(unpipedSql);
        }
      }
    } catch (JSQLParserException e) {
      StarlakeConnection.LOGGER.log(Level.WARNING, "Statement can not be parsed:\n" + sql);
      return wrapped.execute(sql);
    }
  }

  @Override
  public ResultSet getResultSet() throws SQLException {
    return new StarlakeResultSet(this, wrapped.getResultSet());
  }

  @Override
  public int getUpdateCount() throws SQLException {
    return wrapped.getUpdateCount();
  }

  @Override
  public boolean getMoreResults() throws SQLException {
    return wrapped.getMoreResults();
  }

  @Override
  public void setFetchDirection(int direction) throws SQLException {
    wrapped.setFetchDirection(direction);
  }

  @Override
  public int getFetchDirection() throws SQLException {
    return wrapped.getFetchDirection();
  }

  @Override
  public void setFetchSize(int rows) throws SQLException {
    wrapped.setFetchSize(rows);
  }

  @Override
  public int getFetchSize() throws SQLException {
    return wrapped.getFetchSize();
  }

  @Override
  public int getResultSetConcurrency() throws SQLException {
    return wrapped.getResultSetConcurrency();
  }

  @Override
  public int getResultSetType() throws SQLException {
    return wrapped.getResultSetType();
  }

  @Override
  public void addBatch(String sql) throws SQLException {
    wrapped.addBatch(sql);
  }

  @Override
  public void clearBatch() throws SQLException {
    wrapped.clearBatch();
  }

  @Override
  public int[] executeBatch() throws SQLException {
    return wrapped.executeBatch();
  }

  @Override
  public Connection getConnection() throws SQLException {
    return connection;
  }

  @Override
  public boolean getMoreResults(int current) throws SQLException {
    return wrapped.getMoreResults(current);
  }

  @Override
  public ResultSet getGeneratedKeys() throws SQLException {
    return new StarlakeResultSet(this, wrapped.getGeneratedKeys());
  }

  @Override
  public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
    return wrapped.executeUpdate(sql, autoGeneratedKeys);
  }

  @Override
  public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
    return wrapped.executeUpdate(sql, columnIndexes);
  }

  @Override
  public int executeUpdate(String sql, String[] columnNames) throws SQLException {
    return wrapped.executeUpdate(sql, columnNames);
  }

  @Override
  public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
    return wrapped.execute(sql, autoGeneratedKeys);
  }

  @Override
  public boolean execute(String sql, int[] columnIndexes) throws SQLException {
    return wrapped.execute(sql, columnIndexes);
  }

  @Override
  public boolean execute(String sql, String[] columnNames) throws SQLException {
    return wrapped.execute(sql, columnNames);
  }

  @Override
  public int getResultSetHoldability() throws SQLException {
    return wrapped.getResultSetHoldability();
  }

  @Override
  public boolean isClosed() throws SQLException {
    return wrapped.isClosed();
  }

  @Override
  public void setPoolable(boolean poolable) throws SQLException {
    wrapped.setPoolable(poolable);
  }

  @Override
  public boolean isPoolable() throws SQLException {
    return wrapped.isPoolable();
  }

  @Override
  public void closeOnCompletion() throws SQLException {
    wrapped.closeOnCompletion();
  }

  @Override
  public boolean isCloseOnCompletion() throws SQLException {
    return wrapped.isCloseOnCompletion();
  }

  @Override
  public long getLargeUpdateCount() throws SQLException {
    return wrapped.getLargeUpdateCount();
  }

  @Override
  public void setLargeMaxRows(long max) throws SQLException {
    wrapped.setLargeMaxRows(max);
  }

  @Override
  public long getLargeMaxRows() throws SQLException {
    return wrapped.getLargeMaxRows();
  }

  @Override
  public long[] executeLargeBatch() throws SQLException {
    return wrapped.executeLargeBatch();
  }

  @Override
  public long executeLargeUpdate(String sql) throws SQLException {
    return wrapped.executeLargeUpdate(sql);
  }

  @Override
  public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
    return wrapped.executeLargeUpdate(sql, autoGeneratedKeys);
  }

  @Override
  public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
    return wrapped.executeLargeUpdate(sql, columnIndexes);
  }

  @Override
  public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
    return wrapped.executeLargeUpdate(sql, columnNames);
  }

  @Override
  public String enquoteLiteral(String val) throws SQLException {
    return wrapped.enquoteLiteral(val);
  }

  @Override
  public String enquoteIdentifier(String identifier, boolean alwaysQuote) throws SQLException {
    return wrapped.enquoteIdentifier(identifier, alwaysQuote);
  }

  @Override
  public boolean isSimpleIdentifier(String identifier) throws SQLException {
    return wrapped.isSimpleIdentifier(identifier);
  }

  @Override
  public String enquoteNCharLiteral(String val) throws SQLException {
    return wrapped.enquoteNCharLiteral(val);
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
