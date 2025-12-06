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
public class SnowFlakeReconnectTest {
  private final static Logger LOGGER = Logger.getLogger(SnowFlakeReconnectTest.class.getName());
  public static Driver driver;
  public static String url;

  @BeforeAll
  static void init() {
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

    url = String.format(
        "jdbc:starlake:snowflake://%s.snowflakecomputing.com/?user=%s&password=%s&warehouse=%s&db=%s&schema=%s",
        account, user, password, warehouse, database, schema);

    try {
      Class.forName("net.snowflake.client.jdbc.SnowflakeDriver");
      Class.forName("ai.starlake.jdbc.StarlakeDriver");
      driver = DriverManager.getDriver(url);

    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "Failed load the Snowflake Driver.", ex);
      throw new RuntimeException(ex);
    }
  }


  @Test
  void explicitSchemaWithinSessionTest() throws ClassNotFoundException, SQLException {
    final String sessionId = "session1";

    // Step 1: new connection and new Branch, don't apply yet
    try (StarlakeConnection conn = (StarlakeConnection) driver.connect(url, new Properties());) {
      try (Statement st = conn.createStatement()) {

        st.executeUpdate("DROP SCHEMA IF EXISTS " + sessionId);
        st.executeUpdate("DROP SCHEMA IF EXISTS test4;");

        // Start a session
        st.execute("SESSION START " + sessionId + " WITH persist=true,keep=TRUE");

        // Create and populate a new schema with a new table w/o altering the physical metadata
        st.executeUpdate("CREATE SCHEMA test4;");
        st.executeUpdate("USE SCHEMA test4;");
        st.executeUpdate("CREATE TABLE IF NOT EXISTS f (c VARCHAR(40));");
        st.executeUpdate("INSERT INTO f VALUES('test1'); ");

        // Verify:
        // - we can access this branched table using the fully qualified name
        // - it contains data
        try (ResultSet rs = st.executeQuery("SELECT * FROM test4.f")) {
          if (rs.next()) {
            org.junit.jupiter.api.Assertions.assertEquals("test1", rs.getString(1));
          } else {
            org.junit.jupiter.api.Assertions.fail("No records returned.");
          }
        }
      }
      // close the connection
    }

    // Step2: new connection and continue branch, apply
    try (StarlakeConnection conn = (StarlakeConnection) driver.connect(url, new Properties());) {
      try (Statement st = conn.createStatement()) {

        // Verify:
        // - no schema or table exists outside the branch
        Assertions.assertThatExceptionOfType(SQLException.class)
            .isThrownBy(() -> st.executeQuery("SELECT * FROM test4.f"));

        // Continue session and apply
        st.execute("SESSION START " + sessionId + " WITH persist=true,keep=TRUE");
        st.execute("SESSION APPLY " + sessionId + " WITH persist=true,keep=FALSE");
        st.executeUpdate("DROP SCHEMA IF EXISTS " + sessionId);

        // Verify:
        // - we can access this schema and table now outside the branch
        // - table contains data
        try (ResultSet rs = st.executeQuery("SELECT * FROM test4.f")) {
          if (rs.next()) {
            org.junit.jupiter.api.Assertions.assertEquals("test1", rs.getString(1));
          } else {
            org.junit.jupiter.api.Assertions.fail("No records returned.");
          }
        }
      }
      // close the connection
    }

    // Step 3: new connection without any branch
    try (StarlakeConnection conn = (StarlakeConnection) driver.connect(url, new Properties());) {
      try (Statement st = conn.createStatement()) {
        // Verify:
        // - schema and table exists outside the branch
        Assertions.assertThatNoException()
            .isThrownBy(() -> st.executeQuery("SELECT * FROM test4.f"));
        st.executeUpdate("DROP SCHEMA IF EXISTS test4;");
      }
      // close the connection
    }
  }
}
