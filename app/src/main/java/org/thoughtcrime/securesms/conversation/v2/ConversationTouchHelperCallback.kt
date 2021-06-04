package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.view_visible_message.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageView
import org.thoughtcrime.securesms.loki.utilities.toDp
import org.thoughtcrime.securesms.loki.utilities.toPx
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class ConversationTouchHelperCallback(private val adapter: ConversationAdapter, private val context: Context,
    private val onSwipe: (Int) -> Unit) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
    private val background = ContextCompat.getDrawable(context, R.drawable.ic_baseline_reply_24)!!
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

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        val adjustedDistanceInPx = dX / 4
        super.onChildDraw(c, recyclerView, viewHolder, adjustedDistanceInPx, dY, actionState, isCurrentlyActive)
        val absDistanceInDp = abs(toDp(dX, context.resources))
        val threshold = ConversationTouchHelperCallback.swipeToReplyThreshold
        val view = viewHolder.itemView
        if (view !is VisibleMessageView) { return }
        // Draw the background
        val messageContentView = view.messageContentView
        if (dX < 0) { // Swipe to the left
            val alpha = min(absDistanceInDp, threshold) / threshold
            background.alpha = (alpha * 255.0f).roundToInt()
            val spacing = context.resources.getDimension(R.dimen.medium_spacing).toInt()
            val itemViewTop = viewHolder.itemView.top
            val itemViewBottom = viewHolder.itemView.bottom
            val height = itemViewBottom - itemViewTop
            val iconSize = toPx(24, context.resources)
            val offset = (height - iconSize) / 2
            background.bounds = Rect(
                messageContentView.right + adjustedDistanceInPx.toInt() + spacing,
                itemViewTop + offset,
                messageContentView.right + adjustedDistanceInPx.toInt() + iconSize + spacing,
                itemViewTop + offset + iconSize
            )
        } else {
            //background.setBounds(0, 0, 0, 0)
        }
        background.draw(c)
        // Perform haptic feedback and invoke onSwipe callback if threshold has been reached
        if (absDistanceInDp > threshold && previousX < threshold) {
            view.isHapticFeedbackEnabled = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onSwipe(viewHolder.adapterPosition)
        }
        previousX = absDistanceInDp
    }
}