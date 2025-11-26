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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.Properties;

/**
 * Tests for ResultSetMetaData functionality that supports SQLDescribeCol and SQLColAttribute
 * in the ODBC driver.
 */
class ResultSetMetadataTest {

  private Connection conn;

  @BeforeEach
  void setUp() throws ClassNotFoundException, SQLException {
    Class.forName("ai.starlake.jdbc.StarlakeDriver");
    Driver driver = DriverManager.getDriver("jdbc:starlake:h2:mem:");
    Properties info = new Properties();
    info.put("username", "SA");
    info.put("password", "");
    conn = driver.connect("jdbc:starlake:h2:mem:testdb;DB_CLOSE_DELAY=-1", info);
    
    // Create test table with various column types
    try (Statement st = conn.createStatement()) {
      st.execute("DROP TABLE IF EXISTS metadata_test");
      st.execute("CREATE TABLE metadata_test (" +
          "id INTEGER NOT NULL PRIMARY KEY, " +
          "name VARCHAR(100), " +
          "amount DECIMAL(10,2), " +
          "created_date DATE, " +
          "created_time TIME, " +
          "created_ts TIMESTAMP, " +
          "is_active BOOLEAN, " +
          "price DOUBLE, " +
          "quantity BIGINT, " +
          "description CLOB, " +
          "data BLOB" +
          ")");
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    if (conn != null && !conn.isClosed()) {
      conn.close();
    }
  }

  @Test
  void testColumnMetadata() throws SQLException {
    try (Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery("SELECT * FROM metadata_test")) {
      
      ResultSetMetaData meta = rs.getMetaData();
      
      // Test column count
      Assertions.assertEquals(11, meta.getColumnCount());
      
      // Test column names
      Assertions.assertEquals("ID", meta.getColumnName(1));
      Assertions.assertEquals("NAME", meta.getColumnName(2));
      Assertions.assertEquals("AMOUNT", meta.getColumnName(3));
      
      // Test column types
      Assertions.assertEquals(Types.INTEGER, meta.getColumnType(1));
      Assertions.assertEquals(Types.VARCHAR, meta.getColumnType(2));
      Assertions.assertEquals(Types.DECIMAL, meta.getColumnType(3));
      Assertions.assertEquals(Types.DATE, meta.getColumnType(4));
      Assertions.assertEquals(Types.TIME, meta.getColumnType(5));
      Assertions.assertEquals(Types.TIMESTAMP, meta.getColumnType(6));
      Assertions.assertEquals(Types.BOOLEAN, meta.getColumnType(7));
      Assertions.assertEquals(Types.DOUBLE, meta.getColumnType(8));
      Assertions.assertEquals(Types.BIGINT, meta.getColumnType(9));
    }
  }

  @Test
  void testPrecisionAndScale() throws SQLException {
    try (Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery("SELECT * FROM metadata_test")) {
      
      ResultSetMetaData meta = rs.getMetaData();
      
      // VARCHAR(100) should have precision 100
      Assertions.assertEquals(100, meta.getPrecision(2));
      
      // DECIMAL(10,2) should have precision 10 and scale 2
      Assertions.assertEquals(10, meta.getPrecision(3));
      Assertions.assertEquals(2, meta.getScale(3));
      
      // INTEGER scale should be 0
      Assertions.assertEquals(0, meta.getScale(1));
    }
  }

  @Test
  void testNullability() throws SQLException {
    try (Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery("SELECT * FROM metadata_test")) {
      
      ResultSetMetaData meta = rs.getMetaData();
      
      // ID is NOT NULL (PRIMARY KEY)
      Assertions.assertEquals(ResultSetMetaData.columnNoNulls, meta.isNullable(1));
      
      // NAME is nullable (no NOT NULL constraint)
      Assertions.assertEquals(ResultSetMetaData.columnNullable, meta.isNullable(2));
    }
  }

  @Test
  void testDisplaySize() throws SQLException {
    try (Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery("SELECT * FROM metadata_test")) {
      
      ResultSetMetaData meta = rs.getMetaData();
      
      // Display sizes should be positive
      for (int i = 1; i <= meta.getColumnCount(); i++) {
        Assertions.assertTrue(meta.getColumnDisplaySize(i) > 0,
            "Column " + meta.getColumnName(i) + " should have positive display size");
      }
    }
  }

  @Test
  void testTypeInfo() throws SQLException {
    DatabaseMetaData dbMeta = conn.getMetaData();
    try (ResultSet rs = dbMeta.getTypeInfo()) {
      // Should return at least some type information
      Assertions.assertTrue(rs.next(), "getTypeInfo should return at least one type");
      
      // Each row should have TYPE_NAME column
      String typeName = rs.getString("TYPE_NAME");
      Assertions.assertNotNull(typeName, "TYPE_NAME should not be null");
    }
  }
}

