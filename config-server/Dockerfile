# EIP-resso Configuration Server - Production Dockerfile

# Build stage
FROM maven:3.9.0-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml ./
COPY config-server/pom.xml ./config-server/
COPY config-server/src ./config-server/src

# Build the application
RUN mvn clean package -DskipTests -pl config-server

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Create non-root user for security
RUN addgroup -g 1001 -S eipresso && \
    adduser -u 1001 -S eipresso -G eipresso

# Create application directory
WORKDIR /app

# Copy built JAR
COPY --from=builder /build/config-server/target/*.jar app.jar

# Create logs directory
RUN mkdir -p /var/logs/config-server && \
    chown -R eipresso:eipresso /var/logs/config-server

# Switch to non-root user
USER eipresso

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

# Expose port
EXPOSE 8888

# Environment variables for production
ENV SPRING_PROFILES_ACTIVE=production
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Labels for metadata
LABEL maintainer="EIP-resso Team" \
      version="1.0.0" \
      description="EIP-resso Configuration Server" \
      service="config-server" 