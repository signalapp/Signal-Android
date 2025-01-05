package org.thoughtcrime.securesms.conversation.colors

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * Helper class for all things ChatColors.
 *
 * - Maintains a mapping for group recipient colors
 * - Gives easy access to different bubble colors
 * - Watches and responds to RecyclerView scroll and layout changes to update a ColorizerView
 */
class Colorizer {

  private var colorsHaveBeenSet = false

  @Deprecated("Not needed for CFv2")
  private val groupSenderColors: MutableMap<RecipientId, NameColor> = mutableMapOf()

  private val groupMembers: LinkedHashSet<ServiceId> = linkedSetOf()

  @ColorInt
  fun getOutgoingBodyTextColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.conversation_outgoing_body_color)
  }

  @ColorInt
  fun getOutgoingFooterTextColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.conversation_outgoing_footer_color)
  }

  @ColorInt
  fun getOutgoingFooterIconColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.conversation_outgoing_footer_color)
  }

  @ColorInt
  fun getIncomingBodyTextColor(context: Context, hasWallpaper: Boolean): Int {
    return if (hasWallpaper) {
      ContextCompat.getColor(context, R.color.signal_colorNeutralInverse)
    } else {
      ContextCompat.getColor(context, R.color.signal_colorOnSurface)
    }
  }

  @ColorInt
  fun getIncomingFooterTextColor(context: Context, hasWallpaper: Boolean): Int {
    return if (hasWallpaper) {
      ContextCompat.getColor(context, R.color.signal_colorNeutralVariantInverse)
    } else {
      ContextCompat.getColor(context, R.color.signal_colorOnSurfaceVariant)
    }
  }

  @ColorInt
  fun getIncomingFooterIconColor(context: Context, hasWallpaper: Boolean): Int {
    return if (hasWallpaper) {
      ContextCompat.getColor(context, R.color.signal_colorNeutralVariantInverse)
    } else {
      ContextCompat.getColor(context, R.color.signal_colorOnSurfaceVariant)
    }
  }

  @ColorInt
  fun getIncomingGroupSenderColor(context: Context, recipient: Recipient): Int {
    return if (groupMembers.isEmpty()) {
      groupSenderColors[recipient.id]?.getColor(context) ?: getDefaultColor(context, recipient)
    } else if (recipient.hasServiceId) {
      val memberPosition = groupMembers.indexOf(recipient.requireServiceId())

      if (memberPosition >= 0) {
        val colorPosition = memberPosition % ChatColorsPalette.Names.all.size
        ChatColorsPalette.Names.all[colorPosition].getColor(context)
      } else {
        getDefaultColor(context, recipient)
      }
    } else {
      getDefaultColor(context, recipient)
    }
  }

  fun onGroupMembershipChanged(serviceIds: List<ServiceId>) {
    groupMembers.addAll(serviceIds.sortedBy { it.toString() })
  }

  @Deprecated("Not needed for CFv2", ReplaceWith("onGroupMembershipChanged"))
  fun onNameColorsChanged(nameColorMap: Map<RecipientId, NameColor>) {
    groupSenderColors.clear()
    groupSenderColors.putAll(nameColorMap)
    colorsHaveBeenSet = true
  }

  @ColorInt
  private fun getDefaultColor(context: Context, recipient: Recipient): Int {
    return if (colorsHaveBeenSet) {
      val color = ChatColorsPalette.Names.all[groupSenderColors.size % ChatColorsPalette.Names.all.size]
      groupSenderColors[recipient.id] = color
      return color.getColor(context)
    } else {
      getIncomingBodyTextColor(context, recipient.hasWallpaper)
    }
  }
}
