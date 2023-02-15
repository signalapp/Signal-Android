package org.thoughtcrime.securesms.components.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import androidx.annotation.Discouraged
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.WindowUtil

@Discouraged("The DSL API can be completely replaced by compose. See ComposeFragment or ComposeBottomSheetFragment for an alternative to this API")
abstract class DSLSettingsBottomSheetFragment(
  @LayoutRes private val layoutId: Int = R.layout.dsl_settings_bottom_sheet,
  val layoutManagerProducer: (Context) -> RecyclerView.LayoutManager = { context -> LinearLayoutManager(context) },
  override val peekHeightPercentage: Float = 1f
) : FixedRoundedCornerBottomSheetDialogFragment() {

  protected lateinit var recyclerView: RecyclerView
    private set

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(layoutId, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    recyclerView = view.findViewById(R.id.recycler)
    recyclerView.edgeEffectFactory = EdgeEffectFactory()
    val adapter = DSLSettingsAdapter()

    recyclerView.layoutManager = layoutManagerProducer(requireContext())
    recyclerView.adapter = adapter
    recyclerView.overScrollMode = RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS

    bindAdapter(adapter)
  }

  override fun onResume() {
    super.onResume()
    WindowUtil.initializeScreenshotSecurity(requireContext(), dialog!!.window!!)
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
}
