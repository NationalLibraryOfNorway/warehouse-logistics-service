FROM eclipse-temurin:21-jdk-alpine
COPY wls.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]