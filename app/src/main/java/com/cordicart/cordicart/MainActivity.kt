package com.cordicart.cordicart

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * One screen for both new and returning students.
 *
 * "Send Code": signs in, or creates the account if it doesn't exist yet.
 *              Already-verified students go straight to the feed.
 *              New/unverified students get their profile saved and a 6-digit code emailed.
 * "Verify":    checks the code; on success the student enters the feed.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var departmentSpinner: Spinner
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var otpInput: EditText
    private lateinit var sendCodeButton: Button
    private lateinit var verifyButton: Button
    private lateinit var resultText: TextView

    private var cooldownTimer: CountDownTimer? = null
    private var coolingDown = false

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val otpManager by lazy { OtpManager() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nameInput = findViewById(R.id.nameInput)
        departmentSpinner = findViewById(R.id.departmentSpinner)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        otpInput = findViewById(R.id.otpInput)
        sendCodeButton = findViewById(R.id.sendCodeButton)
        verifyButton = findViewById(R.id.verifyButton)
        resultText = findViewById(R.id.resultText)

        sendCodeButton.setOnClickListener { onSendCodeClicked() }
        verifyButton.setOnClickListener { onVerifyClicked() }
    }

    override fun onStart() {
        super.onStart()
        // Stay logged in: a verified student who reopens the app skips this screen.
        val user = auth.currentUser ?: return

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (isFinishing) return@addOnSuccessListener
                if (doc.exists() && doc.getBoolean("verified") == true) {
                    openFeed()
                } else {
                    emailInput.setText(user.email)
                    showInfo("Enter the code sent to ${user.email}, or tap Send Code to get a new one.")
                }
            }
    }

    // ------------------------------------------------------------------ Send code

    private fun onSendCodeClicked() {
        val email = EmailValidator.normalize(emailInput.text.toString())
        val password = passwordInput.text.toString()

        when {
            email.isEmpty() -> { showError(getString(R.string.error_empty_email)); return }
            !EmailValidator.isValidStudent(email) -> { showError(getString(R.string.error_invalid_email)); return }
            password.length < 8 -> { showError(getString(R.string.error_short_password)); return }
        }

        setBusy(true)
        showInfo("Signing in...")

        // Try signing in first. If there is no such account, create one.
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result -> result.user?.let { afterSignIn(it) } }
            .addOnFailureListener { e ->
                if (e is FirebaseAuthInvalidCredentialsException || e is FirebaseAuthInvalidUserException) {
                    createAccount(email, password)
                } else {
                    setBusy(false)
                    showError("Sign-in failed: ${e.message}")
                }
            }
    }

    private fun createAccount(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result -> result.user?.let { afterSignIn(it) } }
            .addOnFailureListener { e ->
                setBusy(false)
                if (e is FirebaseAuthUserCollisionException) {
                    // The account exists, so the sign-in above failed because of the password.
                    showError(getString(R.string.error_wrong_password))
                } else {
                    showError("Could not create account: ${e.message}")
                }
            }
    }

    private fun afterSignIn(user: FirebaseUser) {
        val userRef = db.collection("users").document(user.uid)

        userRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.getBoolean("verified") == true) {
                    setBusy(false)
                    Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                    openFeed()
                    return@addOnSuccessListener
                }

                // New or unverified student: name and department are required.
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    setBusy(false)
                    showError(getString(R.string.error_empty_name))
                    return@addOnSuccessListener
                }
                val department = departmentSpinner.selectedItem.toString()

                val save: Task<Void> = if (doc.exists()) {
                    userRef.update("displayName", name, "department", department)
                } else {
                    userRef.set(
                        hashMapOf(
                            "uid" to user.uid,
                            "email" to (user.email ?: ""),
                            "displayName" to name,
                            "department" to department,
                            "verified" to false,          // only the OTP can change this
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                save.addOnSuccessListener { sendCode(user) }
                    .addOnFailureListener { e ->
                        setBusy(false)
                        showError("Could not save your profile: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                setBusy(false)
                showError("Could not load your account: ${e.message}")
            }
    }

    private fun sendCode(user: FirebaseUser) {
        val email = user.email ?: ""
        showInfo("Sending code...")

        otpManager.sendOtp(
            uid = user.uid,
            email = email,
            onSent = { fallbackCode ->
                if (isFinishing || isDestroyed) return@sendOtp
                setBusy(false)
                startCooldown()
                if (fallbackCode != null) {
                    showInfo("Demo mode: email sending is not configured.")
                    AlertDialog.Builder(this)
                        .setTitle("Demo mode")
                        .setMessage("SMTP is not set up, so your code is shown here instead:\n\n$fallbackCode")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    showSuccess("Code sent to $email. Check your inbox (and Junk folder).")
                }
            },
            onFailure = { message ->
                if (isFinishing || isDestroyed) return@sendOtp
                setBusy(false)
                showError(message)
            }
        )
    }

    // ------------------------------------------------------------------ Verify

    private fun onVerifyClicked() {
        val user = auth.currentUser
        if (user == null) {
            showError("Tap Send Code first.")
            return
        }

        val code = otpInput.text.toString().trim()
        if (!code.matches(Regex("\\d{6}"))) {
            showError(getString(R.string.error_invalid_otp))
            return
        }

        setBusy(true)
        otpManager.verifyOtp(
            uid = user.uid,
            enteredCode = code,
            onResult = { outcome ->
                if (isFinishing || isDestroyed) return@verifyOtp
                setBusy(false)
                when (outcome.result) {
                    OtpManager.Result.SUCCESS -> {
                        // A Toast stays visible across the screen change, so the user sees it.
                        Toast.makeText(this, R.string.verification_success, Toast.LENGTH_LONG).show()
                        openFeed()
                    }
                    OtpManager.Result.WRONG_CODE ->
                        showError("Incorrect code. ${outcome.attemptsLeft} attempt(s) left.")
                    OtpManager.Result.EXPIRED ->
                        showError("This code has expired. Tap Send Code to get a new one.")
                    OtpManager.Result.TOO_MANY_ATTEMPTS ->
                        showError("Too many wrong attempts. Tap Send Code to get a new one.")
                    OtpManager.Result.NO_CODE ->
                        showError("No active code. Tap Send Code first.")
                }
            },
            onError = { message ->
                if (isFinishing || isDestroyed) return@verifyOtp
                setBusy(false)
                showError("Verification failed: $message")
            }
        )
    }

    // ------------------------------------------------------------------ Helpers

    /** Blocks Send Code for 60 seconds so the inbox can't be spammed. */
    private fun startCooldown() {
        cooldownTimer?.cancel()
        coolingDown = true
        sendCodeButton.isEnabled = false
        cooldownTimer = object : CountDownTimer(OtpManager.RESEND_COOLDOWN_MS, 1000) {
            override fun onTick(millisLeft: Long) {
                sendCodeButton.text = "Resend in ${(millisLeft + 999) / 1000}s"
            }

            override fun onFinish() {
                coolingDown = false
                sendCodeButton.setText(R.string.send_code)
                sendCodeButton.isEnabled = true
            }
        }.start()
    }

    private fun setBusy(busy: Boolean) {
        sendCodeButton.isEnabled = !busy && !coolingDown
        verifyButton.isEnabled = !busy
    }

    private fun showError(message: String) {
        resultText.setTextColor(Color.RED)
        resultText.text = message
    }

    private fun showInfo(message: String) {
        resultText.setTextColor(Color.parseColor("#555555"))
        resultText.text = message
    }

    private fun showSuccess(message: String) {
        resultText.setTextColor(Color.parseColor("#006400"))
        resultText.text = message
    }

    private fun openFeed() {
        val intent = Intent(this, FeedActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        cooldownTimer?.cancel()
        super.onDestroy()
    }
}
