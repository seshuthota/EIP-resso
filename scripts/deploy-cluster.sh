#!/bin/bash

# EIP-resso Cluster Deployment Script
# Comprehensive deployment and health checking for multi-instance clustering

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 EIP-resso Clustering Deployment Starting...${NC}"
echo "=================================================="

# Function to print colored output
print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to check if a service is healthy
check_service_health() {
    local service_name=$1
    local health_url=$2
    local timeout=$3
    
    print_status $YELLOW "🔍 Checking health of $service_name..."
    
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        if curl -sf "$health_url" > /dev/null 2>&1; then
            print_status $GREEN "✅ $service_name is healthy"
            return 0
        fi
        
        print_status $YELLOW "⏳ Waiting for $service_name ($elapsed/$timeout seconds)..."
        sleep 10
        elapsed=$((elapsed + 10))
    done
    
    print_status $RED "❌ $service_name failed to become healthy within $timeout seconds"
    return 1
}

print_status $BLUE "📦 Phase 1: Building Services"

print_status $YELLOW "Building Maven projects..."
mvn clean compile -DskipTests -q || {
    print_status $RED "❌ Maven build failed"
    exit 1
}

print_status $GREEN "✅ Maven build completed"

print_status $BLUE "🏗️  Phase 2: Starting Infrastructure Services"

print_status $YELLOW "Starting infrastructure services..."
docker-compose -f docker-compose.clustering.yml up -d postgres-master consul-1

# Wait for infrastructure
sleep 30

print_status $GREEN "✅ Infrastructure services started"

print_status $BLUE "📊 Phase 3: Starting Monitoring Services"

docker-compose -f docker-compose.clustering.yml up -d hazelcast-mancenter prometheus

sleep 20

print_status $GREEN "✅ Monitoring services started"

print_status $GREEN "🎉 EIP-resso Cluster Deployment Completed!"
print_status $BLUE "🔗 Access Hazelcast Management Center: http://localhost:8080" 