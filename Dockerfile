# Build en dos etapas: compila con Maven, y la imagen final solo lleva el JRE
# y el jar. Railway detecta el Dockerfile automaticamente y lo usa en lugar de
# Nixpacks.
#
# Por que Docker y no nixpacks.toml: con Nixpacks el arranque ocurre en un
# directorio donde target/ no existe y el wildcard del jar tampoco expande.
# Con el jar copiado a una ruta absoluta (/app/app.jar) no hay forma de que
# falle por rutas. (Leccion de ligadecampeones-backend.)

# ---------- Etapa 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Primero solo el pom, para que Maven baje las dependencias en una capa que
# se reutiliza si solo cambia el codigo.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

# ---------- Etapa 2: runtime ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /build/target/inventarioclean-backend-*.jar app.jar

EXPOSE 8080

# Sin -Dserver.port: la app escucha en el 8080 de application-prod.yml, que es
# el Target Port del dominio en Railway. Usar ${PORT} aqui rompe el deploy
# (leccion de ligadecampeones-backend: Railway siempre inyecta PORT con su
# puerto interno y el proxy manda al 8080 -> 502).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
