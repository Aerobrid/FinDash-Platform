# Build Stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
# Install common first then package gateway
RUN mvn install -pl common -am -DskipTests
RUN mvn package -pl api-gateway -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build --chown=appuser:appgroup /app/api-gateway/target/api-gateway-*.jar app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]