package com.cordicart.cordicart

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** Seller actions shared by Listing details and My listings, so both behave the same. */
object MyListingActions {

    private val repository = MarketRepository()

    /** Available -> Sold, or Sold -> Available. Sold items leave the market and can't get new chats. */
    fun toggleSold(activity: Activity, item: MarketItem, onDone: () -> Unit) {
        val id = item.id ?: return
        val markSold = item.isAvailable
        val newStatus = if (markSold) MarketItem.STATUS_SOLD else MarketItem.STATUS_AVAILABLE

        val apply = {
            repository.setStatus(
                id, newStatus,
                onSuccess = {
                    Toast.makeText(
                        activity,
                        if (markSold) "Marked as sold. It's no longer on the market." else "Back on the market!",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDone()
                },
                onError = { msg -> Toast.makeText(activity, "Could not update: $msg", Toast.LENGTH_LONG).show() }
            )
        }

        if (markSold) {
            // Confirm the handoff first, so a sale isn't closed by accident.
            AlertDialog.Builder(activity)
                .setTitle("Confirm handoff")
                .setMessage("Did you already hand over \"${item.title}\" to the buyer? It will be removed from the market.")
                .setPositiveButton("Yes, mark as sold") { _, _ -> apply() }
                .setNegativeButton("Not yet", null)
                .show()
        } else {
            apply()
        }
    }

    fun confirmDelete(activity: Activity, item: MarketItem, onDone: () -> Unit) {
        val id = item.id ?: return
        AlertDialog.Builder(activity)
            .setTitle("Delete listing?")
            .setMessage("\"${item.title}\" will be removed for good. This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteListing(
                    id,
                    onSuccess = {
                        Toast.makeText(activity, "Listing deleted.", Toast.LENGTH_SHORT).show()
                        onDone()
                    },
                    onError = { msg -> Toast.makeText(activity, "Could not delete: $msg", Toast.LENGTH_LONG).show() }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
