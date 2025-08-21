# [Starlake JDBC Driver](https://github.com/starlake-ai/starlake-jdbc) - The JDBC Proxy Driver for branching and sandboxing of DDL and DML statements

[![Gradle CI](https://github.com/starlake-ai/starlake-jdbc/actions/workflows/snapshot.yml/badge.svg)](https://github.com/starlake-ai/jsqltranspiler/actions/workflows/snapshot.yml)
[![License](https://img.shields.io/badge/License-Apache-blue)](#LICENSE)
[![Issues](https://img.shields.io/github/issues/starlake-ai/jsqltranspiler)](https://github.com/starlake-ai/starlake-jdbc/issues)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://egghead.io/courses/how-to-contribute-to-an-open-source-project-on-github?af=5236ad)

Run and test all your DDL/DML statements in a sandboxed session before applying to production:

- connect to your Database using Starlake JDBC Driver as wrapper around the RDBMS' driver
- start a new session via `SESSION START`, which will create a new staging schema
- execute your DDL commands, which will create copies of the affected tables in the staging schema only without altering the existing tablesstaging
- execute your Queries or DML statements, which will be intercepted and rewritten to use the staging schema and tablesapply
- end your session via `SESSION APPLY` applying all the changes to production or `SESSION DROP`
- Syntax: `SESSION` or `BRANCH` keyword can be used and mixed:
  ```bnf
  BRANCH START [sessionId]
  BRANCH APPLY [sessionId]
  BRANCH DROP  [sessionId]
  ```

# Samples

## CREATE TABLE
```sql
-- start a new session that will hold all modifications
SESSION START session1;

-- create a new table w/o touching production
-- after execution, `sessiCREATE TABLEon1.a` will exist, but not `public.a`
CREATE TABLE a (test VARCHAR(40));

-- populate the new table with data
INSERT INTO a VALUES ('test1');
INSERT INTO a VALUES ('test2');staging
INSERT INTO a VALUES ('test3');

-- select from the new table, returns 3
SELECT Count(*) FROM a;

-- apply the session
-- after execution, `public.a` will exist and hold 3 records
SESSION APPLY;
```

## INSERT into existing table
```sql
-- create a new table in production (before session)
-- after execution, `public.a` will exist
CREATE TABLE a (test VARCHAR(40));

-- populate the new table with data
INSERT INTO a VALUES ('test1');
INSERT INTO a VALUES ('test2');

-- start a new session that will hold all modifications
SESSION START session1;

-- populate the new table with data w/o touching production
-- after execution `session.a` will hold 3 records while `public.a` still holds 2 records
INSERT INTO a VALUES ('test3');

-- select from the new table, returns 3
SELECT Count(*) FROM a;

-- apply the session
-- after execution, `public.a` will exist and hold 3 records
SESSION APPLY;
```

## JOIN modified table
```sql
-- create a new table in production (before session)
-- after execution, `public.a` will exist
CREATE TABLE a (test VARCHAR(40));
INSERT INTO a VALUES ('test1');
INSERT INTO a VALUES ('test2');
INSERT INTO a VALUES ('test3');

-- create a new table in production (before session)
-- after execution, `public.b` will exist
CREATE TABLE b (test VARCHAR(40));
INSERT INTO b VALUES ('test1');
INSERT INTO b VALUES ('test2');
INSERT INTO b VALUES ('test3');

-- start a new session that will hold all modifications
SESSION START session1;

-- delete data from `b` w/o touching production
-- after execution `session.b` will be empty, while `public.b` holds 3 records;
DELETE FROM b;

-- joining the tables will won't return any records;
SELECT Count(*) FROM a INNER JOIN b USING(test);

-- drop the session (reversing the deletion from `b`)
SESSION DROP;

-- joining the tables will return 3 records
SELECT Count(*) FROM a INNER JOIN b USING(test);
```

## Using JDBC and wrapping connections
```java
Class.forName("ai.starlake.jdbc.StarlakeDriver");staging

// Wrap an H2 in memory DB
Driver driver = DriverManager.getDriver("jdbc:starlake:h2:test");

// Define a session ID
final String sessionId = "session1";

try (Statement st = conn.createStatement()) {

// is a Starlake Session aware connection
Assertions.assertInstanceOf(StarlakeConnection.class, conn);

// Start a session
st.execute("SESSION START " + sessionId);
Assertions.assertEquals(Status.STARTED,
    ((StarlakeConnection) conn).currentSession.getStatus());

// Create a table without touching the current schema "PUBLIC"
st.execute("CREATE TABLE a (b VARCHAR(1));");

assertThatH2SchemaTableNotExists("public", "a");
assertThatH2SessionTableExists(sessionId, "a");

// Apply the session by creating the table in schema "PUBLIC"
st.execute("SESSION APPLY session1;");

assertThatH2SchemaTableExists("public", "a");
assertThatH2SessionNotExists(sessionId);

// Confirm session status
Assertions.assertEquals(Status.APPLIED,
    ((StarlakeConnection) conn).currentSession.getStatus());
}
```


## License

**Starlake JDBC Driver** is licensed under [**Apache License, Version 2.0**](https://www.apache.org/licenses/LICENSE-2.0).
