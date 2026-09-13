package com.saimega.vinayakacablenetwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `same password and salt produce the same hash every time`() {
        val salt = PasswordHasher.generateSalt()
        val hash1 = PasswordHasher.hash("mypassword", salt)
        val hash2 = PasswordHasher.hash("mypassword", salt)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `different passwords with the same salt produce different hashes`() {
        val salt = PasswordHasher.generateSalt()
        val hash1 = PasswordHasher.hash("password1", salt)
        val hash2 = PasswordHasher.hash("password2", salt)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `same password with different salts produces different hashes`() {
        val salt1 = PasswordHasher.generateSalt()
        val salt2 = PasswordHasher.generateSalt()
        val hash1 = PasswordHasher.hash("samepassword", salt1)
        val hash2 = PasswordHasher.hash("samepassword", salt2)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `generateSalt produces different values each call`() {
        val salt1 = PasswordHasher.generateSalt()
        val salt2 = PasswordHasher.generateSalt()
        assertNotEquals(salt1, salt2)
    }

    @Test
    fun `hash never contains the plain password as a substring`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("1234", salt)
        assertTrue(!hash.contains("1234"))
    }
}
