package ai.starlake.jdbc;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

class StarlakeDriverTest {

    @Test
    void testH2() throws ClassNotFoundException, SQLException {
        Class.forName("ai.starlake.jdbc.StarlakeDriver");

        Driver driver = DriverManager.getDriver("jdbc:starlake:h2:tcp://localhost//home/are/.manticore/riskbox;IFEXISTS=FALSE;COMPRESS=TRUE;PAGE_SIZE=128;DB_CLOSE_DELAY=30;AUTO_RECONNECT=TRUE;CACHE_SIZE=8192;MODE=Oracle;LOCK_TIMEOUT=10000;RETENTION_TIME=0");

        Assertions.assertInstanceOf(StarlakeDriver.class, driver);
    }
}