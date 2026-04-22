# syntax=docker/dockerfile:1.7

# ---- build ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Download Maven once into the image layer cache.
ARG MAVEN_VERSION=3.9.9
RUN set -eux; \
    apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && \
    curl -fsSL "https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        | tar -xz -C /opt && \
    ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn && \
    rm -rf /var/lib/apt/lists/*

# Prime the dependency cache before copying sources so subsequent builds are fast.
COPY pom.xml ./
COPY local-maven-repo ./local-maven-repo
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package && \
    cp target/stocktrace-*.jar /workspace/app.jar

# ---- runtime ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Non-root user.
RUN groupadd --system stocktrace && useradd --system --gid stocktrace --home /app stocktrace

COPY --from=build /workspace/app.jar /app/app.jar
RUN chown -R stocktrace:stocktrace /app
USER stocktrace

EXPOSE 8080

# Spring Boot actuator health endpoint (unauthenticated by default).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
