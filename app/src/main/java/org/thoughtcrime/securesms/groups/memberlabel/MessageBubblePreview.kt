/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.conversation.v2.items.SenderNameWithLabelView
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.visible
import org.signal.core.ui.R as CoreUiR

@Composable
fun MessageBubblePreview(
  sender: Recipient,
  senderNameColor: NameColor?,
  labelEmoji: String?,
  labelText: String?,
  messageText: String,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val senderNameColorInt = senderNameColor?.getColor(context) ?: MaterialTheme.colorScheme.onSurface.toArgb()

  Box(
    modifier = modifier
      .widthIn(max = 600.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(27.dp))
      .background(SignalTheme.colors.colorSurface2)
      .padding(start = 4.dp, end = 16.dp, top = 20.dp, bottom = 20.dp)
  ) {
    if (LocalInspectionMode.current) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
          .height(96.dp)
      ) {
        Text(
          text = "<MessageBubblePreview>",
          fontStyle = FontStyle.Italic
        )
      }
    } else {
      AndroidView(
        factory = ::MessageBubblePreviewView,
        update = { view ->
          view.setData(
            context = view.context,
            sender = sender,
            senderNameColor = senderNameColorInt,
            labelEmoji = labelEmoji,
            labelText = labelText,
            messageText = messageText
          )
        },
        modifier = modifier
      )
    }
  }
}

private class MessageBubblePreviewView(context: Context) : FrameLayout(context) {
  private val avatarView: AvatarImageView
  private val groupSenderView: SenderNameWithLabelView
  private val bodyBubble: View
  private val bodyText: EmojiTextView

  init {
    LayoutInflater.from(context).inflate(R.layout.v2_conversation_item_text_only_incoming, this, true)

    avatarView = findViewById(R.id.contact_photo)
    groupSenderView = findViewById(R.id.group_sender_name_with_label)
    bodyBubble = findViewById(R.id.conversation_item_body_wrapper)
    bodyText = findViewById(R.id.conversation_item_body)

    bodyBubble.background = GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = DimensionUnit.DP.toPixels(18f)
      setColor(ContextCompat.getColor(context, CoreUiR.color.signal_colorSurface))
    }

    bodyBubble.minimumWidth = DimensionUnit.DP.toPixels(180f).toInt()

    // match the appearance of V2ConversationItemTextOnlyViewHolder when a member label is shown:
    (bodyBubble.layoutParams as? MarginLayoutParams)?.marginEnd = 0
    (bodyText.layoutParams as? MarginLayoutParams)?.topMargin = 0

    findViewById<View>(R.id.conversation_item_reply)?.visible = false
    findViewById<View>(R.id.conversation_item_reactions)?.visible = false
  }

  fun setData(
    context: Context,
    sender: Recipient,
    @ColorInt senderNameColor: Int,
    labelEmoji: String?,
    labelText: String?,
    messageText: String
  ) {
    avatarView.visible = true
    avatarView.setAvatarUsingProfile(sender)

    groupSenderView.visible = true
    groupSenderView.setSender(sender.getDisplayName(context), senderNameColor)

    val memberLabel = if (labelEmoji.isNullOrBlank() && labelText.isNullOrBlank()) {
      null
    } else {
      MemberLabel(emoji = labelEmoji, text = labelText.orEmpty())
    }
    groupSenderView.setLabel(memberLabel)

    bodyText.text = messageText
    bodyText.setTextColor(ContextCompat.getColor(context, CoreUiR.color.signal_colorOnSurface))
    bodyText.visible = true
  }
}

@DayNightPreviews
@Composable
private fun MessageBubblePreviewPreview() {
  Previews.Preview {
    Box(
      modifier = Modifier
        .background(Color.Black)
        .padding(20.dp)
    ) {
      MessageBubblePreview(
        sender = Recipient(
          profileName = ProfileName.fromParts("Kahless", "The Unforgettable")
        ),
        senderNameColor = NameColor(
          lightColor = MaterialTheme.colorScheme.onSurface.toArgb(),
          darkColor = MaterialTheme.colorScheme.onSurface.toArgb()
        ),
        labelEmoji = "⚔️️",
        labelText = "Legendary Warrior",
        messageText = "Questions are the beginning of wisdom, the mark of a true warrior."
      )
    }
  }
}
