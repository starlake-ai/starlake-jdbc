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

import ai.starlake.transpiler.JSQLReplacer;
import ai.starlake.transpiler.schema.CaseInsensitiveLinkedHashMap;
import ai.starlake.transpiler.schema.JdbcCatalog;
import ai.starlake.transpiler.schema.JdbcMetaData;
import ai.starlake.transpiler.schema.JdbcSchema;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.MultiPartName;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.schema.CreateSchema;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.select.FromItemVisitorAdapter;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;

import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StarlakeSession {
  public static final String BASE62 =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  public final static Logger LOGGER = Logger.getLogger(StarlakeSession.class.getName());

  // rewrite the queries using the replacement tables
  JdbcMetaData metaData;

  CaseInsensitiveLinkedHashMap<String> options = new CaseInsensitiveLinkedHashMap<>();

  public enum Status {
    STARTED, APPLIED, DROPPED
  }

  /**
   * Options
   *
   * `persist` if session properties can be persisted in the schema
   *
   */

  private final StarlakeConnection conn;
  private final String sessionId;
  private Status status;
  private final Instant startInstant;
  private Instant endInstant;

  public final DBObjects dbObjects = new DBObjects();

  PreparedStatement historyStatement = null;
  PreparedStatement tableMappingDeleteStatement = null;
  PreparedStatement tableMappingInsertStatement = null;

  private final CaseInsensitiveLinkedHashMap<Table> replacementTables =
      new CaseInsensitiveLinkedHashMap<>();
  private final TreeMap<Instant, String> statements = new TreeMap<>();

  private final ExpressionVisitor<Boolean> modifyingTableExpressionVisitor =
      new ExpressionVisitorAdapter<>();

  private final SelectVisitorAdapter<Boolean> modifyingTableSelectVisitor =
      new SelectVisitorAdapter<>(modifyingTableExpressionVisitor) {
        @Override
        public void visit(PlainSelect plainSelect) {
          // detect any INSERT INTO tables
          modifyingTableVisitor.visitFromItem(plainSelect.getIntoTempTable(), null);
          modifyingTableVisitor.visitTables(plainSelect.getIntoTables(), null);

          super.visit(plainSelect);
        }
      };

  // detects tables to be modified and add those to the Replacement Tables
  private final FromItemVisitorAdapter<Boolean> modifyingTableVisitor =
      new FromItemVisitorAdapter<>(modifyingTableSelectVisitor, modifyingTableExpressionVisitor) {
        @Override
        public <S> Boolean visit(Table table, S context) {
          String originalName = table.setUnsetCatalogAndSchema(metaData.getCurrentCatalogName(), metaData.getCurrentSchemaName()).getFullyQualifiedName();
          if (!replacementTables.containsKey(originalName)) {
            Table replacementTable =
                new Table(table.withSchemaName(sessionId).getFullyQualifiedName());

            // keep appending suffices until we find a free name
            String tableName = replacementTable.getName();
            int i = 1;
            while (metaData.getTable(replacementTable) != null) {
              table.setName(tableName + "_" + i);
              replacementTable.setName(tableName + "_" + i++);
            }

            if (!(context instanceof CreateTable)) {
              // create table as select
              try (java.sql.Statement st = conn.wrapped.createStatement()) {
                String sql = conn.getCloneTableStatement(replacementTable.getFullyQualifiedName(),
                    originalName);
                st.executeUpdate(sql);
                metaData.updateTable(conn, replacementTable);
              } catch (Exception ex) {
                throw new RuntimeException("Failed to create a copy of " + originalName, ex);
              }

            }

            replacementTables.put(originalName, replacementTable);

            if (isPersistent() && tableMappingInsertStatement != null) {
              try {
                tableMappingDeleteStatement.setString(1, originalName);
                tableMappingDeleteStatement.executeUpdate();

                tableMappingInsertStatement.setString(1, originalName);
                tableMappingInsertStatement.setString(2, replacementTable.getFullyQualifiedName());
                tableMappingInsertStatement.executeUpdate();
              } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to persist the table mapping: " + originalName
                    + " --> " + replacementTable, ex);
              }
            }
            return true;
          } else {
            return false;
          }
        }
      };

  private final StatementVisitorAdapter<Void> statementVisitor = new StatementVisitorAdapter<>() {
    @Override
    public <S> Void visit(CreateTable createTable, S context) {
      createTable.getTable().accept(modifyingTableVisitor, context);
      // check if table does not exist yet existing schema
      return super.visit(createTable, context);
    }

    @Override
    public <S> Void visit(CreateSchema createSchema, S context) {
      String schemaName = createSchema.getSchemaName();
      String catalogName = metaData.getCurrentCatalogName();

      if (MultiPartName.isQuoted(schemaName)) {
        schemaName = MultiPartName.unquote(schemaName);
      }

      String[] nameParts = schemaName.split("\\.");
      if (nameParts.length == 2) {
        catalogName = nameParts[0];
        schemaName = nameParts[1];
      }

      JdbcCatalog catalog = metaData.get(catalogName);
      if (catalog != null && !catalog.containsKey(schemaName)) {
        catalog.put(new JdbcSchema(schemaName, catalogName));
      }

      // check if table does not exist yet existing schema
      return super.visit(createSchema, context);
    }

    public <S> Void visit(CreateIndex index, S context) {
      modifyingTableVisitor.visitFromItem(index.getTable(), context);

      return super.visit(index, context);
    }

    @Override
    public <S> Void visit(Delete delete, S context) {
      modifyingTableVisitor.visitFromItem(delete.getTable(), context);

      return super.visit(delete, context);
    }

    @Override
    public <S> Void visit(Update update, S context) {
      modifyingTableVisitor.visitFromItem(update.getTable(), context);

      return super.visit(update, context);
    }

    @Override
    public <S> Void visit(Insert insert, S context) {
      modifyingTableVisitor.visitFromItem(insert.getTable(), context);

      return super.visit(insert, context);
    }

    @Override
    public <S> Void visit(Drop drop, S context) {
      if (drop.getType().equalsIgnoreCase("table")) {
        modifyingTableVisitor.visitFromItem(drop.getName(), context);
      } else if (drop.getType().equalsIgnoreCase("schema")) {
        String schemaName = drop.getName().getFullyQualifiedName();
        String catalogName = metaData.getCurrentCatalogName();

        if (MultiPartName.isQuoted(schemaName)) {
          schemaName = MultiPartName.unquote(schemaName);
        }

        String[] nameParts = schemaName.split("\\.");
        if (nameParts.length == 2) {
          catalogName = nameParts[0];
          schemaName = nameParts[1];
        }

        JdbcCatalog catalog = metaData.get(catalogName);
        if (catalog != null && !catalog.containsKey(schemaName)) {
          catalog.remove(schemaName);
        }
      }

      return super.visit(drop, context);
    }

    @Override
    public <S> Void visit(Truncate truncate, S context) {
      modifyingTableVisitor.visitFromItem(truncate.getTable(), context);

      return super.visit(truncate, context);
    }

    @Override
    public <S> Void visit(Merge merge, S context) {
      modifyingTableVisitor.visitFromItem(merge.getTable(), context);

      return super.visit(merge, context);
    }

    @Override
    public <S> Void visit(Alter alter, S context) {
      modifyingTableVisitor.visitFromItem(alter.getTable(), context);
      // rewrite the queries using the replacement tables
      return super.visit(alter, context);
    }
  };

  /**
   * Extracts the values of "db" and "schema" from a JDBC URL. Both values are optional and may be
   * null.
   *
   * @param jdbcUrl The JDBC connection URL.
   * @return A map with keys "db" and "schema", values may be null if not present.
   */
  public static Map<String, String> connectionProperties(String jdbcUrl) {
    Map<String, String> result = new HashMap<>();
    result.put("db", null);
    result.put("schema", null);

    if (jdbcUrl == null || !jdbcUrl.contains("?")) {
      return result; // no query params at all
    }

    String queryPart = jdbcUrl.substring(jdbcUrl.indexOf("?") + 1);
    String[] params = queryPart.split("&");

    for (String param : params) {
      String[] keyValue = param.split("=", 2);
      if (keyValue.length == 2) {
        String key = keyValue[0].toLowerCase();
        String value = keyValue[1];
        if ("db".equals(key)) {
          result.put("db", value);
        } else if ("schema".equals(key)) {
          result.put("schema", value);
        }
      }
    }

    return result;
  }

  public boolean isPersistent() {
    return isTrue("persist");
  }

  public boolean isTrue(String key) {
    return isTrue(options, key);
  }

  public static boolean isTrue(Map<String, String> options, String key) {
    if (options == null) {
      return false;
    } else {
      String e = options.get(key);
      return e != null && List.of("y", "yes", "true", "1").contains(e);
    }
  }

  public boolean isFalse(String key) {
    return !isTrue(key);
  }

  public static boolean isFalse(Map<String, String> options, String key) {
    return !isTrue(options, key);
  }

  public String getMappingTable() {
    return getMappingTable(sessionId);
  }

  public static String getMappingTable(String sessionId) {
    return sessionId + ".sl_table_map";
  }

  public String getHistoryTable() {
    return getHistoryTable(sessionId);
  }

  public static String getHistoryTable(String sessionId) {
    return sessionId + ".sl_session_hst";
  }

  public static StarlakeSession getPersistingSession(StarlakeConnection conn, String id,
      Map<String, String> options) throws SQLException {
    boolean foundMappingTable = false;
    boolean foundHistoryTable = false;

    final TreeMap<Instant, String> statements = new TreeMap<>();
    final CaseInsensitiveLinkedHashMap<Table> replacementTables =
        new CaseInsensitiveLinkedHashMap<>();

    if (isTrue(options, "persist") || isTrue(options, "continue")) {
      try (java.sql.Statement statement = conn.wrapped.createStatement()) {
        try (ResultSet rs = statement.executeQuery("SELECT * FROM " + getMappingTable(id))) {
          while (rs.next()) {
            String fromTable = rs.getString(1);
            String toTable = rs.getString(2);
            replacementTables.put(fromTable, new Table(toTable));
          }
          foundMappingTable = true;

        } catch (Exception e) {
          LOGGER.warning("Did not find mapping table for session " + id);
        }
      }

      try (java.sql.Statement statement = conn.wrapped.createStatement()) {
        try (ResultSet rs = statement.executeQuery("SELECT * FROM " + getHistoryTable(id))) {
          while (rs.next()) {
            java.sql.Timestamp timestamp = rs.getTimestamp(1);
            String sqlText = rs.getString(2);

            statements.put(timestamp.toInstant(), sqlText);
          }
          foundHistoryTable = true;
        } catch (Exception e) {
          LOGGER.warning("Did not find history table for session " + id);
        }
      }

      if (foundMappingTable && foundHistoryTable) {
        StarlakeSession session = new StarlakeSession(conn, id, options);
        session.replacementTables.putAll(replacementTables);
        session.statements.putAll(statements);

        // Maintain the meta data for all persisting replacement tables
        for (String sqlStr : statements.values()) {
          try {
            Statement st = CCJSqlParserUtil.parse(sqlStr);
            if (st instanceof CreateSchema) {
              st.accept(session.statementVisitor, null);
            } else if (st instanceof Drop) {
              Drop drop = (Drop) st;
              if ("schema".equalsIgnoreCase(drop.getType())) {
                st.accept(session.statementVisitor, null);
              }
            }
          } catch (JSQLParserException ignore) {
            LOGGER.fine("Failed to parse:\n" + sqlStr);
          }
        }

        // Maintain the meta data for all persisting replacement tables
        for (Table replacementTable : replacementTables.values()) {
          session.metaData.updateTable(conn, replacementTable);
        }

        return session;
      }
    } else {
      LOGGER.warning("Did not find session " + id);
    }

    return null;
  }

  public StarlakeSession(StarlakeConnection conn, String id, Map<String, String> options)
      throws SQLException {
    this.options.clear();
    this.options.putAll(options);
    this.conn = conn;
    this.sessionId = id;
    this.status = Status.STARTED;
    this.startInstant = Instant.now();

    String currentCatalog = conn.getCatalog();
    String currentSchema = conn.getSchema();


    try (java.sql.Statement statement = conn.wrapped.createStatement()) {
      if (isPersistent()) {
        statement.executeUpdate("CREATE SCHEMA IF NOT EXISTS " + id);

        // use row based tables on snowflake
        String hybridStr = conn.type == StarlakeConnection.DatabaseType.SNOWFLAKE ? " HYBRID" : "";

        //@formatter:off
        final String SESSION_DDL_STR =
              "CREATE" + hybridStr + " TABLE IF NOT EXISTS " + getMappingTable() + " (\n" +
              "    from_table       VARCHAR (256)\n" +
              "    , to_table       VARCHAR (256)\n" +
              "    , primary key(from_table)\n" +
              ")\n" +
              ";\n" +
              "CREATE" + hybridStr + " TABLE IF NOT EXISTS " + getHistoryTable()  +" (\n" +
              "    update_timestamp         TIMESTAMP\n" +
              "    , sql_text               "+ conn.getClobType() +"\n" +
              "    , primary key (update_timestamp)\n" +
              ")\n" +
              ";";
        //@formatter:on

        try {
          for (Statement st : CCJSqlParserUtil.parseStatements(SESSION_DDL_STR)) {
            statement.execute(st.toString());
          }
        } catch (JSQLParserException parserException) {
          LOGGER.warning("Failed to create session persistence tables.");
          this.options.put("persist", "false");
        }

        historyStatement = conn.wrapped.prepareStatement(
            "INSERT INTO " + getHistoryTable() + " VALUES ( CURRENT_TIMESTAMP, ? )");

        tableMappingDeleteStatement = conn.wrapped
            .prepareStatement("DELETE FROM " + getMappingTable() + " WHERE from_table = ?");

        tableMappingInsertStatement =
            conn.wrapped.prepareStatement("INSERT INTO " + getMappingTable() + " VALUES ( ?, ? )");
      } else {
        statement.executeUpdate("CREATE SCHEMA " + id);
      }

      // Snowflake sets any CREATE SCHEMA as current schema
      if (List.of(StarlakeConnection.DatabaseType.SNOWFLAKE).contains(conn.type)) {
        try {
          statement.executeUpdate("USE SCHEMA " + currentCatalog + "." + currentSchema);
        } catch (Exception e) {
          LOGGER.warning("Failed to set current schema.");
        }
      }
    }
    this.metaData = new JdbcMetaData(conn.wrapped);
  }

  public static String generateUniqueID() {
    UUID uuid = UUID.randomUUID();
    BigInteger bigInt = new BigInteger(uuid.toString().replace("-", ""), 16);

    StringBuilder sb = new StringBuilder();
    while (bigInt.compareTo(BigInteger.ZERO) > 0) {
      BigInteger[] divRem = bigInt.divideAndRemainder(BigInteger.valueOf(62));
      sb.append(BASE62.charAt(divRem[1].intValue()));
      bigInt = divRem[0];
    }
    return sb.reverse().toString();
  }

  public StarlakeSession(StarlakeConnection conn, Map<String, String> options) throws SQLException {
    this(conn, generateUniqueID(), options);
  }

  public void drop(Map<String, String> options) {
    this.status = Status.DROPPED;
    this.endInstant = Instant.now();


    try (java.sql.Statement statement = conn.wrapped.createStatement()) {
      // @todo: skip when table has been dropped already by a DROP TABLE statement
      for (Table t : replacementTables.values()) {
        try {
          statement.executeUpdate("DROP TABLE IF EXISTS " + t.getFullyQualifiedName());
        } catch (SQLException e) {
          LOGGER.warning("Failed to drop " + t.getFullyQualifiedName());
        }
      }

      if (isPersistent() && tableMappingInsertStatement != null) {
        statement.executeUpdate("DROP TABLE IF EXISTS  " + getMappingTable());
      }

      if (isPersistent() && historyStatement != null) {
        statement.executeUpdate("DROP TABLE IF EXISTS  " + getHistoryTable());
      }

      statement.executeUpdate("DROP SCHEMA IF EXISTS " + sessionId);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Failed to drop schema when dropping session", e);
    }
  }

  public void apply(Map<String, String> options) {
    this.status = Status.APPLIED;
    this.endInstant = Instant.now();

    try (java.sql.Statement statement = conn.wrapped.createStatement()) {
      for (String sqlStr : statements.values()) {
        try {
          statement.execute(sqlStr);
        } catch (SQLException ex) {
          LOGGER.log(Level.SEVERE, "Failed apply statement from session:\n" + sqlStr, ex);
        }
      }

      if (isFalse(options, "keep")) {
        for (Table t : replacementTables.values()) {
          statement.executeUpdate("DROP TABLE " + t.getFullyQualifiedName());
        }
        if (isPersistent() && tableMappingInsertStatement != null) {
          statement.executeUpdate("DROP TABLE " + getMappingTable());
        }

        if (isPersistent() && historyStatement != null) {
          statement.executeUpdate("DROP TABLE " + getHistoryTable());
        }
        statement.executeUpdate("DROP SCHEMA " + sessionId);
      }
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Failed to drop schema after applying session", e);
    }
  }

  public String rewriteSql(Statement st, String sqlStr) throws SQLException {
    LOGGER.info("Rewrite:\n" + sqlStr);
    if (status == Status.STARTED) {
      statements.put(Instant.now(), sqlStr);

      if (isPersistent() && historyStatement != null) {
        historyStatement.setString(1, sqlStr);
        historyStatement.executeUpdate();
      }

      // maintain the replacement tables
      st.accept(statementVisitor, st);

      if (!(st instanceof CreateTable)) {
        for (Map.Entry<String, Table> e : replacementTables.entrySet()) {
          metaData.addSynonym(e.getKey(), e.getValue().setUnsetCatalogAndSchema(metaData.getCurrentCatalogName(), metaData.getCurrentSchemaName()).getFullyQualifiedName());
        }
      }

      if (st instanceof Drop) {
        for (Map.Entry<String, Table> e : replacementTables.entrySet()) {
          metaData.dropSynonym(e.getKey(), e.getValue().setUnsetCatalogAndSchema(metaData.getCurrentCatalogName(), metaData.getCurrentSchemaName()).getFullyQualifiedName());
        }
      }

      JSQLReplacer replacer = new JSQLReplacer(metaData);
      final Statement replace = replacer.replace(st, replacementTables);

      return replace.toString();
    } else {
      return sqlStr;
    }
  }

  public String getSessionId() {
    return sessionId;
  }

  public Status getStatus() {
    return status;
  }

  public StarlakeSession setStatus(Status status) {
    this.status = status;
    return this;
  }

  public Instant getStartInstant() {
    return startInstant;
  }

  public Instant getEndInstant() {
    return endInstant;
  }

  public StarlakeSession setEndInstant(Instant endInstant) {
    this.endInstant = endInstant;
    return this;
  }

  public CaseInsensitiveLinkedHashMap<Table> getReplacementTables() {
    return replacementTables;
  }

  public TreeMap<Instant, String> getStatements() {
    return statements;
  }
}
