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
        if (isInvalid()) {
            return false
        }

        if (!isDeleted() && location.isBlank()) {
            throw ValidationException("Location can not be blank for a regular payload")
        }
        return true
    }

    fun isInvalid(): Boolean =
        when (this.motiveType) {
            MotiveType.StockUnavailable, MotiveType.SpaceUnavailable, MotiveType.Shortage -> true
            else -> false
        }

    /**
     * Returns true for motives which are related to an order being explicitly deleted, which should still be processed as such.
     */
    fun isDeleted(): Boolean =
        when (this.motiveType) {
            MotiveType.Deleted, MotiveType.Canceled -> true
            else -> false
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
