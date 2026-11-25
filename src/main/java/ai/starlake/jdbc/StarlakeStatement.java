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
import net.sf.jsqlparser.statement.SetStatement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.UseStatement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.schema.CreateSchema;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.drop.Drop;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class StarlakeStatement implements Statement {
  protected StarlakeConnection connection;
  private final Statement wrapped;

  public StarlakeStatement(StarlakeConnection connection, Statement wrapped) {
    this.connection = connection;
    this.wrapped = wrapped;
  }

  private StatementVisitorAdapter<Boolean> objectsVisitor = new StatementVisitorAdapter<>() {
    @Override
    public <S> Boolean visit(CreateSchema createSchema, S context) {
      return connection.currentSession.dbObjects.canCreate(createSchema.getSchemaName(),
          DBObjects.ObjectType.SCHEMA);
    }

    @Override
    public <S> Boolean visit(UseStatement use, S context) {
      if (connection.currentSession.dbObjects.isCreated(use.getName(),
          DBObjects.ObjectType.SCHEMA)) {
        connection.currentSession.metaData.setCurrentSchemaName(use.getName());
        return true;
      } else {
        return false;
      }
    }

    @Override
    public <S> Boolean visit(SetStatement set, S context) {
      if ("SCHEMA".equalsIgnoreCase(String.valueOf(set.getName()))) {
        String name = String.valueOf(set.getExpressions(0));
        if (connection.currentSession.dbObjects.isCreated(name, DBObjects.ObjectType.SCHEMA)) {
          connection.currentSession.metaData.setCurrentSchemaName(name);
          return true;
        } else {
          return false;
        }
      } else {
        return null;
      }
    }

    @Override
    public <S> Boolean visit(Drop drop, S context) {
      if ("SCHEMA".equalsIgnoreCase(drop.getType())) {
        return connection.currentSession.dbObjects.canCreate(drop.getName().getFullyQualifiedName(),
            DBObjects.ObjectType.SCHEMA);
      } else {
        return null;
      }
    }
  };


  @FunctionalInterface
  private interface ResultSetExecutor {
    ResultSet execute(String sql) throws SQLException;
  }

  // Generic method for executeQuery operations
  private ResultSet executeQueryInternal(String sql, ResultSetExecutor wrappedExecutor,
      ResultSetExecutor fallbackExecutor) throws SQLException {

    try {
      // @todo: avoid double parsing and unpipe into Statement instead of String
      String unpipedSql = JSQLTranspiler.unpipe(sql, connection.executor, p -> {
      });

      final net.sf.jsqlparser.statement.Statement st =
          CCJSqlParserUtil.parse(unpipedSql, connection.executor, parser -> {
          });

      if (st instanceof SessionStatement) {
        return connection.executeQuery((SessionStatement) st);
      } else {
        if (connection.currentSession != null) {
          String rewrittenSql = connection.currentSession.rewriteSql(st, sql);

          Boolean isSchema = st.accept(objectsVisitor, connection.getMetaData());
          final ResultSet result;
          if (isSchema == null || !isSchema) {
            result = new StarlakeResultSet(this, wrappedExecutor.execute(rewrittenSql));
          } else {
            StarlakeSession.LOGGER.warning("Ignore schema DML statements.");
            result = new StarlakeVirtualResultSet("Records", "0");
          }
          connection.currentSession.recordStatement(st, sql);
          return result;
        } else {
          return new StarlakeResultSet(this, wrappedExecutor.execute(unpipedSql));
        }
      }
    } catch (JSQLParserException e) {
      return executeUnparsed(sql, e, fallbackExecutor::execute);
    }
  }

  // executeQuery methods
  @Override
  public ResultSet executeQuery(String sql) throws SQLException {
    return executeQueryInternal(sql, wrapped::executeQuery, wrapped::executeQuery);
  }

  @FunctionalInterface
  private interface SqlExecutor<T> {
    T execute(String sql) throws SQLException;
  }


  // Generic method that contains all the common logic
  private <T extends Number> T executeUpdateInternal(String sql, SqlExecutor<T> wrappedExecutor,
      SqlExecutor<T> fallbackExecutor) throws SQLException {

    try {
      // @todo: avoid double parsing and unpipe into Statement instead of String
      String unpipedSql = JSQLTranspiler.unpipe(sql, connection.executor, p -> {
      });

      final net.sf.jsqlparser.statement.Statement st =
          CCJSqlParserUtil.parse(unpipedSql, connection.executor, p -> {
          });

      if (st instanceof SessionStatement) {
        connection.execute((SessionStatement) st);
        // Return appropriate type (-1 cast to T)
        return (T) Integer.valueOf(-1);
      } else {
        if (connection.currentSession != null) {
          String rewrittenSql = connection.currentSession.rewriteSql(st, sql);
          T result;

          Boolean isSchema = st.accept(objectsVisitor, connection.getMetaData());
          if (isSchema == null || !isSchema) {
            result = wrappedExecutor.execute(rewrittenSql);
          } else {
            StarlakeSession.LOGGER.warning("Ignore schema DML statements.");
            result = (T) Integer.valueOf(-1);
          }
          connection.currentSession.recordStatement(st, sql);

          // Update metadata when tables have been changed
          updateMetadataIfNeeded(st);

          return result;
        } else {
          return wrappedExecutor.execute(unpipedSql);
        }
      }
    } catch (JSQLParserException e) {
      return executeUnparsed(sql, e, fallbackExecutor::execute);
    }
  }

  /** Statements that only read, run as is on a branch when they can not be parsed. */
  private static final List<String> UNPARSED_READ_ONLY =
      List.of("SHOW", "DESC", "DESCRIBE", "EXPLAIN", "LIST");

  /**
   * Statements that set the session context and touch no table data, run as is on a branch when
   * they can not be parsed, and replayed by APPLY.
   */
  private static final List<String> UNPARSED_CONTEXT = List.of("USE", "SET", "UNSET", "BEGIN",
      "COMMIT", "ROLLBACK", "START TRANSACTION", "ALTER SESSION");

  /**
   * Run a statement JSqlParser could not parse. Outside a branch it runs as is. On a branch it can
   * not be rewritten to read and write the branch tables, so running it as is would hit the
   * production tables: it is refused, unless it is a statement that touches no table data.
   */
  private <T> T executeUnparsed(String sql, JSQLParserException e, SqlExecutor<T> executor)
      throws SQLException {
    final StarlakeSession session = connection.activeSession();
    if (session == null) {
      StarlakeConnection.LOGGER.log(Level.WARNING, "Statement can not be parsed:\n" + sql);
      return executor.execute(sql);
    }

    final boolean readOnly = checkUnparsed(session, sql, e);
    final T result = executor.execute(sql);
    if (!readOnly) {
      session.recordStatement(null, sql);
    }
    return result;
  }

  /**
   * Check that a statement JSqlParser could not parse may run as is on a branch: only statements
   * that touch no table data may.
   *
   * @return whether the statement only reads, and so is not replayed by APPLY
   * @throws SQLException with SQLState 0A000 when the statement may not run on the branch
   */
  static boolean checkUnparsed(StarlakeSession session, String sql, JSQLParserException e)
      throws SQLException {
    final String keywords = leadingKeywords(sql);
    final boolean readOnly = startsWithAny(keywords, UNPARSED_READ_ONLY);
    if (!readOnly && !startsWithAny(keywords, UNPARSED_CONTEXT)) {
      throw new SQLException("Branch " + session.getSessionId()
          + " is active and this statement can not be parsed, so it can not be redirected to the"
          + " branch tables. It was not run, to keep it from changing production:\n" + sql, "0A000",
          e);
    }
    StarlakeConnection.LOGGER.log(Level.WARNING,
        "Statement can not be parsed, run as is on branch " + session.getSessionId() + ":\n" + sql);
    return readOnly;
  }

  /**
   * A statement prepared or batched on a branch.
   */
  static final class BranchSql {
    /** The parsed statement, null when it could not be parsed. */
    final net.sf.jsqlparser.statement.Statement parsed;
    /** The statement as sent by the client. */
    final String sql;
    /** The statement rewritten to read and write the branch tables. */
    final String rewrittenSql;
    /** Whether APPLY replays the statement: false for read-only statements. */
    final boolean replayed;

    BranchSql(net.sf.jsqlparser.statement.Statement parsed, String sql, String rewrittenSql,
        boolean replayed) {
      this.parsed = parsed;
      this.sql = sql;
      this.rewrittenSql = rewrittenSql;
      this.replayed = replayed;
    }
  }

  /**
   * Rewrite a statement prepared or batched on an active branch. BRANCH commands and schema
   * statements (CREATE or DROP SCHEMA, USE, SET SCHEMA) are refused: the branch plays some of them
   * without running them, which only {@link #execute(String)} and its variants do.
   */
  static BranchSql toBranchSql(StarlakeConnection connection, StarlakeSession session, String sql)
      throws SQLException {
    final net.sf.jsqlparser.statement.Statement st;
    try {
      String unpipedSql = JSQLTranspiler.unpipe(sql, connection.executor, p -> {
      });
      st = CCJSqlParserUtil.parse(unpipedSql, connection.executor, p -> {
      });
    } catch (JSQLParserException e) {
      return new BranchSql(null, sql, sql, !checkUnparsed(session, sql, e));
    }

    if (st instanceof SessionStatement || st instanceof CreateSchema || st instanceof UseStatement
        || st instanceof SetStatement
            && "SCHEMA".equalsIgnoreCase(String.valueOf(((SetStatement) st).getName()))
        || st instanceof Drop && "SCHEMA".equalsIgnoreCase(((Drop) st).getType())) {
      throw new SQLException("On branch " + session.getSessionId()
          + ", BRANCH commands and schema statements can not be prepared or batched: run them with"
          + " Statement.execute:\n" + sql, "0A000");
    }

    return new BranchSql(st, sql, session.rewriteSql(st, sql), !StarlakeSession.isReadOnly(st));
  }

  private static boolean startsWithAny(String keywords, List<String> prefixes) {
    for (String prefix : prefixes) {
      if (keywords.equals(prefix) || keywords.startsWith(prefix + " ")) {
        return true;
      }
    }
    return false;
  }

  /**
   * The first two words of a statement, upper cased, after any leading comments and whitespace.
   */
  static String leadingKeywords(String sql) {
    int i = 0;
    final int n = sql.length();
    while (i < n) {
      if (Character.isWhitespace(sql.charAt(i))) {
        i++;
      } else if (sql.startsWith("--", i)) {
        int end = sql.indexOf('\n', i);
        i = end < 0 ? n : end + 1;
      } else if (sql.startsWith("/*", i)) {
        int end = sql.indexOf("*/", i + 2);
        i = end < 0 ? n : end + 2;
      } else {
        break;
      }
    }
    String[] words = sql.substring(i).trim().split("[\\s;(]+", 3);
    StringBuilder sb = new StringBuilder();
    for (int w = 0; w < Math.min(2, words.length); w++) {
      if (!words[w].isEmpty()) {
        if (sb.length() > 0) {
          sb.append(' ');
        }
        sb.append(words[w].toUpperCase(java.util.Locale.ROOT));
      }
    }
    return sb.toString();
  }

  // Extract metadata update logic to separate method
  void updateMetadataIfNeeded(net.sf.jsqlparser.statement.Statement st) {
    try {
      if (st instanceof CreateTable) {
        Table t = new Table(((CreateTable) st).getTable().getFullyQualifiedName());
        connection.currentSession.updateTable(connection.wrapped, t);
      } else if (st instanceof CreateView) {
        // a view the next statements read from, such as the SL_INCOMING view of a merge
        Table t = new Table(((CreateView) st).getView().getFullyQualifiedName());
        connection.currentSession.updateTable(connection.wrapped, t);
      } else if (st instanceof Alter) {
        Table t = new Table(((Alter) st).getTable().getFullyQualifiedName());
        connection.currentSession.updateTable(connection.wrapped, t);
      } else if (st instanceof Drop) {
        Drop drop = (Drop) st;
        if ("TABLE".equalsIgnoreCase(drop.getType())) {
          Table t = new Table(drop.getName().getFullyQualifiedName());
          connection.currentSession.metaData.dropTable(
              t.setUnsetCatalogAndSchema(connection.getCatalog(), connection.getSchema()));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int executeUpdate(String sql) throws SQLException {
    return executeUpdateInternal(sql, wrapped::executeUpdate, wrapped::executeUpdate);
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

  @FunctionalInterface
  private interface BooleanExecutor {
    boolean execute(String sql) throws SQLException;
  }

  // Generic method for execute operations
  private boolean executeInternal(String sql, BooleanExecutor wrappedExecutor,
      BooleanExecutor fallbackExecutor) throws SQLException {

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
          Boolean isSchema = st.accept(objectsVisitor, connection.getMetaData());
          if (isSchema == null || !isSchema) {
            boolean result = wrappedExecutor.execute(rewrittenSql);
            connection.currentSession.recordStatement(st, sql);
            // Update metadata when tables have been changed
            updateMetadataIfNeeded(st);
            return result;
          } else {
            StarlakeSession.LOGGER.warning("Ignore schema DML statements.");
            connection.currentSession.recordStatement(st, sql);
            return false;
          }
        } else {
          return wrappedExecutor.execute(unpipedSql);
        }
      }
    } catch (JSQLParserException e) {
      return executeUnparsed(sql, e, fallbackExecutor::execute);
    }
  }

  @Override
  public boolean execute(String sql) throws SQLException {
    return executeInternal(sql, wrapped::execute, wrapped::execute);
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
    final StarlakeSession session = connection.activeSession();
    if (session == null) {
      wrapped.addBatch(sql);
      addPending(null, null, null);
    } else {
      final BranchSql branchSql = toBranchSql(connection, session, sql);
      wrapped.addBatch(branchSql.rewrittenSql);
      addPending(session, branchSql.parsed, branchSql.replayed ? sql : null);
    }
  }

  /**
   * A command of the current batch, kept to record it for APPLY once the batch has run.
   */
  private static final class Pending {
    final StarlakeSession session;
    final net.sf.jsqlparser.statement.Statement parsed;
    /** The statement APPLY replays, null when it is not replayed. */
    final String replayedSql;

    Pending(StarlakeSession session, net.sf.jsqlparser.statement.Statement parsed,
        String replayedSql) {
      this.session = session;
      this.parsed = parsed;
      this.replayedSql = replayedSql;
    }
  }

  /** The commands of the current batch, in the order of the wrapped batch. */
  private final List<Pending> batch = new ArrayList<>();

  /** Add a command to the current batch, after it was added to the wrapped batch. */
  void addPending(StarlakeSession session, net.sf.jsqlparser.statement.Statement parsed,
      String replayedSql) {
    batch.add(new Pending(session, parsed, replayedSql));
  }

  /**
   * Record the batch commands that ran on a branch. A command counts as run when the driver did not
   * report it as failed.
   */
  private void recordBatch(int count, java.util.function.IntPredicate failed) throws SQLException {
    try {
      for (int i = 0; i < Math.min(count, batch.size()); i++) {
        Pending pending = batch.get(i);
        if (pending.session == null || failed.test(i)) {
          continue;
        }
        if (pending.replayedSql != null) {
          pending.session.recordStatement(pending.parsed, pending.replayedSql);
        }
        if (pending.parsed != null && pending.session == connection.currentSession) {
          updateMetadataIfNeeded(pending.parsed);
        }
      }
    } finally {
      batch.clear();
    }
  }

  @Override
  public void clearBatch() throws SQLException {
    wrapped.clearBatch();
    batch.clear();
  }

  @Override
  public int[] executeBatch() throws SQLException {
    final int[] counts;
    try {
      counts = wrapped.executeBatch();
    } catch (BatchUpdateException e) {
      final int[] done = e.getUpdateCounts();
      recordBatch(done == null ? 0 : done.length, i -> done[i] == EXECUTE_FAILED);
      throw e;
    }
    recordBatch(counts.length, i -> counts[i] == EXECUTE_FAILED);
    return counts;
  }

  @Override
  public Connection getConnection() {
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
    return executeUpdateInternal(sql, s -> wrapped.executeUpdate(s, autoGeneratedKeys),
        s -> wrapped.executeUpdate(s, autoGeneratedKeys));
  }

  @Override
  public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
    return executeUpdateInternal(sql, s -> wrapped.executeUpdate(s, columnIndexes),
        s -> wrapped.executeUpdate(s, columnIndexes));
  }

  @Override
  public int executeUpdate(String sql, String[] columnNames) throws SQLException {
    return executeUpdateInternal(sql, s -> wrapped.executeUpdate(s, columnNames),
        s -> wrapped.executeUpdate(s, columnNames));
  }

  @Override
  public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
    return executeInternal(sql, s -> wrapped.execute(s, autoGeneratedKeys),
        s -> wrapped.execute(s, autoGeneratedKeys));
  }

  @Override
  public boolean execute(String sql, int[] columnIndexes) throws SQLException {
    return executeInternal(sql, s -> wrapped.execute(s, columnIndexes),
        s -> wrapped.execute(s, columnIndexes));
  }

  @Override
  public boolean execute(String sql, String[] columnNames) throws SQLException {
    return executeInternal(sql, s -> wrapped.execute(s, columnNames),
        s -> wrapped.execute(s, columnNames));
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
    final long[] counts;
    try {
      counts = wrapped.executeLargeBatch();
    } catch (BatchUpdateException e) {
      final long[] done = e.getLargeUpdateCounts();
      recordBatch(done == null ? 0 : done.length, i -> done[i] == EXECUTE_FAILED);
      throw e;
    }
    recordBatch(counts.length, i -> counts[i] == EXECUTE_FAILED);
    return counts;
  }

  @Override
  public long executeLargeUpdate(String sql) throws SQLException {
    return executeUpdateInternal(sql, wrapped::executeLargeUpdate, wrapped::executeLargeUpdate);
  }

  @Override
  public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
    return executeUpdateInternal(sql, s -> wrapped.executeLargeUpdate(s, autoGeneratedKeys),
        s -> wrapped.executeLargeUpdate(s, autoGeneratedKeys));
  }

  @Override
  public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
    return executeUpdateInternal(sql, s -> wrapped.executeLargeUpdate(s, columnIndexes),
        s -> wrapped.executeLargeUpdate(s, columnIndexes));
  }

  @Override
  public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
    return executeUpdateInternal(sql, s -> wrapped.executeLargeUpdate(s, columnNames),
        s -> wrapped.executeLargeUpdate(s, columnNames));
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
