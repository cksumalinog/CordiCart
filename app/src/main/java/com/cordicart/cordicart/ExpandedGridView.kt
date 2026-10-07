package com.cordicart.cordicart

import android.content.Context
import android.util.AttributeSet
import android.widget.GridView

/**
 * A GridView that shows all its rows instead of scrolling by itself.
 * This lets the whole dashboard (header, banner, chips and grid) scroll as one page.
 */
class ExpandedGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.gridViewStyle
) : GridView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val expanded = MeasureSpec.makeMeasureSpec(Int.MAX_VALUE shr 2, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, expanded)
    }
}
