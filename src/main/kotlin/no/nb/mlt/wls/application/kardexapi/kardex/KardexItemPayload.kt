package no.nb.mlt.wls.application.kardexapi.kardex

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem

private val logger = KotlinLogging.logger {}

interface KardexItemPayload {
    val hostName: String
    val hostId: String
    val quantity: Double
    val location: String
}

fun KardexItemPayload.toUpdateItemPayload(): UpdateItem.UpdateItemPayload {
    val resolvedHostName =
        try {
            HostName.fromString(hostName)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid hostname '$hostName' provided, defaulting to UNKNOWN. Error: ${e.message}" }
            HostName.UNKNOWN
        }

    return UpdateItem.UpdateItemPayload(
        hostName = resolvedHostName,
        hostId = hostId,
        quantity = quantity.toInt(),
        location = location
    )
}
