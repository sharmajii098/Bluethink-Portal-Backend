FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system --gid 10001 app && \
    useradd --system --uid 10001 --gid app app
COPY --from=build /workspace/target/*.jar app.jar
RUN chown app:app app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]