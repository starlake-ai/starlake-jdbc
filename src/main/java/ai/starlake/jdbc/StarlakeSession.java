package ai.starlake.jdbc;

import ai.starlake.transpiler.JSQLReplacer;
import ai.starlake.transpiler.schema.CaseInsensitiveLinkedHashMap;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
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
import java.sql.SQLException;
import java.time.Instant;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StarlakeSession {
  public static final String BASE62 =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  public final static Logger LOGGER = Logger.getLogger(StarlakeSession.class.getName());

  public enum Status {
    STARTED, APPLIED, DROPPED
  }

  private final StarlakeConnection conn;
  private final String sessionId;
  private Status status;
  private final Instant startInstant;
  private Instant endInstant;
  private final CaseInsensitiveLinkedHashMap<Table> replacementTables = new CaseInsensitiveLinkedHashMap<>();
  private final TreeMap<Instant, String> statements = new TreeMap<>();

  private final ExpressionVisitor<Boolean> modifyingTableExpressionVisitor =
          new ExpressionVisitorAdapter<>();

  private final SelectVisitorAdapter<Boolean> modifyingTableSelectVisitor = new SelectVisitorAdapter<>(modifyingTableExpressionVisitor) {
    @Override
    public void visit(PlainSelect plainSelect) {
      // detect any INSERT INTO tables
      modifyingTableVisitor.visitFromItem(plainSelect.getIntoTempTable(), null);
      modifyingTableVisitor.visitTables(plainSelect.getIntoTables(), null);

      super.visit(plainSelect);
    }
  };

  // detects tables to be modified and add those to the Replacement Tables
  private final FromItemVisitorAdapter<Boolean> modifyingTableVisitor = new FromItemVisitorAdapter<>(modifyingTableSelectVisitor, modifyingTableExpressionVisitor) {
    @Override
    public <S> Boolean visit(Table table, S context) {
      String originalName = table.getFullyQualifiedName();
      if (!replacementTables.containsKey(originalName)) {
        replacementTables.put(originalName, new Table( table.withSchemaName(sessionId).getFullyQualifiedName()) );
        return true;
      } else {
        return false;
      }
    }
  };

  private final StatementVisitorAdapter<Void> statementVisitor =
      new StatementVisitorAdapter<>() {
        @Override
        public <S> Void visit(CreateTable createTable, S context) {
          if (createTable.getTable().accept(modifyingTableVisitor, null)) {
            // check if table does not exist yet existing schema
          };
          return super.visit(createTable, context);
        }

        @Override
        public <S> Void visit(Delete delete, S context) {
          modifyingTableVisitor.visitFromItem(delete.getTable(), null);

          return super.visit(delete, context);
        }

        @Override
        public <S> Void visit(Update update, S context) {
          modifyingTableVisitor.visitFromItem(update.getTable(), null);

          return super.visit(update, context);
        }

        @Override
        public <S> Void visit(Insert insert, S context) {
          modifyingTableVisitor.visitFromItem(insert.getTable(), null);

          return super.visit(insert, context);
        }

        @Override
        public <S> Void visit(Drop drop, S context) {
          if (drop.getType().equalsIgnoreCase("table")) {
            modifyingTableVisitor.visitFromItem(drop.getName(), null);
          }

          return super.visit(drop, context);
        }

        @Override
        public <S> Void visit(Truncate truncate, S context) {
          modifyingTableVisitor.visitFromItem(truncate.getTable(), null);

          return super.visit(truncate, context);
        }

        @Override
        public <S> Void visit(Merge merge, S context) {
          modifyingTableVisitor.visitFromItem(merge.getTable(), null);

          return super.visit(merge, context);
        }
      };


  public StarlakeSession(StarlakeConnection conn, String id) throws SQLException {
    try (java.sql.Statement statement = conn.createStatement()) {
      statement.executeUpdate("CREATE SCHEMA " + id);
    }
    this.conn = conn;
    this.sessionId = id;
    this.status = Status.STARTED;
    this.startInstant = Instant.now();
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

  public String rewriteSql(net.sf.jsqlparser.statement.Statement st, String sqlStr) throws SQLException {
    LOGGER.info("Rewrite:\n" + sqlStr);
    if (status == Status.STARTED) {
      statements.put(Instant.now(), sqlStr);

      // maintain the replacement tables
      st.accept(statementVisitor, null);

      // rewrite the queries using the replacement tables
      JSQLReplacer replacer = new JSQLReplacer(conn.wrapped);
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
