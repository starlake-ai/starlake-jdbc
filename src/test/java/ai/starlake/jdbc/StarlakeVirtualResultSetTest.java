package ai.starlake.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.math.BigDecimal;

public class StarlakeVirtualResultSetTest {

  private StarlakeVirtualResultSet resultSet;
  private String[] columnLabels;
  private Class<?>[] columnClasses;
  private Object[][] testData;

  @BeforeEach
  void setUp() {
    columnLabels = new String[] {"id", "name", "age", "salary", "active", "birth_date"};
    columnClasses = new Class<?>[] {Integer.class, String.class, Integer.class, BigDecimal.class,
        Boolean.class, Date.class};
    testData = new Object[][] {
        {1, "John Doe", 25, new BigDecimal("50000.00"), true, Date.valueOf("1998-05-15")},
        {2, "Jane Smith", 30, new BigDecimal("75000.50"), false, Date.valueOf("1993-12-22")},
        {3, "Bob Johnson", 35, new BigDecimal("60000.25"), true, Date.valueOf("1988-08-10")},
        {4, null, null, null, null, null} // Test null values
    };

    resultSet = new StarlakeVirtualResultSet(columnLabels, columnClasses, testData);
  }

  @Test
  @DisplayName("Constructor should validate input parameters")
  void testConstructorValidation() {
    // Test null parameters
    assertThrows(IllegalArgumentException.class,
        () -> new StarlakeVirtualResultSet(null, columnClasses, testData));
    assertThrows(IllegalArgumentException.class,
        () -> new StarlakeVirtualResultSet(columnLabels, null, testData));
    assertThrows(IllegalArgumentException.class,
        () -> new StarlakeVirtualResultSet(columnLabels, columnClasses, null));

    // Test mismatched array lengths
    String[] shortLabels = {"id", "name"};
    assertThrows(IllegalArgumentException.class,
        () -> new StarlakeVirtualResultSet(shortLabels, columnClasses, testData));

    // Test mismatched data column count
    Object[][] badData = {{1, "John"}}; // Only 2 columns but expecting 6
    assertThrows(IllegalArgumentException.class,
        () -> new StarlakeVirtualResultSet(columnLabels, columnClasses, badData));
  }

  @Test
  @DisplayName("Initial ResultSet state should be before first row")
  void testInitialState() throws SQLException {
    assertTrue(resultSet.isBeforeFirst());
    assertFalse(resultSet.isFirst());
    assertFalse(resultSet.isLast());
    assertFalse(resultSet.isAfterLast());
    assertEquals(0, resultSet.getRow());
  }

  @Test
  @DisplayName("Navigation methods should work correctly")
  void testNavigation() throws SQLException {
    // Test next() navigation
    assertTrue(resultSet.next());
    assertTrue(resultSet.isFirst());
    assertEquals(1, resultSet.getRow());

    assertTrue(resultSet.next());
    assertEquals(2, resultSet.getRow());

    assertTrue(resultSet.next());
    assertEquals(3, resultSet.getRow());

    assertTrue(resultSet.next());
    assertTrue(resultSet.isLast());
    assertEquals(4, resultSet.getRow());

    assertFalse(resultSet.next());
    assertTrue(resultSet.isAfterLast());

    // Test previous() navigation
    assertTrue(resultSet.previous());
    //@todo: assertTrue(resultSet.isLast());

    // Test first() and last()
    assertTrue(resultSet.first());
    assertTrue(resultSet.isFirst());
    assertEquals(1, resultSet.getRow());

    assertTrue(resultSet.last());
    assertTrue(resultSet.isLast());
    assertEquals(4, resultSet.getRow());

    // Test beforeFirst() and afterLast()
    resultSet.beforeFirst();
    assertTrue(resultSet.isBeforeFirst());
    assertEquals(0, resultSet.getRow());

    resultSet.afterLast();
    assertTrue(resultSet.isAfterLast());
    assertEquals(0, resultSet.getRow());
  }

  @Test
  @DisplayName("Absolute and relative positioning should work correctly")
  void testAbsoluteAndRelativePositioning() throws SQLException {
    // Test absolute positioning
    assertTrue(resultSet.absolute(2));
    assertEquals(2, resultSet.getRow());

    assertTrue(resultSet.absolute(-1)); // Last row
    assertEquals(4, resultSet.getRow());

    assertTrue(resultSet.absolute(-2)); // Second to last row
    assertEquals(3, resultSet.getRow());

    assertFalse(resultSet.absolute(10)); // Beyond last row
    assertTrue(resultSet.isAfterLast());

    assertFalse(resultSet.absolute(-10)); // Before first row
    assertTrue(resultSet.isBeforeFirst());

    // Test relative positioning
    resultSet.absolute(2);
    assertTrue(resultSet.relative(1));
    assertEquals(3, resultSet.getRow());

    assertTrue(resultSet.relative(-2));
    assertEquals(1, resultSet.getRow());
  }

  @Test
  @DisplayName("Data retrieval by column index should work correctly")
  void testDataRetrievalByIndex() throws SQLException {
    resultSet.next(); // Move to first row

    assertEquals(1, resultSet.getInt(1));
    assertEquals("John Doe", resultSet.getString(2));
    assertEquals(25, resultSet.getInt(3));
    assertEquals(new BigDecimal("50000.00"), resultSet.getBigDecimal(4));
    assertTrue(resultSet.getBoolean(5));
    assertEquals(Date.valueOf("1998-05-15"), resultSet.getDate(6));

    // Test type conversions
    assertEquals("1", resultSet.getString(1)); // int to string
    assertEquals(1.0, resultSet.getDouble(1)); // int to double
    assertEquals(25L, resultSet.getLong(3)); // int to long
  }

  @Test
  @DisplayName("Data retrieval by column label should work correctly")
  void testDataRetrievalByLabel() throws SQLException {
    resultSet.next(); // Move to first row

    assertEquals(1, resultSet.getInt("id"));
    assertEquals("John Doe", resultSet.getString("name"));
    assertEquals(25, resultSet.getInt("age"));
    assertEquals(new BigDecimal("50000.00"), resultSet.getBigDecimal("salary"));
    assertTrue(resultSet.getBoolean("active"));
    assertEquals(Date.valueOf("1998-05-15"), resultSet.getDate("birth_date"));

    // Test case-insensitive lookup
    assertEquals(1, resultSet.getInt("ID"));
    assertEquals("John Doe", resultSet.getString("NAME"));
  }

  @Test
  @DisplayName("findColumn should return correct column indices")
  void testFindColumn() throws SQLException {
    assertEquals(1, resultSet.findColumn("id"));
    assertEquals(2, resultSet.findColumn("name"));
    assertEquals(3, resultSet.findColumn("age"));
    assertEquals(4, resultSet.findColumn("salary"));
    assertEquals(5, resultSet.findColumn("active"));
    assertEquals(6, resultSet.findColumn("birth_date"));

    // Test case-insensitive
    assertEquals(1, resultSet.findColumn("ID"));
    assertEquals(2, resultSet.findColumn("Name"));

    // Test non-existent column
    assertThrows(SQLException.class, () -> resultSet.findColumn("nonexistent"));
  }

  @Test
  @DisplayName("Null handling should work correctly")
  void testNullHandling() throws SQLException {
    resultSet.absolute(4); // Move to row with null values

    assertNull(resultSet.getString("name"));
    assertTrue(resultSet.wasNull());

    assertEquals(0, resultSet.getInt("age"));
    assertTrue(resultSet.wasNull());

    assertNull(resultSet.getBigDecimal("salary"));
    assertTrue(resultSet.wasNull());

    assertFalse(resultSet.getBoolean("active"));
    assertTrue(resultSet.wasNull());

    assertNull(resultSet.getDate("birth_date"));
    assertTrue(resultSet.wasNull());

    // Test non-null value resets wasNull
    resultSet.first();
    assertEquals(1, resultSet.getInt("id"));
    assertFalse(resultSet.wasNull());
  }

  @Test
  @DisplayName("getObject with Class parameter should work correctly")
  void testGetObjectWithClass() throws SQLException {
    resultSet.next();

    assertEquals(Integer.valueOf(1), resultSet.getObject(1, Integer.class));
    assertEquals("John Doe", resultSet.getObject(2, String.class));
    assertEquals(Boolean.TRUE, resultSet.getObject(5, Boolean.class));

    // Test type conversion
    assertEquals("1", resultSet.getObject(1, String.class));
    assertEquals(Long.valueOf(1), resultSet.getObject(1, Long.class));
    assertEquals(Double.valueOf(1.0), resultSet.getObject(1, Double.class));

    // Test invalid conversion
    assertThrows(SQLException.class, () -> resultSet.getObject(2, Integer.class)); // String to
                                                                                   // Integer
                                                                                   // without
                                                                                   // parsing
  }

  @Test
  @DisplayName("ResultSetMetaData should provide correct information")
  void testMetaData() throws SQLException {
    ResultSetMetaData metaData = resultSet.getMetaData();

    assertEquals(6, metaData.getColumnCount());

    assertEquals("id", metaData.getColumnLabel(1));
    assertEquals("name", metaData.getColumnLabel(2));
    assertEquals("age", metaData.getColumnLabel(3));
    assertEquals("salary", metaData.getColumnLabel(4));
    assertEquals("active", metaData.getColumnLabel(5));
    assertEquals("birth_date", metaData.getColumnLabel(6));

    assertEquals(Types.INTEGER, metaData.getColumnType(1));
    assertEquals(Types.VARCHAR, metaData.getColumnType(2));
    assertEquals(Types.INTEGER, metaData.getColumnType(3));
    assertEquals(Types.DECIMAL, metaData.getColumnType(4));
    assertEquals(Types.BOOLEAN, metaData.getColumnType(5));
    assertEquals(Types.DATE, metaData.getColumnType(6));

    assertEquals("Integer", metaData.getColumnTypeName(1));
    assertEquals("String", metaData.getColumnTypeName(2));
    assertEquals("BigDecimal", metaData.getColumnTypeName(4));

    assertEquals("java.lang.Integer", metaData.getColumnClassName(1));
    assertEquals("java.lang.String", metaData.getColumnClassName(2));
  }

  @Test
  @DisplayName("ResultSet characteristics should be correct")
  void testResultSetCharacteristics() throws SQLException {
    assertEquals(ResultSet.TYPE_SCROLL_INSENSITIVE, resultSet.getType());
    assertEquals(ResultSet.CONCUR_READ_ONLY, resultSet.getConcurrency());
    assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, resultSet.getHoldability());
    assertEquals(ResultSet.FETCH_FORWARD, resultSet.getFetchDirection());
    assertEquals(0, resultSet.getFetchSize());

    // Test setting fetch direction and size
    resultSet.setFetchDirection(ResultSet.FETCH_REVERSE);
    resultSet.setFetchSize(100);

    // Invalid fetch direction should throw exception
    assertThrows(SQLException.class, () -> resultSet.setFetchDirection(999));

    // Negative fetch size should throw exception
    assertThrows(SQLException.class, () -> resultSet.setFetchSize(-1));
  }

  @Test
  @DisplayName("Update operations should throw SQLException")
  void testUpdateOperationsNotSupported() throws SQLException {
    resultSet.next();

    assertThrows(SQLException.class, () -> resultSet.updateString(1, "test"));
    assertThrows(SQLException.class, () -> resultSet.updateInt("id", 999));
    assertThrows(SQLException.class, () -> resultSet.updateRow());
    assertThrows(SQLException.class, () -> resultSet.insertRow());
    assertThrows(SQLException.class, () -> resultSet.deleteRow());
    assertThrows(SQLException.class, () -> resultSet.moveToInsertRow());

    assertFalse(resultSet.rowUpdated());
    assertFalse(resultSet.rowInserted());
    assertFalse(resultSet.rowDeleted());
  }

  @Test
  @DisplayName("Invalid column access should throw SQLException")
  void testInvalidColumnAccess() throws SQLException {
    resultSet.next();

    // Invalid column index
    assertThrows(SQLException.class, () -> resultSet.getString(0));
    assertThrows(SQLException.class, () -> resultSet.getString(10));

    // Invalid column label
    assertThrows(SQLException.class, () -> resultSet.getString("invalid_column"));

    // Access when not on valid row
    resultSet.beforeFirst();
    assertThrows(SQLException.class, () -> resultSet.getString(1));

    resultSet.afterLast();
    assertThrows(SQLException.class, () -> resultSet.getString(1));
  }

  @Test
  @DisplayName("Closed ResultSet should throw SQLException")
  void testClosedResultSet() throws SQLException {
    assertFalse(resultSet.isClosed());

    resultSet.close();
    assertTrue(resultSet.isClosed());

    // Operations on closed ResultSet should throw SQLException
    assertThrows(SQLException.class, () -> resultSet.next());
    assertThrows(SQLException.class, () -> resultSet.getString(1));
    assertThrows(SQLException.class, () -> resultSet.findColumn("id"));
    assertThrows(SQLException.class, () -> resultSet.getMetaData());
    assertThrows(SQLException.class, () -> resultSet.wasNull());
  }

  @Test
  @DisplayName("Unsupported operations should throw SQLException")
  void testUnsupportedOperations() throws SQLException {
    resultSet.next();

    assertThrows(SQLException.class, () -> resultSet.getBytes(1));
    assertThrows(SQLException.class, () -> resultSet.getAsciiStream(1));
    assertThrows(SQLException.class, () -> resultSet.getBinaryStream(1));
    assertThrows(SQLException.class, () -> resultSet.getCharacterStream(1));
    assertThrows(SQLException.class, () -> resultSet.getBlob(1));
    assertThrows(SQLException.class, () -> resultSet.getClob(1));
    assertThrows(SQLException.class, () -> resultSet.getArray(1));
    assertThrows(SQLException.class, () -> resultSet.getRef(1));
    assertThrows(SQLException.class, () -> resultSet.getURL(1));
    assertThrows(SQLException.class, () -> resultSet.getSQLXML(1));
    assertThrows(SQLException.class, () -> resultSet.getNClob(1));
    assertThrows(SQLException.class, () -> resultSet.getRowId(1));
    assertThrows(SQLException.class, () -> resultSet.getCursorName());
  }

  @Test
  @DisplayName("Wrapper interface should work correctly")
  void testWrapperInterface() throws SQLException {
    assertTrue(resultSet.isWrapperFor(StarlakeVirtualResultSet.class));
    assertTrue(resultSet.isWrapperFor(ResultSet.class));
    assertFalse(resultSet.isWrapperFor(String.class));

    StarlakeVirtualResultSet unwrapped = resultSet.unwrap(StarlakeVirtualResultSet.class);
    assertSame(resultSet, unwrapped);

    assertThrows(SQLException.class, () -> resultSet.unwrap(String.class));
  }

  @Test
  @DisplayName("Empty ResultSet should handle navigation correctly")
  void testEmptyResultSet() throws SQLException {
    String[] emptyColumns = {"id", "name"};
    Class<?>[] emptyTypes = {Integer.class, String.class};
    Object[][] emptyData = {};

    try (StarlakeVirtualResultSet emptyRS =
        new StarlakeVirtualResultSet(emptyColumns, emptyTypes, emptyData)) {
      assertTrue(emptyRS.isBeforeFirst());
      //@todo: assertFalse(emptyRS.isAfterLast());
      assertFalse(emptyRS.next());
      assertFalse(emptyRS.first());
      assertFalse(emptyRS.last());
      assertEquals(0, emptyRS.getRow());

      emptyRS.afterLast();
      assertTrue(emptyRS.isAfterLast());
      assertFalse(emptyRS.previous());
    }
  }
}
