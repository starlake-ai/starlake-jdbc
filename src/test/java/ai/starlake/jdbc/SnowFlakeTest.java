package ai.starlake.jdbc;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class SnowFlakeTest {
  public static Connection conn;

  @BeforeAll
  static void init() throws SQLException {
    try {
      Class.forName("net.snowflake.client.jdbc.SnowflakeDriver");
      Class.forName("ai.starlake.jdbc.StarlakeDriver");
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    String account = System.getenv("SNOWFLAKE_ACCOUNT");
    String user = System.getenv("SNOWFLAKE_USER");
    String password = System.getenv("SNOWFLAKE_PASSWORD");
    String warehouse = System.getenv("SNOWFLAKE_WAREHOUSE");
    String database = System.getenv("SNOWFLAKE_DATABASE");
    String schema = System.getenv("SNOWFLAKE_SCHEMA");

    if (account == null || user == null || password == null) {
      throw new IllegalStateException(
          "Missing required Snowflake environment variables! Please adjust");
    }

    String url = String.format(
        "jdbc:starlake:snowflake://%s.snowflakecomputing.com/?user=%s&password=%s&warehouse=%s&db=%s&schema=%s",
        account, user, password, warehouse, database, schema);
    Driver driver = DriverManager.getDriver(url);

    Properties info = new Properties();
    try {
      conn = driver.connect(url, info);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Disabled
  void testSelect() throws SQLException {
    String sqlStr = "SELECT * FROM customer;";
    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sqlStr)) {
      if (!rs.next()) {
        Assertions.fail("No records returned;");
      }
    }
  }

  @Test
  void testClonedDMLSameSchema() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.executeUpdate("DROP TABLE IF EXISTS a;");

      // create table and insert values
      st.executeUpdate("CREATE TABLE a (test VARCHAR(40));");
      st.executeUpdate("INSERT INTO a VALUES ('test1');");
      st.executeUpdate("INSERT INTO a VALUES ('test2');");
      st.executeUpdate("INSERT INTO a VALUES ('test3');");

      // clone table
      st.executeUpdate("DROP TABLE IF EXISTS b;");
      st.executeUpdate("CREATE TABLE b CLONE a;");

      // DML on cloned table
      st.executeUpdate("DELETE FROM b;");

      try (ResultSet rs = st.executeQuery("SELECT count(*) from b")) {
        if (rs.next()) {
          Assertions.assertThat(rs.getInt(1)).isEqualTo(0);
        }
      }

      try (ResultSet rs = st.executeQuery("SELECT count(*) from a")) {
        if (rs.next()) {
          Assertions.assertThat(rs.getInt(1)).isEqualTo(3);
        }
      }
    }
  }

  @Test
  void testClonedDMLDifferentSchema() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.executeUpdate("DROP TABLE IF EXISTS a;");

      // create table and insert values
      st.executeUpdate("CREATE TABLE a (test VARCHAR(40));");
      st.executeUpdate("INSERT INTO a VALUES ('test1');");
      st.executeUpdate("INSERT INTO a VALUES ('test2');");
      st.executeUpdate("INSERT INTO a VALUES ('test3');");

      // clone table
      st.executeUpdate("DROP SCHEMA IF EXISTS session1 cascade;");
      st.executeUpdate("CREATE SCHEMA session1;");
      st.executeUpdate("CREATE TABLE session1.a CLONE a;");

      // DML on cloned table
      st.executeUpdate("DELETE FROM session1.a;");

      try (ResultSet rs = st.executeQuery("SELECT count(*) from session1.a")) {
        if (rs.next()) {
          Assertions.assertThat(rs.getInt(1)).isEqualTo(0);
        }
      }
      try (ResultSet rs = st.executeQuery("SELECT count(*) from public.a")) {
        if (rs.next()) {
          Assertions.assertThat(rs.getInt(1)).isEqualTo(3);
        }
      }
    }
  }

  @Test
  void testJoinModifiedTable() throws SQLException {
    // Define a session ID
    final String sessionId = "session1";
    try (Statement st = conn.createStatement()) {
      st.executeUpdate("DROP TABLE IF EXISTS a;");
      st.executeUpdate("DROP SCHEMA IF EXISTS " + sessionId + " CASCADE;");

      // create table and insert values
      st.executeUpdate("CREATE TABLE a (test VARCHAR(40));");
      st.executeUpdate("INSERT INTO a VALUES ('test1');");
      st.executeUpdate("INSERT INTO a VALUES ('test2');");
      st.executeUpdate("INSERT INTO a VALUES ('test3');");

      // create table and insert values
      st.executeUpdate("DROP TABLE IF EXISTS b;");
      st.executeUpdate("CREATE TABLE b (test VARCHAR(40));");
      st.executeUpdate("INSERT INTO b VALUES ('test1');");
      st.executeUpdate("INSERT INTO b VALUES ('test2');");
      st.executeUpdate("INSERT INTO b VALUES ('test3');");


      // Start a sessionSESSIONpublic
      st.execute("BRANCH START " + sessionId);

      // This should create a new session table
      st.executeUpdate("DELETE FROM b;");

      // Leaving the original table, but removing records from session table only

      // joining the session table will return 0
      try (ResultSet rs = st.executeQuery("SELECT Count(*) FROM a INNER JOIN b USING(test);")) {
        if (rs.next()) {
          org.junit.jupiter.api.Assertions.assertEquals(0, rs.getInt(1));
        } else {
          org.junit.jupiter.api.Assertions.fail("No records returned.");
        }
      }

      // Drop the session and discard all changes
      st.execute("BRANCH DROP;");

      // after dropping the changes, we expect the join to work
      try (ResultSet rs = st.executeQuery("SELECT Count(*) FROM a INNER JOIN b USING(test);")) {
        if (rs.next()) {
          org.junit.jupiter.api.Assertions.assertEquals(3, rs.getInt(1));
        } else {
          org.junit.jupiter.api.Assertions.fail("No records returned.");
        }
      }
    }
  }
}
