package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import network.loki.messenger.R
import kotlin.math.roundToInt

object MessageBubbleUtilities {

    fun calculateRadii(context: Context, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean, isOutgoing: Boolean): IntArray {
        val roundedDimen = context.resources.getDimension(R.dimen.message_corner_radius).roundToInt()
        val collapsedDimen = context.resources.getDimension(R.dimen.message_corner_collapse_radius).roundToInt()
        val (tl, tr, bl, br) = when {
            // Single message
            isStartOfMessageCluster && isEndOfMessageCluster -> intArrayOf(roundedDimen, roundedDimen, roundedDimen, roundedDimen)
            // Start of message cluster; collapsed BL
            isStartOfMessageCluster -> intArrayOf(roundedDimen, roundedDimen, collapsedDimen, roundedDimen)
            // End of message cluster; collapsed TL
            isEndOfMessageCluster -> intArrayOf(collapsedDimen, roundedDimen, roundedDimen, roundedDimen)
            // In the middle; no rounding on the left
            else -> intArrayOf(collapsedDimen, roundedDimen, collapsedDimen, roundedDimen)
        }
        // TL, TR, BR, BL (CW direction)
        // Flip if the message is outgoing
        return intArrayOf(
            if (!isOutgoing) tl else tr, // TL
            if (!isOutgoing) tr else tl, // TR
            if (!isOutgoing) br else bl, // BR
            if (!isOutgoing) bl else br // BL
        )
    }
}