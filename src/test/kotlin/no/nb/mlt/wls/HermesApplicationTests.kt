package no.nb.mlt.wls

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@EnableTestcontainers
@SpringBootTest
class HermesApplicationTests {
    @Test
    fun contextLoads() {
    }

    @Test
    fun itsFunToNOTFail() {
        assert(true)
    }
}
