package org.thoughtcrime.securesms.components.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EdgeEffect
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import java.lang.UnsupportedOperationException

/**
 * The DSL API can be completely replaced by compose.
 * See ComposeFragment or ComposeBottomSheetFragment for an alternative to this API
 */
abstract class DSLSettingsFragment(
  @StringRes private val titleId: Int = -1,
  @MenuRes private val menuId: Int = -1,
  @LayoutRes layoutId: Int = R.layout.dsl_settings_fragment,
  protected var layoutManagerProducer: (Context) -> RecyclerView.LayoutManager = { context -> LinearLayoutManager(context) }
) : Fragment(layoutId) {

  protected var recyclerView: RecyclerView? = null
    private set

  private var toolbar: Toolbar? = null

  @CallSuper
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    toolbar = view.findViewById(R.id.toolbar)

    if (titleId != -1) {
      toolbar?.setTitle(titleId)
    }

    toolbar?.setNavigationOnClickListener {
      onToolbarNavigationClicked()
    }

    if (menuId != -1) {
      toolbar?.inflateMenu(menuId)
      toolbar?.setOnMenuItemClickListener { onOptionsItemSelected(it) }
    }

    val config = ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build()
    val settingsAdapters = createAdapters()
    val settingsAdapter: RecyclerView.Adapter<out RecyclerView.ViewHolder> = when {
      settingsAdapters.size > 1 -> ConcatAdapter(config, *settingsAdapters)
      settingsAdapters.size == 1 -> settingsAdapters.first()
      else -> error("Require one or more settings adapters.")
    }

    recyclerView = view.findViewById<RecyclerView>(R.id.recycler).apply {
      edgeEffectFactory = EdgeEffectFactory()
      layoutManager = layoutManagerProducer(requireContext())
      adapter = settingsAdapter

      getMaterial3OnScrollHelper(toolbar)?.let {
        it.attach(this)
      }
    }

    when (settingsAdapter) {
      is ConcatAdapter -> bindAdapters(settingsAdapter)
      is MappingAdapter -> bindAdapter(settingsAdapter)
      else -> error("Illegal adapter subtype: ${settingsAdapter.javaClass.simpleName}")
    }
  }

  open fun getMaterial3OnScrollHelper(toolbar: Toolbar?): Material3OnScrollHelper? {
    if (toolbar == null) {
      return null
    }

    return Material3OnScrollHelper(requireActivity(), toolbar)
  }

  open fun onToolbarNavigationClicked() {
    requireActivity().onBackPressed()
  }

  override fun onDestroyView() {
    recyclerView = null
    toolbar = null
    super.onDestroyView()
  }

  fun setTitle(@StringRes resId: Int) {
    toolbar?.setTitle(resId)
  }

  fun setTitle(title: CharSequence) {
    toolbar?.title = title
  }

  open fun createAdapters(): Array<MappingAdapter> {
    return arrayOf(DSLSettingsAdapter())
  }

  open fun bindAdapter(adapter: MappingAdapter) {
    throw UnsupportedOperationException("This method is not implemented.")
  }

  open fun bindAdapters(adapter: ConcatAdapter) {
    throw UnsupportedOperationException("This method is not implemented.")
  }

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
}
