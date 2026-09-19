# AgenticTripAI — P0 production hardening

This build hardens the application around the P0 production gaps identified in the codebase review.

## Implemented

- Spring Security session authentication with a real database-backed user account.
- BCrypt password hashing (work factor 12).
- UI login and account registration pages.
- Authenticated `/api/**` boundary; unauthenticated API calls return HTTP 401 instead of a login HTML redirect.
- Server-side identity: request `userId` values are ignored/overridden with the authenticated principal.
- Ownership checks for trips, conversations, LangGraph threads, SSE progress, restore and graph-history endpoints.
- CSRF protection for browser POST/DELETE requests.
- HTTP-only, SameSite=Lax session cookie; production defaults require HTTPS for the session cookie.
- Versioned Flyway migrations. Hibernate is `validate`, not `update`.
- Production datasource credentials are environment-only; no default DB password in the production configuration.
- Bounded travel-plan executor and bounded specialist executor; the previous cached thread pool was removed.
- Global and per-user active-plan admission control.
- Database-backed per-user planning rate limit.
- Durable PostgreSQL-backed graph progress events so SSE can reconnect across application instances without relying on JVM-local replay state.
- Reduced Hibernate bind-parameter logging from TRACE to WARN.
- Browser automation is disabled by default for server deployments.

## First run

Set at minimum:

```text
DB_URL=jdbc:postgresql://<host>:5432/travel_agent
DB_USERNAME=<db-user>
DB_PASSWORD=<strong-secret>
```

Then start the application. Flyway creates the application tables. LangGraph's PostgreSQL checkpoint saver and Spring AI pgvector initialization continue to manage their own supporting tables.

For local development without HTTPS:

```text
SPRING_PROFILES_ACTIVE=local
SESSION_COOKIE_SECURE=false
```

The `local` profile only restores local PostgreSQL defaults; do not use it in production.

## UI

Open `/`. An unauthenticated user is redirected to `/login`.

Use **Create an account** to create a user. Passwords must be 10–128 characters.

After login, the sidebar shows the authenticated account and a **Sign out** action. The user ID/session field is no longer editable.

## Runtime controls

Environment variables:

```text
TRAVEL_MAX_CONCURRENT_PLANS=12
TRAVEL_MAX_CONCURRENT_PLANS_PER_USER=2
TRAVEL_MAX_CONCURRENT_SPECIALISTS=12
TRAVEL_PLAN_QUEUE_CAPACITY=40
TRAVEL_RATE_LIMIT_PER_WINDOW=6
TRAVEL_RATE_LIMIT_WINDOW_SECONDS=60
```

Tune these to the available LLM/provider quotas and deployment size.

## Important deployment note

The durable SSE implementation uses PostgreSQL polling rather than a JVM-local event map. This is intentionally dependency-light and works across multiple application instances. At very high event volume, move the progress transport to Redis Streams/PubSub or Kafka; that is a scalability optimization, not an identity/security boundary.


## Spring Security matcher compatibility

The security configuration does not use `AntPathRequestMatcher`. API unauthenticated requests use a small `RequestMatcher` lambda based on the servlet path, while authorization uses the modern `requestMatchers(String...)` DSL. This avoids the `AntPathRequestMatcher cannot be resolved` / deprecated matcher issue with newer Spring Security releases.
