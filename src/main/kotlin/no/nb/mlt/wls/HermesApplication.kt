package no.nb.mlt.wls

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HermesApplication

fun main(args: Array<String>) {
  runApplication<HermesApplication>(*args)
}
