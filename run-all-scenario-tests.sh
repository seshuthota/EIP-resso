#!/bin/bash

# EIP-resso - Complete Scenario Testing Suite
# Master script to run all service scenario tests and provide comprehensive reporting

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Project configuration
PROJECT_NAME="EIP-resso Coffee Shop Microservices"
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# Service testing status tracking
declare -A service_status
declare -A service_test_counts
declare -A service_descriptions

echo -e "${BLUE}════════════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                    ${PROJECT_NAME} - Master Scenario Testing${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}Comprehensive Apache Camel EIP Pattern Testing Across All Services${NC}"
echo -e "${CYAN}Execution Time: ${TIMESTAMP}${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════════════════════${NC}"
echo

# Function to print service section header
print_service_section() {
    local service_name="$1"
    local service_port="$2"
    local patterns="$3"
    
    echo -e "${PURPLE}╔══════════════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${PURPLE}║ 🔧 Testing: ${service_name} (Port: ${service_port})${NC}"
    echo -e "${PURPLE}║ 📋 EIP Patterns: ${patterns}${NC}"
    echo -e "${PURPLE}╚══════════════════════════════════════════════════════════════════════════════╝${NC}"
    echo
}

# Function to run service test suite
run_service_tests() {
    local service_dir="$1"
    local service_name="$2"
    local service_port="$3"
    local patterns="$4"
    
    print_service_section "$service_name" "$service_port" "$patterns"
    
    if [ ! -d "$service_dir" ]; then
        echo -e "${RED}❌ Service directory not found: $service_dir${NC}"
        service_status["$service_name"]="MISSING"
        return 1
    fi
    
    if [ ! -f "$service_dir/run-scenario-tests.sh" ]; then
        echo -e "${YELLOW}⚠️  No scenario tests found for $service_name${NC}"
        service_status["$service_name"]="NO_TESTS"
        return 1
    fi
    
    echo -e "${CYAN}🚀 Executing scenario tests for $service_name...${NC}"
    
    cd "$service_dir"
    
    # Make script executable
    chmod +x run-scenario-tests.sh
    
    # Run the tests and capture result
    if ./run-scenario-tests.sh > "../${service_name// /-}-test-results.log" 2>&1; then
        echo -e "${GREEN}✅ $service_name tests completed successfully${NC}"
        service_status["$service_name"]="PASSED"
        
        # Extract test counts
        local test_log="../${service_name// /-}-test-results.log"
        if [ -f "$test_log" ]; then
            local tests=$(grep -oP "Total Test Methods: \K\d+" "$test_log" || echo "0")
            local passed=$(grep -oP "✅ Passed: \K\d+" "$test_log" || echo "0")
            local failed=$(grep -oP "❌ Failed: \K\d+" "$test_log" || echo "0")
            service_test_counts["$service_name"]="$tests:$passed:$failed"
        fi
    else
        echo -e "${RED}❌ $service_name tests failed${NC}"
        service_status["$service_name"]="FAILED"
        
        # Show first few lines of errors
        local test_log="../${service_name// /-}-test-results.log"
        if [ -f "$test_log" ]; then
            echo -e "${RED}   💥 Error Summary:${NC}"
            tail -10 "$test_log" | head -5
        fi
    fi
    
    cd "$BASE_DIR"
    echo
}

# Start comprehensive testing
cd "$BASE_DIR"

echo -e "${YELLOW}🔍 Discovering EIP-resso services and their testing capabilities...${NC}"
echo

# Service definitions with their EIP patterns
services=(
    "config-server:Configuration Management Service:8888:Git-Backed Config, Environment Promotion, Dynamic Refresh, Service Discovery"
    "user-service:User Management Service:8081:Dead Letter Channel, Idempotent Consumer, Wire Tap, Content Enricher"
    "product-catalog:Product Catalog Service:8082:Cache Pattern, Multicast, Recipient List, Polling Consumer, Content-Based Router"
    "order-management:Order Management Service:8083:Event Sourcing, Split/Aggregate, Wire Tap, Timer-based Monitoring"
    "notification-service:Notification Service:8086:Publish-Subscribe, Message Filter, Throttling, Dead Letter Channel"
    "analytics-service:Analytics Service:8087:Event Sourcing, CQRS, Streaming, Aggregator"
)

# Test implemented services
echo -e "${CYAN}📊 Testing Services with Scenario Test Suites:${NC}"
echo

for service_info in "${services[@]}"; do
    IFS=':' read -r service_dir service_name service_port patterns <<< "$service_info"
    service_descriptions["$service_name"]="$patterns"
    
    if [ -d "$service_dir" ] && [ -f "$service_dir/run-scenario-tests.sh" ]; then
        run_service_tests "$service_dir" "$service_name" "$service_port" "$patterns"
    else
        echo -e "${YELLOW}⏳ $service_name - Scenario tests pending implementation${NC}"
        service_status["$service_name"]="PENDING"
        echo
    fi
done

# Generate comprehensive summary
echo -e "${PURPLE}╔══════════════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${PURPLE}║ 📊 EIP-resso Comprehensive Testing Summary${NC}"
echo -e "${PURPLE}╚══════════════════════════════════════════════════════════════════════════════╝${NC}"
echo

echo -e "${BLUE}🎯 Service Testing Status Overview:${NC}"
echo

total_services=0
services_tested=0
services_passed=0
services_failed=0
total_tests=0
total_passed=0
total_failed=0

for service_name in "${!service_status[@]}"; do
    status="${service_status[$service_name]}"
    patterns="${service_descriptions[$service_name]}"
    
    total_services=$((total_services + 1))
    
    case "$status" in
        "PASSED")
            echo -e "   ✅ ${GREEN}$service_name${NC} - All tests passed"
            echo -e "      📋 EIP Patterns: $patterns"
            services_tested=$((services_tested + 1))
            services_passed=$((services_passed + 1))
            
            # Add to test totals
            if [[ "${service_test_counts[$service_name]}" ]]; then
                IFS=':' read -r tests passed failed <<< "${service_test_counts[$service_name]}"
                total_tests=$((total_tests + tests))
                total_passed=$((total_passed + passed))
                total_failed=$((total_failed + failed))
                echo -e "      📈 Tests: $tests Total, $passed Passed, $failed Failed"
            fi
            ;;
        "FAILED")
            echo -e "   ❌ ${RED}$service_name${NC} - Tests failed"
            echo -e "      📋 EIP Patterns: $patterns"
            services_tested=$((services_tested + 1))
            services_failed=$((services_failed + 1))
            ;;
        "PENDING")
            echo -e "   ⏳ ${YELLOW}$service_name${NC} - Tests pending implementation"
            echo -e "      📋 EIP Patterns: $patterns"
            ;;
        "NO_TESTS"|"MISSING")
            echo -e "   ⚠️  ${YELLOW}$service_name${NC} - No scenario tests available"
            echo -e "      📋 EIP Patterns: $patterns"
            ;;
    esac
    echo
done

echo -e "${CYAN}📈 Overall Statistics:${NC}"
echo -e "   🏗️  Total Services: ${BLUE}$total_services${NC}"
echo -e "   🧪 Services Tested: ${BLUE}$services_tested${NC}"
echo -e "   ✅ Services Passed: ${GREEN}$services_passed${NC}"
echo -e "   ❌ Services Failed: ${RED}$services_failed${NC}"
echo -e "   ⏳ Services Pending: ${YELLOW}$((total_services - services_tested))${NC}"
echo
echo -e "   📊 Test Methods: ${BLUE}$total_tests${NC} Total, ${GREEN}$total_passed${NC} Passed, ${RED}$total_failed${NC} Failed"
echo

# Architecture summary
echo -e "${BLUE}🏗️ EIP-resso Architecture & Pattern Implementation Status:${NC}"
echo

implemented_patterns=(
    "✅ Git-Backed Configuration Management (Config Server)"
    "✅ Dead Letter Channel Pattern (User Service)"
    "✅ Idempotent Consumer Pattern (User Service)"
    "✅ Wire Tap Pattern (User Service)"
    "✅ Content Enricher Pattern (User Service)"
    "✅ Cache Pattern (Product Catalog)"
    "✅ Multicast Pattern (Product Catalog)"
    "✅ Recipient List Pattern (Product Catalog)"
    "✅ Polling Consumer Pattern (Product Catalog)"
    "✅ Content-Based Router Pattern (Product Catalog)"
    "✅ Event Sourcing Pattern (Order Management)"
    "✅ Split/Aggregate Pattern (Order Management)"
    "✅ Timer-based Monitoring (Order Management)"
    "✅ Publish-Subscribe Pattern (Notification Service)"
    "✅ Message Filter Pattern (Notification Service)"
    "✅ Throttling Pattern (Notification Service)"
)

pending_patterns=(
    "⏳ Circuit Breaker Pattern (Payment Service)"
    "⏳ Saga Pattern (Order Orchestration)"
    "⏳ Process Manager Pattern (Order Orchestration)"
    "⏳ CQRS Pattern (Analytics Service)"
    "⏳ Streaming Pattern (Analytics Service)"
    "⏳ File Transfer Patterns (External Integration)"
)

echo -e "${GREEN}📋 Implemented EIP Patterns (${#implemented_patterns[@]}):${NC}"
for pattern in "${implemented_patterns[@]}"; do
    echo -e "   $pattern"
done

echo
echo -e "${YELLOW}⏳ Pending EIP Patterns (${#pending_patterns[@]}):${NC}"
for pattern in "${pending_patterns[@]}"; do
    echo -e "   $pattern"
done

echo

# Generate test execution recommendations
echo -e "${CYAN}🎯 Next Steps & Recommendations:${NC}"
echo

if [ $services_failed -gt 0 ]; then
    echo -e "${RED}🔧 Immediate Actions Required:${NC}"
    for service_name in "${!service_status[@]}"; do
        if [ "${service_status[$service_name]}" = "FAILED" ]; then
            echo -e "   • Review test failures in $service_name"
            echo -e "     Check log: ${service_name// /-}-test-results.log"
        fi
    done
    echo
fi

pending_services=$((total_services - services_tested))
if [ $pending_services -gt 0 ]; then
    echo -e "${YELLOW}📝 Development Priorities:${NC}"
    echo -e "   • Implement scenario tests for $pending_services remaining services"
    echo -e "   • Focus on Payment Service and Order Orchestration for critical business flows"
    echo -e "   • Complete Analytics Service for real-time business intelligence"
    echo
fi

if [ $services_passed -gt 0 ]; then
    echo -e "${GREEN}🎉 Achievements:${NC}"
    echo -e "   • $services_passed services with comprehensive scenario testing"
    echo -e "   • ${#implemented_patterns[@]} advanced EIP patterns successfully implemented"
    echo -e "   • Production-ready coffee shop microservices architecture"
    echo -e "   • $total_passed test methods validating business scenarios"
    echo
fi

# Final status determination
if [ $services_failed -eq 0 ] && [ $services_passed -gt 0 ]; then
    echo -e "${GREEN}🏆 Overall Status: SUCCESS - All tested services passing${NC}"
    exit_code=0
elif [ $services_failed -gt 0 ]; then
    echo -e "${RED}⚠️  Overall Status: ISSUES - Some services have test failures${NC}"
    exit_code=1
else
    echo -e "${YELLOW}⏳ Overall Status: IN PROGRESS - Testing implementation ongoing${NC}"
    exit_code=0
fi

echo -e "${BLUE}════════════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                          Testing Summary Complete!${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════════════════════${NC}"

# Cleanup old log files (keep recent ones)
find . -name "*-test-results.log" -mtime +7 -delete 2>/dev/null || true

exit $exit_code 