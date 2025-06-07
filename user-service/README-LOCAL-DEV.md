# Running User Management Service Locally

## Quick Start for IntelliJ IDEA

### Option 1: Using Local Profile (Recommended)
1. Open the project in IntelliJ IDEA
2. Navigate to `UserServiceApplication.java`
3. Right-click and select "Run 'UserServiceApplication'"
4. Edit the run configuration and add to **Program arguments**:
   ```
   --spring.profiles.active=local
   ```
5. Run the application

### Option 2: Using VM Options
1. In your run configuration, add to **VM options**:
   ```
   -Dspring.profiles.active=local
   ```

### Option 3: Using Environment Variables
1. In your run configuration, add **Environment variable**:
   ```
   SPRING_PROFILES_ACTIVE=local
   ```

## What the Local Profile Provides

- ✅ **No Config Server Required** - Runs independently
- ✅ **No Consul Required** - Service discovery disabled
- ✅ **H2 In-Memory Database** - No PostgreSQL setup needed
- ✅ **Simplified Logging** - Cleaner console output
- ✅ **H2 Console Access** - Available at `http://localhost:8081/h2-console`
- ✅ **All EIP Patterns Active** - Full Camel routes working
- ✅ **JWT Authentication** - Complete authentication system

## Application Endpoints

Once running on `http://localhost:8081`:

### Authentication Endpoints
- `POST /api/v1/users/register` - User registration
- `POST /api/v1/users/login` - User login
- `GET /api/v1/users/profile` - Get user profile (requires JWT)

### Health & Monitoring
- `GET /actuator/health` - Application health check
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/prometheus` - Prometheus metrics

### H2 Database Console
- `GET /h2-console` - Database management interface
  - **JDBC URL**: `jdbc:h2:mem:eipresso_local`
  - **Username**: `sa`
  - **Password**: (leave empty)

## Testing the Application

### 1. Register a User
```bash
curl -X POST http://localhost:8081/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "email": "john@eipresso.com",
    "password": "password123",
    "confirmPassword": "password123",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

### 2. Login
```bash
curl -X POST http://localhost:8081/api/v1/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john@eipresso.com",
    "password": "password123"
  }'
```

### 3. Access Profile (use JWT from login response)
```bash
curl -X GET http://localhost:8081/api/v1/users/profile \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE"
```

## Apache Camel Routes Available

The following EIP patterns are active and working:
- ✅ **Dead Letter Channel** - Failed operation handling
- ✅ **Idempotent Consumer** - Duplicate registration prevention
- ✅ **Wire Tap** - Security audit logging
- ✅ **Content Enricher** - User profile enhancement

## Development Features

- **Fast BCrypt** - Lower strength (4) for quicker password encoding
- **Detailed Logging** - All user operations logged to console
- **Hot Reload** - Use Spring Boot DevTools for automatic restart
- **In-Memory Database** - Data resets on each restart (clean state)

## Troubleshooting

### Common Issues:

1. **Port 8081 already in use**
   - Add to application arguments: `--server.port=8082`

2. **Config Server error persists**
   - Ensure you're using the `local` profile
   - Check no other profiles are active

3. **Database connection issues**
   - With local profile, H2 should work automatically
   - Check H2 console for database state

4. **JWT issues**
   - Tokens are valid for 15 minutes in local mode
   - Generate new tokens if expired

### Support

For any issues, check the application logs in the IntelliJ console. All major operations are logged with DEBUG level for troubleshooting. 