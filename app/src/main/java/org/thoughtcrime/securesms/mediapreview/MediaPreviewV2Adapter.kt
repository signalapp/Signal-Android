package org.thoughtcrime.securesms.mediapreview

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.adapter.StableIdGenerator

class MediaPreviewV2Adapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
  private var items: List<Attachment> = listOf()
  private val stableIdGenerator = StableIdGenerator<Attachment>()

  override fun getItemCount(): Int {
    return items.count()
  }

  override fun getItemId(position: Int): Long {
    return stableIdGenerator.getId(items[position])
  }

  override fun createFragment(position: Int): Fragment {
    val attachment: Attachment = items[position]

    val contentType = attachment.contentType
    val args = bundleOf(
      MediaPreviewFragment.DATA_URI to attachment.uri,
      MediaPreviewFragment.DATA_CONTENT_TYPE to contentType,
      MediaPreviewFragment.DATA_SIZE to attachment.size,
      MediaPreviewFragment.AUTO_PLAY to true,
      MediaPreviewFragment.VIDEO_GIF to attachment.isVideoGif,
    )
    val fragment = if (MediaUtil.isVideo(contentType)) {
      VideoMediaPreviewFragment()
    } else if (MediaUtil.isImageType(contentType)) {
      ImageMediaPreviewFragment()
    } else {
      throw AssertionError("Unexpected media type: $contentType")
    }

    fragment.arguments = args

    return fragment
  }

  fun getFragmentTag(position: Int): String? {
    if (position < 0 || position > itemCount) {
      return null
    }

    return "f${getItemId(position)}"
  }

  fun findItemPosition(media: Media): Int {
    return items.indexOfFirst { it.uri == media.uri }
  }

  fun updateBackingItems(newItems: Collection<Attachment>) {
    if (newItems != items) {
      items = newItems.toList()
      notifyDataSetChanged()
    }
  }
}
