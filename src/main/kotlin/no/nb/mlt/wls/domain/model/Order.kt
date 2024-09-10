package no.nb.mlt.wls.domain.model

data class Order (
    val hostName: HostName,
    val hostOrderId: String,
    val status: Status,
    val productLine: List<OrderItem>,
    val orderType: Type,
    val owner: Owner?,
    val receiver: Receiver,
    val callbackUrl: String
) {
    data class OrderItem (
        val hostId: String,
        val status: Status
    ) {
        enum class Status {
            NOT_STARTED,
            PICKED,
            FAILED
        }
    }

    data class Receiver (
        val name: String,
        val location: String,
        val address: String?,
        val city: String?,
        val postalCode: String?,
        val phoneNumber: String?
    )

    enum class Status {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        DELETED
    }

    enum class Type {
        LOAN,
        DIGITIZATION
    }
}
