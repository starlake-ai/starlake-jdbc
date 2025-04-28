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


import org.duckdb.DuckDBDriver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

class StarlakeDriverTest {

  @Test
  void testH2() throws ClassNotFoundException, SQLException {
    Class.forName("ai.starlake.jdbc.StarlakeDriver");

    // Wrap an H2 in memory DB
    Driver driver = DriverManager.getDriver("jdbc:starlake:h2:test");
    Assertions.assertInstanceOf(StarlakeDriver.class, driver);
    Assertions.assertInstanceOf(org.h2.Driver.class, ((StarlakeDriver) driver).getWrappedDriver());

    Properties info = new Properties();
    info.put("username", "SA");
    info.put("password", "");

    try (Connection conn = driver.connect("jdbc:starlake:h2:mem:", info);
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT 1");) {
      if (rs.next()) {
        // executed against H2 correctly
        Assertions.assertEquals(1, rs.getInt(1));
      }

      // instance of the Starlake ResultSet implementation
      Assertions.assertInstanceOf(StarlakeResultSet.class, rs);

      // instance of the Starlake Statement implementation
      Assertions.assertInstanceOf(StarlakeStatement.class, rs.getStatement());

      // instance of the Starlake Connection implementation
      Assertions.assertInstanceOf(StarlakeConnection.class, st.getConnection());
    }
  }

  @Test
  void testDuckDB() throws ClassNotFoundException, SQLException {
    Class.forName("ai.starlake.jdbc.StarlakeDriver");

    // Wrap a DuckDB in memory DB
    Driver driver = DriverManager.getDriver("jdbc:starlake:duckdb:");
    Assertions.assertInstanceOf(StarlakeDriver.class, driver);
    Assertions.assertInstanceOf(DuckDBDriver.class, ((StarlakeDriver) driver).getWrappedDriver());

    Properties info = new Properties();
    info.put("username", "SA");
    info.put("password", "");

    try (Connection conn = driver.connect("jdbc:starlake:duckdb:", info);
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT 1");) {
      if (rs.next()) {
        // executed against H2 correctly
        Assertions.assertEquals(1, rs.getInt(1));
      }

      // instance of the Starlake ResultSet implementation
      Assertions.assertInstanceOf(StarlakeResultSet.class, rs);

      // instance of the Starlake Statement implementation
      Assertions.assertInstanceOf(StarlakeStatement.class, rs.getStatement());

      // instance of the Starlake Connection implementation
      Assertions.assertInstanceOf(StarlakeConnection.class, st.getConnection());
    }
  }
}
