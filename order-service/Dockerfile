# Imagen del modulo order-service. Se construye DESPUES de "mvn clean package" (copia el jar ya generado).
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/order-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
