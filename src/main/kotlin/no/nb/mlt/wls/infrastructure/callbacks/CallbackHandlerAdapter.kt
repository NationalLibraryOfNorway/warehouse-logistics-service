package no.nb.mlt.wls.infrastructure.callbacks

import no.nb.mlt.wls.application.hostapi.order.toApiOrderPayload
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.CallbackHandler
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class CallbackHandlerAdapter(
    private val webClient: WebClient
) : CallbackHandler {
    override fun handleItemCallback(item: Item) {
        TODO("Future task")
    }

    override fun handleOrderCallback(order: Order) {
        webClient
            .post()
            .uri(order.callbackUrl)
            .bodyValue(order.toApiOrderPayload())
            .retrieve()
            .bodyToMono(Void::class.java)
            .retry(5)
            .subscribe()
    }
}
