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

// //////////////////////////////////////////////////////////////////////////////

// If you want to make me happy:
// Split test classes with these headers, leave two blank lines above and below

// //////////////////////////////////////////////////////////////////////////////
// ///////////////////////////////  Test Setup  /////////////////////////////////
// //////////////////////////////////////////////////////////////////////////////

// //////////////////////////////////////////////////////////////////////////////
// /////////////////////////////  Test Functions  ///////////////////////////////
// //////////////////////////////////////////////////////////////////////////////

// //////////////////////////////////////////////////////////////////////////////
// //////////////////////////////  Test Helpers  ////////////////////////////////
// //////////////////////////////////////////////////////////////////////////////

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

// //////////////////////////////////////////////////////////////////////////////

// Import helper objects and functions with:
// import no.nb.mlt.wls.toOrder
// import no.nb.mlt.wls.toItemMetadata
// import no.nb.mlt.wls.toApiUpdatePayload
// import no.nb.mlt.wls.createOrderAddress
// import no.nb.mlt.wls.createTestItem
// import no.nb.mlt.wls.createTestOrder

// //////////////////////////////////////////////////////////////////////////////

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
    hostName: HostName = HostName.AXIELL,
    hostId: String = "testItem-01",
    description: String = "description",
    itemCategory: ItemCategory = ItemCategory.PAPER,
    preferredEnvironment: Environment = Environment.NONE,
    packaging: Packaging = Packaging.NONE,
    callbackUrl: String? = "https://callback-wls.no/item",
    location: String = "SYNQ_WAREHOUSE",
    quantity: Int = 1
) = Item(hostId, hostName, description, itemCategory, preferredEnvironment, packaging, callbackUrl, location, quantity)

fun createTestOrder(
    hostName: HostName = HostName.AXIELL,
    hostOrderId: String = "testOrder-01",
    status: Order.Status = Order.Status.NOT_STARTED,
    orderLine: List<Order.OrderItem> = orderLines,
    orderType: Order.Type = Order.Type.LOAN,
    address: Order.Address? = createOrderAddress(),
    contactPerson: String = "contactPerson",
    contactEmail: String? = "contact@ema.il",
    note: String? = "note",
    callbackUrl: String = "https://callback-wls.no/order"
) = Order(hostName, hostOrderId, status, orderLine, orderType, address, contactPerson, contactEmail, note, callbackUrl)

private val orderLines =
    listOf(
        Order.OrderItem("testItem-01", Order.OrderItem.Status.NOT_STARTED),
        Order.OrderItem("testItem-02", Order.OrderItem.Status.NOT_STARTED)
    )
