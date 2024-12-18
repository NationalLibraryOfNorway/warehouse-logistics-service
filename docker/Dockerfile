# Use temurin as base image, getting it from local harbor registry, since this is a Kotlin application
# To keep the bloat to a minumum, it uses the alpine version of the image
# Should consider removing the french language pack as well: "sudo rm -fr /*"
FROM harbor.nb.no/library/eclipse-temurin:21-jdk-alpine

# Set metadata for the image, this is used to more easily identify the image and who created it
LABEL maintainer="\"Magasin og Logistikk\" team at NLN"
LABEL description="Hermes WLS (Warehouse and Logistics Service) functions as a \
middleware between NLNs (National Library of Norway) catalogues and storage systems"

# Copy the jar file from the target folder to the root of the container
# GitHub workflow generates the wls.jar file
# When building manually generate the jar file with "mvn clean package"
#     and copy it to the docker folder with "cp target/wls.jar docker/wls.jar"
COPY wls.jar app.jar

# Mark the port that the application is listenting on
# This port is defined in the src/main/resources/application.yml file
EXPOSE 8080

# Add a healthcheck to the container, this is used to check if the container is running as expected
# This is not a replacement for health check in kubernetes
# However it is convenient when running locally, or in a purely Docker based environment
HEALTHCHECK --start-period=40s --retries=5 \
  CMD curl --fail --silent localhost:8081/actuator/health | grep UP || exit 1

# Set the entrypoint for the container, this is the command that will be run when the container starts up
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
