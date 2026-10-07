package com.cordicart.cordicart

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Turns each MarketItem into a card in the 2-column grid. */
class MarketItemAdapter(context: Context, items: MutableList<MarketItem>) :
    ArrayAdapter<MarketItem>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_market_card, parent, false)

        getItem(position)?.let { item ->
            val info = Categories.info(item.category)
            view.findViewById<View>(R.id.imageArea).backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, info.tileColor))
            view.findViewById<ImageView>(R.id.imgCategory).apply {
                setImageResource(info.icon)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, info.iconColor))
            }
            view.findViewById<TextView>(R.id.txtCourseTag).text = item.courseCode
            view.findViewById<TextView>(R.id.txtTitle).text = item.title
            view.findViewById<TextView>(R.id.txtMeta).text =
                listOf(item.condition, item.sellerDept).filter { it.isNotBlank() }.joinToString(" · ")
            view.findViewById<TextView>(R.id.txtPrice).text = Ui.priceText(context, item)
            view.contentDescription = "${item.title}, ${item.displayPrice}"
        }
        return view
    }
}
