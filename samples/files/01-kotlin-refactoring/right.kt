package com.example.orders

import java.time.Instant

data class OrderItem(
    val sku: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val discountCents: Long = 0L
)

data class Order(
    val id: String,
    val accountId: String,
    val items: List<OrderItem>,
    val createdAt: Instant,
    val priority: Boolean = false
)

class OrderProcessor(
    private val regionalTaxRate: Double = 0.0825
) {
    fun calculateSubtotal(order: Order): Long {
        return order.items.sumOf { item ->
            val gross = item.quantity * item.unitPriceCents
            (gross - item.discountCents).coerceAtLeast(0L)
        }
    }

    fun formatReceipt(order: Order): String {
        val subtotal = calculateSubtotal(order)
        val tax = (subtotal * regionalTaxRate).toLong()
        return "Order #${order.id} for account ${order.accountId}: grandTotal=${subtotal + tax}"
    }

    fun isEligibleForExpressShipping(order: Order): Boolean {
        return order.priority || calculateSubtotal(order) >= 10_000L
    }
}
