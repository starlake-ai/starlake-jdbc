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
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.MultiPartName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class StarlakeSessionTest {
  StarlakeConnection conn;

  @BeforeEach
  void createDatabase() throws SQLException, ClassNotFoundException {
    Class.forName("ai.starlake.jdbc.StarlakeDriver");

    // Wrap an H2 in memory DB
    Driver driver = DriverManager.getDriver("jdbc:starlake:h2:mem:");

    Properties info = new Properties();
    info.put("username", "SA");
    info.put("password", "");

    conn = (StarlakeConnection) driver.connect("jdbc:starlake:h2:mem:", info);
    try (Statement st = conn.createStatement()) {
      st.executeUpdate("CREATE SCHEMA IF NOT EXISTS test;");
      st.executeUpdate("CREATE TABLE IF NOT EXISTS test.b (c VARCHAR(40));");
      st.executeUpdate("CREATE SCHEMA IF NOT EXISTS test2;");
      st.executeUpdate("CREATE TABLE IF NOT EXISTS test2.b (c VARCHAR(40));");
      st.executeUpdate("CREATE SCHEMA IF NOT EXISTS test3;");
      st.executeUpdate("CREATE TABLE IF NOT EXISTS test3.b (c VARCHAR(40));");
    }
  }

  @AfterEach
  void closeDatabase() throws SQLException {
    conn.close();
  }

  public static List<Map<String, Object>> toRowMaps(ResultSet rs) throws SQLException {
    List<Map<String, Object>> rows = new ArrayList<>();
    ResultSetMetaData meta = rs.getMetaData();
    int colCount = meta.getColumnCount();

    while (rs.next()) {
      Map<String, Object> row = new LinkedHashMap<>();
      for (int i = 1; i <= colCount; i++) {
        String colName = meta.getColumnLabel(i); // column alias or name
        row.put(colName, rs.getObject(i));
      }
      rows.add(row);
    }
    return rows;
  }

  void assertRecordsetIsEqual(ResultSet rs1, ResultSet rs2) throws Exception {
    List<Map<String, Object>> list1 = toRowMaps(rs1);
    List<Map<String, Object>> list2 = toRowMaps(rs2);

    org.assertj.core.api.Assertions.assertThat(list1).usingRecursiveFieldByFieldElementComparator()
        .containsExactlyInAnyOrderElementsOf(list2);
  }

  private boolean checkH2TableExists(String schema, String table) throws SQLException {
    String catalogName = null;
    String schemaName = "";
    String tableName = "";

    // split the schema
    if (MultiPartName.isQuoted(schema) && schema.contains(".")) {
      String[] parts = MultiPartName.unquote(schema).split("\\.");

      switch (parts.length) {
        case 2:
          catalogName = parts[0].trim();
          schemaName = parts[1].trim();
          break;
        default:
          throw new RuntimeException("Invalid schema name: " + schema);
      }
    } else if (schema.contains(".")) {
      String[] parts = schema.split("\\.");

      switch (parts.length) {
        case 2:
          catalogName = parts[0].trim();
          schemaName = parts[1].trim();
          break;
        default:
          throw new RuntimeException("Invalid schema name: " + schema);
      }
    } else {
      schemaName = schema;
    }

    // split the table
    if (MultiPartName.isQuoted(table) && schema.contains(".")) {
      String[] parts = MultiPartName.unquote(table).split("\\.");

      switch (parts.length) {
        case 3:
          catalogName = parts[0].trim();
          schemaName = parts[1].trim();
          tableName = parts[2].trim();
          break;
        case 2:
          schemaName = parts[0].trim();
          tableName = parts[1].trim();
          break;
        default:
          throw new RuntimeException("Invalid table name: " + table);
      }
    } else if (table.contains(".")) {
      String[] parts = table.split("\\.");

      switch (parts.length) {
        case 3:
          catalogName = parts[0].trim();
          schemaName = parts[1].trim();
          tableName = parts[2].trim();
          break;
        case 2:
          schemaName = parts[0].trim();
          tableName = parts[1].trim();
          break;
        default:
          throw new RuntimeException("Invalid table name: " + table);
      }
    } else {
      tableName = table;
    }

    try (Statement st = conn.wrapped.createStatement();
        ResultSet rs = st.executeQuery(catalogName != null
            ? "SELECT table_name\n" + "FROM information_schema.tables\n" + "WHERE table_catalog = '"
                + catalogName + "' AND table_schema = '" + schemaName + "'\n"
                + "    AND table_name = '" + tableName + "'\n" + ";"
            : "SELECT table_name\n" + "FROM information_schema.tables\n" + "WHERE table_schema = '"
                + schemaName + "'\n" + "    AND table_name = '" + tableName + "'\n" + ";")) {
      return rs.next();
    }
  }

  private boolean checkH2SchemaExists(String schema) throws SQLException {
    String catalogName = null;
    String schemaName = "";
    if (MultiPartName.isQuoted(schema) && schema.contains(".")) {
      String[] parts = MultiPartName.unquote(schema).split("\\.");

      switch (parts.length) {
        case 2:
          catalogName = parts[0].trim();
          schemaName = parts[1].trim();
          break;
        default:
          throw new RuntimeException("Invalid schema name: " + schema);
      }
    } else if (schema.contains(".")) {
      String[] parts = schema.split("\\.");

      switch (parts.length) {
        case 2:
          catalogName = parts[0].trim();
          schemaName = parts[1].trim();
          break;
        default:
          throw new RuntimeException("Invalid schema name: " + schema);
      }
    } else {
      schemaName = schema;
    }

    try (Statement st = conn.wrapped.createStatement();
        ResultSet rs = st.executeQuery(catalogName != null
            ? "SELECT schema_name\n" + "FROM information_schema.schemata\n"
                + "WHERE catalog_name = '" + catalogName.toUpperCase() + "' AND schema_name = '"
                + schemaName.toUpperCase() + "'\n" + ";"
            : "SELECT schema_name\n" + "FROM information_schema.schemata\n"
                + "WHERE schema_name = '" + schemaName.toUpperCase() + "'\n" + ";")) {
      return rs.next();
    }
  }

  private void assertThatH2SessionTableExists(String idSession, String tableName)
      throws SQLException {
    Assertions.assertTrue(checkH2TableExists(idSession.toUpperCase(), tableName.toUpperCase()));
  }

  private void assertThatH2SessionExists(String idSession) throws SQLException {
    Assertions.assertTrue(checkH2SchemaExists(idSession.toUpperCase()));
  }

  private void assertThatH2SessionNotExists(String idSession) throws SQLException {
    Assertions.assertFalse(checkH2SchemaExists(idSession.toUpperCase()));
  }

  private void assertThatH2SchemaTableExists(String schema, String tableName) throws SQLException {
    Assertions.assertTrue(checkH2TableExists(schema.toUpperCase(), tableName.toUpperCase()));
  }

  private void assertThatH2SchemaTableNotExists(String schema, String tableName)
      throws SQLException {
    Assertions.assertFalse(checkH2TableExists(schema.toUpperCase(), tableName.toUpperCase()));
  }

  private void assertThatH2SchemaNotExists(String schema) throws SQLException {
    Assertions.assertFalse(checkH2SchemaExists(schema));
  }

  private void assertThatH2SchemaExists(String schema) throws SQLException {
    Assertions.assertTrue(checkH2SchemaExists(schema));
  }

  private void assertThatH2SchemaTableTableIsEmpty(String schemaName, String tableName)
      throws SQLException {
    try (Statement st = conn.wrapped.createStatement();
        ResultSet rs = st.executeQuery("SELECT * from " + schemaName + "." + tableName)) {
      if (rs.next()) {
        Assertions.fail("Table " + schemaName + "." + tableName + " is not empty.");
      }
    }
  }

  @Test
  void testExistingSessionSchema() throws ClassNotFoundException, SQLException {
    String catalog = conn.getCatalog();
    final String sessionId = catalog + ".session1";

    // Define a session ID

    try (Statement st = conn.createStatement()) {
      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // pre-create the schema
      st.execute("CREATE SCHEMA session1");
      assertThatH2SessionExists(sessionId);

      // Start a session
      st.execute("SESSION START " + sessionId + " WITH persist=TRUE");
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());
      Assertions.assertTrue(conn.currentSession.isPersistent());
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getHistoryTable());
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getMappingTable());

      // Create a table without touching the current schema "PUBLIC"
      st.executeUpdate("CREATE TABLE a (b VARCHAR(1)); ");

      Assertions.assertEquals(1,
          conn.currentSession.metaData.get("UNNAMED").get("session1").get("a").columns.size());

      st.executeUpdate("INSERT INTO a VALUES('1'); ");

      assertThatH2SchemaTableNotExists("public", "a");
      assertThatH2SessionTableExists(sessionId, "a");

      // query this table using the rewrite
      try (ResultSet rs = st.executeQuery("SELECT * FROM public.a")) {
        if (rs.next()) {
          Assertions.assertEquals("1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }

      // Apply the session by creating the table in schema "PUBLIC"
      // Keep the session objects
      st.execute("SESSION APPLY " + sessionId + " WITH keep=TRUE");

      assertThatH2SchemaTableExists("public", "a");

      // check that session schema still exists
      assertThatH2SessionExists(sessionId);
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getHistoryTable());
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getMappingTable());

      // Confirm session status
      Assertions.assertEquals(Status.APPLIED, conn.currentSession.getStatus());
    }
  }


  @Test
  void testSessionWithCatalog() throws ClassNotFoundException, SQLException {
    String catalog = conn.getCatalog();
    final String sessionId = catalog + ".session1";

    // Define a session ID

    try (Statement st = conn.createStatement()) {

      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // Start a session
      st.execute("SESSION START " + sessionId + " WITH persist=TRUE");
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());
      Assertions.assertTrue(conn.currentSession.isPersistent());
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getHistoryTable());
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getMappingTable());

      // Create a table without touching the current schema "PUBLIC"
      st.executeUpdate("CREATE TABLE a (b VARCHAR(1)); ");

      Assertions.assertEquals(1,
          conn.currentSession.metaData.get("UNNAMED").get("session1").get("a").columns.size());

      st.executeUpdate("INSERT INTO a VALUES('1'); ");

      assertThatH2SchemaTableNotExists("public", "a");
      assertThatH2SessionTableExists(sessionId, "a");

      // query this table using the rewrite
      try (ResultSet rs = st.executeQuery("SELECT * FROM public.a")) {
        if (rs.next()) {
          Assertions.assertEquals("1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }

      // Apply the session by creating the table in schema "PUBLIC"
      st.execute("SESSION APPLY " + sessionId);

      assertThatH2SchemaTableExists("public", "a");
      assertThatH2SessionNotExists(sessionId);

      // Confirm session status
      Assertions.assertEquals(Status.APPLIED, conn.currentSession.getStatus());
    }
  }

  @Test
  void testApplySession() throws ClassNotFoundException, SQLException {
    // Define a session ID
    final String sessionId = "session1";

    try (Statement st = conn.createStatement()) {

      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // Start a session
      st.execute("SESSION START " + sessionId);
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());

      // Create a table without touching the current schema "PUBLIC"
      st.executeUpdate("CREATE TABLE a (b VARCHAR(1)); ");
      st.executeUpdate("INSERT INTO a VALUES('1'); ");

      assertThatH2SchemaTableNotExists("public", "a");
      assertThatH2SessionTableExists(sessionId, "a");

      // query this table using the rewrite
      try (ResultSet rs = st.executeQuery("SELECT * FROM public.a")) {
        if (rs.next()) {
          Assertions.assertEquals("1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }

      // Apply the session by creating the table in schema "PUBLIC"
      st.execute("SESSION APPLY session1;");

      assertThatH2SchemaTableExists("public", "a");
      assertThatH2SessionNotExists(sessionId);

      // Confirm session status
      Assertions.assertEquals(Status.APPLIED, conn.currentSession.getStatus());
    }
  }

  @Test
  void testDropSession() throws ClassNotFoundException, SQLException {
    // Define a session ID
    final String sessionId = "session1";

    try (Statement st = conn.createStatement()) {

      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // Start a session
      st.execute("SESSION START " + sessionId);
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());

      // Create a table without touching the current schema "PUBLIC"
      st.execute("CREATE TABLE a (b VARCHAR(1));");

      assertThatH2SchemaTableNotExists("public", "a");
      assertThatH2SessionTableExists(sessionId, "a");

      // Apply the session by creating the table in schema "PUBLIC"
      st.execute("SESSION DROP session1;");

      assertThatH2SchemaTableNotExists("public", "a");
      assertThatH2SessionNotExists(sessionId);

      // Confirm session status
      Assertions.assertEquals(Status.DROPPED, conn.currentSession.getStatus());
    }
  }

  @Test
  void modifyApplyExistingTable() throws SQLException {
    // Define a session ID
    final String sessionId = "session1";
    try (Statement st = conn.createStatement()) {
      // Start a session
      st.execute("SESSION START " + sessionId);

      // Insert a value into the existing but empty table
      st.executeUpdate("INSERT INTO test.b VALUES('test');");

      // This should create a new session table
      assertThatH2SessionTableExists(sessionId, "b");

      // Leaving the original table untouched and empty
      assertThatH2SchemaTableTableIsEmpty("test", "b");

      // while we can still query and use the new record
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test", rs.getString(1));
        }
      }

      // Apply the session and all its changes to the original table
      st.execute("SESSION APPLY session1;");
      assertThatH2SessionNotExists(sessionId);

      // Confirm session status
      Assertions.assertEquals(Status.APPLIED, conn.currentSession.getStatus());

      // after applying, the original table should hold the record
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test", rs.getString(1));
        }
      }
    }
  }

  @Test
  void modifyDropExistingTable() throws SQLException {
    // Define a session ID
    final String sessionId = "session1";
    try (Statement st = conn.createStatement()) {
      // Start a session
      st.execute("SESSION START " + sessionId);

      // Insert a value into the existing but empty table
      st.executeUpdate("INSERT INTO test.b VALUES('test');");

      // This should create a new session table
      assertThatH2SessionTableExists(sessionId, "b");

      // Leaving the original table untouched and empty
      assertThatH2SchemaTableTableIsEmpty("test", "b");

      // while we can still query and use the new record
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test", rs.getString(1));
        }
      }

      // Drop the session and discard all changes
      st.execute("SESSION DROP session1;");
      assertThatH2SessionNotExists(sessionId);

      // Confirm session status
      Assertions.assertEquals(Status.DROPPED, conn.currentSession.getStatus());

      // after dropping the original table still should be empty
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.fail("Table is not empty.");
        }
      }
    }
  }

  @Test
  void testCreateInsert() throws SQLException {
    // Define a session ID
    final String sessionId = "session1";
    try (Statement st = conn.createStatement()) {
      // Start a session
      st.execute("SESSION START " + sessionId);

      // create table and insert values
      st.executeUpdate("CREATE TABLE a (test VARCHAR(40));");
      st.executeUpdate("INSERT INTO a VALUES ('test1');");
      st.executeUpdate("INSERT INTO a VALUES ('test2');");
      st.executeUpdate("INSERT INTO a VALUES ('test3');");

      // This should create a new session table
      assertThatH2SessionTableExists(sessionId, "a");

      // Leaving the original table untouched and empty
      assertThatH2SchemaTableNotExists("public", "a");

      // while we can still query and use the new record
      try (ResultSet rs = st.executeQuery("SELECT count(*) FROM a")) {
        if (rs.next()) {
          Assertions.assertEquals(3, rs.getInt(1));
        }
      }

      // Drop the session and discard all changes
      st.execute("SESSION APPLY;");
      assertThatH2SessionNotExists(sessionId);

      // after dropping the original table still should be empty
      try (ResultSet rs = st.executeQuery("SELECT count(*) FROM a")) {
        if (rs.next()) {
          Assertions.assertEquals(3, rs.getInt(1));
        }
      }
    }
  }

  @Test
  void testJoinModifiedTable() throws SQLException {
    // Define a session ID
    final String sessionId = "session1";
    try (Statement st = conn.createStatement()) {
      // create table and insert values
      st.executeUpdate("CREATE TABLE a (test VARCHAR(40));");
      st.executeUpdate("INSERT INTO a VALUES ('test1');");
      st.executeUpdate("INSERT INTO a VALUES ('test2');");
      st.executeUpdate("INSERT INTO a VALUES ('test3');");
      assertThatH2SchemaTableExists("public", "a");

      // create table and insert values
      st.executeUpdate("CREATE TABLE b (test VARCHAR(40));");
      st.executeUpdate("INSERT INTO b VALUES ('test1');");
      st.executeUpdate("INSERT INTO b VALUES ('test2');");
      st.executeUpdate("INSERT INTO b VALUES ('test3');");
      assertThatH2SchemaTableExists("public", "b");


      // Start a sessionSESSION
      st.execute("BRANCH START " + sessionId);

      // This should create a new session table
      st.executeUpdate("DELETE FROM b;");

      // Leaving the original table, but removing records from session table only
      assertThatH2SessionTableExists(sessionId, "b");

      // joining the session table will return 0
      try (ResultSet rs = st.executeQuery("SELECT Count(*) FROM a INNER JOIN b USING(test);")) {
        if (rs.next()) {
          Assertions.assertEquals(0, rs.getInt(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }

      // Drop the session and discard all changes
      st.execute("BRANCH DROP;");
      assertThatH2SessionNotExists(sessionId);

      // after dropping the changes, we expect the join to work
      try (ResultSet rs = st.executeQuery("SELECT Count(*) FROM a INNER JOIN b USING(test);")) {
        if (rs.next()) {
          Assertions.assertEquals(3, rs.getInt(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }
    }
  }

  @Test
  void testDuplicateTableNames() throws ClassNotFoundException, SQLException {
    String catalog = conn.getCatalog();
    final String sessionId = catalog + ".session1";

    // Define a session ID

    try (Statement st = conn.createStatement()) {
      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // pre-create the schema
      st.execute("CREATE SCHEMA session1");
      assertThatH2SessionExists(sessionId);

      // Start a session
      st.execute("SESSION START " + sessionId + " WITH persist=TRUE");
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());
      Assertions.assertTrue(conn.currentSession.isPersistent());
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getHistoryTable());
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getMappingTable());

      st.executeUpdate("INSERT INTO test.b VALUES('test1'); ");
      st.executeUpdate("INSERT INTO test2.b VALUES('test2'); ");
      st.executeUpdate("INSERT INTO test3.b VALUES('test3'); ");

      // query this table using the rewrite
      assertThatH2SessionTableExists(sessionId, "b");
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }

      assertThatH2SessionTableExists(sessionId, "b_1");
      try (ResultSet rs = st.executeQuery("SELECT * FROM test2.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test2", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }

      assertThatH2SessionTableExists(sessionId, "b_2");
      try (ResultSet rs = st.executeQuery("SELECT * FROM test3.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test3", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }

      // DROP the session, tables should be empty after
      st.execute("SESSION DROP " + sessionId + " WITH keep=TRUE");

      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.fail("RRecords found in test1.b");
        }
      }

      try (ResultSet rs = st.executeQuery("SELECT * FROM test2.b")) {
        if (rs.next()) {
          Assertions.fail("RRecords found in test2.b");
        }
      }

      try (ResultSet rs = st.executeQuery("SELECT * FROM test3.b")) {
        if (rs.next()) {
          Assertions.fail("Records found in test3.b");
        }
      }

    }
  }

  @Test
  void testAlterTableDropSessionAfter() throws ClassNotFoundException, SQLException {
    final String sessionId = "session1";

    // Define a session ID

    try (Statement st = conn.createStatement()) {
      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // Start a session
      st.execute("SESSION START " + sessionId + " WITH persist=TRUE");
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());
      Assertions.assertTrue(conn.currentSession.isPersistent());
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getHistoryTable());
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getMappingTable());

      st.executeUpdate("alter table test.b add column d VARCHAR(20);");
      st.executeUpdate("INSERT INTO test.b VALUES('test1', 'test2'); ");

      // query this table using the rewrite
      assertThatH2SessionTableExists(sessionId, "b");
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
          Assertions.assertEquals("test2", rs.getString(2));
        } else {
          Assertions.fail("No records returned.");
        }
      }

      st.execute("SESSION DROP ");
      Assertions.assertEquals(Status.DROPPED, conn.currentSession.getStatus());

      st.executeUpdate("INSERT INTO test.b VALUES('test1'); ");
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
          Assertions.assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
              Assertions.assertEquals("test2", rs.getString(2));
            }
          });
        } else {
          Assertions.fail("No records returned.");
        }
      }

    }
  }

  @Test
  void testAlterTableApplySessionAfter() throws ClassNotFoundException, SQLException {
    final String sessionId = "session1";

    // Define a session ID

    try (Statement st = conn.createStatement()) {
      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // Start a session
      st.execute("SESSION START " + sessionId + " WITH persist=TRUE");
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());
      Assertions.assertTrue(conn.currentSession.isPersistent());
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getHistoryTable());
      assertThatH2SessionTableExists(sessionId, conn.currentSession.getMappingTable());

      st.executeUpdate("alter table test.b add column d VARCHAR(20);");
      st.executeUpdate("INSERT INTO test.b VALUES('test1', 'test2'); ");

      // query this table using the rewrite
      assertThatH2SessionTableExists(sessionId, "b");
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
          Assertions.assertEquals("test2", rs.getString(2));
        } else {
          Assertions.fail("No records returned.");
        }
      }

      st.execute("SESSION APPLY");
      Assertions.assertEquals(Status.APPLIED, conn.currentSession.getStatus());
      assertThatH2SessionNotExists(sessionId);
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
          Assertions.assertEquals("test2", rs.getString(2));
        } else {
          Assertions.fail("No records returned.");
        }
      }

    }
  }

  @Test
  void testDropTableApplySessionAfter() throws ClassNotFoundException, SQLException {
    final String sessionId = "session1";
    try (Statement st = conn.createStatement()) {
      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // Start a session
      st.execute("SESSION START " + sessionId);
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());

      st.executeUpdate("drop table test.b;");

      Assertions.assertThrows(ai.starlake.transpiler.TableNotFoundException.class,
          new Executable() {
            @Override
            public void execute() throws Throwable {
              st.executeUpdate("INSERT INTO test.b VALUES('test1'); ");
            }
          });

      st.execute("SESSION DROP");
      assertThatH2SessionNotExists(sessionId);

      st.executeUpdate("INSERT INTO test.b VALUES('test1'); ");
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }

    }
  }

  @Test
  void continueSession() throws ClassNotFoundException, SQLException {
    final String sessionId = "session1";
    try (Statement st = conn.createStatement()) {
      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // Start a session
      st.execute("SESSION START " + sessionId + " WITH persist=true");
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());

      st.executeUpdate("INSERT INTO test.b VALUES('test1'); ");

      // Start a session
      st.execute("SESSION START " + sessionId + " WITH persist=true");
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());

      st.executeUpdate("INSERT INTO test.b VALUES('test1'); ");

      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }
    }
  }

  @Test
  void filterSchemaDMLWithinSession() throws ClassNotFoundException, SQLException {
    final String sessionId = "session1";
    try (Statement st = conn.createStatement()) {
      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // Start a session
      st.execute("SESSION START " + sessionId + " WITH persist=true");
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());

      st.executeUpdate("CREATE SCHEMA test4;");
      assertThatH2SchemaNotExists("test4");

      st.executeUpdate("CREATE TABLE IF NOT EXISTS test4.f (c VARCHAR(40));");
      st.executeUpdate("INSERT INTO test4.f VALUES('test1'); ");
      try (ResultSet rs = st.executeQuery("SELECT * FROM test4.f")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }
      st.execute("SESSION APPLY " + sessionId + " WITH persist=true");
      assertThatH2SchemaExists("test4");

      try (ResultSet rs = st.executeQuery("SELECT * FROM test4.f")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }

    }
  }

  @Test
  void implicitSchemaWithinSessionTest() throws ClassNotFoundException, SQLException {
    final String sessionId = "session1";
    try (Statement st = conn.createStatement()) {
      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // Start a session
      st.execute("SESSION START " + sessionId + " WITH persist=true");
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());

      st.executeUpdate("CREATE SCHEMA test4;");
      assertThatH2SchemaNotExists("test4");

      st.executeUpdate("SET SCHEMA test4;");
      assertThatH2SchemaNotExists("test4");

      st.executeUpdate("CREATE TABLE IF NOT EXISTS f (c VARCHAR(40));");
      st.executeUpdate("INSERT INTO f VALUES('test1'); ");
      try (ResultSet rs = st.executeQuery("SELECT * FROM f")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }
      st.execute("SESSION APPLY " + sessionId + " WITH persist=true");
      assertThatH2SchemaExists("test4");

      try (ResultSet rs = st.executeQuery("SELECT * FROM f")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }
    }
  }

  @Test
  void explicitSchemaWithinSessionTest() throws ClassNotFoundException, SQLException {
    final String sessionId = "session1";
    try (Statement st = conn.createStatement()) {
      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // Start a session
      st.execute("SESSION START " + sessionId + " WITH persist=true");
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());

      st.executeUpdate("CREATE SCHEMA test4;");
      assertThatH2SchemaNotExists("test4");

      st.executeUpdate("SET SCHEMA test4;");
      assertThatH2SchemaNotExists("test4");

      st.executeUpdate("CREATE TABLE IF NOT EXISTS f (c VARCHAR(40));");
      st.executeUpdate("INSERT INTO f VALUES('test1'); ");
      try (ResultSet rs = st.executeQuery("SELECT * FROM test4.f")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }
      st.execute("SESSION APPLY " + sessionId + " WITH persist=true");
      assertThatH2SchemaExists("test4");

      try (ResultSet rs = st.executeQuery("SELECT * FROM test4.f")) {
        if (rs.next()) {
          Assertions.assertEquals("test1", rs.getString(1));
        } else {
          Assertions.fail("No records returned.");
        }
      }
    }
  }

  private int countRows(java.sql.Connection c, String table) throws SQLException {
    try (Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
      if (!rs.next()) {
        throw new SQLException("No row counted in " + table);
      }
      return rs.getInt(1);
    }
  }

  @Test
  void unparsedStatementIsRefusedOnBranch() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1");
      // H2 MERGE ... KEY syntax, which JSqlParser can not parse: run as is, it would write
      // to the production table test.b
      SQLException e = Assertions.assertThrows(SQLException.class,
          () -> st.executeUpdate("MERGE INTO test.b KEY(c) VALUES('prod')"));
      Assertions.assertEquals("0A000", e.getSQLState());
      Assertions.assertTrue(conn.currentSession.getStatements().isEmpty());
    }
    Assertions.assertEquals(0, countRows(conn.wrapped, "test.b"));
  }

  @Test
  void unparsedStatementRunsOutsideBranch() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.executeUpdate("MERGE INTO test.b KEY(c) VALUES('prod')");
    }
    Assertions.assertEquals(1, countRows(conn.wrapped, "test.b"));
  }

  @Test
  void leadingKeywordsSkipsComments() {
    Assertions.assertEquals("ALTER SESSION",
        StarlakeStatement.leadingKeywords("  -- set the tz\n/* x */ alter  session set TZ='UTC'"));
    Assertions.assertEquals("SHOW", StarlakeStatement.leadingKeywords("show;"));
    Assertions.assertEquals("", StarlakeStatement.leadingKeywords("-- only a comment"));
  }

  @Test
  void readOnlyStatements() throws Exception {
    Assertions.assertTrue(StarlakeSession.isReadOnly(CCJSqlParserUtil.parse("SELECT * FROM a")));
    Assertions.assertTrue(StarlakeSession
        .isReadOnly(CCJSqlParserUtil.parse("WITH x AS (SELECT 1) SELECT * FROM x UNION SELECT 2")));
    Assertions
        .assertFalse(StarlakeSession.isReadOnly(CCJSqlParserUtil.parse("SELECT * INTO b FROM a")));
    Assertions.assertFalse(
        StarlakeSession.isReadOnly(CCJSqlParserUtil.parse("INSERT INTO a VALUES (1)")));
    Assertions.assertFalse(StarlakeSession.isReadOnly(null));
  }

  @Test
  void failedAndReadOnlyStatementsAreNotApplied() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1");
      st.executeUpdate("INSERT INTO test.b VALUES('1')");
      // fails on the branch: one column only
      Assertions.assertThrows(SQLException.class,
          () -> st.executeUpdate("INSERT INTO test.b VALUES('1', '2')"));
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (!rs.next()) {
          Assertions.fail("No records returned.");
        }
      }
      Assertions.assertEquals(1, conn.currentSession.getStatements().size());

      st.execute("SESSION APPLY session1");
    }
    Assertions.assertEquals(1, countRows(conn.wrapped, "test.b"));
  }

  @Test
  void applyStopsAtFirstError() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1");
      st.executeUpdate("CREATE TABLE test.c (x INT)");
      st.executeUpdate("INSERT INTO test.c VALUES (1)");
      st.executeUpdate("INSERT INTO test.b VALUES('1')");

      // production gets a test.c of its own: replaying the CREATE TABLE fails
      try (Statement prod = conn.wrapped.createStatement()) {
        prod.executeUpdate("CREATE TABLE test.c (x INT)");
      }

      SQLException e =
          Assertions.assertThrows(SQLException.class, () -> st.execute("SESSION APPLY session1"));
      Assertions.assertTrue(e.getMessage().contains("stopped at statement 1 of 3"), e.getMessage());
      Assertions.assertEquals(Status.STARTED, conn.currentSession.getStatus());
    }
    // nothing after the failed statement reached production
    Assertions.assertEquals(0, countRows(conn.wrapped, "test.c"));
    Assertions.assertEquals(0, countRows(conn.wrapped, "test.b"));
  }

  @Test
  void preparedStatementOnBranchIsRewrittenAndApplied() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1");
      try (PreparedStatement ps = conn.prepareStatement("INSERT INTO test.b VALUES (?)")) {
        ps.setString(1, "it's");
        Assertions.assertEquals(1, ps.executeUpdate());
      }
      Assertions.assertEquals(0, countRows(conn.wrapped, "test.b"));
      Assertions.assertEquals(List.of("INSERT INTO test.b VALUES ('it''s')"),
          new ArrayList<>(conn.currentSession.getStatements().values()));

      st.execute("SESSION APPLY session1");
    }
    try (Statement prod = conn.wrapped.createStatement();
        ResultSet rs = prod.executeQuery("SELECT c FROM test.b")) {
      if (!rs.next()) {
        Assertions.fail("No records returned.");
      }
      Assertions.assertEquals("it's", rs.getString(1));
    }
  }

  @Test
  void preparedQueryOnBranchReadsBranchAndIsNotRecorded() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1");
      st.executeUpdate("INSERT INTO test.b VALUES('branch')");
      try (PreparedStatement ps = conn.prepareStatement("SELECT c FROM test.b WHERE c = ?")) {
        ps.setString(1, "branch");
        try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next()) {
            Assertions.fail("No records returned.");
          }
        }
      }
      Assertions.assertEquals(1, conn.currentSession.getStatements().size());
    }
  }

  @Test
  void preparedBatchOnBranchIsApplied() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1");
      try (PreparedStatement ps = conn.prepareStatement("INSERT INTO test.b VALUES (?)")) {
        ps.setString(1, "a");
        ps.addBatch();
        ps.setNull(1, java.sql.Types.VARCHAR);
        ps.addBatch();
        ps.executeBatch();
      }
      Assertions.assertEquals(0, countRows(conn.wrapped, "test.b"));
      Assertions.assertEquals(
          List.of("INSERT INTO test.b VALUES ('a')", "INSERT INTO test.b VALUES (NULL)"),
          new ArrayList<>(conn.currentSession.getStatements().values()));
      st.execute("SESSION APPLY session1");
    }
    Assertions.assertEquals(2, countRows(conn.wrapped, "test.b"));
  }

  @Test
  void statementBatchOnBranchIsApplied() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1");
      st.addBatch("INSERT INTO test.b VALUES('a')");
      st.addBatch("INSERT INTO test.b VALUES('b')");
      st.executeBatch();
      Assertions.assertEquals(0, countRows(conn.wrapped, "test.b"));
      Assertions.assertEquals(2, conn.currentSession.getStatements().size());
      st.execute("SESSION APPLY session1");
    }
    Assertions.assertEquals(2, countRows(conn.wrapped, "test.b"));
  }

  @Test
  void unparsedStatementIsRefusedInBatchOnBranch() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1");
      SQLException e = Assertions.assertThrows(SQLException.class,
          () -> st.addBatch("MERGE INTO test.b KEY(c) VALUES('prod')"));
      Assertions.assertEquals("0A000", e.getSQLState());
    }
  }

  @Test
  void unrecordableParameterIsRefusedOnBranch() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1");
      try (PreparedStatement ps = conn.prepareStatement("INSERT INTO test.b VALUES (?)")) {
        ps.setCharacterStream(1, new java.io.StringReader("x"));
        SQLException e = Assertions.assertThrows(SQLException.class, ps::executeUpdate);
        Assertions.assertEquals("0A000", e.getSQLState());
      }
      Assertions.assertTrue(conn.currentSession.getStatements().isEmpty());
    }
    Assertions.assertEquals(0, countRows(conn.wrapped, "test.b"));
  }

  @Test
  void statementPreparedBeforeBranchIsRefused() throws SQLException {
    try (Statement st = conn.createStatement();
        PreparedStatement ps = conn.prepareStatement("INSERT INTO test.b VALUES (?)")) {
      st.execute("SESSION START session1");
      ps.setString(1, "prod");
      SQLException e = Assertions.assertThrows(SQLException.class, ps::executeUpdate);
      Assertions.assertEquals("0A000", e.getSQLState());
    }
    Assertions.assertEquals(0, countRows(conn.wrapped, "test.b"));
  }

  @Test
  void callableStatementIsRefusedOnBranch() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1");
      SQLException e =
          Assertions.assertThrows(SQLException.class, () -> conn.prepareCall("CALL 1"));
      Assertions.assertEquals("0A000", e.getSQLState());
    }
  }

  @Test
  void bindLiteralsSkipsQuotedQuestionMarks() throws SQLException {
    Map<Integer, Object> parameters = new java.util.HashMap<>();
    parameters.put(1, 42);
    parameters.put(2, java.sql.Date.valueOf("2026-01-31"));
    parameters.put(3, "a\\b'c");
    Assertions.assertEquals(
        "SELECT '?', \"?\", $$?$$ /* ? */ FROM t WHERE a = 42 AND b = DATE '2026-01-31'"
            + " AND c = 'a\\\\b''c' -- ?",
        StarlakePreparedStatement.bindLiterals(
            "SELECT '?', \"?\", $$?$$ /* ? */ FROM t WHERE a = ? AND b = ? AND c = ? -- ?",
            parameters, StarlakeConnection.DatabaseType.SNOWFLAKE));
    Assertions.assertEquals("'it\\'s'",
        StarlakePreparedStatement.quote("it's", StarlakeConnection.DatabaseType.BIGQUERY));
    Assertions.assertEquals("'it''s'",
        StarlakePreparedStatement.quote("it's", StarlakeConnection.DatabaseType.DUCKDB));
  }

  @Test
  void applyResumesAfterError() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1");
      st.executeUpdate("INSERT INTO test.b VALUES('1')");
      st.executeUpdate("CREATE TABLE test.c (x INT)");
      st.executeUpdate("INSERT INTO test.c VALUES (1)");

      try (Statement prod = conn.wrapped.createStatement()) {
        prod.executeUpdate("CREATE TABLE test.c (x INT)");
      }
      Assertions.assertThrows(SQLException.class, () -> st.execute("SESSION APPLY session1"));
      Assertions.assertEquals(1, conn.currentSession.getAppliedCount());
      Assertions.assertEquals(1, countRows(conn.wrapped, "test.b"));

      // remove the cause and apply again: the INSERT into test.b is not replayed
      try (Statement prod = conn.wrapped.createStatement()) {
        prod.executeUpdate("DROP TABLE test.c");
      }
      st.execute("SESSION APPLY session1");
      Assertions.assertEquals(Status.APPLIED, conn.currentSession.getStatus());
    }
    Assertions.assertEquals(1, countRows(conn.wrapped, "test.b"));
    Assertions.assertEquals(1, countRows(conn.wrapped, "test.c"));
  }

  @Test
  void applyProgressIsPersisted() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("SESSION START session1 WITH persist=true");
      st.executeUpdate("INSERT INTO test.b VALUES('1')");
      st.executeUpdate("CREATE TABLE test.c (x INT)");
      st.executeUpdate("INSERT INTO test.c VALUES (1)");

      try (Statement prod = conn.wrapped.createStatement()) {
        prod.executeUpdate("CREATE TABLE test.c (x INT)");
      }
      Assertions.assertThrows(SQLException.class,
          () -> st.execute("SESSION APPLY session1 WITH persist=true"));
    }
    // the session read back from the database resumes after the first statement
    StarlakeSession reloaded =
        StarlakeSession.getPersistingSession(conn, "session1", Map.of("persist", "true"));
    Assertions.assertNotNull(reloaded);
    Assertions.assertEquals(3, reloaded.getStatements().size());
    Assertions.assertEquals(1, reloaded.getAppliedCount());
    Assertions
        .assertFalse(reloaded.getReplacementTables().containsKey(StarlakeSession.APPLIED_MARKER));
  }

}
