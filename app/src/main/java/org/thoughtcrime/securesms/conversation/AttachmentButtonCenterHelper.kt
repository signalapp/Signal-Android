package org.thoughtcrime.securesms.conversation

import android.view.View
import android.view.View.OnLayoutChangeListener
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.DimensionUnit
import org.signal.core.util.logging.Log

/**
 * Adds necessary padding to each side of the given ViewGroup in order to ensure that
 * if all buttons can fit in the visible real-estate as defined by the wrapper view,
 * then they are centered. However if there are too many buttons to fit on the screen,
 * then put a basic amount of padding on each side so that it looks nice when scrolling
 * to either end.
 */
class AttachmentButtonCenterHelper(val buttonHolder: View, val wrapper: View) {

  companion object {
    val TAG = Log.tag(AttachmentButtonCenterHelper::class)
    private val DEFAULT_PADDING = DimensionUnit.DP.toPixels(16f).toInt()
  }

  /** The wrapper width is the maximum size of the button holder before scrollbars appear. */
  private val wrapperWidthObservable: PublishSubject<Int> = PublishSubject.create()
  private val emitNewWrapperWidth = OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
    if (oldRight - oldLeft == right - left)
      return@OnLayoutChangeListener
    wrapperWidthObservable.onNext(right - left)
  }

  /** The "core width" of the button holder is the size of its contents. */
  private val coreWidthObservable: PublishSubject<Int> = PublishSubject.create()
  private val emitNewCoreWidth = OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
    val newCoreWidth = view.run { width - (paddingLeft + paddingRight) }
    coreWidthObservable.onNext(newCoreWidth)
  }

  private var listener: Disposable? = null

  fun attach() {
    wrapper.addOnLayoutChangeListener(emitNewWrapperWidth)
    buttonHolder.addOnLayoutChangeListener(emitNewCoreWidth)

    listener?.dispose()
    listener = Observable.combineLatest(wrapperWidthObservable, coreWidthObservable, ::Pair)
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { widths ->
        val wrapperWidth = widths.first
        val coreWidth = widths.second
        Log.d(TAG, "wrapperWidth: $wrapperWidth, coreWidth: $coreWidth")
        recenter(coreWidth, wrapperWidth)
      }
  }

  fun detach() {
    wrapper.removeOnLayoutChangeListener(emitNewWrapperWidth)
    buttonHolder.removeOnLayoutChangeListener(emitNewCoreWidth)
    listener?.dispose()
    listener = null
  }

  fun recenter(buttonHolderCoreWidth: Int, wrapperWidth: Int) {
    val extraSpace = wrapperWidth - buttonHolderCoreWidth
    val horizontalPadding = if (extraSpace >= 0)
      (extraSpace / 2f).toInt()
    else
      DEFAULT_PADDING
    Log.d(TAG, "will add $horizontalPadding px on either side")
    buttonHolder.apply { setPadding(horizontalPadding, paddingTop, horizontalPadding, paddingBottom) }
  }
}
