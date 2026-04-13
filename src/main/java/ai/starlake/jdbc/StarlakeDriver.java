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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StarlakeDriver implements Driver {
  public static final String URL_SCHEME = "jdbc:starlake:";
  public static final Logger LOGGER = Logger.getLogger(StarlakeDriver.class.getName());

  Driver wrapped;
  static {
    try {
      DriverManager.registerDriver(new StarlakeDriver());
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, "Couldn't register " + StarlakeDriver.class.getName(), e);
    }
  }

  public static String getDriverClassName(String jdbcUrl) {
    if (jdbcUrl == null) {
      return null;
    }

    jdbcUrl = jdbcUrl.toLowerCase();

    if (jdbcUrl.startsWith("jdbc:mysql:")) {
      return "com.mysql.cj.jdbc.Driver";
    } else if (jdbcUrl.startsWith("jdbc:mariadb:")) {
      return "org.mariadb.jdbc.Driver";
    } else if (jdbcUrl.startsWith("jdbc:postgresql:")) {
      return "org.postgresql.Driver";
    } else if (jdbcUrl.startsWith("jdbc:oracle:thin:")) {
      return "oracle.jdbc.OracleDriver";
    } else if (jdbcUrl.startsWith("jdbc:sqlserver:")) {
      return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    } else if (jdbcUrl.startsWith("jdbc:sqlite:")) {
      return "org.sqlite.JDBC";
    } else if (jdbcUrl.startsWith("jdbc:h2:")) {
      return "org.h2.Driver";
    } else if (jdbcUrl.startsWith("jdbc:derby:")) {
      return "org.apache.derby.jdbc.EmbeddedDriver";
    } else if (jdbcUrl.startsWith("jdbc:db2:")) {
      return "com.ibm.db2.jcc.DB2Driver";
    } else if (jdbcUrl.startsWith("jdbc:firebirdsql:")) {
      return "org.firebirdsql.jdbc.FBDriver";
    } else if (jdbcUrl.startsWith("jdbc:hsqldb:")) {
      return "org.hsqldb.jdbc.JDBCDriver";
    } else if (jdbcUrl.startsWith("jdbc:informix-sqli:")) {
      return "com.informix.jdbc.IfxDriver";
    } else if (jdbcUrl.startsWith("jdbc:teradata:")) {
      return "com.teradata.jdbc.TeraDriver";
    } else if (jdbcUrl.startsWith("jdbc:sap:")) {
      return "com.sap.db.jdbc.Driver";
    }

    return null; // unknown or unsupported
  }

  private String extractClassName(String url) {
    // Assuming parameters come after a "?" in the URL
    int paramIndex = url.indexOf('?');
    if (paramIndex == -1) {
      return null;
    }

    String query = url.substring(paramIndex + 1);
    String[] pairs = query.split("&");

    for (String pair : pairs) {
      String[] keyValue = pair.split("=", 2);
      if (keyValue.length == 2 && "CLASSNAME".equalsIgnoreCase(keyValue[0])) {
        return keyValue[1];
      }
    }
    return null;
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    if (url == null) {
      throw new SQLException("URL cannot be null.");
    }

    if (!url.toLowerCase().startsWith(URL_SCHEME)) {
      // throw new SQLException("Invalid URL.");
      return null;
    }

    String unwrappedUrlStr = "jdbc:" + url.substring(URL_SCHEME.length());
    LOGGER.info("Found: " + unwrappedUrlStr);

    // Extract optional CLASSNAME parameter from the URL
    String className = extractClassName(unwrappedUrlStr);
    if (className != null && !className.isEmpty()) {
      try {
        // Dynamically load and instantiate the driver class
        Class<?> clazz = Class.forName(className);
        wrapped = (Driver) clazz.getDeclaredConstructor().newInstance();

        if (wrapped.acceptsURL(unwrappedUrlStr)) {
          return new StarlakeConnection(wrapped.connect(unwrappedUrlStr, info));
        } else {
          throw new SQLException("Driver " + className + " does not accept URL: " + url);
        }
      } catch (Exception e) {
        throw new SQLException("Failed to load driver class: " + className, e);
      }
    } else {
      // No CLASSNAME given — look through registered drivers
      Enumeration<Driver> drivers = DriverManager.getDrivers();
      while (drivers.hasMoreElements()) {
        wrapped = drivers.nextElement();
        try {
          if (wrapped.acceptsURL(unwrappedUrlStr)) {
            return new StarlakeConnection(wrapped.connect(unwrappedUrlStr, info));
          }
        } catch (SQLException ignored) {
          // Continue trying other drivers
        }
      }
      throw new SQLException("No suitable driver found for URL: " + url);
    }
  }


  @Override
  public boolean acceptsURL(String url) throws SQLException {
    if (url == null) {
      return false;
    }

    if (!url.toLowerCase().startsWith(URL_SCHEME)) {
      return false;
    }

    String unwrappedUrlStr = "jdbc:" + url.substring(URL_SCHEME.length());
    LOGGER.info("Found: " + unwrappedUrlStr);

    // Extract optional CLASSNAME parameter from the URL
    String className = extractClassName(unwrappedUrlStr);
    if (className != null && !className.isEmpty()) {
      try {
        // Dynamically load and instantiate the driver class
        Class<?> clazz = Class.forName(className);
        Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
        boolean acceptsUrl = driver.acceptsURL(unwrappedUrlStr);

        if (acceptsUrl) {
          wrapped = driver;
          return true;
        } else {
          return false;
        }
      } catch (Exception e) {
        throw new SQLException("Failed to load driver class: " + className, e);
      }
    } else {
      // No CLASSNAME given — look through registered drivers
      Enumeration<Driver> drivers = DriverManager.getDrivers();
      boolean acceptsUrl = false;
      while (drivers.hasMoreElements()) {
        Driver driver = drivers.nextElement();
        acceptsUrl = driver.acceptsURL(unwrappedUrlStr);

        if (acceptsUrl) {
          wrapped = driver;
          return true;
        }
      }
      return acceptsUrl;
    }
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return wrapped.getPropertyInfo(url, info);
  }

  @Override
  public int getMajorVersion() {
    return wrapped.getMajorVersion();
  }

  @Override
  public int getMinorVersion() {
    return wrapped.getMinorVersion();
  }

  @Override
  public boolean jdbcCompliant() {
    return wrapped.jdbcCompliant();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return wrapped.getParentLogger();
  }

  public Driver getWrappedDriver() {
    return wrapped;
  }
}
