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


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

class StarlakeDriverTest {

  @Test
  void testH2() throws ClassNotFoundException, SQLException {
    Class.forName("ai.starlake.jdbc.StarlakeDriver");

    Driver driver = DriverManager.getDriver(
        "jdbc:starlake:h2:tcp://localhost//home/are/.manticore/riskbox;IFEXISTS=FALSE;COMPRESS=TRUE;PAGE_SIZE=128;DB_CLOSE_DELAY=30;AUTO_RECONNECT=TRUE;CACHE_SIZE=8192;MODE=Oracle;LOCK_TIMEOUT=10000;RETENTION_TIME=0");

    Assertions.assertInstanceOf(StarlakeDriver.class, driver);
  }
}
