package org.thoughtcrime.securesms.mediapreview

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.adapter.StableIdGenerator

class MediaPreviewV2Adapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
  private val TAG = Log.tag(MediaPreviewV2Adapter::class.java)
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
      MediaPreviewFragment.DATA_URI to attachment.displayUri,
      MediaPreviewFragment.DATA_CONTENT_TYPE to contentType,
      MediaPreviewFragment.DATA_SIZE to attachment.size,
      MediaPreviewFragment.AUTO_PLAY to attachment.videoGif,
      MediaPreviewFragment.VIDEO_GIF to attachment.videoGif
    )
    val fragment = if (MediaUtil.isVideo(contentType)) {
      VideoMediaPreviewFragment()
    } else if (MediaUtil.isImageType(contentType)) {
      ImageMediaPreviewFragment()
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
