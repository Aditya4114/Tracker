# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace/app

# Copy root pom and wrapper
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Copy modules
COPY common-events common-events
COPY monolith monolith
COPY gateway gateway
COPY auth-service auth-service
COPY ingestion-service ingestion-service
COPY intelligence-service intelligence-service
COPY core-service core-service

ARG MODULE
RUN ./mvnw clean package -pl ${MODULE} -am -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
ARG MODULE
COPY --from=builder /workspace/app/${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080 8081 8082 8083 8084 8000
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
