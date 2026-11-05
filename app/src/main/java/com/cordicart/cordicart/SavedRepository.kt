package com.cordicart.cordicart

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Saved items (the heart button). Stored privately per student:
 *   users/{uid}/saved/{listingId}  ->  { listingId, savedAt }
 */
class SavedRepository(private val uid: String) {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val saved by lazy { db.collection("users").document(uid).collection("saved") }
    private var generation = 0   // ignores slow results from an older update

    /** Live: is this one listing saved? Used to fill or empty the heart. */
    fun listenIsSaved(listingId: String, onChange: (Boolean) -> Unit): ListenerRegistration =
        saved.document(listingId).addSnapshotListener { snap, _ -> onChange(snap?.exists() == true) }

    fun save(listingId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        saved.document(listingId)
            .set(mapOf("listingId" to listingId, "savedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Unknown error") }
    }

    fun unsave(listingId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        saved.document(listingId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Unknown error") }
    }

    /**
     * Live list of saved listings, newest saved first.
     * Listings that were deleted are skipped; sold ones are kept so you can see what happened.
     */
    fun listenSavedListings(
        onItems: (List<MarketItem>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration =
        saved.addSnapshotListener { snapshot, error ->
            if (error != null) { onError(error.message ?: "Unknown error"); return@addSnapshotListener }
            val ids = snapshot?.documents
                ?.sortedByDescending { it.getTimestamp("savedAt")?.seconds ?: Long.MAX_VALUE }
                ?.map { it.id }
                .orEmpty()
            val thisGeneration = ++generation
            if (ids.isEmpty()) { onItems(emptyList()); return@addSnapshotListener }

            // Load each saved listing. A student saves a handful of items, so one read each is fine.
            val results = arrayOfNulls<MarketItem>(ids.size)
            var pending = ids.size
            ids.forEachIndexed { index, id ->
                db.collection("listings").document(id).get()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val doc = task.result
                            results[index] = doc?.let { MarketItem.fromDocument(it) }
                            // The seller deleted this listing: tidy up the saved entry too.
                            if (doc != null && !doc.exists()) saved.document(id).delete()
                        }
                        pending--
                        if (pending == 0 && thisGeneration == generation) onItems(results.filterNotNull())
                    }
            }
        }
}
