package org.thoughtcrime.securesms.mediapreview

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.MediaDatabase
import org.thoughtcrime.securesms.util.AttachmentUtil
import org.thoughtcrime.securesms.util.rx.RxStore

class MediaPreviewV2ViewModel : ViewModel() {
  private val TAG = Log.tag(MediaPreviewV2ViewModel::class.java)
  private val store = RxStore(MediaPreviewV2State())
  private val disposables = CompositeDisposable()
  private val repository: MediaPreviewRepository = MediaPreviewRepository()

  val state: Flowable<MediaPreviewV2State> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  fun fetchAttachments(startingUri: Uri, threadId: Long, sorting: MediaDatabase.Sorting) {
    disposables += store.update(repository.getAttachments(startingUri, threadId, sorting)) {
      result: MediaPreviewRepository.Result, oldState: MediaPreviewV2State ->
      oldState.copy(
        position = result.initialPosition,
        mediaRecords = result.records,
        loadState = MediaPreviewV2State.LoadState.READY,
      )
    }
  }

  fun setShowThread(value: Boolean) {
    store.update { oldState ->
      oldState.copy(showThread = value)
    }
  }

  fun setCurrentPage(position: Int) {
    store.update { oldState ->
      oldState.copy(position = position, loadState = MediaPreviewV2State.LoadState.LOADED)
    }
  }

  fun deleteItem(context: Context, attachment: DatabaseAttachment, onSuccess: Consumer<in Unit>, onError: Consumer<in Throwable>) {
    disposables += Single.fromCallable { AttachmentUtil.deleteAttachment(context.applicationContext, attachment) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(onSuccess, onError)
  }

  override fun onCleared() {
    disposables.dispose()
    store.dispose()
  }
}
