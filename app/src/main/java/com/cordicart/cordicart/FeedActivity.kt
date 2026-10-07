package com.cordicart.cordicart

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

/** Screen 4 · Marketplace dashboard, the app's main screen. */
class FeedActivity : AppCompatActivity() {

    private val repository = MarketRepository()
    private val allItems = mutableListOf<MarketItem>()       // everything from Firestore
    private val displayList = mutableListOf<MarketItem>()    // what the grid shows
    private lateinit var adapter: MarketItemAdapter
    private lateinit var grid: ExpandedGridView
    private lateinit var emptyView: View
    private lateinit var sectionTitle: TextView
    private var listingsRegistration: ListenerRegistration? = null
    private var loaded = false

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
            Ui.openFresh(this, MainActivity::class.java)
            return
        }
        setContentView(R.layout.activity_feed)

        // Greeting changes with the time of day
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        findViewById<TextView>(R.id.txtGreeting).text = when {
            hour < 12 -> "Good morning,"
            hour < 18 -> "Good afternoon,"
            else -> "Good evening,"
        }

        // Grid of listing cards
        grid = findViewById(R.id.itemGridView)
        emptyView = findViewById(R.id.emptyView)
        sectionTitle = findViewById(R.id.txtSectionTitle)
        adapter = MarketItemAdapter(this, displayList)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, ListingDetailActivity::class.java)
            intent.putExtra(ListingDetailActivity.EXTRA_LISTING_ID, displayList[position].id)
            startActivity(intent)
        }

        // Category chips
        chips = listOf(
            findViewById<TextView>(R.id.chipAll) to MarketItem.CATEGORY_ALL,
            findViewById<TextView>(R.id.chipTextbooks) to Categories.TEXTBOOKS,
            findViewById<TextView>(R.id.chipUniforms) to Categories.UNIFORMS,
            findViewById<TextView>(R.id.chipDrafting) to Categories.DRAFTING,
            findViewById<TextView>(R.id.chipLab) to Categories.LAB,
            findViewById<TextView>(R.id.chipGadgets) to Categories.GADGETS,
            findViewById<TextView>(R.id.chipOthers) to Categories.OTHERS
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

        // Posting
        val openPost = View.OnClickListener { startActivity(Intent(this, PostItemActivity::class.java)) }
        findViewById<View>(R.id.fabPostItem).setOnClickListener(openPost)
        findViewById<View>(R.id.btnSellNow).setOnClickListener(openPost)
        findViewById<View>(R.id.btnEmptySell).setOnClickListener(openPost)

        // Profile and placeholders
        findViewById<View>(R.id.txtAvatar).setOnClickListener { showProfileSheet() }
        findViewById<View>(R.id.navProfile).setOnClickListener { showProfileSheet() }
        findViewById<View>(R.id.navMarket).setOnClickListener {
            findViewById<ScrollView>(R.id.feedScroll).smoothScrollTo(0, 0)
        }
        findViewById<View>(R.id.btnNotify).setOnClickListener { Ui.comingSoon(this, "Notifications") }
        findViewById<View>(R.id.btnFilter).setOnClickListener { Ui.comingSoon(this, "More filters") }
        findViewById<View>(R.id.navSaved).setOnClickListener { Ui.comingSoon(this, "Saved items") }
        findViewById<View>(R.id.navMessages).setOnClickListener { Ui.comingSoon(this, "In-app chat") }

        loadMyProfile()
    }

    override fun onStart() {
        super.onStart()
        if (user == null) return
        listingsRegistration = repository.listenToAvailableListings(
            onListings = { items ->
                loaded = true
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

        val empty = loaded && displayList.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        grid.visibility = if (empty) View.GONE else View.VISIBLE
        sectionTitle.text =
            if (selectedCategory == MarketItem.CATEGORY_ALL) getString(R.string.fresh_on_campus) else selectedCategory
    }

    private fun updateChipStyles() {
        val white = ContextCompat.getColor(this, R.color.white)
        val ink = ContextCompat.getColor(this, R.color.cc_ink)
        for ((chip, category) in chips) {
            val selected = category == selectedCategory
            chip.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
            chip.setTextColor(if (selected) white else ink)
            chip.compoundDrawableTintList = ColorStateList.valueOf(if (selected) white else ink)
            chip.isSelected = selected
        }
    }

    /** Loads the student's name and college, and sends unverified students back to verification. */
    private fun loadMyProfile() {
        val uid = user?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (isFinishing) return@addOnSuccessListener
                if (!doc.exists() || doc.getBoolean("verified") != true) {
                    Ui.openFresh(this, VerifyEmailActivity::class.java)
                    return@addOnSuccessListener
                }
                doc.getString("displayName")?.takeIf { it.isNotBlank() }?.let { myName = it }
                doc.getString("department")?.let { myDept = it }
                findViewById<TextView>(R.id.txtName).text = myName.substringBefore(" ")
                findViewById<TextView>(R.id.txtAvatar).text = Ui.initials(myName)
            }
    }

    private fun showProfileSheet() {
        val sheet = BottomSheetDialog(this, R.style.CC_BottomSheetDialog)
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_profile, null)
        view.findViewById<TextView>(R.id.sheetAvatar).text = Ui.initials(myName)
        view.findViewById<TextView>(R.id.sheetName).text = myName
        view.findViewById<TextView>(R.id.sheetDept).apply {
            text = myDept
            visibility = if (myDept.isEmpty()) View.GONE else View.VISIBLE
        }
        view.findViewById<TextView>(R.id.sheetEmail).text = user?.email
        view.findViewById<Button>(R.id.btnSignOut).setOnClickListener {
            sheet.dismiss()
            FirebaseAuth.getInstance().signOut()
            Ui.openFresh(this, MainActivity::class.java)
        }
        sheet.setContentView(view)
        sheet.show()
    }
}
