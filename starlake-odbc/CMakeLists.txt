cmake_minimum_required(VERSION 3.20)
project(starlake_odbc C)

set(CMAKE_C_STANDARD 11)

# Find ODBC
if(WIN32)
    # Windows ODBC is part of the system
    set(ODBC_INCLUDE_DIRS "")
    set(ODBC_LIBRARIES odbc32)
else()
    find_package(ODBC REQUIRED)
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/c
    ${ODBC_INCLUDE_DIRS}
    ${GRAALVM_INCLUDE_DIR}  # Set via -DGRAALVM_INCLUDE_DIR=...
)

# Source files
set(SOURCES
    src/main/c/starlake_odbc.c
)

# Build shared library (DLL on Windows)
add_library(starlake_odbc SHARED ${SOURCES})

# Link libraries
target_link_libraries(starlake_odbc
    ${ODBC_LIBRARIES}
    ${GRAALVM_LIBRARY}  # Set via -DGRAALVM_LIBRARY=...
)

# Windows-specific settings
if(WIN32)
    # Create .def file for exports
    set(DEF_FILE ${CMAKE_CURRENT_BINARY_DIR}/starlake_odbc.def)
    file(WRITE ${DEF_FILE} "LIBRARY starlake_odbc\nEXPORTS\n")
    # Core handle management
    file(APPEND ${DEF_FILE} "    SQLAllocHandle\n")
    file(APPEND ${DEF_FILE} "    SQLFreeHandle\n")

    # Connection functions
    file(APPEND ${DEF_FILE} "    SQLDriverConnect\n")
    file(APPEND ${DEF_FILE} "    SQLDisconnect\n")
    file(APPEND ${DEF_FILE} "    SQLSetConnectAttr\n")
    file(APPEND ${DEF_FILE} "    SQLGetConnectAttr\n")

    # Statement execution
    file(APPEND ${DEF_FILE} "    SQLExecDirect\n")
    file(APPEND ${DEF_FILE} "    SQLPrepare\n")
    file(APPEND ${DEF_FILE} "    SQLExecute\n")
    file(APPEND ${DEF_FILE} "    SQLBindParameter\n")

    # Result set handling
    file(APPEND ${DEF_FILE} "    SQLFetch\n")
    file(APPEND ${DEF_FILE} "    SQLGetData\n")
    file(APPEND ${DEF_FILE} "    SQLNumResultCols\n")
    file(APPEND ${DEF_FILE} "    SQLDescribeCol\n")
    file(APPEND ${DEF_FILE} "    SQLColAttribute\n")

    # Metadata/Catalog functions
    file(APPEND ${DEF_FILE} "    SQLTables\n")
    file(APPEND ${DEF_FILE} "    SQLColumns\n")
    file(APPEND ${DEF_FILE} "    SQLGetTypeInfo\n")

    # Transaction functions
    file(APPEND ${DEF_FILE} "    SQLEndTran\n")

    # Diagnostics
    file(APPEND ${DEF_FILE} "    SQLGetDiagRec\n")

    # Environment attributes
    file(APPEND ${DEF_FILE} "    SQLSetEnvAttr\n")
    file(APPEND ${DEF_FILE} "    SQLGetEnvAttr\n")
    
    target_sources(starlake_odbc PRIVATE ${DEF_FILE})
    set_target_properties(starlake_odbc PROPERTIES
        LINK_FLAGS "/DEF:${DEF_FILE}"
    )
endif()

# Installation
install(TARGETS starlake_odbc
    RUNTIME DESTINATION bin
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)

# Install header
install(FILES src/main/c/starlake_native.h
    DESTINATION include
)

# Print configuration
message(STATUS "Building Starlake ODBC Driver")
message(STATUS "  ODBC Include: ${ODBC_INCLUDE_DIRS}")
message(STATUS "  ODBC Library: ${ODBC_LIBRARIES}")
message(STATUS "  GraalVM Include: ${GRAALVM_INCLUDE_DIR}")
message(STATUS "  GraalVM Library: ${GRAALVM_LIBRARY}")

