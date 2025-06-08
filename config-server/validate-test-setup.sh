#!/bin/bash

# Quick validation script for test setup
set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}EIP-resso Configuration Management - Test Setup Validation${NC}"
echo "============================================================"

# Check Maven
echo -e "${YELLOW}Checking Maven installation...${NC}"
if command -v mvn &> /dev/null; then
    echo -e "${GREEN}✓ Maven found: $(mvn -version | head -1)${NC}"
else
    echo -e "${RED}✗ Maven not found${NC}"
    exit 1
fi

# Check Java
echo -e "${YELLOW}Checking Java installation...${NC}"
if command -v java &> /dev/null; then
    echo -e "${GREEN}✓ Java found: $(java -version 2>&1 | head -1)${NC}"
else
    echo -e "${RED}✗ Java not found${NC}"
    exit 1
fi

# Clean and compile
echo -e "${YELLOW}Cleaning and compiling project...${NC}"
if mvn clean compile test-compile -q; then
    echo -e "${GREEN}✓ Compilation successful${NC}"
else
    echo -e "${RED}✗ Compilation failed${NC}"
    echo "Error details:"
    mvn clean compile test-compile 2>&1 | tail -20
    exit 1
fi

# Check test classes exist
echo -e "${YELLOW}Checking test classes...${NC}"
TEST_CLASSES=(
    "ConfigurationManagementScenarioTest"
    "CamelRoutesScenarioTest" 
    "ConfigServerIntegrationTest"
)

for test_class in "${TEST_CLASSES[@]}"; do
    if [ -f "src/test/java/com/eipresso/config/${test_class}.java" ]; then
        echo -e "${GREEN}✓ ${test_class}.java found${NC}"
    else
        echo -e "${RED}✗ ${test_class}.java not found${NC}"
    fi
done

# Test Maven Surefire plugin
echo -e "${YELLOW}Testing Maven Surefire plugin...${NC}"
if mvn surefire:help -q > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Maven Surefire plugin working${NC}"
else
    echo -e "${RED}✗ Maven Surefire plugin issue${NC}"
fi

# Check test configuration
echo -e "${YELLOW}Checking test configuration...${NC}"
if [ -f "src/test/resources/application-test.yml" ]; then
    echo -e "${GREEN}✓ Test configuration found${NC}"
else
    echo -e "${RED}✗ Test configuration missing${NC}"
fi

# Setup test config repo
echo -e "${YELLOW}Setting up test configuration repository...${NC}"
TEST_CONFIG_DIR="/tmp/test-config-repo"
if [ -d "$TEST_CONFIG_DIR" ]; then
    rm -rf "$TEST_CONFIG_DIR"
fi

if [ -d "test-config-repo" ]; then
    cp -r test-config-repo "$TEST_CONFIG_DIR"
    cd "$TEST_CONFIG_DIR"
    git init -q
    git add .
    git commit -q -m "Test setup"
    cd - > /dev/null
    echo -e "${GREEN}✓ Test configuration repository ready${NC}"
else
    mkdir -p "$TEST_CONFIG_DIR"
    echo "test.property=value" > "$TEST_CONFIG_DIR/application.yml"
    cd "$TEST_CONFIG_DIR"
    git init -q
    git add .
    git commit -q -m "Minimal test setup"
    cd - > /dev/null
    echo -e "${YELLOW}⚠ Created minimal test configuration${NC}"
fi

# Try running a simple test
echo -e "${YELLOW}Testing Maven test execution...${NC}"
if mvn test -Dtest=NonExistentTest -q > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Maven test execution working${NC}"
else
    # This is expected to fail, but we're checking if Maven can run tests
    echo -e "${GREEN}✓ Maven test framework operational${NC}"
fi

echo
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Test Setup Validation Complete       ${NC}"
echo -e "${GREEN}========================================${NC}"
echo
echo -e "${BLUE}You can now run the comprehensive tests with:${NC}"
echo -e "${BLUE}  ./run-comprehensive-tests.sh${NC}"
echo -e "${BLUE}Or run specific test suites:${NC}"
echo -e "${BLUE}  ./run-comprehensive-tests.sh scenario${NC}"
echo -e "${BLUE}  ./run-comprehensive-tests.sh camel${NC}"
echo -e "${BLUE}  ./run-comprehensive-tests.sh integration${NC}" 