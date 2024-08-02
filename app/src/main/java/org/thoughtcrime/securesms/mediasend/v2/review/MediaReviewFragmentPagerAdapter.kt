package org.thoughtcrime.securesms.mediasend.v2.review

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.v2.documents.MediaReviewDocumentPageFragment
import org.thoughtcrime.securesms.mediasend.v2.gif.MediaReviewGifPageFragment
import org.thoughtcrime.securesms.mediasend.v2.images.MediaReviewImagePageFragment
import org.thoughtcrime.securesms.mediasend.v2.videos.MediaReviewVideoPageFragment
import org.thoughtcrime.securesms.util.MediaUtil
import java.util.LinkedList

class MediaReviewFragmentPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

  private val mediaList: MutableList<Media> = mutableListOf()

  fun submitMedia(media: List<Media>) {
    val oldMedia: List<Media> = LinkedList(mediaList)
    mediaList.clear()
    mediaList.addAll(media)

    DiffUtil
      .calculateDiff(Callback(oldMedia, mediaList))
      .dispatchUpdatesTo(this)
  }

  override fun getItemId(position: Int): Long {
    if (position > mediaList.size || position < 0) {
      return RecyclerView.NO_ID
    }

    return mediaList[position].uri.hashCode().toLong()
  }

  override fun containsItem(itemId: Long): Boolean {
    return mediaList.any { it.uri.hashCode().toLong() == itemId }
  }

  override fun getItemCount(): Int = mediaList.size

  override fun createFragment(position: Int): Fragment {
    val mediaItem: Media = mediaList[position]

    return when {
      MediaUtil.isGif(mediaItem.contentType) -> MediaReviewGifPageFragment.newInstance(mediaItem.uri)
      MediaUtil.isImageType(mediaItem.contentType) -> MediaReviewImagePageFragment.newInstance(mediaItem.uri)
      MediaUtil.isVideoType(mediaItem.contentType) -> MediaReviewVideoPageFragment.newInstance(mediaItem.uri, mediaItem.isVideoGif)
      MediaUtil.isDocumentType(mediaItem.contentType) -> MediaReviewDocumentPageFragment.newInstance(mediaItem)
      else -> {
        throw UnsupportedOperationException("Can only render images and videos. Found mimetype: '" + mediaItem.contentType + "'")
      }
    }
  }

  private class Callback(
    private val oldList: List<Media>,
    private val newList: List<Media>
  ) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
      return oldList[oldItemPosition].uri == newList[newItemPosition].uri
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
      return oldList[oldItemPosition] == newList[newItemPosition]
    }
  }
}
