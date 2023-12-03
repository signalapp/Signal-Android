package org.thoughtcrime.securesms.messages

import org.signal.core.util.Base64
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.whispersystems.signalservice.api.messages.SignalServicePreview
import org.whispersystems.signalservice.api.messages.SignalServiceTextAttachment
import java.io.IOException
import java.util.Optional
import kotlin.math.roundToInt

object StorySendUtil {
  @JvmStatic
  @Throws(IOException::class)
  fun deserializeBodyToStoryTextAttachment(message: OutgoingMessage, getPreviewsFor: (OutgoingMessage) -> List<SignalServicePreview>): SignalServiceTextAttachment {
    val storyTextPost = StoryTextPost.ADAPTER.decode(Base64.decode(message.body))
    val preview = if (message.linkPreviews.isEmpty()) {
      Optional.empty()
    } else {
      Optional.of(getPreviewsFor(message)[0])
    }

    return if (storyTextPost.background!!.linearGradient != null) {
      SignalServiceTextAttachment.forGradientBackground(
        Optional.ofNullable(storyTextPost.body),
        Optional.ofNullable(getStyle(storyTextPost.style)),
        Optional.of(storyTextPost.textForegroundColor),
        Optional.of(storyTextPost.textBackgroundColor),
        preview,
        SignalServiceTextAttachment.Gradient(
          Optional.of(storyTextPost.background.linearGradient!!.rotation.roundToInt()),
          ArrayList(storyTextPost.background.linearGradient.colors),
          ArrayList(storyTextPost.background.linearGradient.positions)
        )
      )
    } else {
      SignalServiceTextAttachment.forSolidBackground(
        Optional.ofNullable(storyTextPost.body),
        Optional.ofNullable(getStyle(storyTextPost.style)),
        Optional.of(storyTextPost.textForegroundColor),
        Optional.of(storyTextPost.textBackgroundColor),
        preview,
        storyTextPost.background.singleColor!!.color
      )
    }
  }

  private fun getStyle(style: StoryTextPost.Style): SignalServiceTextAttachment.Style {
    return when (style) {
      StoryTextPost.Style.REGULAR -> SignalServiceTextAttachment.Style.REGULAR
      StoryTextPost.Style.BOLD -> SignalServiceTextAttachment.Style.BOLD
      StoryTextPost.Style.SERIF -> SignalServiceTextAttachment.Style.SERIF
      StoryTextPost.Style.SCRIPT -> SignalServiceTextAttachment.Style.SCRIPT
      StoryTextPost.Style.CONDENSED -> SignalServiceTextAttachment.Style.CONDENSED
      else -> SignalServiceTextAttachment.Style.DEFAULT
    }
  }
}
