package org.thoughtcrime.securesms.mediasend.v2.text

import android.graphics.Color
import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.core.graphics.ColorUtils
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.fonts.TextFont
import org.thoughtcrime.securesms.scribbles.HSVColorSlider
import org.thoughtcrime.securesms.util.RemoteConfig

@Parcelize
data class TextStoryPostCreationState(
  val body: CharSequence = "",
  val textColor: Int = HSVColorSlider.getLastColor(),
  val textColorStyle: TextColorStyle = TextColorStyle.NO_BACKGROUND,
  val textAlignment: TextAlignment = if (RemoteConfig.storiesTextFunctions) TextAlignment.START else TextAlignment.CENTER,
  val textFont: TextFont = TextFont.REGULAR,
  @IntRange(from = 0, to = 100) val textScale: Int = 50,
  val backgroundColor: ChatColors = TextStoryBackgroundColors.getInitialBackgroundColor(),
  val linkPreviewUri: String? = null
) : Parcelable {

  @ColorInt
  @IgnoredOnParcel
  val textForegroundColor: Int = when (textColorStyle) {
    TextColorStyle.NO_BACKGROUND -> textColor
    TextColorStyle.NORMAL -> textColor
    TextColorStyle.INVERT -> getDefaultColorForLightness(textColor)
  }

  @ColorInt
  @IgnoredOnParcel
  val textBackgroundColor: Int = when (textColorStyle) {
    TextColorStyle.NO_BACKGROUND -> Color.TRANSPARENT
    TextColorStyle.NORMAL -> getDefaultColorForLightness(textColor)
    TextColorStyle.INVERT -> textColor
  }

  private fun getDefaultColorForLightness(textColor: Int): Int {
    val hsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(textColor, hsl)

    val lightness = hsl[2]

    return if (lightness >= 0.9f) {
      Color.BLACK
    } else {
      Color.WHITE
    }
  }
}
