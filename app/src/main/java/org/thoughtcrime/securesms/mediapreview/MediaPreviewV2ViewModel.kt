package org.thoughtcrime.securesms.mediapreview

import android.content.Context
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.MediaDatabase
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.util.AttachmentUtil
import org.thoughtcrime.securesms.util.rx.RxStore
import java.util.Optional

class MediaPreviewV2ViewModel : ViewModel() {
  private val TAG = Log.tag(MediaPreviewV2ViewModel::class.java)
  private val store = RxStore(MediaPreviewV2State())
  private val disposables = CompositeDisposable()
  private val repository: MediaPreviewRepository = MediaPreviewRepository()

  val state: Flowable<MediaPreviewV2State> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  fun fetchAttachments(startingAttachmentId: AttachmentId, threadId: Long, sorting: MediaDatabase.Sorting, forceRefresh: Boolean = false) {
    if (store.state.loadState == MediaPreviewV2State.LoadState.INIT || forceRefresh) {
      disposables += store.update(repository.getAttachments(startingAttachmentId, threadId, sorting)) { result: MediaPreviewRepository.Result, oldState: MediaPreviewV2State ->
        val albums = result.records.fold(mutableMapOf()) { acc: MutableMap<Long, MutableList<Media>>, mediaRecord: MediaDatabase.MediaRecord ->
          val attachment = mediaRecord.attachment
          if (attachment != null) {
            val convertedMedia = mediaRecord.toMedia() ?: return@fold acc
            acc.getOrPut(attachment.mmsId) { mutableListOf() }.add(convertedMedia)
          }
          acc
        }
        if (oldState.leftIsRecent) {
          oldState.copy(
            position = result.initialPosition,
            mediaRecords = result.records,
            albums = albums,
            loadState = MediaPreviewV2State.LoadState.DATA_LOADED,
          )
        } else {
          oldState.copy(
            position = result.records.size - result.initialPosition - 1,
            mediaRecords = result.records.reversed(),
            albums = albums.mapValues { it.value.reversed() },
            loadState = MediaPreviewV2State.LoadState.DATA_LOADED,
          )
        }
      }
    }
  }

  fun initialize(showThread: Boolean, allMediaInAlbumRail: Boolean, leftIsRecent: Boolean) {
    if (store.state.loadState == MediaPreviewV2State.LoadState.INIT) {
      store.update { oldState ->
        oldState.copy(showThread = showThread, allMediaInAlbumRail = allMediaInAlbumRail, leftIsRecent = leftIsRecent)
      }
    }
  }

  fun setCurrentPage(position: Int) {
    store.update { oldState ->
      oldState.copy(position = position)
    }
  }

  fun setMediaReady() {
    store.update { oldState ->
      oldState.copy(loadState = MediaPreviewV2State.LoadState.MEDIA_READY)
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

  fun onDestroyView() {
    store.update { oldState ->
      oldState.copy(loadState = MediaPreviewV2State.LoadState.DATA_LOADED)
    }
  }
}

fun MediaDatabase.MediaRecord.toMedia(): Media? {
  val attachment = this.attachment
  val uri = attachment?.uri
  if (attachment == null || uri == null) {
    return null
  }

  return Media(
    uri,
    this.contentType,
    this.date,
    attachment.width,
    attachment.height,
    attachment.size,
    0,
    attachment.isBorderless,
    attachment.isVideoGif,
    Optional.empty(),
    Optional.ofNullable(attachment.caption),
    Optional.empty()
  )
}
