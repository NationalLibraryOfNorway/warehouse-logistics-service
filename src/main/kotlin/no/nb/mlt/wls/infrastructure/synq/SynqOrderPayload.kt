package no.nb.mlt.wls.infrastructure.synq

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import jakarta.validation.constraints.Min
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
    // TODO - Should all the fields here be annotated?
    data class Address(
        val contactPerson: String,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val addressLine1: String? = null,
        val addressLine2: String? = null,
        val city: String? = null,
        val state: String? = null,
        val country: String? = null,
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
        owner = owner.toSynqOwner(),
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
                    addressLine1 = address?.addressLine1,
                    addressLine2 = address?.addressLine2,
                    city = address?.city,
                    state = address?.region,
                    country = address?.country,
                    postalCode = address?.zipcode
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

/**
 * Utility classed used to wrap the payload.
 * This is required for SynQ's specification of handling orders
 */
data class SynqOrder(val order: List<SynqOrderPayload>)
