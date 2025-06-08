#!/bin/bash

# EIP-resso Phase 8 Load Testing Script

echo "🚀 Starting EIP-resso Phase 8 Load Testing..."

# Test Blue environment
echo "📊 Testing Blue environment (production)..."
ab -n 1000 -c 10 http://localhost:8890/actuator/health

# Test Green environment  
echo "📊 Testing Green environment (new version)..."
ab -n 1000 -c 10 http://localhost:8891/actuator/health

# Test Load Balancer
echo "📊 Testing Load Balancer distribution..."
ab -n 2000 -c 20 http://localhost:8892/actuator/health

# Test Hazelcast clustering endpoints
echo "📊 Testing Hazelcast Management endpoints..."
curl -s "http://localhost:8890/actuator/hazelcast" | jq '.'

echo "✅ Load testing completed!"
