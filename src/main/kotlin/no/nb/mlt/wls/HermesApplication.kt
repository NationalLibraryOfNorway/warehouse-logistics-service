package no.nb.mlt.wls

import org.reactivestreams.Publisher
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HermesApplication

fun main(args: Array<String>) {
    runApplication<HermesApplication>(*args)
}

// Test Qodana, delete later
class Dummy : Publisher<String> {
    override fun subscribe(s: org.reactivestreams.Subscriber<in String>?) {
        val i = if (s != null) 1 else 1
        throw UnsupportedOperationException("not implemented")
    }
}
