package me.habitnudge.data

import java.security.MessageDigest

/** Authentication helper for secret notes with PIN/password protection. */
object NoteAuth {
    private const val SESSION_DURATION_MS = 30 * 60 * 1000L // 30 minutes

    /** Hash PIN/password using SHA-256. */
    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /** Check if currently authenticated (session not expired). */
    fun isAuthenticated(prefs: Prefs): Boolean {
        return System.currentTimeMillis() < prefs.notesAuthenticatedUntil
    }

    /** Authenticate with PIN. Returns true if correct, grants 30-min session. */
    fun authenticate(pin: String, prefs: Prefs): Boolean {
        val storedHash = prefs.notesAuthPin ?: return false

        if (hashPin(pin) == storedHash) {
            prefs.notesAuthenticatedUntil = System.currentTimeMillis() + SESSION_DURATION_MS
            return true
        }
        return false
    }

    /** Set up PIN for the first time. */
    fun setPin(pin: String, prefs: Prefs) {
        prefs.notesAuthPin = hashPin(pin)
    }

    /** Check if PIN has been configured. */
    fun isPinConfigured(prefs: Prefs): Boolean {
        return prefs.notesAuthPin != null
    }

    /** Clear authentication session (logout). */
    fun clearSession(prefs: Prefs) {
        prefs.notesAuthenticatedUntil = 0
    }
}
