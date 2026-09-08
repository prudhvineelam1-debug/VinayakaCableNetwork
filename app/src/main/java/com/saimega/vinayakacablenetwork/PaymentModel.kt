package com.saimega.vinayakacablenetwork

data class PaymentModel(
    val paymentId: String = "",
    val customerId: String = "",
    val name: String = "",
    val baseAmount: Double = 0.0,
    val extraCharges: Double = 0.0,
    val total: Double = 0.0,
    val paid: Double = 0.0,
    val remaining: Double = 0.0,
    val paymentMode: String = "",
    val paymentNumber: String = "",
    val date: String = "",
    val month: Int = 0,
    val year: Int = 0,
    val timestamp: Long = 0L
)
