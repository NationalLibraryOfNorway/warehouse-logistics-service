# Overwritable options:
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
.PHONY: deps-up deps-down deps-clean deps-list deps-logs spotless clean test package image run stop logs startup shutdown refresh cleanup restart clean-restart


# Dependencies (docker compose stack used by the app):
deps-up:
	$(COMPOSE_CMD) up -d
	@echo 'keycloak: http://localhost:8082'
	@echo 'maildev: http://localhost:8081'

deps-down:
	$(COMPOSE_CMD) down

deps-clean:
	$(COMPOSE_CMD) down --remove-orphans --volumes

deps-list:
	$(COMPOSE_CMD) ps -a

deps-logs:
	$(COMPOSE_CMD) logs -f


# Maven targets:
spotless:
	mvn spotless:apply

clean:
	mvn clean

test:
	mvn verify -e -Dspring.profiles.active="$(SPRING_PROFILE)"

package:
	mvn package -DskipTests


# App targets (build and run local application container):
image:
	@cp target/wls.jar docker/wls.jar
	$(CONTAINER_RUNTIME) build -t $(IMAGE) docker/
	@rm docker/wls.jar
	@echo "Built image: $(IMAGE)"

run:
	$(CONTAINER_RUNTIME) run -d --name $(APP_CONTAINER) -e SPRING_PROFILES_ACTIVE="$(SPRING_PROFILE)" --network host $(IMAGE)
	@echo 'Hermes running at: http://localhost:8080/hermes, swagger on http://localhost:8080/hermes/swagger'

stop:
	@$(CONTAINER_RUNTIME) stop $(APP_CONTAINER) >/dev/null 2>&1 || true
	@$(CONTAINER_RUNTIME) rm -f $(APP_CONTAINER) >/dev/null 2>&1 || true

logs:
	$(CONTAINER_RUNTIME) logs -f $(APP_CONTAINER)


# Composed targets (combined workflows):
startup: deps-up package image run

shutdown: stop deps-down

refresh: stop run

restart: shutdown startup

cleanup: stop deps-clean

clean-restart: cleanup startup
