package ai.starlake.jdbc;

import ai.starlake.transpiler.schema.CaseInsensitiveLinkedHashMap;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.RenameTableStatement;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.AlterView;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.refresh.RefreshMaterializedViewStatement;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.upsert.Upsert;
import net.sf.jsqlparser.util.deparser.AlterDeParser;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.MergeDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;
import net.sf.jsqlparser.util.deparser.StatementDeParser;
import net.sf.jsqlparser.util.deparser.UpsertDeParser;

import java.math.BigInteger;
import java.sql.Connection;
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

  private final Connection conn;
  private final String sessionId;
  private Status status;
  private final Instant startInstant;
  private Instant endInstant;

  private final CaseInsensitiveLinkedHashMap<Table> tables = new CaseInsensitiveLinkedHashMap<>();
  private final TreeMap<Instant, String> statements = new TreeMap<>();

  private final StringBuilder builder = new StringBuilder();
  private final SelectDeParser selectDeParser = new SelectDeParser(builder) {
    @Override
    public <S> StringBuilder visit(Table tableName, S context) {
      if (tables.containsKey(tableName.getFullyQualifiedName())) {
        tableName.setSchemaName(sessionId);
      }
      return super.visit(tableName, context);
    }
  };
  private final ExpressionDeParser expressionDeParser =
      new ExpressionDeParser(selectDeParser, builder);
  private final StatementDeParser statementDeParser =
      new StatementDeParser(expressionDeParser, selectDeParser, builder) {
        @Override
        public <S> StringBuilder visit(CreateIndex createIndex, S context) {
          Table table = createIndex.getTable();
          String originalName = table.getFullyQualifiedName();
          if (tables.containsKey(originalName)) {
            table.setSchemaName(sessionId);
          }

          return super.visit(createIndex, context);
        }

        @Override
        public <S> StringBuilder visit(CreateTable createTable, S context) {
          Table table = createTable.getTable();
          String originalName = table.getFullyQualifiedName();
          tables.put(originalName, table.withSchemaName(sessionId));

          return super.visit(createTable, context);
        }

        @Override
        public <S> StringBuilder visit(CreateView createView, S context) {
          Table table = createView.getView();
          String originalName = table.getFullyQualifiedName();
          tables.put(originalName, table.withSchemaName(sessionId));

          return super.visit(createView, context);
        }

        @Override
        public <S> StringBuilder visit(RefreshMaterializedViewStatement materializedViewStatement,
            S context) {
          Table table = materializedViewStatement.getView();
          if (tables.containsKey(table.getFullyQualifiedName())) {
            table.setSchemaName(sessionId);
          }

          return super.visit(materializedViewStatement, context);
        }

        @Override
        public <S> StringBuilder visit(AlterView alterView, S context) {
          Table table = alterView.getView();
          if (tables.containsKey(table.getFullyQualifiedName())) {
            alterView.setView(tables.get(table.getFullyQualifiedName()));
          }

          return super.visit(alterView, context);
        }

        @Override
        public <S> StringBuilder visit(Delete delete, S context) {
          Table table = delete.getTable();
          if (tables.containsKey(table.getFullyQualifiedName())) {
            delete.setTable(tables.get(table.getFullyQualifiedName()));
          }

          return super.visit(delete, context);
        }

        @Override
        public <S> StringBuilder visit(Drop drop, S context) {
          Table table = drop.getName();
          if (tables.containsKey(table.getFullyQualifiedName())) {
            drop.setName(tables.get(table.getFullyQualifiedName()));
          }

          return super.visit(drop, context);
        }

        @Override
        public <S> StringBuilder visit(Insert insert, S context) {
          Table table = insert.getTable();
          if (tables.containsKey(table.getFullyQualifiedName())) {
            insert.setTable(tables.get(table.getFullyQualifiedName()));
          }

          return super.visit(insert, context);
        }


        @Override
        public <S> StringBuilder visit(Truncate truncate, S context) {
          Table table = truncate.getTable();
          if (tables.containsKey(table.getFullyQualifiedName())) {
            truncate.setTable(tables.get(table.getFullyQualifiedName()));
          } else {
            // @todo: create table in schema
          }

          return super.visit(truncate, context);
        }

        @Override
        public <S> StringBuilder visit(Update update, S context) {
          Table table = update.getTable();
          if (tables.containsKey(table.getFullyQualifiedName())) {
            update.setTable(tables.get(table.getFullyQualifiedName()));
          } else {
            // @todo: create table in schema
          }

          return super.visit(update, context);
        }

        @Override
        public <S> StringBuilder visit(Alter alter, S context) {
          AlterDeParser alterDeParser = new AlterDeParser(builder);
          alterDeParser.deParse(alter);
          return builder;
        }


        @SuppressWarnings({"PMD.CyclomaticComplexity"})
        @Override
        public <S> StringBuilder visit(Merge merge, S context) {
          Table table = merge.getTable();

          if (tables.containsKey(table.getFullyQualifiedName())) {
            merge.setTable(tables.get(table.getFullyQualifiedName()));
          }

          new MergeDeParser(expressionDeParser, selectDeParser, builder).deParse(merge);
          return builder;
        }

        @Override
        public <S> StringBuilder visit(Upsert upsert, S context) {
          UpsertDeParser upsertDeParser =
              new UpsertDeParser(expressionDeParser, selectDeParser, builder);
          upsertDeParser.deParse(upsert);
          return builder;
        }


        // @Override
        // public <S> StringBuilder visit(AlterSequence alterSequence, S context) {
        // new AlterSequenceDeParser(builder).deParse(alterSequence);
        // return builder;
        // }


        // @Override
        // public <S> StringBuilder visit(CreateSynonym createSynonym, S context) {
        // new CreateSynonymDeparser(builder).deParse(createSynonym);
        // return builder;
        // }

        @Override
        public <S> StringBuilder visit(RenameTableStatement renameTableStatement, S context) {
          renameTableStatement.appendTo(builder);
          return builder;
        }
      };


  public StarlakeSession(Connection conn, String id) throws SQLException {
    try (java.sql.Statement statement = conn.createStatement()) {
      statement.executeUpdate("CREATE SCHEMA " + id);
    }
    this.conn = conn;
    this.sessionId = id;
    this.status = Status.STARTED;
    this.startInstant = Instant.now();

    selectDeParser.setExpressionVisitor(expressionDeParser);
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

  public StarlakeSession(Connection conn) throws SQLException {
    this(conn, generateUniqueID());
  }

  public void drop() {
    this.status = Status.DROPPED;
    this.endInstant = Instant.now();

    try (java.sql.Statement statement = conn.createStatement()) {
      for (Table t : tables.values()) {
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
      for (Table t : tables.values()) {
        statement.executeUpdate("DROP TABLE " + t.getFullyQualifiedName());
      }
      statement.executeUpdate("DROP SCHEMA " + sessionId);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Failed to drop schema after applying session", e);
    }
  }

  public String rewriteSql(net.sf.jsqlparser.statement.Statement st, String sqlStr) {
    if (status == Status.STARTED) {
      statements.put(Instant.now(), sqlStr);
      final StringBuilder builder = st.accept(statementDeParser, null);
      return builder.toString();
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

  public CaseInsensitiveLinkedHashMap<Table> getTables() {
    return tables;
  }

  public TreeMap<Instant, String> getStatements() {
    return statements;
  }
}
