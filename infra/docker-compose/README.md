# Local dev infra

Postgres, Kafka, Schema Registry and Redis for running services locally.

```
docker compose -f infra/docker-compose/docker-compose.yml up -d
```

Run authentication-service against it:

```
SPRING_PROFILES_ACTIVE=local ./mvnw -pl services/authentication-service -am spring-boot:run
```

Liquibase applies migrations automatically on startup. Once it's up, Swagger UI is at
http://localhost:8081/swagger-ui.html (authentication-service listens on 8081, not the Spring
Boot default 8080, so api-gateway can own 8080 as the public entry point).

Run api-gateway (needs authentication-service already running, for JWKS lookup and routing):

```
./mvnw -pl services/api-gateway -am spring-boot:run
```

Then hit the same endpoints through the gateway at http://localhost:8080/api/v1/auth/....
