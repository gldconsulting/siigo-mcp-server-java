# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
LABEL org.opencontainers.image.title="GLD Consulting Siigo MCP Server" \
      org.opencontainers.image.source="https://github.com/gldconsulting/siigo-mcp-server-java" \
      org.opencontainers.image.licenses="MIT"
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/siigo-mcp-server-*.jar /app/siigo-mcp-server.jar
COPY --chown=10001:10001 LICENSE /app/LICENSE
USER 10001:10001
ENV SIIGO_MCP_TRANSPORT=stdio
# Metadata only. Publish HTTP to loopback explicitly; do not expose this MCP publicly.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/siigo-mcp-server.jar"]
