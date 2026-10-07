package com.cordicart.cordicart

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Screen 3 · Verify your email (step 2 of 2).
 * Sends a 6-digit code to the student's UC email and checks it.
 */
class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var boxes: List<EditText>
    private lateinit var txtResend: TextView
    private lateinit var btnVerify: Button
    private lateinit var msgBox: View

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val otpManager by lazy { OtpManager() }
    private var user: FirebaseUser? = null
    private var cooldownTimer: CountDownTimer? = null
    private var coolingDown = false
    private var codeDialog: AlertDialog? = null   // the demo-mode code dialog, closed when the screen closes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        user = auth.currentUser
        if (user == null) {
            Ui.openFresh(this, MainActivity::class.java)
            return
        }
        setContentView(R.layout.activity_verify_email)

        boxes = listOf(R.id.otp1, R.id.otp2, R.id.otp3, R.id.otp4, R.id.otp5, R.id.otp6).map { findViewById<EditText>(it) }
        txtResend = findViewById(R.id.txtResend)
        btnVerify = findViewById(R.id.btnVerify)
        msgBox = findViewById(R.id.msgBox)
        findViewById<TextView>(R.id.txtEmail).text = user?.email

        setUpCodeBoxes()
        txtResend.setOnClickListener { if (!coolingDown) sendCode() }
        btnVerify.setOnClickListener { verify() }
        findViewById<View>(R.id.btnBack).setOnClickListener { leave() }
        findViewById<View>(R.id.txtChangeEmail).setOnClickListener { changeEmail() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        // Send automatically the first time the screen opens (not again on rotation).
        if (savedInstanceState == null) sendCode()
    }

    /** Typing a digit jumps to the next box; Backspace on an empty box goes back one. */
    private fun setUpCodeBoxes() {
        boxes.forEachIndexed { i, box ->
            box.doAfterTextChanged { text ->
                if (text?.length == 1 && i < boxes.lastIndex) boxes[i + 1].requestFocus()
                if (enteredCode().length == boxes.size) Ui.hideMessage(msgBox)
            }
            box.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN &&
                    box.text.isEmpty() && i > 0
                ) {
                    boxes[i - 1].text.clear()
                    boxes[i - 1].requestFocus()
                    true
                } else false
            }
        }
    }

    private fun enteredCode(): String = boxes.joinToString("") { it.text.toString().trim() }

    private fun clearBoxes() {
        boxes.forEach { it.text.clear() }
        boxes.first().requestFocus()
    }

    private fun sendCode() {
        val current = user ?: return
        txtResend.isEnabled = false
        Ui.showInfo(msgBox, "Sending code...")

        otpManager.sendOtp(
            uid = current.uid,
            email = current.email.orEmpty(),
            onSent = { fallbackCode ->
                if (isFinishing || isDestroyed) return@sendOtp
                startCooldown()
                if (fallbackCode != null) {
                    Ui.showInfo(msgBox, "Demo mode: email sending is not set up, so the code is shown on screen.")
                    codeDialog?.dismiss()
                    codeDialog = AlertDialog.Builder(this)
                        .setTitle("Demo mode")
                        .setMessage("Code: $fallbackCode")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    Ui.showInfo(msgBox, "Code sent. Check your inbox and the Junk folder.")
                }
                clearBoxes()
            },
            onFailure = { message ->
                if (isFinishing || isDestroyed) return@sendOtp
                txtResend.isEnabled = true
                Ui.showError(msgBox, message)
            }
        )
    }

    private fun verify() {
        val current = user ?: return
        val code = enteredCode()
        if (!code.matches(Regex("\\d{6}"))) {
            Ui.showError(msgBox, getString(R.string.error_invalid_otp))
            return
        }

        btnVerify.isEnabled = false
        otpManager.verifyOtp(
            uid = current.uid,
            enteredCode = code,
            onResult = { outcome ->
                if (isFinishing || isDestroyed) return@verifyOtp
                btnVerify.isEnabled = true
                when (outcome.result) {
                    OtpManager.Result.SUCCESS -> {
                        Toast.makeText(this, R.string.verification_success, Toast.LENGTH_LONG).show()
                        Ui.openFresh(this, FeedActivity::class.java)
                    }
                    OtpManager.Result.WRONG_CODE -> {
                        Ui.showError(msgBox, "Incorrect code. ${outcome.attemptsLeft} attempt(s) left.")
                        clearBoxes()
                    }
                    OtpManager.Result.EXPIRED ->
                        Ui.showError(msgBox, "This code has expired. Tap Resend code to get a new one.")
                    OtpManager.Result.TOO_MANY_ATTEMPTS ->
                        Ui.showError(msgBox, "Too many wrong attempts. Tap Resend code to get a new one.")
                    OtpManager.Result.NO_CODE ->
                        Ui.showError(msgBox, "No active code. Tap Resend code.")
                }
            },
            onError = { message ->
                if (isFinishing || isDestroyed) return@verifyOtp
                btnVerify.isEnabled = true
                Ui.showError(msgBox, "Verification failed: $message")
            }
        )
    }

    /** Blocks Resend for 60 seconds so the inbox can't be spammed. */
    private fun startCooldown() {
        cooldownTimer?.cancel()
        coolingDown = true
        txtResend.isEnabled = false
        txtResend.setTextColor(ContextCompat.getColor(this, R.color.cc_muted))
        cooldownTimer = object : CountDownTimer(OtpManager.RESEND_COOLDOWN_MS, 1000) {
            override fun onTick(millisLeft: Long) {
                txtResend.text = "Resend in ${(millisLeft + 999) / 1000}s"
            }

            override fun onFinish() {
                coolingDown = false
                txtResend.isEnabled = true
                txtResend.setText(R.string.resend_code)
                txtResend.setTextColor(ContextCompat.getColor(this@VerifyEmailActivity, R.color.cc_pine))
            }
        }.start()
    }

    /** Back: sign out and return to Log in. The student can finish verifying later by logging in. */
    private fun leave() {
        auth.signOut()
        Ui.openFresh(this, MainActivity::class.java)
    }

    /** "Wrong email? Change it": removes this unverified account and starts sign-up again. */
    private fun changeEmail() {
        val current = user ?: return
        current.delete().addOnCompleteListener {
            auth.signOut()
            val login = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivities(arrayOf(login, Intent(this, RegisterActivity::class.java)))
            finish()
        }
    }

    override fun onDestroy() {
        codeDialog?.dismiss()
        cooldownTimer?.cancel()
        super.onDestroy()
    }
}