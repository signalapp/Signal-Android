package org.thoughtcrime.securesms.conversation.colors

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Helper class for all things ChatColors.
 *
 * - Maintains a mapping for group recipient colors
 * - Gives easy access to different bubble colors
 * - Watches and responds to RecyclerView scroll and layout changes to update a ColorizerView
 */
class Colorizer {

  private var colorsHaveBeenSet = false
  private val groupSenderColors: MutableMap<RecipientId, NameColor> = mutableMapOf()

  @ColorInt
  fun getOutgoingBodyTextColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.white)
  }

  @ColorInt
  fun getOutgoingFooterTextColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.conversation_item_outgoing_footer_fg)
  }

  @ColorInt
  fun getOutgoingFooterIconColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.conversation_item_outgoing_footer_fg)
  }

  @ColorInt
  fun getIncomingGroupSenderColor(context: Context, recipient: Recipient): Int = groupSenderColors[recipient.id]?.getColor(context) ?: getDefaultColor(context, recipient.id)

  fun onNameColorsChanged(nameColorMap: Map<RecipientId, NameColor>) {
    groupSenderColors.clear()
    groupSenderColors.putAll(nameColorMap)
    colorsHaveBeenSet = true
  }

  @ColorInt
  private fun getDefaultColor(context: Context, recipientId: RecipientId): Int {
    return if (colorsHaveBeenSet) {
      val color = ChatColorsPalette.Names.all[groupSenderColors.size % ChatColorsPalette.Names.all.size]
      groupSenderColors[recipientId] = color
      return color.getColor(context)
    } else {
      Color.TRANSPARENT
    }
  }
}
