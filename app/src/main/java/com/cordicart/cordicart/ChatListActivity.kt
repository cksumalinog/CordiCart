package com.cordicart.cordicart

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

/** Screen 7 · Messages. Every chat you're in, as a buyer or a seller, newest first. */
class ChatListActivity : AppCompatActivity() {

    private val repository = ChatRepository()
    private val chats = mutableListOf<Chat>()
    private lateinit var adapter: ChatAdapter
    private lateinit var list: ListView
    private lateinit var emptyView: View
    private var registration: ListenerRegistration? = null
    private var myUid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Ui.openFresh(this, MainActivity::class.java)
            return
        }
        myUid = user.uid
        setContentView(R.layout.activity_chat_list)

        list = findViewById(R.id.chatList)
        emptyView = findViewById(R.id.emptyView)
        adapter = ChatAdapter(this, chats, myUid)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chats[position].id)
            startActivity(intent)
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnBrowse).setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        if (myUid.isEmpty()) return
        registration = repository.listenToMyChats(
            myUid,
            onChats = { result ->
                chats.clear()
                chats.addAll(result)
                adapter.notifyDataSetChanged()
                val empty = result.isEmpty()
                emptyView.visibility = if (empty) View.VISIBLE else View.GONE
                list.visibility = if (empty) View.GONE else View.VISIBLE
            },
            onError = { message ->
                Toast.makeText(this, "Could not load messages: $message", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onStop() {
        registration?.remove()
        registration = null
        super.onStop()
    }

    private class ChatAdapter(
        context: Context,
        items: MutableList<Chat>,
        private val myUid: String
    ) : ArrayAdapter<Chat>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(context).inflate(R.layout.item_chat, parent, false)
            val chat = getItem(position) ?: return view
            val otherName = chat.otherName(myUid)

            view.findViewById<TextView>(R.id.txtAvatar).text = Ui.initials(otherName)
            view.findViewById<TextView>(R.id.txtName).text = otherName
            view.findViewById<TextView>(R.id.txtRole).text =
                if (chat.isSeller(myUid)) "Buying from you" else "Selling"
            view.findViewById<TextView>(R.id.txtListing).text = chat.listingTitle
            val unread = chat.isUnreadFor(myUid)
            view.findViewById<TextView>(R.id.txtPreview).apply {
                text = chat.preview(myUid)
                // Unread chats: dark bold preview and an amber dot.
                setTextColor(ContextCompat.getColor(context, if (unread) R.color.cc_ink else R.color.cc_muted))
                // Keep the app's font; only switch between regular and bold.
                // The original font is remembered in the view's tag, because rows get reused.
                val baseFont = (tag as? Typeface) ?: typeface.also { tag = it }
                typeface = Typeface.create(baseFont, if (unread) Typeface.BOLD else Typeface.NORMAL)
            }
            view.findViewById<View>(R.id.unreadDot).visibility = if (unread) View.VISIBLE else View.GONE
            view.findViewById<TextView>(R.id.txtTime).text = chat.updatedAt?.let {
                DateUtils.getRelativeTimeSpanString(
                    it.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
                )
            } ?: ""
            view.contentDescription =
                (if (unread) "Unread. " else "") + "Chat with $otherName about ${chat.listingTitle}"
            return view
        }
    }
}