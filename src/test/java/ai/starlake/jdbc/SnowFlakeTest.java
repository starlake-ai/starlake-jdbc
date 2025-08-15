package ai.starlake.jdbc;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Disabled
public class SnowFlakeTest {
  public static Connection conn;

  @BeforeAll
  static void init() {
    try {
      Class.forName("net.snowflake.client.jdbc.SnowflakeDriver");
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
        "jdbc:snowflake://%s.snowflakecomputing.com/?user=%s&password=%s&warehouse=%s&db=%s&schema=%s",
        account, user, password, warehouse, database, schema);


    try {
      conn = DriverManager.getConnection(url);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testSelect() throws SQLException {
    String sqlStr = "SELECT * FROM customer;";
    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sqlStr)) {
      Assertions.assertThat(rs.next()).isTrue();
    }
  }
}
