package no.nb.mlt.wls.infrastructure.config

import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class TrailingSlashRedirectFilter : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> {
        val request = exchange.request
        val path = request.path.value()

        if (path.endsWith("/")) {
            return chain.filter(
                exchange.mutate().request(
                    request.mutate().path(
                        path.removeSuffix("/")
                    ).build()
                ).build()
            )
        }

        return chain.filter(exchange)
    }
}
