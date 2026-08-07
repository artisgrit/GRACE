# GRACE Development Setup Guide

This guide provides step-by-step instructions for setting up a local development environment for GRACE.

## Prerequisites

### Required Software

- **Java 21** (Eclipse Temurin or OpenJDK)
- **Node.js 20+** with pnpm
- **Docker** and **Docker Compose**
- **PostgreSQL 16** (or use Docker)
- **Git**

### Recommended Tools

- **IntelliJ IDEA** (for Java development)
- **VS Code** (for frontend development)
- **Postman** or **Insomnia** (for API testing)
- **pgAdmin** or **DBeaver** (for database management)
- **Redis Insight** (for cache inspection)

## Quick Start with Docker

The fastest way to get started is using Docker Compose:

```bash
# Clone the repository
git clone https://github.com/your-org/grace.git
cd grace

# Copy environment file
cp .env.example .env

# Edit .env and update passwords
# IMPORTANT: Change default passwords for production!

# Start all services
docker-compose up -d

# Check service status
docker-compose ps

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

### Access Points

After starting with Docker Compose:

| Service | URL | Credentials |
|---------|-----|-------------|
| Frontend | http://localhost:3000 | - |
| Backend API | http://localhost:8080 | - |
| Keycloak | http://localhost:8180 | admin / admin_secure_password_change_me |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin_secure_password_change_me |
| OpenSearch | http://localhost:9200 | - |
| PostgreSQL | localhost:5432 | grace / grace_secure_password_change_me |
| Redis | localhost:6379 | - |

## Manual Backend Setup

### 1. Install Java 21

```bash
# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# macOS (with Homebrew)
brew install openjdk@21

# Verify installation
java -version
```

### 2. Set up PostgreSQL Database

```bash
# Using Docker (recommended)
docker run -d \
  --name grace-postgres \
  -e POSTGRES_DB=grace \
  -e POSTGRES_USER=grace \
  -e POSTGRES_PASSWORD=grace_dev_password \
  -p 5432:5432 \
  postgres:16-alpine

# Or install locally and create database
createdb -U postgres grace
```

### 3. Set up Redis

```bash
# Using Docker
docker run -d \
  --name grace-redis \
  -p 6379:6379 \
  redis:7-alpine

# Or install locally
sudo apt install redis-server
```

### 4. Build and Run Backend

```bash
cd backend

# Make Maven wrapper executable
chmod +x mvnw

# Build the application
./mvnw clean package -DskipTests

# Run with development profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or run the JAR
java -jar target/grace-backend-0.1.0-SNAPSHOT.jar
```

### 5. Configure Backend

Create `backend/src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/grace
    username: grace
    password: grace_dev_password
  redis:
    host: localhost
    port: 6379
  opensearch:
    uris: http://localhost:9200

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin

keycloak:
  url: http://localhost:8180
  realm: grace
  client-id: grace-backend

jwt:
  secret: your_development_jwt_secret_minimum_32_characters_long
```

## Manual Frontend Setup

### 1. Install Node.js and pnpm

```bash
# Install Node.js 20+
# Download from https://nodejs.org or use nvm

# Enable pnpm
corepack enable

# Verify installations
node --version
pnpm --version
```

### 2. Install Dependencies

```bash
cd frontend

# Install dependencies
pnpm install
```

### 3. Configure Environment

Create `frontend/.env.local`:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_KEYCLOAK_URL=http://localhost:8180
NEXT_PUBLIC_KEYCLOAK_REALM=grace
NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=grace-frontend
```

### 4. Run Development Server

```bash
# Start development server
pnpm dev

# Access at http://localhost:3000
```

## Keycloak Setup

### 1. Access Keycloak Admin Console

Navigate to http://localhost:8180 and login with admin credentials.

### 2. Create Realm

1. Click on the realm dropdown (top-left)
2. Click "Create Realm"
3. Name: `grace`
4. Click "Create"

### 3. Create Client

1. Go to Clients → Create client
2. Client type: OpenID Connect
3. Client ID: `grace-frontend`
4. Next
5. Capability config:
   - Client authentication: ON
   - Authorization: OFF
   - Next
6. Login settings:
   - Root URL: http://localhost:3000
   - Valid redirect URIs: http://localhost:3000/*
   - Valid post logout redirect URIs: http://localhost:3000/*
   - Web origins: http://localhost:3000
   - Save

### 4. Create Roles

Go to Realm roles → Create role:

- `super_admin`
- `tenant_admin`
- `librarian`
- `contributor`
- `reviewer`
- `faculty`
- `student`

### 5. Create Test User

1. Go to Users → Add user
2. Username: `testuser`
3. Email: `test@example.com`
4. First name: Test
5. Last name: User
6. Email verified: ON
7. Save
8. Go to Credentials tab
9. Set password: `password123`
10. Temporary: OFF
11. Go to Role mapping tab
12. Assign roles: `librarian`

## OpenSearch Setup

### Using Docker (Recommended)

```bash
docker run -d \
  --name grace-opensearch \
  -e discovery.type=single-node \
  -e plugins.security.disabled=true \
  -e "OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m" \
  -p 9200:9200 \
  -p 9600:9600 \
  opensearchproject/opensearch:2.11.0
```

### Verify OpenSearch

```bash
curl http://localhost:9200/_cluster/health
```

Expected response:
```json
{
  "status": "green",
  "number_of_nodes": 1,
  "number_of_data_nodes": 1
}
```

## MinIO Setup

### Using Docker

```bash
docker run -d \
  --name grace-minio \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -p 9000:9000 \
  -p 9001:9001 \
  minio/minio server /data --console-address ":9001"
```

### Create Bucket

1. Access console at http://localhost:9001
2. Login with minioadmin credentials
3. Create bucket: `grace-uploads`
4. Set access policy to "Public" for development

## Database Migration

GRACE uses Flyway for database migrations.

### Initial Setup

The `deploy/init.sql` script runs automatically on first database startup via Docker Compose.

### Manual Migration

```bash
cd backend

# Run migrations
./mvnw flyway:migrate

# Validate database
./mvnw flyway:validate
```

### Check Migration Status

```bash
./mvnw flyway:info
```

## Running Tests

### Backend Tests

```bash
cd backend

# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=GuideServiceTest

# Run with coverage
./mvnw clean test jacoco:report
```

### Frontend Tests

```bash
cd frontend

# Run unit tests
pnpm test

# Run E2E tests
pnpm test:e2e

# Run with coverage
pnpm test:coverage
```

## API Documentation

Once the backend is running, access:

- **OpenAPI/Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs
- **Actuator Health**: http://localhost:8080/actuator/health
- **Actuator Metrics**: http://localhost:8080/actuator/metrics

## Common Development Tasks

### Reset Database

```bash
# Drop and recreate database
docker-compose down -v
docker-compose up -d postgres

# Wait for PostgreSQL to be ready
sleep 10

# Restart backend to apply migrations
docker-compose restart backend
```

### Clear Cache

```bash
# Redis CLI
docker exec -it grace-redis redis-cli

# Flush all keys
FLUSHALL
```

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f backend
docker-compose logs -f frontend
```

### Debug Backend

In IntelliJ IDEA:

1. Run → Edit Configurations
2. Add new Spring Boot configuration
3. Main class: `com.grace.GraceApplication`
4. Environment variables from `.env`
5. Debug port: 5005
6. Run in debug mode

### Debug Frontend

In VS Code:

1. Install Debugger for Chrome extension
2. Add launch configuration to `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "chrome",
      "request": "launch",
      "name": "Launch Chrome against localhost",
      "url": "http://localhost:3000",
      "webRoot": "${workspaceFolder}"
    }
  ]
}
```

## Troubleshooting

### Port Already in Use

```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>

# Or change port in configuration
```

### Database Connection Failed

```bash
# Check PostgreSQL is running
docker-compose ps postgres

# Check connection string
# Verify credentials in .env

# Test connection
psql -h localhost -U grace -d grace
```

### Out of Memory

Increase Docker memory:
1. Docker Desktop → Settings → Resources
2. Increase Memory to 8GB+

Or adjust JVM options:
```yaml
environment:
  JAVA_TOOL_OPTIONS: "-Xmx2g -Xms1g"
```

### Keycloak Authentication Issues

```bash
# Check Keycloak logs
docker-compose logs keycloak

# Verify realm exists
# Check client configuration
# Ensure redirect URIs match
```

## Next Steps

After setup:

1. ✅ Review [Architecture Documentation](docs/ARCHITECTURE.md)
2. ✅ Explore the API documentation
3. ✅ Create your first tenant
4. ✅ Import sample data
5. ✅ Start developing features

## Getting Help

- Check existing issues on GitHub
- Read the documentation in `/docs`
- Join the developer community
- Contact the core team

---

Happy coding! 🚀
