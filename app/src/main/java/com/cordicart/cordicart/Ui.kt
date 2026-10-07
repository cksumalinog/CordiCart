package com.cordicart.cordicart

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Small helpers shared by the screens. */
object Ui {

    /** Shows the red message box (layout_message.xml). */
    fun showError(box: View, message: String) = show(box, message, isInfo = false)

    /** Shows the same box in green, for neutral updates like "Sending code...". */
    fun showInfo(box: View, message: String) = show(box, message, isInfo = true)

    fun hideMessage(box: View) {
        box.visibility = View.GONE
    }

    private fun show(box: View, message: String, isInfo: Boolean) {
        val color = ContextCompat.getColor(box.context, if (isInfo) R.color.cc_pine else R.color.cc_error)
        box.setBackgroundResource(if (isInfo) R.drawable.bg_message_info else R.drawable.bg_message_error)
        box.findViewById<ImageView>(R.id.msgIcon).apply {
            setImageResource(if (isInfo) R.drawable.ic_info else R.drawable.ic_alert)
            imageTintList = ColorStateList.valueOf(color)
        }
        box.findViewById<TextView>(R.id.msgText).apply {
            text = message
            setTextColor(color)
        }
        box.visibility = View.VISIBLE
    }

    /** "Clarence Kenzo Sumalinog" -> "CK" */
    fun initials(name: String): String =
        name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "UC" }

    /** For buttons whose feature is not built yet, so nothing feels broken when tapped. */
    fun comingSoon(context: Context, feature: String) {
        Toast.makeText(context, context.getString(R.string.coming_soon, feature), Toast.LENGTH_SHORT).show()
    }

    /** Opens a screen and clears everything behind it (so Back can't return to log-in screens). */
    fun openFresh(from: Activity, target: Class<out Activity>) {
        val intent = Intent(from, target)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        from.startActivity(intent)
        from.finish()
    }

    /** "₱180 or trade", "₱250", or "Trade only", with the trade part in amber. */
    fun priceText(context: Context, item: MarketItem): CharSequence {
        val amber = ContextCompat.getColor(context, R.color.cc_amber_dark)
        val out = SpannableStringBuilder()
        val price = item.price
        if (price != null) {
            out.append(MarketItem.formatPeso(price))
            if (item.openToTrade) {
                val start = out.length
                out.append("  or trade")
                out.setSpan(ForegroundColorSpan(amber), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(RelativeSizeSpan(0.72f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            out.append("Trade only")
            out.setSpan(ForegroundColorSpan(amber), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** Makes one part of a sentence bold and pine green. */
    fun highlight(context: Context, before: String, bold: String, after: String): CharSequence {
        val out = SpannableStringBuilder(before)
        val start = out.length
        out.append(bold)
        out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.cc_pine)),
            start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        out.append(after)
        return out
    }
}
