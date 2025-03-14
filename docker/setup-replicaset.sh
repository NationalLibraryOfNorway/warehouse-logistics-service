#!/bin/sh

# Set up replica set for the local database and run/load the init file
docker exec wls-local-mongo-db-1 mongo -u root -p toor --eval "try {rs.conf()} catch(err) {rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'wls-local-mongo-db-1:27017'}]})}"
docker exec wls-local-mongo-db-1 mongo -u root -p toor --eval "load('/mongo-init.js')"
