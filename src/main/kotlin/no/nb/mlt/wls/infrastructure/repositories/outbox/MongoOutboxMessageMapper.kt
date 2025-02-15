package no.nb.mlt.wls.infrastructure.repositories.outbox

import no.nb.mlt.wls.domain.ports.outbound.OutboxMessage

object MongoOutboxMessageMapper {
    fun mapFromMongoOutboxMessage(obj: MongoOutboxMessage): OutboxMessage {
        TODO("Not yet implemented")
//        return when (obj.type) {
//            OrderCreatedMessage::class -> OrderCreatedMessage(obj.body as Order)
//            OrderUpdatedMessage::class -> OrderUpdatedMessage(obj.body as Order)
//            else -> throw IllegalArgumentException("Unknown type: ${obj.type}")
//        }
    }
}
