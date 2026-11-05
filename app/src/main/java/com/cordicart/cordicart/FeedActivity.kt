package com.cordicart.cordicart

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
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
    private var verified = false   // nothing is loaded until the profile confirms the student is verified

    // The current filter state. Both are applied together every time.
    private var selectedCategory = MarketItem.CATEGORY_ALL
    private var searchQuery = ""

    // Extra filters from the filter sheet. null / false = not filtering on that.
    private var filterCondition: String? = null
    private var filterMinPrice: Double? = null
    private var filterMaxPrice: Double? = null
    private var filterTradeOnly = false
    private var filterButtonBackground: android.graphics.drawable.Drawable? = null   // original ripple
    private val filtersActive: Boolean
        get() = filterCondition != null || filterMinPrice != null || filterMaxPrice != null || filterTradeOnly

    // Search waits until you pause typing, instead of re-filtering on every letter.
    private val handler = Handler(Looper.getMainLooper())
    private val runSearch = Runnable { resetPaging(); applyFilters() }

    // The grid shows listings in pages, so a long feed doesn't build hundreds of cards at once.
    private var shownCount = PAGE_SIZE
    private lateinit var btnShowMore: TextView
    private lateinit var chips: List<Pair<TextView, String>>

    // Red badge on the Messages tab
    private lateinit var badge: TextView
    private val onUnreadChanged: (Int) -> Unit = { count -> showBadge(count) }

    // Android 13+ asks before an app may show notifications.
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* works either way */ }

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
            // getOrNull: the list can change between the tap and this code (live updates).
            val item = displayList.getOrNull(position) ?: return@setOnItemClickListener
            intent.putExtra(ListingDetailActivity.EXTRA_LISTING_ID, item.id)
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
                if (selectedCategory == category) return@setOnClickListener
                selectedCategory = category
                updateChipStyles()
                resetPaging()
                applyFilters()
            }
        }
        updateChipStyles()

        // Search (combined with the selected category)
        findViewById<EditText>(R.id.searchEditText).doOnTextChanged { text, _, _, _ ->
            searchQuery = text?.toString() ?: ""
            handler.removeCallbacks(runSearch)
            handler.postDelayed(runSearch, SEARCH_DELAY_MS)
        }

        // "Show more" adds the next page of listings
        btnShowMore = findViewById(R.id.btnShowMore)
        btnShowMore.setOnClickListener {
            shownCount += PAGE_SIZE
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
        findViewById<View>(R.id.btnFilter).apply {
            filterButtonBackground = background
            setOnClickListener { showFilterSheet() }
        }
        findViewById<View>(R.id.navSaved).setOnClickListener {
            startActivity(Intent(this, SavedActivity::class.java))
        }
        findViewById<View>(R.id.navMessages).setOnClickListener {
            startActivity(Intent(this, ChatListActivity::class.java))
        }

        // Unread badge (notifications start once the profile is confirmed, in loadMyProfile)
        badge = findViewById(R.id.badgeMessages)
        ChatNotifier.addUnreadListener(onUnreadChanged)

        loadMyProfile()
    }

    override fun onDestroy() {
        handler.removeCallbacks(runSearch)
        ChatNotifier.removeUnreadListener(onUnreadChanged)
        super.onDestroy()
    }

    private fun showBadge(count: Int) {
        badge.visibility = if (count > 0) View.VISIBLE else View.GONE
        badge.text = if (count > 9) "9+" else count.toString()
        findViewById<View>(R.id.navMessages).contentDescription =
            if (count > 0) "Messages, $count unread" else "Messages"
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        if (verified) startListings()
    }

    /** Live listings. Only started for verified students, so the database never refuses it. */
    private fun startListings() {
        if (user == null || listingsRegistration != null) return
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

    private fun resetPaging() {
        shownCount = PAGE_SIZE
    }

    private fun applyFilters() {
        val matches = MarketRepository.filter(allItems, selectedCategory, searchQuery)
            .filter { passesExtraFilters(it) }
        val page = matches.take(shownCount)

        // Only rebuild the grid when something actually changed (data class == compares every field).
        if (page != displayList) {
            displayList.clear()
            displayList.addAll(page)
            adapter.notifyDataSetChanged()
        }

        val hidden = matches.size - page.size
        btnShowMore.visibility = if (hidden > 0) View.VISIBLE else View.GONE
        btnShowMore.text = getString(R.string.show_more, minOf(hidden, PAGE_SIZE))

        val empty = loaded && displayList.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        grid.visibility = if (empty) View.GONE else View.VISIBLE
        sectionTitle.text =
            if (selectedCategory == MarketItem.CATEGORY_ALL) getString(R.string.fresh_on_campus) else selectedCategory
        if (filtersActive) sectionTitle.append(" · " + getString(R.string.filtered))
    }

    /** Condition, price range and trade-only, from the filter sheet. */
    private fun passesExtraFilters(item: MarketItem): Boolean {
        filterCondition?.let { if (item.condition != it) return false }
        if (filterTradeOnly && !item.openToTrade) return false
        if (filterMinPrice != null || filterMaxPrice != null) {
            val price = item.price ?: return false          // trade-only items have no price to compare
            filterMinPrice?.let { if (price < it) return false }
            filterMaxPrice?.let { if (price > it) return false }
        }
        return true
    }

    private fun showFilterSheet() {
        val sheet = BottomSheetDialog(this, R.style.CC_BottomSheetDialog)
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_filters, null)

        // Condition pills (one at a time)
        var condition = filterCondition
        val pills = listOf(
            view.findViewById<TextView>(R.id.fCondAny) to null,
            view.findViewById<TextView>(R.id.fCondLikeNew) to "Like New",
            view.findViewById<TextView>(R.id.fCondGently) to "Gently Used",
            view.findViewById<TextView>(R.id.fCondUsed) to "Used"
        )
        val white = ContextCompat.getColor(this, R.color.white)
        val ink = ContextCompat.getColor(this, R.color.cc_ink)
        fun stylePills() {
            for ((pill, value) in pills) {
                val on = value == condition
                pill.setBackgroundResource(if (on) R.drawable.bg_pill_option_selected else R.drawable.bg_pill_option)
                pill.setTextColor(if (on) white else ink)
                pill.isSelected = on
            }
        }
        for ((pill, value) in pills) pill.setOnClickListener { condition = value; stylePills() }
        stylePills()

        val minInput = view.findViewById<EditText>(R.id.fMinPrice)
        val maxInput = view.findViewById<EditText>(R.id.fMaxPrice)
        val tradeSwitch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.fTradeOnly)
        filterMinPrice?.let { minInput.setText(it.toLong().toString()) }
        filterMaxPrice?.let { maxInput.setText(it.toLong().toString()) }
        tradeSwitch.isChecked = filterTradeOnly

        view.findViewById<Button>(R.id.fReset).setOnClickListener {
            condition = null
            stylePills()
            minInput.setText("")
            maxInput.setText("")
            tradeSwitch.isChecked = false
        }
        view.findViewById<Button>(R.id.fApply).setOnClickListener {
            var min = minInput.text.toString().toDoubleOrNull()
            var max = maxInput.text.toString().toDoubleOrNull()
            if (min != null && max != null && min > max) { val t = min; min = max; max = t }   // swap if reversed
            filterCondition = condition
            filterMinPrice = min
            filterMaxPrice = max
            filterTradeOnly = tradeSwitch.isChecked
            updateFilterButton()
            resetPaging()
            applyFilters()
            sheet.dismiss()
            if (filtersActive) Toast.makeText(this, "Filters applied", Toast.LENGTH_SHORT).show()
        }

        sheet.setContentView(view)
        sheet.show()
    }

    /** The filter button turns amber while filters are on. */
    private fun updateFilterButton() {
        findViewById<ImageButton>(R.id.btnFilter).apply {
            if (filtersActive) setBackgroundResource(R.drawable.bg_circle_amber)
            else background = filterButtonBackground
            contentDescription = if (filtersActive) "Filters (on)" else "Filters"
        }
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

                // Verified: now it's safe to load listings and watch for messages.
                verified = true
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startListings()
                user?.let { ChatNotifier.start(this, it.uid) }
                askForNotificationPermission()
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
        view.findViewById<View>(R.id.btnMyListings).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, MyListingsActivity::class.java))
        }
        view.findViewById<View>(R.id.btnSavedItems).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, SavedActivity::class.java))
        }
        view.findViewById<Button>(R.id.btnSignOut).setOnClickListener {
            sheet.dismiss()
            ChatNotifier.stop()
            FirebaseAuth.getInstance().signOut()
            Ui.openFresh(this, MainActivity::class.java)
        }
        sheet.setContentView(view)
        sheet.show()
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val SEARCH_DELAY_MS = 250L
    }
}
