package no.nb.mlt.wls.domain.model.statistics

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.time.Instant

data class ItemStatisticsEvent(
    val itemId: String,
    override val eventType: String,
    override val details: Map<String, Any>,
    @JsonSerialize(using = ToStringSerializer::class)
    override val timestamp: Instant = Instant.now()
) : StatisticsEvent {
    override val id: String
        get() = itemId
}
