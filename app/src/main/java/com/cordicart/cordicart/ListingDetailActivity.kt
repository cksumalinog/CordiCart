package com.cordicart.cordicart

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.DateFormat

/**
 * Screen 6 · Listing details.
 *  - Buyers: Message seller, Save (heart), Share.
 *  - Sellers (own listing): Mark as sold / available, Delete, Share.
 */
class ListingDetailActivity : AppCompatActivity() {

    private var savedRegistration: ListenerRegistration? = null
    private var isSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listing_detail)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val listingId = intent.getStringExtra(EXTRA_LISTING_ID)
        if (listingId == null) {
            Toast.makeText(this, "Listing not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        load(listingId)
    }

    override fun onDestroy() {
        savedRegistration?.remove()
        super.onDestroy()
    }

    /** Re-reads from Firestore so the details are current, not a stale copy. */
    private fun load(listingId: String) {
        FirebaseFirestore.getInstance().collection("listings").document(listingId).get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val item = MarketItem.fromDocument(doc)
                if (item == null) {
                    Toast.makeText(this, "This listing no longer exists.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    bind(item)
                }
            }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                Toast.makeText(this, "Could not load listing: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun bind(item: MarketItem) {
        val info = Categories.info(item.category)
        findViewById<View>(R.id.imageArea).setBackgroundColor(ContextCompat.getColor(this, info.tileColor))
        findViewById<ImageView>(R.id.imgCategory).apply {
            setImageResource(info.icon)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, info.iconColor))
        }

        findViewById<TextView>(R.id.txtCourseTag).text = item.courseCode
        findViewById<TextView>(R.id.txtConditionTag).text = item.condition
        findViewById<TextView>(R.id.txtCategory).text = item.category
        findViewById<TextView>(R.id.txtTitle).text = item.title
        findViewById<TextView>(R.id.txtPrice).text = Ui.priceText(this, item)
        findViewById<TextView>(R.id.txtDescription).text =
            item.description.ifEmpty { "No description provided." }

        findViewById<TextView>(R.id.txtSellerAvatar).text = Ui.initials(item.sellerName)
        findViewById<TextView>(R.id.txtSeller).text = item.sellerName
        findViewById<TextView>(R.id.txtSellerDept).text =
            listOf(item.sellerDept, getString(R.string.verified_student)).filter { it.isNotBlank() }.joinToString(" · ")

        item.createdAt?.let { date ->
            findViewById<TextView>(R.id.txtPosted).text =
                "Posted " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date)
        }

        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        val messageButton = findViewById<View>(R.id.btnMessageSeller)
        val saveButton = findViewById<ImageButton>(R.id.btnSave)
        val saveTop = findViewById<ImageButton>(R.id.btnSaveTop)
        val note = findViewById<TextView>(R.id.txtOwnerNote)
        val id = item.id ?: return

        // Share works for everyone: it opens Android's share menu (an implicit intent).
        findViewById<View>(R.id.btnShare).setOnClickListener { share(item) }

        // Reset, because bind() also runs again after Mark as sold.
        messageButton.visibility = View.VISIBLE
        saveButton.visibility = View.VISIBLE
        note.visibility = View.GONE

        when {
            item.isOwnedBy(myUid) -> {
                // Your own listing: no messaging or saving; the heart becomes a "manage" button.
                messageButton.visibility = View.GONE
                saveButton.visibility = View.GONE
                note.visibility = View.VISIBLE
                note.text = if (item.isAvailable) "This is your listing" else "You marked this as sold"
                saveTop.setImageResource(R.drawable.ic_more)
                saveTop.contentDescription = getString(R.string.manage_listing)
                val manage = View.OnClickListener { showSellerActions(item) }
                saveTop.setOnClickListener(manage)
                note.setOnClickListener(manage)
            }
            else -> {
                if (item.isAvailable) {
                    messageButton.setOnClickListener {
                        ChatActivity.openForListing(this, id, myUid ?: return@setOnClickListener)
                    }
                } else {
                    messageButton.visibility = View.GONE
                    note.visibility = View.VISIBLE
                    note.text = "This item has been sold"
                }
                if (myUid != null) setUpSaveButtons(id, myUid, saveButton, saveTop)
            }
        }
    }

    /** Heart buttons: tap to save or unsave. They stay in sync with the database. */
    private fun setUpSaveButtons(listingId: String, uid: String, vararg buttons: ImageButton) {
        val repo = SavedRepository(uid)
        savedRegistration?.remove()
        savedRegistration = repo.listenIsSaved(listingId) { saved ->
            isSaved = saved
            for (b in buttons) {
                b.setImageResource(if (saved) R.drawable.ic_heart_filled else R.drawable.ic_heart)
                b.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, if (saved) R.color.cc_error else R.color.cc_ink)
                )
                b.contentDescription = getString(if (saved) R.string.unsave_listing else R.string.save_listing)
            }
        }
        val toggle = View.OnClickListener {
            val onError: (String) -> Unit = { msg ->
                Toast.makeText(this, "Could not update saved items: $msg", Toast.LENGTH_LONG).show()
            }
            if (isSaved) {
                repo.unsave(listingId, onSuccess = {
                    Toast.makeText(this, "Removed from Saved", Toast.LENGTH_SHORT).show()
                }, onError = onError)
            } else {
                repo.save(listingId, onSuccess = {
                    Toast.makeText(this, "Saved. Find it in the Saved tab.", Toast.LENGTH_SHORT).show()
                }, onError = onError)
            }
        }
        for (b in buttons) b.setOnClickListener(toggle)
    }

    /** Implicit intent: Android asks which app to share with (Messenger, SMS, Gmail, ...). */
    private fun share(item: MarketItem) {
        val text = buildString {
            append("For sale on CordiCart: ").append(item.title).append('\n')
            append(item.displayPrice).append(" · ").append(item.condition)
            append(" · ").append(item.courseCode).append('\n')
            append("Seller: ").append(item.sellerName)
            if (item.sellerDept.isNotBlank()) append(" (").append(item.sellerDept).append(")")
            append("\n\nOpen CordiCart and search \"").append(item.title).append("\" to message the seller.")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "CordiCart: ${item.title}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_listing)))
    }

    /** Seller menu: Mark as sold / available, or Delete. */
    private fun showSellerActions(item: MarketItem) {
        val id = item.id ?: return
        val toggleLabel = if (item.isAvailable) "Mark as sold" else "Mark as available again"
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(arrayOf(toggleLabel, "Delete listing")) { _, which ->
                when (which) {
                    0 -> MyListingActions.toggleSold(this, item) { load(id) }
                    1 -> MyListingActions.confirmDelete(this, item) { finish() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        const val EXTRA_LISTING_ID = "listing_id"
    }
}
