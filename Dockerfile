FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY AverServer.java .
CMD ["java","AverServer.java"]
