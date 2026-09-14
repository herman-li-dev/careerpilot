# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S careerpilot && adduser -S careerpilot -G careerpilot
WORKDIR /app

COPY --from=build /workspace/target/careerpilot-0.0.1-SNAPSHOT.jar app.jar

USER careerpilot
EXPOSE 8123
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
