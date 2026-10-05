package com.cordicart.cordicart

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.util.Date
import java.util.Locale

/** One listing. Stored in Firestore as listings/{id}. */
data class MarketItem(
    val id: String?,              // null until saved to Firestore
    val title: String,
    val category: String,
    val courseCode: String,
    val condition: String,
    val price: Double?,           // null = no cash price (trade only)
    val openToTrade: Boolean,
    val description: String,
    val sellerId: String,
    val sellerName: String,
    val sellerDept: String,
    val status: String = STATUS_AVAILABLE,
    val createdAt: Date? = null
) {

    val isAvailable: Boolean get() = status == STATUS_AVAILABLE

    fun isOwnedBy(uid: String?): Boolean = uid != null && uid == sellerId

    val displayPrice: String
        get() = when {
            price != null && openToTrade -> "${formatPeso(price)} or trade"
            price != null -> formatPeso(price)
            else -> "Trade only"
        }

    /** Text shown in the grey image placeholder on each card. */
    val imageLabel: String
        get() = when (category) {
            "Textbooks" -> "📚 Textbook"
            "Uniforms" -> "👕 Uniform"
            "Drafting & Lab Gear" -> "📐 Drafting / Lab"
            else -> "📦 Item"
        }

    /** True if the item is in the chosen category AND matches the search text. */
    fun matches(selectedCategory: String, query: String): Boolean {
        val inCategory = selectedCategory == CATEGORY_ALL || category == selectedCategory
        if (!inCategory) return false

        val q = compact(query)
        if (q.isEmpty()) return true
        // Spaces are ignored, so "math101" finds "MATH 101".
        return compact(title).contains(q) ||
            compact(courseCode).contains(q) ||
            compact(category).contains(q) ||
            compact(sellerDept).contains(q)
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "category" to category,
        "courseCode" to courseCode,
        "condition" to condition,
        "price" to price,
        "openToTrade" to openToTrade,
        "description" to description,
        "sellerId" to sellerId,
        "sellerName" to sellerName,
        "sellerDept" to sellerDept,
        "status" to status,
        "createdAt" to FieldValue.serverTimestamp()
    )

    companion object {
        const val STATUS_AVAILABLE = "available"
        const val STATUS_SOLD = "sold"
        const val CATEGORY_ALL = "All"
        const val DEFAULT_COURSE_CODE = "GENERAL"

        /**
         * Makes course codes consistent so they can be searched and grouped:
         * "math101", "Math  101", " MATH 101 " all become "MATH 101".
         */
        fun normalizeCourseCode(raw: String?): String {
            if (raw == null) return DEFAULT_COURSE_CODE
            var code = raw.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")
            code = code.replace(Regex("([A-Z])\\s*(\\d)"), "\$1 \$2")   // space between letters and numbers
            return code.ifEmpty { DEFAULT_COURSE_CODE }
        }

        fun formatPeso(amount: Double): String =
            if (amount == Math.floor(amount)) String.format(Locale.US, "₱%,.0f", amount)
            else String.format(Locale.US, "₱%,.2f", amount)

        /** Builds an item from Firestore. Returns null for malformed documents instead of crashing. */
        fun fromDocument(doc: DocumentSnapshot): MarketItem? {
            if (!doc.exists()) return null
            val title = doc.getString("title") ?: return null

            // ESTIMATE gives a usable time even before the server confirms a new post.
            val createdAt = doc.getTimestamp(
                "createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.toDate()

            return MarketItem(
                id = doc.id,
                title = title,
                category = doc.getString("category").orDefault("Other"),
                courseCode = doc.getString("courseCode").orDefault(DEFAULT_COURSE_CODE),
                condition = doc.getString("condition").orDefault("Used"),
                price = doc.getDouble("price"),
                openToTrade = doc.getBoolean("openToTrade") == true,
                description = doc.getString("description").orDefault(""),
                sellerId = doc.getString("sellerId").orDefault(""),
                sellerName = doc.getString("sellerName").orDefault("UC Student"),
                sellerDept = doc.getString("sellerDept").orDefault(""),
                status = doc.getString("status").orDefault(STATUS_AVAILABLE),
                createdAt = createdAt
            )
        }

        private fun String?.orDefault(fallback: String): String =
            if (this.isNullOrBlank()) fallback else this

        private fun compact(s: String): String =
            s.replace(Regex("\\s+"), "").lowercase(Locale.ROOT)
    }
}
