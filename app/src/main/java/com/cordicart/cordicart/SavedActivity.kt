package com.cordicart.cordicart

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

/** Screen 10 · Saved. Listings you tapped the heart on. Only you can see this list. */
class SavedActivity : AppCompatActivity() {

    private val items = mutableListOf<MarketItem>()
    private lateinit var adapter: MarketItemAdapter
    private lateinit var grid: GridView
    private lateinit var emptyView: View
    private lateinit var summary: TextView
    private var repository: SavedRepository? = null
    private var registration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { Ui.openFresh(this, MainActivity::class.java); return }
        repository = SavedRepository(user.uid)
        setContentView(R.layout.activity_listing_grid)

        findViewById<TextView>(R.id.txtTitle).setText(R.string.saved_title)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        summary = findViewById(R.id.txtSummary)
        grid = findViewById(R.id.grid)
        emptyView = findViewById(R.id.emptyView)

        findViewById<ImageView>(R.id.emptyIcon).setImageResource(R.drawable.ic_heart)
        findViewById<TextView>(R.id.emptyTitle).setText(R.string.saved_empty_title)
        findViewById<TextView>(R.id.emptyBody).setText(R.string.saved_empty_body)
        findViewById<TextView>(R.id.btnEmptyAction).apply {
            setText(R.string.browse_market)
            setOnClickListener { finish() }
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
    }

    override fun onStart() {
        super.onStart()
        registration = repository?.listenSavedListings(
            onItems = { list -> show(list) },
            onError = { msg -> Toast.makeText(this, "Could not load saved items: $msg", Toast.LENGTH_LONG).show() }
        )
    }

    override fun onStop() {
        registration?.remove()
        registration = null
        super.onStop()
    }

    private fun show(list: List<MarketItem>) {
        if (isFinishing || isDestroyed) return
        items.clear()
        items.addAll(list)
        adapter.notifyDataSetChanged()
        val empty = list.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        grid.visibility = if (empty) View.GONE else View.VISIBLE
        summary.visibility = if (empty) View.GONE else View.VISIBLE
        summary.text = resources.getQuantityString(R.plurals.saved_summary, list.size, list.size)
    }
}
