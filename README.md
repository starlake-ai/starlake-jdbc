# [Starlake JDBC Driver](https://github.com/starlake-ai/starlake-jdbc) - The JDBC Proxy Driver for branching and sandboxing of DDL and DML statements

[![Gradle CI](https://github.com/starlake-ai/starlake-jdbc/actions/workflows/snapshot.yml/badge.svg)](https://github.com/starlake-ai/jsqltranspiler/actions/workflows/snapshot.yml)
[![License](https://img.shields.io/badge/License-Apache-blue)](#LICENSE)
[![Issues](https://img.shields.io/github/issues/starlake-ai/jsqltranspiler)](https://github.com/starlake-ai/starlake-jdbc/issues)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://egghead.io/courses/how-to-contribute-to-an-open-source-project-on-github?af=5236ad)

Run and test all your DDL/DML statements in a sandboxed branch before applying to production:

- connect to your Database using Starlake JDBC Driver as wrapper around the RDBMS' driver
- start a new branch via `BRANCH START`, which will create a new staging schema
- execute your DDL commands, which will create copies of the affected tables in the staging schema only without altering the existing tablesstaging
- execute your Queries or DML statements, which will be intercepted and rewritten to use the staging schema and tablesapply
- end your branch via `BRANCH APPLY` applying all the changes to production or `BRANCH DROP`
- Syntax: `BRANCH` or `SESSION` keyword can be used and mixed:
  ```bnf
  BRANCH START [branchId] [ WITH key=value (, key=value)* ]
  BRANCH APPLY [branchId] [ WITH key=value (, key=value)* ]
  BRANCH DROP  [branchId] [ WITH key=value (, key=value)* ]
  BRANCH DESCRIBE [branchId] 
  BRANCH STATUS [branchId]
  ```

# Samples

## CREATE TABLE
```sql
-- start a new branch that will hold all modifications
BRANCH START branch1;

-- create a new table w/o touching production
-- after execution, `branch1.a` will exist, but not `public.a`
CREATE TABLE a (test VARCHAR(40));

-- populate the new table with data
INSERT INTO a VALUES ('test1');
INSERT INTO a VALUES ('test2');
INSERT INTO a VALUES ('test3');

-- select from the new table, returns 3
SELECT Count(*) FROM a;

-- apply the branch
-- after execution, `public.a` will exist and hold 3 records
BRANCH APPLY;
```

## INSERT into existing table
```sql
-- create a new table in production (before branch)
-- after execution, `public.a` will exist
CREATE TABLE a (test VARCHAR(40));

-- populate the new table with data
INSERT INTO a VALUES ('test1');
INSERT INTO a VALUES ('test2');

-- start a new branch that will hold all modifications
BRANCH START branch1;

-- populate the new table with data w/o touching productionadd more test cases
-- after execution `branch.a` will hold 3 records while `public.a` still holds 2 records
INSERT INTO a VALUES ('test3');

-- select from the new table, returns 3
SELECT Count(*) FROM a;

-- apply the branch
-- after execution, `public.a` will exist and hold 3 records
BRANCH APPLY;
```

## JOIN modified table
```sql
-- create a new table in production (before branch)
-- after execution, `public.a` will exist
CREATE TABLE a (test VARCHAR(40));
INSERT INTO a VALUES ('test1');
INSERT INTO a VALUES ('test2');
INSERT INTO a VALUES ('test3');

-- create a new table in production (before branch)
-- after execution, `public.b` will exist
CREATE TABLE b (test VARCHAR(40));
INSERT INTO b VALUES ('test1');
INSERT INTO b VALUES ('test2');
INSERT INTO b VALUES ('test3');

-- start a new branch that will hold all modifications
BRANCH START branch1;

-- delete data from `b` w/o touching production
-- after execution `branch.b` will be empty, while `public.b` holds 3 records;
DELETE FROM b;

-- joining the tables will return count=0
SELECT Count(*) FROM a INNER JOIN b USING(test);

-- drop the branch (reversing the deletion from `b`)
BRANCH DROP;

-- joining the tables will return count=3
SELECT Count(*) FROM a INNER JOIN b USING(test);
```

## SCHEMA branching
```sql
-- BRANCH ------------------------------------------------------------------------
BRANCH START session1;

CREATE SCHEMA test4;                -- ommit physical creation
SET SCHEMA test4;                   -- now `test4` is current schema,
                                    -- even when there is no schema `test4`
CREATE TABLE f (c VARCHAR(40));     -- effectively meaning `test4.f`
INSERT INTO f VALUES('test1');

SELECT * FROM f;                    -- works on BRANCHED table `session1.f`
SELECT * FROM test4.f;              -- also works on BRANCHED table `session1.f`

BRANCH APPLY session1;              -- implement the branched changes
                                    -- creating schema `test4` and table `test4.f`           

-- PHYSICAL ----------------------------------------------------------------------
SELECT * FROM f;                    -- works on PHYSICAL table `test4.f`
SELECT * FROM test4.f;              -- also works on PHYSICAL table `test4.f`
```

## Using JDBC and wrapping connections
```java
Class.forName("ai.starlake.jdbc.StarlakeDriver");

// Wrap an H2 in memory DB
Driver driver = DriverManager.getDriver("jdbc:starlake:h2:test");

// Define a branch ID
final String branchId = "branch1";

try (Statement st = conn.createStatement()) {

    // is a Starlake Session aware connection
    Assertions.assertInstanceOf(StarlakeConnection.class, conn);
    
    // Start a branch
    st.execute("BRANCH START " + branchId);
    Assertions.assertEquals(Status.STARTED,
        ((StarlakeConnection) conn).currentSession.getStatus());
    
    // Create a table without touching the current schema "PUBLIC"
    st.execute("CREATE TABLE a (b VARCHAR(1));");
    
    assertThatH2SchemaTableNotExists("public", "a");
    assertThatH2SessionTableExists(branchId, "a");
    
    // Apply the branch by creating the table in schema "PUBLIC"
    st.execute("BRANCH APPLY branch1;");
    
    assertThatH2SchemaTableExists("public", "a");
    assertThatH2SessionNotExists(branchId);
    
    // Confirm branch status
    Assertions.assertEquals(Status.APPLIED,
        ((StarlakeConnection) conn).currentSession.getStatus());
}
```


## License

**Starlake JDBC Driver** is licensed under [**Apache License, Version 2.0**](https://www.apache.org/licenses/LICENSE-2.0).
