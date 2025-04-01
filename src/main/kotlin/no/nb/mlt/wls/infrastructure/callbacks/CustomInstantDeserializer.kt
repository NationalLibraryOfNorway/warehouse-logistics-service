package no.nb.mlt.wls.infrastructure.callbacks

import com.fasterxml.jackson.datatype.jsr310.JavaTimeFeature
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.function.Function

class CustomInstantDeserializer :
    InstantDeserializer<Instant>(
        Instant::class.java,
        DateTimeFormatter.ISO_INSTANT,
        Function { temporal: TemporalAccessor? -> Instant.from(temporal) },
        Function { a: FromIntegerArguments -> Instant.ofEpochMilli(a.value) },
        Function { a: FromDecimalArguments -> Instant.ofEpochSecond(a.integer, a.fraction.toLong()) },
        null,
        true,
        JavaTimeFeature.NORMALIZE_DESERIALIZED_ZONE_ID.enabledByDefault(),
        JavaTimeFeature.ALWAYS_ALLOW_STRINGIFIED_DATE_TIMESTAMPS.enabledByDefault()
    )
