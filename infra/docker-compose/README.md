# Local dev infra

Postgres, Kafka and Schema Registry for running a service locally.

```
docker compose -f infra/docker-compose/docker-compose.yml up -d
```

Run authentication-service against it:

```
SPRING_PROFILES_ACTIVE=local ./mvnw -pl services/authentication-service -am spring-boot:run
```

Liquibase applies migrations automatically on startup. Once it's up, Swagger UI is at
http://localhost:8080/swagger-ui.html.
