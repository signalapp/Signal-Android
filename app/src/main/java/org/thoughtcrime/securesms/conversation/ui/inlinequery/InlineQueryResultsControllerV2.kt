package org.thoughtcrime.securesms.conversation.ui.inlinequery

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowSizeClass
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.DimensionUnit
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ComposeText
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerFragmentV2
import org.thoughtcrime.securesms.util.adapter.mapping.AnyMappingModel
import org.thoughtcrime.securesms.util.doOnEachLayout

/**
 * Controller for inline search results.
 */
class InlineQueryResultsControllerV2(
  private val parentFragment: Fragment,
  private val threadId: Long,
  private val viewModel: InlineQueryViewModelV2,
  private val anchor: View,
  private val container: ViewGroup,
  editText: ComposeText
) : InlineQueryResultsPopup.Callback {

  companion object {
    private const val MENTION_TAG = "mention_fragment_tag"
  }

  private val lifecycleDisposable: LifecycleDisposable = LifecycleDisposable()
  private var emojiPopup: InlineQueryResultsPopup? = null
  private var mentionFragment: MentionsPickerFragmentV2? = null
  private var previousResults: InlineQueryViewModelV2.Results? = null
  private var canShow: Boolean = false
  private var shouldHideForWindowSizeClass: Boolean = false

  init {
    lifecycleDisposable.bindTo(parentFragment.viewLifecycleOwner)

    viewModel
      .results
      .subscribeBy { updateList(it) }
      .addTo(lifecycleDisposable)

    parentFragment.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onDestroy(owner: LifecycleOwner) {
        dismiss()
      }
    })

    canShow = editText.hasFocus()
    editText.addOnFocusChangeListener { _, hasFocus ->
      canShow = hasFocus
      updateList(previousResults ?: InlineQueryViewModelV2.None)
    }

    anchor.doOnEachLayout { emojiPopup?.updateWithAnchor() }
  }

  override fun onSelection(model: AnyMappingModel) {
    viewModel.onSelection(model)
  }

  override fun onDismiss() {
    emojiPopup = null
  }

  fun onWindowSizeClassChanged(windowSizeClass: WindowSizeClass) {
    this.shouldHideForWindowSizeClass = windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT

    if (shouldHideForWindowSizeClass) {
      dismiss()
    } else {
      updateList(previousResults ?: InlineQueryViewModelV2.None)
    }
  }

  private fun updateList(results: InlineQueryViewModelV2.Results) {
    previousResults = results
    if (results is InlineQueryViewModelV2.None || !canShow || shouldHideForWindowSizeClass) {
      dismiss()
    } else if (results is InlineQueryViewModelV2.EmojiResults) {
      showEmojiPopup(results)
    } else if (results is InlineQueryViewModelV2.MentionResults) {
      showMentionsPickerFragment(results)
    }
  }

  private fun showEmojiPopup(results: InlineQueryViewModelV2.EmojiResults) {
    if (emojiPopup != null) {
      emojiPopup?.setResults(results.results)
    } else {
      emojiPopup = InlineQueryResultsPopup(
        anchor = anchor,
        container = container,
        results = results.results,
        baseOffsetX = DimensionUnit.DP.toPixels(16f).toInt(),
        callback = this
      ).show()
    }
  }

  private fun showMentionsPickerFragment(results: InlineQueryViewModelV2.MentionResults) {
    if (mentionFragment == null) {
      mentionFragment = parentFragment.childFragmentManager.findFragmentByTag(MENTION_TAG) as? MentionsPickerFragmentV2
      if (mentionFragment == null) {
        mentionFragment = MentionsPickerFragmentV2.create(threadId)
        parentFragment.childFragmentManager.commit {
          replace(R.id.mention_fragment_container, mentionFragment!!)
          runOnCommit { mentionFragment!!.updateList(results.results) }
        }
      }
    } else {
      parentFragment.childFragmentManager.commit {
        show(mentionFragment!!)
      }
    }
  }

  private fun dismiss() {
    emojiPopup?.dismiss()
    emojiPopup = null

    mentionFragment?.let {
      parentFragment.childFragmentManager.commit(allowStateLoss = true) {
        hide(it)
      }
    }
  }
}
