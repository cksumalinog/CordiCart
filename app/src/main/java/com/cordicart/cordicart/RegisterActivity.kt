package com.cordicart.cordicart

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Screen 2 · Create account (step 1 of 2).
 * Creates the Firebase account and saves the profile as NOT verified.
 * Only the code on the next screen can mark it verified.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var departmentSpinner: Spinner
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmInput: EditText
    private lateinit var btnContinue: Button
    private lateinit var msgBox: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        nameInput = findViewById(R.id.nameInput)
        departmentSpinner = findViewById(R.id.departmentSpinner)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmInput = findViewById(R.id.confirmInput)
        btnContinue = findViewById(R.id.btnContinue)
        msgBox = findViewById(R.id.msgBox)

        departmentSpinner.adapter = ArrayAdapter.createFromResource(
            this, R.array.department_options, R.layout.item_spinner
        ).also { it.setDropDownViewResource(R.layout.item_spinner_dropdown) }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.txtGoLogin).setOnClickListener { finish() }
        btnContinue.setOnClickListener { attemptRegister() }
    }

    private fun attemptRegister() {
        val name = nameInput.text.toString().trim()
        val department = departmentSpinner.selectedItem.toString()
        val email = EmailValidator.normalize(emailInput.text.toString())
        val password = passwordInput.text.toString()
        val confirm = confirmInput.text.toString()

        val error = when {
            name.isEmpty() -> getString(R.string.error_empty_name)
            email.isEmpty() -> getString(R.string.error_empty_email)
            !EmailValidator.isValidStudent(email) -> getString(R.string.error_invalid_email)
            password.length < 8 -> getString(R.string.error_short_password)
            password != confirm -> getString(R.string.error_password_mismatch)
            else -> null
        }
        if (error != null) {
            Ui.showError(msgBox, error)
            return
        }

        Ui.hideMessage(msgBox)
        setBusy(true)
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener
                val profile = hashMapOf(
                    "uid" to user.uid,
                    "email" to email,
                    "displayName" to name,
                    "department" to department,
                    "verified" to false,                  // only the OTP can change this
                    "createdAt" to FieldValue.serverTimestamp()
                )
                FirebaseFirestore.getInstance().collection("users").document(user.uid).set(profile)
                    .addOnSuccessListener { Ui.openFresh(this, VerifyEmailActivity::class.java) }
                    .addOnFailureListener { e ->
                        setBusy(false)
                        Ui.showError(msgBox, "Your account was created, but the profile could not be saved (${e.message}). Log in to finish.")
                    }
            }
            .addOnFailureListener { e ->
                setBusy(false)
                val message = when (e) {
                    is FirebaseAuthUserCollisionException -> getString(R.string.error_email_taken)
                    is FirebaseAuthWeakPasswordException -> getString(R.string.error_short_password)
                    else -> "Could not create your account: ${e.message}"
                }
                Ui.showError(msgBox, message)
            }
    }

    private fun setBusy(busy: Boolean) {
        btnContinue.isEnabled = !busy
        btnContinue.text = if (busy) "Creating account..." else getString(R.string.continue_label)
    }
}
