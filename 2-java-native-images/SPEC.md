# Java Native Images

## Objective

Evolve the PostgreSQL-backed Hero Association application from `1-containers` into a Quarkus native executable, and run that executable both directly on WSL and in Docker.

## Technology

- Java 25 LTS
- Quarkus LTS
- Maven
- GraalVM for JDK 25 with Native Image, for the local-native path
- Docker and Docker Compose
- PostgreSQL

## Initial state

- An independent copy of the final Hero Association application from `1-containers`.
- JVM-based multi-stage Docker image.
- PostgreSQL-backed API with the existing `Hero` model and endpoints.
- Docker Compose runs the API and PostgreSQL.

## Final state

- The API and database behavior remain unchanged.
- A developer can use local GraalVM to build a native executable and run the API directly on WSL.
- Docker Compose can start only PostgreSQL for that local-native execution, exposing port `5432` to the host.
- A multi-stage Dockerfile uses a Mandrel 25 builder image to compile the native executable.
- Default Maven and Docker builds remain JVM-based.
- An explicit Docker Compose override runs the native API image with PostgreSQL.
- The final runtime image is compatible with the UBI 9 native build environment.

## Local native execution

- The executable is built with `mvn package -Dnative` using local GraalVM.
- If local GraalVM Native Image is unavailable, `mvn package -Dnative` fails instead of using Docker as a fallback.
- PostgreSQL runs in Docker.
- The executable receives a JDBC URL for `localhost:5432` through environment variables.

## Out of scope

- New API endpoints or model fields
- Database migrations
- Authentication and authorization
- Kubernetes
- Native-image tuning beyond the default Quarkus build
- Performance claims that depend on a specific machine

## Acceptance criteria

- `initial` and `final` build and run independently.
- The initial checkpoint behaves like `1-containers/final`.
- The final checkpoint passes its automated tests.
- With GraalVM installed, the final checkpoint produces a local native executable.
- A host-native executable can use PostgreSQL started by Docker Compose.
- Docker Compose builds and runs the final native API image with PostgreSQL.
- Default Maven and Docker commands build the JVM application.
