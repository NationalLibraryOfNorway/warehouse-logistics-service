package no.nb.mlt.wls.domain.model

import no.nb.mlt.wls.createTestOrder
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OrderTest {
    @Test
    fun `should not be able to update to NOT_STARTED when processing is started`() {
        val order = createTestOrder(status = Order.Status.IN_PROGRESS)

        assertThrows(ValidationException::class.java) {
            order.updateStatus(Order.Status.NOT_STARTED)
        }
    }

    @Test
    fun `should not be able to update to IN_PROGRESS or NOT_STARTED when order is picked`() {
        val order = createPickedOrder()

        assertThrows(ValidationException::class.java) {
            order.updateStatus(Order.Status.NOT_STARTED)
        }

        assertThrows(ValidationException::class.java) {
            order.updateStatus(Order.Status.IN_PROGRESS)
        }
    }

    @Test
    fun `should not be able to update status when order is deleted`() {
        val deletedOrder = createTestOrder(status = Order.Status.DELETED)

        Order.Status.entries.forEach { status ->
            assertThrows(ValidationException::class.java) {
                deletedOrder.updateStatus(status)
            }
        }
    }

    @Test
    fun `should not be able to update status when order is returned`() {
        val returnedOrder = createTestOrder(status = Order.Status.RETURNED)

        Order.Status.entries.forEach { status ->
            assertThrows(ValidationException::class.java) {
                returnedOrder.updateStatus(status)
            }
        }
    }

    @Test
    fun `should be able to update status in correct direction`() {
        var order = createTestOrder(status = Order.Status.NOT_STARTED)

        // Valid transitions
        order = order.updateStatus(Order.Status.IN_PROGRESS)
        assertThat(order.status).isEqualTo(Order.Status.IN_PROGRESS)

        order = order.updateStatus(Order.Status.COMPLETED)
        assertThat(order.status).isEqualTo(Order.Status.COMPLETED)

        val returnedOrder = order.updateStatus(Order.Status.RETURNED)
        val deletedOrder = order.updateStatus(Order.Status.DELETED)

        assertThat(returnedOrder.status).isEqualTo(Order.Status.RETURNED)
        assertThat(deletedOrder.status).isEqualTo(Order.Status.DELETED)
    }

    @Test
    fun `should be closed when status is DELETED or RETURNED`() {
        var order = createTestOrder(status = Order.Status.NOT_STARTED)
        assertThat(order.isClosed()).isFalse()
        order = order.updateStatus(Order.Status.IN_PROGRESS)
        assertThat(order.isClosed()).isFalse()
        order = order.updateStatus(Order.Status.COMPLETED)
        assertThat(order.isClosed()).isFalse()

        val deletedOrder = order.updateStatus(Order.Status.DELETED)
        val returnedOrder = order.updateStatus(Order.Status.RETURNED)
        assertThat(deletedOrder.isClosed()).isTrue()
        assertThat(returnedOrder.isClosed()).isTrue()
    }

    @Test
    fun `should not be able to update order line status for closed order`() {
        val returnedOrder = createTestOrder(status = Order.Status.RETURNED)

        assertThrows(ValidationException::class.java) {
            returnedOrder.pickItems(returnedOrder.orderLine.map { it.hostId })
        }

        val deletedOrder = createTestOrder(status = Order.Status.DELETED)
        assertThrows(ValidationException::class.java) {
            deletedOrder.pickItems(deletedOrder.orderLine.map { it.hostId })
        }
    }

    @Test
    fun `partially picking items should set order status to IN_PROGRESS and items to picked`() {
        var order = createTestOrder(status = Order.Status.NOT_STARTED)
        order = order.pickItems(listOf(order.orderLine[0].hostId))

        assertThat(order.status).isEqualTo(Order.Status.IN_PROGRESS)
        assertThat(order.orderLine[0].status).isEqualTo(Order.OrderItem.Status.PICKED)
        assertThat(order.orderLine[1].status).isEqualTo(Order.OrderItem.Status.NOT_STARTED)
    }

    @Test
    fun `picking all items should set order status to COMPLETED and items to picked`() {
        var order = createTestOrder(status = Order.Status.NOT_STARTED)
        order = order.pickItems(order.orderLine.map { it.hostId })

        assertThat(order.status).isEqualTo(Order.Status.COMPLETED)
        order.orderLine.forEach {
            assertThat(it.status).isEqualTo(Order.OrderItem.Status.PICKED)
        }
    }

    @Test
    fun `partially returning items should keep order status as COMPLETED and set items to returned`() {
        var order = createPickedOrder()

        order = order.returnItems(listOf(order.orderLine[0].hostId))

        assertThat(order.status).isEqualTo(Order.Status.COMPLETED)
        assertThat(order.orderLine[0].status).isEqualTo(Order.OrderItem.Status.RETURNED)
        assertThat(order.orderLine[1].status).isEqualTo(Order.OrderItem.Status.PICKED)
    }

    @Test
    fun `returning all items should set order status to RETURNED and items to returned`() {
        var order = createPickedOrder()
        order = order.returnItems(order.orderLine.map { it.hostId })

        assertThat(order.status).isEqualTo(Order.Status.RETURNED)
        order.orderLine.forEach {
            assertThat(it.status).isEqualTo(Order.OrderItem.Status.RETURNED)
        }
    }

    private fun createPickedOrder() =
        createTestOrder(
            status = Order.Status.COMPLETED,
            orderLine =
                listOf(
                    Order.OrderItem(hostId = "item1", status = Order.OrderItem.Status.PICKED),
                    Order.OrderItem(hostId = "item2", status = Order.OrderItem.Status.PICKED)
                )
        )
}
