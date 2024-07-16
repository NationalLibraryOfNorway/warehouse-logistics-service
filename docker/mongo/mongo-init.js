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

db.products.insertOne({
    "hostName": "AXIELL",
    "hostId": "product-12345",
    "category": "BOOK",
    "description": "Tyv etter loven",
    "packaging": "NONE",
    "location": "SYNQ_WAREHOUSE",
    "quantity": 1,
    "preferredEnvironment": "NONE",
    "owner": "NB",
    "_class": "no.nb.mlt.wls.product.model.Product"
})

print('END #################################################################');
