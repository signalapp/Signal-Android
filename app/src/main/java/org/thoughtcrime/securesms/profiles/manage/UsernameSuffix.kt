package org.thoughtcrime.securesms.profiles.manage

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R

/**
 * Describes the state of the username suffix, which is a spanned CharSequence.
 */
data class UsernameSuffix(
  val charSequence: CharSequence?
) {

  val isInProgress = charSequence == null

  companion object {
    @JvmField
    val LOADING = UsernameSuffix(null)

    @JvmField
    val NONE = UsernameSuffix("")

    @JvmStatic
    fun fromCode(code: Int) = UsernameSuffix("#$code")

    @JvmStatic
    fun getInProgressDrawable(context: Context): IndeterminateDrawable<CircularProgressIndicatorSpec> {
      val progressIndicatorSpec = CircularProgressIndicatorSpec(context, null).apply {
        indicatorInset = 0
        indicatorSize = DimensionUnit.DP.toPixels(16f).toInt()
        trackColor = ContextCompat.getColor(context, R.color.signal_colorOnSurfaceVariant)
        trackThickness = DimensionUnit.DP.toPixels(1f).toInt()
      }

      return IndeterminateDrawable.createCircularDrawable(context, progressIndicatorSpec).apply {
        setBounds(0, 0, DimensionUnit.DP.toPixels(16f).toInt(), DimensionUnit.DP.toPixels(16f).toInt())
      }
    }
  }
}
