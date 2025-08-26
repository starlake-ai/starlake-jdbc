package ai.starlake.jdbc;

import ai.starlake.transpiler.JSQLReplacer;
import ai.starlake.transpiler.schema.CaseInsensitiveLinkedHashMap;
import ai.starlake.transpiler.schema.JdbcMetaData;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.select.FromItemVisitorAdapter;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;

import java.math.BigInteger;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
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

  public enum Status {
    STARTED, APPLIED, DROPPED
  }

  private final StarlakeConnection conn;
  private final String sessionId;
  private Status status;
  private final Instant startInstant;
  private Instant endInstant;
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
          String originalName = table.getFullyQualifiedName();
          if (!replacementTables.containsKey(originalName)) {
            Table replacementTable =
                new Table(table.withSchemaName(sessionId).getFullyQualifiedName());

            if (!(context instanceof CreateTable)) {
              // create table as select
              try (java.sql.Statement st = conn.wrapped.createStatement()) {
                if (conn.wrapped.getMetaData().getDriverName().toLowerCase()
                    .contains("jdbc:snowflake")) {
                  LOGGER.info("Using `CLONE` on SnowFlake");
                  st.executeUpdate("CREATE TABLE " + replacementTable.getFullyQualifiedName()
                      + " CLONE " + originalName);
                } else {
                  st.executeUpdate("CREATE TABLE " + replacementTable.getFullyQualifiedName()
                      + " AS SELECT * FROM " + originalName);
                }
                metaData.updateTable(conn, replacementTable);
              } catch (Exception ex) {
                throw new RuntimeException("Failed to create a copy of " + originalName, ex);
              }

            }

            replacementTables.put(originalName, replacementTable);
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

      return super.visit(alter, context);// rewrite the queries using the replacement tables
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

  public StarlakeSession(StarlakeConnection conn, String id) throws SQLException {
    String currentSchema = conn.getSchema();

    try (java.sql.Statement statement = conn.createStatement()) {
      statement.executeUpdate("CREATE SCHEMA " + id);

      try {
        // Snowflake sets any CREATE SCHEMA as current schema
        statement.executeUpdate("USE SCHEMA " + currentSchema);
      } catch (Exception e) {
        LOGGER.warning("Failed to set current schema.");
      }
    }



    this.conn = conn;
    this.sessionId = id;
    this.status = Status.STARTED;
    this.startInstant = Instant.now();
    this.metaData = new JdbcMetaData(conn);
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

  public StarlakeSession(StarlakeConnection conn) throws SQLException {
    this(conn, generateUniqueID());
  }

  public void drop() {
    this.status = Status.DROPPED;
    this.endInstant = Instant.now();

    try (java.sql.Statement statement = conn.createStatement()) {
      for (Table t : replacementTables.values()) {
        statement.executeUpdate("DROP TABLE " + t.getFullyQualifiedName());
      }
      statement.executeUpdate("DROP SCHEMA " + sessionId);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Failed to drop schema when dropping session", e);
    }
  }

  public void apply() {
    this.status = Status.APPLIED;
    this.endInstant = Instant.now();

    try (java.sql.Statement statement = conn.createStatement()) {
      for (String sqlStr : statements.values()) {
        try {
          statement.execute(sqlStr);
        } catch (SQLException ex) {
          LOGGER.log(Level.SEVERE, "Failed apply statement from session:\n" + sqlStr, ex);
        }
      }
      for (Table t : replacementTables.values()) {
        statement.executeUpdate("DROP TABLE " + t.getFullyQualifiedName());
      }
      statement.executeUpdate("DROP SCHEMA " + sessionId);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Failed to drop schema after applying session", e);
    }
  }

  public String rewriteSql(Statement st, String sqlStr) throws SQLException {
    LOGGER.info("Rewrite:\n" + sqlStr);
    if (status == Status.STARTED) {
      statements.put(Instant.now(), sqlStr);

      // maintain the replacement tables
      st.accept(statementVisitor, st);

      if (st instanceof Insert || st instanceof Update || st instanceof Delete
          || st instanceof Merge || st instanceof Select) {
        for (Map.Entry<String, Table> e : replacementTables.entrySet()) {
          metaData.addSynonym(e.getKey(), e.getValue().getFullyQualifiedName());
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
