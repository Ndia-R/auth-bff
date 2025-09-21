# Suggested Commands for Auth BFF Development

## Build Commands
```bash
# Build the application (excluding tests)
./gradlew build -x test

# Build with tests
./gradlew build

# Clean build
./gradlew clean build
```

## Run Commands
```bash
# Run the application
./gradlew bootRun

# Run with Docker Compose (includes Redis)
docker compose up

# Run tests
./gradlew test
```

## Development Commands
```bash
# Check dependencies
./gradlew dependencies

# Generate wrapper (if needed)
./gradlew wrapper

# Show project tasks
./gradlew tasks
```

## System Utilities (Linux)
- `git` - Version control
- `ls` - List files and directories
- `cd` - Change directory
- `grep` - Search text patterns
- `find` - Find files and directories
- `cat` - Display file contents
- `ps` - Process status
- `docker` - Container management
- `docker compose` - Multi-container application management

## File Operations
- Create/edit files: Use IDE or `nano`, `vim`
- File permissions: `chmod`
- File ownership: `chown`
- Copy files: `cp`
- Move files: `mv`
- Remove files: `rm`