# syntax=docker/dockerfile:1

FROM eclipse-temurin:21.0.12.1_1-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon installDist

FROM eclipse-temurin:21.0.12.1_1-jre
WORKDIR /app
COPY --from=build /src/build/install/okspkur .
EXPOSE 8080
ENTRYPOINT ["bin/okspkur"]
