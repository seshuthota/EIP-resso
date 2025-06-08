#!/bin/bash

# EIP-resso Configuration Management Service - Comprehensive Test Suite
# This script runs all scenario-based tests with proper Maven configuration

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  EIP-resso Configuration Management   ${NC}"
echo -e "${BLUE}     Comprehensive Test Suite          ${NC}"
echo -e "${BLUE}========================================${NC}"
echo

# Function to print section headers
print_section() {
    echo -e "${PURPLE}▶ $1${NC}"
    echo -e "${PURPLE}$(printf '%.0s─' {1..50})${NC}"
}

# Function to run a specific test method
run_test_method() {
    local test_class=$1
    local test_method=$2
    local description=$3
    
    echo -e "${CYAN}  Testing: ${description}${NC}"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if mvn -q test -Dtest="${test_class}#${test_method}" -Dspring.profiles.active=test > /dev/null 2>&1; then
        echo -e "${GREEN}    ✓ PASSED${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}    ✗ FAILED${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        # Show error details for failed tests
        echo -e "${YELLOW}    Error details:${NC}"
        mvn -q test -Dtest="${test_class}#${test_method}" -Dspring.profiles.active=test 2>&1 | tail -10 | sed 's/^/      /'
    fi
    echo
}

# Function to setup test environment
setup_test_environment() {
    print_section "Setting up Test Environment"
    
    echo -e "${CYAN}  Cleaning previous builds...${NC}"
    mvn clean -q
    
    echo -e "${CYAN}  Compiling test sources...${NC}"
    if mvn compile test-compile -q; then
        echo -e "${GREEN}    ✓ Compilation successful${NC}"
    else
        echo -e "${RED}    ✗ Compilation failed${NC}"
        echo -e "${YELLOW}    Error details:${NC}"
        mvn compile test-compile 2>&1 | tail -10 | sed 's/^/      /'
        exit 1
    fi
    
    echo -e "${CYAN}  Setting up test configuration repository...${NC}"
    # Create temporary test config repo
    TEST_CONFIG_DIR="/tmp/test-config-repo"
    if [ -d "$TEST_CONFIG_DIR" ]; then
        rm -rf "$TEST_CONFIG_DIR"
    fi
    
    # Copy our test config repo
    if [ -d "test-config-repo" ]; then
        cp -r test-config-repo "$TEST_CONFIG_DIR"
        cd "$TEST_CONFIG_DIR"
        git init -q
        git add .
        git commit -q -m "Initial test configuration"
        cd - > /dev/null
        echo -e "${GREEN}    ✓ Test configuration repository ready${NC}"
    else
        echo -e "${YELLOW}    ⚠ Test config repo not found, creating minimal setup${NC}"
        mkdir -p "$TEST_CONFIG_DIR"
        echo "test.property=value" > "$TEST_CONFIG_DIR/application.yml"
        cd "$TEST_CONFIG_DIR"
        git init -q
        git add .
        git commit -q -m "Minimal test configuration"
        cd - > /dev/null
    fi
    
    echo
}

# Function to run all scenario tests
run_scenario_tests() {
    print_section "Configuration Management Scenario Tests"
    
    # Multi-Environment Configuration Management
    echo -e "${YELLOW}Multi-Environment Configuration Management:${NC}"
    run_test_method "ConfigurationManagementScenarioTest" "testDynamicConfigurationRefresh" "Dynamic configuration refresh across services"
    run_test_method "ConfigurationManagementScenarioTest" "testEnvironmentPromotion" "Configuration promotion from staging to production"
    run_test_method "ConfigurationManagementScenarioTest" "testEncryptedConfigurationHandling" "Encrypted sensitive configuration handling"
    run_test_method "ConfigurationManagementScenarioTest" "testServiceSpecificConfigurationOverrides" "Service-specific configuration overrides"
    
    # Service Discovery Integration
    echo -e "${YELLOW}Service Discovery Integration:${NC}"
    run_test_method "ConfigurationManagementScenarioTest" "testConfigServerRegistration" "Config server registration with service discovery"
    run_test_method "ConfigurationManagementScenarioTest" "testClientServiceDiscovery" "Client service discovery of config server"
    run_test_method "ConfigurationManagementScenarioTest" "testFailoverBehavior" "Failover behavior when config server unavailable"
    run_test_method "ConfigurationManagementScenarioTest" "testBootstrapFailureHandling" "Bootstrap failure handling and recovery"
    
    # Git-Backed Configuration
    echo -e "${YELLOW}Git-Backed Configuration:${NC}"
    run_test_method "ConfigurationManagementScenarioTest" "testGitRepositoryChanges" "Git repository change detection and refresh"
    run_test_method "ConfigurationManagementScenarioTest" "testBranchBasedEnvironments" "Branch-based environment configuration"
    run_test_method "ConfigurationManagementScenarioTest" "testConfigurationValidation" "Configuration validation and error handling"
    run_test_method "ConfigurationManagementScenarioTest" "testLargeConfigurationFiles" "Large configuration file performance"
    
    # Camel Routes Integration
    echo -e "${YELLOW}Camel Routes Integration:${NC}"
    run_test_method "ConfigurationManagementScenarioTest" "testCamelRouteMonitoring" "Camel route monitoring and health checks"
    run_test_method "ConfigurationManagementScenarioTest" "testMetricsCollection" "Metrics collection and reporting"
    run_test_method "ConfigurationManagementScenarioTest" "testEndToEndConfigurationFlow" "End-to-end configuration flow"
    
    # Performance & Reliability
    echo -e "${YELLOW}Performance & Reliability:${NC}"
    run_test_method "ConfigurationManagementScenarioTest" "testConcurrentConfigurationRequests" "Concurrent configuration requests handling"
    run_test_method "ConfigurationManagementScenarioTest" "testConfigServerResilience" "Config server resilience under load"
}

# Function to run Camel route tests
run_camel_tests() {
    print_section "Camel Routes Scenario Tests"
    
    # Standard Production Routes
    echo -e "${YELLOW}Standard Production Routes:${NC}"
    run_test_method "CamelRoutesScenarioTest" "testHealthMonitoringRoute" "Health monitoring route functionality"
    run_test_method "CamelRoutesScenarioTest" "testMetricsCollectionRoute" "Metrics collection route performance"
    run_test_method "CamelRoutesScenarioTest" "testConfigurationStatusRoute" "Configuration status monitoring"
    run_test_method "CamelRoutesScenarioTest" "testAuditLoggingRoute" "Audit logging route functionality"
    
    # Production Configuration Monitoring
    echo -e "${YELLOW}Production Configuration Monitoring:${NC}"
    run_test_method "CamelRoutesScenarioTest" "testGitHubWebhookRoute" "GitHub webhook processing"
    run_test_method "CamelRoutesScenarioTest" "testGitLabWebhookRoute" "GitLab webhook processing"
    run_test_method "CamelRoutesScenarioTest" "testExternalGitMonitoringRoute" "External Git API monitoring"
    
    # Configuration Change Processing
    echo -e "${YELLOW}Configuration Change Processing:${NC}"
    run_test_method "CamelRoutesScenarioTest" "testMultiServiceNotificationRoute" "Multi-service notification processing"
    run_test_method "CamelRoutesScenarioTest" "testConfigurationHealthCheckRoute" "Configuration health check processing"
    run_test_method "CamelRoutesScenarioTest" "testAlertProcessingRoute" "Alert processing and escalation"
    
    # Error Handling & Recovery
    echo -e "${YELLOW}Error Handling & Recovery:${NC}"
    run_test_method "CamelRoutesScenarioTest" "testDeadLetterChannelHandling" "Dead letter channel error handling"
    run_test_method "CamelRoutesScenarioTest" "testRouteResilienceUnderFailure" "Route resilience under failure conditions"
    
    # Performance & Load Testing
    echo -e "${YELLOW}Performance & Load Testing:${NC}"
    run_test_method "CamelRoutesScenarioTest" "testHighVolumeRequestProcessing" "High volume request processing"
    run_test_method "CamelRoutesScenarioTest" "testTimerRoutePerformance" "Timer route performance under load"
}

# Function to run integration tests
run_integration_tests() {
    print_section "Config Server Integration Tests"
    
    # Coffee Shop Morning Startup
    echo -e "${YELLOW}Coffee Shop Morning Startup:${NC}"
    run_test_method "ConfigServerIntegrationTest" "testMorningStartupAllServices" "Morning startup - all services bootstrap"
    run_test_method "ConfigServerIntegrationTest" "testServiceDiscoveryValidation" "Service discovery validation"
    
    # Peak Hours Operation
    echo -e "${YELLOW}Peak Hours Operation:${NC}"
    run_test_method "ConfigServerIntegrationTest" "testPeakHoursConcurrentRequests" "Peak hours concurrent request handling"
    run_test_method "ConfigServerIntegrationTest" "testConfigurationCachingBehavior" "Configuration caching behavior"
    
    # Configuration Changes
    echo -e "${YELLOW}Configuration Changes:${NC}"
    run_test_method "ConfigServerIntegrationTest" "testMenuUpdateScenario" "Menu update scenario"
    run_test_method "ConfigServerIntegrationTest" "testDatabaseConnectionUpdate" "Database connection update"
    
    # Failure Recovery
    echo -e "${YELLOW}Failure Recovery:${NC}"
    run_test_method "ConfigServerIntegrationTest" "testGracefulDegradation" "Graceful degradation handling"
    run_test_method "ConfigServerIntegrationTest" "testRestartScenario" "Service restart scenario"
    
    # Multi-Environment Operations
    echo -e "${YELLOW}Multi-Environment Operations:${NC}"
    run_test_method "ConfigServerIntegrationTest" "testEnvironmentPromotionFlow" "Environment promotion flow"
    run_test_method "ConfigServerIntegrationTest" "testConfigurationIsolation" "Configuration isolation between environments"
    
    # Performance & Monitoring
    echo -e "${YELLOW}Performance & Monitoring:${NC}"
    run_test_method "ConfigServerIntegrationTest" "testMetricsIntegration" "Metrics integration and reporting"
    run_test_method "ConfigServerIntegrationTest" "testEndToEndFlow" "End-to-end configuration flow"
}

# Function to generate test report
generate_report() {
    print_section "Test Execution Summary"
    
    echo -e "${CYAN}Total Tests Run: ${TOTAL_TESTS}${NC}"
    echo -e "${GREEN}Passed: ${PASSED_TESTS}${NC}"
    echo -e "${RED}Failed: ${FAILED_TESTS}${NC}"
    
    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "${GREEN}🎉 All tests passed successfully!${NC}"
        echo -e "${GREEN}Configuration Management Service is ready for production.${NC}"
    else
        echo -e "${YELLOW}⚠ Some tests failed. Please review the errors above.${NC}"
        echo -e "${YELLOW}Success Rate: $(( (PASSED_TESTS * 100) / TOTAL_TESTS ))%${NC}"
    fi
    
    echo
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  Test Suite Execution Complete        ${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# Main execution
main() {
    # Check if we're in the right directory
    if [ ! -f "pom.xml" ]; then
        echo -e "${RED}Error: pom.xml not found. Please run this script from the config-server directory.${NC}"
        exit 1
    fi
    
    # Setup test environment
    setup_test_environment
    
    # Run all test suites
    run_scenario_tests
    run_camel_tests
    run_integration_tests
    
    # Generate final report
    generate_report
}

# Handle script arguments
case "${1:-all}" in
    "scenario")
        setup_test_environment
        run_scenario_tests
        generate_report
        ;;
    "camel")
        setup_test_environment
        run_camel_tests
        generate_report
        ;;
    "integration")
        setup_test_environment
        run_integration_tests
        generate_report
        ;;
    "all"|*)
        main
        ;;
esac 