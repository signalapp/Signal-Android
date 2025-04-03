package org.thoughtcrime.securesms.util

import android.animation.ValueAnimator
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.animation.ArgbEvaluatorCompat
import com.google.android.material.appbar.AppBarLayout
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.views.Stub

/**
 * Sets the view's isActivated state when the content of the attached recycler can scroll up.
 * This can be used to appropriately tint toolbar backgrounds. Also can emit the state change
 * for other purposes.
 */
open class Material3OnScrollHelper(
  private val activity: FragmentActivity,
  private val setStatusBarColor: (Int) -> Unit = { WindowUtil.setStatusBarColor(activity.window, it) },
  getStatusBarColor: () -> Int = { WindowUtil.getStatusBarColor(activity.window) },
  private val setChatFolderColor: (Int) -> Unit = {},
  private val onSetToolbarColor: (Int) -> Unit = {},
  private val views: List<View> = emptyList(),
  private val viewStubs: List<Stub<out View>> = emptyList(),
  lifecycleOwner: LifecycleOwner
) {

  companion object {
    /**
     * Override for our single java usage.
     */
    @JvmStatic
    fun create(activity: FragmentActivity, toolbar: View): Material3OnScrollHelper {
      return Material3OnScrollHelper(activity = activity, views = listOf(toolbar), lifecycleOwner = activity)
    }
  }

  open val activeColorSet: ColorSet = ColorSet(
    toolbarColorRes = R.color.signal_colorSurface2,
    statusBarColorRes = R.color.signal_colorSurface2,
    chatFolderColorRes = R.color.signal_colorBackground
  )
  open val inactiveColorSet: ColorSet = ColorSet(
    toolbarColorRes = R.color.signal_colorBackground,
    statusBarColorRes = R.color.signal_colorBackground,
    chatFolderColorRes = R.color.signal_colorSurface2
  )

  protected var previousStatusBarColor: Int = getStatusBarColor()

  private var animator: ValueAnimator? = null
  private var active: Boolean? = null

  init {
    lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onResume(owner: LifecycleOwner) {
        setColorImmediate()
      }

      override fun onDestroy(owner: LifecycleOwner) {
        animator?.cancel()
        setStatusBarColor(previousStatusBarColor)
      }
    })
  }

  fun attach(nestedScrollView: NestedScrollView) {
    nestedScrollView.setOnScrollChangeListener(
      OnScrollListener().apply {
        onScrollChange(nestedScrollView, 0, 0, 0, 0)
      }
    )
  }

  fun attach(recyclerView: RecyclerView) {
    recyclerView.addOnScrollListener(
      OnScrollListener().apply {
        onScrolled(recyclerView, 0, 0)
      }
    )
  }

  fun attach(appBarLayout: AppBarLayout) {
    appBarLayout.addOnOffsetChangedListener(
      OnScrollListener().apply {
        onOffsetChanged(appBarLayout, 0)
      }
    )
  }

  /**
   * Cancels any currently running scroll animation and sets the color immediately.
   */
  fun setColorImmediate() {
    if (active == null) {
      return
    }

    animator?.cancel()
    val colorSet = if (active == true) activeColorSet else inactiveColorSet
    setToolbarColor(ContextCompat.getColor(activity, colorSet.toolbarColorRes))
    setStatusBarColor(ContextCompat.getColor(activity, colorSet.statusBarColorRes))
    setChatFolderColor(ContextCompat.getColor(activity, colorSet.chatFolderColorRes))
  }

  private fun updateActiveState(isActive: Boolean) {
    if (active == isActive) {
      return
    }

    val hadActiveState = active != null
    active = isActive

    views.forEach { it.isActivated = isActive }
    viewStubs.filter { it.resolved() }.forEach { it.get().isActivated = isActive }

    if (animator?.isRunning == true) {
      animator?.reverse()
    } else {
      val startColorSet = if (isActive) inactiveColorSet else activeColorSet
      val endColorSet = if (isActive) activeColorSet else inactiveColorSet

      if (hadActiveState) {
        val startToolbarColor = ContextCompat.getColor(activity, startColorSet.toolbarColorRes)
        val endToolbarColor = ContextCompat.getColor(activity, endColorSet.toolbarColorRes)
        val startStatusBarColor = ContextCompat.getColor(activity, startColorSet.statusBarColorRes)
        val endStatusBarColor = ContextCompat.getColor(activity, endColorSet.statusBarColorRes)
        val startChatFolderColor = ContextCompat.getColor(activity, startColorSet.chatFolderColorRes)
        val endChatFolderColor = ContextCompat.getColor(activity, endColorSet.chatFolderColorRes)

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
          duration = 200
          addUpdateListener {
            setToolbarColor(ArgbEvaluatorCompat.getInstance().evaluate(it.animatedFraction, startToolbarColor, endToolbarColor))
            setStatusBarColor(ArgbEvaluatorCompat.getInstance().evaluate(it.animatedFraction, startStatusBarColor, endStatusBarColor))
            setChatFolderColor(ArgbEvaluatorCompat.getInstance().evaluate(it.animatedFraction, startChatFolderColor, endChatFolderColor))
          }
          start()
        }
      } else {
        setColorImmediate()
      }
    }
  }

  private fun setToolbarColor(@ColorInt color: Int) {
    views.forEach { it.setBackgroundColor(color) }
    viewStubs.filter { it.resolved() }.forEach { it.get().setBackgroundColor(color) }
    onSetToolbarColor(color)
  }

  private inner class OnScrollListener : RecyclerView.OnScrollListener(), AppBarLayout.OnOffsetChangedListener, NestedScrollView.OnScrollChangeListener {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      updateActiveState(recyclerView.canScrollVertically(-1))
    }

    override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
      updateActiveState(verticalOffset != 0)
    }

    override fun onScrollChange(v: NestedScrollView, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
      updateActiveState(v.canScrollVertically(-1))
    }
  }

  /**
   * A pair of colors tied to a specific state.
   */
  data class ColorSet(
    @ColorRes val toolbarColorRes: Int,
    @ColorRes val statusBarColorRes: Int,
    @ColorRes val chatFolderColorRes: Int
  ) {
    constructor(@ColorRes color: Int) : this(color, color)
    constructor(@ColorRes toolbarColorRes: Int, @ColorRes statusBarColorRes: Int) : this(toolbarColorRes, statusBarColorRes, toolbarColorRes)
  }
}
