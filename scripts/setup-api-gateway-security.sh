#!/bin/bash

echo "🔒 EIP-resso Phase 9: API Gateway Security Setup"
echo "================================================"

# Create security directories
mkdir -p load-balancers/security
mkdir -p load-balancers/nginx/conf.d
mkdir -p monitoring/security

# Create JWT validation Lua script for Nginx
cat > load-balancers/security/jwt_validation.lua << 'EOF'
local jwt = require "resty.jwt"
local http = require "resty.http"
local cjson = require "cjson"

-- JWT Secret (should match microservice secret)
local jwt_secret = "eipresso-super-secret-key-for-jwt-tokens-must-be-256-bits-minimum"

-- Rate limiting configuration
local rate_limit_requests = 100  -- requests per minute
local rate_limit_window = 60     -- seconds

-- Validate JWT token
function validate_jwt_token()
    local auth_header = ngx.var.http_authorization
    
    if not auth_header then
        ngx.log(ngx.ERR, "No Authorization header found")
        return false, "Missing Authorization header"
    end
    
    local token = string.match(auth_header, "Bearer%s+(.+)")
    if not token then
        ngx.log(ngx.ERR, "Invalid Authorization header format")
        return false, "Invalid Authorization header format"
    end
    
    -- Check JWT blacklist via HTTP call to user service
    local httpc = http.new()
    httpc:set_timeout(5000)  -- 5 second timeout
    
    local blacklist_res, err = httpc:request_uri("http://user-service:8080/api/users/token/validate", {
        method = "POST",
        body = cjson.encode({token = token}),
        headers = {
            ["Content-Type"] = "application/json",
        }
    })
    
    if blacklist_res and blacklist_res.status == 401 then
        ngx.log(ngx.ERR, "Token is blacklisted")
        return false, "Token has been invalidated"
    end
    
    -- Verify JWT signature
    local jwt_obj = jwt:verify(jwt_secret, token)
    if not jwt_obj.valid then
        ngx.log(ngx.ERR, "Invalid JWT token: " .. (jwt_obj.reason or "unknown"))
        return false, "Invalid or expired token"
    end
    
    -- Extract user info
    local payload = jwt_obj.payload
    ngx.var.user_id = payload.userId
    ngx.var.username = payload.username
    ngx.var.user_role = payload.role
    
    return true, "Valid token"
end

-- Rate limiting check
function check_rate_limit()
    local key = "rate_limit:" .. ngx.var.remote_addr .. ":" .. ngx.var.user_id
    local redis = require "resty.redis"
    local red = redis:new()
    red:set_timeout(1000)
    
    local ok, err = red:connect("redis", 6379)
    if not ok then
        ngx.log(ngx.ERR, "Failed to connect to Redis: " .. err)
        return true  -- Allow request if Redis is down
    end
    
    local current_requests, err = red:incr(key)
    if err then
        ngx.log(ngx.ERR, "Redis incr error: " .. err)
        return true
    end
    
    if current_requests == 1 then
        red:expire(key, rate_limit_window)
    end
    
    red:close()
    
    if current_requests > rate_limit_requests then
        ngx.log(ngx.WARN, "Rate limit exceeded for " .. ngx.var.remote_addr)
        return false
    end
    
    return true
end

-- Main authentication function
function authenticate()
    -- Skip authentication for public endpoints
    local uri = ngx.var.uri
    local public_endpoints = {
        "/api/users/register",
        "/api/users/login",
        "/actuator/health",
        "/api/test"
    }
    
    for _, endpoint in ipairs(public_endpoints) do
        if string.find(uri, endpoint) then
            return
        end
    end
    
    -- Validate JWT
    local valid, message = validate_jwt_token()
    if not valid then
        ngx.status = 401
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({
            error = "Unauthorized",
            message = message,
            timestamp = ngx.time()
        }))
        ngx.exit(401)
    end
    
    -- Check rate limit
    if not check_rate_limit() then
        ngx.status = 429
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({
            error = "Rate limit exceeded",
            message = "Too many requests. Please try again later.",
            timestamp = ngx.time()
        }))
        ngx.exit(429)
    end
    
    -- Add security headers
    ngx.header["X-User-ID"] = ngx.var.user_id
    ngx.header["X-Username"] = ngx.var.username
    ngx.header["X-User-Role"] = ngx.var.user_role
end

-- Execute authentication
authenticate()
EOF

# Create secure Nginx configuration
cat > load-balancers/nginx/secure-api-gateway.conf << 'EOF'
# EIP-resso Secure API Gateway Configuration
upstream user_service {
    server user-service:8080 weight=1 max_fails=3 fail_timeout=30s;
}

upstream product_catalog {
    server product-catalog:8081 weight=1 max_fails=3 fail_timeout=30s;
}

upstream order_management {
    server order-management:8082 weight=1 max_fails=3 fail_timeout=30s;
}

upstream payment_service {
    server payment-service:8083 weight=1 max_fails=3 fail_timeout=30s;
}

upstream notification_service {
    server notification-service:8084 weight=1 max_fails=3 fail_timeout=30s;
}

upstream analytics_service {
    server analytics-service:8085 weight=1 max_fails=3 fail_timeout=30s;
}

upstream orchestration_service {
    server order-orchestration:8086 weight=1 max_fails=3 fail_timeout=30s;
}

# Rate limiting zones
limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m;
limit_req_zone $binary_remote_addr zone=api:10m rate=100r/m;
limit_req_zone $binary_remote_addr zone=sensitive:10m rate=10r/m;

# Connection limiting
limit_conn_zone $binary_remote_addr zone=addr:10m;

server {
    listen 80;
    server_name api.eip-resso.com localhost;
    
    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'" always;
    
    # Rate limiting
    limit_conn addr 20;
    
    # JWT Authentication via Lua
    access_by_lua_file /etc/nginx/lua/jwt_validation.lua;
    
    # User Service Routes
    location /api/users/ {
        # Special rate limiting for login
        location ~ ^/api/users/(login|register)$ {
            limit_req zone=login burst=3 nodelay;
            proxy_pass http://user_service;
            include /etc/nginx/proxy_params;
        }
        
        limit_req zone=api burst=20 nodelay;
        proxy_pass http://user_service;
        include /etc/nginx/proxy_params;
    }
    
    # Product Catalog Routes
    location /api/products/ {
        limit_req zone=api burst=50 nodelay;
        proxy_pass http://product_catalog;
        include /etc/nginx/proxy_params;
    }
    
    # Order Management Routes
    location /api/orders/ {
        limit_req zone=sensitive burst=10 nodelay;
        proxy_pass http://order_management;
        include /etc/nginx/proxy_params;
    }
    
    # Payment Service Routes
    location /api/payments/ {
        limit_req zone=sensitive burst=5 nodelay;
        proxy_pass http://payment_service;
        include /etc/nginx/proxy_params;
    }
    
    # Notification Service Routes
    location /api/notifications/ {
        limit_req zone=api burst=20 nodelay;
        proxy_pass http://notification_service;
        include /etc/nginx/proxy_params;
    }
    
    # Analytics Service Routes
    location /api/analytics/ {
        limit_req zone=api burst=30 nodelay;
        proxy_pass http://analytics_service;
        include /etc/nginx/proxy_params;
    }
    
    # Orchestration Service Routes
    location /api/orchestration/ {
        limit_req zone=sensitive burst=10 nodelay;
        proxy_pass http://orchestration_service;
        include /etc/nginx/proxy_params;
    }
    
    # Health check endpoints (no auth required)
    location /actuator/health {
        access_log off;
        proxy_pass http://user_service;
        include /etc/nginx/proxy_params;
    }
    
    # Security monitoring endpoint
    location /security/stats {
        access_by_lua_block {
            if ngx.var.user_role ~= "ADMIN" then
                ngx.status = 403
                ngx.say('{"error":"Forbidden"}')
                ngx.exit(403)
            end
        }
        
        return 200 '{"message":"Security stats would be here","timestamp":"$time_iso8601"}';
        add_header Content-Type application/json;
    }
    
    # Default deny
    location / {
        return 404 '{"error":"Endpoint not found"}';
        add_header Content-Type application/json;
    }
}
EOF

# Create proxy parameters
cat > load-balancers/nginx/proxy_params << 'EOF'
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Request-ID $request_id;

# Security headers for backend
proxy_set_header X-User-ID $user_id;
proxy_set_header X-Username $username;
proxy_set_header X-User-Role $user_role;

# Timeout settings
proxy_connect_timeout 30s;
proxy_send_timeout 30s;
proxy_read_timeout 30s;

# Buffer settings
proxy_buffering on;
proxy_buffer_size 4k;
proxy_buffers 8 4k;
EOF

# Create security monitoring configuration for Prometheus
cat > monitoring/security/security-metrics.yml << 'EOF'
# Security Metrics Collection for Prometheus
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "security-rules.yml"

scrape_configs:
  - job_name: 'eip-resso-security'
    static_configs:
      - targets: ['user-service:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    
  - job_name: 'nginx-security'
    static_configs:
      - targets: ['nginx-api-gateway:9113']
    metrics_path: '/metrics'
    scrape_interval: 15s

  - job_name: 'redis-security'
    static_configs:
      - targets: ['redis:6379']
    metrics_path: '/metrics'
    scrape_interval: 30s
EOF

# Create security alerting rules
cat > monitoring/security/security-rules.yml << 'EOF'
groups:
  - name: security_alerts
    rules:
      - alert: HighFailedLoginRate
        expr: rate(security_audit_events_total{event="LOGIN_FAILED"}[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
          service: authentication
        annotations:
          summary: "High failed login rate detected"
          description: "Failed login rate is {{ $value }} per second"
          
      - alert: SuspiciousActivity
        expr: security_audit_events_total{event="SUSPICIOUS_ACTIVITY"} > 0
        for: 0s
        labels:
          severity: critical
          service: security
        annotations:
          summary: "Suspicious activity detected"
          description: "Suspicious activity pattern detected for user/IP"
          
      - alert: TokenBlacklistingSpike
        expr: rate(security_audit_events_total{event="TOKEN_BLACKLISTED"}[5m]) > 0.05
        for: 1m
        labels:
          severity: warning
          service: authentication
        annotations:
          summary: "High token blacklisting rate"
          description: "Token blacklisting rate is {{ $value }} per second"
          
      - alert: RateLimitExceeded
        expr: nginx_http_requests_total{status="429"} > 100
        for: 1m
        labels:
          severity: warning
          service: api_gateway
        annotations:
          summary: "Rate limit frequently exceeded"
          description: "Rate limiting is being triggered frequently"
          
      - alert: UnauthorizedAccessSpike
        expr: rate(security_audit_events_total{event="UNAUTHORIZED_ACCESS"}[5m]) > 0.2
        for: 2m
        labels:
          severity: critical
          service: security
        annotations:
          summary: "High unauthorized access attempts"
          description: "Unauthorized access rate is {{ $value }} per second"
EOF

# Create Docker Compose extension for secure API Gateway
cat > docker-compose.security.yml << 'EOF'
version: '3.8'

services:
  nginx-api-gateway:
    image: openresty/openresty:alpine
    container_name: eip-resso-secure-gateway
    ports:
      - "8090:80"  # Secure API Gateway port
    volumes:
      - ./load-balancers/nginx/secure-api-gateway.conf:/etc/nginx/conf.d/default.conf:ro
      - ./load-balancers/nginx/proxy_params:/etc/nginx/proxy_params:ro
      - ./load-balancers/security/jwt_validation.lua:/etc/nginx/lua/jwt_validation.lua:ro
    depends_on:
      - redis
      - user-service
    networks:
      - eip-resso-network
    environment:
      - NGINX_WORKER_PROCESSES=auto
      - NGINX_WORKER_CONNECTIONS=1024
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    restart: unless-stopped
    
  nginx-exporter:
    image: nginx/nginx-prometheus-exporter:latest
    container_name: eip-resso-nginx-exporter
    ports:
      - "9113:9113"
    command:
      - '-nginx.scrape-uri=http://nginx-api-gateway/nginx_status'
    depends_on:
      - nginx-api-gateway
    networks:
      - eip-resso-network
    restart: unless-stopped

networks:
  eip-resso-network:
    external: true
EOF

echo "✅ API Gateway Security Configuration Created"
echo ""
echo "🔧 Next Steps:"
echo "1. Deploy secure API Gateway: docker-compose -f docker-compose.security.yml up -d"
echo "2. Test JWT authentication: curl -H 'Authorization: Bearer <token>' http://localhost:8090/api/users/profile"
echo "3. Monitor security metrics: http://localhost:9090 (Prometheus)"
echo "4. View rate limiting: curl -i http://localhost:8090/api/orders/ (repeat rapidly)"
echo ""
echo "📊 Security Features Enabled:"
echo "- JWT token validation"
echo "- Rate limiting per endpoint"
echo "- Security audit logging"
echo "- Real-time threat detection"
echo "- Connection limiting"
echo "- Security headers"
echo ""
echo "🚀 Phase 9 Security Layer: READY TO DEPLOY!" 