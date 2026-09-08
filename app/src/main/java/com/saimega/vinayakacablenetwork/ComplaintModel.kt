package com.saimega.vinayakacablenetwork

import java.io.Serializable

data class ComplaintModel(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val description: String = "",
    val status: String = "NEW", // NEW, IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED, CANCELLED
    val priority: String = "NORMAL", // LOW, NORMAL, HIGH, URGENT
    val assignedTo: String = "",
    val createdBy: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val closedTimestamp: Long = 0,
    val resolution: String = ""
) : Serializable
