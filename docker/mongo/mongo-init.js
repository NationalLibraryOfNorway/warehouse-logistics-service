/// <reference types="@mongosh/shell-api" />
/** @typedef {import('@mongosh/shell-api').Database} Db */

// Create WLS database, user, and collections, seeding them with sample data.
// Create MoveIt database, user, and collections, as they belong together, and MoveIt needs WLS to function.
// Each function is idempotent: it checks whether its target already exists before acting, so the script is safe to re-run at any time.


// Logging utility for consistent log formatting
const LOG_PREFIX = "[mongo-db-entrypoint]  [data-init]";
const log = (msg) => console.error(`${LOG_PREFIX} ${msg}`);

// Modify these values to change databases, users, and collections that this script creates.
const wlsDbName = "wls";
const wlsDbUserName = "wls";
const wlsDbUserPass = "slw";
const wlsCollections = ["items", "orders", "catalog-events", "storage-events", "email-events"];
const moveitDbName = "moveit";
const moveitDbUserName = "moveit";
const moveitDbUserPass = "tievom";
const moveitCollections = ["accounts", "user-sessions", "users"];

// Define refs to the databases for reuse in functions
/** @type {Db} */
const wlsDb = db.getSiblingDB(wlsDbName);
/** @type {Db} */
const moveitDb = db.getSiblingDB(moveitDbName);

/**
 * Ensures that a user with the specified credentials exists in the given database.
 *
 * If the user already exists, no action is taken.
 * If the user does not exist, it will be created with read-write permissions for the specified database.
 *
 * @param {Db}     db       - The initialized MongoDB connection object for interaction with the database
 * @param {string} database - The database that the user will have access to
 * @param {string} user     - The user login name
 * @param {string} password - The user login password
 */
function ensureUserExists(db, database, user, password) {
    if (db.getUser(user)) {
        log(`User "${user}" in "${database}" database already exists; skipping.`);
        return;
    }

    log(`Creating user "${user}" with read-write access to the "${database}" database...`);

    db.createUser({
        user: user,
        pwd: password,
        roles: [{ role: "readWrite", db: database }],
    });

    log(`Created user "${user}" with read-write access to the "${database}" database.`);
}

/**
 * Ensures that a collection with the specified name exists in the given database.
 *
 * If the collection does not exist, it will be created.
 *
 * @param {Db}     db         - The initialized MongoDB connection object for interaction with the database
 * @param {string} database   - The database that the collection will belong to
 * @param {string} collection - The collection name
 */
function ensureCollectionExists(db, database, collection) {
    if (db.getCollectionNames().includes(collection)) return;

    log(`Creating "${collection}" collection in "${database}" database...`);

    db.createCollection(collection)

    log(`"${collection}" collection in "${database}" database ready.`);
}

/**
 * Creates the necessary indexes for the "wls" database collections to optimize query performance.
 *
 * This ensures optimized queries for collections in WLS DB, closely mimicking production setup.
 *
 * In the case of `items` and `orders` collections, compound indexes are created for unique and ascending sorting.
 * This ensures that the DB does not accept duplicate entries (unique host and id).
 *
 * Here are the indexes created for each collection:
 * - `items` collection:
 *   - `hostName` + `hostId` (unique, ascending)
 *   - `hostName` (ascending)
 *   - `hostId` (ascending)
 *   - `location` (ascending)
 *   - `itemCategory` (ascending)
 *   - `associatedStorage` (ascending)
 * - `orders` collection:
 *   - `hostName` + `hostOrderId` (unique, ascending)
 *   - `hostName` (ascending)
 *   - `hostOrderId` (ascending)
 *   - `status` (ascending)
 *   - `orderType` (ascending)
 * - `catalog-events`, `storage-events`, and `email-events` collections:
 *   - `createdTimestamp` (descending)
 *   - `processedTimestamp` (descending)
 */
function createIndexesForWlsCollections() {
    log("Creating indexes for wls.items collection...");

    wlsDb["items"].createIndex({hostName: 1, hostId: -1}, {name: "host-asc_id-desc_unique_index", unique: true});
    wlsDb["items"].createIndex({hostName: 1}, {name: "host-asc_index"});
    wlsDb["items"].createIndex({hostId: 1}, {name: "id-asc_index"});
    wlsDb["items"].createIndex({location: 1}, {name: "location-asc_index"});
    wlsDb["items"].createIndex({itemCategory: 1}, {name: "item-category-asc_index"});
    wlsDb["items"].createIndex({associatedStorage: 1}, {name: "associated-storage-asc_index"});

    log("...indexes created.");
    log("Creating indexes for orders collection...");

    wlsDb["orders"].createIndex({hostName: 1, hostOrderId: -1}, {name: "host-asc_id-desc_unique_index", unique: true});
    wlsDb["orders"].createIndex({hostName: 1}, {name: "host-asc_index"});
    wlsDb["orders"].createIndex({hostOrderId: 1}, {name: "id-asc_index"});
    wlsDb["orders"].createIndex({status: 1}, {name: "status-asc_index"});
    wlsDb["orders"].createIndex({orderType: 1}, {name: "order-type-asc_index"});

    log("...indexes created.");

    ["catalog-events", "storage-events", "email-events"].forEach((collection) => {
        log(`Creating indexes for ${collection} collection...`);

        wlsDb[`${collection}`].createIndex({createdTimestamp: -1}, {name: "created-timestamp-desc_index"});
        wlsDb[`${collection}`].createIndex({processedTimestamp: -1}, {name: "processed-timestamp-desc_index"});

        log(`...indexes created for ${collection} collection.`);
    });
}

/**
 * Creates the necessary indexes for the "moveit" database collections to optimize query performance.
 *
 * This ensures optimized queries for collections in MoveIt DB, closely mimicking production setup.
 *
 * Here are the indexes created for each collection:
 * - `users` collection:
 *   - `email` (ascending)
 *   - `name` (ascending)
 * - `accounts`, and `user-sessions` collection:
 *   - `expires_at` (descending)
 *
 * @return {void} This method does not return a value.
 */
function createIndexesForMoveitCollections() {
    log("Creating indexes for moveit.users collection...");

    moveitDb["users"].createIndex({email: 1}, {name: "email-asc_index"});
    moveitDb["users"].createIndex({name: 1}, {name: "name-asc_index"});

    log("...indexes created.");

    ["accounts", "user-sessions"].forEach((collection) => {
        log(`Creating indexes for ${collection} collection...`);

        moveitDb[`${collection}`].createIndex({expires_at: -1}, {name: "expires-at-desc_index"});

        log(`...indexes created for ${collection} collection.`);
    });
}

/**
 * Seeds the "items" collection in the given database with sample data if the collection is empty.
 *
 * @param {Db} db - The initialized MongoDB connection object for interaction with the database
 */
function seedItemsCollection(db) {
    if (db["items"].countDocuments() > 0) {
        log(`The "items" collection already has data; skipping seed.`);
        return;
    }

    log("Seeding items collection with sample data...");

    db["items"].insertMany([
        {
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
        },{
            hostId: "mlt-54321",
            hostName: "AXIELL",
            description: "Tyv etter loven",
            itemCategory: "FILM",
            preferredEnvironment: "FREEZE",
            packaging: "NONE",
            callbackUrl: "https://callback-wls.no/item",
            location: "FREEZE_WAREHOUSE",
            quantity: 1,
            associatedStorage: "SYNQ",
            confidential: true,
            _class: "no.nb.mlt.wls.infrastructure.repositories.item.MongoItem",
        },{
            hostId: "mlt-69420",
            hostName: "BIBLIOFIL_DEP",
            description: "Ringenes Herre Serie, Samler Utgave",
            itemCategory: "MONOGRAPH",
            preferredEnvironment: "NONE",
            packaging: "BOX",
            callbackUrl: "https://callback-wls.no/item",
            location: "DEPOT_WAREHOUSE",
            quantity: 1,
            associatedStorage: "DEPOT",
            confidential: false,
            _class: "no.nb.mlt.wls.infrastructure.repositories.item.MongoItem",
        },{
            hostId: "mlt-02496",
            hostName: "BIBLIOFIL_DEP",
            description: "Illustrert vitenskap",
            itemCategory: "PERIODICAL",
            preferredEnvironment: "NONE",
            packaging: "NONE",
            callbackUrl: "https://callback-wls.no/item",
            location: "ON_LOAN",
            quantity: 0,
            associatedStorage: "DEPOT",
            confidential: false,
            _class: "no.nb.mlt.wls.infrastructure.repositories.item.MongoItem",
        }
    ])

    log("...seeded items collection with sample data.");
}

/**
 * Seeds the "orders" collection in the given database with sample data if the collection is empty.
 *
 * @param {Db} db - The initialized MongoDB connection object for interaction with the database
 */
function seedOrdersCollection(db) {
    if (db["orders"].countDocuments() > 0) {
        log(`The "orders" collection already has data; skipping seed.`);
        return;
    }

    log("Seeding orders collection with sample data...");

    db["orders"].insertOne({
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
    })

    log("...seeded orders collection with sample data.");
}

/**
 * Initializes user accounts for "wls" and "moveit" databases.
 * If the users already exist, this function does nothing.
 */
function initializeUsers() {
    log("Initializing users...");

    ensureUserExists(wlsDb, wlsDbName, wlsDbUserName, wlsDbUserPass);
    ensureUserExists(moveitDb, moveitDbName, moveitDbUserName, moveitDbUserPass);

    log("...Users initialized.");
}

/**
 * Initializes collections for "wls" and "moveit" databases.
 * If the collections already exist, this function does nothing.
 *
 * To add more collections, add their names to the wls/moveit collections arrays at the start of the script.
 */
function initializeCollections() {
    log("Initializing collections...");

    wlsCollections.forEach((collection) => ensureCollectionExists(wlsDb, wlsDbName, collection));
    moveitCollections.forEach((collection) => ensureCollectionExists(moveitDb, moveitDbName, collection));

    log("...Collections initialized.");
}

/**
 * Creates indexes for various collections in the database if they do not already exist.
 *
 * This method ensures that appropriate indexes are created for the following collections:
 * - `items`
 * - `orders`
 * - Event collections such as `catalog-events`, `storage-events`, and `email-events`
 *
 * For the `items` and `orders` collections, custom compound and single-field indexes are created.
 * For event-based collections, timestamp indexes (both `createdTimestamp` and `processedTimestamp`) are established.
 *
 * The function checks for any existing custom indexes before creating new ones to avoid duplication.
 */
function indexCollections() {
    log("Creating indexes for all collections as needed...");

    createIndexesForWlsCollections();
    createIndexesForMoveitCollections();

    log("...Indexes created.");
}

/**
 * Seeds the wls collections with sample data if the collections are empty.
 * If the collections already have data, this function does nothing.
 *
 * Collections in the moveit database are not seeded as they are used for handling user sessions in MoveIt.
 */
function seedCollections() {
    log("Seeding collections...");

    seedItemsCollection(wlsDb);
    seedOrdersCollection(wlsDb);

    log("...Collections seeded.");
}

// This part just calls the defined function to set up local MongoDB instance

log("#####################################################################");
log("MongoDB initialization script started...");
log("#####################################################################");

initializeUsers();
initializeCollections();
indexCollections();
seedCollections();

log("#####################################################################");
log("MongoDB initialization script completed!");
log("#####################################################################");
