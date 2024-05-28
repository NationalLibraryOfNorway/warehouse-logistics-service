FROM harbor.nb.no/library/eclipse-temurin:21-jdk-alpine
LABEL maintainer="Magasin og Logistikk"
COPY wls.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]