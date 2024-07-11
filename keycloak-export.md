Keycloak is configured to automatically import a test realm with service accounts
GUI will be available at: http://localhost:8082
When making changes to the realm, export the realm and save it in the keycloak/import folder
```bash
docker exec -it wls-local-keycloak-1 /opt/keycloak/bin/kc.sh export --file "/tmp/mlt-local-realm-export.json" --users "same_file" --realm "mlt-local"
docker cp wls-local-keycloak-1:/tmp/mlt-local-realm-export.json ./docker/keycloak/import/mlt-local-realm-export.json
```
