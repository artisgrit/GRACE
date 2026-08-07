# GRACE Backend - Spring Boot Application
FROM maven:3.9.16-eclipse-temurin-25-alpine AS builder

WORKDIR /app

# Copy Maven configuration
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY src src

# Test and build the application
RUN mvn clean verify -B

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Install runtime dependencies
RUN apk add --no-cache curl tzdata

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy built application from builder
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Expose port
EXPOSE 8080 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health/readiness || exit 1

# Run as non-root user
USER appuser

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
