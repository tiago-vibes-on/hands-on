# Containers

## Objective

Containerize a small Quarkus Hero Association service, add PostgreSQL persistence, and evolve the container image from a single-stage build to a multi-stage build.

## Technology

- Java 25 LTS
- Quarkus LTS
- Maven
- Docker and Docker Compose
- PostgreSQL

## Initial state

- A stateless Quarkus Hero Association service.
- `GET /status` confirms that the application is running.
- No hero data, database, Docker configuration, or persistence.

## Final state

- A PostgreSQL-backed Hero Association API.
- A `Hero` has `id`, `name`, `alias`, and `power`.
- The API provides:
  - `POST /heroes`
  - `GET /heroes`
  - `GET /heroes/{id}`
  - `PUT /heroes/{id}`
  - `PATCH /heroes/{id}`
- `name`, `alias`, and `power` are required.
- `alias` is unique.
- A duplicate alias returns `409`.
- Unknown hero IDs return `404`.
- Invalid requests return `400`.
- Creation returns `201`.
- Docker Compose runs the API and PostgreSQL.
- PostgreSQL uses a named volume.
- The project demonstrates a single-stage Dockerfile before using the multi-stage Dockerfile retained as the final default.

## Out of scope

- Native images
- Authentication and authorization
- Pagination
- Delete operations
- Database migrations
- OpenAPI documentation
- Kubernetes

## Acceptance criteria

- Both `initial` and `final` build and run independently.
- The initial application responds successfully to `GET /status`.
- The final application supports the specified API operations in containers.
- Hero data remains available after restarting the API container.
- Relevant automated tests pass.
