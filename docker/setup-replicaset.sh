#!/bin/sh

# setup replica set
docker compose exec mongo1 mongo -u root -p toor --eval "try {rs.conf()} catch(err) {rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'mongo1:27017'}]})}"
docker compose exec mongo1 mongo -u root -p toor --eval "load('/mongo-init.js')"
