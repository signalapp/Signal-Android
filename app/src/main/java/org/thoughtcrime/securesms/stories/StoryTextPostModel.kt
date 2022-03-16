package org.thoughtcrime.securesms.stories

import android.graphics.Bitmap
import android.view.View
import androidx.core.graphics.scale
import androidx.core.view.drawToBitmap
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.SimpleResource
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.Base64
import java.security.MessageDigest

/**
 * Glide model to render a StoryTextPost as a bitmap
 */
data class StoryTextPostModel(
  private val storyTextPost: StoryTextPost,
  private val storySentAtMillis: Long,
  private val storyAuthor: RecipientId
) : Key {

  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update(storyTextPost.toByteArray())
    messageDigest.update(storySentAtMillis.toString().toByteArray())
    messageDigest.update(storyAuthor.serialize().toByteArray())
  }

  val text: String = storyTextPost.body

  companion object {

    fun parseFrom(messageRecord: MessageRecord): StoryTextPostModel {
      return parseFrom(
        messageRecord.body,
        messageRecord.timestamp,
        if (messageRecord.isOutgoing) Recipient.self().id else messageRecord.individualRecipient.id
      )
    }

    @JvmStatic
    fun parseFrom(body: String, storySentAtMillis: Long, storyAuthor: RecipientId): StoryTextPostModel {
      return StoryTextPostModel(
        storyTextPost = StoryTextPost.parseFrom(Base64.decode(body)),
        storySentAtMillis = storySentAtMillis,
        storyAuthor = storyAuthor
      )
    }
  }

  class Decoder : ResourceDecoder<StoryTextPostModel, Bitmap> {

    companion object {
      private const val RENDER_WIDTH = 1080
      private const val RENDER_HEIGHT = 1920
    }

    override fun handles(source: StoryTextPostModel, options: Options): Boolean = true

    override fun decode(source: StoryTextPostModel, width: Int, height: Int, options: Options): Resource<Bitmap> {
      val message = SignalDatabase.mmsSms.getMessageFor(source.storySentAtMillis, source.storyAuthor)
      val view = StoryTextPostView(ApplicationDependencies.getApplication())

      view.bindFromStoryTextPost(source.storyTextPost)
      view.bindLinkPreview((message as? MmsMessageRecord)?.linkPreviews?.firstOrNull())

      view.invalidate()
      view.measure(View.MeasureSpec.makeMeasureSpec(RENDER_WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(RENDER_HEIGHT, View.MeasureSpec.EXACTLY))
      view.layout(0, 0, view.measuredWidth, view.measuredHeight)

      val bitmap = view.drawToBitmap().scale(width, height)

      return SimpleResource(bitmap)
    }
  }
}
