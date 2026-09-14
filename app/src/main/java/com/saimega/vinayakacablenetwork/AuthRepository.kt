package com.saimega.vinayakacablenetwork

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

sealed class LoginResult {
    data class Success(val username: String, val name: String, val role: String) : LoginResult()
    object InvalidCredentials : LoginResult()
    object AccountDeactivated : LoginResult()
    data class Failure(val exception: Exception) : LoginResult()
}

sealed class ChangePasswordResult {
    object Success : ChangePasswordResult()
    object IncorrectCurrentPassword : ChangePasswordResult()
    data class Failure(val exception: Exception) : ChangePasswordResult()
}

data class UserAccount(
    val username: String,
    val name: String,
    val role: String,
    val active: Boolean
)

sealed class CreateEmployeeResult {
    object Success : CreateEmployeeResult()
    object UsernameAlreadyExists : CreateEmployeeResult()
    data class Failure(val exception: Exception) : CreateEmployeeResult()
}

class AuthRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Seeds the three pre-existing hardcoded accounts into Firestore the
     * first time this app ever runs against a project with an empty
     * `users` collection. A no-op on every subsequent call. Passwords
     * match what they always were ("1234") so nothing breaks for
     * existing staff — they should change it via Settings afterward.
     */
    suspend fun ensureSeeded() {
        val snapshot = try {
            db.collection("users").limit(1).get(Source.SERVER).await()
        } catch (e: Exception) {
            return
        }
        if (!snapshot.isEmpty) return

        val seedAccounts = listOf(
            Triple("admin", "Admin", Roles.ADMIN),
            Triple("ravi", "Ravi", Roles.EMPLOYEE),
            Triple("tech", "Technician", Roles.TECHNICIAN)
        )

        val batch = db.batch()
        for ((username, name, role) in seedAccounts) {
            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hash("1234", salt)
            val ref = db.collection("users").document(username)
            batch.set(ref, mapOf(
                "username" to username,
                "name" to name,
                "passwordHash" to hash,
                "passwordSalt" to salt,
                "role" to role,
                "active" to true,
                "createdAt" to System.currentTimeMillis()
            ))
        }
        batch.commit().await()
    }

    suspend fun login(username: String, password: String): LoginResult {
        return try {
            val docId = username.trim().lowercase()
            val doc = db.collection("users").document(docId).get(Source.SERVER).await()
            if (!doc.exists()) return LoginResult.InvalidCredentials

            val storedHash = doc.getString("passwordHash") ?: ""
            val salt = doc.getString("passwordSalt") ?: ""
            val computedHash = PasswordHasher.hash(password, salt)
            if (computedHash != storedHash) return LoginResult.InvalidCredentials

            val active = doc.getBoolean("active") ?: true
            if (!active) return LoginResult.AccountDeactivated

            LoginResult.Success(
                username = docId,
                name = doc.getString("name") ?: docId,
                role = doc.getString("role") ?: Roles.EMPLOYEE
            )
        } catch (e: Exception) {
            LoginResult.Failure(e)
        }
    }

    suspend fun changePassword(username: String, currentPassword: String, newPassword: String): ChangePasswordResult {
        return try {
            val docId = username.trim().lowercase()
            val ref = db.collection("users").document(docId)
            val doc = ref.get(Source.SERVER).await()

            val storedHash = doc.getString("passwordHash") ?: ""
            val salt = doc.getString("passwordSalt") ?: ""
            if (PasswordHasher.hash(currentPassword, salt) != storedHash) {
                return ChangePasswordResult.IncorrectCurrentPassword
            }

            val newSalt = PasswordHasher.generateSalt()
            val newHash = PasswordHasher.hash(newPassword, newSalt)
            ref.update(mapOf("passwordHash" to newHash, "passwordSalt" to newSalt)).await()
            ChangePasswordResult.Success
        } catch (e: Exception) {
            ChangePasswordResult.Failure(e)
        }
    }

    suspend fun listUsers(): List<UserAccount> {
        val snapshot = db.collection("users").get(Source.SERVER).await()
        return snapshot.documents.map { doc ->
            UserAccount(
                username = doc.getString("username") ?: doc.id,
                name = doc.getString("name") ?: doc.id,
                role = doc.getString("role") ?: Roles.EMPLOYEE,
                active = doc.getBoolean("active") ?: true
            )
        }
    }

    suspend fun createEmployee(username: String, name: String, password: String, role: String): CreateEmployeeResult {
        return try {
            val docId = username.trim().lowercase()
            val ref = db.collection("users").document(docId)
            val existing = ref.get(Source.SERVER).await()
            if (existing.exists()) return CreateEmployeeResult.UsernameAlreadyExists

            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hash(password, salt)
            ref.set(mapOf(
                "username" to docId,
                "name" to name,
                "passwordHash" to hash,
                "passwordSalt" to salt,
                "role" to role,
                "active" to true,
                "createdAt" to System.currentTimeMillis()
            )).await()
            CreateEmployeeResult.Success
        } catch (e: Exception) {
            CreateEmployeeResult.Failure(e)
        }
    }

    suspend fun updateUserRoleAndActive(username: String, role: String, active: Boolean): Boolean {
        return try {
            db.collection("users").document(username.trim().lowercase())
                .update(mapOf("role" to role, "active" to active))
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteUser(username: String): Boolean {
        return try {
            db.collection("users").document(username.trim().lowercase()).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
