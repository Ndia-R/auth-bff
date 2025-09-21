# Code Style and Conventions

## Java Code Style
- **Package Structure**: Following standard Java conventions
  - `com.example.auth_bff` as base package
  - Organized by layers: `controller`, `config`, `service`

## Spring Boot Conventions
- **Controller Classes**: 
  - Use `@RestController` annotation
  - Use `@RequestMapping` for base path mapping
  - Use `@RequiredArgsConstructor` from Lombok for dependency injection
  - Return `ResponseEntity<>` for HTTP responses

- **Configuration Classes**:
  - Use `@Configuration` annotation
  - Use `@Bean` methods for Spring bean definitions
  - Clear method names describing the bean purpose

## Lombok Usage
- `@RequiredArgsConstructor` for constructor injection
- Avoid excessive Lombok annotations

## Naming Conventions
- **Classes**: PascalCase (e.g., `AuthController`, `SecurityConfig`)
- **Methods**: camelCase (e.g., `getAccessToken`, `refreshAccessToken`)
- **Variables**: camelCase
- **Constants**: UPPER_SNAKE_CASE
- **Packages**: lowercase with dots

## Code Organization
- Controllers handle HTTP requests/responses only
- Services contain business logic
- Configuration classes for Spring beans and settings
- Clear separation of concerns

## Error Handling
- Return appropriate HTTP status codes
- Use `Map<String, Object>` for JSON responses
- Consistent error response structure

## Security Best Practices
- HttpOnly, Secure, SameSite cookie attributes
- CSRF protection disabled for API endpoints
- CORS configuration for frontend integration
- Session timeout and invalidation