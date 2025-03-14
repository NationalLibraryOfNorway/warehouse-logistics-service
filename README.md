# Hermes the Warehouse & Logistics Service

Hermes WLS (Warehouse and Logistics Service) functions as a middleware between NLNs ([National Library of Norway](https://nb.no/en "English version of the NLN website")) catalogues and storage systems.
The goal with the service is to unite all the storage systems and catalogues used at NLN with a common interface.

Benefits of this approach are:
- Decoupling of the storage systems and catalogues, which make it easier to change systems if needed.
- Makes it easier for end users to access material stored in different systems.
- Storage systems don't need to know which catalogue to inform about changes, as the service will handle this.

More features and benefits will be added as the service is developed.


# Table of Contents

1. [Hermes the Warehouse \& Logistics Service](#hermes-the-warehouse--logistics-service)
2. [Technologies](#technologies)
3. [Running the Application](#running-the-application)
   1. [Building and Running Locally](#building-and-running-locally)
      1. [Using Maven](#using-maven)
      2. [Using Docker](#using-docker)
      3. [Using an IDE](#using-an-ide)
   2. [Running Tests](#running-tests)
      1. [Running Tests in the Pipeline](#running-tests-in-the-pipeline)
      2. [Running Tests in an IDE](#running-tests-in-an-ide)
4. [Usage](#usage)
5. [Dependencies](#dependencies)
   1. [Local Dependencies](#local-dependencies)
   2. [Deployment Dependencies](#deployment-dependencies)
6. [Development](#development)
7. [Configuration](#configuration)
8. [Deployment](#deployment)
   1. [Deploying to Staging Environment](#deploying-to-staging-environment)
   2. [Deploying to Production Environment](#deploying-to-production-environment)
9. [Contact](#contact)
10. [License](#license)


# Technologies

Hermes WLS uses the following technologies:
- [Eclipse Temurin](https://adoptium.net "Eclipse Temurin homepage") for the Java runtime.
- [Kotlin](https://kotlinlang.org "Kotlin homepage") for the application code.
- [Maven](https://maven.apache.org "Maven homepage") for project management.
- [Spring Boot](https://spring.io/projects/spring-boot "Spring Boot homepage") for the application framework.
- [MongoDB](https://www.mongodb.com "MongoDB homepage") for data storage.
- [Keycloak](https://www.keycloak.org "Keycloak homepage") for client authentication and authorization.
- [Swagger](https://swagger.io "Swagger homepage") for API documentation.
- [Docker](https://www.docker.com "Docker homepage") for containerization.
- [Harbor](https://goharbor.io "Harbor homepage") for container registry.
- [Kubernetes](https://kubernetes.io "Kubernetes homepage") for deployment and orchestration.
- [Vault](https://www.vaultproject.io "Vault homepage") for secrets management.
- [GitHub Actions](https://github.com/features/actions "GitHub Actions homepage") for CI/CD.

As of now the service is in the early stages of development, and is not yet in production.
Therefore the technologies listed above are subject to change.
Check the [pom.xml](pom.xml "Link to project's POM file") file for the most up-to-date list of dependencies.
As well as the [Dockerfile](docker/Dockerfile "Link to project's Dockerfile") for the current Docker image setup.
And lastly the [Kubernetes deployment](k8s/prod/wls.yml "Link to project's k8s deployment file in production") for the current deployment setup.
You might also want to check the [GitHub Actions](.github/workflows/deploy-project.yaml "Link to project's CI/CD pipeline definition file") for the current CI/CD setup.

# Running the Application

The Warehouse Logistics Service is a Spring Boot application that can be run locally or in a container.
It is recommended to use Docker for local testing, as the service is designed to run in a containerized environment.
Additionally, this service depends on other applications, such as MongoDB, which can be spun up using provided [Docker Compose file](docker/compose.yaml "Link to project's Docker compose file").
For development an IDE such as [IntelliJ IDEA](https://www.jetbrains.com/idea/ "Link to JetBrains IntelliJ IDEA program") is recommended.

## Building and Running Locally

### Using Maven

Use following commands to build and run the application locally:

```shell
# Package the application, will execute the tests too
mvn clean package

# Run it locally
java -jar target/wls.jar
```

### Using Docker

After building the JAR file, it can be used to build a Docker image using the following commands:

```shell
# Move the jar to the Docker directory
cp target/wls.jar docker/wls.jar

# Use Docker Buildx to build the Docker Image
docker buildx build --platform linux/amd64 -t wls:latest docker/
```

***Caveats:***
- When building the Docker image outside of NLNs network the build will fail, as it won't be able to access internal Harbor instance.
  In this case change the `FROM` line in the [Dockerfile](docker/Dockerfile "Link to project's Dockerfile") to `FROM eclipse-temurin:21-jdk-alpine` and build the image locally.
- If you are outside of NLNs network your tests will fail, as an image for the dummy SynQ server is required for tests.
  There is currently no replacement available for this, except for building the image for Dummy SynQ manually and pointing to it in places where it's used.
- Do not attempt to push the image to Harbor manually, as it will fail.
  The image is built and pushed to Harbor automatically by the CI/CD pipeline.

A pre-built image can be found on NLNs internal Harbor instance under the `mlt` namespace.
The images are built based on the `main` branch as well as project `tags`, and can be pulled using the following command:

```shell
# Pull the latest image
docker pull harbor.nb.no/mlt/wls:latest

# Or pull a specific tag (either a GitHub tag or "main" for the latest main branch image)
docker pull harbor.nb.no/mlt/wls:<TAG>
```

With the image either built or pulled, it can be run using the following command:

```shell
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE="local-dev" harbor.nb.no/mlt/wls:<TAG>
```

### Using an IDE

For local development and testing an IDE like [IntelliJ IDEA](https://www.jetbrains.com/idea/) is recommended.
Its default Spring run configuration for the application works well.
To test the service with authentication make sure that dev version of the Keycloak is running, and set the `SPRING_PROFILES_ACTIVE` variable to `local-dev`.
In case you can't/won't use local Keycloak instance, then provide the `KEYCLOAK_ISSUER_URI` variable (see below) and set the `SPRING_PROFILES_ACTIVE` variable to `stage`.
Keycloak in `dev` and `stage` is set up with a test client `wls`.

## Running Tests

In order to run the tests, use the following command:

```shell
mvn clean test
```

It should run all the tests in the project and provide a report at the end.
You can see the results in both the console and in the `target/surefire-reports` directory.

### Running Tests in the Pipeline

The CI/CD pipeline will run the tests automatically when a pull request is created.
It will create a report and provide it in the pull request.
It can be accessed by clicking on the `Details` link in the `Checks` section for the `Deploy Project / JUnit Tests` check of the pull request.

### Running Tests in an IDE

In an IDE like IntelliJ IDEA, the tests can be run by right-clicking on the `src/test/kotlin` directory in the project and selecting `Run tests in 'kotlin'`.
Further configuration can be done in the `Run/Debug Configurations` menu.

In order to run tests with authentication, you will need to set the `spring.profiles.active` system property to `local-dev` in the run configuration.
This can be easily done by adding the following to the `VM options` field in the run configuration:

```shell
-ea  -Dspring.profiles.active=local-dev
```

Of course this requires you to have the dependent services running locally, see the [Local Dependencies](#local-dependencies) section for more information.
In short you will need to start the services using the provided [Docker Compose file](docker/compose.yaml "Link to project's Docker compose file").

# Usage

Hermes WLS provides a REST API for interacting with the service.
The API is documented using Swagger, and can be accessed by running the application and navigating to the following URL:
`http://localhost:8080/swagger`

As the staging and production environments are deployed on internal networks, the deployed API is not accessible from the outside.
If you need to access the API in these environments, you will need to use a VPN connection to NLN's network.
The API is accessible at the usual URL, with the `/hermes` suffix.


# Dependencies

## Local Dependencies

Regardless of what method you used to run the Hermes WLS, it has other services and applications that it depends on.
In order to run these, use the provided [Docker Compose file](docker/compose.yaml "Link to project's Docker compose file").
This will spin up the following services:
- MongoDB: database for the application
  - Can be accessed through provided mongo-express service at: `http://localhost:8081`
  - Use the following credentials to log in:
    - Username: `root`
    - Password: `toor`
- Keycloak: authentication and authorization service for the application
  - Can be accessed at: `http://localhost:8082`
  - Use the following credentials to log in:
    - Username: `root`
    - Password: `toor`

To start the services, run the following command:

```shell
cd docker
docker compose up -d

# If its the first time setting up, run the following command to setup replica set for MongoDB
chmod 755 ./setup-replicaset.sh
./setup-replicaset.sh
```

And to stop the services, run the following command:

```shell
docker compose down
```

## Deployment Dependencies

In addition to the local dependencies, the Hermes WLS also depends on the following services:
- Kubernetes: for deployment and orchestration of the application
- Harbor: for hosting the Docker image of the application
- Vault: for secrets management in the deployment pipeline

All of these services are managed by the NLN's Platform team, and are not needed for local development.
However, they are needed for deployment of the application to the staging and production environments.
MongoDB and Keycloak are also maintained by the Platform team, and are used in the deployed application.


# Development

The development of the Hermes WLS is done in "feature branches" that are merged into the `main` branch.
Name each feature branch after its JIRA code followed by a short summary of the feature.
For example `mlt-0018-add-readme`.

Make sure that your development tool supports the [EditorConfig](https://editorconfig.org "Link to EditorConfig homepage") standard, and use the included [`.editorconfig`](.editorconfig "Link to project's EditorConfig file") file.
IntelliJ IDEA supports formatting the code according to the `.editorconfig` file, and can be set up in the `Editor` settings.

Additionally you should run the tests, and run Spotless to ensure that the code is formatted correctly.
To run tests and spotless, use the following commands:

```shell
mvn clean test
mvn spotless:apply
```

The CI/CD pipelnine will run these commands automatically when a pull request is created.
Although it's better to run them on your machine before pushing as it will save time and resources.

# Configuration

The following environment variables are relevant to configuring the application:

- `KEYCLOAK_ISSUER_URI`: Is used to point at the Keycloak server used for authentication (required)
- `KEYCLOAK_TOKEN_AUD`: Is used to set the audience of the Keycloak JWT token, it must match with the issued token audience value, which is different between environments (required)
- `SPRING_PROFILES_ACTIVE`: Is used to set the active Spring profile, use `local-dev` or `stage` for testing authentication (optional, default is `pipeline`)
- `MONGODB_USERNAME`: Is the username used for connecting to MongoDB (required)
- `MONGODB_PASSWORD`: Is the password used for connecting to MongoDB (required)
- `MONGODB_DATABASE`: Is the name of the database that Hermes should use (required)
- `MONGODB_URL`: Is the base URL at which a MongoDB instance runs (required)
- `SYNQ_BASE_URL`: Is the base URL used for communicating against SynQ (required)
- `EMAIL_SERVER`: Is the URL used for the email adapters email server (optional)
- `EMAIL_PORT`: Is the port used for the email server (optional)

# Deployment

The section [Running the Application](#running-the-application) describes how to run the application locally.
Therefore this section will focus on how to deploy the application to the staging and production environments.

Deployment to both environments is handled by their respective Kubernetes deployment files.
They describe the deployment, service, and ingress for the application.
There is very little difference between the two files, as the environments are very similar.
They mostly deal with resource limits and requests, as well as the number of replicas.

In both cases the deployment is handled by the CI/CD pipeline.

## Deploying to Staging Environment

To deploy the application to the staging environment, push new changes to the `main` branch.
The repository is set up to only accept merges to the `main` branch through pull requests.
Therefore in order to deploy to the staging environment, create a pull request and merge it to the `main` branch.
Actions in pull request should test the application to make ensure that it is working as expected.

When a pull request is merged to the `main` branch, the CI/CD pipeline will build the application, create a Docker image, and push it to Harbor.
Then the image will be deployed to the staging cluster in NLN's internal Kubernetes system.

## Deploying to Production Environment

To deploy the application to the production environment, create a new tag in the repository.
The tag should be in the format `vX.Y.Z`, where `X`, `Y`, and `Z` are numbers, following the semantic versioning standard.

This will trigger the CI/CD pipeline which will deploy the application to the production environment.
This is quite similar to the staging deployment.


# Contact

The project is maintained by the [National Library of Norway](https://github.com/NationalLibraryOfNorway/ "Link to the National Library of Norway's GitHub organization") organization.
It is owned by the "Warehouse and Logistics" team (MLT).

For questions or issues, please create a new issue in the repository.

You can also contact the team by email at `mlt at nb dot no`.


# License

This project is licensed under the [MIT License](LICENSE.md "Link to project's LICENSE file").
