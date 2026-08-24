package org.thoughtcrime.securesms.components.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.signal.core.ui.logging.LoggingFragment
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsNavHostFragment
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

/**
 * The DSL API can be completely replaced by compose.
 * See ComposeFragment or ComposeBottomSheetFragment for an alternative to this API
 */
abstract class DSLSettingsFragment(
  @StringRes private val titleId: Int = -1,
  @MenuRes private val menuId: Int = -1,
  @LayoutRes layoutId: Int = R.layout.dsl_settings_fragment,
  protected var layoutManagerProducer: (Context) -> RecyclerView.LayoutManager = { context -> LinearLayoutManager(context) }
) : LoggingFragment(layoutId) {

  protected var recyclerView: RecyclerView? = null
    private set

  private var toolbar: Toolbar? = null

  /**
   * Set by layouts that anchor the list to the top of the toolbar rather than below it. Those lists scroll
   * behind the toolbar, so they have to carry the status bar inset and the toolbar height themselves.
   */
  protected open val listScrollsBehindToolbar: Boolean = false

  /**
   * Set by layouts with text input inside the list. The host windows are edge-to-edge, so `adjustResize` no
   * longer shrinks the window when the keyboard opens; the list has to carry the IME inset itself to keep the
   * focused field visible.
   */
  protected open val listAvoidsKeyboard: Boolean = false

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

    applyEdgeToEdgeInsets()

    when (settingsAdapter) {
      is ConcatAdapter -> bindAdapters(settingsAdapter)
      is MappingAdapter -> bindAdapter(settingsAdapter)
      else -> error("Illegal adapter subtype: ${settingsAdapter.javaClass.simpleName}")
    }
  }

  /**
   * All host windows are edge-to-edge — activities centrally via [org.thoughtcrime.securesms.BaseActivity],
   * and the full-screen dialog hosts ([org.thoughtcrime.securesms.components.WrapperDialogFragment],
   * [org.thoughtcrime.securesms.stories.settings.create.CreateStoryFlowDialogFragment]) via their own
   * `enableEdgeToEdge` calls — so the toolbar tint must extend behind the (transparent) status bar and the
   * list must scroll clear of the navigation bar. Skipped only for fragments embedded directly in
   * [MainActivity]'s Compose scaffolding, which insets those containers itself — except the
   * conversation-settings detail entry ([ConversationSettingsNavHostFragment]), which leaves insets to these
   * fragments so the toolbar scroll tint can reach behind the status bar.
   */
  private fun applyEdgeToEdgeInsets() {
    if (isInsetByMainActivityHost()) {
      return
    }

    toolbar?.let { toolbar ->
      toolbar.updateLayoutParams { height = ViewGroup.LayoutParams.WRAP_CONTENT }
      SystemWindowInsetsSetter.attach(toolbar, viewLifecycleOwner, WindowInsetsCompat.Type.statusBars())
    }

    recyclerView?.let { recycler ->
      val insetTypes = WindowInsetsCompat.Type.navigationBars() or
        (if (listScrollsBehindToolbar) WindowInsetsCompat.Type.statusBars() else 0) or
        (if (listAvoidsKeyboard) WindowInsetsCompat.Type.ime() else 0)

      if (listScrollsBehindToolbar) {
        recycler.updatePadding(top = recycler.paddingTop + resources.getDimensionPixelSize(R.dimen.signal_m3_toolbar_height))
      }

      recycler.clipToPadding = false
      SystemWindowInsetsSetter.attach(recycler, viewLifecycleOwner, insetTypes)
    }
  }

  private fun isInsetByMainActivityHost(): Boolean {
    if (activity !is MainActivity) {
      return false
    }
    return generateSequence(parentFragment) { it.parentFragment }.none { it is ConversationSettingsNavHostFragment }
  }

  open fun getMaterial3OnScrollHelper(toolbar: Toolbar?): Material3OnScrollHelper? {
    if (toolbar == null) {
      return null
    }

    return Material3OnScrollHelper(
      activity = requireActivity(),
      views = listOf(toolbar),
      lifecycleOwner = viewLifecycleOwner
    )
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
