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

import ai.starlake.jdbc.StarlakeSession.Status;
import ai.starlake.transpiler.schema.CaseInsensitiveLinkedHashMap;
import net.sf.jsqlparser.statement.SessionStatement;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.sql.Struct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StarlakeConnection implements Connection {
  public final static Logger LOGGER = Logger.getLogger(StarlakeConnection.class.getName());
  protected final Connection wrapped;
  protected final ExecutorService executor = Executors.newCachedThreadPool();

  protected enum DatabaseType {
    MYSQL, MARIADB, POSTGRES, ORACLE, SQLSERVER, SQLITE, H2, DERBY, DB2, FIREBIRD, HSQL, INFORMIX, TERADATA, SAPDB, BIGQUERY, SNOWFLAKE, REDSHIFT, DATABRICKS, UNKNOWN
  }

  protected final DatabaseType type;

  protected DatabaseType getType() {
    return type;
  }

  private static DatabaseType resolveDatabaseType(Connection wrapped) {
    String productName = "";
    String url = "";
    try {
      DatabaseMetaData metaData = wrapped.getMetaData();
      productName = metaData.getDatabaseProductName().toLowerCase();
      url = metaData.getURL() != null ? metaData.getURL().toLowerCase() : "";

      // Match by product name
      if (productName.contains("mysql")) {
        return DatabaseType.MYSQL;
      }
      if (productName.contains("mariadb")) {
        return DatabaseType.MARIADB;
      }
      if (productName.contains("postgresql")) {
        return DatabaseType.POSTGRES;
      }
      if (productName.contains("oracle")) {
        return DatabaseType.ORACLE;
      }
      if (productName.contains("sql server")) {
        return DatabaseType.SQLSERVER;
      }
      if (productName.contains("sqlite")) {
        return DatabaseType.SQLITE;
      }
      if (productName.contains("h2")) {
        return DatabaseType.H2;
      }
      if (productName.contains("derby")) {
        return DatabaseType.DERBY;
      }
      if (productName.contains("db2")) {
        return DatabaseType.DB2;
      }
      if (productName.contains("firebird")) {
        return DatabaseType.FIREBIRD;
      }
      if (productName.contains("hsql")) {
        return DatabaseType.HSQL;
      }
      if (productName.contains("informix")) {
        return DatabaseType.INFORMIX;
      }
      if (productName.contains("teradata")) {
        return DatabaseType.TERADATA;
      }
      if (productName.contains("sap")) {
        return DatabaseType.SAPDB;
      }

      // Cloud warehouses often identified better via URL
      if (url.contains("bigquery")) {
        return DatabaseType.BIGQUERY;
      }
      if (url.contains("snowflake")) {
        return DatabaseType.SNOWFLAKE;
      }
      if (url.contains("redshift")) {
        return DatabaseType.REDSHIFT;
      }
      if (url.contains("databricks")) {
        return DatabaseType.DATABRICKS;
      }
    } catch (SQLException e) {
      LOGGER.log(Level.WARNING, "Failed to determine the RDBMS " + productName + " " + url);
    }
    return DatabaseType.UNKNOWN;
  }

  public final String getClobType() {
    switch (type) {
      case MYSQL:
      case MARIADB:
        return "LONGTEXT";
      case POSTGRES:
      case SQLITE:
      case INFORMIX:
        return "TEXT";
      case ORACLE:
      case H2:
      case DERBY:
      case DB2:
      case HSQL:
      case TERADATA:
        return "CLOB";
      case SQLSERVER:
        return "VARCHAR(MAX)";
      case FIREBIRD:
        return "BLOB SUB_TYPE TEXT"; // Firebird uses BLOB with text subtype
      case SAPDB:
        return "LONG VARCHAR"; // SAP DB (a.k.a. MaxDB) uses LONG VARCHAR for large text
      case BIGQUERY:
        return "STRING"; // up to 10 MB
      case SNOWFLAKE:
        return "STRING"; // or TEXT (alias), up to 16 MB
      case REDSHIFT:
        return "VARCHAR(MAX)"; // up to 65535 bytes
      case DATABRICKS:
        return "STRING"; // up to 2 GB
      default:
        throw new UnsupportedOperationException("Unsupported DatabaseType: " + type);
    }
  }

  public final String getCloneTableStatement(String tableName, String sourceName) {
    switch (type) {
      case SNOWFLAKE:
        // Snowflake supports true zero-copy cloning
        return String.format("CREATE TABLE %s CLONE %s;", tableName, sourceName);

      case DATABRICKS:
        // Databricks Delta supports shallow clone
        return String.format("CREATE TABLE %s SHALLOW CLONE %s;", tableName, sourceName);

      case BIGQUERY:
        // BigQuery supports clone via copy (as of 2022+)
        // Uses CREATE SNAPSHOT TABLE for logical clone
        return String.format("CREATE SNAPSHOT TABLE %s CLONE %s;", tableName, sourceName);

      default:
        return String.format("CREATE TABLE %s AS SELECT * FROM %s;", tableName, sourceName);
    }
  }


  public StarlakeSession currentSession = null;
  final CaseInsensitiveLinkedHashMap<StarlakeSession> sessions =
      new CaseInsensitiveLinkedHashMap<>();

  public StarlakeConnection(Connection wrapped) {
    if (wrapped == null) {
      throw new IllegalArgumentException("Connection cannot be null");
    }
    this.wrapped = wrapped;
    this.type = resolveDatabaseType(wrapped);
  }

  @Override
  public Statement createStatement() throws SQLException {
    return new StarlakeStatement(this, wrapped.createStatement());
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException {
    return new StarlakePreparedStatement(this, wrapped.prepareStatement(sql));
  }

  @Override
  public CallableStatement prepareCall(String sql) throws SQLException {
    return new StarlakeCallableStatement(this, wrapped.prepareCall(sql));
  }

  @Override
  public String nativeSQL(String sql) throws SQLException {
    return wrapped.nativeSQL(sql);
  }

  @Override
  public void setAutoCommit(boolean autoCommit) throws SQLException {
    wrapped.setAutoCommit(autoCommit);
  }

  @Override
  public boolean getAutoCommit() throws SQLException {
    return wrapped.getAutoCommit();
  }

  @Override
  public void commit() throws SQLException {
    wrapped.commit();
  }

  @Override
  public void rollback() throws SQLException {
    wrapped.rollback();
  }

  @Override
  public void close() throws SQLException {
    if (currentSession != null) {
      if (currentSession.getStatus() == Status.STARTED) {
        LOGGER.warning("Started current session " + currentSession.getSessionId()
            + " found when closing connection. Will DROP it.");
        currentSession.drop(currentSession.options);
      }
      sessions.remove(currentSession.getSessionId());
      currentSession = null;
    }

    executor.shutdown();
    try {
      executor.awaitTermination(1, TimeUnit.MINUTES);

    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    wrapped.close();
  }

  @Override
  public boolean isClosed() throws SQLException {
    return wrapped.isClosed();
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException {
    return new StarlakeDatabaseMetaData(wrapped.getMetaData());
  }

  @Override
  public void setReadOnly(boolean readOnly) throws SQLException {
    wrapped.setReadOnly(readOnly);
  }

  @Override
  public boolean isReadOnly() throws SQLException {
    return wrapped.isReadOnly();
  }

  @Override
  public void setCatalog(String catalog) throws SQLException {
    wrapped.setCatalog(catalog);
  }

  @Override
  public String getCatalog() throws SQLException {
    return wrapped.getCatalog();
  }

  @Override
  public void setTransactionIsolation(int level) throws SQLException {
    wrapped.setTransactionIsolation(level);
  }

  @Override
  public int getTransactionIsolation() throws SQLException {
    return wrapped.getTransactionIsolation();
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
  public Statement createStatement(int resultSetType, int resultSetConcurrency)
      throws SQLException {
    return new StarlakeStatement(this,
        wrapped.createStatement(resultSetType, resultSetConcurrency));
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    return new StarlakePreparedStatement(this,
        wrapped.prepareStatement(sql, resultSetType, resultSetConcurrency));
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    return new StarlakeCallableStatement(this,
        wrapped.prepareCall(sql, resultSetType, resultSetConcurrency));
  }

  @Override
  public Map<String, Class<?>> getTypeMap() throws SQLException {
    return wrapped.getTypeMap();
  }

  @Override
  public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
    wrapped.setTypeMap(map);
  }

  @Override
  public void setHoldability(int holdability) throws SQLException {
    wrapped.setHoldability(holdability);
  }

  @Override
  public int getHoldability() throws SQLException {
    return wrapped.getHoldability();
  }

  @Override
  public Savepoint setSavepoint() throws SQLException {
    return wrapped.setSavepoint();
  }

  @Override
  public Savepoint setSavepoint(String name) throws SQLException {
    return wrapped.setSavepoint(name);
  }

  @Override
  public void rollback(Savepoint savepoint) throws SQLException {
    wrapped.rollback(savepoint);
  }

  @Override
  public void releaseSavepoint(Savepoint savepoint) throws SQLException {
    wrapped.releaseSavepoint(savepoint);
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency,
      int resultSetHoldability) throws SQLException {
    return new StarlakeStatement(this,
        wrapped.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
      int resultSetHoldability) throws SQLException {
    return new StarlakePreparedStatement(this,
        wrapped.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
      int resultSetHoldability) throws SQLException {
    return new StarlakeCallableStatement(this,
        wrapped.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
    return new StarlakePreparedStatement(this, wrapped.prepareStatement(sql, autoGeneratedKeys));
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
    return new StarlakePreparedStatement(this, wrapped.prepareStatement(sql, columnIndexes));
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
    return new StarlakePreparedStatement(this, wrapped.prepareStatement(sql, columnNames));
  }

  @Override
  public Clob createClob() throws SQLException {
    return wrapped.createClob();
  }

  @Override
  public Blob createBlob() throws SQLException {
    return wrapped.createBlob();
  }

  @Override
  public NClob createNClob() throws SQLException {
    return wrapped.createNClob();
  }

  @Override
  public SQLXML createSQLXML() throws SQLException {
    return wrapped.createSQLXML();
  }

  @Override
  public boolean isValid(int timeout) throws SQLException {
    return wrapped.isValid(timeout);
  }

  @Override
  public void setClientInfo(String name, String value) throws SQLClientInfoException {
    wrapped.setClientInfo(name, value);
  }

  @Override
  public void setClientInfo(Properties properties) throws SQLClientInfoException {
    wrapped.setClientInfo(properties);
  }

  @Override
  public String getClientInfo(String name) throws SQLException {
    return wrapped.getClientInfo(name);
  }

  @Override
  public Properties getClientInfo() throws SQLException {
    return wrapped.getClientInfo();
  }

  @Override
  public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    return wrapped.createArrayOf(typeName, elements);
  }

  @Override
  public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
    return wrapped.createStruct(typeName, attributes);
  }

  @Override
  public void setSchema(String schema) throws SQLException {
    wrapped.setSchema(schema);
  }

  @Override
  public String getSchema() throws SQLException {
    return wrapped.getSchema();
  }

  @Override
  public void abort(Executor executor) throws SQLException {
    wrapped.abort(executor);
  }

  @Override
  public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
    wrapped.setNetworkTimeout(executor, milliseconds);
  }

  @Override
  public int getNetworkTimeout() throws SQLException {
    return wrapped.getNetworkTimeout();
  }

  @Override
  public void beginRequest() throws SQLException {
    wrapped.beginRequest();
  }

  @Override
  public void endRequest() throws SQLException {
    wrapped.endRequest();
  }

  @Override
  public boolean setShardingKeyIfValid(ShardingKey shardingKey, ShardingKey superShardingKey,
      int timeout) throws SQLException {
    return wrapped.setShardingKeyIfValid(shardingKey, superShardingKey, timeout);
  }

  @Override
  public boolean setShardingKeyIfValid(ShardingKey shardingKey, int timeout) throws SQLException {
    return wrapped.setShardingKeyIfValid(shardingKey, timeout);
  }

  @Override
  public void setShardingKey(ShardingKey shardingKey, ShardingKey superShardingKey)
      throws SQLException {
    wrapped.setShardingKey(shardingKey, superShardingKey);
  }

  @Override
  public void setShardingKey(ShardingKey shardingKey) throws SQLException {
    wrapped.setShardingKey(shardingKey);
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return wrapped.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return wrapped.isWrapperFor(iface);
  }

  public boolean execute(SessionStatement st) throws SQLException {
    LOGGER.info(st.toString());
    switch (st.getAction()) {
      case START:
        if (st.getId() == null) {
          startSession(st.getOptions());
        } else {
          startSession(st.getId(), st.getOptions());
        }
        break;
      case APPLY:
        if (st.getId() == null) {
          applySession(st.getOptions());
        } else {
          applySession(st.getId(), st.getOptions());
        }
        break;
      case DROP:
        if (st.getId() == null) {
          dropSession(st.getOptions());
        } else {
          dropSession(st.getId(), st.getOptions());
        }
        break;
      case SHOW:
        if (st.getId() == null) {
          showSession(st.getOptions());
        } else {
          showSession(st.getId(), st.getOptions());
        }
        break;
      case DESCRIBE:
        describeSession(st.getOptions());
        break;
    }
    return false;
  }

  public String getSessionCommand(SessionStatement st) {
    String id = st.getId() == null ? currentSession.getSessionId() : st.getId();
    return st.getAction().toString() + " " + id;
  }

  public ResultSet executeQuery(SessionStatement st) throws SQLException {
    LOGGER.info(st.toString());
    ArrayList<Object[]> data = new ArrayList<>();
    switch (st.getAction()) {
      case START:
        if (st.getId() == null) {
          startSession(st.getOptions());
        } else {
          startSession(st.getId(), st.getOptions());
        }
        return new StarlakeVirtualResultSet("SESSION_COMMAND", getSessionCommand(st));
      case APPLY:
        if (st.getId() == null) {
          applySession(st.getOptions());
        } else {
          applySession(st.getId(), st.getOptions());
        }
        return new StarlakeVirtualResultSet("SESSION_COMMAND", getSessionCommand(st));
      case DROP:
        if (st.getId() == null) {
          dropSession(st.getOptions());
        } else {
          dropSession(st.getId(), st.getOptions());
        }
        return new StarlakeVirtualResultSet("SESSION_COMMAND", getSessionCommand(st));
      case SHOW:
        if (st.getId() == null) {
          showSession(st.getOptions());
        } else {
          showSession(st.getId(), st.getOptions());
        }

        if (currentSession == null) {
          data.add(new Object[] {"status", "Does not exist"});
        } else {

          data.add(new Object[] {"id", currentSession.getSessionId()});
          data.add(new Object[] {"status", currentSession.getStatus().toString()});
          if (currentSession.getStartInstant() != null) {
            data.add(new Object[] {"start_timestamp", currentSession.getStartInstant().toString()});
          }
          if (currentSession.getStartInstant() != null) {
            data.add(new Object[] {"end_timestamp", currentSession.getStartInstant()});
          }
          for (Map.Entry<String, String> e : currentSession.options.entrySet()) {
            data.add(new Object[] {e.getKey(), e.getValue()});
          }
        }
        return new StarlakeVirtualResultSet(new String[] {"KEY", "VALUE"},
            new Class[] {String.class, String.class}, data.toArray(new Object[data.size()][]));
      case DESCRIBE:
        describeSession(st.getOptions());

        for (Map.Entry<Instant, String> e : currentSession.getStatements().entrySet()) {
          data.add(new Object[] {e.getKey(), e.getValue()});
        }
        return new StarlakeVirtualResultSet(new String[] {"UPDATE_TIMESTAMP", "SQL_TEXT"},
            new Class[] {Instant.class, String.class}, data.toArray(new Object[data.size()][]));
      default:
        throw new IllegalStateException("Unexpected value: " + st.getAction());
    }
  }

  public void startSession(Map<String, String> options) throws SQLException {
    if (currentSession != null && currentSession.getStatus() == Status.STARTED
        && !currentSession.isPersistent()) {
      throw new RuntimeException("Session in progress: " + currentSession.getSessionId());
    } else {
      currentSession = new StarlakeSession(this, options);
      sessions.put(currentSession.getSessionId(), currentSession);
      LOGGER.info("Session started: " + currentSession.getSessionId());
    }
  }

  public void startSession(String id, Map<String, String> options) throws SQLException {
    if (currentSession != null && currentSession.getStatus() == Status.STARTED
        && !currentSession.isPersistent()) {
      throw new RuntimeException("Session in progress: " + currentSession.getSessionId());
    } else {
      currentSession = StarlakeSession.getPersistingSession(this, id, options);
      if (currentSession != null) {
        sessions.put(id, currentSession);
        LOGGER.info("Persisting Session continued: " + currentSession.getSessionId());
      } else {
        currentSession = new StarlakeSession(this, id, options);
        sessions.put(id, currentSession);
        LOGGER.info("Session started: " + currentSession.getSessionId());
      }
    }
  }

  public void applySession(Map<String, String> options) {
    if (currentSession == null) {
      throw new RuntimeException("No current Session in progress.");
    } else if (currentSession.getStatus() != Status.STARTED) {
      throw new RuntimeException("Session not in progress: " + currentSession.getSessionId()
          + " -> " + currentSession.getStatus());
    } else {
      currentSession.apply(options);
      LOGGER.info("Session applied: " + currentSession.getSessionId());
    }
  }

  public void applySession(String id, Map<String, String> options) {
    if (!sessions.containsKey(id)) {
      throw new RuntimeException("Session not found: " + id);
    } else {
      currentSession = sessions.get(id);
    }

    if (currentSession.getStatus() != Status.STARTED) {
      throw new RuntimeException("Session not in progress: " + currentSession.getSessionId()
          + " -> " + currentSession.getStatus());
    } else {
      currentSession.apply(options);
      LOGGER.info("Session applied: " + currentSession.getSessionId());
    }
  }

  public void dropSession(Map<String, String> options) {
    if (currentSession == null) {
      throw new RuntimeException("No current Session in progress.");
    } else if (currentSession.getStatus() != Status.STARTED) {
      throw new RuntimeException("No current Session in progress: " + currentSession.getSessionId()
          + " -> " + currentSession.getStatus());
    } else {
      currentSession.drop(options);
      LOGGER.info("Session dropped: " + currentSession.getSessionId());
    }
  }

  public void dropSession(String id, Map<String, String> options) {
    if (!sessions.containsKey(id)) {
      if (StarlakeSession.isTrue(options, "persist")) {
        try {
          StarlakeSession session = StarlakeSession.getPersistingSession(this, id, options);
          if (session != null && session.getStatus() == Status.STARTED) {
            session.drop(options);
            LOGGER.info("Session dropped: " + currentSession.getSessionId());
          } else {
            LOGGER.warning("No running Session not found: " + id);
          }
        } catch (Exception ex) {
          LOGGER.warning("Session not found: " + id);
        }
        return;
      } else {
        throw new RuntimeException("Session not found: " + id);
      }
    } else {
      currentSession = sessions.get(id);
    }

    if (currentSession != null) {
      if (currentSession.getStatus() != Status.STARTED) {
        throw new RuntimeException("Session not in progress: " + currentSession.getSessionId()
            + " -> " + currentSession.getStatus());
      } else {
        currentSession.drop(options);
        LOGGER.info("Session dropped: " + currentSession.getSessionId());
      }
    }
  }

  public void showSession(Map<String, String> options) {
    if (currentSession == null) {
      LOGGER.warning("No current Session exists.");
    } else {
      LOGGER.info("Current Session: " + currentSession.getSessionId() + " -> "
          + currentSession.getStatus());
    }
  }

  public void showSession(String id, Map<String, String> options) {
    if (!sessions.containsKey(id)) {
      LOGGER.warning("Session does not exist: " + id);
    } else {
      StarlakeSession session = sessions.get(id);
      LOGGER.info("Session: " + session.getSessionId() + " -> " + session.getStatus());
    }
  }

  public void describeSession(Map<String, String> options) {
    if (sessions.isEmpty()) {
      LOGGER.warning("Sessions are empty.");
    } else {
      for (StarlakeSession session : sessions.values()) {
        LOGGER.info("Session: " + session.getSessionId() + " -> " + session.getStatus());
      }
    }
  }
}
