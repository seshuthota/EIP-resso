# EIP-resso Advanced Testing Strategy
## 🚀 Comprehensive Deep Testing Plan

### 🎯 **Testing Objectives**
1. **Integration Testing**: Service-to-service communication validation
2. **End-to-End Workflows**: Complete business process testing
3. **EIP Pattern Validation**: Advanced pattern behavior testing
4. **Performance Testing**: Load testing and bottleneck identification
5. **Clustering & Failover**: High availability scenario testing
6. **Error Recovery**: Resilience and fault tolerance validation

---

## 🔄 **Phase 1: Service Integration Testing**

### **Test 1.1: Service Discovery & Health Checks**
```bash
# Test all services registration with Consul
curl -s http://localhost:8500/v1/health/service/config-server | jq '.[].Status'
curl -s http://localhost:8500/v1/health/service/user-service | jq '.[].Status'
curl -s http://localhost:8500/v1/health/service/product-catalog | jq '.[].Status'
curl -s http://localhost:8500/v1/health/service/order-management | jq '.[].Status'
curl -s http://localhost:8500/v1/health/service/notification-service | jq '.[].Status'
curl -s http://localhost:8500/v1/health/service/analytics-service | jq '.[].Status'
```

### **Test 1.2: Configuration Propagation Testing**
```bash
# Test config server serving configurations
curl -s http://localhost:8888/user-service/dev | jq '.propertySources[0].source'
curl -s http://localhost:8888/product-catalog/dev | jq '.propertySources[0].source'
curl -s http://localhost:8888/order-management/dev | jq '.propertySources[0].source'

# Test dynamic config refresh
curl -X POST http://localhost:8081/actuator/refresh
curl -X POST http://localhost:8082/actuator/refresh
curl -X POST http://localhost:8083/actuator/refresh
```

### **Test 1.3: Database Connectivity Validation**
```bash
# Test PostgreSQL connections for each service
docker exec coffee-postgres psql -U coffee_user -d user_management -c "\dt"
docker exec coffee-postgres psql -U coffee_user -d product_catalog -c "\dt"
docker exec coffee-postgres psql -U coffee_user -d order_management -c "\dt"
docker exec coffee-postgres psql -U coffee_user -d analytics -c "\dt"
```

---

## 🎯 **Phase 2: End-to-End Business Workflow Testing**

### **Test 2.1: Complete User Journey**
```bash
# Step 1: User Registration
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe", 
    "email": "john.doe@test.com",
    "password": "SecurePass123!",
    "phoneNumber": "+1234567890"
  }'

# Step 2: User Login & JWT Token
JWT_TOKEN=$(curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "john.doe@test.com", "password": "SecurePass123!"}' \
  | jq -r '.accessToken')

# Step 3: Browse Products
curl -H "Authorization: Bearer $JWT_TOKEN" \
  http://localhost:8082/api/products

# Step 4: Create Order
ORDER_ID=$(curl -X POST http://localhost:8083/api/orders \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [{"productId": 1, "quantity": 2, "unitPrice": 4.99}],
    "totalAmount": 9.98
  }' | jq -r '.id')

# Step 5: Process Payment (Mock)
curl -X POST http://localhost:8083/api/orders/$ORDER_ID/payment \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"paymentMethod": "CREDIT_CARD", "amount": 9.98}'

# Step 6: Track Order Status
curl -H "Authorization: Bearer $JWT_TOKEN" \
  http://localhost:8083/api/orders/$ORDER_ID/status
```

### **Test 2.2: Notification Workflow Validation**
```bash
# Test notification triggers from order events
curl -X POST http://localhost:8086/api/notifications/send \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "john.doe@test.com",
    "type": "ORDER_CONFIRMATION",
    "channel": "EMAIL",
    "priority": "HIGH",
    "content": "Your order #'$ORDER_ID' has been confirmed!"
  }'

# Verify notification processing
curl http://localhost:8086/api/notifications/history?userId=john.doe@test.com
```

---

## ⚡ **Phase 3: Advanced EIP Pattern Testing**

### **Test 3.1: Event Sourcing Pattern Validation (Order Management)**
```bash
# Create order and verify event sourcing
ORDER_ID=$(curl -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": [{"productId": 1, "quantity": 1, "unitPrice": 4.99}], "totalAmount": 4.99}' \
  | jq -r '.id')

# Verify all events captured
curl http://localhost:8083/api/orders/$ORDER_ID/events

# Test event replay
curl http://localhost:8083/api/orders/$ORDER_ID/replay

# Verify state transitions
curl -X PUT http://localhost:8083/api/orders/$ORDER_ID/status/PAID
curl -X PUT http://localhost:8083/api/orders/$ORDER_ID/status/PREPARING  
curl -X PUT http://localhost:8083/api/orders/$ORDER_ID/status/SHIPPED

# Check complete event history
curl http://localhost:8083/api/orders/$ORDER_ID/events | jq '.[].eventType'
```

### **Test 3.2: Multicast Pattern Testing (Product Catalog)**
```bash
# Trigger price change to test multicast
curl -X PUT http://localhost:8082/api/products/1/price \
  -H "Content-Type: application/json" \
  -d '{"newPrice": 5.99}'

# Verify multicast routes triggered
curl -s http://localhost:8082/actuator/camelroutes | jq '.[] | select(.routeId | contains("multicast"))'

# Check route statistics
curl -s http://localhost:8082/actuator/metrics/camel.route.exchanges.total | jq '.measurements[0].value'
```

### **Test 3.3: Content-Based Router Testing (User Service)**
```bash
# Test routing based on user types
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName": "VIP", "lastName": "Customer", "email": "vip@test.com", "password": "VIPPass123!"}'

# Test different routing paths
curl -H "X-Customer-Type: VIP" http://localhost:8081/api/users/profile
curl -H "X-Customer-Type: STANDARD" http://localhost:8081/api/users/profile

# Verify routing metrics
curl -s http://localhost:8081/actuator/metrics | grep -i route
```

---

## 🔧 **Phase 4: Performance & Load Testing**

### **Test 4.1: Concurrent User Registration**
```bash
# Create load testing script
cat > load_test_registration.sh << 'EOF'
#!/bin/bash
for i in {1..50}; do
  curl -X POST http://localhost:8081/api/auth/register \
    -H "Content-Type: application/json" \
    -d "{\"firstName\": \"User$i\", \"lastName\": \"Test\", \"email\": \"user$i@test.com\", \"password\": \"TestPass123!\"}" &
done
wait
EOF

chmod +x load_test_registration.sh
./load_test_registration.sh

# Check idempotent consumer prevented duplicates
curl http://localhost:8081/api/users/count
```

### **Test 4.2: Product Cache Performance**
```bash
# Warm up cache
for i in {1..10}; do
  curl http://localhost:8082/api/products/$i
done

# Load test cached responses
time (
  for i in {1..100}; do
    curl -s http://localhost:8082/api/products/1 > /dev/null &
  done
  wait
)

# Check cache hit ratios
curl -s http://localhost:8082/actuator/metrics/cache.gets | jq '.measurements[]'
```

### **Test 4.3: Order Processing Performance**
```bash
# Create multiple orders concurrently
for i in {1..20}; do
  curl -X POST http://localhost:8083/api/orders \
    -H "Content-Type: application/json" \
    -d "{\"userId\": $i, \"items\": [{\"productId\": 1, \"quantity\": 1, \"unitPrice\": 4.99}], \"totalAmount\": 4.99}" &
done
wait

# Check processing times
curl -s http://localhost:8083/actuator/metrics/http.server.requests | jq '.measurements[] | select(.statistic == "MEAN")'
```

---

## 🏗️ **Phase 5: Clustering & High Availability Testing**

### **Test 5.1: Hazelcast Clustering Validation**
```bash
# Check cluster member status for each service
curl -s http://localhost:8081/actuator/hazelcast | jq '.members'
curl -s http://localhost:8082/actuator/hazelcast | jq '.members'  
curl -s http://localhost:8086/actuator/hazelcast | jq '.members'
curl -s http://localhost:8087/actuator/hazelcast | jq '.members'

# Test distributed cache consistency
curl -X PUT http://localhost:8082/cache/test-key -d "test-value"
curl http://localhost:8082/cache/test-key
```

### **Test 5.2: Service Failover Testing**
```bash
# Simulate service failure and test routing
# Stop one instance and verify requests still work
docker-compose -f docker-compose.dev.yml stop user-service
sleep 5

# Test if load balancer routes to healthy instances
curl http://localhost:8081/actuator/health || echo "Expected failure - testing failover"

# Restart service and verify recovery
docker-compose -f docker-compose.dev.yml start user-service
sleep 10
curl http://localhost:8081/actuator/health
```

---

## 🔄 **Phase 6: Error Recovery & Resilience Testing**

### **Test 6.1: Dead Letter Channel Testing**
```bash
# Create malformed requests to trigger error handling
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"invalid": "data"}'

# Check dead letter queue processing
curl http://localhost:8081/actuator/metrics | grep -i "dead.letter"

# Verify error audit trail
curl http://localhost:8081/api/auth/audit | jq '.[] | select(.eventType == "AUTHENTICATION_FAILED")'
```

### **Test 6.2: Circuit Breaker Testing**
```bash
# Test external service failure simulation
curl -X POST http://localhost:8086/api/notifications/test-circuit-breaker

# Verify circuit breaker metrics
curl -s http://localhost:8086/actuator/metrics | grep -i circuit
```

### **Test 6.3: Message Queue Resilience**
```bash
# Test RabbitMQ connection failure recovery
docker-compose -f docker-compose.dev.yml stop coffee-rabbitmq
sleep 5

# Send notifications during outage
curl -X POST http://localhost:8086/api/notifications/send \
  -H "Content-Type: application/json" \
  -d '{"userId": "test@test.com", "type": "TEST", "channel": "EMAIL", "content": "Test during outage"}'

# Restart RabbitMQ and verify message processing
docker-compose -f docker-compose.dev.yml start coffee-rabbitmq
sleep 10

# Check if queued messages were processed
curl http://localhost:8086/api/notifications/history
```

---

## 📊 **Phase 7: Monitoring & Observability Testing**

### **Test 7.1: Metrics Collection Validation**
```bash
# Verify Prometheus metrics endpoints
curl -s http://localhost:8081/actuator/prometheus | head -20
curl -s http://localhost:8082/actuator/prometheus | head -20
curl -s http://localhost:8083/actuator/prometheus | head -20

# Check JMX metrics
curl -s http://localhost:8081/actuator/jolokia/read/org.apache.camel:*
```

### **Test 7.2: Health Check Aggregation**
```bash
# Create health check aggregation test
curl -s http://localhost:8081/actuator/health/db
curl -s http://localhost:8082/actuator/health/redis  
curl -s http://localhost:8083/actuator/health/hazelcast

# Test health dependency chain
curl -s http://localhost:8888/actuator/health | jq '.components'
```

---

## 🎯 **Test Execution Commands**

### **Run All Tests**
```bash
# Execute complete test suite
./run-advanced-tests.sh

# Generate test report
./generate-test-report.sh
```

### **Success Criteria**
- ✅ All services register with Consul successfully
- ✅ Configuration propagation works across all services  
- ✅ Complete user journey executes without errors
- ✅ All EIP patterns demonstrate correct behavior
- ✅ Performance tests meet SLA requirements
- ✅ Clustering provides high availability
- ✅ Error recovery mechanisms function properly
- ✅ Monitoring captures comprehensive metrics

### **Expected Outcomes**
- **Integration**: 100% service-to-service communication success
- **Performance**: <200ms response times for 95% of requests
- **Availability**: 99.9% uptime during failover testing
- **Resilience**: Graceful degradation during component failures
- **Observability**: Complete metrics and health visibility 