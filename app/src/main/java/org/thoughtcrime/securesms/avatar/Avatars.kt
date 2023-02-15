package org.thoughtcrime.securesms.avatar

import android.content.Context
import android.graphics.Paint
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import kotlin.math.abs
import kotlin.math.min

object Avatars {

  /**
   * Enum class mirroring AvatarColors codes but utilizing foreground colors for text or icon tinting.
   */
  enum class ForegroundColor(private val code: String, @ColorInt val colorInt: Int) {
    A100("A100", 0xFF3838F5.toInt()),
    A110("A110", 0xFF1251D3.toInt()),
    A120("A120", 0xFF086DA0.toInt()),
    A130("A130", 0xFF067906.toInt()),
    A140("A140", 0xFF661AFF.toInt()),
    A150("A150", 0xFF9F00F0.toInt()),
    A160("A160", 0xFFB8057C.toInt()),
    A170("A170", 0xFFBE0404.toInt()),
    A180("A180", 0xFF836B01.toInt()),
    A190("A190", 0xFF7D6F40.toInt()),
    A200("A200", 0xFF4F4F6D.toInt()),
    A210("A210", 0xFF5C5C5C.toInt());

    fun deserialize(code: String): ForegroundColor {
      return values().find { it.code == code } ?: throw IllegalArgumentException()
    }

    fun serialize(): String = code
  }

  /**
   * Mapping which associates color codes to ColorPair objects containing background and foreground colors.
   */
  val colorMap: Map<String, ColorPair> = ForegroundColor.values().map {
    ColorPair(AvatarColor.deserialize(it.serialize()), it)
  }.associateBy {
    it.code
  }

  val colors: List<ColorPair> = colorMap.values.toList()

  val defaultAvatarsForSelf = linkedMapOf(
    "avatar_abstract_01" to DefaultAvatar(R.drawable.ic_avatar_abstract_01, "A130"),
    "avatar_abstract_02" to DefaultAvatar(R.drawable.ic_avatar_abstract_02, "A120"),
    "avatar_abstract_03" to DefaultAvatar(R.drawable.ic_avatar_abstract_03, "A170"),
    "avatar_cat" to DefaultAvatar(R.drawable.ic_avatar_cat, "A190"),
    "avatar_dog" to DefaultAvatar(R.drawable.ic_avatar_dog, "A140"),
    "avatar_fox" to DefaultAvatar(R.drawable.ic_avatar_fox, "A190"),
    "avatar_tucan" to DefaultAvatar(R.drawable.ic_avatar_tucan, "A120"),
    "avatar_sloth" to DefaultAvatar(R.drawable.ic_avatar_sloth, "A160"),
    "avatar_dinosaur" to DefaultAvatar(R.drawable.ic_avatar_dinosour, "A130"),
    "avatar_pig" to DefaultAvatar(R.drawable.ic_avatar_pig, "A180"),
    "avatar_incognito" to DefaultAvatar(R.drawable.ic_avatar_incognito, "A220"),
    "avatar_ghost" to DefaultAvatar(R.drawable.ic_avatar_ghost, "A100")
  )

  val defaultAvatarsForGroup = linkedMapOf(
    "avatar_heart" to DefaultAvatar(R.drawable.ic_avatar_heart, "A180"),
    "avatar_house" to DefaultAvatar(R.drawable.ic_avatar_house, "A120"),
    "avatar_melon" to DefaultAvatar(R.drawable.ic_avatar_melon, "A110"),
    "avatar_drink" to DefaultAvatar(R.drawable.ic_avatar_drink, "A170"),
    "avatar_celebration" to DefaultAvatar(R.drawable.ic_avatar_celebration, "A100"),
    "avatar_balloon" to DefaultAvatar(R.drawable.ic_avatar_balloon, "A220"),
    "avatar_book" to DefaultAvatar(R.drawable.ic_avatar_book, "A100"),
    "avatar_briefcase" to DefaultAvatar(R.drawable.ic_avatar_briefcase, "A180"),
    "avatar_sunset" to DefaultAvatar(R.drawable.ic_avatar_sunset, "A120"),
    "avatar_surfboard" to DefaultAvatar(R.drawable.ic_avatar_surfboard, "A110"),
    "avatar_soccerball" to DefaultAvatar(R.drawable.ic_avatar_soccerball, "A130"),
    "avatar_football" to DefaultAvatar(R.drawable.ic_avatar_football, "A220")
  )

  @DrawableRes
  fun getDrawableResource(key: String): Int? {
    val defaultAvatar = defaultAvatarsForSelf.getOrDefault(key, defaultAvatarsForGroup[key])

    return defaultAvatar?.vectorDrawableId
  }

  private fun textPaint(context: Context) = Paint().apply {
    isAntiAlias = true
    typeface = AvatarRenderer.getTypeface(context)
    textSize = 1f
  }

  /**
   * Calculate the text size for a give string using a maximum desired width and a maximum desired font size.
   */
  @JvmStatic
  fun getTextSizeForLength(context: Context, text: String, @Px maxWidth: Float, @Px maxSize: Float): Float {
    val paint = textPaint(context)
    return branchSizes(0f, maxWidth / 2, maxWidth, maxSize, paint, text)
  }

  /**
   * Uses binary search to determine optimal font size to within 1% given the input parameters.
   */
  private fun branchSizes(@Px lastFontSize: Float, @Px fontSize: Float, @Px target: Float, @Px maxFontSize: Float, paint: Paint, text: String): Float {
    paint.textSize = fontSize
    val textWidth = paint.measureText(text)
    val delta = abs(lastFontSize - fontSize) / 2f
    val isWithinThreshold = abs(1f - (textWidth / target)) <= 0.01f

    if (textWidth == 0f) {
      return maxFontSize
    }

    if (delta == 0f) {
      return min(maxFontSize, fontSize)
    }

    return when {
      fontSize >= maxFontSize -> {
        maxFontSize
      }
      isWithinThreshold -> {
        fontSize
      }
      textWidth > target -> {
        branchSizes(fontSize, fontSize - delta, target, maxFontSize, paint, text)
      }
      else -> {
        branchSizes(fontSize, fontSize + delta, target, maxFontSize, paint, text)
      }
    }
  }

  @JvmStatic
  fun getForegroundColor(avatarColor: AvatarColor): ForegroundColor {
    return ForegroundColor.values().firstOrNull { it.serialize() == avatarColor.serialize() } ?: ForegroundColor.A210
  }

  data class DefaultAvatar(
    @DrawableRes val vectorDrawableId: Int,
    val colorCode: String
  )

  data class ColorPair(
    @ColorInt val backgroundColor: Int,
    @ColorInt val foregroundColor: Int,
    val code: String
  ) {
    constructor(backgroundAvatarColor: AvatarColor, foregroundAvatarColor: ForegroundColor) : this(backgroundAvatarColor.colorInt(), foregroundAvatarColor.colorInt, backgroundAvatarColor.serialize())
  }
}
