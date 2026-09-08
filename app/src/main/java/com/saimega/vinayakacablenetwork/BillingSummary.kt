package com.saimega.vinayakacablenetwork

/**
 * Data class representing a monthly billing summary stored in the
 * `billing/{customerId}/months/{yyyy-MM}` collection.
 */
 data class BillingSummary(
    val customerId: String = "",
    val name: String = "",
    val total: Double = 0.0,
    val paid: Double = 0.0,
    val remaining: Double = 0.0,
    val timestamp: Long = 0L
)
