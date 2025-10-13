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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

@Execution(ExecutionMode.SAME_THREAD)
@Disabled
public class SnowFlakeTest {
  private final static Logger LOGGER = Logger.getLogger(SnowFlakeTest.class.getName());
  public static Connection conn;

  @BeforeAll
  static void init() {
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
    Driver driver = null;
    try {
      driver = DriverManager.getDriver(url);
      Properties info = new Properties();
      conn = driver.connect(url, info);
    } catch (SQLException ex) {
      LOGGER.log(Level.SEVERE, "Failed to connect to DB.", ex);
      throw new RuntimeException(ex);
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
    final String sessionId = "sessionB";
    try (Statement st = conn.createStatement()) {
      st.executeUpdate("DROP TABLE IF EXISTS a;");

      // create table and insert values
      st.executeUpdate("CREATE TABLE a (test VARCHAR(40));");
      st.executeUpdate("INSERT INTO a VALUES ('test1');");
      st.executeUpdate("INSERT INTO a VALUES ('test2');");
      st.executeUpdate("INSERT INTO a VALUES ('test3');");

      // clone table
      st.executeUpdate("DROP SCHEMA IF EXISTS " + sessionId + " cascade;");
      st.executeUpdate("CREATE SCHEMA " + sessionId + "; ");
      st.executeUpdate("CREATE TABLE " + sessionId + ".a CLONE a;");

      // DML on cloned table
      st.executeUpdate("DELETE FROM " + sessionId + ".a;");

      try (ResultSet rs = st.executeQuery("SELECT count(*) from " + sessionId + ".a")) {
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
    final String sessionId = "sessionB";
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

      // Start a session
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

  @Test
  void testUnsupportedStatementsTable() throws SQLException {
    String sessionId = "sessionC";
    try (Statement st = conn.createStatement()) {
      st.executeUpdate("ALTER SESSION SET QUOTED_IDENTIFIERS_IGNORE_CASE = true");
      st.execute("BRANCH START " + sessionId + " WITH persist=true");
      st.executeUpdate("ALTER SESSION SET QUOTED_IDENTIFIERS_IGNORE_CASE = true");
      st.execute("BRANCH DROP " + sessionId + " WITH keep=true");
    }
  }

  @Test
  void testIssue12Clean() throws SQLException {
    String sessionId = "branch_database.my_db_session3";
    try (Statement st = conn.createStatement()) {
      // clean any stale remains
      st.execute("BRANCH DROP " + sessionId + " WITH persist=true");

      // start new branch
      st.execute("BRANCH START " + sessionId + " WITH persist=true");
      st.executeUpdate("ALTER SESSION SET QUERY_TAG = 'starlake'");
      st.executeUpdate("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
      st.executeUpdate("ALTER SESSION SET QUOTED_IDENTIFIERS_IGNORE_CASE = true");
      st.executeUpdate("CREATE SCHEMA IF NOT EXISTS audit");
      st.executeUpdate("CREATE TABLE IF NOT EXISTS audit.audit (\n"
          + "    jobid                VARCHAR         NOT NULL\n"
          + "    , paths              TEXT            NOT NULL\n"
          + "    , domain             VARCHAR         NOT NULL\n"
          + "    , schema             VARCHAR         NOT NULL\n"
          + "    , success            BOOLEAN         NOT NULL\n"
          + "    , count              BIGINT          NOT NULL\n"
          + "    , countaccepted      BIGINT          NOT NULL\n"
          + "    , countrejected      BIGINT          NOT NULL\n"
          + "    , timestamp          TIMESTAMP       NOT NULL\n"
          + "    , duration           BIGINT          NOT NULL\n"
          + "    , message            VARCHAR (8192)  NOT NULL\n"
          + "    , step               VARCHAR         NOT NULL\n"
          + "    , database           VARCHAR\n" + "    , tenant             VARCHAR\n"
          + "    , scheduled_date     TIMESTAMP       NOT NULL\n" + ")\n" + ";");

      // continue new branch
      st.execute("BRANCH START " + sessionId + " WITH persist=true");
      st.executeUpdate("ALTER SESSION SET QUERY_TAG = 'starlake'");
    }
  }

  @Test
  void testIssue12Dirty() throws SQLException {
    String sessionId = "branch_database.my_db_session1";
    try (Statement st = conn.createStatement()) {

      // continue existing brt
      st.execute("BRANCH START " + sessionId + " WITH persist=true");
      st.executeUpdate("ALTER SESSION SET QUERY_TAG = 'starlake'");
      st.executeUpdate("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
      st.executeUpdate("ALTER SESSION SET QUOTED_IDENTIFIERS_IGNORE_CASE = true");

      st.executeUpdate("CREATE SCHEMA IF NOT EXISTS audit");

      st.executeUpdate("CREATE TABLE IF NOT EXISTS audit.audit (\n"
          + "    jobid                VARCHAR         NOT NULL\n"
          + "    , paths              TEXT            NOT NULL\n"
          + "    , domain             VARCHAR         NOT NULL\n"
          + "    , schema             VARCHAR         NOT NULL\n"
          + "    , success            BOOLEAN         NOT NULL\n"
          + "    , count              BIGINT          NOT NULL\n"
          + "    , countaccepted      BIGINT          NOT NULL\n"
          + "    , countrejected      BIGINT          NOT NULL\n"
          + "    , timestamp          TIMESTAMP       NOT NULL\n"
          + "    , duration           BIGINT          NOT NULL\n"
          + "    , message            VARCHAR (8192)  NOT NULL\n"
          + "    , step               VARCHAR         NOT NULL\n"
          + "    , database           VARCHAR\n" + "    , tenant             VARCHAR\n"
          + "    , scheduled_date     TIMESTAMP       NOT NULL\n" + ")\n" + ";");

      st.execute("BRANCH START " + sessionId + " WITH persist=true");
      st.executeUpdate("ALTER SESSION SET QUERY_TAG = 'starlake'");
    }
  }

  @Test
  void testIssue12MoreComplex() throws SQLException {
    String sessionId = "branch_database.my_db_session7";
    try (Statement st = conn.createStatement()) {

      // st.execute("DROP TABLE IF EXISTS sales_kpi.byseller_kpi");

      // continue existing brt
      st.execute("BRANCH START " + sessionId + " WITH persist=true");
      st.executeUpdate("ALTER SESSION SET QUERY_TAG = 'starlake'");
      st.executeUpdate("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
      st.executeUpdate("ALTER SESSION SET QUOTED_IDENTIFIERS_IGNORE_CASE = true");
      st.executeUpdate("CREATE SCHEMA IF NOT EXISTS sales_kpi");


      st.execute("BRANCH START " + sessionId + " WITH persist=true");
      st.executeUpdate("ALTER SESSION SET QUERY_TAG = 'starlake'");
      st.executeUpdate("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
      st.executeUpdate("ALTER SESSION SET QUOTED_IDENTIFIERS_IGNORE_CASE = true");
      st.executeUpdate("USE SCHEMA sales_kpi");

      st.executeUpdate("CREATE TABLE sales_kpi.byseller_kpi\n" + "    AS WITH mycte AS (\n"
          + "        SELECT  o.amount\n" + "                , c.id\n"
          + "                , CURRENT_TIMESTAMP() AS timestamp\n" + "        FROM sales.orders o\n"
          + "            , sales.customers c\n" + "        WHERE o.customer_id = c.id )\n"
          + "SELECT  id\n" + "        , Sum( amount ) AS sum\n" + "        , timestamp\n"
          + "FROM mycte\n" + "GROUP BY    mycte.id\n" + "            , mycte.timestamp\n" + ";");

    }
  }

}
