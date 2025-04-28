# [Starlake JDBC Driver](https://github.com/starlake-ai/starlake-jdbc) - The JDBC Proxy Driver for staging and sandboxing of DDL and DML statements

[![Gradle CI](https://github.com/starlake-ai/starlake-jdbc/actions/workflows/snapshot.yml/badge.svg)](https://github.com/starlake-ai/jsqltranspiler/actions/workflows/snapshot.yml)
[![License](https://img.shields.io/badge/License-Apache-blue)](#LICENSE)
[![Issues](https://img.shields.io/github/issues/starlake-ai/jsqltranspiler)](https://github.com/starlake-ai/starlake-jdbc/issues)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://egghead.io/courses/how-to-contribute-to-an-open-source-project-on-github?af=5236ad)

Run and test all your DDL/DML statements in a sandboxed session before applying to production:

- connect to your Database using Starlake JDBC Driver as wrapper around the RDBMS' driver
- start a new session via `START SESSION`, which will create a new staging schema
- execute your DDL commands, which will create copies of the affected tables in the staging schema only without altering the existing tables
- execute your Queries or DML statements, which will be intercepted and rewritten to use the staging schema and tables
- end your session via `END SESSION` applying all the changes to production or `CANCEL SESSION`


## License

**Starlake JDBC Driver** is licensed under [**Apache License, Version 2.0**](https://www.apache.org/licenses/LICENSE-2.0).
