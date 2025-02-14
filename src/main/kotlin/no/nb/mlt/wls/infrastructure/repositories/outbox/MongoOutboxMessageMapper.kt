package no.nb.mlt.wls.infrastructure.repositories.outbox

import no.nb.mlt.wls.domain.model.OrderCreatedMessage
import no.nb.mlt.wls.domain.ports.outbound.OutboxMessage

object MongoOutboxMessageMapper {
    fun mapToMongoOutboxMessage(obj: OutboxMessage): MongoOutboxMessage {
        return when (obj) {
            is OrderCreatedMessage -> {
                MongoOutboxMessage(body = obj)
            }
            else -> throw IllegalArgumentException("Unknown type: ${obj::class.simpleName}")
        }
    }

    fun mapFromMongoOutboxMessage(obj: MongoOutboxMessage): OutboxMessage {
        return when (obj.body) {
            is OrderCreatedMessage -> {
                obj.body
            }
            else -> throw IllegalArgumentException("Unknown type: ${obj.body::class.simpleName}")
        }
    }
}
