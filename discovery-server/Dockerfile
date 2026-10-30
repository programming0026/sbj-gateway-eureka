# Imagen del modulo discovery-server. Se construye DESPUES de "mvn clean package" (copia el jar ya generado).
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/discovery-server-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
