package com.cordicart.cordicart

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

/**
 * Screen 9 · My listings. Everything you posted, available and sold.
 * Tap a card to open it; long-press for Mark as sold / available and Delete.
 */
class MyListingsActivity : AppCompatActivity() {

    private val repository = MarketRepository()
    private val items = mutableListOf<MarketItem>()
    private lateinit var adapter: MarketItemAdapter
    private lateinit var grid: GridView
    private lateinit var emptyView: View
    private lateinit var summary: TextView
    private var registration: ListenerRegistration? = null
    private var myUid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { Ui.openFresh(this, MainActivity::class.java); return }
        myUid = user.uid
        setContentView(R.layout.activity_listing_grid)

        findViewById<TextView>(R.id.txtTitle).setText(R.string.my_listings)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        summary = findViewById(R.id.txtSummary)
        grid = findViewById(R.id.grid)
        emptyView = findViewById(R.id.emptyView)

        // Empty state
        findViewById<ImageView>(R.id.emptyIcon).setImageResource(R.drawable.ic_tag)
        findViewById<TextView>(R.id.emptyTitle).setText(R.string.my_listings_empty_title)
        findViewById<TextView>(R.id.emptyBody).setText(R.string.my_listings_empty_body)
        findViewById<TextView>(R.id.btnEmptyAction).apply {
            setText(R.string.sell_an_item)
            setOnClickListener { startActivity(Intent(this@MyListingsActivity, PostItemActivity::class.java)) }
        }

        adapter = MarketItemAdapter(this, items)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            val item = items.getOrNull(position) ?: return@setOnItemClickListener
            startActivity(
                Intent(this, ListingDetailActivity::class.java)
                    .putExtra(ListingDetailActivity.EXTRA_LISTING_ID, item.id)
            )
        }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            val item = items.getOrNull(position) ?: return@setOnItemLongClickListener false
            showActions(item)
            true
        }
    }

    override fun onStart() {
        super.onStart()
        if (myUid.isEmpty()) return
        registration = repository.listenToMyListings(
            myUid,
            onListings = { list -> show(list) },
            onError = { msg -> Toast.makeText(this, "Could not load your listings: $msg", Toast.LENGTH_LONG).show() }
        )
    }

    override fun onStop() {
        registration?.remove()
        registration = null
        super.onStop()
    }

    private fun show(list: List<MarketItem>) {
        items.clear()
        items.addAll(list)
        adapter.notifyDataSetChanged()

        val empty = list.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        grid.visibility = if (empty) View.GONE else View.VISIBLE

        val available = list.count { it.isAvailable }
        val sold = list.size - available
        summary.visibility = if (empty) View.GONE else View.VISIBLE
        summary.text = getString(R.string.my_listings_summary, available, sold)
    }

    private fun showActions(item: MarketItem) {
        val toggleLabel = if (item.isAvailable) "Mark as sold" else "Mark as available again"
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(arrayOf(toggleLabel, "Delete listing")) { _, which ->
                // The live listener refreshes the grid by itself after each change.
                when (which) {
                    0 -> MyListingActions.toggleSold(this, item) { }
                    1 -> MyListingActions.confirmDelete(this, item) { }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
