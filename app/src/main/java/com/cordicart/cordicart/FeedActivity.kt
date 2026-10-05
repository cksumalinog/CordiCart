package com.cordicart.cordicart

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FeedActivity : AppCompatActivity() {

    private val repository = MarketRepository()
    private val allItems = mutableListOf<MarketItem>()       // everything from Firestore
    private val displayList = mutableListOf<MarketItem>()    // what the grid shows
    private lateinit var adapter: MarketItemAdapter
    private var listingsRegistration: ListenerRegistration? = null

    // The current filter state. Both are applied together every time.
    private var selectedCategory = MarketItem.CATEGORY_ALL
    private var searchQuery = ""

    private lateinit var chips: List<Pair<TextView, String>>

    private var user: FirebaseUser? = null
    private var myName = "UC Student"
    private var myDept = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            goToLogin()
            return
        }
        setContentView(R.layout.activity_feed)

        // Grid of listing cards
        val itemGridView = findViewById<GridView>(R.id.itemGridView)
        adapter = MarketItemAdapter(this, displayList)
        itemGridView.adapter = adapter
        itemGridView.emptyView = findViewById(R.id.txtEmpty)
        itemGridView.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, ListingDetailActivity::class.java)
            intent.putExtra(ListingDetailActivity.EXTRA_LISTING_ID, displayList[position].id)
            startActivity(intent)
        }

        // Category chips
        chips = listOf(
            findViewById<TextView>(R.id.chipAll) to MarketItem.CATEGORY_ALL,
            findViewById<TextView>(R.id.chipTextbooks) to "Textbooks",
            findViewById<TextView>(R.id.chipUniforms) to "Uniforms",
            findViewById<TextView>(R.id.chipDrafting) to "Drafting & Lab Gear"
        )
        for ((chip, category) in chips) {
            chip.setOnClickListener {
                selectedCategory = category
                updateChipStyles()
                applyFilters()
            }
        }
        updateChipStyles()

        // Search (combined with the selected category)
        findViewById<EditText>(R.id.searchEditText).doOnTextChanged { text, _, _, _ ->
            searchQuery = text?.toString() ?: ""
            applyFilters()
        }

        findViewById<TextView>(R.id.fabPostItem).setOnClickListener { showCreateListingDialog() }
        findViewById<TextView>(R.id.btnProfileHeader).setOnClickListener { showProfileDialog() }
        findViewById<TextView>(R.id.btnMessages).setOnClickListener {
            Toast.makeText(this, "In-app chat is coming in the next feature.", Toast.LENGTH_SHORT).show()
        }

        loadMyProfile()
    }

    override fun onStart() {
        super.onStart()
        if (user == null) return
        listingsRegistration = repository.listenToAvailableListings(
            onListings = { items ->
                allItems.clear()
                allItems.addAll(items)
                applyFilters()
            },
            onError = { message ->
                Toast.makeText(this, "Could not load listings: $message", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onStop() {
        // Stop listening while the screen is hidden to save data and battery.
        listingsRegistration?.remove()
        listingsRegistration = null
        super.onStop()
    }

    private fun applyFilters() {
        displayList.clear()
        displayList.addAll(MarketRepository.filter(allItems, selectedCategory, searchQuery))
        adapter.notifyDataSetChanged()
    }

    private fun updateChipStyles() {
        for ((chip, category) in chips) {
            val selected = category == selectedCategory
            chip.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            chip.setTextColor(if (selected) Color.BLACK else Color.parseColor("#888888"))
        }
    }

    /** Loads the seller name/department used when posting, and blocks unverified users. */
    private fun loadMyProfile() {
        val uid = user?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists() || doc.getBoolean("verified") != true) {
                    goToLogin()   // not verified: back to the verification screen
                    return@addOnSuccessListener
                }
                doc.getString("displayName")?.takeIf { it.isNotBlank() }?.let { myName = it }
                doc.getString("department")?.let { myDept = it }
            }
    }

    // ------------------------------------------------------------------ Post item

    private fun showCreateListingDialog() {
        val me = user ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_listing, null)

        val editTitle = dialogView.findViewById<EditText>(R.id.editTitle)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val editTag = dialogView.findViewById<EditText>(R.id.editTag)
        val spinnerCondition = dialogView.findViewById<Spinner>(R.id.spinnerCondition)
        val editPrice = dialogView.findViewById<EditText>(R.id.editPrice)
        val checkTrade = dialogView.findViewById<CheckBox>(R.id.checkTrade)
        val editDescription = dialogView.findViewById<EditText>(R.id.editDescription)

        spinnerCategory.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Textbooks", "Uniforms", "Drafting & Lab Gear")
        )
        spinnerCondition.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Like New", "Gently Used", "Used")
        )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Publish Listing", null)   // click handled below
            .setNegativeButton("Cancel", null)
            .create()

        // Overriding the button AFTER the dialog is shown keeps it open when input is
        // invalid, so the user doesn't lose what they typed.
        dialog.setOnShowListener {
            val publish = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            publish.setOnClickListener {
                val title = editTitle.text.toString().trim()
                val priceText = editPrice.text.toString().trim()
                val openToTrade = checkTrade.isChecked

                if (title.isEmpty()) {
                    editTitle.error = "Title cannot be empty"
                    return@setOnClickListener
                }

                var price: Double? = null
                if (priceText.isNotEmpty()) {
                    price = priceText.toDoubleOrNull()
                    when {
                        price == null -> { editPrice.error = "Enter a valid amount, e.g. 250"; return@setOnClickListener }
                        price <= 0 -> { editPrice.error = "Price must be more than ₱0"; return@setOnClickListener }
                        price > MAX_PRICE -> { editPrice.error = "Price is too high"; return@setOnClickListener }
                    }
                }
                if (price == null && !openToTrade) {
                    editPrice.error = "Enter a price or tick \"Open to trade\""
                    return@setOnClickListener
                }

                val item = MarketItem(
                    id = null,
                    title = title,
                    category = spinnerCategory.selectedItem.toString(),
                    courseCode = MarketItem.normalizeCourseCode(editTag.text.toString()),
                    condition = spinnerCondition.selectedItem.toString(),
                    price = price,
                    openToTrade = openToTrade,
                    description = editDescription.text.toString().trim(),
                    sellerId = me.uid,
                    sellerName = myName,
                    sellerDept = myDept
                )

                publish.isEnabled = false   // prevents double-posting from double taps
                repository.addListing(
                    item,
                    onSuccess = {
                        Toast.makeText(this, "Item published to CordiCart!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        // No manual refresh needed: the live listener updates the grid.
                    },
                    onError = { message ->
                        publish.isEnabled = true
                        Toast.makeText(this, "Could not publish: $message", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
        dialog.show()
    }

    // ------------------------------------------------------------------ Profile / sign out

    private fun showProfileDialog() {
        AlertDialog.Builder(this)
            .setTitle(myName)
            .setMessage("Department: ${myDept.ifEmpty { "-" }}\nEmail: ${user?.email}")
            .setPositiveButton("Sign out") { _, _ ->
                FirebaseAuth.getInstance().signOut()
                goToLogin()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun goToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val MAX_PRICE = 100_000.0
    }
}
