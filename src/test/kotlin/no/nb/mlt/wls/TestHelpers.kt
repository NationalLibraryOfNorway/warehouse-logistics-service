package no.nb.mlt.wls

import no.nb.mlt.wls.application.hostapi.order.ApiOrderPayload
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
// import no.nb.mlt.wls.createOrderAddress


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
        contactPerson = "contactPerson",
        contactEmail = "contact@ema.il",
        address = createOrderAddress(),
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
    hostName: HostName = HostName.AXIELL,
    hostId: String = "mlt-12345",
    description: String = "Tyven, tyven skal du hete",
    itemCategory: ItemCategory = ItemCategory.PAPER,
    preferredEnvironment: Environment = Environment.NONE,
    packaging: Packaging = Packaging.NONE,
    callbackUrl: String? = "https://callback-wls.no/item",
    location: String = "UNKNOWN",
    quantity: Int = 0
) = Item(hostId, hostName, description, itemCategory, preferredEnvironment, packaging, callbackUrl, location, quantity)
