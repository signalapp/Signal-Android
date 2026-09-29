/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.navigationBarsCompat
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.glide.compose.GlideImage
import org.signal.mediakeyboard.data.KeyboardSticker
import org.thoughtcrime.securesms.R

private const val PANEL_ALPHA = 0.6f

/** Used below API 31, where the keyboard behind the panel can't be blurred. */
private const val UNBLURRED_PANEL_ALPHA = 0.9f

/** How much to blur the keyboard behind the panel. */
internal val StickerConfirmationBlurRadius = 20.dp

private val PanelShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
private val ButtonSize = 40.dp
private val StickerSize = 200.dp

/**
 * Asks the user to confirm a sticker picked from the media keyboard, over the whole keyboard sheet.
 * The sheet blurs the keyboard behind it by [StickerConfirmationBlurRadius].
 */
@Composable
fun ChatStickerConfirmation(
  controller: ChatStickerConfirmationController,
  modifier: Modifier = Modifier
) {
  val confirmation = controller.confirmation
  var lastConfirmation by remember { mutableStateOf<StickerConfirmation?>(null) }
  if (confirmation != null) {
    lastConfirmation = confirmation
  }

  BackHandler(enabled = confirmation != null) {
    controller.dismiss()
  }

  AnimatedVisibility(
    visible = confirmation != null,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = modifier
  ) {
    lastConfirmation?.let {
      StickerConfirmationPanel(
        confirmation = it,
        sendColor = controller.sendColor,
        onBack = controller::dismiss,
        onSend = controller::send,
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@Composable
private fun StickerConfirmationPanel(
  confirmation: StickerConfirmation,
  sendColor: Color,
  onBack: () -> Unit,
  onSend: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(PanelShape)
      .background(SignalTheme.colors.colorSurface3.copy(alpha = if (Build.VERSION.SDK_INT >= 31) PANEL_ALPHA else UNBLURRED_PANEL_ALPHA))
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
          }
        }
      }
      .windowInsetsPadding(WindowInsets.navigationBarsCompat.only(WindowInsetsSides.Bottom))
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
    ) {
      IconButton(
        onClick = onBack,
        colors = IconButtonDefaults.iconButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
          .size(ButtonSize)
          .align(Alignment.CenterStart)
      ) {
        Icon(
          imageVector = SignalIcons.ArrowStart.imageVector,
          contentDescription = stringResource(R.string.ChatStickerConfirmation__go_back)
        )
      }

      if (confirmation.replyTo != null) {
        ReplyHeader(
          replyTo = confirmation.replyTo,
          modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = ButtonSize + 8.dp)
        )
      }

      IconButton(
        onClick = onSend,
        colors = IconButtonDefaults.iconButtonColors(
          containerColor = sendColor,
          contentColor = colorResource(R.color.conversation_send_button_tint)
        ),
        modifier = Modifier
          .size(ButtonSize)
          .align(Alignment.CenterEnd)
      ) {
        Icon(
          imageVector = SignalIcons.SendFill.imageVector,
          contentDescription = stringResource(R.string.conversation_activity__send)
        )
      }
    }

    GlideImage(
      model = confirmation.sticker.image,
      enableApngAnimation = true,
      modifier = Modifier
        .size(StickerSize)
        .align(Alignment.Center)
    )
  }
}

@Composable
private fun ReplyHeader(
  replyTo: String,
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_reply_24),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(16.dp)
    )

    Text(
      text = stringResource(R.string.ChatStickerConfirmation__reply_to_s, replyTo),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun StickerConfirmationPanelPreview() {
  StickerConfirmationPanel(
    confirmation = StickerConfirmation(sticker = previewSticker, replyTo = null),
    sendColor = Color(0xFF315FF4),
    onBack = {},
    onSend = {},
    modifier = Modifier.size(width = 412.dp, height = 409.dp)
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun StickerConfirmationPanelReplyPreview() {
  StickerConfirmationPanel(
    confirmation = StickerConfirmation(sticker = previewSticker, replyTo = "Maya Johnson"),
    sendColor = Color(0xFF315FF4),
    onBack = {},
    onSend = {},
    modifier = Modifier.size(width = 412.dp, height = 409.dp)
  )
}

private val previewSticker = KeyboardSticker(
  packId = "pack",
  packKey = "key",
  stickerId = 1,
  emoji = null,
  image = Unit
)
