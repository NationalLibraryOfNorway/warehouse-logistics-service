package no.nb.mlt.wls

import no.nb.mlt.wls.application.hostapi.order.ApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.ApiUpdateOrderPayload
import no.nb.mlt.wls.application.hostapi.order.toApiOrderLine
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata

////////////////////////////////////////////////////////////////////////////////

// If you want to make me happy:
// Split test classes with these headers, leave two blank lines above and below

////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////  Test Setup  /////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
///////////////////////////////  Test Functions  ///////////////////////////////
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
////////////////////////////////  Test Helpers  ////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

// "Test Setup" includes:
// - Mocks
// - cut (class in test)
// - beforeX and afterX functions
// - lateinit variables
// - etc.

// "Test Functions" includes:
// - Test functions themselves

// "Test Helpers" includes:
// - Helper functions
// - re-used test objects
// - etc.

////////////////////////////////////////////////////////////////////////////////


// Import helper objects and functions with:
// import no.nb.mlt.wls.testItem
// import no.nb.mlt.wls.testOrder
// import no.nb.mlt.wls.toOrder
// import no.nb.mlt.wls.toItemMetadata
// import no.nb.mlt.wls.toApiUpdatePayload
// import no.nb.mlt.wls.createOrderAddress
// import no.nb.mlt.wls.createTestItem
// import no.nb.mlt.wls.createTestOrder


////////////////////////////////////////////////////////////////////////////////

val testItem =
    Item(
        hostName = HostName.AXIELL,
        hostId = "testItem-01",
        description = "description",
        itemCategory = ItemCategory.PAPER,
        preferredEnvironment = Environment.NONE,
        packaging = Packaging.NONE,
        callbackUrl = "https://callback-wls.no/item",
        location = "SYNQ_WAREHOUSE",
        quantity = 1
    )

val testOrder =
    Order(
        hostName = HostName.AXIELL,
        hostOrderId = "testOrder-01",
        status = Order.Status.NOT_STARTED,
        orderLine = listOf(
            Order.OrderItem(testItem.hostId, Order.OrderItem.Status.NOT_STARTED),
            Order.OrderItem("testItem-02", Order.OrderItem.Status.NOT_STARTED)
        ),
        orderType = Order.Type.LOAN,
        address = createOrderAddress(),
        contactPerson = "contactPerson",
        contactEmail = "contact@ema.il",
        note = "note",
        callbackUrl = "https://callback-wls.no/order"
    )

fun ApiOrderPayload.toOrder() =
    Order(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = status ?: Order.Status.NOT_STARTED,
        orderLine = orderLine.map { it.toOrderItem() },
        orderType = orderType,
        address = address,
        contactPerson = contactPerson,
        contactEmail = contactEmail,
        note = note,
        callbackUrl = callbackUrl
    )

fun Order.toApiUpdatePayload() =
    ApiUpdateOrderPayload(
    hostName = hostName,
    hostOrderId = hostOrderId,
    orderLine = orderLine.map { it.toApiOrderLine() },
    orderType = orderType,
    contactPerson = contactPerson,
    contactEmail = contactEmail,
    address = address,
    callbackUrl = callbackUrl,
    note = note
)

fun Item.toItemMetadata() =
    ItemMetadata(
        hostId = hostId,
        hostName = hostName,
        description = description,
        itemCategory = itemCategory,
        preferredEnvironment = preferredEnvironment,
        packaging = packaging,
        callbackUrl = callbackUrl
    )

fun createOrderAddress(): Order.Address {
    return Order.Address("recipient", "addressLine1", "addressLine2", "postcode", "city", "region", "country")
}

fun createTestItem(
    hostName: HostName = testItem.hostName,
    hostId: String = testItem.hostId,
    description: String = testItem.description,
    itemCategory: ItemCategory = testItem.itemCategory,
    preferredEnvironment: Environment = testItem.preferredEnvironment,
    packaging: Packaging = testItem.packaging,
    callbackUrl: String? = testItem.callbackUrl,
    location: String = testItem.location,
    quantity: Int = testItem.quantity,
) = Item(hostId, hostName, description, itemCategory, preferredEnvironment, packaging, callbackUrl, location, quantity)

fun createTestOrder(
    hostName: HostName = testOrder.hostName,
    hostOrderId: String = testOrder.hostOrderId,
    status: Order.Status = testOrder.status,
    orderLine: List<Order.OrderItem> = testOrder.orderLine,
    orderType: Order.Type = testOrder.orderType,
    address: Order.Address? = testOrder.address,
    contactPerson: String = testOrder.contactPerson,
    contactEmail: String? = testOrder.contactEmail,
    note: String? = testOrder.note,
    callbackUrl: String = testOrder.callbackUrl
) = Order(hostName, hostOrderId, status, orderLine, orderType, address, contactPerson, contactEmail, note, callbackUrl)
