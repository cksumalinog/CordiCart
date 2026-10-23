package com.cordicart.cordicart

import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/**
 * One conversation between a buyer and a seller about one listing.
 * Stored in Firestore as chats/{listingId}_{buyerId}, so each buyer has exactly one chat per item.
 */
data class Chat(
    val id: String,
    val listingId: String,
    val listingTitle: String,
    val listingCategory: String,
    val listingPrice: String,      // plain text, e.g. "₱250 or trade"
    val buyerId: String,
    val buyerName: String,
    val sellerId: String,
    val sellerName: String,
    val lastMessage: String,
    val lastSenderId: String,
    val updatedAt: Date?
) {

    fun isSeller(uid: String?): Boolean = uid != null && uid == sellerId

    /** The name of the person you're talking to. */
    fun otherName(myUid: String?): String = if (isSeller(myUid)) buyerName else sellerName

    /** Shown in the Messages list: "You: Is this still available?" */
    fun preview(myUid: String?): String = when {
        lastMessage.isBlank() -> "No messages yet"
        lastSenderId == myUid -> "You: $lastMessage"
        else -> lastMessage
    }

    companion object {
        fun idFor(listingId: String, buyerId: String): String = "${listingId}_$buyerId"

        /** Builds a chat from Firestore. Returns null for malformed documents instead of crashing. */
        fun fromDocument(doc: DocumentSnapshot): Chat? {
            if (!doc.exists()) return null
            val listingId = doc.getString("listingId") ?: return null
            val buyerId = doc.getString("buyerId") ?: return null
            val sellerId = doc.getString("sellerId") ?: return null
            return Chat(
                id = doc.id,
                listingId = listingId,
                listingTitle = doc.getString("listingTitle").orEmpty().ifBlank { "Listing" },
                listingCategory = Categories.normalize(doc.getString("listingCategory")),
                listingPrice = doc.getString("listingPrice").orEmpty(),
                buyerId = buyerId,
                buyerName = doc.getString("buyerName").orEmpty().ifBlank { "UC Student" },
                sellerId = sellerId,
                sellerName = doc.getString("sellerName").orEmpty().ifBlank { "UC Student" },
                lastMessage = doc.getString("lastMessage").orEmpty(),
                lastSenderId = doc.getString("lastSenderId").orEmpty(),
                updatedAt = doc.getTimestamp(
                    "updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )?.toDate()
            )
        }
    }
}

/** One message. Stored as chats/{chatId}/messages/{messageId}. */
data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAt: Date?
) {
    companion object {
        fun fromDocument(doc: DocumentSnapshot): ChatMessage? {
            val text = doc.getString("text") ?: return null
            return ChatMessage(
                id = doc.id,
                senderId = doc.getString("senderId").orEmpty(),
                text = text,
                // ESTIMATE shows your own message right away, before the server confirms the time.
                createdAt = doc.getTimestamp(
                    "createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )?.toDate()
            )
        }
    }
}
