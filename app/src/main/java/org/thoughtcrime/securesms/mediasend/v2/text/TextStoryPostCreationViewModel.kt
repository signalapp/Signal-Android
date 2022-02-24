package org.thoughtcrime.securesms.mediasend.v2.text

import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Bundle
import androidx.annotation.ColorInt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.fonts.Fonts
import org.thoughtcrime.securesms.fonts.TextFont
import org.thoughtcrime.securesms.util.FutureTaskListener
import org.thoughtcrime.securesms.util.livedata.Store
import java.util.Locale
import java.util.concurrent.ExecutionException

class TextStoryPostCreationViewModel : ViewModel() {

  private val store = Store(TextStoryPostCreationState())
  private val textFontSubject: Subject<TextFont> = BehaviorSubject.create()
  private val disposables = CompositeDisposable()

  private val internalThumbnail = MutableLiveData<Bitmap>()
  val thumbnail: LiveData<Bitmap> = internalThumbnail

  private val internalTypeface = MutableLiveData<Typeface>()

  val state: LiveData<TextStoryPostCreationState> = store.stateLiveData
  val typeface: LiveData<Typeface> = internalTypeface

  init {
    textFontSubject.onNext(store.state.textFont)

    textFontSubject
      .observeOn(Schedulers.io())
      .distinctUntilChanged()
      .map { Fonts.resolveFont(ApplicationDependencies.getApplication(), Locale.getDefault(), it) }
      .switchMap {
        when (it) {
          is Fonts.FontResult.Async -> asyncFontEmitter(it)
          is Fonts.FontResult.Immediate -> Observable.just(it.typeface)
        }
      }
      .subscribeOn(Schedulers.io())
      .subscribe {
        internalTypeface.postValue(it)
      }
  }

  fun setBitmap(bitmap: Bitmap) {
    internalThumbnail.value?.recycle()
    internalThumbnail.value = bitmap
  }

  private fun asyncFontEmitter(async: Fonts.FontResult.Async): Observable<Typeface> {
    return Observable.create {
      it.onNext(async.placeholder)

      val listener = object : FutureTaskListener<Typeface> {
        override fun onSuccess(result: Typeface) {
          it.onNext(result)
          it.onComplete()
        }

        override fun onFailure(exception: ExecutionException?) {
          Log.w(TAG, "Failed to load remote font.", exception)
          it.onComplete()
        }
      }

      it.setCancellable {
        async.future.removeListener(listener)
      }

      async.future.addListener(listener)
    }
  }

  override fun onCleared() {
    disposables.clear()
    thumbnail.value?.recycle()
  }

  fun saveToInstanceState(outState: Bundle) {
    outState.putParcelable(TEXT_STORY_INSTANCE_STATE, store.state)
  }

  fun restoreFromInstanceState(inState: Bundle) {
    if (inState.containsKey(TEXT_STORY_INSTANCE_STATE)) {
      val state: TextStoryPostCreationState = inState.getParcelable(TEXT_STORY_INSTANCE_STATE)!!
      textFontSubject.onNext(store.state.textFont)
      store.update { state }
    }
  }

  fun getBody(): CharSequence {
    return store.state.body
  }

  @ColorInt
  fun getTextColor(): Int {
    return store.state.textColor
  }

  fun setTextColor(@ColorInt textColor: Int) {
    store.update { it.copy(textColor = textColor) }
  }

  fun setBody(body: CharSequence) {
    store.update { it.copy(body = body) }
  }

  fun setAlignment(textAlignment: TextAlignment) {
    store.update { it.copy(textAlignment = textAlignment) }
  }

  fun setTextScale(scale: Int) {
    store.update { it.copy(textScale = scale) }
  }

  fun setTextColorStyle(textColorStyle: TextColorStyle) {
    store.update { it.copy(textColorStyle = textColorStyle) }
  }

  fun setTextFont(textFont: TextFont) {
    textFontSubject.onNext(textFont)
    store.update { it.copy(textFont = textFont) }
  }

  fun cycleBackgroundColor() {
    store.update { it.copy(backgroundColor = TextStoryBackgroundColors.cycleBackgroundColor(it.backgroundColor)) }
  }

  fun setLinkPreview(url: String) {
    store.update { it.copy(linkPreviewUri = url) }
  }

  companion object {
    private val TAG = Log.tag(TextStoryPostCreationViewModel::class.java)
    private const val TEXT_STORY_INSTANCE_STATE = "text.story.instance.state"
  }
}
