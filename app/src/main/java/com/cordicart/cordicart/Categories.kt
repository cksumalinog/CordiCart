package com.cordicart.cordicart

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes

/** The six item categories, with the icon and colors each one uses on screen. */
object Categories {

    const val TEXTBOOKS = "Textbooks"
    const val UNIFORMS = "Uniforms & PE"
    const val DRAFTING = "Drafting & Art Supplies"
    const val LAB = "Lab & Clinical Gear"
    const val GADGETS = "Calculators & Gadgets"
    const val OTHERS = "Others"

    data class Info(
        val name: String,
        @DrawableRes val icon: Int,
        @ColorRes val tileColor: Int,   // background of the image area
        @ColorRes val iconColor: Int    // icon color on that background
    )

    val ALL = listOf(
        Info(TEXTBOOKS, R.drawable.ic_book, R.color.cc_pine_tint, R.color.cc_pine),
        Info(UNIFORMS, R.drawable.ic_shirt, R.color.cc_blue_tint, R.color.cc_blue),
        Info(DRAFTING, R.drawable.ic_ruler, R.color.cc_amber_tint, R.color.cc_amber_dark),
        Info(LAB, R.drawable.ic_flask, R.color.cc_pine_tint, R.color.cc_pine),
        Info(GADGETS, R.drawable.ic_calculator, R.color.cc_blue_tint, R.color.cc_blue),
        Info(OTHERS, R.drawable.ic_grid, R.color.cc_amber_tint, R.color.cc_amber_dark)
    )

    fun info(name: String): Info = ALL.firstOrNull { it.name == name } ?: ALL.last()

    /**
     * Listings posted with the old 3-category version still exist in Firestore.
     * This maps their old names to the new ones so they keep showing up in the right place.
     */
    fun normalize(raw: String?): String {
        val name = raw?.trim().orEmpty()
        return when {
            name == "Uniforms" -> UNIFORMS
            name == "Drafting & Lab Gear" -> DRAFTING
            ALL.any { it.name == name } -> name
            else -> OTHERS
        }
    }
}
