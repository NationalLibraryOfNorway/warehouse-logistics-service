# Warehouse Logistics Service

Hermes WLS (Warehouse and Logistics Service) functions as a middleware between NLNs (National Library of Norway) catalogues and storage systems.
The goal with the service is to unite the different storage systems and catalogues with a common interface.
This should help us in decoupling the storage systems and catalogues from each other and make it easier to introduce new ones in the future.

## Running the application

Hermes WLS runs on Java 21.

### Building

To build and run this application use Maven and the following commands:

```shell
# Build the jar with the maven-jar-plugin
mvn jar:jar

# Run it locally
cd target/
java -jar wls.jar
```

It's possible to build a Docker image from source using the following commands:
```
... commands
```
A pre-built image can be found on NLNs local Harbor instance under the `mlt` namespace.

For local development and testing an IDE like [IntelliJ IDEA](https://www.jetbrains.com/idea/) is recommended.
Its default Spring run configuration for the application works well.
To test the service with authentication set the "Active profiles" variable to `stage`, and provide the `KEYCLOAK_ISSUER_URI` variable (see below).
Keycloak in stage is set up with a test client.

### Configuration


The following environment variables are relevant to configuring the application:

- `KEYCLOAK_ISSUER_URI`: Is used to point at the Keycloak server used for authentication
  - required for local testing, and when using it in staging
- `KEYCLOAK_ISSUER_URI_PROD`: Is used to point at the Keycloak server used for authentication
  - required when deployed to k8s and used in production environments, usually with the "prod" profile

### Deploying

When deploying locally, make sure to activate the "stage" profile with Spring, and provide the KEYCLOAK_ISSUER_URI.
Easiest way to test the functionality is to use the provided Swagger UI at http://localhost:8080/swagger

```shell
java -DKEYCLOAK_ISSUER_URI=<url> -Dspring.profiles.active=stage -jar wls.jar
```

TODO - Pipeline deployment

## License

©️2024 National Library of Norway. All rights reserved.
