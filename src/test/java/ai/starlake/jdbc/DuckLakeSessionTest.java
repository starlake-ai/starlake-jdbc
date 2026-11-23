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

import org.duckdb.DuckDBConnection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Branching on a DuckLake catalog attached to DuckDB: the catalog DuckDB, DuckLake and Quack on
 * Demand serve. Writes run in a transaction, as a job writes.
 */
public class DuckLakeSessionTest {

  @TempDir
  Path lake;

  private Connection openLake() throws SQLException {
    Connection raw = DriverManager.getConnection("jdbc:duckdb:");
    try (Statement st = raw.createStatement()) {
      st.execute("INSTALL ducklake");
      st.execute("LOAD ducklake");
      st.execute("ATTACH IF NOT EXISTS 'ducklake:" + lake.resolve("meta.ducklake") + "' AS lake"
          + " (DATA_PATH '" + lake.resolve("data") + "/')");
      st.execute("USE lake");
    }
    return raw;
  }

  private static void execute(Connection conn, String sql) throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute(sql);
    }
  }

  private static List<String> rows(Connection conn, String sql) throws SQLException {
    List<String> result = new ArrayList<>();
    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
      while (rs.next()) {
        result.add(rs.getString(1) + ":" + rs.getString(2));
      }
    }
    return result;
  }

  @Test
  void branchedWritesStayOffProductionUntilApplied() throws SQLException {
    Connection raw = openLake();
    execute(raw, "CREATE SCHEMA sales");
    execute(raw, "CREATE TABLE sales.orders (id VARCHAR, amount INTEGER)");
    execute(raw, "INSERT INTO sales.orders VALUES ('1', 100), ('2', 200)");

    StarlakeConnection branched = new StarlakeConnection(raw);
    Assertions.assertEquals(StarlakeConnection.DatabaseType.DUCKDB, branched.getType());
    execute(branched, "BRANCH START br1 WITH PERSIST = TRUE, KEEP = TRUE");

    // an upsert as a job runs it: a transaction, a temporary view of the incoming rows, and a
    // DELETE whose correlated subquery names the target by its schema qualified name
    branched.setAutoCommit(false);
    execute(branched, "CREATE TEMPORARY VIEW incoming AS"
        + " SELECT * FROM (VALUES ('2', 250), ('3', 300)) AS t(id, amount)");
    execute(branched, "DELETE FROM sales.orders WHERE EXISTS"
        + " (SELECT 1 FROM incoming WHERE incoming.id = sales.orders.id)");
    execute(branched, "INSERT INTO sales.orders SELECT * FROM incoming");
    branched.commit();
    branched.setAutoCommit(true);

    List<String> branchRows = List.of("1:100", "2:250", "3:300");
    String query = "SELECT id, amount FROM sales.orders ORDER BY id";
    Assertions.assertEquals(branchRows, rows(branched, query));
    Assertions.assertEquals(List.of("1:100", "2:200"), rows(raw, query));

    // a new connection continues the persisted branch. It shares the DuckDB instance, which holds
    // the DuckLake catalog, as Starlake's connection pool does
    Connection second = ((DuckDBConnection) raw).duplicate();
    execute(second, "USE lake");
    StarlakeConnection continued = new StarlakeConnection(second);
    execute(continued, "BRANCH START br1 WITH PERSIST = TRUE, KEEP = TRUE");
    Assertions.assertEquals(branchRows, rows(continued, query));

    execute(continued, "BRANCH APPLY br1");
    Assertions.assertEquals(branchRows, rows(raw, query));
    continued.close();
    branched.close();
  }
}
