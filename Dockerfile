# syntax=docker/dockerfile:1

# Stage 1: Build application
FROM maven:3.9.6-eclipse-temurin-21-jammy AS builder
WORKDIR /workspace

# Cache dependency layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application artifact
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-jammy
LABEL maintainer="Alexandre Motologa"
LABEL description="MiniS3: Lightweight S3-Compatible Content-Addressable Storage Engine"

WORKDIR /app

# Create non-root user and persistent storage directory
RUN groupadd -r minis3 && useradd -r -g minis3 -u 1001 minis3 && \
    mkdir -p /data/minis3 && \
    chown -R minis3:minis3 /app /data/minis3

COPY --from=builder --chown=minis3:minis3 /workspace/target/minis3-1.0.0-SNAPSHOT.jar app.jar

USER minis3:minis3

ENV SERVER_PORT=8080 \
    MINIS3_STORAGE_ROOT_DIR=/data/minis3 \
    MINIS3_STORAGE_CHUNK_SIZE_BYTES=4194304 \
    MINIS3_STORAGE_ZSTD_COMPRESSION_LEVEL=3 \
    MINIS3_AUTH_ACCESS_KEY=minis3admin \
    MINIS3_AUTH_SECRET_KEY=minis3secret \
    MINIS3_AUTH_ALLOW_ANONYMOUS=true \
    MINIS3_TELEMETRY_SAMPLE_INTERVAL_SECONDS=5

EXPOSE 8080

VOLUME ["/data/minis3"]

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/api/admin/stats || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-jar", "app.jar"]
