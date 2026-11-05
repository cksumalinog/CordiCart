package com.cordicart.cordicart

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

/**
 * Screen 1 · Log in (the app's launcher screen).
 * A student who is already signed in skips this screen and goes where they belong.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var btnTogglePassword: ImageButton
    private lateinit var btnLogin: Button
    private lateinit var msgBox: View

    private var passwordVisible = false
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        btnLogin = findViewById(R.id.btnLogin)
        msgBox = findViewById(R.id.msgBox)

        btnTogglePassword.setOnClickListener { togglePassword() }
        btnLogin.setOnClickListener { attemptLogin() }
        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { attemptLogin(); true } else false
        }
        findViewById<TextView>(R.id.btnForgotPassword).setOnClickListener { showPasswordReset() }
        findViewById<Button>(R.id.btnCreateAccount).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        // Stay logged in: a signed-in student goes straight to the right screen.
        val user = auth.currentUser ?: return
        setBusy(true)
        AuthRouter.route(this, user) { message ->
            setBusy(false)
            Ui.showError(msgBox, message)
        }
    }

    private fun attemptLogin() {
        val email = EmailValidator.normalize(emailInput.text.toString())
        val password = passwordInput.text.toString()

        when {
            email.isEmpty() -> { Ui.showError(msgBox, getString(R.string.error_empty_email)); return }
            !EmailValidator.isValidStudent(email) -> { Ui.showError(msgBox, getString(R.string.error_invalid_email)); return }
            password.isEmpty() -> { Ui.showError(msgBox, "Please enter your password."); return }
        }

        Ui.hideMessage(msgBox)
        setBusy(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener
                AuthRouter.route(this, user) { message ->
                    setBusy(false)
                    Ui.showError(msgBox, message)
                }
            }
            .addOnFailureListener { e ->
                setBusy(false)
                val message =
                    if (e is FirebaseAuthInvalidCredentialsException || e is FirebaseAuthInvalidUserException)
                        getString(R.string.error_wrong_login)
                    else "Log in failed: ${e.message}"
                Ui.showError(msgBox, message)
            }
    }

    /**
     * "Forgot password?": Firebase emails the student a link to set a new password.
     * The email field is pre-filled with whatever is typed on the Log in screen.
     */
    private fun showPasswordReset() {
        val input = EditText(this).apply {
            hint = getString(R.string.hint_email)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(emailInput.text)
            setSingleLine()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.reset_title)
            .setMessage(R.string.reset_message)
            .setView(box)
            .setPositiveButton(R.string.reset_send, null)   // set below, so a bad email doesn't close the dialog
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = EmailValidator.normalize(input.text.toString())
                if (!EmailValidator.isValidStudent(email)) {
                    input.error = getString(R.string.error_invalid_email)
                    return@setOnClickListener
                }
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (isFinishing || isDestroyed) return@addOnCompleteListener
                        dialog.dismiss()
                        // Same message either way, so the screen doesn't reveal which emails have accounts.
                        if (task.isSuccessful || task.exception is FirebaseAuthInvalidUserException) {
                            Ui.showInfo(msgBox, getString(R.string.reset_sent, email))
                            Toast.makeText(this, "Check your UC email for the reset link.", Toast.LENGTH_LONG).show()
                        } else {
                            Ui.showError(msgBox, "Could not send the reset email: ${task.exception?.message}")
                        }
                    }
            }
        }
        dialog.show()
    }

    /** Shows or hides the password, keeping the cursor at the end. */
    private fun togglePassword() {
        passwordVisible = !passwordVisible
        passwordInput.inputType = InputType.TYPE_CLASS_TEXT or
            if (passwordVisible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else InputType.TYPE_TEXT_VARIATION_PASSWORD
        passwordInput.setSelection(passwordInput.text.length)
        btnTogglePassword.setImageResource(if (passwordVisible) R.drawable.ic_eye_off else R.drawable.ic_eye)
        btnTogglePassword.contentDescription = if (passwordVisible) "Hide password" else "Show password"
    }

    private fun setBusy(busy: Boolean) {
        btnLogin.isEnabled = !busy
        btnLogin.text = if (busy) "Logging in..." else getString(R.string.log_in)
    }
}
