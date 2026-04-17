# Overwritable options
# Use `make VAR=value <target>` to override defaults for this invocation.
# Export variables to override defaults for all invocations in the current shell session: `export VAR=value`

CONTAINER_RUNTIME ?= sudo nerdctl
COMPOSE_FILE ?= docker/compose.yaml
COMPOSE_CMD ?= $(CONTAINER_RUNTIME) compose -f $(COMPOSE_FILE)
IMAGE ?= wls:local
APP_CONTAINER ?= wls-local
SPRING_PROFILE ?= local-dev


# .PHONY marks non-file targets so make always runs the recipe,
# even if a file with the same name exists in the working directory.
.PHONY: deps-up deps-down deps-clean deps-list deps-logs spotless clean test package image run rm list logs start shutdown refresh cleanup restart clean-restart


# Dependencies (docker compose stack used by the app)
deps-up:
	@$(COMPOSE_CMD) up -d

deps-down:
	@$(COMPOSE_CMD) down

deps-clean:
	@$(COMPOSE_CMD) down --remove-orphans --volumes

deps-list:
	@$(COMPOSE_CMD) ps -a

deps-logs:
	@$(COMPOSE_CMD) logs -f


# Maven targets
spotless:
	@mvn spotless:apply

clean:
	@mvn clean

test:
	@echo '<?xml version="1.0" encoding="UTF-8"?><configuration><root level="OFF"/></configuration>' > src/test/resources/logback-test.xml
	@SPRING_MAIN_BANNER_MODE=off mvn verify -Dspring.profiles.active="$(SPRING_PROFILE)"; status=$$?; rm -f src/test/resources/logback-test.xml; exit $$status
	@xdg-open target/reports/surefire.html

package:
	@mvn package -DskipTests


# App targets (build and run local application container)
image:
	@echo 'Building image: $(IMAGE) from target/wls.jar ...'
	@cp target/wls.jar docker/wls.jar
	@$(CONTAINER_RUNTIME) build -t $(IMAGE) docker/
	@rm docker/wls.jar

run:
	@echo 'Starting $(APP_CONTAINER) with profile: $(SPRING_PROFILE)'
	@$(CONTAINER_RUNTIME) run -d --name $(APP_CONTAINER) -e SPRING_PROFILES_ACTIVE="$(SPRING_PROFILE)" --network host $(IMAGE) 1>/dev/null
	@echo 'Hermes running at: http://localhost:8080/hermes, swagger on http://localhost:8080/hermes/swagger'
	@echo 'Connect to MongoDB with: mongodb://wls:slw@localhost/wls?replicaSet=rs0&authSource=wls&readPreference=primary&w=majority&journal=true&retryWrites=true&directConnection=true'
	@echo 'Fake Email GUI at: http://localhost:8081'
	@echo 'Keycloak GUI at: http://localhost:8082'

rm:
	@echo "Stopping: $(APP_CONTAINER)"
	@$(CONTAINER_RUNTIME) rm -f $(APP_CONTAINER) >/dev/null 2>&1 || true
	@echo "Stopped: $(APP_CONTAINER)"

list:
	@$(CONTAINER_RUNTIME) ps -a --filter "name=$(APP_CONTAINER)"

logs:
	@$(CONTAINER_RUNTIME) logs -f $(APP_CONTAINER)


# Composed targets (combined workflows)
# Spin up everything, useful for the first time run or after a cleanup
start: deps-up package image run

# Stop the app and dependencies, but keep volumes for data persistence
stop: rm deps-down

# Refresh app, useful for quick code iterations
refresh: rm package image run

# Stop everything and remove volumes, useful if app or dependencies need restart
restart: stop deps-up run

# Clean both app build dir and dependencies, useful for a full reset and clean of the environment
cleanup: stop clean deps-clean

# Reset everything and restart the app, useful for clean slate development or troubleshooting
clean-restart: cleanup start
