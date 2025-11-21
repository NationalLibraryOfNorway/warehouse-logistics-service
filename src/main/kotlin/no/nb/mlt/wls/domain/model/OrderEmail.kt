package no.nb.mlt.wls.domain.model

// TODO - Rename?
data class OrderEmail(
    val hostOrderId: String,
    val hostName: HostName,
    val orderType: Order.Type,
    val contactPerson: String,
    val contactEmail: String?,
    val note: String?,
    val orderLines: List<OrderLine>
) {
    data class OrderLine(
        val hostId: String,
        val description: String,
        val location: String
    )
}
