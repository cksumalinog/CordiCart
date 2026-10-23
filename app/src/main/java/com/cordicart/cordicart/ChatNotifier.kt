package com.cordicart.cordicart

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date

/**
 * Watches your chats while the app is running:
 *  - counts unread chats for the red badge on the Messages tab
 *  - shows a phone notification when a new message arrives
 *
 * It runs from the moment the dashboard opens until you sign out (or Android closes the app).
 * Notifications while the app is fully closed would need Firebase Cloud Messaging plus a
 * Cloud Function, which requires the paid Blaze plan.
 */
object ChatNotifier {

    private const val CHANNEL_ID = "messages"

    private val repository = ChatRepository()
    private var registration: ListenerRegistration? = null
    private var myUid: String? = null
    private lateinit var appContext: Context

    /** The chat currently on screen. No notification is shown for it. */
    var openChatId: String? = null

    /** Last message time already handled per chat, so each message notifies only once. */
    private val seen = mutableMapOf<String, Date?>()
    private var firstLoad = true

    private val unreadListeners = mutableListOf<(Int) -> Unit>()
    var unreadCount = 0
        private set

    fun start(context: Context, uid: String) {
        if (registration != null && myUid == uid) return
        stop()
        appContext = context.applicationContext
        myUid = uid
        firstLoad = true
        createChannel()

        registration = repository.listenToMyChats(
            uid,
            onChats = { chats -> onChatsChanged(chats) },
            onError = {
                // Firestore closes a listener after an error, so forget it. The next start() makes a new one.
                registration?.remove()
                registration = null
                myUid = null
            }
        )
    }

    /** Call on sign-out so the next person on this phone doesn't get your notifications. */
    fun stop() {
        registration?.remove()
        registration = null
        myUid = null
        seen.clear()
        openChatId = null
        setUnread(0)
        if (::appContext.isInitialized) NotificationManagerCompat.from(appContext).cancelAll()
    }

    /** The badge calls this to stay up to date. It's called right away with the current count. */
    fun addUnreadListener(listener: (Int) -> Unit) {
        unreadListeners.add(listener)
        listener(unreadCount)
    }

    fun removeUnreadListener(listener: (Int) -> Unit) {
        unreadListeners.remove(listener)
    }

    /** Removes a chat's notification, e.g. when you open that chat. */
    fun clearNotification(chatId: String) {
        if (::appContext.isInitialized) NotificationManagerCompat.from(appContext).cancel(chatId.hashCode())
    }

    private fun onChatsChanged(chats: List<Chat>) {
        val uid = myUid ?: return
        setUnread(chats.count { it.isUnreadFor(uid) })

        for (chat in chats) {
            val previous = seen[chat.id]
            val isNewMessage = !seen.containsKey(chat.id) || previous != chat.updatedAt
            seen[chat.id] = chat.updatedAt

            // On the first load, only remember what's there. Old messages shouldn't pop up as notifications.
            if (firstLoad || !isNewMessage) continue
            if (!chat.isUnreadFor(uid) || chat.id == openChatId) continue
            notify(chat, uid)
        }
        firstLoad = false
    }

    private fun setUnread(count: Int) {
        unreadCount = count
        unreadListeners.toList().forEach { it(count) }
    }

    private fun notify(chat: Chat, uid: String) {
        val context = appContext
        // Android 13+ asks the student first. If they said no, just skip the notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // Tapping the notification opens the chat, with the dashboard behind it for Back.
        val feed = Intent(context, FeedActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val open = Intent(context, ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_CHAT_ID, chat.id)
        val pending = PendingIntent.getActivities(
            context, chat.id.hashCode(), arrayOf(feed, open),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.cc_pine))
            .setContentTitle(chat.otherName(uid))
            .setContentText(chat.lastMessage)
            .setSubText(chat.listingTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(chat.lastMessage))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        try {
            // One notification per chat: a newer message replaces the older one.
            NotificationManagerCompat.from(context).notify(chat.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission was turned off in Settings while the app was running.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "New messages from buyers and sellers"
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
