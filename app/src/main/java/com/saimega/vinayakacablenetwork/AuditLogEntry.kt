package com.saimega.vinayakacablenetwork

data class AuditLogEntry(
    val id: String = "",
    val timestamp: Long = 0L,
    val actorUsername: String = "",
    val actorRole: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val targetName: String = "",
    val details: String = ""
)
