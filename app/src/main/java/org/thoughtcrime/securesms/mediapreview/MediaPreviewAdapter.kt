package org.thoughtcrime.securesms.mediapreview

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.signal.core.models.media.Media
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.adapter.StableIdGenerator

class MediaPreviewAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
  private val TAG = Log.tag(MediaPreviewAdapter::class.java)
  private var items: List<Attachment> = listOf()
  private val stableIdGenerator = StableIdGenerator<Attachment>()
  private val currentIdSet: HashSet<Long> = HashSet()

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
      MediaPreviewPageFragment.DATA_URI to attachment.displayUri,
      MediaPreviewPageFragment.DATA_CONTENT_TYPE to contentType,
      MediaPreviewPageFragment.DATA_SIZE to attachment.size,
      MediaPreviewPageFragment.AUTO_PLAY to attachment.videoGif,
      MediaPreviewPageFragment.VIDEO_GIF to attachment.videoGif
    )
    val fragment = if (MediaUtil.isVideo(contentType)) {
      VideoMediaPreviewPageFragment()
    } else if (MediaUtil.isImageType(contentType)) {
      ImageMediaPreviewPageFragment()
    } else {
      throw AssertionError("Unexpected media type: $contentType")
    }

    fragment.arguments = args

    if (attachment is DatabaseAttachment) {
      AttachmentDownloadJob.downloadAttachmentIfNeeded(attachment)
    }

    return fragment
  }

  override fun containsItem(itemId: Long): Boolean {
    return currentIdSet.contains(itemId)
  }

  fun getFragmentTag(position: Int): String? {
    if (items.isEmpty() || position < 0 || position > itemCount) {
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
      currentIdSet.clear()
      items.forEach {
        currentIdSet.add(stableIdGenerator.getId(it))
      }
      notifyDataSetChanged()
    }
  }
}
