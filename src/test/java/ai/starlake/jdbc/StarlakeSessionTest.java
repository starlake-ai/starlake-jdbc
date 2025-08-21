package ai.starlake.jdbc;

import ai.starlake.jdbc.StarlakeSession.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    }
  }

  @AfterEach
  void closeDatabase() throws SQLException {
    conn.close();
  }

  private boolean checkH2TableExists(String schema, String tableName) throws SQLException {
    try (Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(
            "SELECT table_name\n" + "FROM information_schema.tables\n" + "WHERE table_schema = '"
                + schema + "'\n" + "    AND table_name = '" + tableName + "'\n" + ";")) {
      return rs.next();
    }
  }

  private boolean checkH2SchemaExists(String schema) throws SQLException {
    try (Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT schema_name\n" + "FROM information_schema.schemata\n"
            + "WHERE schema_name = '" + schema + "'\n" + ";")) {
      return rs.next();
    }
  }

  private void assertThatH2SessionTableExists(String idSession, String tableName)
      throws SQLException {
    Assertions.assertTrue(checkH2TableExists(idSession.toUpperCase(), tableName.toUpperCase()));
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


  @Test
  void testApplySession() throws ClassNotFoundException, SQLException {
    // Define a session ID
    final String sessionId = "session1";

    try (Statement st = conn.createStatement()) {

      // is a Starlake Session aware connection
      Assertions.assertInstanceOf(StarlakeConnection.class, conn);

      // Start a session
      st.execute("SESSION START " + sessionId);
      Assertions.assertEquals(Status.STARTED,
          ((StarlakeConnection) conn).currentSession.getStatus());

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
      Assertions.assertEquals(Status.APPLIED,
          ((StarlakeConnection) conn).currentSession.getStatus());
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
      Assertions.assertEquals(Status.STARTED,
          ((StarlakeConnection) conn).currentSession.getStatus());

      // Create a table without touching the current schema "PUBLIC"
      st.execute("CREATE TABLE a (b VARCHAR(1));");

      assertThatH2SchemaTableNotExists("public", "a");
      assertThatH2SessionTableExists(sessionId, "a");

      // Apply the session by creating the table in schema "PUBLIC"
      st.execute("SESSION DROP session1;");

      assertThatH2SchemaTableNotExists("public", "a");
      assertThatH2SessionNotExists(sessionId);

      // Confirm session status
      Assertions.assertEquals(Status.DROPPED,
          ((StarlakeConnection) conn).currentSession.getStatus());
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
      Assertions.assertEquals(Status.APPLIED,
          ((StarlakeConnection) conn).currentSession.getStatus());

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
      Assertions.assertEquals(Status.DROPPED,
          ((StarlakeConnection) conn).currentSession.getStatus());

      // after dropping the original table still should be empty
      try (ResultSet rs = st.executeQuery("SELECT * FROM test.b")) {
        if (rs.next()) {
          Assertions.fail("Table is not empty.");
        }
      }
    }
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
}
