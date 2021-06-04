package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.loki.utilities.toDp
import kotlin.math.abs

class ConversationTouchHelperCallback(private val adapter: ConversationAdapter, private val context: Context,
    private val onSwipe: (Int) -> Unit) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
    private var previousX: Float = 0.0f

    companion object {
        const val swipeToReplyThreshold = 200.0f // dp
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        adapter.notifyItemChanged(viewHolder.adapterPosition)
    }

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        super.onChildDraw(c, recyclerView, viewHolder, dX / 4 , dY, actionState, isCurrentlyActive)
        val x = abs(toDp(dX, context.resources))
        val threshold = ConversationTouchHelperCallback.swipeToReplyThreshold
        if (x > threshold && previousX < threshold) {
            val view = viewHolder.itemView
            view.isHapticFeedbackEnabled = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onSwipe(viewHolder.adapterPosition)
        }
        previousX = x
    }
}