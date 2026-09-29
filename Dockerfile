# ---- Etapa 1: build con Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Etapa 2: imagen de ejecucion ----
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S campuslab && adduser -S campuslab -G campuslab
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown campuslab:campuslab app.jar
USER campuslab

EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
