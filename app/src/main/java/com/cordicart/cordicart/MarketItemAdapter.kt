package com.cordicart.cordicart

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

/** Turns each MarketItem into a card in the 2-column grid. */
class MarketItemAdapter(context: Context, items: MutableList<MarketItem>) :
    ArrayAdapter<MarketItem>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_market_card, parent, false)

        getItem(position)?.let { item ->
            view.findViewById<TextView>(R.id.txtImageLabel).text = item.imageLabel
            view.findViewById<TextView>(R.id.txtCourseTag).text = item.courseCode
            view.findViewById<TextView>(R.id.txtConditionTag).text = item.condition
            view.findViewById<TextView>(R.id.txtTitle).text = item.title
            view.findViewById<TextView>(R.id.txtPrice).text = item.displayPrice
        }
        return view
    }
}
