/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.fallback

import androidx.annotation.DrawableRes
import androidx.annotation.Px
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.util.NameUtil

/**
 * Specifies what kind of avatar should be generated for a given recipient.
 */
sealed interface FallbackAvatar {

  val color: AvatarColor

  /**
   * Transparent avatar
   */
  data object Transparent : FallbackAvatar {
    override val color: AvatarColor = AvatarColor.UNKNOWN
  }

  /**
   * Generated avatars utilize the initials of the given recipient
   */
  data class Text(val content: String, override val color: AvatarColor) : FallbackAvatar {
    init {
      check(content.isNotEmpty())
    }
  }

  /**
   * Fallback avatars that are backed by resources.
   */
  sealed interface Resource : FallbackAvatar {

    @DrawableRes
    fun getIconBySize(size: Size): Int

    /**
     * Local user
     */
    data class Local(override val color: AvatarColor) : Resource {
      override fun getIconBySize(size: Size): Int {
        return when (size) {
          Size.SMALL -> R.drawable.symbol_note_compact_16
          Size.MEDIUM -> R.drawable.symbol_note_24
          Size.LARGE -> R.drawable.symbol_note_display_bold_40
        }
      }
    }

    /**
     * Individual user without a display name.
     */
    data class Person(override val color: AvatarColor) : Resource {
      override fun getIconBySize(size: Size): Int {
        return when (size) {
          Size.SMALL -> R.drawable.symbol_person_compact_16
          Size.MEDIUM -> R.drawable.symbol_person_24
          Size.LARGE -> R.drawable.symbol_person_display_bold_40
        }
      }
    }

    /**
     * A group
     */
    data class Group(override val color: AvatarColor) : Resource {
      override fun getIconBySize(size: Size): Int {
        return when (size) {
          Size.SMALL -> R.drawable.symbol_group_compact_16
          Size.MEDIUM -> R.drawable.symbol_group_24
          Size.LARGE -> R.drawable.symbol_group_display_bold_40
        }
      }
    }

    /**
     * Story distribution lists
     */
    data class DistributionList(override val color: AvatarColor) : Resource {
      override fun getIconBySize(size: Size): Int {
        return when (size) {
          Size.SMALL -> R.drawable.symbol_stories_compact_16
          Size.MEDIUM -> R.drawable.symbol_stories_24
          Size.LARGE -> R.drawable.symbol_stories_display_bold_40
        }
      }
    }

    /**
     * Call Links
     */
    data class CallLink(override val color: AvatarColor) : Resource {
      override fun getIconBySize(size: Size): Int {
        return when (size) {
          Size.SMALL -> R.drawable.symbol_video_compact_16
          Size.MEDIUM -> R.drawable.symbol_video_24
          Size.LARGE -> R.drawable.symbol_video_display_bold_40
        }
      }
    }
  }

  enum class Size {
    /**
     * Smaller than 32dp
     */
    SMALL,

    /**
     * 32dp and larger
     */
    MEDIUM,

    /**
     * 80dp and larger
     */
    LARGE
  }

  companion object {
    const val ICON_TO_BACKGROUND_SCALE = 0.625

    @JvmStatic
    @JvmOverloads
    fun forTextOrDefault(text: String, avatarColor: AvatarColor, default: FallbackAvatar = Resource.Person(avatarColor)): FallbackAvatar {
      val abbreviation = NameUtil.getAbbreviation(text)
      return if (abbreviation != null) {
        Text(abbreviation, avatarColor)
      } else {
        default
      }
    }

    fun getSizeByPx(@Px px: Int): Size {
      return getSizeByDp(DimensionUnit.PIXELS.toDp(px.toFloat()).dp)
    }

    fun getSizeByDp(dp: Dp): Size {
      val rawDp = dp.value
      return when {
        rawDp >= 80.0 -> Size.LARGE
        rawDp < 32.0 -> Size.SMALL
        else -> Size.MEDIUM
      }
    }
  }
}
