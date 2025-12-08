/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageLarge
import org.thoughtcrime.securesms.components.webrtc.AudioIndicatorView
import org.thoughtcrime.securesms.components.webrtc.TextureViewRenderer
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.AvatarUtil

/**
 * Encapsulates views needed to show a call participant including their
 * avatar in full screen or pip mode, and their video feed.
 *
 * This is a Compose reimplementation of [org.thoughtcrime.securesms.components.webrtc.CallParticipantView].
 */
@Composable
fun CallParticipantViewer(
  participant: CallParticipant,
  modifier: Modifier = Modifier,
  renderInPip: Boolean = false,
  raiseHandAllowed: Boolean = false,
  selfPipMode: SelfPipMode = SelfPipMode.NOT_SELF_PIP,
  isMoreThanOneCameraAvailable: Boolean = false,
  onSwitchCameraClick: (() -> Unit)? = null,
  onInfoMoreInfoClick: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val recipient = participant.recipient
  val isBlocked = recipient.isBlocked
  val isMissingMediaKeys = !participant.isMediaKeysReceived &&
    (System.currentTimeMillis() - participant.addedToCallTime) > 5000
  val infoMode = isBlocked || isMissingMediaKeys

  Box(modifier = modifier) {
    BlurredBackgroundAvatar(recipient = recipient)

    if (infoMode) {
      InfoOverlay(
        recipient = recipient,
        isBlocked = isBlocked,
        renderInPip = renderInPip,
        onMoreInfoClick = onInfoMoreInfoClick
      )
    } else {
      val hasContentToRender = participant.isVideoEnabled || participant.isScreenSharing

      if (hasContentToRender) {
        VideoRenderer(
          participant = participant,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        if (!renderInPip) {
          AvatarWithBadge(
            recipient = recipient,
            modifier = Modifier.align(Alignment.Center)
          )
        }

        if (renderInPip) {
          PipAvatar(
            recipient = recipient,
            modifier = Modifier
              .fillMaxSize()
              .align(Alignment.Center)
          )
        }
      }

      AudioIndicator(
        participant = participant,
        selfPipMode = selfPipMode,
        modifier = Modifier.align(
          when (selfPipMode) {
            SelfPipMode.MINI_SELF_PIP -> Alignment.BottomCenter
            else -> Alignment.BottomStart
          }
        )
      )

      if (selfPipMode != SelfPipMode.NOT_SELF_PIP && isMoreThanOneCameraAvailable && selfPipMode != SelfPipMode.MINI_SELF_PIP) {
        SwitchCameraButton(
          selfPipMode = selfPipMode,
          onClick = onSwitchCameraClick,
          modifier = Modifier.align(Alignment.BottomEnd)
        )
      }

      if (raiseHandAllowed && participant.isHandRaised) {
        RaiseHandIndicator(
          name = participant.getShortRecipientDisplayName(context),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 8.dp, top = 8.dp)
        )
      }
    }
  }
}

@Composable
private fun BlurredBackgroundAvatar(
  recipient: Recipient,
  modifier: Modifier = Modifier
) {
  val isInPreview = LocalInspectionMode.current

  // Use a simple background in preview mode, otherwise use Glide to load the blurred avatar
  if (isInPreview) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(Color(0xFF1B1B1D))
    )
  } else {
    // Use AndroidView to leverage AvatarUtil.loadBlurredIconIntoImageView
    AndroidView(
      factory = { context ->
        androidx.appcompat.widget.AppCompatImageView(context).apply {
          scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
      },
      update = { imageView ->
        AvatarUtil.loadBlurredIconIntoImageView(recipient, imageView)
      },
      modifier = modifier.fillMaxSize()
    )
  }
}

@Composable
private fun AvatarWithBadge(
  recipient: Recipient,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier) {
    AvatarImage(
      recipient = recipient,
      modifier = Modifier.size(112.dp)
    )

    BadgeImageLarge(
      badge = recipient.badges.firstOrNull(),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .size(36.dp)
    )
  }
}

@Composable
private fun PipAvatar(
  recipient: Recipient,
  modifier: Modifier = Modifier
) {
  BoxWithConstraints(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    // Use the smaller dimension to maintain 1:1 aspect ratio
    val avatarModifier = if (maxWidth < maxHeight) {
      Modifier
        .width(maxWidth)
        .aspectRatio(1f)
    } else {
      Modifier
        .height(maxHeight)
        .aspectRatio(1f, true)
    }

    AvatarImage(
      recipient = recipient,
      modifier = avatarModifier
    )

    BadgeImageLarge(
      badge = recipient.badges.firstOrNull(),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .size(36.dp)
    )
  }
}

@Composable
private fun VideoRenderer(
  participant: CallParticipant,
  modifier: Modifier = Modifier
) {
  var renderer by remember { mutableStateOf<TextureViewRenderer?>(null) }

  AndroidView(
    factory = { context ->
      FrameLayout(context).apply {
        setBackgroundColor(android.graphics.Color.parseColor("#CC000000"))
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )

        val textureRenderer = TextureViewRenderer(context).apply {
          layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
          )

          if (participant.isVideoEnabled) {
            participant.videoSink.lockableEglBase.performWithValidEglBase { eglBase ->
              init(eglBase)
            }
            attachBroadcastVideoSink(participant.videoSink)
          }
        }

        renderer = textureRenderer
        addView(textureRenderer)
      }
    },
    update = {
      val textureRenderer = renderer
      if (textureRenderer != null) {
        if (participant.isVideoEnabled) {
          participant.videoSink.lockableEglBase.performWithValidEglBase { eglBase ->
            textureRenderer.init(eglBase)
          }
          textureRenderer.attachBroadcastVideoSink(participant.videoSink)
        } else {
          textureRenderer.attachBroadcastVideoSink(null)
        }
      }
    },
    onRelease = {
      renderer?.release()
    },
    modifier = modifier
  )
}

@Composable
private fun AudioIndicator(
  participant: CallParticipant,
  selfPipMode: SelfPipMode,
  modifier: Modifier = Modifier
) {
  val margin = when (selfPipMode) {
    SelfPipMode.NORMAL_SELF_PIP -> 10.dp
    SelfPipMode.EXPANDED_SELF_PIP -> 10.dp
    SelfPipMode.MINI_SELF_PIP -> 10.dp
    SelfPipMode.FOCUSED_SELF_PIP -> 12.dp
    SelfPipMode.NOT_SELF_PIP -> 12.dp
  }

  AndroidView(
    factory = { context ->
      AudioIndicatorView(context, null)
    },
    update = { view ->
      view.bind(participant.isMicrophoneEnabled, participant.audioLevel)
    },
    modifier = modifier
      .padding(margin)
      .size(28.dp)
  )
}

@Composable
private fun SwitchCameraButton(
  selfPipMode: SelfPipMode,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  val size = when (selfPipMode) {
    SelfPipMode.EXPANDED_SELF_PIP, SelfPipMode.FOCUSED_SELF_PIP -> 48.dp
    else -> 28.dp
  }

  val margin = when (selfPipMode) {
    SelfPipMode.FOCUSED_SELF_PIP -> 12.dp
    else -> 10.dp
  }

  val iconInset = when (selfPipMode) {
    SelfPipMode.EXPANDED_SELF_PIP, SelfPipMode.FOCUSED_SELF_PIP -> 12.dp
    SelfPipMode.MINI_SELF_PIP -> 7.dp
    else -> 6.dp
  }

  // Only clickable in EXPANDED_SELF_PIP mode (per setSelfPipMode logic)
  val clickModifier = if (selfPipMode == SelfPipMode.EXPANDED_SELF_PIP && onClick != null) {
    Modifier.clickable { onClick() }
  } else {
    Modifier
  }

  Box(
    modifier = modifier
      .padding(end = margin, bottom = margin)
      .size(size)
      .background(
        color = Color(0xFF383838),
        shape = CircleShape
      )
      .padding(iconInset)
      .then(clickModifier),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      painter = painterResource(id = R.drawable.symbol_switch_24),
      contentDescription = "Switch camera direction",
      tint = Color.White
    )
  }
}

@Composable
private fun RaiseHandIndicator(
  name: String,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .background(
          color = colorResource(R.color.signal_light_colorSurface),
          shape = CircleShape
        ),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        painter = painterResource(id = R.drawable.symbol_raise_hand_24),
        contentDescription = null,
        tint = Color.Unspecified, // Let the drawable use its default color
        modifier = Modifier.size(24.dp)
      )
    }

    Spacer(modifier = Modifier.width(8.dp))

    Text(
      text = name,
      color = colorResource(R.color.signal_light_colorOnPrimary),
      fontSize = 14.sp,
      style = MaterialTheme.typography.bodyMedium
    )
  }
}

@Composable
private fun InfoOverlay(
  recipient: Recipient,
  isBlocked: Boolean,
  renderInPip: Boolean,
  onMoreInfoClick: (() -> Unit)?
) {
  val context = LocalContext.current

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0x66000000)),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(
        painter = painterResource(
          id = if (isBlocked) R.drawable.ic_block_tinted_24 else R.drawable.ic_error_solid_24
        ),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(48.dp)
      )

      if (!renderInPip) {
        Spacer(modifier = Modifier.size(12.dp))

        // Use AndroidView for EmojiTextView
        AndroidView(
          factory = { ctx ->
            EmojiTextView(ctx).apply {
              setTextColor(android.graphics.Color.WHITE)
              gravity = Gravity.CENTER_HORIZONTAL
              maxLines = 3
              setPadding(
                context.resources.getDimensionPixelSize(R.dimen.dsl_settings_gutter),
                0,
                context.resources.getDimensionPixelSize(R.dimen.dsl_settings_gutter),
                0
              )
            }
          },
          update = { view ->
            view.text = if (isBlocked) {
              context.getString(
                R.string.CallParticipantView__s_is_blocked,
                recipient.getShortDisplayName(context)
              )
            } else {
              context.getString(
                R.string.CallParticipantView__cant_receive_audio_video_from_s,
                recipient.getShortDisplayName(context)
              )
            }
          },
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        Spacer(modifier = Modifier.size(12.dp))

        Buttons.Small(
          onClick = { onMoreInfoClick?.invoke() },
          modifier = Modifier
        ) {
          Text(text = stringResource(R.string.CallParticipantView__more_info))
        }
      }
    }
  }
}

enum class SelfPipMode {
  NOT_SELF_PIP,
  NORMAL_SELF_PIP,
  EXPANDED_SELF_PIP,
  MINI_SELF_PIP,
  FOCUSED_SELF_PIP
}

@NightPreview
@Composable
private fun CallParticipantViewerPreview() {
  Previews.Preview {
    CallParticipantViewer(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Alice Johnson"),
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.MEDIUM
      ),
      raiseHandAllowed = false,
      renderInPip = false,
      modifier = Modifier.size(400.dp, 600.dp)
    )
  }
}

@NightPreview
@Composable
private fun CallParticipantViewerRaiseHandPreview() {
  Previews.Preview {
    CallParticipantViewer(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Bob Smith"),
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.HIGH,
        handRaisedTimestamp = System.currentTimeMillis()
      ),
      raiseHandAllowed = true,
      renderInPip = false,
      modifier = Modifier.size(400.dp, 600.dp)
    )
  }
}

@NightPreview
@Composable
private fun CallParticipantViewerPipPreview() {
  Previews.Preview {
    CallParticipantViewer(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Charlie Davis"),
        isMicrophoneEnabled = false
      ),
      renderInPip = true,
      modifier = Modifier.size(200.dp, 200.dp)
    )
  }
}

@NightPreview
@Composable
private fun CallParticipantViewerPipLandscapePreview() {
  Previews.Preview {
    CallParticipantViewer(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Charlie Davis"),
        isMicrophoneEnabled = false
      ),
      renderInPip = true,
      modifier = Modifier.size(200.dp, 100.dp)
    )
  }
}

@NightPreview
@Composable
private fun CallParticipantViewerBlockedPreview() {
  Previews.Preview {
    CallParticipantViewer(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Diana Prince", isBlocked = true)
      ),
      renderInPip = false,
      onInfoMoreInfoClick = {},
      modifier = Modifier.size(400.dp, 600.dp)
    )
  }
}

@NightPreview
@Composable
private fun CallParticipantViewerSelfPipNormalPreview() {
  Previews.Preview {
    CallParticipantViewer(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "You", isSelf = true),
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.MEDIUM
      ),
      renderInPip = true,
      selfPipMode = SelfPipMode.NORMAL_SELF_PIP,
      isMoreThanOneCameraAvailable = true,
      onSwitchCameraClick = {},
      modifier = Modifier.size(CallScreenMetrics.NormalRendererDpSize)
    )
  }
}

@NightPreview
@Composable
private fun CallParticipantViewerSelfPipExpandedPreview() {
  Previews.Preview {
    CallParticipantViewer(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "You", isSelf = true),
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.HIGH
      ),
      renderInPip = true,
      selfPipMode = SelfPipMode.EXPANDED_SELF_PIP,
      isMoreThanOneCameraAvailable = true,
      onSwitchCameraClick = {},
      modifier = Modifier.size(CallScreenMetrics.ExpandedRendererDpSize)
    )
  }
}

@NightPreview
@Composable
private fun CallParticipantViewerSelfPipMiniPreview() {
  Previews.Preview {
    CallParticipantViewer(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "You", isSelf = true),
        isMicrophoneEnabled = false
      ),
      renderInPip = true,
      selfPipMode = SelfPipMode.MINI_SELF_PIP,
      isMoreThanOneCameraAvailable = false,
      modifier = Modifier.size(CallScreenMetrics.SmallRendererDpSize)
    )
  }
}
