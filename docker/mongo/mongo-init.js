// Create a WLS database, a user, and a collection for items and orders.
// This file only runs once during container creation.
// If for some reason it fails to load, re-create the container.
//     (by running "docker compose down")
print("START ##################################################################");

db = db.getSiblingDB('wls');
db.createUser(
    {
        user: "bruker",
        pwd: "drossap",
        roles: [{ role: "readWrite", db: "wls" }],
    },
);


db.createCollection("items");
db.items.insertOne({
    "hostId": "mlt-12345",
    "hostName": "AXIELL",
    "description": "Tyven, tyven skal du hete",
    "itemCategory": "PAPER",
    "preferredEnvironment": "NONE",
    "packaging": "NONE",
    "callbackUrl": "https://callback-wls.no/item",
    "location": "SYNQ_WAREHOUSE",
    "quantity": 1,
    "_class": "no.nb.mlt.wls.infrastructure.repositories.item.MongoItem"
})
db.items.insertOne({
    "hostId": "mlt-54321",
    "hostName": "AXIELL",
    "description": "Tyv etter loven",
    "itemCategory": "PAPER",
    "preferredEnvironment": "NONE",
    "packaging": "NONE",
    "callbackUrl": "https://callback-wls.no/item",
    "location": "SYNQ_WAREHOUSE",
    "quantity": 1,
    "_class": "no.nb.mlt.wls.infrastructure.repositories.item.MongoItem"
})


db.createCollection("orders")
db.orders.insertOne({
      "hostName": "AXIELL",
      "hostOrderId": "mlt-12345-order",
      "status": "NOT_STARTED",
      "orderLine": [
        {
          "hostId": "mlt-12345",
          "status": "NOT_STARTED"
        }
      ],
      "orderType": "LOAN",
      "contactPerson": "Dr. Heinz Doofenshmirtz",
      "address": {
        "recipient": "Doug Dimmadome",
        "addressLine1": "Dimmsdale Dimmadome",
        "addressLine2": "21st Texan Ave.",
        "city": "Dimmsdale",
        "country": "United States",
        "region": "California",
        "postcode": "CA-55415"
      },
      "callbackUrl": "https://callback-wls.no/order",
    "_class": "no.nb.mlt.wls.infrastructure.repositories.order.MongoOrder"
})

print("END ####################################################################");
