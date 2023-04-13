package org.thoughtcrime.securesms.conversation.ui.inlinequery

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.DimensionUnit
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.components.ComposeText
import org.thoughtcrime.securesms.util.adapter.mapping.AnyMappingModel
import org.thoughtcrime.securesms.util.doOnEachLayout

/**
 * Controller for inline search results.
 */
class InlineQueryResultsController(
  private val context: Context,
  private val viewModel: InlineQueryViewModel,
  private val anchor: View,
  private val container: ViewGroup,
  editText: ComposeText,
  lifecycleOwner: LifecycleOwner
) : InlineQueryResultsPopup.Callback {

  private val lifecycleDisposable: LifecycleDisposable = LifecycleDisposable()
  private var popup: InlineQueryResultsPopup? = null
  private var previousResults: List<AnyMappingModel>? = null
  private var canShow: Boolean = false
  private var isLandscape: Boolean = false

  init {
    lifecycleDisposable.bindTo(lifecycleOwner)

    lifecycleDisposable += viewModel.results
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { updateList(it) }

    lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onDestroy(owner: LifecycleOwner) {
        dismiss()
      }
    })

    editText.addOnFocusChangeListener { _, hasFocus ->
      canShow = hasFocus
      updateList(previousResults ?: emptyList())
    }

    anchor.doOnEachLayout { popup?.updateWithAnchor() }
  }

  override fun onSelection(model: AnyMappingModel) {
    viewModel.onSelection(model)
  }

  override fun onDismiss() {
    popup = null
  }

  fun onOrientationChange(isLandscape: Boolean) {
    this.isLandscape = isLandscape

    if (isLandscape) {
      dismiss()
    } else {
      updateList(previousResults ?: emptyList())
    }
  }

  private fun updateList(results: List<AnyMappingModel>) {
    previousResults = results
    if (results.isEmpty() || !canShow || isLandscape) {
      dismiss()
    } else if (popup != null) {
      popup?.setResults(results)
    } else {
      popup = InlineQueryResultsPopup(
        anchor = anchor,
        container = container,
        results = results,
        baseOffsetX = DimensionUnit.DP.toPixels(16f).toInt(),
        callback = this
      ).show()
    }
  }

  private fun dismiss() {
    popup?.dismiss()
    popup = null
  }
}
