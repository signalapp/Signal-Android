package org.thoughtcrime.securesms.mediasend.v2.gallery

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.mediasend.MediaFolder
import org.thoughtcrime.securesms.util.livedata.Store

class MediaGalleryViewModel(bucketId: String?, bucketTitle: String?, private val repository: MediaGalleryRepository) : ViewModel() {

  private val store = Store(MediaGalleryState(bucketId, bucketTitle))

  val state: LiveData<MediaGalleryState> = store.stateLiveData

  init {
    loadItemsForBucket(bucketId, bucketTitle)
  }

  fun pop(): Boolean {
    return if (store.state.bucketId == null) {
      true
    } else {
      loadItemsForBucket(null, null)
      false
    }
  }

  fun setMediaFolder(mediaFolder: MediaFolder) {
    loadItemsForBucket(mediaFolder.bucketId, mediaFolder.title)
  }

  private fun loadItemsForBucket(bucketId: String?, bucketTitle: String?) {
    if (bucketId == null) {
      repository.getFolders { folders ->
        store.update { state ->
          state.copy(
            bucketId = bucketId,
            bucketTitle = bucketTitle,
            items = folders.map {
              MediaGallerySelectableItem.FolderModel(it)
            }
          )
        }
      }
    } else {
      repository.getMedia(bucketId) { media ->
        store.update { state ->
          state.copy(
            bucketId = bucketId,
            bucketTitle = bucketTitle,
            items = media.map {
              MediaGallerySelectableItem.FileModel(it, false, 0)
            }
          )
        }
      }
    }
  }

  class Factory(
    private val bucketId: String?,
    private val bucketTitle: String?,
    private val repository: MediaGalleryRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(MediaGalleryViewModel(bucketId, bucketTitle, repository)))
    }
  }
}
