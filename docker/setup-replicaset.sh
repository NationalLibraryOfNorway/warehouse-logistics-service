#!/bin/sh

# Set up replica set for the local database and run/load the init file
docker compose exec mongo-db mongosh -u root -p toor --eval "try {rs.conf()} catch(err) {rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'mongo-db:27017'}]})}"
docker compose exec mongo-db mongosh -u root -p toor --eval "load('/mongo-init.js')"
