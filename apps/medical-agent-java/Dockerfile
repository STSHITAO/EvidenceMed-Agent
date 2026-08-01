FROM maven:3.9.12-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 medical
COPY --from=build /workspace/target/medical-agent-java-*.jar /app/application.jar
USER medical
EXPOSE 9100
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/application.jar"]
