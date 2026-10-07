package com.cordicart.cordicart

import android.app.Activity
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Decides where a signed-in student belongs, so no step can be skipped:
 *   verified profile      -> dashboard (FeedActivity)
 *   unverified profile    -> email verification (VerifyEmailActivity)
 *   no profile at all     -> creates a basic one, then verification
 */
object AuthRouter {

    fun route(activity: Activity, user: FirebaseUser, onError: (String) -> Unit) {
        val ref = FirebaseFirestore.getInstance().collection("users").document(user.uid)

        ref.get()
            .addOnSuccessListener { doc ->
                if (activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
                when {
                    doc.exists() && doc.getBoolean("verified") == true ->
                        Ui.openFresh(activity, FeedActivity::class.java)

                    doc.exists() ->
                        Ui.openFresh(activity, VerifyEmailActivity::class.java)

                    else -> {
                        // The account exists but the app closed before the profile was saved.
                        val email = user.email.orEmpty()
                        ref.set(
                            hashMapOf(
                                "uid" to user.uid,
                                "email" to email,
                                "displayName" to email.substringBefore("@"),
                                "department" to "",
                                "verified" to false,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                            .addOnSuccessListener { Ui.openFresh(activity, VerifyEmailActivity::class.java) }
                            .addOnFailureListener { e -> onError("Could not load your account: ${e.message}") }
                    }
                }
            }
            .addOnFailureListener { e -> onError("Could not load your account: ${e.message}") }
    }
}
