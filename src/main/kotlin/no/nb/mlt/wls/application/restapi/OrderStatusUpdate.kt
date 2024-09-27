package no.nb.mlt.wls.application.restapi

import no.nb.mlt.wls.domain.model.HostName

data class OrderStatusUpdate(
    val prevStatus: String,
    val status: String,
    val hostName: HostName,
    val wareHouse: String
)
