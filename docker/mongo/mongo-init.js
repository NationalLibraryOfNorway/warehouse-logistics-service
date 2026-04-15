// Create a WLS database, a user, and seed collections.
// This script runs during first container bootstrap, but it also supports
// partial state recovery if previous setup was interrupted.

// Logging utility for consistent log formatting
const LOG_PREFIX = "[mongo-db-entrypoint]  [data-init]";
const log = (msg) => console.error(`${LOG_PREFIX} ${msg}`);

// Hardcoded database configuration for local bootstrap.
const WLS_DB_NAME = "wls";
const WLS_DB_USER = "wls";
const WLS_DB_PASSWORD = "slw";
const MOVEIT_DB_NAME = "moveit";
const MOVEIT_DB_USER = "moveit";
const MOVEIT_DB_PASSWORD = "tievom";

// Check for existing user and collections to determine if initialization is needed.
const appDb = db.getSiblingDB(WLS_DB_NAME);
const userExists = !!appDb.getUser(WLS_DB_USER);
const collectionNames = appDb.getCollectionNames();
const itemsCollectionExists = collectionNames.includes("items");
const ordersCollectionExists = collectionNames.includes("orders");
const moveitDb = db.getSiblingDB(MOVEIT_DB_NAME);
const moveitUserExists = !!moveitDb.getUser(MOVEIT_DB_USER);
const moveitCollectionNames = moveitDb.getCollectionNames();
const accountsCollectionExists = moveitCollectionNames.includes("accounts");
const userSessionsCollectionExists = moveitCollectionNames.includes("user-sessions");
const usersCollectionExists = moveitCollectionNames.includes("users");

if (
    userExists
    && itemsCollectionExists
    && ordersCollectionExists
    && moveitUserExists
    && accountsCollectionExists
    && userSessionsCollectionExists
    && usersCollectionExists
) {
    log(`Initialization already complete for db=${WLS_DB_NAME} and db=${MOVEIT_DB_NAME}; skipping.`);
    quit(0);
}

function createWlsUser(appDb) {
    appDb.createUser({
        user: WLS_DB_USER,
        pwd: WLS_DB_PASSWORD,
        roles: [{ role: "readWrite", db: WLS_DB_NAME }],
    });
}

function seedItemsCollection(appDb) {
    appDb.createCollection("items");
    appDb.items.insertOne({
        hostId: "mlt-12345",
        hostName: "AXIELL",
        description: "Tyven, tyven skal du hete",
        itemCategory: "PAPER",
        preferredEnvironment: "NONE",
        packaging: "NONE",
        callbackUrl: "https://callback-wls.no/item",
        location: "SYNQ_WAREHOUSE",
        quantity: 1,
        associatedStorage: "SYNQ",
        confidential: false,
        _class: "no.nb.mlt.wls.infrastructure.repositories.item.MongoItem",
    });
    appDb.items.insertOne({
        hostId: "mlt-54321",
        hostName: "AXIELL",
        description: "Tyv etter loven",
        itemCategory: "PAPER",
        preferredEnvironment: "NONE",
        packaging: "NONE",
        callbackUrl: "https://callback-wls.no/item",
        location: "SYNQ_WAREHOUSE",
        quantity: 1,
        associatedStorage: "SYNQ",
        confidential: true,
        _class: "no.nb.mlt.wls.infrastructure.repositories.item.MongoItem",
    });
}

function seedOrdersCollection(appDb) {
    appDb.createCollection("orders");
    appDb.orders.insertOne({
        hostName: "AXIELL",
        hostOrderId: "mlt-12345-order",
        status: "NOT_STARTED",
        orderLine: [
            {
                hostId: "mlt-12345",
                status: "NOT_STARTED",
            },
        ],
        orderType: "LOAN",
        contactPerson: "Dr. Heinz Doofenshmirtz",
        contactEmail: "heinz@doofenshmir.tz",
        address: {
            recipient: "Doug Dimmadome",
            addressLine1: "Dimmsdale Dimmadome",
            addressLine2: "21st Texan Ave.",
            city: "Dimmsdale",
            country: "United States",
            region: "California",
            postcode: "CA-55415",
        },
        callbackUrl: "https://callback-wls.no/order",
        _class: "no.nb.mlt.wls.infrastructure.repositories.order.MongoOrder",
    });
}

function createMoveitUser(moveitDb) {
    moveitDb.createUser({
        user: MOVEIT_DB_USER,
        pwd: MOVEIT_DB_PASSWORD,
        roles: [{ role: "readWrite", db: MOVEIT_DB_NAME }],
    });
}

function ensureMoveitCollection(moveitDb, collectionName) {
    if (!moveitDb.getCollectionNames().includes(collectionName)) {
        moveitDb.createCollection(collectionName);
    }
}

log("#####################################################################");
log("MongoDB initialization script started...");
log("#####################################################################");


if (!userExists) {
    log(`Creating user ${WLS_DB_USER} with read-write access to the ${WLS_DB_NAME} database...`);
    createWlsUser(appDb);
}

log("User ready")

if (!itemsCollectionExists) {
    log("Creating items collection and inserting sample data...");
    seedItemsCollection(appDb);
}

log("Items collection ready and seeded with sample data")

if (!ordersCollectionExists) {
    log("Creating orders collection and inserting sample data...");
    seedOrdersCollection(appDb);
}

log("Orders collection ready and seeded with sample data")


if (!moveitUserExists) {
    log(`Creating user ${MOVEIT_DB_USER} with read-write access to the ${MOVEIT_DB_NAME} database...`);
    createMoveitUser(moveitDb);
}

ensureMoveitCollection(moveitDb, "accounts");
ensureMoveitCollection(moveitDb, "user-sessions");
ensureMoveitCollection(moveitDb, "users");

log(`Moveit database ready with collections: accounts, user-sessions, users`);

log("#####################################################################");
log("MongoDB initialization script completed!");
log("#####################################################################");
