package org.thoughtcrime.securesms.stories

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.fragment.app.FragmentManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.HeaderAction
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.AttachmentTable.TransformProperties
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.v2.stories.ChooseStoryTypeBottomSheet
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.mms.VideoSlide
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.hasLinkPreview
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Collection of helper methods and constants for dealing with the stories feature.
 */
object Stories {

  private val TAG = Log.tag(Stories::class.java)

  const val MAX_TEXT_STORY_SIZE = 700
  const val MAX_TEXT_STORY_LINE_COUNT = 13
  const val MAX_CAPTION_SIZE = 1500

  @JvmField
  val MAX_VIDEO_DURATION_MILLIS: Long = (31.seconds - 1.milliseconds).inWholeMilliseconds

  /**
   * Whether or not the user has the Stories feature enabled.
   */
  @JvmStatic
  fun isFeatureEnabled(): Boolean {
    return !SignalStore.storyValues().isFeatureDisabled
  }

  fun getHeaderAction(onClick: () -> Unit): HeaderAction {
    return HeaderAction(
      R.string.ContactsCursorLoader_new,
      R.drawable.ic_plus_12,
      onClick
    )
  }

  fun getHeaderAction(fragmentManager: FragmentManager): HeaderAction {
    return getHeaderAction {
      ChooseStoryTypeBottomSheet().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  fun sendTextStories(messages: List<OutgoingSecureMediaMessage>): Completable {
    return Completable.create { emitter ->
      MessageSender.sendStories(ApplicationDependencies.getApplication(), messages, null, null)
      emitter.onComplete()
    }
  }

  @JvmStatic
  fun getRecipientsToSendTo(messageId: Long, sentTimestamp: Long, allowsReplies: Boolean): List<Recipient> {
    val recipientIds: List<RecipientId> = SignalDatabase.storySends.getRecipientsToSendTo(messageId, sentTimestamp, allowsReplies)

    return RecipientUtil.getEligibleForSending(recipientIds.map(Recipient::resolved))
  }

  @WorkerThread
  fun onStorySettingsChanged(distributionListId: DistributionListId) {
    val recipientId = SignalDatabase.distributionLists.getRecipientId(distributionListId) ?: error("Cannot find recipient id for distribution list.")
    onStorySettingsChanged(recipientId)
  }

  @WorkerThread
  fun onStorySettingsChanged(storyRecipientId: RecipientId) {
    SignalDatabase.recipients.markNeedsSync(storyRecipientId)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  @JvmStatic
  @WorkerThread
  fun enqueueNextStoriesForDownload(recipientId: RecipientId, force: Boolean = false, limit: Int) {
    val recipient = Recipient.resolved(recipientId)
    if (!force && !recipient.isSelf && (recipient.shouldHideStory() || !recipient.hasViewedStory())) {
      return
    }

    Log.d(TAG, "Enqueuing downloads for up to $limit stories for $recipientId (force: $force)")
    SignalDatabase.mms.getUnreadStories(recipientId, limit).use { reader ->
      reader.forEach {
        enqueueAttachmentsFromStoryForDownloadSync(it as MmsMessageRecord, false)
      }
    }
  }

  fun enqueueAttachmentsFromStoryForDownload(record: MmsMessageRecord, ignoreAutoDownloadConstraints: Boolean): Completable {
    return Completable.fromAction {
      enqueueAttachmentsFromStoryForDownloadSync(record, ignoreAutoDownloadConstraints)
    }.subscribeOn(Schedulers.io())
  }

  @JvmStatic
  @WorkerThread
  fun enqueueAttachmentsFromStoryForDownloadSync(record: MmsMessageRecord, ignoreAutoDownloadConstraints: Boolean) {
    SignalDatabase.attachments.getAttachmentsForMessage(record.id).filterNot { it.isSticker }.forEach {
      val job = AttachmentDownloadJob(record.id, it.attachmentId, ignoreAutoDownloadConstraints)
      ApplicationDependencies.getJobManager().add(job)
    }

    if (record.hasLinkPreview() && record.linkPreviews[0].attachmentId != null) {
      ApplicationDependencies.getJobManager().add(
        AttachmentDownloadJob(record.id, record.linkPreviews[0].attachmentId, true)
      )
    }
  }

  object MediaTransform {

    private val TAG = Log.tag(MediaTransform::class.java)

    /**
     * Describes what needs to be done in order to send a given piece of content.
     * This is what will bubble up to the sending logic.
     */
    enum class SendRequirements {
      /**
       * Don't need to do anything.
       */
      VALID_DURATION,

      /**
       * The media needs to be clipped and clipping is available.
       */
      REQUIRES_CLIP,

      /**
       * Either clipping isn't available or the given media has an invalid duration.
       */
      CAN_NOT_SEND
    }

    /**
     * Describes a duration for a given piece of content.
     */
    private sealed class DurationResult {
      /**
       * Valid to send as-is to a story.
       */
      data class ValidDuration(val duration: Long) : DurationResult()

      /**
       * Invalid to send as-is but can be clipped.
       */
      data class InvalidDuration(val duration: Long) : DurationResult()

      /**
       * Invalid to send, due to failure to get duration
       */
      object CanNotGetDuration : DurationResult()

      /**
       * Valid to send because the content does not have a duration.
       */
      object None : DurationResult()
    }

    @JvmStatic
    @WorkerThread
    fun canPreUploadMedia(media: Media): Boolean {
      return when {
        MediaUtil.isVideo(media.mimeType) -> getSendRequirements(media) != SendRequirements.REQUIRES_CLIP
        else -> true
      }
    }

    @JvmStatic
    @WorkerThread
    fun getSendRequirements(media: Media): SendRequirements {
      return when (getContentDuration(media)) {
        is DurationResult.ValidDuration -> SendRequirements.VALID_DURATION
        is DurationResult.InvalidDuration -> {
          if (canClipMedia(media)) {
            SendRequirements.REQUIRES_CLIP
          } else {
            SendRequirements.CAN_NOT_SEND
          }
        }
        is DurationResult.CanNotGetDuration -> SendRequirements.CAN_NOT_SEND
        is DurationResult.None -> SendRequirements.VALID_DURATION
      }
    }

    @JvmStatic
    @WorkerThread
    fun getSendRequirements(media: List<Media>): SendRequirements {
      return media
        .map { getSendRequirements(it) }
        .fold(SendRequirements.VALID_DURATION) { left, right ->
          if (left == SendRequirements.CAN_NOT_SEND || right == SendRequirements.CAN_NOT_SEND) {
            SendRequirements.CAN_NOT_SEND
          } else if (left == SendRequirements.REQUIRES_CLIP || right == SendRequirements.REQUIRES_CLIP) {
            SendRequirements.REQUIRES_CLIP
          } else {
            SendRequirements.VALID_DURATION
          }
        }
    }

    private fun canClipMedia(media: Media): Boolean {
      return MediaUtil.isVideo(media.mimeType) && MediaConstraints.isVideoTranscodeAvailable()
    }

    private fun getContentDuration(media: Media): DurationResult {
      return if (MediaUtil.isVideo(media.mimeType)) {
        val mediaDuration = if (media.duration == 0L && media.transformProperties.map(TransformProperties::shouldSkipTransform).orElse(true)) {
          getVideoDuration(media.uri)
        } else if (media.transformProperties.map { it.isVideoTrim }.orElse(false)) {
          TimeUnit.MICROSECONDS.toMillis(media.transformProperties.get().videoTrimEndTimeUs - media.transformProperties.get().videoTrimStartTimeUs)
        } else {
          media.duration
        }

        return if (mediaDuration <= 0L) {
          DurationResult.CanNotGetDuration
        } else if (mediaDuration > MAX_VIDEO_DURATION_MILLIS) {
          DurationResult.InvalidDuration(mediaDuration)
        } else {
          DurationResult.ValidDuration(mediaDuration)
        }
      } else {
        DurationResult.None
      }
    }

    /**
     * Utilizes ExoPlayer to ascertain the duration of the video at the given URI. It is the burden of
     * the caller to ensure that the passed URI points to a video. This function must not be called from
     * main, as it blocks on the calling thread and waits for some video player work to happen on the main
     * thread.
     */
    @JvmStatic
    @WorkerThread
    fun getVideoDuration(uri: Uri): Long {
      var duration = 0L
      var player: ExoPlayer? = null
      val countDownLatch = CountDownLatch(1)
      ThreadUtil.runOnMainSync {
        val mainThreadPlayer = ApplicationDependencies.getExoPlayerPool().get("stories_duration_check")
        if (mainThreadPlayer == null) {
          Log.w(TAG, "Could not get a player from the pool, so we cannot get the length of the video.")
          countDownLatch.countDown()
        } else {
          mainThreadPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
              if (playbackState == 3) {
                duration = mainThreadPlayer.duration
                countDownLatch.countDown()
              }
            }

            override fun onPlayerError(error: PlaybackException) {
              countDownLatch.countDown()
            }
          })

          mainThreadPlayer.setMediaItem(MediaItem.fromUri(uri))
          mainThreadPlayer.prepare()

          player = mainThreadPlayer
        }
      }

      countDownLatch.await()

      ThreadUtil.runOnMainSync {
        val mainThreadPlayer = player
        if (mainThreadPlayer != null) {
          ApplicationDependencies.getExoPlayerPool().pool(mainThreadPlayer)
        }
      }

      return max(duration, 0L)
    }

    /**
     * Takes a given piece of media and cuts it into 30 second chunks. It is assumed that the media handed in requires clipping.
     * Callers can utilize canClipMedia to determine if the given media can and should be clipped.
     */
    @JvmStatic
    @WorkerThread
    fun clipMediaToStoryDuration(media: Media): List<Media> {
      val storyDurationUs = TimeUnit.MILLISECONDS.toMicros(MAX_VIDEO_DURATION_MILLIS)
      val startOffsetUs = media.transformProperties.map { it.videoTrimStartTimeUs }.orElse(0L)
      val endOffsetUs = media.transformProperties.map { it.videoTrimEndTimeUs }.orElse(TimeUnit.MILLISECONDS.toMicros(getVideoDuration(media.uri)))
      val durationUs = endOffsetUs - startOffsetUs

      if (durationUs <= 0L) {
        return emptyList()
      }

      val clipCount = (durationUs / storyDurationUs) + (if (durationUs.mod(storyDurationUs) == 0L) 0L else 1L)
      return (0 until clipCount).map { clipIndex ->
        val startTimeUs = clipIndex * storyDurationUs + startOffsetUs
        val endTimeUs = min(startTimeUs + storyDurationUs, endOffsetUs)

        if (startTimeUs > endTimeUs) {
          error("Illegal clip: $startTimeUs > $endTimeUs for clip $clipIndex")
        }

        AttachmentTable.TransformProperties(false, true, startTimeUs, endTimeUs, SentMediaQuality.STANDARD.code)
      }.map { transformMedia(media, it) }
    }

    private fun transformMedia(media: Media, transformProperties: AttachmentTable.TransformProperties): Media {
      Log.d(TAG, "Transforming media clip: ${transformProperties.videoTrimStartTimeUs.microseconds.inWholeSeconds}s to ${transformProperties.videoTrimEndTimeUs.microseconds.inWholeSeconds}s")
      return Media(
        media.uri,
        media.mimeType,
        media.date,
        media.width,
        media.height,
        media.size,
        media.duration,
        media.isBorderless,
        media.isVideoGif,
        media.bucketId,
        media.caption,
        Optional.of(transformProperties)
      )
    }

    /**
     * Convenience method for transforming a Media into a VideoSlide
     */
    @JvmStatic
    fun mediaToVideoSlide(context: Context, media: Media): VideoSlide {
      return VideoSlide(
        context,
        media.uri,
        media.size,
        media.isVideoGif,
        media.width,
        media.height,
        media.caption.orElse(null),
        media.transformProperties.orElse(null)
      )
    }

    /**
     * Convenience method for transforming a VideoSlide into a Media with the
     * specified duration.
     */
    @JvmStatic
    fun videoSlideToMedia(videoSlide: VideoSlide, duration: Long): Media {
      return Media(
        videoSlide.uri!!,
        videoSlide.contentType,
        System.currentTimeMillis(),
        0,
        0,
        videoSlide.fileSize,
        duration,
        videoSlide.isBorderless,
        videoSlide.isVideoGif,
        Optional.empty(),
        videoSlide.caption,
        Optional.empty()
      )
    }
  }
}
