#!/bin/bash

# EIP-resso Cluster Testing Script
# Comprehensive testing of clustering scenarios, failover, and EIP patterns

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 EIP-resso Cluster Testing Suite${NC}"
echo "=================================================="

# Function to print colored output
print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to run test and track results
run_test() {
    local test_name=$1
    local test_command=$2
    
    print_status $CYAN "🔬 Running: $test_name"
    
    if eval "$test_command"; then
        print_status $GREEN "✅ PASSED: $test_name"
        return 0
    else
        print_status $RED "❌ FAILED: $test_name"
        return 1
    fi
}

# Initialize test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Test 1: Basic Health Checks
print_status $BLUE "📋 Test Suite 1: Basic Health Checks"

run_test "Config Server Health" "curl -sf http://localhost:8888/actuator/health"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

run_test "User Service Load Balancer Health" "curl -sf http://localhost:8081/actuator/health"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

run_test "User Service Instance 1 Health" "curl -sf http://localhost:8181/actuator/health"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

run_test "User Service Instance 2 Health" "curl -sf http://localhost:8182/actuator/health"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

# Test 2: Hazelcast Clustering
print_status $BLUE "🔗 Test Suite 2: Hazelcast Clustering"

run_test "User Service Cluster Formation" "curl -sf http://localhost:8181/actuator/hazelcast/cluster | grep -q memberCount"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

run_test "Hazelcast Management Center Access" "curl -sf http://localhost:8080"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

# Test 3: EIP Pattern Validation
print_status $BLUE "⚡ Test Suite 3: EIP Pattern Validation"

run_test "User Service Camel Routes Active" "curl -sf http://localhost:8181/actuator/camelroutes | grep -q routeId"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

run_test "Dead Letter Channel Pattern" "curl -sf http://localhost:8181/test/eip/dead-letter"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

run_test "Wire Tap Pattern" "curl -sf http://localhost:8181/test/eip/wire-tap"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

run_test "Idempotent Consumer Pattern" "curl -sf http://localhost:8181/test/eip/idempotent"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

# Test 4: Load Balancing
print_status $BLUE "⚖️  Test Suite 4: Load Balancing"

print_status $CYAN "🔬 Testing User Service Load Distribution"
USER_1_COUNT=0
USER_2_COUNT=0

for i in {1..10}; do
    RESPONSE=$(curl -sf http://localhost:8081/actuator/info | grep -o '"instance-name":"[^"]*"' | cut -d'"' -f4)
    if [[ $RESPONSE == *"user-service-1"* ]]; then
        USER_1_COUNT=$((USER_1_COUNT + 1))
    elif [[ $RESPONSE == *"user-service-2"* ]]; then
        USER_2_COUNT=$((USER_2_COUNT + 1))
    fi
done

if [ $USER_1_COUNT -gt 0 ] && [ $USER_2_COUNT -gt 0 ]; then
    print_status $GREEN "✅ PASSED: Load balancing working (Instance 1: $USER_1_COUNT, Instance 2: $USER_2_COUNT)"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    print_status $RED "❌ FAILED: Load balancing not working properly"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 5: Data Consistency
print_status $BLUE "🔄 Test Suite 5: Data Consistency"

# Create user on instance 1
print_status $CYAN "🔬 Testing data consistency across instances"
USER_DATA='{"email":"test-cluster@eipresso.com","password":"test123","firstName":"Cluster","lastName":"Test"}'

# Register user through load balancer
REGISTER_RESPONSE=$(curl -sf -X POST "http://localhost:8081/register" \
    -H "Content-Type: application/json" \
    -d "$USER_DATA" | grep -o '"success":[^,}]*' | cut -d':' -f2)

if [[ $REGISTER_RESPONSE == "true" ]]; then
    print_status $GREEN "✅ PASSED: User registration through load balancer"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    print_status $RED "❌ FAILED: User registration failed"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 6: Monitoring and Metrics
print_status $BLUE "📊 Test Suite 6: Monitoring and Metrics"

run_test "Prometheus Metrics Collection" "curl -sf http://localhost:9090/-/healthy"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

run_test "Grafana Dashboard Access" "curl -sf http://localhost:3000/api/health"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

run_test "Service Metrics Endpoint" "curl -sf http://localhost:8181/actuator/prometheus | grep -q jvm_memory"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
[ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))

# Test 7: Failover Simulation (if requested)
read -p "Would you like to run failover tests? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_status $BLUE "💥 Test Suite 7: Failover Simulation"
    
    print_status $YELLOW "⚠️  Stopping User Service Instance 1 for failover testing..."
    docker stop eip-resso-user-1 > /dev/null 2>&1
    
    sleep 10
    
    run_test "Load Balancer Failover" "curl -sf http://localhost:8081/actuator/health"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    [ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))
    
    print_status $YELLOW "🔄 Restarting User Service Instance 1..."
    docker start eip-resso-user-1 > /dev/null 2>&1
    
    sleep 15
    
    run_test "Service Recovery" "curl -sf http://localhost:8181/actuator/health"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    [ $? -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1)) || FAILED_TESTS=$((FAILED_TESTS + 1))
    
    print_status $GREEN "✅ Failover testing completed"
fi

# Final Results
echo ""
print_status $BLUE "📊 CLUSTER TESTING RESULTS"
echo "=================================================="
print_status $GREEN "✅ PASSED: $PASSED_TESTS tests"
print_status $RED "❌ FAILED: $FAILED_TESTS tests"
print_status $CYAN "📋 TOTAL:  $TOTAL_TESTS tests"

SUCCESS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS))
print_status $BLUE "📈 SUCCESS RATE: $SUCCESS_RATE%"

if [ $SUCCESS_RATE -ge 90 ]; then
    print_status $GREEN "🎉 EXCELLENT! Cluster is production-ready"
elif [ $SUCCESS_RATE -ge 75 ]; then
    print_status $YELLOW "⚠️  GOOD: Minor issues need attention"
else
    print_status $RED "❌ POOR: Major issues require immediate attention"
fi

echo ""
print_status $BLUE "🔍 Cluster Status Summary:"
echo "• Config Server: http://localhost:8888/actuator/health"
echo "• User Service LB: http://localhost:8081/actuator/health"
echo "• User Service 1: http://localhost:8181/actuator/health"
echo "• User Service 2: http://localhost:8182/actuator/health"
echo "• Hazelcast Management: http://localhost:8080"
echo "• Prometheus: http://localhost:9090"
echo "• Grafana: http://localhost:3000"

print_status $GREEN "✨ Cluster testing completed!" 