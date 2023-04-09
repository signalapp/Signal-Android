package org.thoughtcrime.securesms.compose

import android.animation.ValueAnimator
import android.app.Activity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.core.content.ContextCompat
import com.google.android.material.animation.ArgbEvaluatorCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.WindowUtil
import kotlin.math.abs

/**
 * Controls status-bar color based off a nested scroll
 */
class StatusBarColorNestedScrollConnection(
  private val activity: Activity
) : NestedScrollConnection {
  private var animator: ValueAnimator? = null

  private val normalColor = ContextCompat.getColor(activity, R.color.signal_colorBackground)
  private val scrollColor = ContextCompat.getColor(activity, R.color.signal_colorSurface2)

  private var contentOffset = 0f

  override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
    return Velocity.Zero
  }

  override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
    val oldContentOffset = contentOffset
    if (consumed.y == 0f && available.y > 0f) {
      contentOffset = 0f
    } else {
      contentOffset += consumed.y
    }

    if (oldContentOffset.isNearZero() xor contentOffset.isNearZero()) {
      applyState()
    }

    return Offset.Zero
  }

  fun setColorImmediate() {
    val end = when {
      contentOffset.isNearZero() -> normalColor
      else -> scrollColor
    }

    animator?.cancel()
    WindowUtil.setStatusBarColor(
      activity.window,
      end
    )
  }

  private fun applyState() {
    val (start, end) = when {
      contentOffset.isNearZero() -> scrollColor to normalColor
      else -> normalColor to scrollColor
    }

    animator?.cancel()
    animator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 200
      addUpdateListener {
        WindowUtil.setStatusBarColor(
          activity.window,
          ArgbEvaluatorCompat.getInstance().evaluate(it.animatedFraction, start, end)
        )
      }
      start()
    }
  }

  private fun Float.isNearZero(): Boolean = abs(this) < 0.001
}
