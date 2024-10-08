package no.nb.mlt.wls.infrastructure.callbacks

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class InventoryNotifierAdapter(
    private val webClient: WebClient
) : InventoryNotifier {
    override fun itemChanged(item: Item) {
        TODO("Future task")
    }

    override fun orderChanged(order: Order) {
        webClient
            .post()
            .uri(order.callbackUrl)
            .bodyValue(order.toNotificationOrderPayload())
            .retrieve()
            .bodyToMono(Void::class.java)
            .retry(5)
            .subscribe()
    }
}
