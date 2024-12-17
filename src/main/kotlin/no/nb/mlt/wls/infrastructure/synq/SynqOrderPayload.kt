package no.nb.mlt.wls.infrastructure.synq

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import jakarta.validation.constraints.Min
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload.SynqPackaging
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload.SynqPackaging.ESK
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload.SynqPackaging.OBJ
import java.time.LocalDateTime

data class SynqOrderPayload(
    val orderId: String,
    val orderType: SynqOrderType,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val dispatchDate: LocalDateTime,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val orderDate: LocalDateTime,
    val priority: Int,
    val owner: SynqOwner,
    val orderLine: List<OrderLine>,
    val shippingAddress: ShippingAddress
) {
    data class OrderLine(
        @Min(1)
        val orderLineNumber: Int,
        val productId: String,
        val quantityOrdered: Double
    )

    enum class SynqOrderType(private val type: String) {
        STANDARD("Standard");

        @JsonValue
        override fun toString(): String {
            return type
        }
    }
}

data class ShippingAddress(
    val address: Address
) {
    data class Address(
        // SynQ does not have a field where we can put owner/contact person for the order, as such this field will be used for order's contact person
        val contactPerson: String,
        // This will contain address.recipient, as contactPerson is used for something else, explained above ^
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val addressLine1: String? = null,
        // This will contain address.addressLine1, as ...
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val addressLine2: String? = null,
        // ...addressLine2...
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val addressLine3: String? = null,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val city: String? = null,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val state: String? = null,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val country: String? = null,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val postalCode: String? = null
    )
}

fun Order.toSynqPayload() =
    SynqOrderPayload(
        orderId = hostName.toString().uppercase() + "_" + hostOrderId,
        orderType = orderType.toSynqOrderType(),
        // When order should be dispatched, AFAIK it's not used by us as we don't receive orders in future
        dispatchDate = LocalDateTime.now(),
        // When order was made in SynQ, if we want to we can omit it and SynQ will set it to current date itself
        orderDate = LocalDateTime.now(),
        priority = 5,
        owner = toSynqOwner(hostName),
        orderLine =
            orderLine.mapIndexed { index, it ->
                SynqOrderPayload.OrderLine(
                    orderLineNumber = index + 1,
                    productId = it.hostId,
                    quantityOrdered = 1.0
                )
            },
        shippingAddress =
            ShippingAddress(
                ShippingAddress.Address(
                    contactPerson = contactPerson,
                    addressLine1 = address?.recipient,
                    addressLine2 = address?.addressLine1,
                    addressLine3 = address?.addressLine2,
                    city = address?.city,
                    state = address?.region,
                    country = address?.country,
                    postalCode = address?.postcode
                )
            )
    )

fun Packaging.toSynqPackaging(): SynqPackaging =
    when (this) {
        Packaging.NONE -> OBJ
        Packaging.BOX -> ESK
    }

fun Order.Type.toSynqOrderType(): SynqOrderPayload.SynqOrderType =
    when (this) {
        // Since mock api defined more types than Synq has we map both to standard
        Order.Type.LOAN -> SynqOrderPayload.SynqOrderType.STANDARD
        Order.Type.DIGITIZATION -> SynqOrderPayload.SynqOrderType.STANDARD
    }

fun toSynqOwner(hostName: HostName): SynqOwner {
    return when (hostName) {
        HostName.ASTA -> SynqOwner.AV
        else -> SynqOwner.NB
    }
}

/**
 * Utility classed used to wrap the payload.
 * This is required for SynQ's specification of handling orders
 */
data class SynqOrder(val order: List<SynqOrderPayload>)
