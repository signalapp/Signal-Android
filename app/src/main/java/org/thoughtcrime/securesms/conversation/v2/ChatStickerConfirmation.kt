/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.keyboard.LocalKeyboardSheetController
import org.signal.core.ui.compose.navigationBarsCompat
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.glide.compose.GlideImage
import org.signal.mediakeyboard.data.KeyboardSticker
import org.thoughtcrime.securesms.R
import kotlin.coroutines.cancellation.CancellationException
import org.signal.core.ui.R as CoreUiR

private const val PANEL_ALPHA = 0.6f

/** Used below API 31, where the keyboard behind the panel can't be blurred. */
private const val UNBLURRED_PANEL_ALPHA = 0.9f

/** How much to blur the keyboard behind the panel. */
internal val StickerConfirmationBlurRadius = 20.dp

private val FADE_MOTION = spring<Float>(
  dampingRatio = Spring.DampingRatioNoBouncy,
  stiffness = Spring.StiffnessMediumLow
)

private val PanelShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
private val ButtonSize = 40.dp
private val StickerSize = 200.dp

/**
 * How far the confirmation has faded in, from 0 to 1. Follows the controller, and a back gesture
 * drives it directly, so the blur behind the panel and the panel itself fade as one.
 */
@Composable
fun rememberStickerConfirmationFade(controller: ChatStickerConfirmationController): Animatable<Float, AnimationVector1D> {
  val fade = remember { Animatable(0f) }

  LaunchedEffect(controller.isShowing) {
    fade.animateTo(if (controller.isShowing) 1f else 0f, FADE_MOTION)
  }

  return fade
}

/**
 * Offers up a sticker long pressed in the media keyboard, over the whole keyboard sheet, to send or
 * act on. The sheet blurs the keyboard behind it by [StickerConfirmationBlurRadius].
 *
 * @param fade From [rememberStickerConfirmationFade].
 */
@Composable
fun ChatStickerConfirmation(
  controller: ChatStickerConfirmationController,
  fade: Animatable<Float, AnimationVector1D>,
  modifier: Modifier = Modifier
) {
  val confirmation = controller.confirmation
  var lastConfirmation by remember { mutableStateOf<StickerConfirmation?>(null) }
  if (confirmation != null) {
    lastConfirmation = confirmation
  }

  val scope = rememberCoroutineScope()

  PredictiveBackHandler(enabled = confirmation != null) { progress ->
    try {
      progress.collect { backEvent -> fade.snapTo(1f - backEvent.progress) }
      controller.dismiss()
    } catch (cancelled: CancellationException) {
      scope.launch { fade.animateTo(1f, FADE_MOTION) }
      throw cancelled
    }
  }

  val fadedOut by remember(fade) { derivedStateOf { fade.value == 0f } }
  val shown = lastConfirmation

  if (shown != null && (confirmation != null || !fadedOut)) {
    StickerConfirmationPanel(
      confirmation = shown,
      sendColor = controller.sendColor,
      favoritesEnabled = controller.favoritesEnabled,
      onBack = controller::dismiss,
      onSend = controller::send,
      onToggleFavorite = controller::toggleFavorite,
      onViewPack = controller::viewPack,
      modifier = modifier.graphicsLayer { alpha = fade.value }
    )
  }

  if (controller.confirmingRemoveFavorite) {
    ConfirmRemoveFavoriteDialog(controller = controller)
  }
}

@Composable
private fun ConfirmRemoveFavoriteDialog(controller: ChatStickerConfirmationController) {
  val host = LocalKeyboardSheetController.current

  // A window of our own in front of the sheet, so the sheet stays put rather than giving way to it.
  DisposableEffect(host) {
    host?.onHostWindowShown()
    onDispose { host?.onHostWindowHidden() }
  }

  Dialogs.SimpleAlertDialog(
    title = stringResource(CoreUiR.string.StickerFavorites__remove_from_favorites_question),
    body = stringResource(CoreUiR.string.StickerFavorites__you_cant_send_it_or_add_it_to_favorites_again),
    confirm = stringResource(CoreUiR.string.StickerFavorites__remove),
    dismiss = stringResource(android.R.string.cancel),
    confirmColor = MaterialTheme.colorScheme.error,
    onConfirm = controller::confirmRemoveFavorite,
    onDeny = controller::cancelRemoveFavorite,
    onDismissRequest = controller::cancelRemoveFavorite
  )
}

@Composable
private fun StickerConfirmationPanel(
  confirmation: StickerConfirmation,
  sendColor: Color,
  favoritesEnabled: Boolean,
  onBack: () -> Unit,
  onSend: () -> Unit,
  onToggleFavorite: () -> Unit,
  onViewPack: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(PanelShape)
      .background(SignalTheme.colors.colorSurface3.copy(alpha = if (Build.VERSION.SDK_INT >= 31) PANEL_ALPHA else UNBLURRED_PANEL_ALPHA))
      // Taken here so a touch on the panel never falls through to the keyboard behind it.
      .pointerInput(Unit) {}
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

      if (confirmation.replyHeader != null) {
        ReplyHeader(
          replyHeader = confirmation.replyHeader,
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

    StickerActionsPill(
      sticker = confirmation.sticker,
      favoritesEnabled = favoritesEnabled,
      onToggleFavorite = onToggleFavorite,
      onViewPack = onViewPack,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 16.dp)
    )
  }
}

@Composable
private fun StickerActionsPill(
  sticker: KeyboardSticker,
  favoritesEnabled: Boolean,
  onToggleFavorite: () -> Unit,
  onViewPack: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Both act on the sticker's pack, which a sticker with no key does not have.
  if (sticker.packKey == null) {
    return
  }

  val buttonColors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

  Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier = modifier
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.primaryContainer)
  ) {
    if (favoritesEnabled) {
      IconButton(
        onClick = onToggleFavorite,
        colors = buttonColors,
        modifier = Modifier.size(ButtonSize)
      ) {
        if (sticker.isFavorite) {
          Icon(
            imageVector = SignalIcons.FavoriteOff.imageVector,
            contentDescription = stringResource(CoreUiR.string.StickerFavorites__remove_from_favorites)
          )
        } else {
          Icon(
            imageVector = SignalIcons.Favorite.imageVector,
            contentDescription = stringResource(CoreUiR.string.StickerFavorites__add_to_favorites)
          )
        }
      }
    }

    IconButton(
      onClick = onViewPack,
      colors = buttonColors,
      modifier = Modifier.size(ButtonSize)
    ) {
      Icon(
        imageVector = SignalIcons.StickerPack.imageVector,
        contentDescription = stringResource(R.string.ChatStickerConfirmation__manage_pack)
      )
    }
  }
}

@Composable
private fun ReplyHeader(
  replyHeader: String,
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
      text = replyHeader,
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
    confirmation = StickerConfirmation(sticker = previewSticker, replyHeader = null),
    sendColor = Color(0xFF315FF4),
    favoritesEnabled = true,
    onBack = {},
    onSend = {},
    onToggleFavorite = {},
    onViewPack = {},
    modifier = Modifier.size(width = 412.dp, height = 409.dp)
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun StickerConfirmationPanelReplyPreview() {
  StickerConfirmationPanel(
    confirmation = StickerConfirmation(sticker = previewSticker.copy(isFavorite = true), replyHeader = "Reply to Maya Johnson"),
    sendColor = Color(0xFF315FF4),
    favoritesEnabled = true,
    onBack = {},
    onSend = {},
    onToggleFavorite = {},
    onViewPack = {},
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
