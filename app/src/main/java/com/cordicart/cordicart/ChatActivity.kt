package com.cordicart.cordicart

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.DateFormat

/**
 * Screen 8 · Chat. One conversation between a buyer and a seller about one listing.
 * Opened from "Message seller" (Listing details) or from the Messages list.
 */
class ChatActivity : AppCompatActivity() {

    private val repository = ChatRepository()
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: MessageAdapter
    private var registration: ListenerRegistration? = null

    private lateinit var chatId: String
    private var myUid = ""
    private var chat: Chat? = null
    private var isNewChat = false     // true until the first message creates the chat in Firestore
    private var sending = false

    private lateinit var input: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var emptyHint: TextView
    private lateinit var suggestions: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = FirebaseAuth.getInstance().currentUser
        val id = intent.getStringExtra(EXTRA_CHAT_ID)
        if (user == null || id == null) {
            Toast.makeText(this, "Chat not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        myUid = user.uid
        chatId = id
        setContentView(R.layout.activity_chat)

        input = findViewById(R.id.inputMessage)
        btnSend = findViewById(R.id.btnSend)
        emptyHint = findViewById(R.id.txtEmptyHint)
        suggestions = findViewById(R.id.suggestionRow)

        adapter = MessageAdapter(this, messages, myUid)
        findViewById<ListView>(R.id.messageList).adapter = adapter

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        btnSend.setOnClickListener { send() }
        input.doOnTextChanged { text, _, _, _ -> updateSendButton(text) }
        updateSendButton(null)

        // Quick questions a buyer can tap to fill in the message box.
        for (chipId in listOf(R.id.suggestAvailable, R.id.suggestMeet, R.id.suggestPrice)) {
            findViewById<TextView>(chipId).setOnClickListener { chip ->
                input.setText((chip as TextView).text)
                input.setSelection(input.text.length)
                input.requestFocus()
            }
        }

        loadChat(intent.getStringExtra(EXTRA_LISTING_ID))
    }

    override fun onStart() {
        super.onStart()
        ChatNotifier.openChatId = chatId        // no notifications for the chat you're looking at
        ChatNotifier.clearNotification(chatId)
        if (chat != null && !isNewChat) startListening()
    }

    override fun onStop() {
        if (ChatNotifier.openChatId == chatId) ChatNotifier.openChatId = null
        stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    /** Opens an existing chat, or prepares a new one if this buyer hasn't messaged about the item yet. */
    private fun loadChat(listingId: String?) {
        repository.getChat(
            chatId,
            onResult = { existing ->
                when {
                    existing != null -> showChat(existing, isNew = false)
                    listingId != null -> prepareNewChat(listingId)
                    else -> fail("This chat no longer exists.")
                }
            },
            onError = { message -> fail("Could not open chat: $message") }
        )
    }

    private fun prepareNewChat(listingId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("listings").document(listingId).get()
            .addOnSuccessListener { doc ->
                val item = MarketItem.fromDocument(doc)
                if (item == null) { fail("This listing no longer exists."); return@addOnSuccessListener }
                if (item.isOwnedBy(myUid)) { fail("You can't message yourself about your own item."); return@addOnSuccessListener }

                // Your own name, so the seller knows who is messaging.
                db.collection("users").document(myUid).get()
                    .addOnSuccessListener { me ->
                        val myName = me.getString("displayName")?.takeIf { it.isNotBlank() } ?: "UC Student"
                        showChat(
                            Chat(
                                id = chatId,
                                listingId = listingId,
                                listingTitle = item.title,
                                listingCategory = item.category,
                                listingPrice = item.displayPrice,
                                buyerId = myUid,
                                buyerName = myName,
                                sellerId = item.sellerId,
                                sellerName = item.sellerName,
                                lastMessage = "",
                                lastSenderId = "",
                                updatedAt = null
                            ),
                            isNew = true
                        )
                    }
                    .addOnFailureListener { e -> fail("Could not open chat: ${e.message}") }
            }
            .addOnFailureListener { e -> fail("Could not open chat: ${e.message}") }
    }

    private fun showChat(loaded: Chat, isNew: Boolean) {
        if (isFinishing || isDestroyed) return
        chat = loaded
        isNewChat = isNew

        // Header: who you're talking to
        val otherName = loaded.otherName(myUid)
        findViewById<TextView>(R.id.txtAvatar).text = Ui.initials(otherName)
        findViewById<TextView>(R.id.txtName).text = otherName
        findViewById<TextView>(R.id.txtRole).text =
            if (loaded.isSeller(myUid)) "Buyer · Verified UC student" else "Seller · Verified UC student"

        // Listing strip: what the chat is about. Tap it to see the item.
        val info = Categories.info(loaded.listingCategory)
        findViewById<View>(R.id.listingTile).backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, info.tileColor))
        findViewById<ImageView>(R.id.listingIcon).apply {
            setImageResource(info.icon)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, info.iconColor))
        }
        findViewById<TextView>(R.id.txtListingTitle).text = loaded.listingTitle
        findViewById<TextView>(R.id.txtListingPrice).text = loaded.listingPrice
        findViewById<View>(R.id.listingStrip).setOnClickListener {
            val intent = Intent(this, ListingDetailActivity::class.java)
            intent.putExtra(ListingDetailActivity.EXTRA_LISTING_ID, loaded.listingId)
            startActivity(intent)
        }

        emptyHint.text = if (loaded.isSeller(myUid))
            "No messages yet."
        else
            "Say hi to ${otherName.substringBefore(" ")}! Ask if the item is still available and where to meet on campus."

        if (isNew) showMessages(emptyList()) else startListening()
    }

    private fun startListening() {
        // Only listen while the screen is visible. onStart starts it again when you come back.
        if (registration != null || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        registration = repository.listenToMessages(
            chatId,
            onMessages = { list ->
                showMessages(list)
                // You're looking at the chat, so everything in it is now read.
                if (list.lastOrNull()?.senderId.let { it != null && it != myUid }) {
                    repository.markRead(chatId, myUid)
                }
            },
            onError = { message ->
                Toast.makeText(this, "Could not load messages: $message", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun stopListening() {
        registration?.remove()
        registration = null
    }

    private fun showMessages(newList: List<ChatMessage>) {
        val list = newList.toList()   // copy, because newList may be built from `messages` itself
        messages.clear()
        messages.addAll(list)
        adapter.notifyDataSetChanged()

        val empty = list.isEmpty()
        emptyHint.visibility = if (empty) View.VISIBLE else View.GONE
        // Quick questions only make sense for the buyer, before the chat starts.
        suggestions.visibility = if (empty && chat?.isSeller(myUid) == false) View.VISIBLE else View.GONE
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || sending) return
        if (text.length > ChatRepository.MAX_MESSAGE_LENGTH) {
            Toast.makeText(this, "Message is too long (max ${ChatRepository.MAX_MESSAGE_LENGTH} characters).", Toast.LENGTH_SHORT).show()
            return
        }
        // Clear the box right away so chatting feels instant. If sending fails, the text comes back.
        input.setText("")
        sendText(text, retried = false)
    }

    private fun sendText(text: String, retried: Boolean) {
        val current = chat ?: return
        val firstMessage = isNewChat
        if (firstMessage) {
            // The first message creates the chat, so it has to finish before the next one.
            // Show it right away as "Sending..." until the server confirms it.
            sending = true
            showMessages(messages + ChatMessage(id = "pending", senderId = myUid, text = text, createdAt = null))
        }
        repository.sendMessage(
            current, firstMessage, myUid, text,
            onSuccess = {
                if (firstMessage) {
                    isNewChat = false
                    sending = false
                    startListening()
                }
            },
            onError = { message ->
                if (firstMessage && !retried) {
                    // Maybe the chat was already started (for example on your other phone).
                    // Load it and send again as a normal message.
                    repository.getChat(
                        chatId,
                        onResult = { existing ->
                            if (existing != null) {
                                chat = existing
                                isNewChat = false
                                sending = false
                                startListening()
                                sendText(text, retried = true)
                            } else {
                                sendFailed(text, message)
                            }
                        },
                        onError = { sendFailed(text, message) }
                    )
                } else {
                    sendFailed(text, message)
                }
            }
        )
    }

    private fun sendFailed(text: String, reason: String) {
        sending = false
        if (isNewChat) showMessages(messages.filter { it.id != "pending" })
        if (input.text.isEmpty()) input.setText(text)
        Toast.makeText(this, "Message not sent: $reason", Toast.LENGTH_LONG).show()
    }

    private fun updateSendButton(text: CharSequence?) {
        val enabled = !text.isNullOrBlank()
        btnSend.isEnabled = enabled
        btnSend.alpha = if (enabled) 1f else 0.4f
    }

    private fun fail(message: String) {
        if (isFinishing) return
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    /** Your messages on the right in green, the other person's on the left in white. */
    private class MessageAdapter(
        context: Context,
        items: MutableList<ChatMessage>,
        private val myUid: String
    ) : ArrayAdapter<ChatMessage>(context, 0, items) {

        private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(context).inflate(R.layout.item_message, parent, false)
            val message = getItem(position) ?: return view
            val mine = message.senderId == myUid

            (view as LinearLayout).gravity = if (mine) Gravity.END else Gravity.START
            view.findViewById<TextView>(R.id.txtBubble).apply {
                text = message.text
                setBackgroundResource(if (mine) R.drawable.bg_bubble_out else R.drawable.bg_bubble_in)
                setTextColor(ContextCompat.getColor(context, if (mine) R.color.white else R.color.cc_ink))
            }
            view.findViewById<TextView>(R.id.txtTime).text =
                message.createdAt?.let { timeFormat.format(it) } ?: "Sending..."
            return view
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_LISTING_ID = "listing_id"   // only when a buyer starts a new chat

        /** Opens the chat between you (the buyer) and the seller of this item. */
        fun openForListing(context: Context, listingId: String, buyerId: String) {
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra(EXTRA_CHAT_ID, Chat.idFor(listingId, buyerId))
            intent.putExtra(EXTRA_LISTING_ID, listingId)
            context.startActivity(intent)
        }
    }
}
