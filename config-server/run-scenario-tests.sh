#!/bin/bash

# EIP-resso Configuration Management Service - Comprehensive Scenario Testing
# This script runs all scenario-based tests for the Configuration Management Service

set -e

echo "🧪 =========================================================================="
echo "🧪 EIP-RESSO CONFIGURATION MANAGEMENT SERVICE - SCENARIO-BASED TESTING"
echo "🧪 =========================================================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test configuration
TEST_PROFILE="test"
MAVEN_OPTS="-Xmx1024m -XX:MaxPermSize=256m"

echo -e "${BLUE}📋 Test Configuration:${NC}"
echo "   • Test Profile: $TEST_PROFILE"
echo "   • Maven Options: $MAVEN_OPTS"
echo "   • Java Version: $(java -version 2>&1 | head -n 1)"
echo ""

# Function to print test section headers
print_section() {
    echo -e "${YELLOW}🧪 ========================================${NC}"
    echo -e "${YELLOW}🧪 $1${NC}"
    echo -e "${YELLOW}🧪 ========================================${NC}"
    echo ""
}

# Function to run specific test class
run_test_class() {
    local test_class=$1
    local description=$2
    
    echo -e "${BLUE}🔍 Running: $description${NC}"
    echo "   Test Class: $test_class"
    echo ""
    
    if mvn test -Dtest="$test_class" -Dspring.profiles.active="$TEST_PROFILE" -q; then
        echo -e "${GREEN}✅ PASSED: $description${NC}"
        return 0
    else
        echo -e "${RED}❌ FAILED: $description${NC}"
        return 1
    fi
}

# Function to run specific test method
run_test_method() {
    local test_class=$1
    local test_method=$2
    local description=$3
    
    echo -e "${BLUE}🔍 Running: $description${NC}"
    echo "   Test: $test_class#$test_method"
    echo ""
    
    if mvn test -Dtest="$test_class#$test_method" -Dspring.profiles.active="$TEST_PROFILE" -q; then
        echo -e "${GREEN}✅ PASSED: $description${NC}"
        return 0
    else
        echo -e "${RED}❌ FAILED: $description${NC}"
        return 1
    fi
}

# Initialize test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Start testing
echo -e "${GREEN}🚀 Starting Configuration Management Service Scenario Testing...${NC}"
echo ""

# ============================================================================
# SCENARIO 1: Multi-Environment Configuration Management
# ============================================================================
print_section "SCENARIO 1: Multi-Environment Configuration Management"

echo "☕ Testing coffee shop database configuration changes..."
if run_test_method "ConfigurationManagementScenarioTest" "testDynamicConfigRefresh_CoffeeShopDatabaseUrlChange" "Dynamic Config Refresh - Database URL Change"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing environment promotion from staging to production..."
if run_test_method "ConfigurationManagementScenarioTest" "testEnvironmentPromotion_StagingToProduction" "Environment Promotion - Staging to Production"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing configuration encryption for payment gateway secrets..."
if run_test_method "ConfigurationManagementScenarioTest" "testConfigurationEncryption_PaymentGatewaySecrets" "Configuration Encryption - Payment Gateway Secrets"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing service-specific Redis connection override..."
if run_test_method "ConfigurationManagementScenarioTest" "testServiceSpecificOverride_RedisConnection" "Service-Specific Override - Redis Connection"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

# ============================================================================
# SCENARIO 2: Service Discovery Integration Testing
# ============================================================================
print_section "SCENARIO 2: Service Discovery Integration Testing"

echo "☕ Testing config server registration for service discovery..."
if run_test_method "ConfigurationManagementScenarioTest" "testConfigServerRegistration_ServiceDiscovery" "Config Server Registration - Service Discovery"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing client configuration discovery..."
if run_test_method "ConfigurationManagementScenarioTest" "testClientConfigurationDiscovery_UserServiceConnection" "Client Configuration Discovery - User Service Connection"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing failover behavior during config server downtime..."
if run_test_method "ConfigurationManagementScenarioTest" "testFailoverBehavior_ConfigServerDowntime" "Failover Behavior - Config Server Downtime"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing bootstrap failure handling..."
if run_test_method "ConfigurationManagementScenarioTest" "testBootstrapFailure_InvalidConfigServerUrl" "Bootstrap Failure - Invalid Config Server URL"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

# ============================================================================
# SCENARIO 3: Git-Backed Configuration Scenarios
# ============================================================================
print_section "SCENARIO 3: Git-Backed Configuration Scenarios"

echo "☕ Testing Git repository changes for coffee shop menu updates..."
if run_test_method "ConfigurationManagementScenarioTest" "testGitRepositoryChanges_MenuUpdatePush" "Git Repository Changes - Menu Update Push"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing branch-based environments..."
if run_test_method "ConfigurationManagementScenarioTest" "testBranchBasedEnvironments_DevStagingProduction" "Branch-Based Environments - Dev/Staging/Production"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing configuration validation for invalid YAML..."
if run_test_method "ConfigurationManagementScenarioTest" "testConfigurationValidation_InvalidYamlDetection" "Configuration Validation - Invalid YAML Detection"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing large configuration files performance..."
if run_test_method "ConfigurationManagementScenarioTest" "testLargeConfigurationFiles_PerformanceValidation" "Large Configuration Files - Performance Validation"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

# ============================================================================
# SCENARIO 4: Camel Routes Integration Testing
# ============================================================================
print_section "SCENARIO 4: Camel Routes Integration Testing"

echo "☕ Testing Camel routes integration..."
if run_test_method "ConfigurationManagementScenarioTest" "testCamelRoutesIntegration_ConfigurationMonitoring" "Camel Routes Integration - Configuration Monitoring"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing metrics collection..."
if run_test_method "ConfigurationManagementScenarioTest" "testMetricsCollection_ConfigServerMetrics" "Metrics Collection - Config Server Metrics"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing end-to-end configuration flow..."
if run_test_method "ConfigurationManagementScenarioTest" "testEndToEndConfigurationFlow_CompleteCoffeeShopSetup" "End-to-End Configuration Flow - Complete Coffee Shop Setup"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

# ============================================================================
# SCENARIO 5: Performance and Reliability Testing
# ============================================================================
print_section "SCENARIO 5: Performance and Reliability Testing"

echo "☕ Testing concurrent configuration requests during rush hour..."
if run_test_method "ConfigurationManagementScenarioTest" "testConcurrentConfigurationRequests_RushHourSimulation" "Concurrent Configuration Requests - Rush Hour Simulation"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

echo "☕ Testing configuration server resilience..."
if run_test_method "ConfigurationManagementScenarioTest" "testConfigurationServerResilience_ErrorRecovery" "Configuration Server Resilience - Error Recovery"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

# ============================================================================
# CAMEL ROUTES SPECIFIC TESTING
# ============================================================================
print_section "CAMEL ROUTES SPECIFIC TESTING"

echo "☕ Running comprehensive Camel routes scenario tests..."
if run_test_class "CamelRoutesScenarioTest" "Camel Routes Scenario Testing"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

# ============================================================================
# INTEGRATION TESTING
# ============================================================================
print_section "INTEGRATION TESTING"

echo "☕ Running comprehensive integration tests..."
if run_test_class "ConfigServerIntegrationTest" "Configuration Server Integration Testing"; then
    ((PASSED_TESTS++))
else
    ((FAILED_TESTS++))
fi
((TOTAL_TESTS++))
echo ""

# ============================================================================
# TEST RESULTS SUMMARY
# ============================================================================
print_section "TEST RESULTS SUMMARY"

echo -e "${BLUE}📊 Configuration Management Service Test Results:${NC}"
echo "   • Total Tests: $TOTAL_TESTS"
echo -e "   • Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "   • Failed: ${RED}$FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo ""
    echo -e "${GREEN}🎉 ALL TESTS PASSED! Configuration Management Service is ready for production.${NC}"
    echo ""
    echo -e "${GREEN}✅ SCENARIO TESTING COMPLETE:${NC}"
    echo "   ✅ Multi-Environment Configuration Management"
    echo "   ✅ Service Discovery Integration"
    echo "   ✅ Git-Backed Configuration Scenarios"
    echo "   ✅ Camel Routes Integration"
    echo "   ✅ Performance and Reliability"
    echo "   ✅ Integration Testing"
    echo ""
    echo -e "${BLUE}🚀 The Configuration Management Service has successfully passed all scenario-based tests!${NC}"
    echo -e "${BLUE}   Ready for coffee shop production deployment.${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}❌ SOME TESTS FAILED! Please review the failed tests above.${NC}"
    echo ""
    echo -e "${YELLOW}📋 Next Steps:${NC}"
    echo "   1. Review failed test output above"
    echo "   2. Fix any configuration or code issues"
    echo "   3. Re-run specific failed tests"
    echo "   4. Run full test suite again"
    echo ""
    exit 1
fi 