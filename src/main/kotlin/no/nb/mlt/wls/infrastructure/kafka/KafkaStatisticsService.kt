package no.nb.mlt.wls.infrastructure.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.events.Event
import no.nb.mlt.wls.domain.model.events.catalog.ItemEvent
import no.nb.mlt.wls.domain.model.events.catalog.OrderEvent
import no.nb.mlt.wls.domain.model.events.storage.ItemCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderDeleted
import no.nb.mlt.wls.domain.model.events.storage.OrderUpdated
import no.nb.mlt.wls.domain.ports.outbound.StatisticsService
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class KafkaStatisticsService(
    private val statisticsProducer: StatisticsProducer
) : StatisticsService {
    override suspend fun recordStatisticsEvent(event: Event) {
        val statisticsEvent =
            when (event) {
                is ItemCreated -> event.toStatisticsEvent()
                is ItemEvent -> event.toStatisticsEvent()
                is OrderCreated -> event.toStatisticsEvent()
                is OrderEvent -> event.toStatisticsEvent()
                is OrderDeleted -> event.toStatisticsEvent()
                is OrderUpdated -> event.toStatisticsEvent()
                else -> {
                    logger.error { "Error while converting event to statistics event, unknown event type: $event" }
                    return
                }
            }

        statisticsProducer
            .sendStatisticsMessage(statisticsEvent.id, statisticsEvent)
            .doOnError { logger.error(it) { "Error while sending statistics event: $statisticsEvent" } }
            .block()
    }
}
