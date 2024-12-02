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
db.createCollection('items');

db.items.insertOne({
    "hostName": "AXIELL",
    "hostId": "item-12345",
    "itemCategory": "BOOK",
    "description": "Tyv etter loven",
    "packaging": "NONE",
    "location": "SYNQ_WAREHOUSE",
    "quantity": 1,
    "preferredEnvironment": "NONE",
    "owner": "NB",
    "_class": "no.nb.mlt.wls.infrastructure.repositories.item.MongoItem"
})

db.items.insertOne({
    "hostName": "AXIELL",
    "hostId": "item-54321",
    "itemCategory": "BOOK",
    "description": "Tyv etter loven",
    "packaging": "NONE",
    "location": "SYNQ_WAREHOUSE",
    "quantity": 1,
    "preferredEnvironment": "NONE",
    "owner": "NB",
    "_class": "no.nb.mlt.wls.infrastructure.repositories.item.MongoItem"
})


db.createCollection('orders')

db.orders.insertOne({
    "hostName": "AXIELL",
    "hostOrderId": "order-12345",
    "status": "NOT_STARTED",
    "orderLine": [
        {
            "hostId": "item-12345",
            "status": "NOT_STARTED"
        }
    ],
    "orderType": "LOAN",
    "owner": "NB",
    "contactPerson": "MLT Team",
    "address": {
        "recipient": "Doug Doug",
        "addressLine1": "Somewhere",
        "addressLine2": "Behind a cardboard box",
        "city": "Las Vegas",
        "country": "United States",
        "region": "Texas",
        "postcode": "TX-55415"
    },
    "callbackUrl": "https://example.com/send/callback/here",
    "_class": "no.nb.mlt.wls.infrastructure.repositories.order.MongoOrder"
})

print('END #################################################################');
