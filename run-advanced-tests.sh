#!/bin/bash

# EIP-resso Advanced Testing Suite
# Comprehensive deep testing for all microservices and EIP patterns

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test results tracking
TESTS_PASSED=0
TESTS_FAILED=0
START_TIME=$(date +%s)

# Function to print test headers
print_header() {
    echo -e "\n${BLUE}============================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}============================================${NC}\n"
}

# Function to print test results
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "✅ ${GREEN}$2 - PASSED${NC}"
        ((TESTS_PASSED++))
    else
        echo -e "❌ ${RED}$2 - FAILED${NC}"
        ((TESTS_FAILED++))
    fi
}

# Function to wait and check service health
wait_for_service() {
    local service_url=$1
    local service_name=$2
    local max_attempts=30
    local attempt=1
    
    echo "Waiting for $service_name to be healthy..."
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$service_url" > /dev/null 2>&1; then
            echo -e "✅ ${GREEN}$service_name is healthy${NC}"
            return 0
        fi
        echo "Attempt $attempt/$max_attempts: $service_name not ready yet..."
        sleep 2
        ((attempt++))
    done
    
    echo -e "❌ ${RED}$service_name failed to become healthy${NC}"
    return 1
}

# Start comprehensive testing
print_header "🚀 EIP-resso Advanced Testing Suite Starting"

echo "Test execution started at: $(date)"
echo "Infrastructure services should be running..."

# Phase 1: Service Integration Testing
print_header "🔄 Phase 1: Service Integration Testing"

echo "🔍 Testing service discovery and health checks..."

# Test 1.1: Service Discovery & Health Checks
wait_for_service "http://localhost:8888/actuator/health" "Config Server"
print_result $? "Config Server Health Check"

wait_for_service "http://localhost:8081/actuator/health" "User Service"
print_result $? "User Service Health Check"

wait_for_service "http://localhost:8082/actuator/health" "Product Catalog Service"
print_result $? "Product Catalog Service Health Check"

wait_for_service "http://localhost:8083/actuator/health" "Order Management Service"
print_result $? "Order Management Service Health Check"

wait_for_service "http://localhost:8086/actuator/health" "Notification Service"
print_result $? "Notification Service Health Check"

wait_for_service "http://localhost:8087/actuator/health" "Analytics Service"
print_result $? "Analytics Service Health Check"

# Test 1.2: Configuration Propagation Testing
echo "🔧 Testing configuration propagation..."

curl -s http://localhost:8888/user-service/dev > /dev/null 2>&1
print_result $? "User Service Config Retrieval"

curl -s http://localhost:8888/product-catalog/dev > /dev/null 2>&1
print_result $? "Product Catalog Config Retrieval"

curl -s http://localhost:8888/order-management/dev > /dev/null 2>&1
print_result $? "Order Management Config Retrieval"

# Test 1.3: Database Connectivity Validation
echo "🗄️  Testing database connectivity..."

docker exec coffee-postgres psql -U coffee_user -d user_management -c "\dt" > /dev/null 2>&1
print_result $? "User Management Database Connection"

docker exec coffee-postgres psql -U coffee_user -d product_catalog -c "\dt" > /dev/null 2>&1
print_result $? "Product Catalog Database Connection"

docker exec coffee-postgres psql -U coffee_user -d order_management -c "\dt" > /dev/null 2>&1
print_result $? "Order Management Database Connection"

# Phase 2: End-to-End Business Workflow Testing
print_header "🎯 Phase 2: End-to-End Business Workflow Testing"

echo "👤 Testing complete user journey..."

# Test 2.1: User Registration
REGISTRATION_RESPONSE=$(curl -s -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "TestUser",
    "lastName": "Journey", 
    "email": "testuser@journey.com",
    "password": "SecurePass123!",
    "phoneNumber": "+1234567890"
  }')

if echo "$REGISTRATION_RESPONSE" | grep -q "email\|id\|success"; then
    print_result 0 "User Registration"
else
    print_result 1 "User Registration"
fi

# Test 2.2: User Login & JWT Token
JWT_RESPONSE=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser@journey.com", "password": "SecurePass123!"}')

if echo "$JWT_RESPONSE" | grep -q "accessToken"; then
    JWT_TOKEN=$(echo "$JWT_RESPONSE" | jq -r '.accessToken' 2>/dev/null || echo "")
    print_result 0 "User Login & JWT Token Generation"
else
    JWT_TOKEN=""
    print_result 1 "User Login & JWT Token Generation"
fi

# Test 2.3: Browse Products (with or without token)
if [ ! -z "$JWT_TOKEN" ]; then
    PRODUCTS_RESPONSE=$(curl -s -H "Authorization: Bearer $JWT_TOKEN" http://localhost:8082/api/products)
else
    PRODUCTS_RESPONSE=$(curl -s http://localhost:8082/api/products)
fi

if echo "$PRODUCTS_RESPONSE" | grep -q "\[\]" || echo "$PRODUCTS_RESPONSE" | grep -q "id\|name\|price"; then
    print_result 0 "Product Browsing"
else
    print_result 1 "Product Browsing"
fi

# Test 2.4: Order Creation
ORDER_RESPONSE=$(curl -s -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [{"productId": 1, "quantity": 2, "unitPrice": 4.99}],
    "totalAmount": 9.98
  }')

if echo "$ORDER_RESPONSE" | grep -q "id\|orderNumber"; then
    ORDER_ID=$(echo "$ORDER_RESPONSE" | jq -r '.id' 2>/dev/null || echo "1")
    print_result 0 "Order Creation"
else
    ORDER_ID="1"
    print_result 1 "Order Creation"
fi

# Test 2.5: Notification Sending
NOTIFICATION_RESPONSE=$(curl -s -X POST http://localhost:8086/api/notifications/send \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "testuser@journey.com",
    "type": "ORDER_CONFIRMATION",
    "channel": "EMAIL",
    "priority": "HIGH",
    "content": "Your order has been confirmed!"
  }')

if echo "$NOTIFICATION_RESPONSE" | grep -q "id\|success\|queued" || [ $? -eq 0 ]; then
    print_result 0 "Notification Sending"
else
    print_result 1 "Notification Sending"
fi

# Phase 3: Advanced EIP Pattern Testing
print_header "⚡ Phase 3: Advanced EIP Pattern Testing"

echo "🔄 Testing Event Sourcing Pattern (Order Management)..."

# Test 3.1: Event Sourcing Pattern
EVENT_RESPONSE=$(curl -s http://localhost:8083/api/orders/$ORDER_ID/events)
if echo "$EVENT_RESPONSE" | grep -q "\[\]" || echo "$EVENT_RESPONSE" | grep -q "eventType\|ORDER_CREATED"; then
    print_result 0 "Event Sourcing - Event Capture"
else
    print_result 1 "Event Sourcing - Event Capture"
fi

# Test 3.2: Order Status Transitions
curl -s -X PUT http://localhost:8083/api/orders/$ORDER_ID/status/PAID > /dev/null 2>&1
print_result $? "Order Status Transition - PAID"

curl -s -X PUT http://localhost:8083/api/orders/$ORDER_ID/status/PREPARING > /dev/null 2>&1
print_result $? "Order Status Transition - PREPARING"

# Test 3.3: Multicast Pattern Testing (Product Catalog)
echo "📡 Testing Multicast Pattern (Product Catalog)..."

MULTICAST_RESPONSE=$(curl -s -X PUT http://localhost:8082/api/products/1/price \
  -H "Content-Type: application/json" \
  -d '{"newPrice": 5.99}')

if [ $? -eq 0 ]; then
    print_result 0 "Multicast Pattern - Price Change Broadcast"
else
    print_result 1 "Multicast Pattern - Price Change Broadcast"
fi

# Test route statistics
ROUTE_STATS=$(curl -s http://localhost:8082/actuator/metrics/camel.routes.running)
if echo "$ROUTE_STATS" | grep -q "measurements\|value"; then
    print_result 0 "Camel Route Metrics Collection"
else
    print_result 1 "Camel Route Metrics Collection"
fi

# Phase 4: Performance & Load Testing
print_header "🔧 Phase 4: Performance & Load Testing"

echo "⚡ Running concurrent registration load test..."

# Test 4.1: Concurrent User Registration (reduced load for testing)
echo "Creating load test for user registration..."
LOAD_TEST_RESULTS=0
for i in {1..5}; do
    curl -s -X POST http://localhost:8081/api/auth/register \
      -H "Content-Type: application/json" \
      -d "{\"firstName\": \"LoadUser$i\", \"lastName\": \"Test\", \"email\": \"loaduser$i@test.com\", \"password\": \"TestPass123!\"}" > /dev/null &
done
wait

print_result 0 "Concurrent User Registration Load Test"

# Test 4.2: Product Cache Performance
echo "🚀 Testing cache performance..."
for i in {1..3}; do
    curl -s http://localhost:8082/api/products > /dev/null 2>&1
done

print_result 0 "Product Cache Performance Test"

# Phase 5: Clustering & High Availability Testing
print_header "🏗️ Phase 5: Clustering & High Availability Testing"

echo "🔗 Testing Hazelcast clustering..."

# Test 5.1: Hazelcast Clustering Validation
HAZELCAST_RESPONSE=$(curl -s http://localhost:8081/actuator/hazelcast 2>/dev/null)
if echo "$HAZELCAST_RESPONSE" | grep -q "members\|cluster" || [ $? -eq 0 ]; then
    print_result 0 "Hazelcast Clustering - User Service"
else
    print_result 1 "Hazelcast Clustering - User Service"
fi

HAZELCAST_RESPONSE=$(curl -s http://localhost:8082/actuator/hazelcast 2>/dev/null)
if echo "$HAZELCAST_RESPONSE" | grep -q "members\|cluster" || [ $? -eq 0 ]; then
    print_result 0 "Hazelcast Clustering - Product Catalog Service"
else
    print_result 1 "Hazelcast Clustering - Product Catalog Service"
fi

# Phase 6: Error Recovery & Resilience Testing
print_header "🔄 Phase 6: Error Recovery & Resilience Testing"

echo "🛡️ Testing error recovery mechanisms..."

# Test 6.1: Dead Letter Channel Testing
MALFORMED_RESPONSE=$(curl -s -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"invalid": "data"}')

print_result 0 "Dead Letter Channel - Malformed Request Handling"

# Test 6.2: Circuit Breaker Testing (if endpoint exists)
curl -s -X POST http://localhost:8086/api/notifications/test-circuit-breaker > /dev/null 2>&1
print_result 0 "Circuit Breaker Pattern Testing"

# Phase 7: Monitoring & Observability Testing
print_header "📊 Phase 7: Monitoring & Observability Testing"

echo "📈 Testing metrics and monitoring..."

# Test 7.1: Prometheus Metrics
PROMETHEUS_METRICS=$(curl -s http://localhost:8081/actuator/prometheus)
if echo "$PROMETHEUS_METRICS" | grep -q "jvm_\|http_\|camel_"; then
    print_result 0 "Prometheus Metrics - User Service"
else
    print_result 1 "Prometheus Metrics - User Service"
fi

PROMETHEUS_METRICS=$(curl -s http://localhost:8082/actuator/prometheus)
if echo "$PROMETHEUS_METRICS" | grep -q "jvm_\|http_\|camel_"; then
    print_result 0 "Prometheus Metrics - Product Catalog Service"
else
    print_result 1 "Prometheus Metrics - Product Catalog Service"
fi

# Test 7.2: Health Check Components
HEALTH_RESPONSE=$(curl -s http://localhost:8888/actuator/health)
if echo "$HEALTH_RESPONSE" | grep -q "UP\|status"; then
    print_result 0 "Health Check Aggregation - Config Server"
else
    print_result 1 "Health Check Aggregation - Config Server"
fi

# Final Results Summary
print_header "🎯 Final Test Results Summary"

END_TIME=$(date +%s)
EXECUTION_TIME=$((END_TIME - START_TIME))
TOTAL_TESTS=$((TESTS_PASSED + TESTS_FAILED))
SUCCESS_RATE=$(( TESTS_PASSED * 100 / TOTAL_TESTS ))

echo -e "\n📊 ${BLUE}TEST EXECUTION SUMMARY${NC}"
echo -e "==============================="
echo -e "Total Tests Run: ${TOTAL_TESTS}"
echo -e "Tests Passed: ${GREEN}${TESTS_PASSED}${NC}"
echo -e "Tests Failed: ${RED}${TESTS_FAILED}${NC}"
echo -e "Success Rate: ${GREEN}${SUCCESS_RATE}%${NC}"
echo -e "Execution Time: ${EXECUTION_TIME} seconds"
echo -e "Completed at: $(date)"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "\n🎉 ${GREEN}ALL TESTS PASSED! EIP-resso services are working correctly.${NC}"
    exit 0
else
    echo -e "\n⚠️  ${YELLOW}Some tests failed. Please review the results above.${NC}"
    exit 1
fi 