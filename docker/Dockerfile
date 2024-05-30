# What base image to use, since we are using Java let's use the temurin image from our harbor registry
FROM harbor.nb.no/library/eclipse-temurin:21-jdk-alpine

# Set the maintainer of the image, makes it easier to know who to contact if there are any issues
LABEL maintainer="Magasin og Logistikk"

# Copy the jar file from the target folder to the root of the container, the wls.jar comes from GitHub workflow
COPY wls.jar app.jar

# Expose the port that the application is running on, this is defined in the src/main/resources/application.yml file
EXPOSE 8080

# Set the entrypoint for the container, this is the command that will be run when the container starts up
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]