package no.nb.mlt.wls.application.kardexapi.kardex

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ValidationException

private val logger = KotlinLogging.logger {}

interface KardexItemPayload {
    val hostName: String
    val hostId: String
    val quantity: Double
    val location: String
    val operator: String
    val motiveType: MotiveType

    /**
     * Validates whether the material is processable
     * @return true if processable, false otherwise
     * @throws ValidationException if payload is badly formatted, for example having blank location for non-deletions
     */
    fun validate(): Boolean {
        if (motiveType in listOf(MotiveType.StockUnavailable, MotiveType.SpaceUnavailable, MotiveType.Shortage)) {
            return false
        }
        if (motiveType !in listOf(MotiveType.Deleted, MotiveType.Canceled) && location.isBlank()) {
            throw ValidationException("Location can not be blank for a regular payload")
        }
        return true
    }
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
        location = location,
        associatedStorage = AssociatedStorage.KARDEX
    )
}
