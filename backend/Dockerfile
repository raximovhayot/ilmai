FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x ./gradlew && ./gradlew --version --no-daemon

COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon \
 && cd build/libs \
 && JAR_FILE="$(ls *-SNAPSHOT.jar 2>/dev/null || ls *.jar | head -n1)" \
 && java -Djarmode=tools -jar "$JAR_FILE" extract --layers --launcher --destination /workspace/extracted

FROM eclipse-temurin:25-jre AS runtime

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 1000 ilmai \
 && useradd --system --uid 1000 --gid ilmai --no-create-home --shell /usr/sbin/nologin ilmai \
 && mkdir -p /opt/ilmai \
 && chown -R ilmai:ilmai /opt/ilmai

WORKDIR /opt/ilmai

COPY --from=builder --chown=ilmai:ilmai /workspace/extracted/dependencies/         ./
COPY --from=builder --chown=ilmai:ilmai /workspace/extracted/spring-boot-loader/   ./
COPY --from=builder --chown=ilmai:ilmai /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=ilmai:ilmai /workspace/extracted/application/          ./

USER ilmai

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    SERVER_PORT=8080

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl --fail --silent --show-error http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
