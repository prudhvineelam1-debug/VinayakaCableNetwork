package com.saimega.vinayakacablenetwork

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

object AuditAction {
    const val EDIT_CUSTOMER = "EDIT_CUSTOMER"
    const val DELETE_CUSTOMER = "DELETE_CUSTOMER"
    const val EDIT_EMPLOYEE = "EDIT_EMPLOYEE"
    const val DELETE_EMPLOYEE = "DELETE_EMPLOYEE"
}

object AuditTargetType {
    const val CUSTOMER = "CUSTOMER"
    const val EMPLOYEE = "EMPLOYEE"
}

class AuditLogRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun logAction(
        actorUsername: String,
        actorRole: String,
        action: String,
        targetType: String,
        targetId: String,
        targetName: String,
        details: String = ""
    ) {
        val ref = db.collection("audit_log").document()
        ref.set(
            mapOf(
                "timestamp" to System.currentTimeMillis(),
                "actorUsername" to actorUsername,
                "actorRole" to actorRole,
                "action" to action,
                "targetType" to targetType,
                "targetId" to targetId,
                "targetName" to targetName,
                "details" to details
            )
        ).await()
    }

    suspend fun listEntries(limit: Long = 200): List<AuditLogEntry> {
        val snapshot = db.collection("audit_log")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .get(Source.SERVER)
            .await()
        return snapshot.documents.map { doc ->
            AuditLogEntry(
                id = doc.id,
                timestamp = doc.getLong("timestamp") ?: 0L,
                actorUsername = doc.getString("actorUsername") ?: "",
                actorRole = doc.getString("actorRole") ?: "",
                action = doc.getString("action") ?: "",
                targetType = doc.getString("targetType") ?: "",
                targetId = doc.getString("targetId") ?: "",
                targetName = doc.getString("targetName") ?: "",
                details = doc.getString("details") ?: ""
            )
        }
    }

    suspend fun deleteEntry(id: String): Boolean {
        return try {
            db.collection("audit_log").document(id).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearAll(): Boolean {
        return try {
            val snapshot = db.collection("audit_log").get(Source.SERVER).await()
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
