package org.thoughtcrime.securesms.conversation.v2

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnPreDraw
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.compose.GlideImage
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.fonts.SignalSymbols
import org.thoughtcrime.securesms.fonts.SignalSymbols.getSpannedString
import org.thoughtcrime.securesms.mms.AudioSlide
import org.thoughtcrime.securesms.mms.DecryptableUri
import org.thoughtcrime.securesms.mms.DocumentSlide
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.mms.StickerSlide
import org.thoughtcrime.securesms.mms.VideoSlide
import org.thoughtcrime.securesms.util.hasSharedContact
import org.thoughtcrime.securesms.util.isPoll
import org.thoughtcrime.securesms.util.isViewOnceMessage
import org.whispersystems.signalservice.api.payments.FormatterOptions
import kotlin.jvm.optionals.getOrDefault

/**
 * Displays pinned messages banner on conversation fragment
 */
@Composable
fun PinnedMessagesBanner(
  messages: List<ConversationMessage> = emptyList(),
  onUnpinMessage: (Long) -> Unit = {},
  onGoToMessage: (Long) -> Unit = {},
  onViewAllMessages: () -> Unit = {}
) {
  val menuController = remember { DropdownMenus.MenuController() }
  var index by remember(messages) { mutableIntStateOf(messages.size - 1) }
  val conversationMessage = messages[index % messages.size]
  val message = conversationMessage.messageRecord as MmsMessageRecord
  val (glyph, body, showThumbnail) = getMessageMetadata(conversationMessage)

  Column {
    HorizontalDivider()
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .background(SignalTheme.colors.colorSurface2)
        .clickable {
          index = (index + 1) % messages.size
          onGoToMessage(message.id)
        }
        .padding(8.dp)
        .height(IntrinsicSize.Min)
    ) {
      if (messages.size > 1) {
        Heading(index, messages.size)
      }

      if (showThumbnail && message.slideDeck.firstSlide?.uri != null) {
        GlideImage(
          model = DecryptableUri(message.slideDeck.firstSlide!!.uri!!),
          modifier = Modifier
            .padding(start = 8.dp)
            .size(32.dp)
            .clip(RoundedCornerShape(12.dp))
        )
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .padding(start = 8.dp)
      ) {
        Text(
          text = if (message.fromRecipient.isSelf) {
            stringResource(R.string.Recipient_you)
          } else {
            message.fromRecipient.getDisplayName(LocalContext.current)
          },
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        val displayBody = if (glyph != null) {
          SpannableStringBuilder()
            .append(getSpannedString(LocalContext.current, SignalSymbols.Weight.REGULAR, glyph, -1))
            .append(" ")
            .append(body)
        } else {
          body
        }

        AndroidView(
          factory = ::EmojiTextView
        ) { view ->
          view.enableRenderSpoilers()
          view.text = displayBody
          view.ellipsize = TextUtils.TruncateAt.END
          view.maxLines = 1
          view.doOnPreDraw {
            (it as EmojiTextView).ellipsizeEmojiTextForMaxLines()
          }
        }
      }

      Box(modifier = Modifier.padding(horizontal = 8.dp)) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.symbol_pin_24),
          contentDescription = stringResource(R.string.PinnedMessage__pinned),
          modifier = Modifier
            .clickable { menuController.show() }
            .padding(vertical = 8.dp),
          tint = MaterialTheme.colorScheme.onSurface
        )

        DropdownMenus.Menu(controller = menuController, offsetX = 2.dp, offsetY = 16.dp) { menuController ->
          Column {
            DropdownMenus.ItemWithIcon(menuController, R.drawable.symbol_pin_slash_24, R.string.PinnedMessage__unpin_message) { onUnpinMessage(message.id) }
            DropdownMenus.ItemWithIcon(menuController, R.drawable.symbol_chat_24, R.string.PinnedMessage__go_to_message) { onGoToMessage(message.id) }
            DropdownMenus.ItemWithIcon(menuController, R.drawable.symbol_list_bullet_24, R.string.PinnedMessage__view_all_messages) { onViewAllMessages() }
          }
        }
      }
    }
  }
}

/**
 * Heading to show how many pinned messages there are and which one (of three) is being displayed
 */
@Composable
fun Heading(selectedIndex: Int, size: Int) {
  Column(
    modifier = Modifier.fillMaxHeight()
  ) {
    for (i in 0 until size) {
      Box(
        modifier = Modifier
          .padding(vertical = 2.dp)
          .width(2.dp)
          .weight(1f)
          .background(
            color = if (i == selectedIndex) {
              MaterialTheme.colorScheme.onSurface
            } else {
              SignalTheme.colors.colorTransparentInverse2
            },
            shape = RoundedCornerShape(16.dp)
          )
      )
    }
  }
}

/**
 * Given the type of message, returns the associated glyph, body, and whether or not a thumbnail should be rendered with it
 */
@Composable
fun getMessageMetadata(conversationMessage: ConversationMessage): Triple<SignalSymbols.Glyph?, SpannableString, Boolean> {
  val context = LocalContext.current
  val message = conversationMessage.messageRecord as MmsMessageRecord
  val slide = message.slideDeck.firstSlide
  return if (slide is StickerSlide) {
    Triple(SignalSymbols.Glyph.STICKER, SpannableString(stringResource(R.string.PinnedMessage__sticker)), false)
  } else if (slide is AudioSlide) {
    Triple(SignalSymbols.Glyph.AUDIO, SpannableString(stringResource(R.string.PinnedMessage__voice)), false)
  } else if (slide is DocumentSlide) {
    Triple(SignalSymbols.Glyph.FILE, SpannableString(slide.fileName.getOrDefault(stringResource(R.string.DocumentView_unnamed_file))), false)
  } else if (message.isViewOnceMessage()) {
    Triple(SignalSymbols.Glyph.VIEW_ONCE, SpannableString(stringResource(R.string.PinnedMessage__view_once)), false)
  } else if (message.isPoll()) {
    Triple(SignalSymbols.Glyph.POLL, SpannableString(stringResource(R.string.Poll__poll_question, message.body)), false)
  } else if (message.hasSharedContact()) {
    Triple(SignalSymbols.Glyph.PERSON_CIRCLE, SpannableString(message.sharedContacts.first().name.givenName), false)
  } else if (message.isPaymentNotification && message.payment != null) {
    Triple(SignalSymbols.Glyph.CREDIT_CARD, SpannableString(message.payment!!.amount.toString(FormatterOptions.defaults())), false)
  } else if (slide?.isVideoGif == true) {
    Triple(SignalSymbols.Glyph.GIF_RECTANGLE, SpannableString(stringResource(R.string.PinnedMessage__gif)), false)
  } else if (slide is ImageSlide && message.body.isEmpty()) {
    Triple(null, SpannableString(stringResource(R.string.PinnedMessage__photo)), true)
  } else if (slide is VideoSlide && message.body.isEmpty()) {
    Triple(null, SpannableString(stringResource(R.string.PinnedMessage__video)), true)
  } else {
    Triple(null, conversationMessage.getDisplayBody(context), true)
  }
}
