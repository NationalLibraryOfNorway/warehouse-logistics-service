// Create a WLS database, a user, and a collection for items and orders.
// This file only runs once during container creation.
// If for some reason it fails to load, re-create the container.
//     (by running "docker compose down")
print('START ##################################################################');

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
    "hostId": "mlt-12345",
    "hostName": "AXIELL",
    "description": "Tyven, tyven skal du hete",
    "itemCategory": "BOOK",
    "preferredEnvironment": "NONE",
    "packaging": "NONE",
    "owner": "NB",
    "callbackUrl": "https://callback-wls.no/item",
    "location": "SYNQ_WAREHOUSE",
    "quantity": 1,
    "_class": "no.nb.mlt.wls.infrastructure.repositories.item.MongoItem"
})
db.items.insertOne({
    "hostId": "mlt-54321",
    "hostName": "AXIELL",
    "description": "Tyv etter loven",
    "itemCategory": "BOOK",
    "preferredEnvironment": "NONE",
    "packaging": "NONE",
    "owner": "NB",
    "callbackUrl": "https://callback-wls.no/item",
    "location": "SYNQ_WAREHOUSE",
    "quantity": 1,
    "_class": "no.nb.mlt.wls.infrastructure.repositories.item.MongoItem"
})


db.createCollection('orders')
db.orders.insertOne({
    "hostName": "AXIELL",
    "hostOrderId": "mlt-order-12345",
    "status": "NOT_STARTED",
    "orderLine": [
        {
            "hostId": "item-12345",
            "status": "NOT_STARTED"
        }
    ],
    "orderType": "LOAN",
    "owner": "NB",
    "receiver": {
        "name": "Doug Doug",
        "address": "Somewhere in the United States"
    },
    "callbackUrl": "https://callback-wls.no/order",
    "_class": "no.nb.mlt.wls.infrastructure.repositories.order.MongoOrder"
})

print('END ####################################################################');
