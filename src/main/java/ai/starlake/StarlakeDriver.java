package ai.starlake;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class StarlakeDriver implements Driver {
    public static final String URL_SCHEME = "jdbc:starlake:";
    public static final Logger LOGGER = Logger.getLogger(StarlakeDriver.class.getName());

    Driver wrapped;

    public static String getDriverClassName(String jdbcUrl) {
        if (jdbcUrl == null) return null;

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

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return null;
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return false;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }
}
