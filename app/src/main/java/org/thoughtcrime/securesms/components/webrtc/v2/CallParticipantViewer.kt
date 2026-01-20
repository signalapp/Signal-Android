/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import org.thoughtcrime.securesms.compose.GlideImage
import org.thoughtcrime.securesms.compose.GlideImageScaleType
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.rememberRecipientField
import org.thoughtcrime.securesms.ringrtc.CameraState
import org.webrtc.RendererCommon

/**
 * Displays a remote participant (or local participant in pre-join screen).
 * Handles both full-size grid view and system PIP mode.
 *
 * @param participant The call participant to display
 * @param renderInPip Whether rendering in system PIP mode (smaller, simplified UI)
 * @param raiseHandAllowed Whether to show raise hand indicator
 * @param onInfoMoreInfoClick Callback when "More Info" is tapped on blocked/missing keys overlay
 */
@Composable
fun RemoteParticipantContent(
  participant: CallParticipant,
  renderInPip: Boolean,
  raiseHandAllowed: Boolean,
  onInfoMoreInfoClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  mirrorVideo: Boolean = false,
  showAudioIndicator: Boolean = true
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
      var isVideoReady by remember(participant.callParticipantId) { mutableStateOf(false) }

      if (!hasContentToRender) {
        isVideoReady = false
      }

      val showAvatar = !hasContentToRender || !isVideoReady

      Crossfade(
        targetState = showAvatar,
        animationSpec = CallGridDefaults.alphaAnimationSpec,
        label = "video-ready-crossfade"
      ) { shouldShowAvatar ->
        if (shouldShowAvatar) {
          if (renderInPip) {
            PipAvatar(
              recipient = recipient,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              AvatarWithBadge(recipient = recipient)
            }
          }
        } else {
          Box(modifier = Modifier.fillMaxSize())
        }
      }

      if (hasContentToRender) {
        VideoRenderer(
          participant = participant,
          onFirstFrameRendered = { isVideoReady = true },
          showLetterboxing = isVideoReady,
          mirror = mirrorVideo,
          modifier = Modifier.fillMaxSize()
        )
      }

      if (showAudioIndicator) {
        AudioIndicator(
          participant = participant,
          selfPipMode = SelfPipMode.NOT_SELF_PIP,
          modifier = Modifier.align(Alignment.BottomStart)
        )
      }

      if (raiseHandAllowed && !renderInPip && participant.isHandRaised) {
        val shortName by rememberRecipientField(participant.recipient) { getShortDisplayName(context) }

        RaiseHandIndicator(
          name = shortName,
          iconSize = 40.dp,
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 12.dp, top = 12.dp)
        )
      }
    }
  }
}

/**
 * Displays the local camera preview overlay (self PIP).
 * Shows video feed with audio indicator and camera switch button when camera is enabled.
 * Shows a blurred gray background with camera-off icon when camera is disabled.
 *
 * @param participant The local call participant
 * @param selfPipMode The current self-pip display mode
 * @param isMoreThanOneCameraAvailable Whether to show camera switch button
 * @param onSwitchCameraClick Callback when camera switch is tapped
 */
@Composable
fun SelfPipContent(
  participant: CallParticipant,
  selfPipMode: SelfPipMode,
  isMoreThanOneCameraAvailable: Boolean,
  onSwitchCameraClick: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  if (participant.isVideoEnabled) {
    Box(modifier = modifier) {
      VideoRenderer(
        participant = participant,
        mirror = participant.cameraDirection == CameraState.Direction.FRONT,
        modifier = Modifier.fillMaxSize()
      )

      AudioIndicator(
        participant = participant,
        selfPipMode = selfPipMode,
        modifier = Modifier.align(Alignment.BottomStart)
      )

      if (isMoreThanOneCameraAvailable) {
        SwitchCameraButton(
          selfPipMode = selfPipMode,
          onClick = onSwitchCameraClick,
          modifier = Modifier.align(Alignment.BottomEnd)
        )
      }
    }
  } else {
    SelfPipCameraOffContent(
      participant = participant,
      selfPipMode = selfPipMode,
      modifier = modifier
    )
  }
}

/**
 * Camera-off state for self PIP.
 * Shows a blurred avatar background with a semi-transparent gray overlay,
 * centered video-off icon, and audio indicator in lower-start.
 */
@Composable
private fun SelfPipCameraOffContent(
  participant: CallParticipant,
  selfPipMode: SelfPipMode,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier) {
    BlurredBackgroundAvatar(recipient = participant.recipient)

    // Semi-transparent overlay
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          color = Color(0x995E5E5E), // rgba(94, 94, 94, 0.6)
          shape = RoundedCornerShape(24.dp)
        )
    )

    Icon(
      imageVector = ImageVector.vectorResource(id = R.drawable.symbol_video_slash_fill_24),
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier
        .size(24.dp)
        .align(Alignment.Center)
    )

    AudioIndicator(
      participant = participant,
      selfPipMode = selfPipMode,
      modifier = Modifier.align(Alignment.BottomStart)
    )
  }
}

/**
 * Displays a remote participant in the overflow strip.
 * Shows video if enabled, otherwise shows the participant's avatar filling the tile.
 *
 * This is a simplified version of [RemoteParticipantContent] that:
 * - Always renders in "pip mode" style (avatar fills the space when video is off)
 * - Uses the same audio indicator metrics as [SelfPipMode.MINI_SELF_PIP]
 * - Does not show raise hand indicators or info overlays
 *
 * @param participant The call participant to display
 */
@Composable
fun OverflowParticipantContent(
  participant: CallParticipant,
  modifier: Modifier = Modifier
) {
  val recipient = participant.recipient

  Box(modifier = modifier) {
    BlurredBackgroundAvatar(recipient = recipient)

    val hasContentToRender = participant.isVideoEnabled || participant.isScreenSharing

    if (hasContentToRender) {
      VideoRenderer(
        participant = participant,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      PipAvatar(
        recipient = recipient,
        modifier = Modifier
          .size(rememberCallScreenMetrics().overflowParticipantRendererAvatarSize)
          .align(Alignment.Center)
      )
    }

    if (participant.isHandRaised) {
      RaiseHandIndicator(
        name = "",
        iconSize = 32.dp,
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(10.dp)
      )
    }
  }
}

@Composable
private fun BlurredBackgroundAvatar(
  recipient: Recipient,
  modifier: Modifier = Modifier
) {
  BlurContainer(
    isBlurred = true,
    modifier = modifier,
    blurRadius = 15.dp
  ) {
    if (LocalInspectionMode.current) {
      Image(
        painter = painterResource(R.drawable.ic_avatar_abstract_02),
        contentDescription = null,
        modifier = Modifier
          .fillMaxSize()
          .background(color = MaterialTheme.colorScheme.background)
      )
    } else {
      val photo = remember(recipient.isSelf, recipient.contactPhoto) {
        if (recipient.isSelf) {
          ProfileContactPhoto(recipient)
        } else {
          recipient.contactPhoto
        }
      }

      GlideImage(
        modifier = Modifier
          .fillMaxSize()
          .background(color = Color.Black),
        model = photo,
        scaleType = GlideImageScaleType.CENTER_CROP
      )
    }
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
  }
}

@Composable
private fun VideoRenderer(
  participant: CallParticipant,
  onFirstFrameRendered: (() -> Unit)? = null,
  showLetterboxing: Boolean = true,
  mirror: Boolean = false,
  modifier: Modifier = Modifier
) {
  var renderer by remember { mutableStateOf<TextureViewRenderer?>(null) }

  val rendererEvents = remember(onFirstFrameRendered) {
    if (onFirstFrameRendered != null) {
      object : RendererCommon.RendererEvents {
        override fun onFirstFrameRendered() {
          onFirstFrameRendered()
        }

        override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
        }
      }
    } else {
      null
    }
  }

  val backgroundColor = if (showLetterboxing) {
    android.graphics.Color.parseColor("#CC000000")
  } else {
    android.graphics.Color.TRANSPARENT
  }

  AndroidView(
    factory = { context ->
      FrameLayout(context).apply {
        setBackgroundColor(backgroundColor)
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
              init(eglBase, rendererEvents)
            }
            attachBroadcastVideoSink(participant.videoSink)
          }

          setMirror(mirror)
        }

        renderer = textureRenderer
        addView(textureRenderer)
      }
    },
    update = { frameLayout ->
      frameLayout.setBackgroundColor(backgroundColor)
      val textureRenderer = renderer
      if (textureRenderer != null) {
        if (participant.isVideoEnabled) {
          participant.videoSink.lockableEglBase.performWithValidEglBase { eglBase ->
            textureRenderer.init(eglBase, rendererEvents)
          }
          textureRenderer.attachBroadcastVideoSink(participant.videoSink)
        } else {
          textureRenderer.attachBroadcastVideoSink(null)
        }

        textureRenderer.setMirror(mirror)
      }
    },
    onRelease = {
      renderer?.release()
    },
    modifier = modifier
  )
}

@Composable
internal fun AudioIndicator(
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
    SelfPipMode.OVERLAY_SELF_PIP -> 0.dp
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
  val targetSize = when (selfPipMode) {
    SelfPipMode.EXPANDED_SELF_PIP, SelfPipMode.FOCUSED_SELF_PIP -> 48.dp
    else -> 28.dp
  }

  val size by animateDpAsState(targetSize)

  val targetMargin = when (selfPipMode) {
    SelfPipMode.FOCUSED_SELF_PIP -> 12.dp
    else -> 10.dp
  }

  val margin by animateDpAsState(targetMargin)

  val targetIconInset = when (selfPipMode) {
    SelfPipMode.EXPANDED_SELF_PIP, SelfPipMode.FOCUSED_SELF_PIP -> 12.dp
    SelfPipMode.MINI_SELF_PIP -> 7.dp
    else -> 6.dp
  }

  val iconInset by animateDpAsState(targetIconInset)
  val hasActiveClick = (selfPipMode == SelfPipMode.EXPANDED_SELF_PIP || selfPipMode == SelfPipMode.FOCUSED_SELF_PIP) && onClick != null

  val clickModifier = if (hasActiveClick) {
    Modifier.clickable { onClick() }
  } else {
    Modifier
  }

  val background by animateColorAsState(
    if (hasActiveClick) {
      MaterialTheme.colorScheme.secondaryContainer
    } else {
      MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    }
  )

  Box(
    modifier = modifier
      .padding(end = margin, bottom = margin)
      .size(size)
      .background(
        color = background,
        shape = CircleShape
      )
      .padding(iconInset)
      .then(clickModifier),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      painter = painterResource(id = R.drawable.symbol_switch_24),
      contentDescription = stringResource(R.string.SwitchCameraButton__switch_camera_direction),
      tint = Color.White
    )
  }
}

@Composable
private fun RaiseHandIndicator(
  name: String,
  iconSize: Dp,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .background(
        color = colorResource(R.color.signal_light_colorSurface),
        shape = RoundedCornerShape(percent = 50)
      ),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = painterResource(id = R.drawable.symbol_raise_hand_24),
      contentDescription = null,
      tint = Color.Unspecified, // Let the drawable use its default color
      modifier = Modifier.size(iconSize).padding(vertical = 6.dp).padding(start = 6.dp, end = if (name.isNotBlank()) 4.dp else 6.dp)
    )

    if (name.isNotBlank()) {
      Text(
        text = name,
        color = colorResource(R.color.signal_light_colorOnSurface),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(end = 12.dp)
      )
    }
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
  FOCUSED_SELF_PIP,
  OVERLAY_SELF_PIP
}

@NightPreview
@Composable
private fun SwitchCameraButtonOnPreview() {
  Previews.Preview {
    Box(
      modifier = Modifier.background(
        brush = Brush.linearGradient(colors = listOf(Color.Green, Color.Blue))
      )
    ) {
      SwitchCameraButton(
        selfPipMode = SelfPipMode.EXPANDED_SELF_PIP,
        onClick = {},
        modifier = Modifier
      )
    }
  }
}

@NightPreview
@Composable
private fun SwitchCameraButtonOffPreview() {
  Previews.Preview {
    Box(
      modifier = Modifier.background(
        brush = Brush.linearGradient(colors = listOf(Color.Green, Color.Blue))
      )
    ) {
      SwitchCameraButton(
        selfPipMode = SelfPipMode.NORMAL_SELF_PIP,
        onClick = {},
        modifier = Modifier
      )
    }
  }
}

// region Remote Participant Previews

@NightPreview
@Composable
private fun RemoteParticipantGridPreview() {
  Previews.Preview {
    RemoteParticipantContent(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Alice Johnson"),
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.MEDIUM
      ),
      raiseHandAllowed = false,
      renderInPip = false,
      onInfoMoreInfoClick = null,
      modifier = Modifier.size(400.dp, 600.dp)
    )
  }
}

@NightPreview
@Composable
private fun RemoteParticipantRaiseHandPreview() {
  Previews.Preview {
    RemoteParticipantContent(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Bob Smith"),
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.HIGH,
        handRaisedTimestamp = System.currentTimeMillis()
      ),
      raiseHandAllowed = true,
      renderInPip = false,
      onInfoMoreInfoClick = null,
      modifier = Modifier.size(400.dp, 600.dp)
    )
  }
}

@NightPreview
@Composable
private fun RemoteParticipantSystemPipPreview() {
  Previews.Preview {
    RemoteParticipantContent(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Charlie Davis"),
        isMicrophoneEnabled = false
      ),
      raiseHandAllowed = false,
      renderInPip = true,
      onInfoMoreInfoClick = null,
      modifier = Modifier.size(200.dp, 200.dp)
    )
  }
}

@NightPreview
@Composable
private fun RemoteParticipantSystemPipLandscapePreview() {
  Previews.Preview {
    RemoteParticipantContent(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Charlie Davis"),
        isMicrophoneEnabled = false
      ),
      raiseHandAllowed = false,
      renderInPip = true,
      onInfoMoreInfoClick = null,
      modifier = Modifier.size(200.dp, 100.dp)
    )
  }
}

@NightPreview
@Composable
private fun RemoteParticipantBlockedPreview() {
  Previews.Preview {
    RemoteParticipantContent(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Diana Prince", isBlocked = true)
      ),
      raiseHandAllowed = false,
      renderInPip = false,
      onInfoMoreInfoClick = {},
      modifier = Modifier.size(400.dp, 600.dp)
    )
  }
}

// endregion

// region Self PIP Previews

@NightPreview
@Composable
private fun SelfPipNormalPreview() {
  Previews.Preview {
    SelfPipContent(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "You", isSelf = true),
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.MEDIUM
      ),
      selfPipMode = SelfPipMode.NORMAL_SELF_PIP,
      isMoreThanOneCameraAvailable = true,
      onSwitchCameraClick = {},
      modifier = Modifier.size(rememberCallScreenMetrics().normalRendererDpSize)
    )
  }
}

@NightPreview
@Composable
private fun SelfPipExpandedPreview() {
  Previews.Preview {
    SelfPipContent(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "You", isSelf = true),
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.HIGH
      ),
      selfPipMode = SelfPipMode.EXPANDED_SELF_PIP,
      isMoreThanOneCameraAvailable = true,
      onSwitchCameraClick = {},
      modifier = Modifier.size(rememberCallScreenMetrics().expandedRendererDpSize)
    )
  }
}

@NightPreview
@Composable
private fun SelfPipMiniPreview() {
  Previews.Preview {
    SelfPipContent(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "You", isSelf = true),
        isVideoEnabled = true,
        isMicrophoneEnabled = false
      ),
      selfPipMode = SelfPipMode.MINI_SELF_PIP,
      isMoreThanOneCameraAvailable = true,
      onSwitchCameraClick = {},
      modifier = Modifier.size(rememberCallScreenMetrics().overflowParticipantRendererDpSize)
    )
  }
}

@NightPreview
@Composable
private fun SelfPipCameraOffPreview() {
  Previews.Preview {
    SelfPipContent(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "You", isSelf = true),
        isMicrophoneEnabled = false,
        isVideoEnabled = false
      ),
      selfPipMode = SelfPipMode.NORMAL_SELF_PIP,
      isMoreThanOneCameraAvailable = false,
      onSwitchCameraClick = null,
      modifier = Modifier.size(rememberCallScreenMetrics().normalRendererDpSize)
    )
  }
}

// endregion

// region Overflow Participant Previews

@NightPreview
@Composable
private fun OverflowParticipantPreview() {
  Previews.Preview {
    OverflowParticipantContent(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Eve Wilson"),
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.MEDIUM
      ),
      modifier = Modifier.size(rememberCallScreenMetrics().overflowParticipantRendererDpSize)
    )
  }
}

@NightPreview
@Composable
private fun OverflowParticipantRaisedHandPreview() {
  Previews.Preview {
    OverflowParticipantContent(
      participant = CallParticipant.EMPTY.copy(
        recipient = Recipient(isResolving = false, systemContactName = "Frank Miller"),
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.HIGH,
        handRaisedTimestamp = System.currentTimeMillis()
      ),
      modifier = Modifier.size(rememberCallScreenMetrics().overflowParticipantRendererDpSize)
    )
  }
}

// endregion
