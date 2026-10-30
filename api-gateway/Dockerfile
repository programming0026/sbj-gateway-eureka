# Imagen del modulo api-gateway. Se construye DESPUES de "mvn clean package" (copia el jar ya generado).
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/api-gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
