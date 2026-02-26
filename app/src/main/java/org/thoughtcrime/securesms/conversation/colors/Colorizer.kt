package org.thoughtcrime.securesms.conversation.colors

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import org.signal.core.models.ServiceId
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.signal.core.ui.R as CoreUiR

/**
 * Provides conversation bubble and sender name colors.
 *
 * Use [ColorizerV2] for new CFv2 code, and [ColorizerV1] for legacy CFv1 code.
 */
interface Colorizer {
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
      ContextCompat.getColor(context, CoreUiR.color.signal_colorNeutralInverse)
    } else {
      ContextCompat.getColor(context, CoreUiR.color.signal_colorOnSurface)
    }
  }

  @ColorInt
  fun getIncomingFooterTextColor(context: Context, hasWallpaper: Boolean): Int {
    return if (hasWallpaper) {
      ContextCompat.getColor(context, CoreUiR.color.signal_colorNeutralVariantInverse)
    } else {
      ContextCompat.getColor(context, CoreUiR.color.signal_colorOnSurfaceVariant)
    }
  }

  @ColorInt
  fun getIncomingFooterIconColor(context: Context, hasWallpaper: Boolean): Int {
    return if (hasWallpaper) {
      ContextCompat.getColor(context, CoreUiR.color.signal_colorNeutralVariantInverse)
    } else {
      ContextCompat.getColor(context, CoreUiR.color.signal_colorOnSurfaceVariant)
    }
  }

  @ColorInt
  fun getIncomingGroupSenderColor(context: Context, recipient: Recipient): Int {
    return getNameColor(context, recipient).getColor(context)
  }

  fun getNameColor(context: Context, recipient: Recipient): NameColor
}

/**
 * [Colorizer] implementation for CFv1 (legacy ConversationFragment).
 *
 * Colors are pre-assigned via [onNameColorsChanged] using a static [RecipientId] → [NameColor] map.
 *
 * See [ColorizerV2] for the CFv2 position-based approach.
 */
@Deprecated("Use ColorizerV2 instead. This class only exists to support the legacy CFv1.")
class ColorizerV1 : Colorizer {
  private var colorsHaveBeenSet = false
  private val groupSenderColors: MutableMap<RecipientId, NameColor> = mutableMapOf()

  /**
   * Replaces the entire mapping of group member IDs to name colors.
   *
   * Must be called before [getNameColor] to ensure colors are assigned correctly.
   */
  fun onNameColorsChanged(nameColorMap: Map<RecipientId, NameColor>) {
    groupSenderColors.clear()
    groupSenderColors.putAll(nameColorMap)
    colorsHaveBeenSet = true
  }

  /**
   * Returns the name color for the given recipient based on their position in the group member list.
   */
  override fun getNameColor(context: Context, recipient: Recipient): NameColor {
    val assignedColor = groupSenderColors[recipient.id]
    if (assignedColor != null) return assignedColor

    if (colorsHaveBeenSet) {
      return nameColorForPosition(groupSenderColors.size)
        .also { groupSenderColors[recipient.id] = it }
    }

    val colorInt = getIncomingBodyTextColor(context, recipient.hasWallpaper)
    return NameColor(lightColor = colorInt, darkColor = colorInt)
  }
}

/**
 * [Colorizer] implementation for CFv2 (ConversationFragment v2).
 *
 * Colors are derived from each member's sorted position in the group, populated via
 * [onGroupMembershipChanged]. For the legacy CFv1 approach, see [ColorizerV1].
 */
class ColorizerV2 @JvmOverloads constructor(groupMemberIds: List<ServiceId> = emptyList()) : Colorizer {
  private val groupMembers: LinkedHashSet<ServiceId> = linkedSetOf()

  init {
    onGroupMembershipChanged(groupMemberIds)
  }

  /**
   * Replaces the entire set of group members used for position-based name color assignment.
   *
   * Must be called before [getNameColor] to ensure colors are assigned correctly.
   */
  fun onGroupMembershipChanged(serviceIds: List<ServiceId>) {
    groupMembers.addAll(serviceIds.sortedBy { it.toString() })
  }

  /**
   * Returns the [NameColor] for the given [recipient] based on their sorted position among the
   * other group members supplied via [onGroupMembershipChanged].
   *
   * Falls back to a default text color if the recipient has no service ID or is not
   * found in the current membership set.
   */
  override fun getNameColor(context: Context, recipient: Recipient): NameColor {
    val serviceId = recipient.serviceId.orNull()
    if (serviceId != null) {
      val position = groupMembers.indexOf(serviceId)
      if (position >= 0) return nameColorForPosition(position)
    }

    val colorInt = getIncomingBodyTextColor(context, recipient.hasWallpaper)
    return NameColor(lightColor = colorInt, darkColor = colorInt)
  }
}

private fun nameColorForPosition(position: Int): NameColor {
  return ChatColorsPalette.Names.all[position % ChatColorsPalette.Names.all.size]
}
