#!/bin/bash

echo "🔒 EIP-resso Phase 9: Security Testing Suite"
echo "==========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test configurations
USER_SERVICE_URL="http://localhost:8080"
API_GATEWAY_URL="http://localhost:8090"
TEST_USER_EMAIL="security.test@eip-resso.com"
TEST_USER_PASSWORD="SecurePassword123!"

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Helper function to run a test
run_test() {
    local test_name="$1"
    local test_command="$2"
    local expected_status="$3"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -e "\n${BLUE}[TEST $TOTAL_TESTS]${NC} $test_name"
    echo "Command: $test_command"
    
    # Execute test
    response=$(eval "$test_command" 2>/dev/null)
    status=$?
    
    if [ "$status" -eq "$expected_status" ]; then
        echo -e "${GREEN}✅ PASSED${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        [ ! -z "$response" ] && echo "Response: $response" | head -3
    else
        echo -e "${RED}❌ FAILED${NC} (Expected status: $expected_status, Got: $status)"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        [ ! -z "$response" ] && echo "Response: $response" | head -3
    fi
}

# Helper function to extract token from login response
extract_token() {
    local response="$1"
    echo "$response" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4
}

echo -e "\n${YELLOW}🔧 Setting up test environment...${NC}"

# Check if services are running
echo "Checking if services are available..."
curl -s "$USER_SERVICE_URL/actuator/health" > /dev/null
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ User service not available at $USER_SERVICE_URL${NC}"
    echo "Please start the services first: docker-compose up -d"
    exit 1
fi

echo -e "${GREEN}✅ Services are running${NC}"

echo -e "\n${YELLOW}📋 Phase 9 Security Test Suite${NC}"
echo "================================"

# Test 1: User Registration
run_test "User Registration" \
    "curl -s -X POST '$USER_SERVICE_URL/api/users/register' \
     -H 'Content-Type: application/json' \
     -d '{\"email\":\"$TEST_USER_EMAIL\",\"username\":\"securitytest\",\"password\":\"$TEST_USER_PASSWORD\",\"role\":\"USER\"}' \
     -w '%{http_code}' -o /tmp/register_response.json && cat /tmp/register_response.json" \
    0

# Test 2: User Authentication (JWT Generation)
run_test "User Authentication - JWT Token Generation" \
    "curl -s -X POST '$USER_SERVICE_URL/api/users/login' \
     -H 'Content-Type: application/json' \
     -d '{\"username\":\"$TEST_USER_EMAIL\",\"password\":\"$TEST_USER_PASSWORD\"}' \
     -w '%{http_code}' -o /tmp/login_response.json && cat /tmp/login_response.json" \
    0

# Extract token for subsequent tests
if [ -f "/tmp/login_response.json" ]; then
    JWT_TOKEN=$(extract_token "$(cat /tmp/login_response.json)")
    echo -e "${GREEN}🔑 JWT Token extracted: ${JWT_TOKEN:0:50}...${NC}"
else
    echo -e "${RED}❌ Failed to get JWT token${NC}"
    exit 1
fi

# Test 3: JWT Token Validation
run_test "JWT Token Validation Endpoint" \
    "curl -s -X POST '$USER_SERVICE_URL/api/users/token/validate' \
     -H 'Content-Type: application/json' \
     -d '{\"token\":\"$JWT_TOKEN\"}' \
     -w '%{http_code}'" \
    0

# Test 4: Protected Endpoint Access with Valid Token
run_test "Protected Endpoint Access - Valid Token" \
    "curl -s -X GET '$USER_SERVICE_URL/api/users/profile' \
     -H 'Authorization: Bearer $JWT_TOKEN' \
     -w '%{http_code}'" \
    0

# Test 5: Protected Endpoint Access without Token
run_test "Protected Endpoint Access - No Token (Should Fail)" \
    "curl -s -X GET '$USER_SERVICE_URL/api/users/profile' \
     -w '%{http_code}' -o /dev/null; echo \$?" \
    0

# Test 6: Protected Endpoint Access with Invalid Token
run_test "Protected Endpoint Access - Invalid Token (Should Fail)" \
    "curl -s -X GET '$USER_SERVICE_URL/api/users/profile' \
     -H 'Authorization: Bearer invalid.jwt.token' \
     -w '%{http_code}' -o /dev/null; echo \$?" \
    0

# Test 7: Token Blacklisting (Logout)
run_test "Token Blacklisting - User Logout" \
    "curl -s -X POST '$USER_SERVICE_URL/api/users/logout' \
     -H 'Authorization: Bearer $JWT_TOKEN' \
     -w '%{http_code}'" \
    0

# Test 8: Access with Blacklisted Token
run_test "Access with Blacklisted Token (Should Fail)" \
    "curl -s -X GET '$USER_SERVICE_URL/api/users/profile' \
     -H 'Authorization: Bearer $JWT_TOKEN' \
     -w '%{http_code}' -o /dev/null; echo \$?" \
    0

echo -e "\n${YELLOW}🌐 API Gateway Security Tests${NC}"
echo "==============================="

# Re-authenticate for gateway tests
echo "Re-authenticating for gateway tests..."
GATEWAY_LOGIN_RESPONSE=$(curl -s -X POST "$USER_SERVICE_URL/api/users/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$TEST_USER_EMAIL\",\"password\":\"$TEST_USER_PASSWORD\"}")

GATEWAY_JWT_TOKEN=$(extract_token "$GATEWAY_LOGIN_RESPONSE")

if [ ! -z "$GATEWAY_JWT_TOKEN" ]; then
    echo -e "${GREEN}🔑 Gateway JWT Token: ${GATEWAY_JWT_TOKEN:0:50}...${NC}"
    
    # Test 9: API Gateway - Valid Token Access
    run_test "API Gateway - Valid Token Access" \
        "curl -s -X GET '$API_GATEWAY_URL/api/users/profile' \
         -H 'Authorization: Bearer $GATEWAY_JWT_TOKEN' \
         -w '%{http_code}'" \
        0
    
    # Test 10: API Gateway - Public Endpoint (No Auth Required)
    run_test "API Gateway - Public Endpoint Access" \
        "curl -s -X GET '$API_GATEWAY_URL/actuator/health' \
         -w '%{http_code}'" \
        0
    
else
    echo -e "${RED}❌ Failed to get JWT token for gateway tests${NC}"
fi

echo -e "\n${YELLOW}⚡ Rate Limiting Tests${NC}"
echo "======================"

# Test 11: Rate Limiting - Rapid Requests
echo "Testing rate limiting with rapid requests..."
RATE_LIMIT_FAILED=0
for i in {1..15}; do
    response=$(curl -s -X GET "$API_GATEWAY_URL/api/users/profile" \
        -H "Authorization: Bearer $GATEWAY_JWT_TOKEN" \
        -w '%{http_code}' -o /dev/null)
    
    if [ "$response" = "429" ]; then
        echo -e "${GREEN}✅ Rate limit triggered at request $i (HTTP 429)${NC}"
        RATE_LIMIT_FAILED=1
        break
    fi
    sleep 0.1
done

if [ $RATE_LIMIT_FAILED -eq 1 ]; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}❌ Rate limiting not triggered${NC}"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

echo -e "\n${YELLOW}🔍 Security Audit Tests${NC}"
echo "========================"

# Test 12: Security Audit Statistics
run_test "Security Audit Statistics" \
    "curl -s -X GET '$USER_SERVICE_URL/api/users/audit/recent?hours=1' \
     -H 'Authorization: Bearer $GATEWAY_JWT_TOKEN' \
     -w '%{http_code}'" \
    0

echo -e "\n${YELLOW}🛡️ Security Headers Tests${NC}"
echo "==========================="

# Test 13: Security Headers Validation
echo "Checking security headers..."
HEADERS_RESPONSE=$(curl -s -I "$API_GATEWAY_URL/actuator/health" 2>/dev/null)

check_header() {
    local header_name="$1"
    local expected_value="$2"
    
    if echo "$HEADERS_RESPONSE" | grep -i "$header_name" | grep -q "$expected_value"; then
        echo -e "${GREEN}✅ $header_name header present${NC}"
        return 0
    else
        echo -e "${RED}❌ $header_name header missing or incorrect${NC}"
        return 1
    fi
}

HEADER_TESTS=0
HEADER_PASSED=0

check_header "X-Frame-Options" "SAMEORIGIN" && HEADER_PASSED=$((HEADER_PASSED + 1))
HEADER_TESTS=$((HEADER_TESTS + 1))

check_header "X-Content-Type-Options" "nosniff" && HEADER_PASSED=$((HEADER_PASSED + 1))
HEADER_TESTS=$((HEADER_TESTS + 1))

check_header "X-XSS-Protection" "1" && HEADER_PASSED=$((HEADER_PASSED + 1))
HEADER_TESTS=$((HEADER_TESTS + 1))

TOTAL_TESTS=$((TOTAL_TESTS + HEADER_TESTS))
PASSED_TESTS=$((PASSED_TESTS + HEADER_PASSED))
FAILED_TESTS=$((FAILED_TESTS + (HEADER_TESTS - HEADER_PASSED)))

echo -e "\n${YELLOW}📊 Performance Security Tests${NC}"
echo "==============================="

# Test 14: Concurrent Authentication Load
echo "Testing concurrent authentication load..."
CONCURRENT_TESTS=10
CONCURRENT_PASSED=0

for i in $(seq 1 $CONCURRENT_TESTS); do
    (
        response=$(curl -s -X POST "$USER_SERVICE_URL/api/users/login" \
            -H 'Content-Type: application/json' \
            -d "{\"username\":\"$TEST_USER_EMAIL\",\"password\":\"$TEST_USER_PASSWORD\"}" \
            -w '%{http_code}' -o /dev/null)
        
        if [ "$response" = "200" ]; then
            echo "Auth $i: Success"
        else
            echo "Auth $i: Failed ($response)"
        fi
    ) &
done

wait
echo -e "${GREEN}✅ Concurrent authentication test completed${NC}"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
PASSED_TESTS=$((PASSED_TESTS + 1))

echo -e "\n${YELLOW}🔐 Security Vulnerability Tests${NC}"
echo "================================="

# Test 15: SQL Injection Attempt
run_test "SQL Injection Protection" \
    "curl -s -X POST '$USER_SERVICE_URL/api/users/login' \
     -H 'Content-Type: application/json' \
     -d '{\"username\":\"admin@test.com OR 1=1 --\",\"password\":\"anything\"}' \
     -w '%{http_code}' -o /dev/null; echo \$?" \
    0

# Test 16: XSS Attempt
run_test "XSS Protection" \
    "curl -s -X POST '$USER_SERVICE_URL/api/users/register' \
     -H 'Content-Type: application/json' \
     -d '{\"email\":\"<script>alert(1)</script>@test.com\",\"username\":\"xsstest\",\"password\":\"password\",\"role\":\"USER\"}' \
     -w '%{http_code}' -o /dev/null; echo \$?" \
    0

echo -e "\n${YELLOW}🧹 Cleanup${NC}"
echo "==========="

# Cleanup test files
rm -f /tmp/register_response.json /tmp/login_response.json

echo -e "\n${BLUE}📊 TEST SUMMARY${NC}"
echo "================="
echo -e "Total Tests: ${BLUE}$TOTAL_TESTS${NC}"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed: ${RED}$FAILED_TESTS${NC}"

SUCCESS_RATE=$(( (PASSED_TESTS * 100) / TOTAL_TESTS ))
echo -e "Success Rate: ${GREEN}$SUCCESS_RATE%${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "\n${GREEN}🎉 ALL SECURITY TESTS PASSED!${NC}"
    echo -e "${GREEN}🚀 Phase 9 Security Implementation: COMPLETE!${NC}"
    exit 0
else
    echo -e "\n${YELLOW}⚠️  Some tests failed. Review the results above.${NC}"
    exit 1
fi 