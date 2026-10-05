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
        listings.addSnapshotListener { snapshot, error ->
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
