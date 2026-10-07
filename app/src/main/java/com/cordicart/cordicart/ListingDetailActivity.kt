package com.cordicart.cordicart

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.DateFormat

/** Screen 6 · Listing details. "Message seller" will become the entry point to chat. */
class ListingDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listing_detail)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        val save = View.OnClickListener { Ui.comingSoon(this, "Saved items") }
        findViewById<View>(R.id.btnSave).setOnClickListener(save)
        findViewById<View>(R.id.btnSaveTop).setOnClickListener(save)

        val listingId = intent.getStringExtra(EXTRA_LISTING_ID)
        if (listingId == null) {
            Toast.makeText(this, "Listing not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Re-read from Firestore so the details are current, not a stale copy.
        FirebaseFirestore.getInstance().collection("listings").document(listingId).get()
            .addOnSuccessListener { doc ->
                val item = MarketItem.fromDocument(doc)
                if (item == null) {
                    Toast.makeText(this, "This listing no longer exists.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    bind(item)
                }
            }
            .addOnFailureListener { e ->
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
        val saveButton = findViewById<View>(R.id.btnSave)
        val note = findViewById<TextView>(R.id.txtOwnerNote)

        when {
            item.isOwnedBy(myUid) -> {
                // You can't message yourself about your own item.
                messageButton.visibility = View.GONE
                saveButton.visibility = View.GONE
                note.visibility = View.VISIBLE
                note.text = "This is your listing"
            }
            !item.isAvailable -> {
                messageButton.visibility = View.GONE
                note.visibility = View.VISIBLE
                note.text = "This item has been sold"
            }
            else -> messageButton.setOnClickListener {
                Toast.makeText(
                    this, "Chat with ${item.sellerName} is coming in the next update.", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        const val EXTRA_LISTING_ID = "listing_id"
    }
}
