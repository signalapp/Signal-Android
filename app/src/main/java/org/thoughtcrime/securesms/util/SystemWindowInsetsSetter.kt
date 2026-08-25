package org.thoughtcrime.securesms.util

import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

object SystemWindowInsetsSetter {

  /**
   * The safe area for a screen root that contains text input. The host windows are edge-to-edge, so
   * `adjustResize` no longer shrinks the window when the keyboard opens and the root has to carry the
   * IME inset itself. Bottom resolves to the keyboard height or the navigation bar, whichever is larger.
   */
  @JvmField
  val SAFE_AREA_WITH_KEYBOARD: Int = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()

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
   * Accounts for the system bar insets by adding them on top of the view's original padding
   * ([ApplyMode.PADDING]) or margin ([ApplyMode.MARGIN]).
   *
   * Applied from two places. Primarily from the inset dispatch, which runs before measure and layout, so the
   * first frame is already inset instead of visibly shifting a frame later. That dispatch doesn't reach every
   * view though (an ancestor may consume the insets first), so each layout re-applies as a fallback, posted
   * because a layout-time `requestLayout()` is dropped by the framework. Both paths are safe to run
   * repeatedly: they only trigger another layout if the values actually changed.
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

    val applyInsets = {
      val insets = resolveInsets(view, insetType)
      val left = base.left + insets.left
      val top = base.top + insets.top
      val right = base.right + insets.right
      val bottom = base.bottom + insets.bottom

      when (applyMode) {
        ApplyMode.PADDING -> view.setPadding(left, top, right, bottom)

        ApplyMode.MARGIN -> {
          val params = view.layoutParams as? ViewGroup.MarginLayoutParams
          if (params != null && (params.leftMargin != left || params.topMargin != top || params.rightMargin != right || params.bottomMargin != bottom)) {
            params.setMargins(left, top, right, bottom)
            view.layoutParams = params
          }
        }
      }
    }

    ViewCompat.setOnApplyWindowInsetsListener(view) { target, windowInsets ->
      // Let the view dispatch on down to its children first, then apply ours on top.
      val result = ViewCompat.onApplyWindowInsets(target, windowInsets)
      applyInsets()
      result
    }

    val listener = view.doOnEachLayout {
      view.post { applyInsets() }
    }

    val lifecycleObserver = object : DefaultLifecycleObserver {
      override fun onDestroy(owner: LifecycleOwner) {
        view.removeOnLayoutChangeListener(listener)
        ViewCompat.setOnApplyWindowInsetsListener(view, null)
      }
    }

    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
  }

  /**
   * Resolves the insets to apply for [insetType], falling back to [ViewUtil] bar heights when
   * [ViewCompat.getRootWindowInsets] reports nothing.
   *
   * On API < 30 there is no platform IME inset, but [WindowInsetsCompat] derives one from the difference between
   * the system window insets and the stable insets, which is exactly what the (edge-to-edge, and therefore never
   * resized) window reports while the keyboard is up.
   */
  private fun resolveInsets(view: View, @WindowInsetsCompat.Type.InsetsType insetType: Int): Insets {
    val rootInsets = ViewCompat.getRootWindowInsets(view)
    val insets: Insets? = rootInsets?.getInsets(insetType)

    if (insets != null && (!insets.isEmpty() || ViewUtil.isGestureNavigation(view.resources, rootInsets))) {
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
