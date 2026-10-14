# --- Étape 1 : compilation ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline

COPY src src
# Les tests sont ignorés ici : ils ont besoin de Docker (Testcontainers)
RUN ./mvnw -q -DskipTests package

# --- Étape 2 : image d'exécution ---
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser

EXPOSE 3002
ENTRYPOINT ["java", "-jar", "app.jar"]