package org.thoughtcrime.securesms.mediapreview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.signal.core.util.dp
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.MediaTable.MediaRecord

object MediaIntentFactory {
  private const val ARGS_KEY = "args"

  const val NOT_IN_A_THREAD = -2
  const val UNKNOWN_TIMESTAMP = -2

  @Parcelize
  data class SharedElementArgs(
    val width: Int = 1,
    val height: Int = 1,
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomRight: Float = 0f,
    val bottomLeft: Float = 0f
  ) : Parcelable

  @Parcelize
  data class MediaPreviewArgs(
    val threadId: Long,
    val date: Long,
    val initialMediaUri: Uri,
    val initialMediaType: String,
    val initialMediaSize: Long,
    val initialCaption: String? = null,
    val leftIsRecent: Boolean = false,
    val hideAllMedia: Boolean = false,
    val showThread: Boolean = false,
    val allMediaInRail: Boolean = false,
    val sorting: MediaTable.Sorting,
    val isVideoGif: Boolean,
    val sharedElementArgs: SharedElementArgs = SharedElementArgs(),
    val skipSharedElementTransition: Boolean
  ) : Parcelable {
    fun skipSharedElementTransition(skipSharedElementTransition: Boolean): MediaPreviewArgs {
      return copy(skipSharedElementTransition = skipSharedElementTransition)
    }
  }

  @JvmStatic
  fun requireArguments(bundle: Bundle): MediaPreviewArgs = bundle.getParcelableCompat(ARGS_KEY, MediaPreviewArgs::class.java)!!

  @JvmStatic
  fun create(context: Context, args: MediaPreviewArgs): Intent {
    return Intent(context, MediaPreviewV2Activity::class.java).putExtra(ARGS_KEY, args)
  }

  fun intentFromMediaRecord(
    context: Context,
    mediaRecord: MediaRecord,
    leftIsRecent: Boolean,
    allMediaInRail: Boolean
  ): Intent {
    val attachment: DatabaseAttachment = mediaRecord.attachment!!
    return create(
      context,
      MediaPreviewArgs(
        mediaRecord.threadId,
        mediaRecord.date,
        attachment.uri!!,
        attachment.contentType,
        attachment.size,
        attachment.caption,
        leftIsRecent,
        allMediaInRail = allMediaInRail,
        sorting = MediaTable.Sorting.Newest,
        isVideoGif = attachment.isVideoGif,
        sharedElementArgs = SharedElementArgs(
          attachment.width,
          attachment.height,
          12.dp.toFloat(),
          12.dp.toFloat(),
          12.dp.toFloat(),
          12.dp.toFloat()
        ),
        skipSharedElementTransition = false
      )
    )
  }
}
