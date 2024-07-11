# Exporting Keycloak Realm
Keycloak is configured to automatically import a test realm with service accounts.
GUI with imported realm is then available [here](http://localhost:8082/admin/master/console/#/mlt-local "Keycloak Admin Console for MLT-Local Realm").
When making changes to the realm, export the realm and save it in the keycloak/import folder.
That way changes will be persisted after the container is restarted, and available to others.
```bash
docker exec -it wls-local-keycloak-1 /opt/keycloak/bin/kc.sh export --file "/tmp/mlt-local-realm-export.json" --users "same_file" --realm "mlt-local"
docker cp wls-local-keycloak-1:/tmp/mlt-local-realm-export.json ./docker/keycloak/import/mlt-local-realm-export.json
```

Make sure that the container name `wls-local-keycloak-1` is correct.
You can check the container name by running `docker ps` and looking for the container running the `quay.io/keycloak/keycloak` image.
