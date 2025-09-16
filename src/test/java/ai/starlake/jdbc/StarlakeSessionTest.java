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
import net.sf.jsqlparser.schema.MultiPartName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.Driver;
import java.sql.DriverManager;
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
                + "WHERE catalog_name = '" + catalogName + "' AND schema_name = '" + schemaName
                + "'\n" + ";"
            : "SELECT schema_name\n" + "FROM information_schema.schemata\n"
                + "WHERE schema_name = '" + schemaName + "'\n" + ";")) {
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


}
