# Warehouse Logistics Service

Hermes WLS (Warehouse and Logistics Service) functions as a middleware between NLNs (National Library of Norway) catalogues and storage systems.
The goal with the service is to unite the different storage systems and magazines under the same service.


## Running the application

Hermes WLS runs on Java 21.

### Building

If using CLI exclusively, you can build and run with the following commands:

```shell
# Build the jar with the maven-jar-plugin
mvn jar:jar

# Run it locally
cd target/
java -jar wls.jar
```

Otherwise we recommend using [IntelliJ IDEA](https://www.jetbrains.com/idea/) to both build and deploy locally.
The default Spring run configuration for the application works well.

### Configuration


The following environment variables are relevant to configuring the application:

- `KEYCLOAK_ISSUER_URI`: Is used to point at the Keycloak server used for authentication
  - required for local testing, and when using it in staging
- `KEYCLOAK_ISSUER_URI_PROD`: Is used to point at the Keycloak server used for authentication
  - required when deployed to k8s and used in production environments, usually with the "prod" profile

### Deploying

Deploying is as simple as running the jar.
When running locally, make sure to activate the "stage" profile with Spring.

```shell
java -DKEYCLOAK_ISSUER_URI=<url> -Dspring.profiles.active=stage -jar wls.jar
```

## License

©️2024 National Library of Norway. All rights reserved.
