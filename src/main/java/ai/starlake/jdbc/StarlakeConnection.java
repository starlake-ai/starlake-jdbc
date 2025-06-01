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
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class StarlakeConnection implements Connection {
  private final Logger LOGGER = Logger.getLogger(StarlakeConnection.class.getName());
  private final Connection wrapped;

  public StarlakeSession currentSession = null;
  final CaseInsensitiveLinkedHashMap<StarlakeSession> sessions =
      new CaseInsensitiveLinkedHashMap<>();

  public StarlakeConnection(Connection wrapped) {
    this.wrapped = wrapped;
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
          startSession();
        } else {
          startSession(st.getId());
        }
        break;
      case APPLY:
        if (st.getId() == null) {
          applySession();
        } else {
          applySession(st.getId());
        }
        break;
      case DROP:
        if (st.getId() == null) {
          dropSession();
        } else {
          dropSession(st.getId());
        }
        break;
      case SHOW:
        if (st.getId() == null) {
          showSession();
        } else {
          showSession(st.getId());
        }
        break;
      case DESCRIBE:
        describeSession();
        break;
    }
    return false;
  }

  public void startSession() throws SQLException {
    if (currentSession != null && currentSession.getStatus() == Status.STARTED) {
      throw new RuntimeException("Session in progress: " + currentSession.getSessionId());
    } else {
      currentSession = new StarlakeSession(this);
      sessions.put(currentSession.getSessionId(), currentSession);
      LOGGER.info("Session started: " + currentSession.getSessionId());
    }
  }

  public void startSession(String id) throws SQLException {
    if (currentSession != null && currentSession.getStatus() == Status.STARTED) {
      throw new RuntimeException("Session in progress: " + currentSession.getSessionId());
    } else {
      currentSession = new StarlakeSession(this, id);
      sessions.put(id, currentSession);
      LOGGER.info("Session started: " + currentSession.getSessionId());
    }
  }

  public void applySession() {
    if (currentSession == null) {
      throw new RuntimeException("No current Session in progress.");
    } else if (currentSession.getStatus() != Status.STARTED) {
      throw new RuntimeException("Session not in progress: " + currentSession.getSessionId()
          + " -> " + currentSession.getStatus());
    } else {
      currentSession.apply();
      LOGGER.info("Session applied: " + currentSession.getSessionId());
    }
  }

  public void applySession(String id) {
    if (!sessions.containsKey(id)) {
      throw new RuntimeException("Session not found: " + id);
    } else {
      currentSession = sessions.get(id);
    }

    if (currentSession.getStatus() != Status.STARTED) {
      throw new RuntimeException("Session not in progress: " + currentSession.getSessionId()
          + " -> " + currentSession.getStatus());
    } else {
      currentSession.apply();
      LOGGER.info("Session applied: " + currentSession.getSessionId());
    }
  }

  public void dropSession() {
    if (currentSession == null) {
      throw new RuntimeException("No current Session in progress.");
    } else if (currentSession.getStatus() != Status.STARTED) {
      throw new RuntimeException("No current Session in progress: " + currentSession.getSessionId()
          + " -> " + currentSession.getStatus());
    } else {
      currentSession.drop();
      LOGGER.info("Session dropped: " + currentSession.getSessionId());
    }
  }

  public void dropSession(String id) {
    if (!sessions.containsKey(id)) {
      throw new RuntimeException("Session not found: " + id);
    } else {
      currentSession = sessions.get(id);
    }

    if (currentSession.getStatus() != Status.STARTED) {
      throw new RuntimeException("Session not in progress: " + currentSession.getSessionId()
          + " -> " + currentSession.getStatus());
    } else {
      currentSession.drop();
      LOGGER.info("Session dropped: " + currentSession.getSessionId());
    }
  }

  public void showSession() {
    if (currentSession == null) {
      throw new RuntimeException("No current Session in progress.");
    } else {
      LOGGER.info("Current Session: " + currentSession.getSessionId() + " -> "
          + currentSession.getStatus());
    }
  }

  public void showSession(String id) {
    if (!sessions.containsKey(id)) {
      throw new RuntimeException("Session not found: " + id);
    } else {
      StarlakeSession session = sessions.get(id);
      LOGGER.info("Session: " + session.getSessionId() + " -> " + session.getStatus());
    }
  }

  public void describeSession() {
    if (sessions.isEmpty()) {
      LOGGER.warning("Sessions are empty.");
    } else {
      for (StarlakeSession session : sessions.values()) {
        LOGGER.info("Session: " + session.getSessionId() + " -> " + session.getStatus());
      }
    }
  }
}
