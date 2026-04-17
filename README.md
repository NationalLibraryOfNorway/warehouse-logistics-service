# Hermes the Warehouse & Logistics Service


Hermes WLS (Warehouse and Logistics Service) functions as middleware between NLNs ([National Library of Norway](https://nb.no/en "English version of the NLN website")) catalogues and storage systems.
The goal with the service is to unite all the storage systems and catalogues used at NLN with a common interface.

The benefits of this approach are:
- Decoupling of the storage systems and catalogues, which makes it easier to change systems if needed.
- Makes it easier for end users to access material stored in different systems.
- Storage systems don't need to know which catalogue to inform about changes, as the service will handle this.

More features and benefits will be added as the service is developed.


# Table of Contents


1. [Hermes the Warehouse \& Logistics Service](#hermes-the-warehouse--logistics-service)
1. [Technologies](#technologies)
1. [Running the Application](#running-the-application)
   1. [Building and Running Locally](#building-and-running-locally)
      1. [Using Maven](#using-maven)
      1. [Using Make](#using-make)
      1. [Using Docker](#using-docker)
      1. [Using containerd + BuildKit (nerdctl)](#using-containerd--buildkit-nerdctl)
      1. [Using an IDE](#using-an-ide)
   1. [Running Tests](#running-tests)
      1. [Running Tests in the Pipeline](#running-tests-in-the-pipeline)
      1. [Running Tests in an IDE](#running-tests-in-an-ide)
1. [Usage](#usage)
1. [Dependencies](#dependencies)
   1. [Local Dependencies](#local-dependencies)
   1. [Deployment Dependencies](#deployment-dependencies)
1. [Development](#development)
1. [Configuration](#configuration)
1. [Deployment](#deployment)
   1. [Deploying to Staging Environment](#deploying-to-staging-environment)
   1. [Deploying to Production Environment](#deploying-to-production-environment)
1. [Contact](#contact)
1. [License](#license)


# Technologies


Hermes WLS uses the following technologies:
- [Eclipse Temurin](https://adoptium.net "Eclipse Temurin homepage") for the Java runtime.
- [Kotlin](https://kotlinlang.org "Kotlin homepage") for the application code.
- [Maven](https://maven.apache.org "Maven homepage") for project management.
- [Spring Boot](https://spring.io/projects/spring-boot "Spring Boot homepage") for the application framework.
- [MongoDB](https://www.mongodb.com "MongoDB homepage") for data storage.
- [Kafka](https://kafka.apache.org "Kafka homepage") for event streaming.
- [Keycloak](https://www.keycloak.org "Keycloak homepage") for client authentication and authorization.
- [Swagger](https://swagger.io "Swagger homepage") for API documentation.
- [Docker](https://www.docker.com "Docker homepage") for containerization.
- [Harbor](https://goharbor.io "Harbor homepage") for container registry.
- [Kubernetes](https://kubernetes.io "Kubernetes homepage") for deployment and orchestration.
- [Argo CD](https://argo-cd.readthedocs.io/en/stable/ "Argo CD homepage") for application deployment from GitHub and management.
- [Vault](https://www.vaultproject.io "Vault homepage") for secrets management.
- [GitHub Actions](https://github.com/features/actions "GitHub Actions homepage") for CI/CD.

As of now, the service is in the early stages of development, so there's a chance of major changes occurring.
Check the [pom.xml](pom.xml "Link to project's POM file") file for the most up-to-date list of dependencies.
As well as the [Dockerfile](docker/Dockerfile "Link to project's Dockerfile") for the current Docker image setup.
You might also want to check the [GitHub Actions](.github/workflows/deploy-project.yaml "Link to project's CI/CD pipeline definition file") for the current CI/CD setup.


# Running the Application


The Warehouse Logistics Service is a Spring Boot application that can be run locally or in a container.
It is recommended to use Docker or containerd (via `nerdctl`) for local testing, as the service is designed to run in a containerized environment.
Additionally, this service depends on other applications, such as MongoDB, which can be spun up using the provided [Docker Compose file](docker/compose.yaml "Link to project's Docker compose file").
For development an IDE such as [IntelliJ IDEA](https://www.jetbrains.com/idea/ "Link to JetBrains IntelliJ IDEA program") is highly recommended.

## Building and Running Locally

### Using Maven

Use these commands to build and run the application locally:

```shell
# Package the application, will execute the tests too
mvn clean package

# Package the application and skip tests
mvn clean package -DskipTests

# Run it locally
java -jar target/wls.jar
```

Tests in this project use Testcontainers and require a working Docker runtime (raw containerd won't work, Podman and similar require setup) available on your machine.

### Using Make

If you prefer `make`, you can wrap the same Maven and `nerdctl` or `docker` commands in local helper targets.
The repository includes these targets in a dedicated [Makefile](Makefile).
It comes with sane defaults that should work for most users, but allows you to override them:
- `CONTAINER_RUNTIME` What runtime to use (default is `nerdctl`, most likely to require adjustment)
- `COMPOSE_FILE` Path to the compose file (default is `docker/compose.yaml`)
- `COMPOSE_CMD` Base command for working with compose (default is `$(CONTAINER_RUNTIME) compose -f $(COMPOSE_FILE)`)
- `IMAGE` Name for the built application image (default is `wls:local`)
- `APP_CONTAINER` Name for the application container (default is `wls-local`)
- `SPRING_PROFILE` Spring profile to use (default is `local-dev`)

You can override these variables like this:

```shell
# Use Docker runtime for a single command
make CONTAINER_RUNTIME=docker deps-up

# Make all commands use Docker runtime by default
export CONTAINER_RUNTIME=docker
make deps-up
make test
...
```

Most useful targets:

- `start`: Start the app and its dependencies, but no tests, useful for spinning up full environment
- `stop`: Stop the app and its dependencies, without cleanup, useful for stopping for a while
- `refresh`: Refresh the app, keep dependencies running, but rebuild the app image, useful for testing changes as you work
- `restart`: Stop everything and restart, useful if app or dependencies need a restart
- `cleanup`: Stop, then clean up everything, but do not restart, useful for a full reset or if you want to get rid of everything
- `clean-restart`: Same as cleanup, but will also restart the environment, useful if some remaining state is causing issues, or you made changes that you want to remove
- `git`: Commit and push to remote repo, runs Spotless, does tests, makes a commit and pushes, safety checks to prevent working on `main`

### Using Docker

After building the JAR file, it can be used to build a Docker image using the following commands:

```shell
# Move the jar to the Docker directory
cp target/wls.jar docker/wls.jar

# Build a local image
docker build -t wls:local docker/
```

If you need local setup of Docker on Arch Linux, install and enable the required tooling:

```bash
pacman -S --needed docker docker-buildx docker-compose
systemctl enable --now docker
```

***Caveats:***
- When building the Docker image outside NLNs network, the build will fail.
  That's because it won't be able to access the internal Harbor instance which is used to pull the base image.
  In this case change the `FROM` line in the [Dockerfile](docker/Dockerfile "Link to project's Dockerfile") to `FROM eclipse-temurin:21-jdk-noble` and build the image locally.
- Do not attempt to push the image to Harbor manually, as it will fail.
  The image is built and pushed to Harbor automatically by the CI/CD pipeline.

A pre-built image can be found on NLNs internal Harbor instance under the `mlt` namespace.
The images are built based on the `main` branch, tag releases (`vX.Y.Z`), and hash of the commit that triggered the build.

With the image either built or pulled, WLS can be run using the following commands:

```shell
# For pulled image
docker run --rm -e SPRING_PROFILES_ACTIVE="local-dev" --network host harbor.nb.no/mlt/wls:<TAG>

# For locally built image
docker run --rm -e SPRING_PROFILES_ACTIVE="local-dev" --network host wls:local
```

### Using containerd + BuildKit (nerdctl)

After building the JAR file, it can be used to build an image with `nerdctl` (which uses BuildKit):

```shell
# Move the jar to the Docker directory
cp target/wls.jar docker/wls.jar

# Build a local image
nerdctl build -t wls:local docker/
```

If you need local setup of "rootful" `containerd` on Arch Linux, install and enable the required tooling:

```shell
pacman -S --needed containerd runc nerdctl cni-plugins buildkit iptables-nft rootlesskit
systemctl enable --now containerd
systemctl enable --now buildkit
```

The same caveats from [Using Docker](#using-docker) apply when building outside NLN's network.

With the image either built or pulled, WLS can be run using the following commands:

```shell
# For pulled image
nerdctl run --rm -e SPRING_PROFILES_ACTIVE="local-dev" --network host harbor.nb.no/mlt/wls:<TAG>

# For locally built image
nerdctl run --rm  -e SPRING_PROFILES_ACTIVE="local-dev" --network host wls:local
```

### Using an IDE

For local development and testing an IDE like [IntelliJ IDEA](https://www.jetbrains.com/idea/) is recommended.
Its default Spring run configuration for the application works well, just set the `SPRING_PROFILES_ACTIVE` / `Active Profiles` variable to `local-dev`.
Ensure that you have containers from [Docker Compose file](docker/compose.yaml "Link to project Docker compose file") running before running the application.

You can also use the provided run config[](.run/run-hermes-locally.run.xml) to run the application locally.
It comes with all environment variables set to default values, which you can copy to another run config and override values as needed.
Be careful not to commit modified run configs to the repository.

App has a default configuration for local development however, in your run configuration you can override the default values by setting the environment variables in the run configuration.
Currently supported config values are listed in the [Configuration](#configuration) section.

## Running Tests

To run the tests, use the following command:

```shell
mvn clean test
```

See the note in [Using Maven](#using-maven) about the Docker runtime requirement for Testcontainers.

It should run all the tests in the project and provide a report at the end.
You can see the results in both the console and in the `target/surefire-reports` directory.

### Running Tests in the Pipeline

The CI/CD pipeline will run the tests automatically when a pull request is created.
It will create a report and provide it in the pull request.
It can be accessed by clicking on the `Details` link in the `Checks` section for the `Deploy Project / JUnit Tests` check of the pull request.

As we do not have a test instance of Keycloak, tests requiring authentication are disabled when Spring Profile is set to `pipeline`.

### Running Tests in an IDE

In an IDE like IntelliJ IDEA, the tests can be run by right-clicking on the `src/test/kotlin` directory in the project and selecting `Run tests in 'kotlin'`.
Further configuration can be done in the `Run/Debug Configurations` menu.

To run tests with authentication, you will need to set the `spring.profiles.active` system property to `local-dev` in the run configuration.
This can be easily done by adding the following to the `VM options` field in the run configuration:

```shell
-ea  -Dspring.profiles.active=local-dev
```

We have also a default [run configuration](.run/run-all-tests.run.xml) for running all tests in a local environment.
It comes with all the required configurations already set up, so we recommend using it instead of creating a new one.

Of course this requires you to have the services running locally, see the [Local Dependencies](#local-dependencies) section for more information.


# Usage


Hermes WLS provides a REST API for interacting with the service.
The API is documented using Swagger, and can be accessed by running the application and navigating to the following URL:
`http://localhost:8080/hermes/swagger`

As the staging and production environments are deployed on internal networks, the deployed API is not accessible from the outside.
If you need to access the API in these environments, you will need to use a VPN connection to NLN's network.
The API is accessible at the usual URL, with the `/hermes` suffix.


# Dependencies


## Local Dependencies

Regardless of what method you used to run the Hermes WLS, it has other services and applications that it depends on.
To run these, use the provided [Docker Compose file](docker/compose.yaml "Link to project's Docker compose file").
The compose stack can be run with either `<docker|nerdctl> compose`.
This will spin up the following services:

- MongoDB: database for the application
  - The compose setup provisions two databases:
    - **wls**: main application database used by Hermes WLS
      - Username: `wls`, Password: `slw`
      - Example URI: `mongodb://wls:slw@localhost/wls?replicaSet=rs0&authSource=wls&readPreference=primary&w=majority&journal=true&retryWrites=true&directConnection=true`
    - **moveit**: MoveIt companion database (MoveIt depends on WLS to function)
      - Username: `moveit`, Password: `tievom`
      - Example URI: `mongodb://moveit:tievom@localhost/moveit?replicaSet=rs0&authSource=moveit&readPreference=primary&w=majority&journal=true&retryWrites=true&directConnection=true`
- Email: uses a fake SMTP server for testing email functionality locally
    - Can be accessed at: `http://localhost:8081`
- Keycloak: authentication and authorization service for the application
  - Can be accessed at: `http://localhost:8082`
  - Use the following credentials to log in:
    - Username: `wls`
    - Password: `slw`
- Kafka: a message queue system for handling inventory statistics messages
  - Can be accessed at: `kafka:9092` (requires `/etc/hosts` entry, see below) or `localhost:9092`
  - You can use the Kafka plugin for IntelliJ to view topics, queues, and their contents
    - [Kafka plugin homepage](https://plugins.jetbrains.com/plugin/21704-kafka "Kafka plugin homepage")
    - Connect to either `kafka:9092` or `localhost:9092` - both work
- Mockoon: a service used for mocking web server endpoints, used to test callback functionality
  - Endpoints are available at: `http://localhost:80/item` and `http://localhost:80/order`
  - See below on how to enable mapping `localhost` to `callback-wls.no`
  - To read the logs with request and response data, run:
    - `<docker|nerdctl> compose logs -f mockoon` to use compose log streaming by service name
    - `<docker|nerdctl> logs -f wls-mockoon-1` to use container log streaming by container name
    - Make sure that the container name matches the actual name from running `<docker|nerdctl> compose up`
    - You can check it using `<docker|nerdctl> ps`

For MongoDB replica set auth in local Compose, provide a keyfile at `docker/mongo/secrets/keyfile.key` before startup.
The Compose setup mounts this file read-only and copies it into container-local tmpfs at runtime, so host file ownership is never changed.
Run `bash docker/setup-mongo-keyfile.sh` before startup: it creates the keyfile if missing and validates it if already present.

To start the services, run the following command:

```shell
cd docker
<docker|nerdctl> compose up -d

# Alternatively from project root
<docker|nerdctl> compose -f docker/compose.yaml up -d
```

Additionally, to use the Mockoon service for mocking and logging callbacks to catalogue systems, you will need to make it discoverable on the host machine.
The default `MONGODB_URI` uses `directConnection=true`, which bypasses replica set host discovery, so a `mongo-db` hosts entry is not strictly required when using the default configuration.
However, for convenience and choice between `localhost:27017` and `mongo-db:27017` you can add the `mongo-db` entry as shown below.
You can also add an entry for Kafka to have a consistent connection string (`kafka:9092`) everywhere.
Add the following lines to your `/etc/hosts` file if you are on Linux --- if you are using Windows or Mac switch to a real OS --- and restart your machine:

```
127.0.0.1 callback-wls.no
127.0.0.1 mongo-db
127.0.0.1 kafka
```

To stop the services, run the following command:

```shell
<docker|nerdctl> compose down

# Alternatively from project root
<docker|nerdctl> compose -f docker/compose.yaml down

# Optional step for clean restart (remove volumes in extra command as `system prune --volumes` does not work)
<docker|nerdctl> system prune -af
<docker|nerdctl> volume prune -af
```

## Deployment Dependencies

In addition to the local dependencies, the Hermes WLS also depends on the following services:
- Kubernetes: for deployment and orchestration of the application
- Harbor: for hosting the Docker image of the application
- Vault: for secrets management in the deployment pipeline

All of these services are managed by the NLN's Platform team and are not needed for local development.
However, they are needed to deploy the application to the staging and production environments.
The Platform team also maintains MongoDB, Kafka, Email Server, and Keycloak in the staging and production environments.


# Development


The development of the Hermes WLS is done in "feature branches" that are merged into the `main` branch.
Name each feature branch after its JIRA code followed by a short summary of the feature.
For example `mlt-0018-add-readme`.

Make sure that your development tool supports the [EditorConfig](https://editorconfig.org "Link to EditorConfig homepage") standard, and use the included [`.editorconfig`](.editorconfig "Link to project's EditorConfig file") file.
IntelliJ IDEA supports formatting the code according to the `.editorconfig` file, and can be set up in the `Editor` settings.
Furthermore, we use Spotless to format code automatically before each commit.
To set up Spotless, you can use IntelliJ's [Spotless Applier plugin](https://plugins.jetbrains.com/plugin/22455-spotless-applier "Link to Spotless Applier plugin") or run the following command:

```shell
mvn spotless:apply
```

Lastly, you should run the tests to ensure that the code is running as expected.
The CI/CD pipeline will run the tests automatically when a pull request is created.
However, it's better to run them on your machine before pushing as it will save time and resources.
To run tests, use the following commands:

```shell
mvn clean test
```


# Configuration


The following environment variables are relevant to configuring the application.
When running the application locally, or in a pipeline, all of these variables are set automatically.
However, when deploying to staging or production, they must be set manually.

- `KAFKA_BOOTSTRAP_SERVERS`: Is used to set the Kafka bootstrap servers (default is `localhost:9092`)
- `KEYCLOAK_ISSUER_URI`: Is used to point at the Keycloak server used for authentication (default is `http://localhost:8082/realms/mlt-local`)
- `KEYCLOAK_TOKEN_AUD`: Is used to set the audience of the Keycloak JWT token, it must match with the issued token audience value, which is different between environments (default is `http://localhost:8080`)
- `SPRING_PROFILES_ACTIVE`: Is used to set the active Spring profile, use `local-dev`, `stage` or `prod` (default is `pipeline`)
- `EMAIL_SERVER`: Is the URL to email server used to send emails (default is `localhost`)
- `EMAIL_PORT`: Is the port used by the email server (default is `1025`)
- `MONGODB_URI`: Is the connection URI for our MongoDB instances (default is `mongodb://wls:slw@localhost/wls?replicaSet=rs0&authSource=wls&readPreference=primary&w=majority&journal=true&retryWrites=true&directConnection=true`).
  The URI options used are used in local, staging, and production environments, and are as follows:
  - `replicaSet=rs0`: name of the replica set; required for transactions and change streams
  - `authSource=wls`: database used to authenticate the connecting user
  - `readPreference=primary`: always read from the primary replica set member
  - `w=majority`: write concern; waits for acknowledgement from a majority of replica set members before confirming
  - `journal=true`: write concern; waits until the primary has written the operation to its journal
  - `retryWrites=true`: automatically retries eligible write operations once on a network error
  For local single-host connections, the default URI above already includes `directConnection=true` to bypass replica set discovery and monitoring.
  For multi-host replica sets, omit `directConnection=true` and list all hosts: `mongodb://<user>:<pass>@<host1>,<host2>,<host3>/<database>?...`
- `CALLBACK_SECRET`: Is the secret key used for signing outgoing callbacks (default is `superdupersecretkey`)
- `SYNQ_BASE_URL`: Is the base URL used for communicating against SynQ (default is `http://localhost:8181/synq/resources`)
- `KARDEX_ENABLED`: Is used to enable or disable the Kardex adapter (default is `true`)
- `KARDEX_BASE_URL`: Is the base URL used for communicating against Kardex (default is `http://localhost:8182/kardex`)
- `LOGISTICS_ENABLED`: Is used to enable or disable the Logistics API (default is `true`)
- `ORDER_HANDLER_EMAIL`: Is the email address where orders are sent to (default is `daniel@mlt.hermes.no`)
- `ORDER_SENDER_EMAIL`: Is the email address used as the sender for orders (default is `hermes@mlt.hermes.no`)
- `TIMEOUT_SMTP`: Is the timeout in milliseconds for SMTP connections (default is `8000`)
- `TIMEOUT_MONGO`: Is the timeout in seconds for MongoDB operations (default is `8`)
- `TIMEOUT_INVENTORY`: Is the timeout in seconds for inventory related operations (default is `10`)
- `TIMEOUT_STORAGE`: Is the timeout in seconds for storage related operations (default is `10`)
- `HTTP_PROXY_HOST`: Is the host used for HTTP proxy (default is `localhost`)
- `HTTP_PROXY_PORT`: Is the port used for HTTP proxy (default is `3128`)
- `HTTP_NON_PROXY_HOSTS`: Is the list of hosts that should not be proxied (default is `localhost|127.0.0.1|docker`)


# Deployment


The section [Running the Application](#running-the-application) describes how to run the application locally.
Therefore, this section will focus on how to deploy the application to the staging and production environments.

Deployment to both environments is handled by their respective Kubernetes deployment files.
They describe the deployment, service, and ingress for the application.
There is very little difference between the two files, as the environments are very similar.
They mostly deal with resource limits and requests, as well as the number of replicas.

These files are no longer located in this repository, as they are managed by Argo CD.
In both cases the deployment is handled by the CI/CD pipeline and Argo CD.

## Deploying to Staging Environment

To deploy the application to the staging environment, push new changes to the `main` branch.
The repository is set up to only accept merges to the `main` branch through pull requests.
Therefore, to deploy to the staging environment, create a pull request and merge it to the `main` branch.
Actions in the pull request should test the application to ensure that it is working as expected.

When a pull request is merged to the `main` branch, the CI/CD pipeline will build the application, create a Docker image, and push it to Harbor.
Then the Argo CD application controller will deploy the application to the staging environment using the new Docker image.
The deployment will be done automatically.

## Deploying to Production Environment

To deploy the application to the production environment, create a new tag in the repository.
The tag should be in the format `vX.Y.Z`, where `X`, `Y`, and `Z` are numbers, following the semantic versioning standard.

This will trigger the CI/CD pipeline which will deploy the application to the production environment.
This is quite similar to the staging deployment and is also handled by Argo CD.
You will have to manually approve the deployment in GitHub action UI.


# Contact


The project is maintained by the [National Library of Norway](https://github.com/NationalLibraryOfNorway/ "Link to the National Library of Norway's GitHub organization") organization.
It is owned by the "Warehouse and Logistics" team (MLT).

For questions or issues, please create a new issue in the repository.

You can also contact the team by email at `mlt at nb dot no`.


# License


This project is licensed under the [MIT License](LICENSE.md "Link to project's LICENSE file").
