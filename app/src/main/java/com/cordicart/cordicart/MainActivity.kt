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
        findViewById<TextView>(R.id.btnForgotPassword).setOnClickListener {
            Ui.comingSoon(this, "Password reset")
        }
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
