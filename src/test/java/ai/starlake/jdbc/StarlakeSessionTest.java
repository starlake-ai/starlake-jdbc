package ai.starlake.jdbc;

import ai.starlake.jdbc.StarlakeSession.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class StarlakeSessionTest {
  Connection conn;

  @BeforeEach
  void createDatabase() throws SQLException, ClassNotFoundException {
    Class.forName("ai.starlake.jdbc.StarlakeDriver");

    // Wrap an H2 in memory DB
    Driver driver = DriverManager.getDriver("jdbc:starlake:h2:mem:");

    Properties info = new Properties();
    info.put("username", "SA");
    info.put("password", "");

    conn = driver.connect("jdbc:starlake:h2:mem:", info);
    try (Statement st = conn.createStatement()) {
      st.executeUpdate("CREATE SCHEMA IF NOT EXISTS test;");
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
        Assertions.assertTrue(rs.next());
        Assertions.assertEquals("1", rs.getString(1));
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
}
