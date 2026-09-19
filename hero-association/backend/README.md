# Hero Association Backend

The Hero Association backend is a Quarkus API backed by PostgreSQL. It manages
hero registrations with a unique alias.

## Prerequisites

- Java 25 LTS
- Docker with Docker Compose

## Test

From this directory, run:

```bash
./mvnw test
```

The test suite starts PostgreSQL automatically through Quarkus Dev Services.

## Run with Docker Compose

```bash
docker compose up --build
```

The API is available at `http://localhost:8080/api/v1/heroes`.

To stop the containers while retaining hero data:

```bash
docker compose down
```

To also remove the local PostgreSQL volume:

```bash
docker compose down --volumes
```

## API

A hero has `id`, `name`, `alias`, and `power`. `name`, `alias`, and `power`
are required, and aliases must be unique.

- `POST /api/v1/heroes`
- `GET /api/v1/heroes`
- `GET /api/v1/heroes/{id}`
- `PUT /api/v1/heroes/{id}`
- `PATCH /api/v1/heroes/{id}`
- `DELETE /api/v1/heroes/{id}`

Create a hero:

```bash
curl --request POST http://localhost:8080/api/v1/heroes \
  --header 'Content-Type: application/json' \
  --data '{
    "name": "Anakin Skywalker",
    "alias": "Darth Vader",
    "power": "The Force"
  }'
```
