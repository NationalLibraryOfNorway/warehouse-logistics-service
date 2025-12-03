package no.nb.mlt.wls.domain.model.statistics

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.time.Instant

data class OrderStatisticsEvent(
    val orderId: String,
    override val eventType: String,
    override val details: Map<String, Any>,
    @field:JsonSerialize(using = ToStringSerializer::class)
    override val timestamp: Instant = Instant.now()
) : StatisticsEvent {
    override val id: String
        get() = orderId
}
