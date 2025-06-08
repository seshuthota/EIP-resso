#!/bin/bash

# Payment Service Comprehensive Test Suite Runner
# 
# Executes all test categories with detailed reporting:
# 1. Domain Model Tests
# 2. EIP Pattern Integration Tests  
# 3. REST API Controller Tests
# 4. Performance Tests (optional)

set -e

echo "🚀 Starting Payment Service Comprehensive Test Suite"
echo "=================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test configuration
MAVEN_OPTS="-Xmx2g -XX:+UseG1GC"
TEST_PROFILES="test"
REPORTS_DIR="target/test-reports"

# Create reports directory
mkdir -p $REPORTS_DIR

# Function to print test section headers
print_section() {
    echo
    echo -e "${BLUE}=================================================="
    echo -e "🧪 $1"
    echo -e "==================================================${NC}"
    echo
}

# Function to check test results
check_test_results() {
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ $1 PASSED${NC}"
        return 0
    else
        echo -e "${RED}❌ $1 FAILED${NC}"
        return 1
    fi
}

# Start test execution
START_TIME=$(date +%s)

print_section "1. DOMAIN MODEL TESTS"
echo "Testing Payment entity business logic, state transitions, and validation..."

mvn test \
    -Dtest=PaymentModelTest \
    -Dspring.profiles.active=$TEST_PROFILES \
    -Dmaven.test.failure.ignore=false \
    -q

check_test_results "Domain Model Tests"
DOMAIN_RESULT=$?

print_section "2. EIP PATTERN INTEGRATION TESTS"
echo "Testing all 5 EIP patterns with real message flows..."

mvn test \
    -Dtest=EIPPatternIntegrationTest \
    -Dspring.profiles.active=$TEST_PROFILES \
    -Dmaven.test.failure.ignore=false \
    -q

check_test_results "EIP Pattern Integration Tests"
EIP_RESULT=$?

print_section "3. REST API CONTROLLER TESTS"
echo "Testing all REST endpoints and their integration with EIP patterns..."

mvn test \
    -Dtest=PaymentControllerTest \
    -Dspring.profiles.active=$TEST_PROFILES \
    -Dmaven.test.failure.ignore=false \
    -q

check_test_results "REST API Controller Tests"
API_RESULT=$?

# Performance tests (optional)
if [ "$RUN_PERFORMANCE_TESTS" = "true" ]; then
    print_section "4. PERFORMANCE TESTS"
    echo "Running performance and load tests..."
    
    export RUN_PERFORMANCE_TESTS=true
    
    mvn test \
        -Dtest=PerformanceTest \
        -Dspring.profiles.active=$TEST_PROFILES \
        -Dmaven.test.failure.ignore=false \
        -q
    
    check_test_results "Performance Tests"
    PERF_RESULT=$?
else
    echo -e "${YELLOW}⏭️  Performance tests skipped (set RUN_PERFORMANCE_TESTS=true to enable)${NC}"
    PERF_RESULT=0
fi

print_section "5. COMPREHENSIVE TEST COVERAGE"
echo "Running all tests together with coverage reporting..."

mvn clean test \
    -Dspring.profiles.active=$TEST_PROFILES \
    -Djacoco.skip=false \
    -q

check_test_results "Comprehensive Test Suite"
COVERAGE_RESULT=$?

# Generate test reports
print_section "6. GENERATING TEST REPORTS"

echo "Generating test coverage report..."
mvn jacoco:report -q

echo "Generating Surefire test reports..."
mvn surefire-report:report -q

# Calculate execution time
END_TIME=$(date +%s)
EXECUTION_TIME=$((END_TIME - START_TIME))

# Print final results
echo
echo -e "${BLUE}=================================================="
echo -e "📊 FINAL TEST RESULTS SUMMARY"
echo -e "==================================================${NC}"
echo

if [ $DOMAIN_RESULT -eq 0 ]; then
    echo -e "${GREEN}✅ Domain Model Tests: PASSED${NC}"
else
    echo -e "${RED}❌ Domain Model Tests: FAILED${NC}"
fi

if [ $EIP_RESULT -eq 0 ]; then
    echo -e "${GREEN}✅ EIP Pattern Tests: PASSED${NC}"
else
    echo -e "${RED}❌ EIP Pattern Tests: FAILED${NC}"
fi

if [ $API_RESULT -eq 0 ]; then
    echo -e "${GREEN}✅ REST API Tests: PASSED${NC}"
else
    echo -e "${RED}❌ REST API Tests: FAILED${NC}"
fi

if [ "$RUN_PERFORMANCE_TESTS" = "true" ]; then
    if [ $PERF_RESULT -eq 0 ]; then
        echo -e "${GREEN}✅ Performance Tests: PASSED${NC}"
    else
        echo -e "${RED}❌ Performance Tests: FAILED${NC}"
    fi
fi

if [ $COVERAGE_RESULT -eq 0 ]; then
    echo -e "${GREEN}✅ Test Coverage: GENERATED${NC}"
else
    echo -e "${RED}❌ Test Coverage: FAILED${NC}"
fi

echo
echo -e "${BLUE}📈 Test Execution Summary:${NC}"
echo "- Total Execution Time: ${EXECUTION_TIME}s"
echo "- Test Reports: $REPORTS_DIR"
echo "- Coverage Report: target/site/jacoco/index.html"
echo "- Surefire Report: target/site/surefire-report.html"

# Check overall success
OVERALL_RESULT=$((DOMAIN_RESULT + EIP_RESULT + API_RESULT + PERF_RESULT + COVERAGE_RESULT))

if [ $OVERALL_RESULT -eq 0 ]; then
    echo
    echo -e "${GREEN}🎉 ALL TESTS PASSED SUCCESSFULLY!${NC}"
    echo -e "${GREEN}Payment Service is ready for production deployment.${NC}"
    exit 0
else
    echo
    echo -e "${RED}💥 SOME TESTS FAILED!${NC}"
    echo -e "${RED}Please review the test output and fix failing tests.${NC}"
    exit 1
fi 