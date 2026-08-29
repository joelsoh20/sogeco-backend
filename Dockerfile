# Build : le wrapper Maven telecharge lui-meme la bonne version de Maven,
# aucun outil externe requis dans l'image de build.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B -DskipTests package

# ---------------------------------------------------------------------

# Execution : JRE seul, pas le JDK complet — image plus petite, surface
# d'attaque reduite. Utilisateur non-root par convention de securite.
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --create-home --home-dir /app appuser
COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
