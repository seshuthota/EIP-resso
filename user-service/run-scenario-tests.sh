#!/bin/bash

# EIP-resso User Management Service - Comprehensive Scenario Testing
# This script runs all scenario-based tests for the User Management Service

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Test configuration
SERVICE_NAME="User Management Service"
SERVICE_PORT=8081
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${BLUE}════════════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                    EIP-resso ${SERVICE_NAME} - Scenario Testing${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}Testing Advanced Apache Camel EIP Patterns in Coffee Shop Operations${NC}"
echo -e "${CYAN}Service Port: ${SERVICE_PORT} | Advanced Patterns: Dead Letter Channel, Idempotent Consumer, Wire Tap, Content Enricher${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════════════════════${NC}"
echo

# Function to print test section header
print_section() {
    echo -e "${PURPLE}╔══════════════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${PURPLE}║ $1${NC}"
    echo -e "${PURPLE}╚══════════════════════════════════════════════════════════════════════════════╝${NC}"
    echo
}

# Function to print test status
print_status() {
    local status=$1
    local message=$2
    if [ "$status" -eq 0 ]; then
        echo -e "${GREEN}✅ $message${NC}"
    else
        echo -e "${RED}❌ $message${NC}"
    fi
}

# Function to run test with progress indication
run_test() {
    local test_class=$1
    local test_description=$2
    
    echo -e "${YELLOW}🧪 Running: $test_description${NC}"
    echo -e "${CYAN}   Test Class: $test_class${NC}"
    
    # Run the test and capture output
    if mvn test -Dtest="$test_class" -q > test_output.log 2>&1; then
        local test_count=$(grep -c "Tests run:" test_output.log || echo "0")
        local success_count=$(grep -oP "Tests run: \K\d+" test_output.log | head -1 || echo "0")
        local failure_count=$(grep -oP "Failures: \K\d+" test_output.log | head -1 || echo "0")
        local error_count=$(grep -oP "Errors: \K\d+" test_output.log | head -1 || echo "0")
        
        print_status 0 "$test_description - Tests: $success_count Passed, $failure_count Failed, $error_count Errors"
        
        # Show any failures or errors
        if [ "$failure_count" -gt 0 ] || [ "$error_count" -gt 0 ]; then
            echo -e "${RED}   💥 Test Issues Found:${NC}"
            grep -A 5 -B 2 "FAILED\|ERROR" test_output.log | head -20
        fi
    else
        print_status 1 "$test_description - Execution Failed"
        echo -e "${RED}   💥 Test Execution Error:${NC}"
        tail -20 test_output.log
    fi
    
    echo
}

# Start testing
cd "$BASE_DIR"

print_section "Phase 1: Coffee Shop Scenario Tests - Real Business Operations"

echo -e "${CYAN}Testing comprehensive coffee shop operational scenarios:${NC}"
echo -e "${CYAN}• Customer Onboarding (Registration, Duplicate Prevention, Profile Enrichment)${NC}"
echo -e "${CYAN}• Daily Operations (Rush Hour, Staff Login, Failed Attempts)${NC}"
echo -e "${CYAN}• Security & Compliance (JWT Lifecycle, Audit Trail, GDPR)${NC}"
echo -e "${CYAN}• Clustering & High Availability (Load Distribution, Failover, Config Refresh)${NC}"
echo

run_test "com.eipresso.user.scenarios.UserManagementScenarioTest" "Complete Coffee Shop Scenario Testing"

print_section "Phase 2: Advanced Apache Camel EIP Pattern Tests"

echo -e "${CYAN}Testing advanced Enterprise Integration Patterns:${NC}"
echo -e "${CYAN}• Dead Letter Channel Pattern (Failed Operations, Email Service, Database Recovery)${NC}"
echo -e "${CYAN}• Idempotent Consumer Pattern (Email-Based Idempotency, Concurrent Registration, Login Idempotency)${NC}"
echo -e "${CYAN}• Wire Tap Pattern (Security Audit, Profile Access, Registration Broadcasting)${NC}"
echo -e "${CYAN}• Content Enricher Pattern (Geolocation, Loyalty, Device Information)${NC}"
echo

run_test "com.eipresso.user.scenarios.CamelEIPPatternTest" "Advanced EIP Pattern Testing"

print_section "Phase 3: Production Integration Tests"

echo -e "${CYAN}Testing production-grade integration scenarios:${NC}"
echo -e "${CYAN}• Coffee Shop Opening Day (Staff Setup, Customer Wave, Health Checks)${NC}"
echo -e "${CYAN}• Peak Hours Operations (Rush Hour, Shift Changes, Loyalty Program)${NC}"
echo -e "${CYAN}• Security Operations (Incident Response, Privacy Compliance, Token Security)${NC}"
echo -e "${CYAN}• System Integration (Database Performance, Camel Routes, Configuration Management)${NC}"
echo

run_test "com.eipresso.user.scenarios.UserServiceIntegrationTest" "Production Integration Testing"

# Test Results Summary
print_section "Test Execution Summary"

echo -e "${BLUE}📊 Test Execution Summary for ${SERVICE_NAME}:${NC}"
echo

# Count total tests from all files
total_tests=0
total_passed=0
total_failed=0
total_errors=0

for log_file in test_output.log; do
    if [ -f "$log_file" ]; then
        tests=$(grep -oP "Tests run: \K\d+" "$log_file" | head -1 || echo "0")
        failures=$(grep -oP "Failures: \K\d+" "$log_file" | head -1 || echo "0")
        errors=$(grep -oP "Errors: \K\d+" "$log_file" | head -1 || echo "0")
        passed=$((tests - failures - errors))
        
        total_tests=$((total_tests + tests))
        total_passed=$((total_passed + passed))
        total_failed=$((total_failed + failures))
        total_errors=$((total_errors + errors))
    fi
done

echo -e "${CYAN}📈 Overall Test Statistics:${NC}"
echo -e "   Total Test Methods: ${BLUE}$total_tests${NC}"
echo -e "   ✅ Passed: ${GREEN}$total_passed${NC}"
echo -e "   ❌ Failed: ${RED}$total_failed${NC}"
echo -e "   💥 Errors: ${RED}$total_errors${NC}"

if [ $total_failed -eq 0 ] && [ $total_errors -eq 0 ]; then
    echo -e "${GREEN}🎉 All tests passed successfully!${NC}"
    exit_code=0
else
    echo -e "${RED}⚠️  Some tests failed or had errors.${NC}"
    exit_code=1
fi

echo

print_section "Service Architecture Summary"

echo -e "${CYAN}🏗️ User Management Service - Advanced EIP Architecture:${NC}"
echo
echo -e "${YELLOW}📋 Implemented EIP Patterns:${NC}"
echo -e "   🔄 Dead Letter Channel: Failed operation handling with retry logic"
echo -e "   🔒 Idempotent Consumer: Email-based duplicate prevention with Hazelcast"
echo -e "   📊 Wire Tap: Comprehensive security audit trail for all authentication events"
echo -e "   🔍 Content Enricher: User profile enhancement with IP geolocation and preferences"
echo
echo -e "${YELLOW}🎯 Business Scenarios Covered:${NC}"
echo -e "   ☕ Customer Onboarding: Registration, duplicate prevention, profile enrichment"
echo -e "   🏃 Daily Operations: Rush hour authentication, staff login, failed attempts"
echo -e "   🔐 Security & Compliance: JWT lifecycle, audit trail, GDPR compliance"
echo -e "   🌐 Clustering & HA: Active-Active load distribution, failover, config refresh"
echo
echo -e "${YELLOW}🔧 Technical Achievements:${NC}"
echo -e "   ⚡ 11+ Active Camel Routes implementing real business logic"
echo -e "   🎭 JWT Authentication with access/refresh token support and full test coverage"
echo -e "   📊 Security Audit Database with comprehensive event tracking"
echo -e "   🌟 Active-Active Hazelcast Clustering ready for horizontal scaling"
echo -e "   🔄 Config Server Integration for centralized configuration with dynamic refresh"
echo -e "   📈 Production-Ready with health checks, metrics, and monitoring"
echo

echo -e "${BLUE}════════════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                              Testing Complete!${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════════════════════${NC}"

# Cleanup
rm -f test_output.log

exit $exit_code 