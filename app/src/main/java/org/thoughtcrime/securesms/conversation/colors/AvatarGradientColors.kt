package org.thoughtcrime.securesms.conversation.colors

import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import org.thoughtcrime.securesms.recipients.Recipient
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs

/**
 * Lists gradients used to hide profiles during message request states
 */
object AvatarGradientColors {

  @JvmStatic
  fun getGradientDrawable(recipient: Recipient): GradientDrawable {
    return if (recipient.serviceId.getOrNull() != null) {
      gradients[abs(recipient.requireServiceId().hashCode() % gradients.size)].getDrawable()
    } else if (recipient.groupId.getOrNull() != null) {
      gradients[abs(recipient.requireGroupId().hashCode() % gradients.size)].getDrawable()
    } else {
      gradients[0].getDrawable()
    }
  }

  private val gradients = listOf(
    AvatarGradientColor(0xFF252568.toInt(), 0xFF9C8F8F.toInt()),
    AvatarGradientColor(0xFF2A4275.toInt(), 0xFF9D9EA1.toInt()),
    AvatarGradientColor(0xFF2E4B5F.toInt(), 0xFF8AA9B1.toInt()),
    AvatarGradientColor(0xFF2E426C.toInt(), 0xFF7A9377.toInt()),
    AvatarGradientColor(0xFF1A341A.toInt(), 0xFF807F6E.toInt()),
    AvatarGradientColor(0xFF464E42.toInt(), 0xFFD5C38F.toInt()),
    AvatarGradientColor(0xFF595643.toInt(), 0xFF93A899.toInt()),
    AvatarGradientColor(0xFF2C2F36.toInt(), 0xFF687466.toInt()),
    AvatarGradientColor(0xFF2B1E18.toInt(), 0xFF968980.toInt()),
    AvatarGradientColor(0xFF7B7067.toInt(), 0xFFA5A893.toInt()),
    AvatarGradientColor(0xFF706359.toInt(), 0xFFBDA194.toInt()),
    AvatarGradientColor(0xFF383331.toInt(), 0xFFA48788.toInt()),
    AvatarGradientColor(0xFF924F4F.toInt(), 0xFF897A7A.toInt()),
    AvatarGradientColor(0xFF663434.toInt(), 0xFFC58D77.toInt()),
    AvatarGradientColor(0xFF8F4B02.toInt(), 0xFFAA9274.toInt()),
    AvatarGradientColor(0xFF784747.toInt(), 0xFF8C8F6F.toInt()),
    AvatarGradientColor(0xFF747474.toInt(), 0xFFACACAC.toInt()),
    AvatarGradientColor(0xFF49484C.toInt(), 0xFFA5A6B5.toInt()),
    AvatarGradientColor(0xFF4A4E4D.toInt(), 0xFFABAFAE.toInt()),
    AvatarGradientColor(0xFF3A3A3A.toInt(), 0xFF929887.toInt())
  )

  data class AvatarGradientColor(@ColorInt val startGradient: Int, @ColorInt val endGradient: Int) {
    fun getDrawable(): GradientDrawable {
      val gradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(startGradient, endGradient)
      )
      gradientDrawable.shape = GradientDrawable.OVAL

      return gradientDrawable
    }
  }
}
