package org.thoughtcrime.securesms.conversation.ui.mentions

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryViewModelV2
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.VibrateUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.viewholders.RecipientViewHolder

/**
 * Show inline query results for mentions in a group during message compose.
 */
class MentionsPickerFragmentV2 : LoggingFragment() {

  private val lifecycleDisposable: LifecycleDisposable = LifecycleDisposable()
  private val viewModel: InlineQueryViewModelV2 by viewModels(
    ownerProducer = { requireParentFragment() }
  )

  private lateinit var adapter: MentionsPickerAdapter
  private lateinit var list: RecyclerView
  private lateinit var behavior: BottomSheetBehavior<View>

  private val lockSheetAfterListUpdate = Runnable { behavior.setHideable(false) }
  private val handler = Handler(Looper.getMainLooper())

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    val view = inflater.inflate(R.layout.mentions_picker_fragment, container, false)
    list = view.findViewById(R.id.mentions_picker_list)
    behavior = BottomSheetBehavior.from(view.findViewById(R.id.mentions_picker_bottom_sheet))
    initializeBehavior()
    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    lifecycleDisposable.bindTo(viewLifecycleOwner)

    initializeList()
    viewModel
      .results
      .subscribeBy {
        if (it !is InlineQueryViewModelV2.MentionResults) {
          updateList(emptyList())
        } else {
          updateList(it.results)
        }
      }
      .addTo(
        lifecycleDisposable
      )

    viewModel
      .isMentionsShowing
      .subscribeBy { isShowing ->
        if (isShowing && VibrateUtil.isHapticFeedbackEnabled(requireContext())) {
          VibrateUtil.vibrateTick(requireContext())
        }
      }
      .addTo(lifecycleDisposable)
  }

  private fun initializeBehavior() {
    behavior.isHideable = true
    behavior.state = BottomSheetBehavior.STATE_HIDDEN
    behavior.addBottomSheetCallback(object : BottomSheetCallback() {
      override fun onStateChanged(bottomSheet: View, newState: Int) {
        if (newState == BottomSheetBehavior.STATE_HIDDEN) {
          adapter.submitList(emptyList())
        }
      }

      override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
    })
  }

  private fun initializeList() {
    adapter = MentionsPickerAdapter(MentionEventListener()) { updateBottomSheetBehavior(adapter.itemCount) }

    list.layoutManager = LinearLayoutManager(requireContext())
    list.adapter = adapter
    list.itemAnimator = null
  }

  fun updateList(mappingModels: List<MappingModel<*>>) {
    if (adapter.itemCount > 0 && mappingModels.isEmpty()) {
      updateBottomSheetBehavior(0)
    } else {
      adapter.submitList(mappingModels)
    }
  }

  private fun updateBottomSheetBehavior(count: Int) {
    val isShowing = count > 0
    if (isShowing) {
      list.scrollToPosition(0)
      behavior.state = BottomSheetBehavior.STATE_COLLAPSED
      handler.post(lockSheetAfterListUpdate)
    } else {
      handler.removeCallbacks(lockSheetAfterListUpdate)
      behavior.isHideable = true
      behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }
    viewModel.setIsMentionsShowing(isShowing)
  }

  private inner class MentionEventListener : RecipientViewHolder.EventListener<MentionViewState> {
    override fun onModelClick(model: MentionViewState) {
      viewModel.onSelection(model)
    }

    override fun onClick(recipient: Recipient) = Unit
  }
}
