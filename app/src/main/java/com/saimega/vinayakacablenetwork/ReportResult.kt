package com.saimega.vinayakacablenetwork

/**
 * Data class representing aggregated report results.
 */
 data class ReportResult(
    val totalPaid: Double = 0.0,
    val cashTotal: Double = 0.0,
    val upiTotal: Double = 0.0,
    val paymentCount: Int = 0,
    val startTs: Long = 0L,
    val endTs: Long = 0L
)
