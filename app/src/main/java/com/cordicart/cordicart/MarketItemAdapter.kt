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

    /** Holds the card's views, so findViewById runs once per card instead of on every scroll. */
    private class Holder(view: View) {
        val imageArea: View = view.findViewById(R.id.imageArea)
        val icon: ImageView = view.findViewById(R.id.imgCategory)
        val courseTag: TextView = view.findViewById(R.id.txtCourseTag)
        val title: TextView = view.findViewById(R.id.txtTitle)
        val meta: TextView = view.findViewById(R.id.txtMeta)
        val price: TextView = view.findViewById(R.id.txtPrice)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_market_card, parent, false)
                .also { it.tag = Holder(it) }
        val h = view.tag as Holder

        getItem(position)?.let { item ->
            val info = Categories.info(item.category)
            h.imageArea.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, info.tileColor))
            h.icon.setImageResource(info.icon)
            h.icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, info.iconColor))
            h.courseTag.text = item.courseCode
            h.title.text = item.title
            h.meta.text = listOf(item.condition, item.sellerDept).filter { it.isNotBlank() }.joinToString(" · ")
            h.price.text = Ui.priceText(context, item)
            view.contentDescription = "${item.title}, ${item.displayPrice}"
        }
        return view
    }
}
