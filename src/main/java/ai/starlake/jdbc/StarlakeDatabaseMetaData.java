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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;

public class StarlakeDatabaseMetaData implements DatabaseMetaData {
  private final DatabaseMetaData wrapped;

  public StarlakeDatabaseMetaData(DatabaseMetaData wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public boolean allProceduresAreCallable() throws SQLException {
    return wrapped.allProceduresAreCallable();
  }

  @Override
  public boolean allTablesAreSelectable() throws SQLException {
    return wrapped.allTablesAreSelectable();
  }

  @Override
  public String getURL() throws SQLException {
    return wrapped.getURL();
  }

  @Override
  public String getUserName() throws SQLException {
    return wrapped.getUserName();
  }

  @Override
  public boolean isReadOnly() throws SQLException {
    return wrapped.isReadOnly();
  }

  @Override
  public boolean nullsAreSortedHigh() throws SQLException {
    return wrapped.nullsAreSortedHigh();
  }

  @Override
  public boolean nullsAreSortedLow() throws SQLException {
    return wrapped.nullsAreSortedLow();
  }

  @Override
  public boolean nullsAreSortedAtStart() throws SQLException {
    return wrapped.nullsAreSortedAtStart();
  }

  @Override
  public boolean nullsAreSortedAtEnd() throws SQLException {
    return wrapped.nullsAreSortedAtEnd();
  }

  @Override
  public String getDatabaseProductName() throws SQLException {
    return wrapped.getDatabaseProductName();
  }

  @Override
  public String getDatabaseProductVersion() throws SQLException {
    return wrapped.getDatabaseProductVersion();
  }

  @Override
  public String getDriverName() throws SQLException {
    return wrapped.getDriverName();
  }

  @Override
  public String getDriverVersion() throws SQLException {
    return wrapped.getDriverVersion();
  }

  @Override
  public int getDriverMajorVersion() {
    return wrapped.getDriverMajorVersion();
  }

  @Override
  public int getDriverMinorVersion() {
    return wrapped.getDriverMinorVersion();
  }

  @Override
  public boolean usesLocalFiles() throws SQLException {
    return wrapped.usesLocalFiles();
  }

  @Override
  public boolean usesLocalFilePerTable() throws SQLException {
    return wrapped.usesLocalFilePerTable();
  }

  @Override
  public boolean supportsMixedCaseIdentifiers() throws SQLException {
    return wrapped.supportsMixedCaseIdentifiers();
  }

  @Override
  public boolean storesUpperCaseIdentifiers() throws SQLException {
    return wrapped.storesUpperCaseIdentifiers();
  }

  @Override
  public boolean storesLowerCaseIdentifiers() throws SQLException {
    return wrapped.storesLowerCaseIdentifiers();
  }

  @Override
  public boolean storesMixedCaseIdentifiers() throws SQLException {
    return wrapped.storesMixedCaseIdentifiers();
  }

  @Override
  public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
    return wrapped.supportsMixedCaseQuotedIdentifiers();
  }

  @Override
  public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
    return wrapped.storesUpperCaseQuotedIdentifiers();
  }

  @Override
  public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
    return wrapped.storesLowerCaseQuotedIdentifiers();
  }

  @Override
  public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
    return wrapped.storesMixedCaseQuotedIdentifiers();
  }

  @Override
  public String getIdentifierQuoteString() throws SQLException {
    return wrapped.getIdentifierQuoteString();
  }

  @Override
  public String getSQLKeywords() throws SQLException {
    return wrapped.getSQLKeywords();
  }

  @Override
  public String getNumericFunctions() throws SQLException {
    return wrapped.getNumericFunctions();
  }

  @Override
  public String getStringFunctions() throws SQLException {
    return wrapped.getStringFunctions();
  }

  @Override
  public String getSystemFunctions() throws SQLException {
    return wrapped.getSystemFunctions();
  }

  @Override
  public String getTimeDateFunctions() throws SQLException {
    return wrapped.getTimeDateFunctions();
  }

  @Override
  public String getSearchStringEscape() throws SQLException {
    return wrapped.getSearchStringEscape();
  }

  @Override
  public String getExtraNameCharacters() throws SQLException {
    return wrapped.getExtraNameCharacters();
  }

  @Override
  public boolean supportsAlterTableWithAddColumn() throws SQLException {
    return wrapped.supportsAlterTableWithAddColumn();
  }

  @Override
  public boolean supportsAlterTableWithDropColumn() throws SQLException {
    return wrapped.supportsAlterTableWithDropColumn();
  }

  @Override
  public boolean supportsColumnAliasing() throws SQLException {
    return wrapped.supportsColumnAliasing();
  }

  @Override
  public boolean nullPlusNonNullIsNull() throws SQLException {
    return wrapped.nullPlusNonNullIsNull();
  }

  @Override
  public boolean supportsConvert() throws SQLException {
    return wrapped.supportsConvert();
  }

  @Override
  public boolean supportsConvert(int fromType, int toType) throws SQLException {
    return wrapped.supportsConvert(fromType, toType);
  }

  @Override
  public boolean supportsTableCorrelationNames() throws SQLException {
    return wrapped.supportsTableCorrelationNames();
  }

  @Override
  public boolean supportsDifferentTableCorrelationNames() throws SQLException {
    return wrapped.supportsDifferentTableCorrelationNames();
  }

  @Override
  public boolean supportsExpressionsInOrderBy() throws SQLException {
    return wrapped.supportsExpressionsInOrderBy();
  }

  @Override
  public boolean supportsOrderByUnrelated() throws SQLException {
    return wrapped.supportsOrderByUnrelated();
  }

  @Override
  public boolean supportsGroupBy() throws SQLException {
    return wrapped.supportsGroupBy();
  }

  @Override
  public boolean supportsGroupByUnrelated() throws SQLException {
    return wrapped.supportsGroupByUnrelated();
  }

  @Override
  public boolean supportsGroupByBeyondSelect() throws SQLException {
    return wrapped.supportsGroupByBeyondSelect();
  }

  @Override
  public boolean supportsLikeEscapeClause() throws SQLException {
    return wrapped.supportsLikeEscapeClause();
  }

  @Override
  public boolean supportsMultipleResultSets() throws SQLException {
    return wrapped.supportsMultipleResultSets();
  }

  @Override
  public boolean supportsMultipleTransactions() throws SQLException {
    return wrapped.supportsMultipleTransactions();
  }

  @Override
  public boolean supportsNonNullableColumns() throws SQLException {
    return wrapped.supportsNonNullableColumns();
  }

  @Override
  public boolean supportsMinimumSQLGrammar() throws SQLException {
    return wrapped.supportsMinimumSQLGrammar();
  }

  @Override
  public boolean supportsCoreSQLGrammar() throws SQLException {
    return wrapped.supportsCoreSQLGrammar();
  }

  @Override
  public boolean supportsExtendedSQLGrammar() throws SQLException {
    return wrapped.supportsExtendedSQLGrammar();
  }

  @Override
  public boolean supportsANSI92EntryLevelSQL() throws SQLException {
    return wrapped.supportsANSI92EntryLevelSQL();
  }

  @Override
  public boolean supportsANSI92IntermediateSQL() throws SQLException {
    return wrapped.supportsANSI92IntermediateSQL();
  }

  @Override
  public boolean supportsANSI92FullSQL() throws SQLException {
    return wrapped.supportsANSI92FullSQL();
  }

  @Override
  public boolean supportsIntegrityEnhancementFacility() throws SQLException {
    return wrapped.supportsIntegrityEnhancementFacility();
  }

  @Override
  public boolean supportsOuterJoins() throws SQLException {
    return wrapped.supportsOuterJoins();
  }

  @Override
  public boolean supportsFullOuterJoins() throws SQLException {
    return wrapped.supportsFullOuterJoins();
  }

  @Override
  public boolean supportsLimitedOuterJoins() throws SQLException {
    return wrapped.supportsLimitedOuterJoins();
  }

  @Override
  public String getSchemaTerm() throws SQLException {
    return wrapped.getSchemaTerm();
  }

  @Override
  public String getProcedureTerm() throws SQLException {
    return wrapped.getProcedureTerm();
  }

  @Override
  public String getCatalogTerm() throws SQLException {
    return wrapped.getCatalogTerm();
  }

  @Override
  public boolean isCatalogAtStart() throws SQLException {
    return wrapped.isCatalogAtStart();
  }

  @Override
  public String getCatalogSeparator() throws SQLException {
    return wrapped.getCatalogSeparator();
  }

  @Override
  public boolean supportsSchemasInDataManipulation() throws SQLException {
    return wrapped.supportsSchemasInDataManipulation();
  }

  @Override
  public boolean supportsSchemasInProcedureCalls() throws SQLException {
    return wrapped.supportsSchemasInProcedureCalls();
  }

  @Override
  public boolean supportsSchemasInTableDefinitions() throws SQLException {
    return wrapped.supportsSchemasInTableDefinitions();
  }

  @Override
  public boolean supportsSchemasInIndexDefinitions() throws SQLException {
    return wrapped.supportsSchemasInIndexDefinitions();
  }

  @Override
  public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
    return wrapped.supportsSchemasInPrivilegeDefinitions();
  }

  @Override
  public boolean supportsCatalogsInDataManipulation() throws SQLException {
    return wrapped.supportsCatalogsInDataManipulation();
  }

  @Override
  public boolean supportsCatalogsInProcedureCalls() throws SQLException {
    return wrapped.supportsCatalogsInProcedureCalls();
  }

  @Override
  public boolean supportsCatalogsInTableDefinitions() throws SQLException {
    return wrapped.supportsCatalogsInTableDefinitions();
  }

  @Override
  public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
    return wrapped.supportsCatalogsInIndexDefinitions();
  }

  @Override
  public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
    return wrapped.supportsCatalogsInPrivilegeDefinitions();
  }

  @Override
  public boolean supportsPositionedDelete() throws SQLException {
    return wrapped.supportsPositionedDelete();
  }

  @Override
  public boolean supportsPositionedUpdate() throws SQLException {
    return wrapped.supportsPositionedUpdate();
  }

  @Override
  public boolean supportsSelectForUpdate() throws SQLException {
    return wrapped.supportsSelectForUpdate();
  }

  @Override
  public boolean supportsStoredProcedures() throws SQLException {
    return wrapped.supportsStoredProcedures();
  }

  @Override
  public boolean supportsSubqueriesInComparisons() throws SQLException {
    return wrapped.supportsSubqueriesInComparisons();
  }

  @Override
  public boolean supportsSubqueriesInExists() throws SQLException {
    return wrapped.supportsSubqueriesInExists();
  }

  @Override
  public boolean supportsSubqueriesInIns() throws SQLException {
    return wrapped.supportsSubqueriesInIns();
  }

  @Override
  public boolean supportsSubqueriesInQuantifieds() throws SQLException {
    return wrapped.supportsSubqueriesInQuantifieds();
  }

  @Override
  public boolean supportsCorrelatedSubqueries() throws SQLException {
    return wrapped.supportsCorrelatedSubqueries();
  }

  @Override
  public boolean supportsUnion() throws SQLException {
    return wrapped.supportsUnion();
  }

  @Override
  public boolean supportsUnionAll() throws SQLException {
    return wrapped.supportsUnionAll();
  }

  @Override
  public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
    return wrapped.supportsOpenCursorsAcrossCommit();
  }

  @Override
  public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
    return wrapped.supportsOpenCursorsAcrossRollback();
  }

  @Override
  public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
    return wrapped.supportsOpenStatementsAcrossCommit();
  }

  @Override
  public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
    return wrapped.supportsOpenStatementsAcrossRollback();
  }

  @Override
  public int getMaxBinaryLiteralLength() throws SQLException {
    return wrapped.getMaxBinaryLiteralLength();
  }

  @Override
  public int getMaxCharLiteralLength() throws SQLException {
    return wrapped.getMaxCharLiteralLength();
  }

  @Override
  public int getMaxColumnNameLength() throws SQLException {
    return wrapped.getMaxColumnNameLength();
  }

  @Override
  public int getMaxColumnsInGroupBy() throws SQLException {
    return wrapped.getMaxColumnsInGroupBy();
  }

  @Override
  public int getMaxColumnsInIndex() throws SQLException {
    return wrapped.getMaxColumnsInIndex();
  }

  @Override
  public int getMaxColumnsInOrderBy() throws SQLException {
    return wrapped.getMaxColumnsInOrderBy();
  }

  @Override
  public int getMaxColumnsInSelect() throws SQLException {
    return wrapped.getMaxColumnsInSelect();
  }

  @Override
  public int getMaxColumnsInTable() throws SQLException {
    return wrapped.getMaxColumnsInTable();
  }

  @Override
  public int getMaxConnections() throws SQLException {
    return wrapped.getMaxConnections();
  }

  @Override
  public int getMaxCursorNameLength() throws SQLException {
    return wrapped.getMaxCursorNameLength();
  }

  @Override
  public int getMaxIndexLength() throws SQLException {
    return wrapped.getMaxIndexLength();
  }

  @Override
  public int getMaxSchemaNameLength() throws SQLException {
    return wrapped.getMaxSchemaNameLength();
  }

  @Override
  public int getMaxProcedureNameLength() throws SQLException {
    return wrapped.getMaxProcedureNameLength();
  }

  @Override
  public int getMaxCatalogNameLength() throws SQLException {
    return wrapped.getMaxCatalogNameLength();
  }

  @Override
  public int getMaxRowSize() throws SQLException {
    return wrapped.getMaxRowSize();
  }

  @Override
  public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
    return wrapped.doesMaxRowSizeIncludeBlobs();
  }

  @Override
  public int getMaxStatementLength() throws SQLException {
    return wrapped.getMaxStatementLength();
  }

  @Override
  public int getMaxStatements() throws SQLException {
    return wrapped.getMaxStatements();
  }

  @Override
  public int getMaxTableNameLength() throws SQLException {
    return wrapped.getMaxTableNameLength();
  }

  @Override
  public int getMaxTablesInSelect() throws SQLException {
    return wrapped.getMaxTablesInSelect();
  }

  @Override
  public int getMaxUserNameLength() throws SQLException {
    return wrapped.getMaxUserNameLength();
  }

  @Override
  public int getDefaultTransactionIsolation() throws SQLException {
    return wrapped.getDefaultTransactionIsolation();
  }

  @Override
  public boolean supportsTransactions() throws SQLException {
    return wrapped.supportsTransactions();
  }

  @Override
  public boolean supportsTransactionIsolationLevel(int level) throws SQLException {
    return wrapped.supportsTransactionIsolationLevel(level);
  }

  @Override
  public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
    return wrapped.supportsDataDefinitionAndDataManipulationTransactions();
  }

  @Override
  public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
    return wrapped.supportsDataManipulationTransactionsOnly();
  }

  @Override
  public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
    return wrapped.dataDefinitionCausesTransactionCommit();
  }

  @Override
  public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
    return wrapped.dataDefinitionIgnoredInTransactions();
  }

  @Override
  public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern)
      throws SQLException {
    return wrapped.getProcedures(catalog, schemaPattern, procedureNamePattern);
  }

  @Override
  public ResultSet getProcedureColumns(String catalog, String schemaPattern,
      String procedureNamePattern, String columnNamePattern) throws SQLException {
    return wrapped.getProcedureColumns(catalog, schemaPattern, procedureNamePattern,
        columnNamePattern);
  }

  @Override
  public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern,
      String[] types) throws SQLException {
    return wrapped.getTables(catalog, schemaPattern, tableNamePattern, types);
  }

  @Override
  public ResultSet getSchemas() throws SQLException {
    return wrapped.getSchemas();
  }

  @Override
  public ResultSet getCatalogs() throws SQLException {
    return wrapped.getCatalogs();
  }

  @Override
  public ResultSet getTableTypes() throws SQLException {
    return wrapped.getTableTypes();
  }

  @Override
  public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern,
      String columnNamePattern) throws SQLException {
    return wrapped.getColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern);
  }

  @Override
  public ResultSet getColumnPrivileges(String catalog, String schema, String table,
      String columnNamePattern) throws SQLException {
    return wrapped.getColumnPrivileges(catalog, schema, table, columnNamePattern);
  }

  @Override
  public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    return wrapped.getTablePrivileges(catalog, schemaPattern, tableNamePattern);
  }

  @Override
  public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope,
      boolean nullable) throws SQLException {
    return wrapped.getBestRowIdentifier(catalog, schema, table, scope, nullable);
  }

  @Override
  public ResultSet getVersionColumns(String catalog, String schema, String table)
      throws SQLException {
    return wrapped.getVersionColumns(catalog, schema, table);
  }

  @Override
  public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
    return wrapped.getPrimaryKeys(catalog, schema, table);
  }

  @Override
  public ResultSet getImportedKeys(String catalog, String schema, String table)
      throws SQLException {
    return wrapped.getImportedKeys(catalog, schema, table);
  }

  @Override
  public ResultSet getExportedKeys(String catalog, String schema, String table)
      throws SQLException {
    return wrapped.getExportedKeys(catalog, schema, table);
  }

  @Override
  public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable,
      String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
    return wrapped.getCrossReference(parentCatalog, parentSchema, parentTable, foreignCatalog,
        foreignSchema, foreignTable);
  }

  @Override
  public ResultSet getTypeInfo() throws SQLException {
    return wrapped.getTypeInfo();
  }

  @Override
  public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique,
      boolean approximate) throws SQLException {
    return wrapped.getIndexInfo(catalog, schema, table, unique, approximate);
  }

  @Override
  public boolean supportsResultSetType(int type) throws SQLException {
    return wrapped.supportsResultSetType(type);
  }

  @Override
  public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
    return wrapped.supportsResultSetConcurrency(type, concurrency);
  }

  @Override
  public boolean ownUpdatesAreVisible(int type) throws SQLException {
    return wrapped.ownUpdatesAreVisible(type);
  }

  @Override
  public boolean ownDeletesAreVisible(int type) throws SQLException {
    return wrapped.ownDeletesAreVisible(type);
  }

  @Override
  public boolean ownInsertsAreVisible(int type) throws SQLException {
    return wrapped.ownInsertsAreVisible(type);
  }

  @Override
  public boolean othersUpdatesAreVisible(int type) throws SQLException {
    return wrapped.othersUpdatesAreVisible(type);
  }

  @Override
  public boolean othersDeletesAreVisible(int type) throws SQLException {
    return wrapped.othersDeletesAreVisible(type);
  }

  @Override
  public boolean othersInsertsAreVisible(int type) throws SQLException {
    return wrapped.othersInsertsAreVisible(type);
  }

  @Override
  public boolean updatesAreDetected(int type) throws SQLException {
    return wrapped.updatesAreDetected(type);
  }

  @Override
  public boolean deletesAreDetected(int type) throws SQLException {
    return wrapped.deletesAreDetected(type);
  }

  @Override
  public boolean insertsAreDetected(int type) throws SQLException {
    return wrapped.insertsAreDetected(type);
  }

  @Override
  public boolean supportsBatchUpdates() throws SQLException {
    return wrapped.supportsBatchUpdates();
  }

  @Override
  public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern,
      int[] types) throws SQLException {
    return wrapped.getUDTs(catalog, schemaPattern, typeNamePattern, types);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return wrapped.getConnection();
  }

  @Override
  public boolean supportsSavepoints() throws SQLException {
    return wrapped.supportsSavepoints();
  }

  @Override
  public boolean supportsNamedParameters() throws SQLException {
    return wrapped.supportsNamedParameters();
  }

  @Override
  public boolean supportsMultipleOpenResults() throws SQLException {
    return wrapped.supportsMultipleOpenResults();
  }

  @Override
  public boolean supportsGetGeneratedKeys() throws SQLException {
    return wrapped.supportsGetGeneratedKeys();
  }

  @Override
  public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern)
      throws SQLException {
    return wrapped.getSuperTypes(catalog, schemaPattern, typeNamePattern);
  }

  @Override
  public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    return wrapped.getSuperTables(catalog, schemaPattern, tableNamePattern);
  }

  @Override
  public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern,
      String attributeNamePattern) throws SQLException {
    return wrapped.getAttributes(catalog, schemaPattern, typeNamePattern, attributeNamePattern);
  }

  @Override
  public boolean supportsResultSetHoldability(int holdability) throws SQLException {
    return wrapped.supportsResultSetHoldability(holdability);
  }

  @Override
  public int getResultSetHoldability() throws SQLException {
    return wrapped.getResultSetHoldability();
  }

  @Override
  public int getDatabaseMajorVersion() throws SQLException {
    return wrapped.getDatabaseMajorVersion();
  }

  @Override
  public int getDatabaseMinorVersion() throws SQLException {
    return wrapped.getDatabaseMinorVersion();
  }

  @Override
  public int getJDBCMajorVersion() throws SQLException {
    return wrapped.getJDBCMajorVersion();
  }

  @Override
  public int getJDBCMinorVersion() throws SQLException {
    return wrapped.getJDBCMinorVersion();
  }

  @Override
  public int getSQLStateType() throws SQLException {
    return wrapped.getSQLStateType();
  }

  @Override
  public boolean locatorsUpdateCopy() throws SQLException {
    return wrapped.locatorsUpdateCopy();
  }

  @Override
  public boolean supportsStatementPooling() throws SQLException {
    return wrapped.supportsStatementPooling();
  }

  @Override
  public RowIdLifetime getRowIdLifetime() throws SQLException {
    return wrapped.getRowIdLifetime();
  }

  @Override
  public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
    return wrapped.getSchemas(catalog, schemaPattern);
  }

  @Override
  public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
    return wrapped.supportsStoredFunctionsUsingCallSyntax();
  }

  @Override
  public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
    return wrapped.autoCommitFailureClosesAllResultSets();
  }

  @Override
  public ResultSet getClientInfoProperties() throws SQLException {
    return wrapped.getClientInfoProperties();
  }

  @Override
  public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
      throws SQLException {
    return wrapped.getFunctions(catalog, schemaPattern, functionNamePattern);
  }

  @Override
  public ResultSet getFunctionColumns(String catalog, String schemaPattern,
      String functionNamePattern, String columnNamePattern) throws SQLException {
    return wrapped.getFunctionColumns(catalog, schemaPattern, functionNamePattern,
        columnNamePattern);
  }

  @Override
  public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern,
      String columnNamePattern) throws SQLException {
    return wrapped.getPseudoColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern);
  }

  @Override
  public boolean generatedKeyAlwaysReturned() throws SQLException {
    return wrapped.generatedKeyAlwaysReturned();
  }

  @Override
  public long getMaxLogicalLobSize() throws SQLException {
    return wrapped.getMaxLogicalLobSize();
  }

  @Override
  public boolean supportsRefCursors() throws SQLException {
    return wrapped.supportsRefCursors();
  }

  @Override
  public boolean supportsSharding() throws SQLException {
    return wrapped.supportsSharding();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return wrapped.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return wrapped.isWrapperFor(iface);
  }
}
