package no.nb.mlt.wls.domain.ports.inbound

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item

fun interface UpdateItem {
    suspend fun updateItem(updateItemPayload: UpdateItemPayload): Item

    data class UpdateItemPayload(
        val hostName: HostName,
        @field:NotBlank(message = "Host ID is missing, and can not be blank")
        val hostId: String,
        @field:Min(value = 0, message = "Quantity on hand must not be negative. It must be zero or higher")
        val quantity: Int,
        @field:NotBlank(message = "Location can not be blank")
        val location: String
    )
}
