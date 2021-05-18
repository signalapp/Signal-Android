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
  @StringRes private val titleId: Int,
  @MenuRes private val menuId: Int = -1,
  @LayoutRes layoutId: Int = R.layout.dsl_settings_fragment
) : Fragment(layoutId) {

  private lateinit var recyclerView: RecyclerView
  private lateinit var toolbarShadowHelper: ToolbarShadowHelper

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    val toolbarShadow: View = view.findViewById(R.id.toolbar_shadow)

    toolbar.setTitle(titleId)

    toolbar.setNavigationOnClickListener {
      requireActivity().onBackPressed()
    }

    if (menuId != -1) {
      toolbar.inflateMenu(menuId)
      toolbar.setOnMenuItemClickListener { onOptionsItemSelected(it) }
    }

    recyclerView = view.findViewById(R.id.recycler)
    recyclerView.edgeEffectFactory = EdgeEffectFactory()
    toolbarShadowHelper = ToolbarShadowHelper(toolbarShadow)
    val adapter = DSLSettingsAdapter()

    recyclerView.adapter = adapter
    recyclerView.addOnScrollListener(toolbarShadowHelper)

    bindAdapter(adapter)
  }

  override fun onResume() {
    super.onResume()
    toolbarShadowHelper.onScrolled(recyclerView, 0, 0)
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

  class ToolbarShadowHelper(private val toolbarShadow: View) : RecyclerView.OnScrollListener() {

    private var lastAnimationState = ToolbarAnimationState.NONE

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      val newAnimationState =
        if (recyclerView.canScrollVertically(-1)) ToolbarAnimationState.SHOW else ToolbarAnimationState.HIDE

      if (newAnimationState == lastAnimationState) {
        return
      }

      when (newAnimationState) {
        ToolbarAnimationState.NONE -> throw AssertionError()
        ToolbarAnimationState.HIDE -> toolbarShadow.animate().alpha(0f)
        ToolbarAnimationState.SHOW -> toolbarShadow.animate().alpha(1f)
      }

      lastAnimationState = newAnimationState
    }
  }

  private enum class ToolbarAnimationState {
    NONE,
    HIDE,
    SHOW
  }
}
