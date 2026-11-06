package com.example.orders

import java.time.Instant

data class OrderItem(
    val sku: String,
    val quantity: Int,
    val unitPriceCents: Long
)

data class Order(
    val id: String,
    val customerId: String,
    val items: List<OrderItem>,
    val createdAt: Instant
)

class OrderProcessor(
    private val taxRate: Double = 0.0825
) {
    fun calculateSubtotal(order: Order): Long {
        var total = 0L
        for (item in order.items) {
            total += item.quantity * item.unitPriceCents
        }
        return total
    }

    fun formatReceipt(order: Order): String {
        val subtotal = calculateSubtotal(order)
        val tax = (subtotal * taxRate).toLong()
        return "Order #${order.id} for customer ${order.customerId}: total=${subtotal + tax}"
    }

    @Deprecated("Use formatReceipt instead")
    fun legacySummary(order: Order): String {
        return "${order.id}:${order.items.size}"
    }
}
