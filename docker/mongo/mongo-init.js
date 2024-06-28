// Create a WLS database, a user, and a collection for products
// This file only runs once during container creation.
// If for some reason it fails to load, re-create the container
// (E.G. running "docker compose down")
print('Start #################################################################');

db = db.getSiblingDB('wls');
db.createUser(
    {
        user: 'bruker',
        pwd: 'drossap',
        roles: [{ role: 'readWrite', db: 'wls' }],
    },
);
db.createCollection('products');

print('END #################################################################');
