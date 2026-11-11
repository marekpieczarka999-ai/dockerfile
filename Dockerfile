FROM eclipse-temurin:21-jre
WORKDIR /app
COPY AverServer.java .
CMD ["java","AverServer.java"]
