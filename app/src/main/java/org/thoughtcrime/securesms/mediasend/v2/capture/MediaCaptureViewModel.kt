package org.thoughtcrime.securesms.mediasend.v2.capture

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.util.SingleLiveEvent
import org.thoughtcrime.securesms.util.livedata.Store
import org.whispersystems.libsignal.util.guava.Optional
import java.io.FileDescriptor

class MediaCaptureViewModel(private val repository: MediaCaptureRepository) : ViewModel() {

  private val store: Store<MediaCaptureState> = Store(MediaCaptureState())

  private val internalEvents: SingleLiveEvent<MediaCaptureEvent> = SingleLiveEvent()

  val events: LiveData<MediaCaptureEvent> = internalEvents

  init {
    repository.getMostRecentItem { media ->
      store.update { state ->
        state.copy(mostRecentMedia = media)
      }
    }
  }

  fun onImageCaptured(data: ByteArray, width: Int, height: Int) {
    repository.renderImageToMedia(data, width, height, this::onMediaRendered, this::onMediaRenderFailed)
  }

  fun onVideoCaptured(fd: FileDescriptor) {
    repository.renderVideoToMedia(fd, this::onMediaRendered, this::onMediaRenderFailed)
  }

  fun getMostRecentMedia(): LiveData<Optional<Media>> {
    return Transformations.map(store.stateLiveData) { Optional.fromNullable(it.mostRecentMedia) }
  }

  private fun onMediaRendered(media: Media) {
    internalEvents.postValue(MediaCaptureEvent.MediaCaptureRendered(media))
  }

  private fun onMediaRenderFailed() {
    internalEvents.postValue(MediaCaptureEvent.MediaCaptureRenderFailed)
  }

  class Factory(private val repository: MediaCaptureRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(MediaCaptureViewModel(repository)))
    }
  }
}
