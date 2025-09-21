# Task Completion Guidelines

## Commands to Run After Code Changes

### Build and Test
```bash
# Always run build to check for compilation errors
./gradlew build

# Run tests to ensure nothing is broken
./gradlew test

# For quick compilation check without tests
./gradlew compileJava
```

### Code Quality
- No specific linting or formatting tools configured in this project
- Follow Java and Spring Boot conventions
- Ensure proper indentation and consistent style

### Before Committing
1. Run `./gradlew build` to ensure compilation success
2. Run `./gradlew test` to ensure all tests pass
3. Check that application starts successfully with `./gradlew bootRun`
4. Verify configuration files are valid (application.yml)

### Testing the Application
```bash
# Start the application
./gradlew bootRun

# Test health endpoint
curl http://localhost:8080/auth/health

# If using Docker Compose (with Redis)
docker compose up
```

### Environment Setup
- Ensure Redis is running (via Docker Compose or locally)
- Set required environment variables (see .env.example)
- Verify Keycloak configuration if testing OAuth2 flow

## Important Notes
- Always test authentication endpoints with proper OAuth2 setup
- Verify session management works with Redis
- Check CORS configuration for frontend integration
- Ensure secure cookie settings are maintained