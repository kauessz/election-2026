FROM maven:3.9.11-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21.0.8_9-jre-jammy
WORKDIR /app
RUN groupadd --system election && useradd --system --gid election --home-dir /app election
COPY --from=builder --chown=election:election /build/target/election-api-0.0.1-SNAPSHOT.jar /app/app.jar
USER election
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
