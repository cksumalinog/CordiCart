package com.cordicart.cordicart

import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * Generates, stores, and checks 6-digit one-time PINs.
 * Firestore: otps/{uid} -> codeHash, expiresAt, attempts, sentAt
 * Only a SHA-256 hash of the code is stored, never the code itself.
 */
class OtpManager {

    enum class Result { SUCCESS, WRONG_CODE, EXPIRED, TOO_MANY_ATTEMPTS, NO_CODE }

    data class Outcome(val result: Result, val attemptsLeft: Int)

    private val db = FirebaseFirestore.getInstance()
    private val random = SecureRandom()

    /** Always exactly 6 digits, including leading zeros (e.g. "004521"). */
    private fun generateCode(): String = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000))

    /**
     * Saves a new code (replacing any older one) and emails it.
     * onSent receives the code itself only in demo mode, so it can be shown on screen.
     */
    fun sendOtp(
        uid: String,
        email: String,
        onSent: (fallbackCode: String?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val code = generateCode()
        val now = System.currentTimeMillis()
        val otp = hashMapOf(
            "codeHash" to hash(code, uid),
            "expiresAt" to now + OTP_VALIDITY_MS,
            "attempts" to 0,
            "sentAt" to now
        )

        db.collection("otps").document(uid).set(otp)
            .addOnSuccessListener {
                if (!MailSender.isConfigured) {
                    onSent(code)   // demo mode
                } else {
                    MailSender.sendOtpEmail(
                        recipient = email,
                        code = code,
                        onSent = { onSent(null) },
                        onError = { message -> onFailure("Email could not be sent ($message)") }
                    )
                }
            }
            .addOnFailureListener { e -> onFailure("Could not save code: ${e.message}") }
    }

    /**
     * Runs inside a Firestore transaction so the attempt counter and the
     * "verified" flag are updated together, all-or-nothing.
     */
    fun verifyOtp(
        uid: String,
        enteredCode: String,
        onResult: (Outcome) -> Unit,
        onError: (String) -> Unit
    ) {
        val otpRef = db.collection("otps").document(uid)
        val userRef = db.collection("users").document(uid)

        db.runTransaction<Outcome> { tx ->
            val snap = tx.get(otpRef)
            if (!snap.exists()) return@runTransaction Outcome(Result.NO_CODE, 0)

            val expiresAt = snap.getLong("expiresAt")
            val attempts = (snap.getLong("attempts") ?: 0L).toInt()
            val storedHash = snap.getString("codeHash")

            when {
                attempts >= MAX_ATTEMPTS ->
                    Outcome(Result.TOO_MANY_ATTEMPTS, 0)

                expiresAt == null || System.currentTimeMillis() > expiresAt ->
                    Outcome(Result.EXPIRED, MAX_ATTEMPTS - attempts)

                hash(enteredCode, uid) == storedHash -> {
                    tx.delete(otpRef)                     // a used code can never be reused
                    tx.update(userRef, "verified", true)
                    Outcome(Result.SUCCESS, MAX_ATTEMPTS - attempts)
                }

                else -> {
                    val newAttempts = attempts + 1
                    tx.update(otpRef, "attempts", newAttempts)
                    val left = MAX_ATTEMPTS - newAttempts
                    Outcome(if (left <= 0) Result.TOO_MANY_ATTEMPTS else Result.WRONG_CODE, left)
                }
            }
        }
            .addOnSuccessListener { outcome -> onResult(outcome) }
            .addOnFailureListener { e -> onError(e.message ?: "Unknown error") }
    }

    companion object {
        const val OTP_VALIDITY_MS = 5 * 60 * 1000L     // 5 minutes
        const val MAX_ATTEMPTS = 5
        const val RESEND_COOLDOWN_MS = 60 * 1000L      // 60 seconds

        /** The uid is mixed in so the same code hashes differently for different users. */
        fun hash(code: String, uid: String): String {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest("$uid:$code".toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
