package org.thoughtcrime.securesms.util

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

object SystemWindowInsetsSetter {

  /**
   * How the resolved system-bar insets are applied to the target view. In both modes the view's
   * original padding/margin (captured when [attach] is called) is preserved and the insets are
   * added on top, so a view keeps its designed spacing and still clears the system bars.
   */
  enum class ApplyMode {
    PADDING,
    MARGIN
  }

  /**
   * Updates the view whenever a layout occurs to properly account for the system bar insets, added
   * on top of the view's original padding ([ApplyMode.PADDING]) or margin ([ApplyMode.MARGIN]).
   * This is safe to call repeatedly because it only triggers an extra layout pass IF the applied
   * values actually changed.
   */
  @JvmStatic
  @JvmOverloads
  fun attach(
    view: View,
    lifecycleOwner: LifecycleOwner,
    @WindowInsetsCompat.Type.InsetsType insetType: Int = WindowInsetsCompat.Type.systemBars(),
    applyMode: ApplyMode = ApplyMode.PADDING
  ) {
    val base: Insets = if (applyMode == ApplyMode.MARGIN) {
      val params = view.layoutParams as? ViewGroup.MarginLayoutParams
      Insets.of(params?.leftMargin ?: 0, params?.topMargin ?: 0, params?.rightMargin ?: 0, params?.bottomMargin ?: 0)
    } else {
      Insets.of(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
    }

    val listener = view.doOnEachLayout {
      val insets = resolveInsets(view, insetType)
      val left = base.left + insets.left
      val top = base.top + insets.top
      val right = base.right + insets.right
      val bottom = base.bottom + insets.bottom

      view.post {
        when (applyMode) {
          ApplyMode.PADDING -> view.setPadding(left, top, right, bottom)
          ApplyMode.MARGIN -> {
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return@post
            if (params.leftMargin != left || params.topMargin != top || params.rightMargin != right || params.bottomMargin != bottom) {
              params.setMargins(left, top, right, bottom)
              view.layoutParams = params
            }
          }
        }
      }
    }

    val lifecycleObserver = object : DefaultLifecycleObserver {
      override fun onDestroy(owner: LifecycleOwner) {
        view.removeOnLayoutChangeListener(listener)
      }
    }

    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
  }

  /**
   * Resolves the insets to apply for [insetType], falling back to [ViewUtil] bar heights on the older
   * API levels / IME cases where [ViewCompat.getRootWindowInsets] cannot be trusted.
   */
  private fun resolveInsets(view: View, @WindowInsetsCompat.Type.InsetsType insetType: Int): Insets {
    val rootInsets = ViewCompat.getRootWindowInsets(view)
    val insets: Insets? = rootInsets?.getInsets(insetType)
    val canTrustInsets = Build.VERSION.SDK_INT > 29 || (WindowInsetsCompat.Type.ime() and insetType == 0)

    if (canTrustInsets && insets != null && (!insets.isEmpty() || ViewUtil.isGestureNavigation(view.resources, rootInsets))) {
      return insets
    }

    val top = if (insetType and WindowInsetsCompat.Type.statusBars() != 0) ViewUtil.getStatusBarHeight(view) else 0
    val bottom = if (insetType and WindowInsetsCompat.Type.navigationBars() != 0) ViewUtil.getNavigationBarHeight(view) else 0
    return Insets.of(0, top, 0, bottom)
  }

  private fun Insets.isEmpty(): Boolean {
    return (top + bottom + right + left) == 0
  }
}
