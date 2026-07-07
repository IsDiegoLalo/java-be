---
inclusion: always
---

# Code Review Standards

When writing or reviewing code, verify the following:

## Security
- Never hardcode secrets, tokens, or passwords
- Validate and sanitize all external input
- Use parameterized queries for database access (no string concatenation in SQL)
- Apply principle of least privilege for access control
- Escape output to prevent injection attacks

## Performance
- Avoid N+1 query problems; use joins or batch fetching
- Use pagination for list endpoints that can return large result sets
- Cache expensive computations or frequently-read data where appropriate
- Be mindful of memory allocation in loops and streams

## API Design
- Use RESTful conventions: proper HTTP methods, status codes, and resource naming
- Version APIs when breaking changes are necessary
- Return consistent response structures (e.g., envelope pattern or Problem Details RFC 7807)
- Validate request bodies with Bean Validation (`@Valid`, `@NotNull`, `@Size`, etc.)

## Logging
- Log at appropriate levels: ERROR for failures, WARN for recoverable issues, INFO for business events, DEBUG for troubleshooting
- Include correlation IDs for request tracing
- Never log sensitive data (passwords, tokens, PII)
- Use structured logging (key-value pairs) for better searchability

## Dependency Management
- Pin dependency versions explicitly
- Keep dependencies up to date and audit for vulnerabilities
- Prefer well-maintained, widely-used libraries
- Minimize transitive dependency conflicts
