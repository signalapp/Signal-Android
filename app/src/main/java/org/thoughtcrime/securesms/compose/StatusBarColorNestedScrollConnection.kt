package org.thoughtcrime.securesms.compose

import android.animation.ValueAnimator
import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.core.content.ContextCompat
import com.google.android.material.animation.ArgbEvaluatorCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.WindowUtil
import kotlin.math.abs

/**
 * Controls status-bar color based off a nested scroll
 *
 * Recommended to use this with [rememberStatusBarColorNestedScrollModifier] since it'll prevent you from having to wire through
 * an activity or the connection to subcomponents.
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

/**
 * Remembers the nested scroll modifier to ensure the proper status bar coloring behavior.
 *
 * This is only required if the screen you are modifying does not utilize edgeToEdge.
 */
@Composable
fun rememberStatusBarColorNestedScrollModifier(): Modifier {
  val activity = LocalContext.current as? AppCompatActivity

  return remember {
    if (activity != null) {
      Modifier.nestedScroll(StatusBarColorNestedScrollConnection(activity))
    } else {
      Modifier
    }
  }
}
