package no.nb.mlt.wls.infrastructure.config

import io.micrometer.context.ContextRegistry
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks

@Configuration
class ObservationContextConfig {
    @PostConstruct
    fun configureContextPropagation() {
        // Ensure ObservationThreadLocalAccessor is registered
        ContextRegistry.getInstance().registerThreadLocalAccessor(ObservationThreadLocalAccessor())

        // Enable automatic context propagation for all operators
        Hooks.enableAutomaticContextPropagation()
    }
}
