package com.cordicart.cordicart

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Screen 5 · Post an item. */
class PostItemActivity : AppCompatActivity() {

    private val repository = MarketRepository()
    private lateinit var categoryTiles: List<Pair<TextView, String>>
    private lateinit var conditionPills: List<Pair<TextView, String>>
    private var selectedCategory = Categories.TEXTBOOKS
    private var selectedCondition = "Gently Used"

    private var myName = "UC Student"
    private var myDept = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Ui.openFresh(this, MainActivity::class.java)
            return
        }
        setContentView(R.layout.activity_post_item)

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAddPhoto).setOnClickListener { Ui.comingSoon(this, "Photo upload") }

        // Category tiles: tap one to select it
        categoryTiles = listOf(
            findViewById<TextView>(R.id.catTextbooks) to Categories.TEXTBOOKS,
            findViewById<TextView>(R.id.catUniforms) to Categories.UNIFORMS,
            findViewById<TextView>(R.id.catDrafting) to Categories.DRAFTING,
            findViewById<TextView>(R.id.catLab) to Categories.LAB,
            findViewById<TextView>(R.id.catGadgets) to Categories.GADGETS,
            findViewById<TextView>(R.id.catOthers) to Categories.OTHERS
        )
        categoryTiles.forEach { (tile, name) ->
            tile.setOnClickListener { selectedCategory = name; updateSelections() }
        }

        // Condition pills
        conditionPills = listOf(
            findViewById<TextView>(R.id.condLikeNew) to "Like New",
            findViewById<TextView>(R.id.condGentlyUsed) to "Gently Used",
            findViewById<TextView>(R.id.condUsed) to "Used"
        )
        conditionPills.forEach { (pill, name) ->
            pill.setOnClickListener { selectedCondition = name; updateSelections() }
        }
        updateSelections()

        // Live preview of how the course code will be saved
        val editTag = findViewById<EditText>(R.id.editTag)
        val preview = findViewById<TextView>(R.id.txtTagPreview)
        editTag.doAfterTextChanged { text ->
            if (text.isNullOrBlank()) {
                preview.visibility = View.GONE
            } else {
                preview.visibility = View.VISIBLE
                preview.text = Ui.highlight(
                    this, "Saved as ", MarketItem.normalizeCourseCode(text.toString()), " so classmates can find it."
                )
            }
        }

        findViewById<Button>(R.id.btnPublish).setOnClickListener { publish(user.uid) }

        // Seller name and college come from the student's profile
        FirebaseFirestore.getInstance().collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                doc.getString("displayName")?.takeIf { it.isNotBlank() }?.let { myName = it }
                doc.getString("department")?.let { myDept = it }
            }
    }

    private fun updateSelections() {
        val pine = ContextCompat.getColor(this, R.color.cc_pine)
        val ink = ContextCompat.getColor(this, R.color.cc_ink)
        val white = ContextCompat.getColor(this, R.color.white)
        for ((tile, name) in categoryTiles) {
            val on = name == selectedCategory
            tile.setBackgroundResource(if (on) R.drawable.bg_option_selected else R.drawable.bg_option)
            tile.setTextColor(if (on) pine else ink)
            tile.compoundDrawableTintList = ColorStateList.valueOf(if (on) pine else ink)
            tile.isSelected = on
        }
        for ((pill, name) in conditionPills) {
            val on = name == selectedCondition
            pill.setBackgroundResource(if (on) R.drawable.bg_pill_option_selected else R.drawable.bg_pill_option)
            pill.setTextColor(if (on) white else ink)
            pill.isSelected = on
        }
    }

    private fun publish(uid: String) {
        val editTitle = findViewById<EditText>(R.id.editTitle)
        val editPrice = findViewById<EditText>(R.id.editPrice)
        val title = editTitle.text.toString().trim()
        val priceText = editPrice.text.toString().trim()
        val openToTrade = findViewById<MaterialSwitch>(R.id.switchTrade).isChecked

        if (title.isEmpty()) {
            editTitle.error = "Title cannot be empty"
            editTitle.requestFocus()
            return
        }

        var price: Double? = null
        if (priceText.isNotEmpty()) {
            price = priceText.toDoubleOrNull()
            val problem = when {
                price == null -> "Enter a valid amount, e.g. 250"
                price <= 0 -> "Price must be more than ₱0"
                price > MAX_PRICE -> "Price is too high"
                else -> null
            }
            if (problem != null) {
                editPrice.error = problem
                editPrice.requestFocus()
                return
            }
        }
        if (price == null && !openToTrade) {
            editPrice.error = "Enter a price or turn on \"Open to trade\""
            editPrice.requestFocus()
            return
        }

        val item = MarketItem(
            id = null,
            title = title,
            category = selectedCategory,
            courseCode = MarketItem.normalizeCourseCode(findViewById<EditText>(R.id.editTag).text.toString()),
            condition = selectedCondition,
            price = price,
            openToTrade = openToTrade,
            description = findViewById<EditText>(R.id.editDescription).text.toString().trim(),
            sellerId = uid,
            sellerName = myName,
            sellerDept = myDept
        )

        val button = findViewById<Button>(R.id.btnPublish)
        button.isEnabled = false                 // prevents double-posting from double taps
        button.text = "Publishing..."
        repository.addListing(
            item,
            onSuccess = {
                Toast.makeText(this, "Item published to CordiCart!", Toast.LENGTH_SHORT).show()
                finish()                         // the dashboard's live listener shows it right away
            },
            onError = { message ->
                button.isEnabled = true
                button.setText(R.string.publish_listing)
                Toast.makeText(this, "Could not publish: $message", Toast.LENGTH_LONG).show()
            }
        )
    }

    companion object {
        private const val MAX_PRICE = 100_000.0
    }
}
