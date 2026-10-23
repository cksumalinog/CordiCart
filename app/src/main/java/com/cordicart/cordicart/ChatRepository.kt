package com.cordicart.cordicart

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date

/** All Firestore access for chats goes through here. */
class ChatRepository {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val chats by lazy { db.collection("chats") }

    /**
     * Live list of every chat you're in (as buyer or seller), newest first.
     * Sorting here instead of in the query means no Firestore index is needed.
     */
    fun listenToMyChats(
        uid: String,
        onChats: (List<Chat>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration =
        chats.whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Unknown error")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val list = snapshot.documents
                    .mapNotNull { Chat.fromDocument(it) }
                    .sortedByDescending { it.updatedAt ?: Date(Long.MAX_VALUE) }
                onChats(list)
            }

    /** Live messages in one chat, oldest first (newest at the bottom, like any chat app). */
    fun listenToMessages(
        chatId: String,
        onMessages: (List<ChatMessage>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration =
        chats.document(chatId).collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Unknown error")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val list = snapshot.documents
                    .mapNotNull { ChatMessage.fromDocument(it) }
                    .sortedBy { it.createdAt ?: Date(Long.MAX_VALUE) }
                onMessages(list)
            }

    /** Loads a chat. onResult gets null if the chat hasn't been started yet. */
    fun getChat(chatId: String, onResult: (Chat?) -> Unit, onError: (String) -> Unit) {
        chats.document(chatId).get()
            .addOnSuccessListener { onResult(Chat.fromDocument(it)) }
            .addOnFailureListener { e -> onError(e.message ?: "Unknown error") }
    }

    /**
     * Sends a message. The chat itself is only created with the first message,
     * so tapping "Message seller" and leaving doesn't create an empty chat.
     * Both writes happen together in one batch: either both save or neither does.
     */
    fun sendMessage(
        chat: Chat,
        isNewChat: Boolean,
        senderId: String,
        text: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val chatRef = chats.document(chat.id)
        val messageRef = chatRef.collection("messages").document()
        val batch = db.batch()

        batch.set(
            messageRef,
            mapOf(
                "senderId" to senderId,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        val summary = mapOf(
            "lastMessage" to text,
            "lastSenderId" to senderId,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isNewChat) {
            batch.set(
                chatRef,
                mapOf(
                    "listingId" to chat.listingId,
                    "listingTitle" to chat.listingTitle,
                    "listingCategory" to chat.listingCategory,
                    "listingPrice" to chat.listingPrice,
                    "buyerId" to chat.buyerId,
                    "buyerName" to chat.buyerName,
                    "sellerId" to chat.sellerId,
                    "sellerName" to chat.sellerName,
                    "participants" to listOf(chat.buyerId, chat.sellerId),
                    "lastRead" to mapOf(senderId to FieldValue.serverTimestamp()),
                    "createdAt" to FieldValue.serverTimestamp()
                ) + summary
            )
        } else {
            // Sending also counts as reading, so your own chat never shows as unread.
            batch.update(chatRef, summary + ("lastRead.$senderId" to FieldValue.serverTimestamp()))
        }

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Unknown error") }
    }

    /** Marks the chat as read by you, which clears its badge, dot and notification. */
    fun markRead(chatId: String, uid: String) {
        chats.document(chatId).update("lastRead.$uid", FieldValue.serverTimestamp())
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 1000
    }
}
