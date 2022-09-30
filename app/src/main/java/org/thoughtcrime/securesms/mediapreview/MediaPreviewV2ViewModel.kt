package org.thoughtcrime.securesms.mediapreview

import android.net.Uri
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.MediaDatabase
import org.thoughtcrime.securesms.util.rx.RxStore

class MediaPreviewV2ViewModel : ViewModel() {
  private val TAG = Log.tag(MediaPreviewV2ViewModel::class.java)
  private val store = RxStore(MediaPreviewV2State())
  private val disposables = CompositeDisposable()
  private val repository: MediaPreviewRepository = MediaPreviewRepository()

  val state: Flowable<MediaPreviewV2State> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  fun fetchAttachments(startingUri: Uri, threadId: Long, sorting: MediaDatabase.Sorting) {
    disposables += store.update(repository.getAttachments(startingUri, threadId, sorting)) { attachments, oldState ->
      oldState.copy(attachments = attachments, loadState = MediaPreviewV2State.LoadState.READY)
    }
  }

  override fun onCleared() {
    disposables.dispose()
  }
}
