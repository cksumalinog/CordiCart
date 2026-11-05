package com.cordicart.cordicart

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date

/** All Firestore access for listings goes through here. */
class MarketRepository {

    private val listings by lazy { FirebaseFirestore.getInstance().collection("listings") }

    /**
     * Live updates: onListings runs now and again every time any student posts,
     * so new items appear on every phone without refreshing.
     * Call remove() on the returned registration when the screen stops.
     */
    fun listenToAvailableListings(
        onListings: (List<MarketItem>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration =
        // Ask Firestore for available items only, so sold items are never downloaded.
        // A single "equals" filter needs no extra index.
        listings.whereEqualTo("status", MarketItem.STATUS_AVAILABLE)
            .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.message ?: "Unknown error")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            val items = snapshot.documents
                .mapNotNull { MarketItem.fromDocument(it) }   // skip malformed documents
                .filter { it.isAvailable }
            onListings(sortNewestFirst(items))
        }

    /** Live list of YOUR listings (available and sold), newest first. For the My listings screen. */
    fun listenToMyListings(
        uid: String,
        onListings: (List<MarketItem>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration =
        listings.whereEqualTo("sellerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Unknown error")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                onListings(sortNewestFirst(snapshot.documents.mapNotNull { MarketItem.fromDocument(it) }))
            }

    /** "available" or "sold". The database rules only let the seller change this one field. */
    fun setStatus(listingId: String, status: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        listings.document(listingId).update("status", status)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Unknown error") }
    }

    fun deleteListing(listingId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        listings.document(listingId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Unknown error") }
    }

    fun addListing(item: MarketItem, onSuccess: () -> Unit, onError: (String) -> Unit) {
        listings.add(item.toMap())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Unknown error") }
    }

    companion object {
        /** Applies the category chip AND the search text together. */
        fun filter(all: List<MarketItem>, category: String, query: String): List<MarketItem> =
            all.filter { it.matches(category, query) }

        // Sorting here instead of in the query means no Firestore index is needed.
        // A post without a timestamp yet was just made, so it counts as newest.
        fun sortNewestFirst(items: List<MarketItem>): List<MarketItem> =
            items.sortedByDescending { it.createdAt ?: Date(Long.MAX_VALUE) }
    }
}
