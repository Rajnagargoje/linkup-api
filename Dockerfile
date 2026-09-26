FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system linkup && useradd --system --gid linkup linkup
COPY --from=build --chown=linkup:linkup /build/target/linkup-api-0.0.1-SNAPSHOT.jar /app/app.jar
USER linkup
ENV SPRING_PROFILES_ACTIVE=render
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx320m -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=32m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -XX:ActiveProcessorCount=1"
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
