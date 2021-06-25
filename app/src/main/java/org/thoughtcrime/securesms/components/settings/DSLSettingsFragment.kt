package org.thoughtcrime.securesms.components.settings

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EdgeEffect
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R

abstract class DSLSettingsFragment(
  @StringRes private val titleId: Int = -1,
  @MenuRes private val menuId: Int = -1,
  @LayoutRes layoutId: Int = R.layout.dsl_settings_fragment
) : Fragment(layoutId) {

  private lateinit var recyclerView: RecyclerView
  private lateinit var scrollAnimationHelper: OnScrollAnimationHelper

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    val toolbarShadow: View = view.findViewById(R.id.toolbar_shadow)

    if (titleId != -1) {
      toolbar.setTitle(titleId)
    }

    toolbar.setNavigationOnClickListener {
      requireActivity().onBackPressed()
    }

    if (menuId != -1) {
      toolbar.inflateMenu(menuId)
      toolbar.setOnMenuItemClickListener { onOptionsItemSelected(it) }
    }

    recyclerView = view.findViewById(R.id.recycler)
    recyclerView.edgeEffectFactory = EdgeEffectFactory()
    scrollAnimationHelper = getOnScrollAnimationHelper(toolbarShadow)
    val adapter = DSLSettingsAdapter()

    recyclerView.adapter = adapter
    recyclerView.addOnScrollListener(scrollAnimationHelper)

    bindAdapter(adapter)
  }

  protected open fun getOnScrollAnimationHelper(toolbarShadow: View): OnScrollAnimationHelper {
    return ToolbarShadowAnimationHelper(toolbarShadow)
  }

  abstract fun bindAdapter(adapter: DSLSettingsAdapter)

  private class EdgeEffectFactory : RecyclerView.EdgeEffectFactory() {
    override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
      return super.createEdgeEffect(view, direction).apply {
        if (Build.VERSION.SDK_INT > 21) {
          color =
            requireNotNull(ContextCompat.getColor(view.context, R.color.settings_ripple_color))
        }
      }
    }
  }

  abstract class OnScrollAnimationHelper : RecyclerView.OnScrollListener() {
    private var lastAnimationState = AnimationState.NONE

    protected open val duration: Long = 250L

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      val newAnimationState = getAnimationState(recyclerView)

      if (newAnimationState == lastAnimationState) {
        return
      }

      if (lastAnimationState == AnimationState.NONE) {
        setImmediateState(recyclerView)
        return
      }

      when (newAnimationState) {
        AnimationState.NONE -> throw AssertionError()
        AnimationState.HIDE -> hide(duration)
        AnimationState.SHOW -> show(duration)
      }

      lastAnimationState = newAnimationState
    }

    fun setImmediateState(recyclerView: RecyclerView) {
      val newAnimationState = getAnimationState(recyclerView)

      when (newAnimationState) {
        AnimationState.NONE -> throw AssertionError()
        AnimationState.HIDE -> hide(0L)
        AnimationState.SHOW -> show(0L)
      }

      lastAnimationState = newAnimationState
    }

    protected open fun getAnimationState(recyclerView: RecyclerView): AnimationState {
      return if (recyclerView.canScrollVertically(-1)) AnimationState.SHOW else AnimationState.HIDE
    }

    protected abstract fun show(duration: Long)

    protected abstract fun hide(duration: Long)

    enum class AnimationState {
      NONE,
      HIDE,
      SHOW
    }
  }

  open class ToolbarShadowAnimationHelper(private val toolbarShadow: View) : OnScrollAnimationHelper() {

    override fun show(duration: Long) {
      toolbarShadow.animate()
        .setDuration(duration)
        .alpha(1f)
    }

    override fun hide(duration: Long) {
      toolbarShadow.animate()
        .setDuration(duration)
        .alpha(0f)
    }
  }
}
