package no.nb.mlt.wls.domain.model.events.email

import no.nb.mlt.wls.domain.model.events.Event

/**
 * Represents an email-related event in the system. Email events are created in reaction to
 * items or orders in the system. For example when an order is created, it will emit an
 * [OrderConfirmationMail] event and an [OrderPickupMail] event.
 *
 * This sealed interface ensures that all implementations are known at compile-time, allowing for
 * exhaustive handling of all event types by the processor.
 *
 * Every [EmailEvent] might also include event-type-specific info. This is important as
 * the information in the event should be final. The reason for this is so that even if
 * anything changes in the system, you are able to reproduce the original email.
 *
 * The email events also contain the following properties:
 * - An unique identifier inherited from the [Event] interface
 * - A body representing the contents of the email (e.g. an order, or an order with its [no.nb.mlt.wls.domain.model.Item]s)
 */
sealed interface EmailEvent : Event
