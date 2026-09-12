# Multi-stage so the runtime image carries only a JRE and the jar, not Maven and the build cache.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies are resolved before the sources are copied, so editing code does not invalidate the
# dependency layer and rebuilds stay fast.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Running as a non-root user limits the blast radius if the process is ever compromised.
RUN addgroup -S settleup && adduser -S settleup -G settleup
COPY --from=build /build/target/settleup-1.0.0.jar app.jar
USER settleup

EXPOSE 8080

# Containers get a slice of the host, not the whole machine; without this the JVM can size its heap
# against the host's total RAM and get killed by the OOM reaper.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -q --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
